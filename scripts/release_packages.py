#!/usr/bin/env python3
"""Build public manifests from the exact finished APK bytes."""
import hashlib
import json
import os
from pathlib import Path
repo = os.environ.get('GITHUB_REPOSITORY', 'kanunal99-jpg/LANU-Yalan-n-Seveyim')
for code, version in [(5, '0.4.0-alpha'), (6, '0.4.1-alpha')]:
    tag = f'v{version}'
    folder = Path('release-packages') / tag
    filename = f'LANU-Yalanini-Seveyim-{tag}-arm64-test.apk'
    apk = folder / filename
    data = apk.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    manifest = dict(versionCode=code, versionName=version, apkUrl=f'https://github.com/{repo}/releases/download/{tag}/{filename}', sha256=digest, apkBytes=len(data))
    (folder/'lanu-update.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n')
    (folder/'SHA256SUMS.txt').write_text(f'{digest}  {filename}\n')
    print(f'{tag}: versionCode={code}, bytes={len(data)}, sha256={digest}')
