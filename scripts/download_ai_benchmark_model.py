#!/usr/bin/env python3
"""Download the exact app model for synthetic CI validation; verify before using it."""
import hashlib
import re
import sys
import urllib.request
from pathlib import Path
source=Path('app/src/main/java/com/lanu/yalan/ai/ModelStore.kt').read_text()
url=re.search(r'const val URL = "([^"]+)"',source)[1]
expected=re.search(r'const val HASH = "([a-f0-9]{64})"',source)[1]
size=int(re.search(r'const val SIZE = (\d+)L',source)[1])
path=Path(sys.argv[1]);path.parent.mkdir(parents=True,exist_ok=True)
partial=path.with_suffix('.part')
digest=hashlib.sha256();total=0
try:
 with urllib.request.urlopen(url,timeout=120) as response, partial.open('wb') as output:
  while chunk:=response.read(1024*1024):
   total+=len(chunk)
   if total>size:raise ValueError('Model exceeds pinned size')
   digest.update(chunk);output.write(chunk)
 if total!=size or digest.hexdigest()!=expected:raise ValueError('Model does not match app size/SHA-256')
 partial.replace(path)
 print(f'Pinned model verified: {total} bytes, SHA-256 {expected}')
except BaseException:
 partial.unlink(missing_ok=True)
 raise
