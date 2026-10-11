#!/usr/bin/env python3
"""Freeze final reusable source and bind the immutable build and runtime evidence."""
from pathlib import Path
import hashlib,json,os,subprocess,datetime
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'evidence/final-source'
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
 return h.hexdigest()
assert not OUT.exists(),'Use a new freeze; never overwrite a prior source snapshot'
sources=[]
for directory,dirs,names in os.walk(ROOT):
 dirs[:]=[d for d in dirs if d not in ['evidence','build','.gradle','.kotlin','__pycache__','.git']]
 for name in names:
  p=Path(directory)/name
  if name in ['local.properties','README.md','verification.md'] or name.endswith('.log'):continue
  rel=p.relative_to(ROOT);copy=OUT/rel;copy.parent.mkdir(parents=True,exist_ok=True)
  subprocess.run(['cp','-c',str(p),str(copy)],check=True)
  assert p.stat().st_ino!=copy.stat().st_ino
  sources.append({'path':str(rel),'snapshot':str(copy.relative_to(ROOT)),'sha256':sha(p)})
artifacts=[]
for name in ['candidate1','candidate2']:
 candidate=json.loads((ROOT/f'evidence/{name}/manifest.json').read_text())
 for rel,h in candidate['sources'].items():
  assert sha(ROOT/f'evidence/{name}/source'/rel)==h,rel
  if name=='candidate2':assert sha(ROOT/rel)==h,rel
 for rel,h in candidate['artifacts'].items():
  assert sha(ROOT/rel)==h,rel
  artifacts.append({'path':rel,'sha256':h,'scope':'Captured heap and original behavior build' if name=='candidate1' else 'Final photo-layout repair and behavior build'})
evidence=[]
for p in sorted((ROOT/'evidence').rglob('*')):
 if p.is_file() and 'final-source' not in p.parts and p.name!='runtime-freeze.json':
  evidence.append({'path':str(p.relative_to(ROOT)),'sha256':sha(p)})
manifest={'date':datetime.datetime.now(datetime.timezone.utc).isoformat(),'status':'FROZEN_FOR_INDEPENDENT_REVIEW','sources':sources,'artifacts':artifacts,'evidence_files':evidence,'scope':'Candidate1 binds all six diagnostic debug heaps. Candidate2 changes only detail-photo layout and has final Android/native verification; no candidate2 heap capture is claimed. Losslessly compressed original HPROFs retain uncompressed identities in capture manifest. This source/evidence freeze is not itself a test, native-pixel, content or publication acceptance.'}
(ROOT/'evidence/runtime-freeze.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'sources':len(sources),'evidence_files':len(evidence),'artifacts':len(artifacts)}))
