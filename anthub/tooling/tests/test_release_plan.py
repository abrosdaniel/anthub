import sys
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[3]/'anthub/tooling/seed'))
from publish import plan

def release(version,draft=False,preview=False):
 return dict(tag_name='pack-v'+version,draft=draft,prerelease=preview)
class ReleasePlanTest(unittest.TestCase):
 def test_first(self):self.assertTrue(plan('1.0.0',[]))
 def test_retry_published(self):self.assertFalse(plan('1.0.0',[release('1.0.0')]))
 def test_retry_draft(self):self.assertTrue(plan('1.0.0',[release('1.0.0',True)]))
 def test_new(self):self.assertTrue(plan('1.10.0',[release('1.9.0')]))
 def test_reject_rollback(self):
  with self.assertRaises(ValueError):plan('1.9.0',[release('1.10.0')])
 def test_ignore_preview(self):self.assertTrue(plan('1.0.0',[release('2.0.0',preview=True)]))
 def test_reject_noncanonical(self):
  for version in ('1.0','01.0.0','1.0.0-beta','1.0.0+build'):
   with self.assertRaises(ValueError):plan(version,[])
