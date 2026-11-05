# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The packaged nginx front door rate-limits the /ws OAuth surfaces (issue #4429).

`/ws/services` and `/ws/oauth/authorize` once had no `limit_req`, so an
anonymous client could drive unbounded rejected-call traffic (each one a
synchronous audit insert) through the proxy. These checks pin that every
`limit_req` / `limit_conn` zone a location uses is declared at http level, and
that the routes named in the issue fall into a throttled location.

Run (from the repository root):
    python3 -m unittest discover -s debian/assets/tests
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SITE = ROOT / "debian" / "assets" / "nginx" / "carlos-emr.conf"
LIMITS = ROOT / "debian" / "assets" / "nginx" / "conf.d" / "carlos-emr-limits.conf"


def strip_comments(text):
    # Whole-line comments only: nginx also treats a mid-line `#` as a comment, but
    # a quoted location regex may legitimately contain " #", which a looser strip
    # would silently truncate.
    return "\n".join(re.sub(r"^\s*#.*$", "", ln) for ln in text.splitlines())


def throttled_locations():
    """[(regex, body)] for every `location ~*` block that sets limit_req."""
    text = strip_comments(SITE.read_text(encoding="utf-8"))
    out = []
    for m in re.finditer(r'location\s+~\*\s+"([^"]+)"\s*\{([^}]*)\}', text):
        if "limit_req " in m.group(2):
            out.append((m.group(1), m.group(2)))
    return out


def route_throttle(path):
    """The location body that handles `path`, as nginx would match it
    (first matching regex location, case-insensitive)."""
    text = strip_comments(SITE.read_text(encoding="utf-8"))
    for m in re.finditer(r'location\s+~\*\s+"([^"]+)"\s*\{([^}]*)\}', text):
        # nginx PCRE and Python agree on the constructs used here
        if re.search(m.group(1), path, re.IGNORECASE):
            return m.group(2)
    return None


class TestWsRateLimits(unittest.TestCase):

    def test_declared_zones_cover_every_zone_in_use(self):
        limits = strip_comments(LIMITS.read_text(encoding="utf-8"))
        req_zones = set(re.findall(r"limit_req_zone\s+\S+\s+zone=(\w+):", limits))
        conn_zones = set(re.findall(r"limit_conn_zone\s+\S+\s+zone=(\w+):", limits))
        for _, body in throttled_locations():
            for z in re.findall(r"limit_req\s+zone=(\w+)", body):
                self.assertIn(z, req_zones)
            for z in re.findall(r"limit_conn\s+(\w+)\s", body):
                self.assertIn(z, conn_zones)

    def test_issue_4429_routes_are_throttled(self):
        for path in (
            "/carlos/ws/services/oauth/info",
            "/carlos/ws/services",
            "/carlos/ws/oauth/authorize",
            "/carlos/ws;x/services/demographics",
            "/carlos/ws/oauth;x/authorize",
            "/carlos;jsessionid=x/ws/services/x",
        ):
            with self.subTest(path=path):
                body = route_throttle(path)
                self.assertIsNotNone(body)
                self.assertIn("limit_req ", body)
                self.assertIn("limit_req_status 429", body)

    def test_previously_throttled_routes_stay_throttled(self):
        for path in ("/carlos/ws/LoginService", "/carlos/ws/oauth/initiate",
                     "/carlos/ws/oauth/token", "/carlos/login"):
            with self.subTest(path=path):
                self.assertIn("limit_req ", route_throttle(path) or "")

    def test_session_rest_and_assets_are_not_throttled(self):
        # The NAT "waiting room" rule: ordinary application traffic is unthrottled.
        for path in ("/carlos/ws/rs/schedule/x", "/carlos/loginResource/a.js",
                     "/carlos/provider/providercontrol", "/carlos/ws/servicesfoo"):
            with self.subTest(path=path):
                self.assertNotIn("limit_req ", route_throttle(path) or "")

    def test_services_zone_is_separate_from_credential_zones(self):
        body = route_throttle("/carlos/ws/services/x")
        self.assertNotIn("zone=carlos_login", body)
        self.assertNotIn("zone=carlos_wsauth", body)


if __name__ == "__main__":
    unittest.main()
