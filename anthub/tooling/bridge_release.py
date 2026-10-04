"""Package the terminal AntHub client migration installer from tested build artifacts."""
import argparse
import hashlib
import io
import json
import re
import shutil
import zipfile
from pathlib import Path

def package(root,artifact,output):
    number=re.search(r'^anthubBridgeVersion=(.+)$',(root/'gradle.properties').read_text(),re.M)[1]
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+',number):raise ValueError('Invalid bridge version')
    name=f'anthub-{number}-mc1.21.1-neoforge.jar'
    jars=list(artifact.rglob(name))
    if len(jars)!=1:raise ValueError(f"Expected exactly one tested {name}, found {len(jars)}")
    source=jars[0]
    with zipfile.ZipFile(source) as outer:
        with zipfile.ZipFile(io.BytesIO(outer.read('anthub/game.jar'))) as game:
            names=set(game.namelist())
            if 'dev/abros/anthub/bridge/AntHubBridge.class' not in names:raise ValueError('Not a transition installer')
            if any(n.startswith(('dev/abros/anthub/server/','dev/abros/anthub/core/','org/postgresql/')) or 'mixin' in n for n in names):
                raise ValueError('Unexpected legacy classes in installer')
            protocols=json.loads(game.read('anthub/protocols.json'))
            metadata=game.read('META-INF/neoforge.mods.toml').decode()
            if not re.search(r'(?m)^version\s*=\s*"'+re.escape(number)+r'"\s*$',metadata):raise ValueError('Bundled version mismatch')
            if not re.search(r'(?m)^modId\s*=\s*"anthub"\s*$',metadata):raise ValueError('Bundled mod identity mismatch')
            if protocols != {'pack':1,'auth':3,'menu':3,'helper':1}:raise ValueError('Invalid bridge protocols')
    output.mkdir(parents=True,exist_ok=True)
    destination=output/name;shutil.copyfile(source,destination)
    descriptor={'schemaVersion':1,'version':number,'protocols':protocols,'artifacts':[{
        'minecraft':'1.21.1','neoForge':'21.1.250','java':21,
        'url':f'https://github.com/abrosdaniel/anthub/releases/download/v{number}/{name}',
        'sha256':hashlib.sha256(destination.read_bytes()).hexdigest(),'size':destination.stat().st_size,'helperProtocolVersion':1}]}
    core=output/'core.json';core.write_text(json.dumps(descriptor,indent=2)+'\n')
    notes=output/'RELEASE_NOTES.md';shutil.copyfile(root/'anthub/BRIDGE_RELEASE_NOTES.md',notes)
    files=[destination,core,notes]
    (output/'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.name+'\n' for f in files))
    print(f'Packaged AntHub {number} migration installer ({destination.stat().st_size} bytes)')
    return files+[output/'SHA256SUMS.txt']
if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact',type=Path,default=Path('anthub/bridge/build/libs'))
    parser.add_argument('--output',type=Path,default=Path('dist/bridge-3.9.0'))
    parser.add_argument('--publish',action='store_true')
    args=parser.parse_args();root=Path.cwd()
    if args.publish:
        from release import publish
        number=re.search(r'^anthubBridgeVersion=(.+)$',(root/'gradle.properties').read_text(),re.M)[1]
        if not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)',number):raise ValueError('Invalid bridge version')
        publish(root,args.artifact,args.output,release_version=number,packager=package,release_notes=root/'anthub/BRIDGE_RELEASE_NOTES.md')
    else:package(root,args.artifact,args.output)
