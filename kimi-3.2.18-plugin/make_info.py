#!/usr/bin/env python3
import plistlib, sys, hashlib
from pathlib import Path

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
asar_path = Path(sys.argv[3])

with src.open('rb') as f:
    data = plistlib.load(f)

integrity = data.get('ElectronAsarIntegrity')
if not isinstance(integrity, dict):
    raise SystemExit('ElectronAsarIntegrity missing')

entry = integrity.get('Resources/app.asar')
if not isinstance(entry, dict):
    raise SystemExit('ElectronAsarIntegrity Resources/app.asar missing')

h = hashlib.sha256(asar_path.read_bytes()).hexdigest()
entry['algorithm'] = 'SHA256'
entry['hash'] = h

with dst.open('wb') as f:
    plistlib.dump(data, f, fmt=plistlib.FMT_XML, sort_keys=False)

print('PATCHED_INFO_ASAR_HASH=' + h)
print('INTEGRITY_ENTRY=' + repr(entry))
