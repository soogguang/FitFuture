#!/usr/bin/env python3
"""Device tests. Full flow resets app state; --camera-only uses non-persisted preview mode."""
import argparse
import subprocess
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb', default='adb')
p.add_argument('--recommendation-url', default='', help='Optional running FastAPI fixture URL as seen by the emulator')
p.add_argument('--camera-only', action='store_true')
a = p.parse_args()
for apk in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
    subprocess.run([a.adb, 'install', '--no-streaming', '-r', apk], check=True)
subprocess.run([a.adb, 'shell', 'pm', 'grant', 'com.fitfuture.app', 'android.permission.CAMERA'], check=True)
command = [a.adb, 'shell', 'am', 'instrument', '-w']
if a.recommendation_url:
    command += ['-e', 'recommendationUrl', a.recommendation_url]
if a.camera_only:
    command += ['-e', 'cameraOnly', 'true']
command += ['com.fitfuture.app.test/com.fitfuture.app.FlowInstrumentation']
result = subprocess.run(command, text=True, encoding='utf-8', errors='replace', capture_output=True, timeout=180)
output = result.stdout + result.stderr
out = Path('app/build/qa')
out.mkdir(parents=True, exist_ok=True)
(out / ('camera-test.txt' if a.camera_only else 'flow-test.txt')).write_text(output, encoding='utf-8')
print(output)
if result.returncode != 0 or 'PASS:' not in output or 'FAIL:' in output or 'Process crashed' in output:
    raise SystemExit(1)
