#!/usr/bin/env python3
"""Export a source-only handoff with per-file SHA-256, without runtime caches.

Run from any directory using Python 3. The root follows this script, not cwd.
The release ID is a document/archive identifier, not a Git tag or final release.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser=argparse.ArgumentParser()
parser.add_argument('--release',required=True,help='Archive ID, e.g. B-LOCAL-2026-10-30-01')
args=parser.parse_args()
if not args.release or any(c not in 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_' for c in args.release):
    raise SystemExit('Use only letters, digits, hyphens and underscores in the release ID')
root=Path(__file__).resolve().parents[1]
allowed=['src','data/fixtures','docs','handoff','log','scripts','.mvn','mvnw','mvnw.cmd','pom.xml','README.md','AGENTS.md','.editorconfig','.gitattributes','.gitignore']
files=[]
for item in allowed:
    p=root/item
    if not p.exists():raise SystemExit(f'Missing export source: {item}')
    for f in sorted(p.rglob('*')) if p.is_dir() else [p]:
        if not f.is_file():continue
        rel=f.relative_to(root)
        if f.is_symlink() or any(part in {'.cache','target','.git','__pycache__','.idea','.vscode'} for part in rel.parts):continue
        if f.name=='.DS_Store' or f.suffix in {'.pyc','.log','.key','.pem','.p12','.jks'} or f.name.startswith('.env'):continue
        files.append((f,rel.as_posix()))
manifest={'release':args.release,'scope':'B local source handoff; real team services pending',
          'files':{name:hashlib.sha256(f.read_bytes()).hexdigest() for f,name in files}}
output=root/'deliverables';output.mkdir(exist_ok=True)
archive=output/(args.release+'.zip')
# Fail closed if a known local generated credential appears in any exported file.
secret_file=root/'.cache/staff-preview/password.txt'
if secret_file.exists():
    secret=secret_file.read_bytes().strip()
    if secret and any(secret in f.read_bytes() for f,_ in files):raise SystemExit('Export blocked: generated local credential found in source')
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED) as z:
    for f,name in files:z.write(f,'holiday-resort-booking/'+name)
    z.writestr('holiday-resort-booking/HANDOFF-MANIFEST.json',json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    for name,digest in manifest['files'].items():assert hashlib.sha256(z.read('holiday-resort-booking/'+name)).hexdigest()==digest
sha=hashlib.sha256(archive.read_bytes()).hexdigest()
(output/(archive.name+'.sha256')).write_text(sha+'  '+archive.name+'\n')
print(f'Created {archive.name}: {len(files)} files, {archive.stat().st_size} bytes; manifest verified')
