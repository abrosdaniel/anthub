import importlib.util,unittest,copy,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[3]/'anthub/tooling/seed'))
spec=importlib.util.spec_from_file_location('anthub',Path(__file__).resolve().parents[3]/'anthub/tooling/seed/anthub.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class Contracts(unittest.TestCase):
    def test_template(self):m.load_project(Path(__file__).resolve().parents[3]/'template')
    def test_traversal(self):
        for path in ['../mods/a','mods/../a','mods/CON.jar','config/anthub-client.toml','mods/a\\b','saves/world']:
            with self.assertRaises(ValueError):m.pathcheck(path)
    def test_duplicate_json(self):
        with self.assertRaises(ValueError):m.json.loads('{"x":1,"x":2}',object_pairs_hook=m.pairs)
    def test_dependency_cycle(self):
        c=[dict(id='a',kind='required',dependencies=['b'],conflicts=[]),dict(id='b',kind='optional',dependencies=['a'],conflicts=[])]
        with self.assertRaises(ValueError):m.semantics(c,[],[])
    def test_conflict_required(self):
        c=[dict(id='a',kind='required',dependencies=[],conflicts=['b']),dict(id='b',kind='optional',dependencies=[],conflicts=[])]
        with self.assertRaises(ValueError):m.semantics(c,[],[])

    def test_single_server_template_needs_no_project_metadata(self):
        root=Path(__file__).resolve().parents[3]/'template'
        source=m.read(root/'anthub.json');self.assertNotIn('project',source)
        project,_,_=m.load_project(root)
        self.assertEqual(1,len(project['servers']))
        self.assertEqual(source['name'],project['servers'][0]['name'])
        self.assertEqual(source['server']['address'],project['servers'][0]['address'])
    def test_multiple_servers_rejected_in_source(self):
        root=Path(__file__).resolve().parents[3]/'template'
        source=m.read(root/'anthub.json');source.pop('server')
        source['servers']=[dict(id='a',name='a',address='a.example.org'),dict(id='b',name='b',address='b.example.org')]
        with self.assertRaises(Exception):m.validate_schema('project',source)

    def test_compact_defaults_and_explicit_overrides(self):
        import tempfile,shutil
        root=Path(__file__).resolve().parents[3]/'template'
        project,components,files=m.load_project(root)
        self.assertEqual('neoforge',project['minecraft']['loader'])
        self.assertEqual('required',components['components'][0]['kind'])
        self.assertEqual('preserve',files[0]['policy'])
        self.assertEqual(project['version'],files[0]['version'])
        with tempfile.TemporaryDirectory() as tmp:
            target=Path(tmp)/'project';shutil.copytree(root,target)
            pack=m.read(target/'anthub.json');component=pack['components'][0]
            component['kind']='optional';component['files'][0]['policy']='enforce';component['files'][0]['version']='custom'
            m.write(target/'anthub.json',pack)
            _,components,files=m.load_project(target)
            self.assertEqual('optional',components['components'][0]['kind'])
            self.assertEqual('enforce',files[0]['policy']);self.assertEqual('custom',files[0]['version'])

    def test_external_components_rejected(self):
        import tempfile,shutil
        root=Path(__file__).resolve().parents[3]/'template'
        with tempfile.TemporaryDirectory() as tmp:
            target=Path(tmp)/'project';shutil.copytree(root,target)
            source=m.read(target/'anthub.json');components=source.pop('components')
            m.write(target/'components.json',{'components':components});m.write(target/'anthub.json',source)
            with self.assertRaises(Exception):m.load_project(target)
    def test_invalid_inline_components_rejected(self):
        root=Path(__file__).resolve().parents[3]/'template'
        source=m.read(root/'anthub.json');source['components'][0]['files'][0]['policy']='invalid'
        with self.assertRaises(Exception):m.validate_schema('project',source)
