import argparse, json, shutil, sys, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[3]/'template/tooling'))
import anthub
class MirrorsTest(unittest.TestCase):
 def build(self,responses,local=False):
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder)/'project';shutil.copytree(Path(__file__).resolve().parents[3]/'template',root)
   p=anthub.read(root/'anthub.json');p['components']=[{'id':'test','name':'Test','files':[{'path':'config/test.toml','urls':['https://first.example/test.jar','https://second.example/test.jar']}]}]
   if local:
    (root/'test.jar').write_bytes(b'good');p['components'][0]['files'][0]['repositoryPath']='test.jar'
   anthub.write(root/'anthub.json',p)
   args=argparse.Namespace(project=root,output=Path(folder)/'out',repository='https://github.com/example/project',commit='a'*40,sequence=1,channel='stable',policy='recommended')
   with patch.object(anthub,'download',side_effect=responses):anthub.build(args)
   anthub.verify_release(args.output)
   return anthub.read(args.output/'anthub.lock.json')['files'][0]
 def test_unavailable_primary(self):
  file=self.build([OSError('offline'),b'good']);self.assertEqual(file['sha256'],anthub.sha(b'good'));self.assertEqual(len(file['urls']),2)
 def test_identical_mirrors(self):self.assertEqual(self.build([b'good',b'good'])['size'],4)
 def test_mismatch(self):
  with self.assertRaisesRegex(ValueError,'Mirror hash mismatch'):self.build([b'good',b'evil'])
 def test_all_unavailable(self):
  with self.assertRaisesRegex(ValueError,'All sources unavailable'):self.build([OSError(),OSError()])
 def test_local_file_is_authority(self):
  self.assertEqual(len(self.build([OSError(),b'good'],True)['urls']),3)
  with self.assertRaisesRegex(ValueError,'Mirror hash mismatch'):self.build([b'evil',b'good'],True)
