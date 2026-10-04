"""Read-only check that the official stable Rivet release can be installed."""
import hashlib
import json
import urllib.request

def read(url, limit=1024*1024):
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'AntHub-Rivet-Migration'}), timeout=30) as response:
        data=response.read(limit+1)
    if len(data)>limit: raise ValueError('Oversized Rivet metadata')
    return json.loads(data)

def verify():
    release=read('https://api.github.com/repos/abrosdaniel/rivet/releases/latest')
    tag=release['tag_name']
    import re
    if release['draft'] or release['prerelease'] or not re.fullmatch(r'v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)',tag):
        raise ValueError('No published stable Rivet release')
    base=f'https://github.com/abrosdaniel/rivet/releases/download/{tag}/'
    descriptor=read(base+'core.json')
    if descriptor['schemaVersion']!=1 or descriptor['version']!=tag[1:]: raise ValueError('Rivet descriptor mismatch')
    artifact=next(a for a in descriptor['artifacts'] if a['minecraft']=='1.21.1' and a['java']<=21 and a['neoForge'].startswith('21.1.'))
    name=f'rivet-{tag[1:]}-mc1.21.1-neoforge.jar'
    if artifact['url']!=base+name or not re.fullmatch('[0-9a-f]{64}',artifact['sha256']) or not 0<artifact['size']<=256*1024*1024:
        raise ValueError('Invalid Rivet artifact')
    assets={a['name'] for a in release['assets']}
    if not {'core.json',name}.issubset(assets):raise ValueError('Missing Rivet release assets')
    print(f'Published migration target: {tag}, Minecraft 1.21.1, NeoForge >= {artifact["neoForge"]}')
    return descriptor
if __name__=='__main__':verify()
