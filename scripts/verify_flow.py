#!/usr/bin/env python3
"""Run on a disposable emulator: resets only FitFuture app state on that device."""
import argparse, subprocess
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb',default='adb')
a=p.parse_args()
for apk in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
    subprocess.run([a.adb,'install','--no-streaming','-r',apk],check=True)
result=subprocess.run([a.adb,'shell','am','instrument','-w','com.fitfuture.app.test/com.fitfuture.app.FlowInstrumentation'],text=True,capture_output=True,timeout=120)
output=result.stdout+result.stderr
out=Path('app/build/qa');out.mkdir(parents=True,exist_ok=True);(out/'flow-test.txt').write_text(output)
print(output)
if result.returncode!=0 or 'PASS:' not in output or 'FAIL:' in output or 'Process crashed' in output:raise SystemExit(1)
