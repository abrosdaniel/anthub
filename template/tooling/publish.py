import base64,json,os,subprocess,sys,tempfile
from pathlib import Path
from release_plan import project_version
def run(args,**kw):
    try:return subprocess.run(args,check=True,**kw)
    except subprocess.CalledProcessError as error:raise SystemExit(error.returncode) from None
project=json.loads(Path('anthub.json').read_text());version=project_version(project);channel='stable';repo=os.environ['GITHUB_REPOSITORY'];tag='pack-v'+version
if sys.argv[1]=='build':
    os.environ['ANTHUB_RELEASE_TIME']=subprocess.check_output(['git','show','-s','--format=%cI',os.environ['GITHUB_SHA']],text=True).strip()
    run([sys.executable,'tooling/anthub.py','build-lock','.', '--output','release-output','--repository','https://github.com/'+repo,'--commit',os.environ['GITHUB_SHA'],'--sequence',os.environ['ANTHUB_SEQUENCE']])
elif sys.argv[1]=='publish':
    out=Path('release-output');existing=subprocess.run(['gh','release','view',tag,'--repo',repo],capture_output=True)
    release_exists=existing.returncode==0
    assets=[str(p) for p in out.iterdir() if p.name not in (channel+'.json',)]
    if not release_exists:run(['gh','release','create',tag,*assets,'--repo',repo,'--target',os.environ['GITHUB_SHA'],'--title',tag,'--notes','AntHub pack '+version,'--draft'])
    with tempfile.TemporaryDirectory() as folder:
        run(['gh','release','download',tag,'--repo',repo,'--dir',folder])
        for asset in assets:
            if Path(asset).read_bytes()!=(Path(folder)/Path(asset).name).read_bytes():raise SystemExit('Uploaded asset differs: '+asset)
    run(['gh','release','edit',tag,'--repo',repo,'--draft=false'])
    branch=os.environ.get('GITHUB_REF_NAME','main')
    # Publish the release pointer only after every asset is available.
    def api(endpoint,payload=None):
        cmd=['gh','api',endpoint]
        if payload is not None:cmd+=['--method','POST','--input','-']
        r=subprocess.run(cmd,input=json.dumps(payload).encode() if payload is not None else None,stdout=subprocess.PIPE,check=True)
        return json.loads(r.stdout)
    ref=api('repos/'+repo+'/git/ref/heads/'+branch);head=ref['object']['sha'];commit=api('repos/'+repo+'/git/commits/'+head)
    latest=api('repos/'+repo+'/contents/anthub.json?ref='+head)
    if project_version(json.loads(base64.b64decode(latest['content'])))!=version:
        raise SystemExit('A newer version is on the branch; release retained, channel not changed')

    entries=[]
    for name in (channel+'.json',):
        blob=api('repos/'+repo+'/git/blobs',{'content':base64.b64encode((out/name).read_bytes()).decode(),'encoding':'base64'})
        entries.append({'path':'channels/'+name,'mode':'100644','type':'blob','sha':blob['sha']})
    tree=api('repos/'+repo+'/git/trees',{'base_tree':commit['tree']['sha'],'tree':entries})
    new=api('repos/'+repo+'/git/commits',{'message':'Publish '+tag+' to '+channel,'tree':tree['sha'],'parents':[head]})
    run(['gh','api','--method','PATCH','repos/'+repo+'/git/refs/heads/'+branch,'--input','-'],input=json.dumps({'sha':new['sha'],'force':False}).encode())
else:raise SystemExit('Expected build or publish')
