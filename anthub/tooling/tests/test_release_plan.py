import unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[3]/'template/tooling'))
from release_plan import decision,project_version,compare_versions
class ReleasePlanTest(unittest.TestCase):
    def test_first_push(self):self.assertEqual((True,1),decision('1.0.0',None,None)[:2])
    def test_unchanged_version(self):self.assertFalse(decision('1.0.0','1.0.0',{'version':'1.0.0','sequence':1})[0])
    def test_bumped_version(self):self.assertEqual((True,8),decision('1.1.0','1.0.0',{'version':'1.0.0','sequence':7})[:2])
    def test_already_published_retry(self):self.assertFalse(decision('1.1.0','1.0.0',{'version':'1.1.0','sequence':8})[0])
    def test_channel_commit(self):self.assertFalse(decision('1.1.0','1.1.0',{'version':'1.1.0','sequence':8})[0])

    def test_first_release_after_setup_push(self):self.assertTrue(decision('1.0.0','1.0.0',None)[0])

    def test_only_flat_version(self):
        self.assertEqual("1.2.0",project_version({"version":"1.2.0"}))
        with self.assertRaises(KeyError):project_version({"pack":{"version":"1.1.0"}})

    def test_downgrade_rejected(self):
        with self.assertRaises(ValueError):decision('1.0.0','2.0.0',{'version':'2.0.0','sequence':3})
    def test_build_metadata_is_not_a_new_version(self):
        with self.assertRaises(ValueError):decision('1.0.0+two','1.0.0+one',{'version':'1.0.0+one','sequence':1})
    def test_semantic_prerelease_order(self):
        self.assertGreater(compare_versions('1.0.0','1.0.0-rc.1'),0)
        self.assertGreater(compare_versions('1.0.0-rc.10','1.0.0-rc.2'),0)
