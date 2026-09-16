"""Select stable releases on default-branch pushes without evaluating commit text."""
import json,os,subprocess,re
from pathlib import Path

def project_version(project):
    return project['version']

def compare_versions(left,right):
    def parse(value):
        match=re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?',value)
        if not match:raise ValueError('Invalid version: '+value)
        return tuple(int(match[i]) for i in (1,2,3)),match[4]
    a,ap=parse(left);b,bp=parse(right)
    if a!=b:return (a>b)-(a<b)
    if ap==bp:return 0
    if ap is None:return 1
    if bp is None:return -1
    aa=ap.split('.');bb=bp.split('.')
    for x,y in zip(aa,bb):
        if x==y:continue
        if x.isdigit() and y.isdigit():return (int(x)>int(y))-(int(x)<int(y))
        if x.isdigit()!=y.isdigit():return -1 if x.isdigit() else 1
        return (x>y)-(x<y)
    return (len(aa)>len(bb))-(len(aa)<len(bb))

def decision(version,previous,published):
    if not published:return True,1,'First release for this repository'
    if version==previous:return False,0,'Pack version did not change'
    if published and published['version']==version:return False,0,'Version is already published'
    if compare_versions(version,published['version'])<=0:raise ValueError('New version must be greater than published version')
    return True,published['sequence']+1,'Pack version changed'

def git(*args):return subprocess.check_output(['git',*args],text=True).strip()
def document(ref,path,optional=False):
    if optional:
        paths=git('ls-tree','--name-only',ref,'--',path).splitlines()
        if path not in paths:return None
    return json.loads(git('show',ref+':'+path))

def main():
    event=json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text());branch=event['repository']['default_branch']
    if event.get('deleted') or event['ref']!='refs/heads/'+branch:return
    current=project_version(document('HEAD','anthub.json'));before=event['before'];previous=None
    if before.strip('0'):
        if subprocess.run(['git','cat-file','-e',before+'^{commit}'],capture_output=True).returncode:
            subprocess.run(['git','fetch','origin',before],check=True)
        old=document(before,'anthub.json',True);previous=project_version(old) if old else None
    subprocess.run(['git','fetch','origin',branch],check=True)
    latest=project_version(document('FETCH_HEAD','anthub.json'))
    pointer=document('FETCH_HEAD','channels/stable.json',True)
    repository='https://github.com/'+event['repository']['full_name'].lower()
    if pointer and pointer.get('repository')!=repository:pointer=None
    publish,sequence,reason=decision(current,previous,pointer)
    if latest!=current:publish=False;reason='A newer pack version is already on the default branch'
    with Path(os.environ['GITHUB_OUTPUT']).open('a') as out:
        out.write('publish='+str(publish).lower()+'\nsequence='+str(sequence)+'\n')
    print(reason)
if __name__=='__main__':main()
