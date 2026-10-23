#!/usr/bin/env python3
"""Inspect and hash the exact finished APKs; reject a broken OTA pair before publication."""
import hashlib
import json
import os
import re
import subprocess
from pathlib import Path
repo=os.environ.get('GITHUB_REPOSITORY','kanunal99-jpg/LANU-Yalan-n-Seveyim')
tools=Path(os.environ['ANDROID_HOME'])/'build-tools/35.0.0'
certificate=None
for code,version in [(7,'0.5.0-alpha'),(8,'0.5.1-alpha')]:
 tag=f'v{version}';folder=Path('release-packages')/tag
 filename=f'LANU-Yalanini-Seveyim-{tag}-arm64-test.apk';apk=folder/filename
 package=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
 identity=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",package)
 if not identity or identity.groups()!=('com.lanu.yalan',str(code),version):
  raise SystemExit(f'{tag}: actual APK package/version does not match the release manifest')
 verification=subprocess.check_output([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
 signers=set(re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})',verification))
 if not signers or (certificate is not None and signers!=certificate):raise SystemExit('OTA pair certificates differ or cannot be verified')
 certificate=signers
 digest=hashlib.file_digest(apk.open('rb'),'sha256').hexdigest()
 manifest=dict(versionCode=code,versionName=version,apkUrl=f'https://github.com/{repo}/releases/download/{tag}/{filename}',sha256=digest,apkBytes=apk.stat().st_size)
 (folder/'lanu-update.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
 (folder/'SHA256SUMS.txt').write_text(f'{digest}  {filename}\n')
 signing=dict(package='com.lanu.yalan',versionCode=code,versionName=version,signerSha256=sorted(signers),permanentSigning=os.environ.get('LANU_REQUIRE_SIGNING')=='true')
 (folder/'signing-info.json').write_text(json.dumps(signing,indent=2)+'\n')
 print(f'{tag}: package/version/signature verified, bytes={apk.stat().st_size}, sha256={digest}')
