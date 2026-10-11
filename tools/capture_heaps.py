#!/usr/bin/env python3
"""Capture controlled debug lifecycle heaps; retain source/APK/device/command identity."""
from pathlib import Path
import argparse,datetime,gzip,hashlib,json,os,subprocess,sys,time,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3];PROJECT=ROOT/'projects/memory-lifetimes'
sys.path.insert(0,str(ROOT/'projects/commerce-app/tools'))
from capture_checkpoint_appearance import CaptureSession

def main():
 p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--serial',default='emulator-5554');p.add_argument('--apk',type=Path,default=PROJECT/'app/build/outputs/apk/debug/app-debug.apk');p.add_argument('--analyzer',type=Path,default=PROJECT/'heap-analyzer/build/install/heap-analyzer/bin/heap-analyzer');a=p.parse_args()
 a.package='staff.android.memory';a.max_scrolls=8;a.settle_timeout=45;a.output=a.output.resolve();a.output.mkdir(parents=True,exist_ok=False)
 s=CaptureSession(a);apk=a.apk.resolve();sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
 report={'status':'STARTING','started':datetime.datetime.now(datetime.timezone.utc).isoformat(),'package':a.package,'apkSha256':sha(apk),'snapshots':[],'visits':[],'scope':'Debug diagnostic API36 emulator heap captures. No physical timing, PSS savings, exact allocation layout, or guarantee of all-device collection.'}
 report['analyzerFiles']={str(f.resolve()):sha(f) for f in [a.analyzer]+list((a.analyzer.parent.parent/'lib').glob('*.jar'))}
 def save():
  (a.output/'manifest.json').write_text(json.dumps(report,indent=2)+'\n');(a.output/'commands.json').write_text(json.dumps(s.commands,indent=2)+'\n')
 def launch(fresh=False,**extra):
  if fresh:s.adb('shell','am','force-stop',a.package)
  cmd=['shell','am','start','-W','-n',a.package+'/.MainActivity','-f','0x10008000' if fresh else '0x20000000']
  for k,v in extra.items():cmd+=['--es',k,v]
  s.adb(*cmd)
 def visits(mode,count):
  for _ in range(count):
   launch(diagnostic='open',mode=mode);raw=s.settled(exact=['detail'])
   deadline=time.monotonic()+30
   while not any(n.get('content-desc')=='Camera' for n in ET.fromstring(raw).iter('node')):
    if time.monotonic()>deadline:raise RuntimeError('Camera image did not complete loading')
    raw=s.settled(exact=['detail'])
   ordinal=len(report['visits'])+1;name=f'visit-{ordinal:02d}'
   (a.output/(name+'-detail.xml')).write_text(raw)
   s.tap('Back',raw);back=s.settled(exact=['shop']);(a.output/(name+'-shop.xml')).write_text(back)
   report['visits'].append({'mode':mode,'detailXml':name+'-detail.xml','detailSha256':hashlib.sha256(raw.encode()).hexdigest(),'shopXml':name+'-shop.xml','shopSha256':hashlib.sha256(back.encode()).hexdigest(),'endpoint':'Camera image content-description present; Back reaches Shop before capture'})
 def snapshot(name,cycles,mode):
  raw=s.settled(exact=['shop']);frame=s.capture(name+'-shop',raw)
  (a.output/(name+'-meminfo.txt')).write_bytes(s.adb('shell','dumpsys','meminfo',a.package))
  tag='cg22_'+str(int(time.time()*1000))+'_'+name
  launch(diagnostic='dump',name=tag)
  deadline=time.monotonic()+90
  while True:
   logs=s.adb('logcat','-d','-s','CG22:I','*:S').decode(errors='replace')
   if 'HEAP_READY '+tag+'.hprof' in logs:break
   if time.monotonic()>deadline:raise RuntimeError('Heap snapshot timed out: '+name)
   time.sleep(.5)
  (a.output/(name+'-heap-log.txt')).write_text(logs)
  heap=a.output/(name+'.hprof');heap.write_bytes(s.adb('exec-out','run-as',a.package,'cat','files/'+tag+'.hprof'))
  digest=sha(heap);size=heap.stat().st_size
  analyzer=a.analyzer.resolve()
  cmd=[str(analyzer),str(heap),str(a.output/(name+'-analysis'))]
  done=subprocess.run(cmd,capture_output=True,text=True,timeout=180)
  (a.output/(name+'-analyzer.log')).write_text(done.stdout+done.stderr)
  if done.returncode:raise RuntimeError('Analyzer failed: '+name)
  analysis=json.loads((a.output/(name+'-analysis/analysis.json')).read_text());assert analysis['heapSha256']==digest
  compressed=heap.with_suffix('.hprof.gz')
  with heap.open('rb') as source,gzip.open(compressed,'wb',compresslevel=6) as dest:
   import shutil;shutil.copyfileobj(source,dest)
  with gzip.open(compressed,'rb') as f:assert hashlib.sha256(f.read()).hexdigest()==digest
  heap.unlink();s.adb('shell','run-as',a.package,'rm','files/'+tag+'.hprof')
  report['snapshots'].append({'name':name,'mode':mode,'closedVisits':cycles,'hprof':compressed.name,'uncompressedSha256':digest,'compressedSha256':sha(compressed),'uncompressedBytes':size,'frame':frame,'analysis':name+'-analysis/analysis.json','analyzerCommand':cmd,'targetInstances':len(analysis['targetInstances']),'destroyed':len(analysis['selectedDestroyedIds']),'paths':len(analysis['paths']),'unreachable':analysis['unreachableSelectedObjects']});save()
  print(name+': '+str(report['snapshots'][-1]['destroyed'])+' destroyed, '+str(report['snapshots'][-1]['paths'])+' paths',flush=True)
 try:
  report['properties']=s.adb('shell','getprop').decode();report['settings']=s.settings()
  (a.output/'battery.txt').write_bytes(s.adb('shell','dumpsys','battery'));(a.output/'thermal.txt').write_bytes(s.adb('shell','dumpsys','thermalservice'))
  s.adb('install','-r',str(apk));path=s.adb('shell','pm','path',a.package).decode().strip().removeprefix('package:');s.adb('pull',path,str(a.output/'installed.apk'));assert sha(a.output/'installed.apk')==report['apkSha256']
  launch(fresh=True);s.settled(exact=['shop']);snapshot('baseline',0,'repaired')
  visits('leaky',3);snapshot('leaky3',3,'leaky')
  visits('leaky',3);snapshot('leaky6',6,'leaky')
  launch(diagnostic='clear');snapshot('cleared',6,'diagnostic-reset')
  launch(fresh=True);visits('repaired',3);snapshot('repaired3',3,'repaired')
  visits('repaired',3);snapshot('repaired6',6,'repaired')
  report['status']='CAPTURED_ANALYZED_REVIEW_REQUIRED'
 except Exception as e:
  report['status']='FAILED';report['error']=str(e);report['failureWitness']=s.failure_witness();raise
 finally:
  report['ended']=datetime.datetime.now(datetime.timezone.utc).isoformat();save();(a.output/'tap-guards.json').write_text(json.dumps(s.tap_guards,indent=2)+'\n')
if __name__=='__main__':main()
