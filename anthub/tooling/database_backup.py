"""Owner-only PostgreSQL backup and restore rehearsal. Never restores over a database."""
import argparse
import hashlib
import os
import re
import subprocess
from pathlib import Path

IDENTIFIER = re.compile(r'[A-Za-z_][A-Za-z0-9_]{0,62}')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['backup', 'restore-test'])
    parser.add_argument('--database', required=True)
    parser.add_argument('--user', required=True)
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=5432)
    parser.add_argument('--container', help='Optional local PostgreSQL container; tools run inside it')
    parser.add_argument('--file', type=Path, required=True)
    parser.add_argument('--target', help='A NEW database named anthub_restore_... for a rehearsal')
    args = parser.parse_args()
    for value in (args.database, args.user):
        if not IDENTIFIER.fullmatch(value):parser.error('Invalid database/user identifier')
    if args.operation == 'restore-test' and (not args.target or not IDENTIFIER.fullmatch(args.target) or not args.target.startswith('anthub_restore_') or args.target == args.database):
        parser.error('Restore target must be a NEW anthub_restore_... database, different from source')
    prefix = ['docker', 'exec', '-i', '-e', 'PGPASSWORD', args.container] if args.container else []
    connection = ['--host', args.host, '--port', str(args.port), '--username', args.user, '--no-password']
    def run(tool, *options, **kwargs):
        return subprocess.run([*prefix, tool, *connection, *options], check=True, **kwargs)
    path = args.file.absolute()
    if path.is_symlink():parser.error('Backup must not be a symbolic link')
    if args.operation == 'backup':
        # Exclusive creation never overwrites a previous backup. Partial files are not marked verified.
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'wb') as output:
            run('pg_dump', '--format=custom', '--no-owner', '--no-acl', '--dbname', args.database, stdout=output)
            output.flush();os.fsync(output.fileno())
        # Validate the archive table of contents before writing a completed checksum.
        with path.open('rb') as archive:
            subprocess.run([*prefix, 'pg_restore', '--list'], stdin=archive, stdout=subprocess.DEVNULL, check=True)
        with path.open('rb') as source:digest = hashlib.file_digest(source, 'sha256').hexdigest()
        sidecar = path.with_suffix(path.suffix + '.sha256')
        with os.fdopen(os.open(sidecar, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w') as output:output.write(digest + '\n')
        print('Backup archive and SHA-256 saved. Keep a separate copy off the server.')
    else:
        sidecar=path.with_suffix(path.suffix + '.sha256')
        with path.open('rb') as source:digest=hashlib.file_digest(source,'sha256').hexdigest()
        if digest != sidecar.read_text().strip():raise ValueError('Backup checksum mismatch')
        # createdb fails if the target already exists; no DROP, CLEAN or overwrite option is used.
        run('createdb', '--maintenance-db=postgres', args.target)
        with path.open('rb') as archive:
            run('pg_restore', '--dbname', args.target, '--no-owner', '--no-acl', '--exit-on-error', '--single-transaction', stdin=archive)
        print('Restored into '+args.target+'. Source database was not changed. Verify records before using the backup.')

if __name__ == '__main__':
    main()
