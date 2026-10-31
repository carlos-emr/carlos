#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Repackage a verified upstream Xerces JAR without automatic XML providers (offline)."""

import argparse
import hashlib
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile, ZipInfo

UPSTREAM_SHA256 = "6fc991829af1708d15aea50c66f0beadcd2cfeb6968e0b2f55c1b0909883fe16"
PROVIDERS = {
    "javax.xml.datatype.DatatypeFactory",
    "javax.xml.parsers.DocumentBuilderFactory",
    "javax.xml.parsers.SAXParserFactory",
    "javax.xml.stream.XMLEventFactory",
    "javax.xml.validation.SchemaFactory",
    "org.w3c.dom.DOMImplementationSourceList",
    "org.xml.sax.driver",
}
NOTICE = b"""CARLOS modification of Apache Xerces-J 2.12.2 (2.12.2-carlos1).
The seven upstream META-INF/services registrations were removed so that explicit
CDS parser/schema construction does not override application XML provider lookup.
All other upstream file contents, including LICENSE and NOTICE, are unchanged.
ZIP entries are sorted, stored uncompressed and timestamped 1980-01-01 for an
identical rebuild without depending on the host's zlib version.
Source: https://repo.maven.apache.org/maven2/xerces/xercesImpl/2.12.2/xercesImpl-2.12.2.jar
SHA-256: 6fc991829af1708d15aea50c66f0beadcd2cfeb6968e0b2f55c1b0909883fe16
Rebuild: scripts/build-cds-xerces.py (CARLOS repository).
"""


def build(source: Path, output: Path) -> None:
    if hashlib.sha256(source.read_bytes()).hexdigest() != UPSTREAM_SHA256:
        raise ValueError("Upstream Xerces SHA-256 mismatch")
    if source.resolve() == output.resolve():
        raise ValueError("Output must differ from upstream source")
    with ZipFile(source) as upstream:
        services = {name for name in upstream.namelist()
                    if name.startswith("META-INF/services/") and not name.endswith("/")}
        if services != {"META-INF/services/" + name for name in PROVIDERS}:
            raise ValueError("Unexpected upstream provider registrations")
        entries = {name: upstream.read(name) for name in upstream.namelist()
                   if not name.endswith("/") and name not in services}
    entries["META-INF/CARLOS-MODIFICATIONS.txt"] = NOTICE
    output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(output, "w", compression=ZIP_STORED) as result:
        for name, content in sorted(entries.items()):
            entry = ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            result.writestr(entry, content)
    print(hashlib.sha256(output.read_bytes()).hexdigest(), output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("upstream", type=Path, help="Unmodified Maven Central Xerces 2.12.2 JAR")
    parser.add_argument("output", type=Path, help="Provider-free CDS JAR to create")
    arguments = parser.parse_args()
    build(arguments.upstream, arguments.output)
