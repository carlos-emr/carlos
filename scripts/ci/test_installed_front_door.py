# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import http.client
import time
import unittest
from unittest.mock import Mock, patch

import installed_front_door as front
from junit_evidence import EvidenceError

LOGIN = b'<form><input id="username"><input type="password"></form>'


class InstalledFrontDoorTest(unittest.TestCase):
    def response(self, body=LOGIN, status=200, **headers):
        response = Mock(status=status)
        response.read.return_value = body
        response.getheader.side_effect = lambda key, default=None: headers.get(key, default)
        return response

    def test_generic_success_or_truncated_login_cannot_establish_readiness(self):
        for response, expected in ((self.response(b'<html>starting</html>'), False),
                                   (self.response(status=503), False), (self.response(), True)):
            with patch.object(front.http.client, 'HTTPSConnection') as connection:
                connection.return_value.getresponse.return_value = response
                self.assertEqual(bool(front.login_response(time.monotonic() + 5, object())), expected)
                connection.return_value.close.assert_called_once()
        with patch.object(front.http.client, 'HTTPSConnection') as connection:
            connection.return_value.getresponse.return_value = self.response(**{'Content-Length': str(len(LOGIN) + 1)})
            with self.assertRaises(http.client.IncompleteRead):
                front.login_response(time.monotonic() + 5, object())
            connection.return_value.close.assert_called_once()

    def test_redirect_may_not_follow_another_host_or_downgrade_tls(self):
        for location in ('https://example.invalid/', 'http://127.0.0.1/carlos/',
                         'https://127.0.0.1:444/carlos/', 'https://user@127.0.0.1/carlos/'):
            with patch.object(front.http.client, 'HTTPSConnection') as connection:
                connection.return_value.getresponse.return_value = self.response(b'', 302, Location=location)
                with self.assertRaises(EvidenceError): front.login_response(time.monotonic() + 5, object())
                self.assertEqual(connection.call_count, 1)

    def test_two_complete_responses_are_required_and_certificate_errors_fail(self):
        with patch.object(front.ssl, 'create_default_context'), patch.object(front.time, 'sleep'), \
                patch.object(front, 'login_response', side_effect=[False, True, False, True, True]):
            self.assertEqual(front.wait_for_login()['attempts'], 5)
        with patch.object(front.ssl, 'create_default_context'), \
                patch.object(front, 'login_response', side_effect=front.ssl.SSLCertVerificationError('synthetic')):
            with self.assertRaisesRegex(EvidenceError, 'certificate'): front.wait_for_login()
