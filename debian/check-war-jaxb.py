#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Validate JAXB discovery in the actual WAR, including shaded provider registrations."""
from pathlib import PurePosixPath
import re
import tempfile
import shutil
import sys
import zipfile

SERVICE = 'META-INF/services/jakarta.xml.bind.JAXBContextFactory'
FACTORY = 'org.glassfish.jaxb.runtime.v2.JAXBContextFactory'
MULTI_RELEASE = re.compile(r'^META-INF/versions/[0-9]+/')


def check(war_path):
    registrations = []
    with zipfile.ZipFile(war_path) as war:
        names = war.namelist()
        if len(names) != len(set(names)):
            raise ValueError('duplicate WAR entries')
        for name in names:
            if name.startswith('WEB-INF/classes/'):
                relative = MULTI_RELEASE.sub('', name[len('WEB-INF/classes/'):])
                if relative == SERVICE or relative.startswith('org/eclipse/persistence/'):
                    raise ValueError('loose JAXB override in WEB-INF/classes')
            if PurePosixPath(name).parent != PurePosixPath('WEB-INF/lib') or not name.endswith('.jar'):
                continue
            if name.rsplit('/', 1)[-1].startswith('org.eclipse.persistence'):
                raise ValueError('EclipseLink/MOXy artifact in WAR')
            # Spooled disk storage keeps memory bounded when reading a large nested JAR.
            with tempfile.SpooledTemporaryFile(max_size=1024 * 1024) as spool:
                with war.open(name) as source:
                    shutil.copyfileobj(source, spool)
                spool.seek(0)
                with zipfile.ZipFile(spool) as jar:
                    entries = jar.namelist()
                    if len(entries) != len(set(entries)):
                        raise ValueError('duplicate JAR entries')
                    for entry in entries:
                        relative = MULTI_RELEASE.sub('', entry)
                        if relative.startswith('org/eclipse/persistence/'):
                            raise ValueError('EclipseLink/MOXy runtime in WAR')
                        if relative == SERVICE:
                            if entry != SERVICE:
                                raise ValueError('versioned JAXB provider registration')
                            if jar.getinfo(entry).file_size > 16384:
                                raise ValueError('oversized JAXB service registration')
                            content = jar.read(entry).decode('utf-8')
                            providers = [line.split('#', 1)[0].strip() for line in content.splitlines()]
                            providers = [line for line in providers if line]
                            for provider in providers:
                                if provider.replace('.', '/') + '.class' not in entries:
                                    raise ValueError('JAXB factory class missing from registering JAR')
                            registrations.extend((name, provider) for provider in providers)
        if len(registrations) != 1 or registrations[0][1] != FACTORY:
            raise ValueError('WAR must register exactly one Glassfish JAXB factory')


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if len(argv) != 1:
        print('Usage: check-war-jaxb.py WAR', file=sys.stderr)
        return 2
    try:
        check(argv[0])
    except (OSError, ValueError, zipfile.BadZipFile, RuntimeError, NotImplementedError, EOFError) as failure:
        print(f'JAXB WAR provider validation failed: {failure}', file=sys.stderr)
        return 1
    print('JAXB WAR provider validation passed')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
