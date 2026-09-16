import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("mod_release", Path(__file__).resolve().parents[1] / "release.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class ModRelease(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "gradle.properties").write_text("anthubVersion=1.0.0\nneoVersion=21.1.250\n")
        subprocess.run(["git", "init", str(self.root)], check=True, capture_output=True)
        for name in ["anthub.json", ".github/workflows/release.yml", "tooling/anthub.py"]:
            p = self.root / "template" / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text("example")
        subprocess.run(["git", "-C", str(self.root), "add", "template"], check=True)
        (self.root / "template/untracked.txt").write_text("excluded")
        self.artifact = self.root / "tested"
        self.artifact.mkdir()
        with zipfile.ZipFile(self.artifact / "anthub-1.0.0-mc1.21.1-neoforge.jar", "w") as jar:
            jar.writestr("anthub/game.jar", b"bundle")

    def test_archive_and_hashes_are_reproducible(self):
        files = m.package(self.root, self.artifact, self.root / "out")
        with zipfile.ZipFile(files[1]) as archive:
            self.assertIn(".github/workflows/release.yml", archive.namelist())
            self.assertNotIn("untracked.txt", archive.namelist())
        first = files[1].read_bytes()
        m.package(self.root, self.artifact, self.root / "out")
        self.assertEqual(first, files[1].read_bytes())
        for line in files[3].read_text().splitlines():
            digest, name = line.split("  ")
            self.assertEqual(digest, hashlib.sha256((files[3].parent / name).read_bytes()).hexdigest())

    def test_missing_bundle_is_rejected(self):
        with self.assertRaises(ValueError):
            m.package(self.root, self.root / "absent", self.root / "out")

    def test_published_version_is_never_overwritten(self):
        with patch.dict(os.environ, GITHUB_REPOSITORY="owner/repo", GITHUB_SHA="abc"), patch.object(m, "run", side_effect=["abc", json.dumps([[dict(tag_name="v1.0.0", draft=False)]])]) as command:
            m.publish(self.root, self.artifact, self.root / "out")
            self.assertEqual(command.call_count, 2)
            self.assertFalse((self.root / "out").exists())

    def test_tag_for_another_commit_is_rejected(self):
        with patch.dict(os.environ, GITHUB_REPOSITORY="owner/repo", GITHUB_SHA="abc"), patch.object(m, "run", side_effect=["abc", "[[]]", "tag", "", "other"]):
            with self.assertRaisesRegex(ValueError, "another commit"):
                m.publish(self.root, self.artifact, self.root / "out")

    def test_invalid_version_is_rejected(self):
        (self.root / "gradle.properties").write_text("anthubVersion=../unsafe\n")
        with self.assertRaises(ValueError):
            m.version(self.root)


class Publishing(unittest.TestCase):
    def setUp(self):
        ModRelease.setUp(self)
        notes = self.root / "anthub/RELEASE_NOTES.md"
        notes.parent.mkdir()
        notes.write_text("Release text")
        self.existing = []
        self.uploaded = {}
        self.commands = []
        self.corrupt = False
        self.fail_upload = False

    def command(self, *args):
        self.commands.append(args)
        if args[:2] == ("git", "rev-parse"): return "abc"
        if args[:2] == ("git", "ls-remote"): return ""
        if args[:2] == ("gh", "api"): return json.dumps([self.existing])
        if args[:3] == ("gh", "release", "create"):
            self.existing = [dict(tag_name="v1.0.0", draft=True, target_commitish="abc")]
            self.assertEqual((self.root / "anthub/RELEASE_NOTES.md").read_text(), "Release text")
            self.assertIn("--notes-file", args)
        elif args[:3] == ("gh", "release", "upload"):
            if self.fail_upload: raise RuntimeError("interrupted upload")
            for path in args[4:args.index("--repo")]:
                p = Path(path); self.uploaded[p.name] = p.read_bytes()
        elif args[:3] == ("gh", "release", "download"):
            name = args[args.index("--pattern") + 1]
            target = Path(args[args.index("--dir") + 1]) / name
            target.write_bytes(b"damaged" if self.corrupt else self.uploaded[name])
        return ""

    def publish(self):
        with patch.dict(os.environ, GITHUB_REPOSITORY="abrosdaniel/anthub", GITHUB_SHA="abc"), patch.object(m, "run", side_effect=self.command):
            m.publish(self.root, self.artifact, self.root / "out")

    def test_publishes_only_after_all_assets_verified(self):
        self.publish()
        self.assertEqual(len(self.uploaded), 4)
        self.assertEqual(self.commands[-1][:3], ("gh", "release", "edit"))
        core = json.loads(self.uploaded["core.json"])
        from jsonschema import Draft202012Validator
        schema = Path(__file__).resolve().parents[2] / "core/src/main/resources/anthub/schemas/v1/core-release.schema.json"
        Draft202012Validator(json.loads(schema.read_text())).validate(core)
        artifact = core["artifacts"][0]
        jar = self.uploaded["anthub-1.0.0-mc1.21.1-neoforge.jar"]
        self.assertEqual(artifact["sha256"], hashlib.sha256(jar).hexdigest())
        self.assertEqual(artifact["size"], len(jar))

    def test_interrupted_upload_resumes_existing_draft(self):
        self.fail_upload = True
        with self.assertRaises(RuntimeError): self.publish()
        self.assertFalse(any(c[:3] == ("gh", "release", "edit") for c in self.commands))
        self.fail_upload = False
        self.publish()
        self.assertEqual(sum(c[:3] == ("gh", "release", "create") for c in self.commands), 1)
        self.assertEqual(self.commands[-1][:3], ("gh", "release", "edit"))

    def test_bad_download_never_publishes(self):
        self.corrupt = True
        with self.assertRaises(ValueError): self.publish()
        self.assertFalse(any(c[:3] == ("gh", "release", "edit") for c in self.commands))
