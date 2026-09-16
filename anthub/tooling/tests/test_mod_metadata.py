import io,sys,unittest,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[3]/'template/tooling'))
from mod_metadata import inspect_jar,accepts,accepts_dependency

def jar(identity='example',constraint='[1.21,1.22)',name='META-INF/neoforge.mods.toml'):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w') as z:z.writestr(name,f'modLoader="javafml"\n[[mods]]\nmodId="{identity}"\n[[dependencies.{identity}]]\nmodId="minecraft"\nversionRange="{constraint}"\n')
    return out.getvalue()
class MetadataTests(unittest.TestCase):
 def test_compatible(self):
  seen={};inspect_jar(jar(),'mods/example.jar','1.21.1','21.1.250',seen);self.assertEqual(seen,{'example':'mods/example.jar'})
 def test_wrong_version(self):
  with self.assertRaisesRegex(ValueError,'requires minecraft'):inspect_jar(jar(constraint='[1.20,1.21)'),'mods/test.jar','1.21.1','21.1.250',{})
 def test_wrong_loader(self):
  with self.assertRaisesRegex(ValueError,'Wrong loader'):inspect_jar(jar(name='fabric.mod.json'),'mods/test.jar','1.21.1','21.1.250',{})
 def test_duplicate(self):
  seen={};inspect_jar(jar(),'mods/a.jar','1.21.1','21.1.250',seen)
  with self.assertRaisesRegex(ValueError,'Duplicate modId'):inspect_jar(jar(),'mods/b.jar','1.21.1','21.1.250',seen)
 def test_invalid_archive(self):
  with self.assertRaisesRegex(ValueError,'Invalid JAR'):inspect_jar(b'not a jar','mods/a.jar','1.21.1','21.1.250',{})
 def test_ranges(self):
  self.assertTrue(accepts('[1.21.1]','1.21.1'));self.assertFalse(accepts('[1.21.1]','1.21.2'))
  self.assertTrue(accepts('(,1.20],[1.21,)','1.21.1'));self.assertFalse(accepts('(,1.20],[1.21,)','1.20.1'))
  self.assertIsNone(accepts('[1.21-beta,)','1.21.1'))

 def test_spaces_and_exclusive_endpoint(self):
  self.assertTrue(accepts('[1.21, 1.21.1]', '1.21.1'))
  self.assertFalse(accepts('[1.21,1.21.1)', '1.21.1'))
  self.assertTrue(accepts('(, 1.20], [1.21, )', '1.21.1'))
 def test_incompatible_filename_does_not_override_metadata(self):
  with self.assertRaisesRegex(ValueError, 'Choose a compatible mod file'):
   inspect_jar(jar(constraint='[1.20,1.21)'), 'mods/example-mc1.21.1.jar', '1.21.1', '21.1.250', {})

 def test_neoforge_1211_minecraft_compatibility_matrix(self):
  inspect_jar(jar(constraint='[1.21,1.21.1)'), 'mods/iris.jar', '1.21.1', '21.1.250', {})
  self.assertTrue(accepts_dependency('[1.21]', 'minecraft', '1.21.1', '1.21.1'))
  self.assertFalse(accepts_dependency('[1.21,1.21.1)', 'minecraft', '1.21.2', '1.21.2'))
  self.assertFalse(accepts_dependency('[1.20,1.21)', 'minecraft', '1.21.1', '1.21.1'))
 def test_neoforge_1211_loader_compatibility_matrix(self):
  self.assertTrue(accepts_dependency('[21.0,21.1)', 'neoforge', '21.1.250', '1.21.1'))
  self.assertFalse(accepts_dependency('[21.0,21.0.166)', 'neoforge', '21.1.250', '1.21.1'))
  self.assertFalse(accepts_dependency('[21.0,21.1)', 'neoforge', '21.2.1', '1.21.2'))
