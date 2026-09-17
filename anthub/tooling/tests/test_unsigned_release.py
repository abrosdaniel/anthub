import unittest,tempfile,argparse,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[3]/'anthub/tooling/seed'))
import anthub
class MainReleaseTest(unittest.TestCase):
 def test_build_and_verify_without_keys(self):
  with tempfile.TemporaryDirectory() as folder:
   out=Path(folder)/'release';args=argparse.Namespace(project=Path(__file__).resolve().parents[3]/'template',output=out,repository='https://github.com/example/project',commit='a'*40,policy='recommended')
   anthub.build(args);anthub.verify_release(out)
   self.assertFalse(list(out.glob('*.sig.json')));self.assertFalse((out/'stable.json').exists());self.assertEqual(anthub.sha((out/'anthub.lock.json').read_bytes()),(out/'anthub.lock.sha256').read_text().strip())
   path=out/'anthub.lock.json';path.write_bytes(path.read_bytes()+b' ')
   with self.assertRaisesRegex(ValueError,'hash mismatch'):anthub.verify_release(out)
