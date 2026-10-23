#!/usr/bin/env python3
"""Decode CI's existing private key in runner temp; never generate or publish a key."""
import base64
import os
from pathlib import Path
names = ['LANU_KEYSTORE_BASE64', 'LANU_STORE_PASSWORD', 'LANU_KEY_ALIAS', 'LANU_KEY_PASSWORD', 'LANU_CERT_SHA256']
values = [os.environ.get(n, '') for n in names]
if not any(values):
    print('Permanent signing is not configured; this run produces a test-key pair only.')
elif not all(values):
    raise SystemExit('Signing configuration is incomplete; refusing to fall back to a debug key.')
else:
    raw = base64.b64decode(values[0], validate=True)
    if not 256 <= len(raw) <= 1024 * 1024:
        raise SystemExit('Invalid keystore size.')
    path = Path(os.environ['RUNNER_TEMP']) / 'lanu-signing' / 'release.jks'
    path.parent.mkdir(mode=0o700, exist_ok=True)
    path.write_bytes(raw)
    path.chmod(0o600)
    with open(os.environ['GITHUB_ENV'], 'a') as env:
        env.write(f'LANU_KEYSTORE={path}\nLANU_REQUIRE_SIGNING=true\n')
    print('Existing permanent signing key staged; Gradle will verify its certificate before building.')
