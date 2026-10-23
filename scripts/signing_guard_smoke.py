#!/usr/bin/env python3
"""Exercise Gradle's real signing guard with a disposable key, never a repository secret."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile

clean={k:v for k,v in os.environ.items() if not k.startswith('LANU_')}
results=[]
def check(name,extra,task,wanted,phrase=''):
 env=dict(clean,**extra)
 process=subprocess.run(['./gradlew','--no-daemon','--console=plain','-Planu.uiSmoke=true','-Planu.requireProductionSigning=true',task],env=env,capture_output=True,text=True,timeout=180)
 output=process.stdout+process.stderr
 passed=(process.returncode==0)==wanted and (not phrase or phrase in output)
 results.append(dict(case=name,passed=passed))
 if not passed:raise RuntimeError(f'Signing guard case failed: {name}; exit={process.returncode}')
 print(f'SIGNING_GUARD_PASSED {name}')

path=Path('app/build/reports/signing-guard/results.json');path.parent.mkdir(parents=True,exist_ok=True)
try:
 check('required_without_key',{},'help',False,'Kalıcı üretim imzası gerekli')
 check('partial_configuration',{'LANU_CERT_SHA256':'a'*64},'help',False,'eksik yapılandırılmış')
 with tempfile.TemporaryDirectory(prefix='lanu-signing-smoke-',dir=os.environ.get('RUNNER_TEMP')) as tmp:
  os.chmod(tmp,0o700)
  keystore=Path(tmp)/'disposable.jks'
  password=secrets.token_hex(24)
  env=dict(LANU_KEYSTORE=str(keystore),LANU_STORE_PASSWORD=password,LANU_KEY_PASSWORD=password,LANU_KEY_ALIAS='lanu-smoke')
  subprocess.run(['keytool','-genkeypair','-noprompt','-keystore',str(keystore),'-storetype','JKS','-alias','lanu-smoke','-keyalg','RSA','-keysize','2048','-validity','1','-dname','CN=Disposable CI smoke key','-storepass:env','LANU_STORE_PASSWORD','-keypass:env','LANU_KEY_PASSWORD'],env=dict(clean,**env),capture_output=True,check=True)
  os.chmod(keystore,0o600)
  cert=subprocess.check_output(['keytool','-exportcert','-keystore',str(keystore),'-alias','lanu-smoke','-storepass:env','LANU_STORE_PASSWORD'],env=dict(clean,**env),stderr=subprocess.DEVNULL)
  check('mismatched_certificate',dict(env,LANU_CERT_SHA256='0'*64),'verifyProductionSigning',False,'eşleşmiyor')
  check('correct_key_password_and_certificate',dict(env,LANU_CERT_SHA256=hashlib.sha256(cert).hexdigest()),'verifyProductionSigning',True)
finally:
 path.write_text(json.dumps(dict(disposable_key_only=True,results=results,passed=len(results)==4 and all(r['passed'] for r in results)),indent=2)+'\n')
