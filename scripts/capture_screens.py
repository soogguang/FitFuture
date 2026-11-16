#!/usr/bin/env python3
"""Render every supplied PNG state in a debug app. No source PNG is installed in the APK."""
import argparse, json, re, subprocess, time, unicodedata
from pathlib import Path

def catalog(root):
    entries=[]
    for p in sorted(root.rglob('*.png')):
        n=unicodedata.normalize('NFC',p.stem)
        if 'flow' in n.lower(): continue
        route=n[:3].lower(); options={}
        if route=='s19':route='s18'
        if route=='s29':
            route='s29_delete' if '삭제' in n else 's29_allergy' if '알레르기' in n else 's29_nutrition' if '영양' in n else 's29_shortage' if '부족' in n else 's29_required' if '누락' in n else 's29'
        if route=='s14' and '__Add' in n:route='s14_add'
        if route=='s16' and 'Loading' in n:route='s16_loading'
        if route=='s02':options['intro']=2 if '__03' in n else 1 if '__02' in n else 0
        if '3개월' in n:options['months']=3
        if '6개월' in n:options['months']=6
        if '대안2' in n:options['alternative']=1
        if '대안3' in n:options['alternative']=2
        if '가정 펼침' in n:options['expanded']=True
        entries.append(dict(source=str(p),name=n,route=route,options=options))
    return entries

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--source',type=Path,default=Path('../ui'));parser.add_argument('--output',type=Path,default=Path('app/build/qa'));parser.add_argument('--adb',default='adb');parser.add_argument('--catalog-only',action='store_true');args=parser.parse_args()
    entries=catalog(args.source);args.output.mkdir(parents=True,exist_ok=True);(args.output/'catalog.json').write_text(json.dumps(entries,ensure_ascii=False,indent=2))
    if args.catalog_only:return
    def adb(*cmd,**kwargs):return subprocess.run([args.adb,*cmd],check=True,**kwargs)
    adb("logcat","-c")
    for i,e in enumerate(entries):
        adb('shell','am','force-stop','com.fitfuture.app',stdout=subprocess.DEVNULL)
        cmd=['shell','am','start','-W','-f','0x10008000','-n','com.fitfuture.app/.MainActivity','--es','preview',e['route']]
        for k,v in e['options'].items():cmd+=['--ez' if isinstance(v,bool) else '--ei',k,str(v).lower()]
        adb(*cmd,stdout=subprocess.DEVNULL);time.sleep(.5)
        focus=subprocess.check_output([args.adb,'shell','dumpsys','window'],text=True)
        focused=next((line for line in focus.splitlines() if 'mCurrentFocus=' in line),'')
        if 'com.fitfuture.app' not in focused:raise RuntimeError('App not in foreground: '+focused)
        with (args.output/(e['name']+'.png')).open('wb') as f:adb('exec-out','screencap','-p',stdout=f)
        crashes=subprocess.check_output([args.adb,'logcat','-d','-s','AndroidRuntime:E'],text=True)
        if 'FATAL EXCEPTION' in crashes:raise RuntimeError(crashes)
        print(f'{i+1}/{len(entries)} {e["name"]}',flush=True)
    (args.output/'report.json').write_text(json.dumps({'rendered':len(entries),'status':'passed'},indent=2))
if __name__=='__main__':main()
