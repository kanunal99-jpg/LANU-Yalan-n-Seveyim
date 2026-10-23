#!/usr/bin/env python3
"""Read public release URLs back and compare against the built artifacts."""
import hashlib
import json
import urllib.request
from pathlib import Path
for folder in sorted(Path('apk').glob('v0.5.*-alpha')):
 expected=json.loads((folder/'lanu-update.json').read_text())
 public_manifest=expected['apkUrl'].rsplit('/',1)[0]+'/lanu-update.json'
 with urllib.request.urlopen(public_manifest,timeout=60) as response:
  actual=json.loads(response.read(32769))
 if actual!=expected:raise SystemExit('Published manifest differs from the built release')
 digest=hashlib.sha256();size=0
 with urllib.request.urlopen(expected['apkUrl'],timeout=120) as response:
  while chunk:=response.read(1024*1024):
   size+=len(chunk)
   if size>expected['apkBytes']:raise SystemExit('Published APK exceeds declared size')
   digest.update(chunk)
 if size!=expected['apkBytes'] or digest.hexdigest()!=expected['sha256']:raise SystemExit('Published APK download does not match manifest')
 print(f'PUBLIC_DOWNLOAD_VERIFIED {folder.name} bytes={size} sha256={digest.hexdigest()}')
