#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Bounded metadata-only diagnostics for this harness's exclusively owned Tomcat."""
import json
from pathlib import Path
import re

ROOT = Path("/opt/cds-tomcat-ci/logs")


def collect():
    texts = []
    failed = 0
    files = sorted(ROOT.glob("*.log"))[:8] if ROOT.is_dir() else []
    for path in files:
        try:
            if path.is_symlink() or not path.is_file():
                failed += 1
                continue
            with path.open("rb") as source:
                source.seek(max(0, path.stat().st_size - (1 << 20)))
                texts.append(source.read(1 << 20).decode("utf-8", errors="replace"))
        except OSError:
            failed += 1
    text = "\n".join(texts)
    def unique(pattern, limit):
        return list(dict.fromkeys(re.findall(pattern, text)))[-limit:]
    denied = unique(r"AccessDeniedException:\s*(/[A-Za-z0-9_./-]+)", 20)
    return {
        "filesRead": len(texts), "readFailures": failed,
        "exceptionClasses": unique(r"\b(?:[a-zA-Z_$][a-zA-Z0-9_$]*\.)*[a-zA-Z_$][a-zA-Z0-9_$]*(?:Exception|Error)\b", 100),
        "failedBeans": unique(r"[Bb]ean (?:with )?name ['\"]([A-Za-z0-9_.$#-]{1,128})['\"]", 100),
        "frames": unique(r"\bat [A-Za-z0-9_.$/]+\([A-Za-z0-9_.]+:\d+\)", 180),
        "unclassifiedSourceTables": unique(r"CDS_EXPORT_SOURCE_CATALOGUE_REQUIRES_REVIEW table=([A-Za-z0-9_]{1,64})\b", 16),
        "accessDeniedRoots": list(dict.fromkeys("/" + "/".join(path.split("/")[1:4]) for path in denied)),
        "serverStarted": "Server startup in" in text,
        "tlsStarted": "Starting ProtocolHandler" in text,
        "fatalStartup": any(marker in text for marker in (
            "One or more listeners failed to start", "Context [/carlos] startup failed")),
    }


if __name__ == "__main__":
    print(json.dumps(collect(), sort_keys=True))
