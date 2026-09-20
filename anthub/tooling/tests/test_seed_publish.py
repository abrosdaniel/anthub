import json, os, sys, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[3]/'anthub/tooling/seed'))
import publish

class SeedPublishing(unittest.TestCase):
 def setUp(self):
  self.commands=[];self.releases=[];self.assets={};self.fail=False;self.corrupt=False
 def command(self,*args):
  self.commands.append(args)
  if args[:2]==('git','rev-parse'):return 'a'*40
  if args[:2]==('git','ls-remote'):return ''
  if args[:2]==('gh','api'):return json.dumps([self.releases])
  if args[:3]==('gh','release','create'):
   self.releases=[dict(tag_name='pack-v1.0.0',draft=True,target_commitish='a'*40)]
  elif args[:3]==('gh','release','upload'):
   if self.fail:raise RuntimeError('interrupted')
   for name in args[4:args.index('--repo')]:
    p=Path(name);self.assets[p.name]=p.read_bytes()
  elif args[:3]==('gh','release','download'):
   out=Path(args[args.index('--dir')+1])
   for name,data in self.assets.items():(out/name).write_bytes(b'bad' if self.corrupt else data)
  return ''
 def build(self,root,output,*args):
  output.mkdir();(output/'anthub.lock.json').write_bytes(b'lock');(output/'anthub.lock.sha256').write_bytes(b'hash')
 def invoke(self):
  with patch.dict(os.environ,GITHUB_SHA='a'*40,GITHUB_REPOSITORY='example/seed'),patch.object(sys,'argv',['publish.py','publish']),patch.object(publish.anthub,'load_project',return_value=({'version':'1.0.0'},None,None)),patch.object(publish,'build',side_effect=self.build),patch.object(publish,'run',side_effect=self.command),patch.object(publish,'retry',side_effect=self.command):publish.main()
 def test_retry_draft_after_interrupted_upload(self):
  self.fail=True
  with self.assertRaises(RuntimeError):self.invoke()
  self.assertFalse(any(c[:3]==('gh','release','edit') for c in self.commands))
  self.fail=False;self.invoke()
  self.assertEqual(1,sum(c[:3]==('gh','release','create') for c in self.commands))
  self.assertEqual(('gh','release','edit'),self.commands[-1][:3])
 def test_corrupt_upload_never_published(self):
  self.corrupt=True
  with self.assertRaises(ValueError):self.invoke()
  self.assertFalse(any(c[:3]==('gh','release','edit') for c in self.commands))
 def test_published_release_untouched(self):
  self.releases=[dict(tag_name='pack-v1.0.0',draft=False,prerelease=False)]
  self.invoke();self.assertFalse(any(c[:2]==('gh','release') for c in self.commands))
 def test_draft_from_other_commit_rejected(self):
  self.releases=[dict(tag_name='pack-v1.0.0',draft=True,target_commitish='b'*40)]
  with self.assertRaises(ValueError):self.invoke()

 def test_extra_asset_never_published(self):
  self.assets['unexpected.jar']=b'stale'
  with self.assertRaises(ValueError):self.invoke()
  self.assertFalse(any(c[:3]==('gh','release','edit') for c in self.commands))
 def test_transient_retry_is_bounded(self):
  import subprocess
  failure=subprocess.CalledProcessError(1,['gh'],stderr='HTTP 503: unavailable')
  with patch.object(publish.subprocess,'check_output',side_effect=[failure,failure,'ok']),patch.object(publish.time,'sleep') as wait:
   self.assertEqual('ok',publish.retry('gh','release','upload'))
   self.assertEqual(2,wait.call_count)
  with patch.object(publish.subprocess,'check_output',side_effect=failure) as call,patch.object(publish.time,'sleep'):
   with self.assertRaises(RuntimeError):publish.retry('gh','release','upload')
   self.assertEqual(4,call.call_count)
 def test_permission_failure_is_not_retried(self):
  import subprocess
  with patch.object(publish.subprocess,'check_output',side_effect=subprocess.CalledProcessError(1,['gh'],stderr='HTTP 403')) as call,patch.object(publish.time,'sleep'):
   with self.assertRaises(RuntimeError):publish.retry('gh','release','upload')
   self.assertEqual(1,call.call_count)
