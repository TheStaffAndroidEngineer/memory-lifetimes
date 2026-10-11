#!/usr/bin/env python3
"""Native Common Goods memory checkpoint viewports with fresh guarded targets."""
from pathlib import Path
import argparse,hashlib,json,re,sys,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3];PROJECT=ROOT/'projects/memory-lifetimes'
sys.path.insert(0,str(ROOT/'projects/commerce-app/tools'))
from capture_checkpoint_appearance import CaptureSession,panes,pane_key

def main():
 p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--serial',default='emulator-5554');p.add_argument('--candidate',help='Optional retained author checkpoint');p.add_argument('--apk',type=Path,help='Explicit debug APK; defaults to app/build output');p.add_argument('--configs',nargs='+',choices=['light','dark','large','wide'],default=['light','dark','large','wide']);a=p.parse_args();a.package='staff.android.memory';a.max_scrolls=8;a.settle_timeout=45
 apk=(a.apk or (PROJECT/'evidence'/a.candidate/'artifacts/app-debug.apk' if a.candidate else PROJECT/'app/build/outputs/apk/debug/app-debug.apk')).resolve()
 if not apk.is_file():p.error('APK does not exist; run ./gradlew :app:assembleDebug or supply --apk: '+str(apk))
 a.output.mkdir(parents=True,exist_ok=False)
 s=CaptureSession(a);sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
 report={'status':'STARTING','package':a.package,'apkSha256':sha(apk),'rows':[],'restorationErrors':[],'scope':'Debug API36 emulator native pixels. Shop top/scrolled, detail top/lower, Lab across light/dark/font2/wide. Not all interactions, TalkBack certification or physical timing.'};original=None;failure=None
 def launch():
  s.adb('shell','am','force-stop',a.package);s.adb('shell','am','start','-W','-n',a.package+'/.MainActivity','-f','0x10008000');return s.settled(exact=['shop'])
 def scroll(raw,count=1):
  for _ in range(count):
   ps=panes(ET.fromstring(raw),a.package)
   if not ps:break
   raw=s.swipe(ps[-1],'down')
  return raw
 def save():
  (a.output/'result.json').write_text(json.dumps(report,indent=2)+'\n')
 try:
  original=s.settings();report['originalSettings']=original
  s.adb('install','-r',str(apk));path=s.adb('shell','pm','path',a.package).decode().strip().removeprefix('package:');s.adb('pull',path,str(a.output/'installed.apk'));assert sha(a.output/'installed.apk')==report['apkSha256']
  for config in a.configs:
   s.adb('shell','am','force-stop',a.package)
   s.adb('shell','wm','size','1920x1200' if config=='wide' else 'reset');s.adb('shell','wm','density','240' if config=='wide' else 'reset')
   s.adb('shell','settings','put','system','font_scale','2.0' if config=='large' else '1.0');s.adb('shell','cmd','uimode','night','yes' if config=='dark' else 'no')
   row={'config':config,'settings':s.settings(),'frames':[]};report['rows'].append(row)
   assert float(row['settings']['font'])==(2 if config=='large' else 1)
   assert ('Night mode: '+('yes' if config=='dark' else 'no')) in row['settings']['night']
   if config=='wide':assert 'Override size: 1920x1200' in row['settings']['size'] and 'Override density: 240' in row['settings']['density']
   else:assert 'Override' not in row['settings']['size'] and 'Override' not in row['settings']['density']
   raw=launch();row['frames'].append(s.capture(config+'-shop-top',raw));raw=scroll(raw,2);row['frames'].append(s.capture(config+'-shop-scrolled',raw))
   raw=launch();raw=s.tap('Explore Camera',raw);row['frames'].append(s.capture(config+'-detail-top',raw))
   raw=scroll(raw,2);row['frames'].append(s.capture(config+'-detail-lower',raw));raw=s.tap('Back',raw)
   raw=s.tap('Lab',raw);row['frames'].append(s.capture(config+'-lab-top',raw))
   ps=panes(ET.fromstring(raw),a.package)
   if ps:
    coverage,raw=s.seek(pane_key(ps[-1]),'down',raw=raw);row['labCoverage']=coverage;row['frames'].append(s.capture(config+'-lab-lower',raw))
   row['status']='CAPTURED_NOT_REVIEWED';save();print(config+' captured',flush=True)
  report['status']='CAPTURED_NOT_REVIEWED'
 except Exception as e:
  failure=e;report['status']='FAILED';report['error']=str(e);report['failureWitness']=s.failure_witness()
 finally:
  commands=[('shell','am','force-stop',a.package)]
  if original:
   commands.append(('shell','settings','delete','system','font_scale') if original['font'] in ('null','') else ('shell','settings','put','system','font_scale',original['font']))
   commands.append(('shell','cmd','uimode','night',original['night'].split(':')[-1].strip()))
   for kind in ['size','density']:
    m=re.search(r'Override '+kind+r':\s*(\S+)',original[kind],re.I);commands.append(('shell','wm',kind,m.group(1) if m else 'reset'))
  for command in commands:
   try:s.adb(*command)
   except Exception as e:report['restorationErrors'].append(str(e))
  try:report['restoredSettings']=s.settings()
  except Exception as e:report['restorationErrors'].append(str(e))
  save();(a.output/'commands.json').write_text(json.dumps(s.commands,indent=2)+'\n');(a.output/'tap-guards.json').write_text(json.dumps(s.tap_guards,indent=2)+'\n')
 if failure:raise failure
 if report['restorationErrors']:raise RuntimeError('Settings restoration errors')
if __name__=='__main__':main()
