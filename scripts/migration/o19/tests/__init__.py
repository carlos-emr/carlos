# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Tests of the OSCAR 19 manifest generator and of the manifests it ships.

Run from the repository root, with the carlos-ctl package importable
(a checkout of carlos-emr/carlos-ctl on PYTHONPATH or in CARLOS_CTL_SRC,
or the installed package under /usr/lib/carlos-ctl):
    python3 -m unittest discover -s scripts/migration/o19/tests -t .

The manifests under test are THIS checkout's debian/assets/o19-manifest/,
which is what carlos-ctl's loaders read here (CARLOS_CTL_O19_MANIFEST_DIR).
"""

import os
import sys

_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.dirname(os.path.abspath(__file__))))))
os.environ.setdefault("CARLOS_CTL_O19_MANIFEST_DIR",
                      os.path.join(_ROOT, "debian", "assets", "o19-manifest"))
for _cand in (os.environ.get("CARLOS_CTL_SRC"), "/usr/lib/carlos-ctl"):
    if _cand and os.path.isdir(os.path.join(_cand, "carlos_ctl")) \
            and _cand not in sys.path:
        sys.path.append(_cand)
