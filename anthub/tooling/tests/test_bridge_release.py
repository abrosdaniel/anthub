import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

TOOLS = Path(__file__).resolve().parents[1]
def load(name):
    spec = importlib.util.spec_from_file_location(name, TOOLS / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module
bridge = load('bridge_release')
release = load('release')

class BridgeRelease(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        (self.root/'gradle.properties').write_text('anthubVersion=3.8.1\nanthubBridgeVersion=3.9.0\n')
        (self.root/'anthub').mkdir()
        self.notes = self.root/'anthub/BRIDGE_RELEASE_NOTES.md'
        self.notes.write_text('Client migration only')
        self.artifact = self.root/'tested/nested'
        self.artifact.mkdir(parents=True)
        self.jar = self.artifact/'anthub-3.9.0-mc1.21.1-neoforge.jar'
        self.make_bundle()

    def make_bundle(self, version='3.9.0', legacy=False):
        data=io.BytesIO()
        with zipfile.ZipFile(data,'w') as game:
            game.writestr('dev/abros/anthub/bridge/AntHubBridge.class',b'fixture')
            game.writestr('META-INF/neoforge.mods.toml',f'modId = "anthub"\nversion = "{version}"\n')
            game.writestr('anthub/protocols.json',json.dumps(dict(pack=1,auth=3,menu=3,helper=1)))
            if legacy:game.writestr('dev/abros/anthub/server/Legacy.class',b'fixture')
        with zipfile.ZipFile(self.jar,'w') as outer:outer.writestr('anthub/game.jar',data.getvalue())

    def test_nested_artifact_and_checksums(self):
        files=bridge.package(self.root,self.root/'tested',self.root/'out')
        self.assertEqual(len(files),4)
        core=json.loads((self.root/'out/core.json').read_text())
        self.assertEqual(core['version'],'3.9.0')
        self.assertIn('/anthub/releases/download/v3.9.0/',core['artifacts'][0]['url'])
        for line in files[-1].read_text().splitlines():
            digest,name=line.split('  ')
            self.assertEqual(digest,hashlib.sha256((self.root/'out'/name).read_bytes()).hexdigest())

    def test_legacy_bundle_rejected(self):
        self.make_bundle(legacy=True)
        with self.assertRaisesRegex(ValueError,'legacy'):bridge.package(self.root,self.root/'tested',self.root/'out')

    def test_wrong_embedded_version_rejected(self):
        self.make_bundle(version='3.8.1')
        with self.assertRaisesRegex(ValueError,'version mismatch'):bridge.package(self.root,self.root/'tested',self.root/'out')

    def test_publisher_uses_bridge_version_notes_and_verified_assets(self):
        commands=[];uploaded={}
        def command(*args):
            commands.append(args)
            if args[:2]==('git','rev-parse'):return 'a'*40
            if args[:2]==('gh','api'):return '[[]]'
            if args[:3]==('gh','release','create'):
                self.assertEqual(args[3],'v3.9.0')
                self.assertEqual(args[args.index('--notes-file')+1],str(self.notes))
            if args[:3]==('gh','release','upload'):
                for item in args[4:args.index('--repo')]:
                    p=Path(item);uploaded[p.name]=p.read_bytes()
            if args[:3]==('gh','release','download'):
                name=args[args.index('--pattern')+1]
                (Path(args[args.index('--dir')+1])/name).write_bytes(uploaded[name])
            return ''
        with patch.dict(os.environ,GITHUB_REPOSITORY='abrosdaniel/anthub',GITHUB_SHA='a'*40),patch.object(release,'run',side_effect=command):
            release.publish(self.root,self.root/'tested',self.root/'out',release_version='3.9.0',packager=bridge.package,release_notes=self.notes)
        self.assertEqual(len(uploaded),4)
        self.assertEqual(commands[-1][:3],('gh','release','edit'))
