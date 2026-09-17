"""Publish a seed release. Run trusted tooling with the seed as working directory."""
import argparse
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path
import anthub

VERSION = re.compile(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)')

def run(*args):
    return subprocess.check_output(args, text=True).strip()

def release_version(release):
    tag = release.get('tag_name', '')
    value = tag.removeprefix('pack-v')
    return value if tag.startswith('pack-v') and VERSION.fullmatch(value) else None

def plan(version, releases):
    if not VERSION.fullmatch(version):
        raise ValueError('Pack version must be A.B.C')
    published = [r for r in releases if not r['draft'] and not r.get('prerelease', False) and release_version(r)]
    if published:
        latest = max(published, key=lambda r: tuple(map(int, release_version(r).split('.'))))
        if tuple(map(int, version.split('.'))) < tuple(map(int, release_version(latest).split('.'))):
            raise ValueError('Increase version: a newer pack is already published')
    existing = next((r for r in releases if r['tag_name'] == 'pack-v' + version), None)
    if existing and not existing['draft']:
        return False
    return True

def build(root, output, repository, commit):
    os.environ['ANTHUB_RELEASE_TIME'] = run('git', 'show', '-s', '--format=%cI', commit)
    anthub.build(argparse.Namespace(project=root, output=output, repository='https://github.com/'+repository,
                                   commit=commit, policy='recommended'))
    anthub.verify_release(output)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['build', 'publish'])
    args = parser.parse_args()
    root = Path.cwd()
    project, _, _ = anthub.load_project(root)
    repository = os.environ['GITHUB_REPOSITORY']
    commit = os.environ['GITHUB_SHA']
    if not re.fullmatch(r'[a-zA-Z0-9_.-]+/[a-zA-Z0-9_.-]+', repository):
        raise ValueError('Invalid repository')
    if run('git', 'rev-parse', 'HEAD') != commit:
        raise ValueError('Checkout does not match the requested commit')
    tag = 'pack-v' + project['version']
    if args.command == 'build':
        build(root, root/'release-output', repository, commit)
        return
    pages = json.loads(run('gh', 'api', f'repos/{repository}/releases', '--paginate', '--slurp'))
    releases = [r for page in pages for r in page]
    if not plan(project['version'], releases):
        print(tag + ' is already published; increase version to publish changes')
        return
    existing = next((r for r in releases if r['tag_name'] == tag), None)
    if existing and existing['target_commitish'] != commit:
        raise ValueError('Draft belongs to another commit; rerun its original job or increase version')
    # Reusing a tag from another commit would misidentify the release source.
    refs = run('git', 'ls-remote', 'origin', 'refs/tags/'+tag, 'refs/tags/'+tag+'^{}').splitlines()
    if refs:
        target = next((line.split()[0] for line in refs if line.endswith('^{}')), refs[0].split()[0])
        if target != commit:
            raise ValueError('Release tag belongs to another commit; increase version')
    with tempfile.TemporaryDirectory() as temporary:
        output = Path(temporary)/'assets'
        build(root, output, repository, commit)
        if not existing:
            run('gh', 'release', 'create', tag, '--repo', repository, '--target', commit,
                '--title', tag, '--notes', 'AntHub pack '+project['version'], '--draft')
        # Only unpublished drafts may be completed/replaced after interrupted uploads.
        assets = sorted(output.iterdir())
        run('gh', 'release', 'upload', tag, *map(str, assets), '--repo', repository, '--clobber')
        downloaded = Path(temporary)/'verified'
        downloaded.mkdir()
        run('gh', 'release', 'download', tag, '--repo', repository, '--dir', str(downloaded))
        for asset in assets:
            if asset.read_bytes() != (downloaded/asset.name).read_bytes():
                raise ValueError('Uploaded asset differs: '+asset.name)
        run('gh', 'release', 'edit', tag, '--repo', repository, '--draft=false')
    print('Published https://github.com/'+repository+'/releases/tag/'+tag)

if __name__ == '__main__':
    main()
