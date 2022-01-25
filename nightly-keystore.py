#!/usr/bin/env python3

import base64
import os

KEYSTORE_FILE = os.path.join(os.getenv('HOME'), '.android', 'debug.keystore')

debug_keystore = base64.b64decode(os.getenv('DEBUG_KEYSTORE'))
os.makedirs(os.path.dirname(KEYSTORE_FILE), exist_ok=True)
with open(KEYSTORE_FILE, 'wb') as fp:
    fp.write(debug_keystore)
