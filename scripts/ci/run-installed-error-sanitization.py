#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Temporarily install fault routes on a disposable packaged app, then restore it.

The application classes, real filter mappings, CXF/Struts configuration and nginx
remain installed. Only the test servlet and its exact mappings are added. No
fault route or test class enters a distributable WAR or Debian package.
"""
from contextlib import contextmanager
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import re

import defusedxml.ElementTree as ET
import shutil
import signal
import stat
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
WEBAPP = Path('/usr/share/carlos-emr/webapp/carlos')


def replace_descriptor(path, data, metadata):
    """Publish a whole descriptor atomically while preserving package ownership/mode."""
    descriptor, name = tempfile.mkstemp(prefix='.carlos-test-webxml-', dir=path.parent)
    pending = Path(name)
    try:
        with os.fdopen(descriptor, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fchmod(stream.fileno(), stat.S_IMODE(metadata.st_mode))
            if os.geteuid() == 0:
                os.fchown(stream.fileno(), metadata.st_uid, metadata.st_gid)
            os.fsync(stream.fileno())
        os.replace(pending, path)
    finally:
        pending.unlink(missing_ok=True)


@contextmanager
def fault_routes(webapp, token, compiled):
    descriptor = webapp / 'WEB-INF/web.xml'
    classes = webapp / 'WEB-INF/classes/carlostest5794'
    if (descriptor.is_symlink() or not descriptor.is_file() or classes.exists()
            or classes.is_symlink() or not compiled.is_file() or compiled.is_symlink()):
        raise ValueError('Fault fixture requires ordinary package files and an unused class directory')
    # Refuse linked parents before touching the installed tree.
    if (webapp.resolve() != webapp or descriptor.parent.resolve() != descriptor.parent
            or classes.parent.resolve() != classes.parent):
        raise ValueError('Fault fixture refuses linked application parents')
    if not re.fullmatch(r'[a-f0-9]{32}', token):
        raise ValueError('Invalid fault fixture token')
    metadata = descriptor.stat()
    before = descriptor.read_bytes()
    if before.count(b'</web-app>') != 1 or b'carlostest5794' in before:
        raise ValueError('Ambiguous installed deployment descriptor')
    document = ET.fromstring(before, forbid_dtd=True)
    mappings = [row for row in document.findall('{*}filter-mapping')
                if row.findtext('{*}filter-name') == 'ResponseSanitizationFilter']
    dispatchers = {item.text for row in mappings for item in row.findall('{*}dispatcher')}
    if not {'REQUEST', 'ERROR'} <= dispatchers:
        raise ValueError('Installed sanitizer lacks required REQUEST/ERROR mappings')
    if any(row.findtext('{*}error-code') == '500' for row in document.findall('{*}error-page')):
        raise ValueError('Existing numeric 500 error mapping must be explicitly reconciled')
    addition = f'''<servlet><servlet-name>carlostest5794</servlet-name>
<servlet-class>carlostest5794.ErrorSanitizationFaultServlet</servlet-class></servlet>
<servlet-mapping><servlet-name>carlostest5794</servlet-name><url-pattern>/__test-error-{token}.jsp</url-pattern></servlet-mapping>
<servlet-mapping><servlet-name>carlostest5794</servlet-name><url-pattern>/ws/rs/__test-error-{token}</url-pattern></servlet-mapping>
<error-page><error-code>500</error-code><location>/__test-error-{token}.jsp</location></error-page>
'''.encode()
    installed = before.replace(b'</web-app>', addition + b'</web-app>')
    classes.mkdir(mode=0o755)
    try:
        shutil.copyfile(compiled, classes / compiled.name)
        (classes / compiled.name).chmod(0o644)
        replace_descriptor(descriptor, installed, metadata)
        yield
    finally:
        # Restore the original bytes even after browser failure/interruption.
        # A concurrent descriptor edit must be reported, not silently erased.
        drift = descriptor.read_bytes() not in (installed, before)
        if drift:
            raise RuntimeError('Concurrent descriptor drift; fixture retained for explicit recovery')
        replace_descriptor(descriptor, before, metadata)
        os.utime(descriptor, ns=(metadata.st_atime_ns, metadata.st_mtime_ns))
        (classes / compiled.name).unlink(missing_ok=True)
        classes.rmdir()
        if descriptor.read_bytes() != before or drift:
            raise RuntimeError('Installed descriptor restoration failed or concurrent drift occurred')


def restart():
    subprocess.run(['systemctl', 'restart', 'carlos-emr'], check=True, timeout=120,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    from installed_front_door import wait_for_login
    wait_for_login()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    command = command or ['node', 'scripts/error-sanitization-playwright-checks.js']
    if command[:2] not in (['node', 'scripts/error-sanitization-playwright-checks.js'], ['node', 'scripts/run-playwright-suite.js']):
        raise RuntimeError('Fault adapter only wraps the registered browser check or suite')
    if os.environ.get('CARLOS_DISPOSABLE_HOST') != 'true' or os.geteuid() != 0:
        raise RuntimeError('Fault installation requires root inside the explicit disposable package test environment')
    if not Path('/run/systemd/container').is_file():
        raise RuntimeError('Fault installation is restricted to a systemd test container')
    token = secrets.token_hex(16)
    descriptor = WEBAPP / 'WEB-INF/web.xml'
    original_hash = hashlib.sha256(descriptor.read_bytes()).hexdigest()
    # Termination must pass through finally, restoring the package tree.
    def interrupted(_number, _frame):
        raise InterruptedError('Installed fault test interrupted')
    previous = signal.signal(signal.SIGTERM, interrupted)
    try:
        with tempfile.TemporaryDirectory(prefix='carlos-error-5794-') as temporary:
            classes = Path(temporary)
            subprocess.run(['javac', '-cp', '/usr/share/java/tomcat11-servlet-api.jar', '-d', str(classes),
                            str(ROOT / 'scripts/fixtures/ErrorSanitizationFaultServlet.java')], check=True, timeout=60)
            try:
                with fault_routes(WEBAPP, token, classes / 'carlostest5794/ErrorSanitizationFaultServlet.class'):
                    restart()
                    before_restarts = subprocess.check_output(['systemctl', 'show', 'carlos-emr', '-p', 'NRestarts', '--value'], timeout=20)
                    from process_group import run_process_group
                    result = run_process_group(command, cwd=ROOT,
                        env={**os.environ, 'ERROR_SANITIZATION_FAULT_TOKEN': token}, timeout=36000, grace=10, track_descendants=True)
                    after_restarts = subprocess.check_output(['systemctl', 'show', 'carlos-emr', '-p', 'NRestarts', '--value'], timeout=20)
                    if before_restarts != after_restarts:
                        raise RuntimeError('Installed application restarted during fault qualification')
                    if result.returncode:
                        raise RuntimeError('Installed front-door error check failed')
            finally:
                restart()
            if hashlib.sha256(descriptor.read_bytes()).hexdigest() != original_hash:
                raise RuntimeError('Installed descriptor differs after fixture removal')
            print(json.dumps({'wrappedCommandPassed': True, 'installedDescriptorRestored': True,
                              'faultFixtureRemoved': not (WEBAPP / 'WEB-INF/classes/carlostest5794').exists()}))
    finally:
        signal.signal(signal.SIGTERM, previous)


if __name__ == '__main__':
    main()
