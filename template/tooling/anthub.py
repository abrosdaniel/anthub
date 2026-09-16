#!/usr/bin/env python3
"""AntHub project validation, immutable lock building and SHA-256 verification."""
import argparse, urllib.error, datetime, hashlib, json, os, re, shutil, socket, ipaddress, urllib.request, urllib.parse
from pathlib import Path
from jsonschema import Draft202012Validator
from mod_metadata import inspect_jar

SCHEMAS=Path(__file__).resolve().parents[1]/'schemas/v1'
def pairs(items):
    d={}
    for k,v in items:
        if k in d:raise ValueError('Duplicate JSON key: '+k)
        d[k]=v
    return d
def read(p):return json.loads(Path(p).read_text(),object_pairs_hook=pairs)
def encoded(o):return (json.dumps(o,ensure_ascii=False,indent=2)+'\n').encode()
def write(p,o):Path(p).parent.mkdir(parents=True,exist_ok=True);Path(p).write_bytes(encoded(o))
def validate_schema(name,o):Draft202012Validator(read(SCHEMAS/(name+'.schema.json'))).validate(o)
def sha(b):return hashlib.sha256(b).hexdigest()
def pathcheck(p):
    roots={'mods','config','defaultconfigs','kubejs','resourcepacks','shaderpacks'}
    parts=p.split('/')
    if len(parts)<2 or parts[0] not in roots or any(x in p for x in ('\\',':')) or any(ord(c)<32 for c in p):raise ValueError('Unsafe path: '+p)
    if any(x in ('','.','..') or x.endswith(('.', ' ')) or re.fullmatch(r'(con|prn|aux|nul|com[1-9]|lpt[1-9])(\..*)?',x,re.I) for x in parts):raise ValueError('Unsafe segment: '+p)
    if parts[0]=='config' and parts[1].lower().startswith('anthub'):raise ValueError('Protected config: '+p)
def semantics(components,files,servers):
    ids={c['id']:c for c in components}
    if len(ids)!=len(components):raise ValueError('Duplicate component id')
    def closure(initial):
        result=set();visiting=set()
        def walk(i):
            if i in visiting:raise ValueError('Dependency cycle: '+i)
            if i in result:return
            if i not in ids:raise ValueError('Missing dependency: '+i)
            visiting.add(i)
            for dep in ids[i]['dependencies']:walk(dep)
            visiting.remove(i);result.add(i)
        for i in initial:walk(i)
        for i in result:
            for other in ids[i]['conflicts']:
                if other not in ids:raise ValueError('Missing conflict: '+other)
                if other in result:raise ValueError('Conflicting selection: '+i+'/'+other)
        return result
    required={c['id'] for c in components if c['kind']=='required'}
    closure(required)
    for i in ids:closure(required|{i})
    paths=set()
    for f in files:
        pathcheck(f['path']);key=f['path'].casefold()
        if key in paths:raise ValueError('Duplicate path: '+key)
        paths.add(key)
        if f['componentId'] not in ids:raise ValueError('Missing component')
        if f['path'].startswith('mods/') and f['policy']!='enforce':raise ValueError('Mods must enforce')
    if len({s['id'] for s in servers})!=len(servers):raise ValueError('Duplicate server id')
    if len({s['address'].lower() for s in servers})!=len(servers):raise ValueError('Duplicate server address')
def load_project(root):
    p=read(root/'anthub.json')
    p.setdefault('schemaVersion',1)
    p.setdefault('anthub',{'minVersion':'1.0.0-alpha.1'})
    p.setdefault('policies',{'customFiles':'warn'})
    p.setdefault('integrations',{'luckperms':False})
    p.setdefault('theme',{})
    p.setdefault('content',{})
    if isinstance(p.get('minecraft'),dict):p['minecraft'].setdefault('loader','neoforge')
    validate_schema('project',p)
    address=p.pop('server')['address'];p['servers']=[dict(id='main',name=p['name'],address=address)]
    c={'schemaVersion':1,'components':p.pop('components')}
    for component in c.get('components',[]):
        component.setdefault('description',component.get('name',''))
        component.setdefault('kind','required');component.setdefault('category','other')
        component.setdefault('dependencies',[]);component.setdefault('conflicts',[])
        for file in component.get('files',[]):
            file.setdefault('version',p['version'])
            path=file.get('path','')
            file.setdefault('policy','enforce' if path.startswith('mods/') else 'preserve' if path.startswith(('config/','defaultconfigs/')) else 'update')
    validate_schema('client-pack',c)
    fs=[dict(f,componentId=x['id']) for x in c['components'] for f in x['files']]
    semantics(c['components'],fs,p['servers'])
    return p,c,fs
class Redirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        check_url(newurl)
        return super().redirect_request(req,fp,code,msg,headers,newurl)
def check_url(url):
    u=urllib.parse.urlparse(url)
    if u.scheme!='https' or not u.hostname or u.username or u.password:raise ValueError('Public HTTPS required')
    for a in socket.getaddrinfo(u.hostname,u.port or 443):
        if not ipaddress.ip_address(a[4][0]).is_global:raise ValueError('Private download address rejected')
def download(url):
    check_url(url)
    with urllib.request.build_opener(Redirects()).open(url,timeout=30) as response:
        data=response.read(512*1024*1024+1)
        if len(data)>512*1024*1024:raise ValueError('Tooling download limit 512 MiB exceeded')
        return data
def build(args):
    root=args.project.resolve();p,c,files=load_project(root);out=args.output;out.mkdir(parents=True,exist_ok=True)
    if (out/'anthub.lock.json').exists():raise ValueError('Output already contains a lock; use a fresh output directory')
    repo=args.repository.rstrip('/').removesuffix('.git').lower()
    if not re.fullmatch(r'https://github.com/[a-z0-9_.-]+/[a-z0-9_.-]+',repo):raise ValueError('Expected GitHub repository URL')
    release_url=repo+'/releases/download/pack-v'+p['version']+'/'
    locked=[];mod_ids={}
    for f in files:
        f=dict(f);expected=f.pop('sha256',None)
        data=None
        urls=f.pop('urls',[])
        if 'repositoryPath' in f:
            path=(root/f.pop('repositoryPath')).resolve()
            if not path.is_relative_to(root):raise ValueError('Repository source escapes root')
            data=path.read_bytes();asset=sha(data);(out/asset).write_bytes(data)
            f['urls']=[release_url+asset]+urls
        else:f['urls']=urls
        for url in urls:
            try: candidate=download(url)
            except (OSError, urllib.error.URLError) as error:
                print('Warning: unavailable source for '+f['path']+' ('+urllib.parse.urlparse(url).netloc+'): '+type(error).__name__)
                continue
            if data is None:data=candidate
            elif sha(candidate)!=sha(data):raise ValueError('Mirror hash mismatch: '+f['path']+' ('+urllib.parse.urlparse(url).netloc+'). Sources contain different bytes. Use mirrors of the exact same file, or keep one source. SHA-256 is calculated automatically; do not copy the hash from another build.')
        if data is None:raise ValueError('All sources unavailable: '+f['path'])
        if expected and sha(data)!=expected:raise ValueError('Source hash changed: '+f['path']+'. An explicit sha256 pins the previous bytes. Check the new file, then remove the optional sha256 field to calculate it automatically.')
        if f['path'].startswith('mods/') and f['path'].endswith('.jar'):inspect_jar(data,f['path'],p['minecraft']['version'],p['minecraft']['loaderVersion'],mod_ids)
        f.update(sha256=sha(data),size=len(data));locked.append(f)
    content=[]
    for path in sorted(root.rglob('*')):
        if not path.is_file() or path.relative_to(root).parts[0] not in ('content','assets'):continue
        data=path.read_bytes();asset=sha(data);(out/asset).write_bytes(data);rel=path.relative_to(root).as_posix()
        kind='asset' if rel.startswith('assets/') else ('news' if '/news/' in rel else ('rules' if path.stem=='rules' else 'changelog'))
        content.append(dict(id=rel,type=kind,url=release_url+asset,sha256=asset,size=len(data)))
    now=os.environ.get('ANTHUB_RELEASE_TIME') or datetime.datetime.now(datetime.timezone.utc).isoformat()
    lock={k:v for k,v in p.items() if k not in ('version','content','name')}
    identity=re.sub('[^a-z0-9-]', '-',repo.rsplit('/',1)[-1])[:64].strip('-') or 'project'
    defaults=dict(id=identity,name=p['name'],description=p['servers'][0]['address'],authors=[])
    lock['project']=dict(defaults,repository=repo)
    lock.update(release=dict(version=p['version'],channel=args.channel,sequence=args.sequence,createdAt=now,sourceCommit=args.commit,updatePolicy=args.policy),components=[{k:v for k,v in x.items() if k!='files'} for x in c['components']],files=locked,content=content)
    validate_schema('lock',lock);semantics(lock['components'],locked,p['servers'])
    data=encoded(lock);(out/'anthub.lock.json').write_bytes(data)
    pointer=dict(schemaVersion=1,repository=repo,channel='stable',sequence=args.sequence,version=p['version'],lockUrl=release_url+'anthub.lock.json',lockSha256=sha(data),publishedAt=now)
    write(out/'stable.json',pointer)
    print('Built release',p['version'],sha(data))
def verify_release(folder):
    raw=(folder/'anthub.lock.json').read_bytes();lock=read(folder/'anthub.lock.json');validate_schema('lock',lock)
    pointer=read(folder/'stable.json');validate_schema('channel',pointer)
    if sha(raw)!=pointer['lockSha256']:raise ValueError('Lock hash mismatch')
    if pointer['repository']!=lock['project']['repository'] or pointer['version']!=lock['release']['version'] or pointer['sequence']!=lock['release']['sequence']:raise ValueError('Release identity mismatch')
    semantics(lock['components'],lock['files'],lock['servers'])
    for entry in lock['files']+lock['content']:
        asset=folder/entry['sha256']
        if asset.exists() and (asset.stat().st_size!=entry['size'] or sha(asset.read_bytes())!=entry['sha256']):raise ValueError('Asset hash mismatch')
    print('Release manifest and hashes valid')
def main():
    a=argparse.ArgumentParser();sub=a.add_subparsers(dest='command',required=True)
    verify=sub.add_parser('verify-release');verify.add_argument('release',type=Path)
    v=sub.add_parser('validate');v.add_argument('project',type=Path)
    b=sub.add_parser('build-lock');b.add_argument('project',type=Path);b.add_argument('--output',type=Path,required=True);b.add_argument('--repository',required=True);b.add_argument('--commit',required=True);b.add_argument('--sequence',type=int,required=True);b.add_argument('--channel',choices=['stable'],default='stable');b.add_argument('--policy',choices=['required','recommended'],default='recommended')
    args=a.parse_args()
    if args.command=='validate':load_project(args.project);print('Project valid')
    elif args.command=='build-lock':build(args)
    elif args.command=='verify-release':verify_release(args.release)
if __name__=='__main__':
    try:main()
    except (ValueError,OSError) as error:
        raise SystemExit('Release error: '+str(error)) from None
