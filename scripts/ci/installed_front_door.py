# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Wait for complete, trusted loopback login responses before first-login tests."""
import http.client
import re
import ssl
import signal
import math
import time
from urllib.parse import urljoin, urlsplit

from junit_evidence import EvidenceError


def login_response(deadline, context):
    target = 'https://127.0.0.1/carlos/'
    for _ in range(6):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError('Front-door readiness deadline expired')
        url = urlsplit(target)
        if (url.scheme != 'https' or url.hostname != '127.0.0.1' or url.port not in (None, 443)
                or url.username is not None or url.password is not None or url.fragment):
            raise EvidenceError('Installed login redirected outside its trusted loopback origin')
        connection = http.client.HTTPSConnection('127.0.0.1', context=context, timeout=min(30, remaining))
        try:
            connection.request('GET', url.path + ('?' + url.query if url.query else ''),
                               headers={'Accept-Encoding': 'identity', 'Cache-Control': 'no-cache'})
            response = connection.getresponse()
            body = response.read(1024 * 1024 + 1)
            length = response.getheader('Content-Length')
            if length is not None and (not re.fullmatch(r'[0-9]+', length) or int(length) != len(body)):
                raise http.client.IncompleteRead(body)
            if len(body) > 1024 * 1024 or response.getheader('Content-Encoding', 'identity') != 'identity':
                raise EvidenceError('Installed login response is oversized or unexpectedly encoded')
            status = response.status
            location = response.getheader('Location')
        finally:
            connection.close()
        if status in (301, 302, 303, 307, 308) and location:
            target = urljoin(target, location)
            continue
        # Full framing is consumed before checking the login form. A generic
        # nginx/Tomcat 200, a partial body or a redirect is insufficient.
        return status == 200 and all(re.search(pattern, body, re.I) for pattern in
            (rb'<form\b', rb'\bid\s*=\s*[\x22\x27]username[\x22\x27]',
             rb'\btype\s*=\s*[\x22\x27]password[\x22\x27]'))
    raise EvidenceError('Installed login exceeded the redirect limit')


def _wait_for_login(timeout):
    context = ssl.create_default_context(cafile='/etc/carlos-emr/tls/fullchain.pem')
    started = time.monotonic(); deadline = started + timeout
    consecutive = attempts = 0
    while time.monotonic() < deadline:
        attempts += 1
        try:
            consecutive = consecutive + 1 if login_response(deadline, context) else 0
        except ssl.SSLCertVerificationError:
            raise EvidenceError('Installed front-door certificate does not verify') from None
        except (OSError, http.client.HTTPException):
            consecutive = 0
        if consecutive == 2:
            return {'attempts': attempts, 'consecutiveCompleteLoginResponses': consecutive,
                    'elapsedSeconds': round(time.monotonic() - started, 3), 'tlsVerified': True}
        time.sleep(min(2, max(0, deadline - time.monotonic())))
    raise EvidenceError('Installed front door did not become ready before first login')


def wait_for_login(timeout=420):
    # A socket timeout resets on each read. An actual wall-clock alarm also
    # bounds a peer that dribbles bytes indefinitely inside HTTPResponse.read.
    if type(timeout) not in (int, float) or not math.isfinite(timeout) or timeout <= 0:
        raise EvidenceError('Invalid installed front-door readiness timeout')
    if signal.getitimer(signal.ITIMER_REAL) != (0.0, 0.0):
        raise EvidenceError('Front-door readiness cannot replace an existing process timer')
    def expired(_number, _frame):
        raise TimeoutError('Installed front-door readiness deadline expired')
    previous = signal.signal(signal.SIGALRM, expired)
    try:
        signal.setitimer(signal.ITIMER_REAL, timeout)
        return _wait_for_login(timeout)
    except TimeoutError:
        raise EvidenceError('Installed front door exceeded its readiness deadline') from None
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, previous)
