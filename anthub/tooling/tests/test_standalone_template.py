import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class StandaloneTemplateTest(unittest.TestCase):
    def test_plain_folder_copy_builds_release(self):
        source = Path(__file__).resolve().parents[3] / 'template'
        with tempfile.TemporaryDirectory() as directory:
            project = Path(directory) / 'project'
            shutil.copytree(source, project, ignore=shutil.ignore_patterns('__pycache__', '*.pyc'))
            self.assertTrue((project / '.github/workflows/anthub.yml').is_file())
            environment = os.environ.copy()
            environment.pop('PYTHONPATH', None)
            def run(*command):
                return subprocess.check_output(command, cwd=project, env=environment,
                                               stderr=subprocess.STDOUT, text=True).strip()
            run('git', 'init', '-b', 'main')
            run('git', 'add', '.')
            run('git', '-c', 'user.name=Template test', '-c', 'user.email=test@example.invalid',
                '-c', 'commit.gpgsign=false', 'commit', '-m', 'Template test')
            environment.update(GITHUB_REPOSITORY='example/standalone', GITHUB_SHA=run('git', 'rev-parse', 'HEAD'), ANTHUB_SEQUENCE='1')
            tooling = source.parent / 'anthub/tooling/seed'
            self.assertFalse((project/'tooling').exists())
            self.assertFalse((project/'schemas').exists())
            run(sys.executable, str(tooling/'anthub.py'), 'validate', '.')
            run(sys.executable, str(tooling/'publish.py'), 'build')
            run(sys.executable, str(tooling/'anthub.py'), 'verify-release', 'release-output')
            lock = json.loads((project / 'release-output/anthub.lock.json').read_text())
            self.assertEqual('https://github.com/example/standalone', lock['project']['repository'])
            self.assertEqual(json.loads((project / 'anthub.json').read_text())['version'], lock['release']['version'])
