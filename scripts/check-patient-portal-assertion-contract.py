#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
# Licensed under the GNU General Public License, version 2 or later.
"""Check the Java golden assertions against the installed carlos-patient-portal package.

Run with that project's Python environment; no live server or credentials are used.
PortalStaffAssertionSignerUnitTest checks Java output against the same fixture.
"""

import base64
import json
from datetime import UTC, datetime
from pathlib import Path
import unittest
from unittest.mock import patch
from urllib.parse import urlsplit

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.serialization import Encoding, PublicFormat
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from carlos_patient_portal import staff_identity
from carlos_patient_portal.models import PatientPortalStaffAssertionUse


FIXTURE = json.loads(
    (Path(__file__).resolve().parents[1]
     / "src/test/resources/patientportal/assertion-contract.json").read_text(encoding="utf-8")
)


class PortalAssertionContract(unittest.TestCase):
    def setUp(self):
        self.clock = patch.object(staff_identity, "time", return_value=FIXTURE["issued_at"] + 1)
        self.clock.start()
        self.addCleanup(self.clock.stop)
        self.keys = {"primary": FIXTURE["public_key"]}

    def verify(self, vector, *, keys=None, request_hash=None):
        return staff_identity.verify_staff_assertion(
            self.keys if keys is None else keys,
            vector["assertion"],
            expected_request_hash=vector["request_hash"] if request_hash is None else request_hash,
            allow_legacy_unbound=False,
        )

    def test_accepts_java_vectors_with_production_verifier(self):
        for vector in FIXTURE["vectors"]:
            with self.subTest(method=vector["method"], uri=vector["uri"]):
                uri = urlsplit(vector["uri"])
                digest = staff_identity.staff_request_hash(
                    vector["method"], uri.path.encode(), uri.query.encode(), vector["body"].encode()
                )
                self.assertEqual(digest, vector["request_hash"])
                self.assertTrue(self.verify(vector, request_hash=digest).request_bound)

    def test_rejects_changed_method_patient_query_or_body(self):
        for vector in FIXTURE["vectors"]:
            uri = urlsplit(vector["uri"])
            original = [vector["method"], uri.path.encode(), uri.query.encode(), vector["body"].encode()]
            replacements = ["DELETE", b"/internal/carlos/patients/999/invites", b"limit=99", b'{"changed":true}']
            for index, replacement in enumerate(replacements):
                changed = list(original)
                changed[index] = replacement
                with self.subTest(component=index, uri=vector["uri"]):
                    with self.assertRaises(staff_identity.CarlosServiceAuthenticationError):
                        self.verify(vector, request_hash=staff_identity.staff_request_hash(*changed))

    def test_accepts_overlap_keyring_and_rejects_retired_or_wrong_key(self):
        vector = FIXTURE["vectors"][0]
        other = Ed25519PrivateKey.from_private_bytes(bytes([2]) * 32).public_key()
        other_encoded = base64.urlsafe_b64encode(
            other.public_bytes(Encoding.Raw, PublicFormat.Raw)
        ).rstrip(b"=").decode()
        self.assertTrue(self.verify(vector, keys={"old": other_encoded, **self.keys}).request_bound)
        for keys in ({"old": FIXTURE["public_key"]}, {"primary": other_encoded}):
            with self.assertRaises(staff_identity.CarlosServiceAuthenticationError):
                self.verify(vector, keys=keys)

    def test_rejects_replay_across_independent_database_sessions(self):
        engine = create_engine("sqlite://")
        self.addCleanup(engine.dispose)
        PatientPortalStaffAssertionUse.__table__.create(engine)
        principal = self.verify(FIXTURE["vectors"][0])
        now = datetime.fromtimestamp(FIXTURE["issued_at"] + 1, UTC)
        with patch.object(staff_identity, "utc_now", return_value=now):
            staff_identity.consume_staff_assertion(sessionmaker(engine), principal)
            with self.assertRaises(staff_identity.CarlosServiceAuthenticationError):
                staff_identity.consume_staff_assertion(sessionmaker(engine), principal)

    def test_rejects_expired_assertion(self):
        with patch.object(staff_identity, "time", return_value=FIXTURE["issued_at"] + 60):
            with self.assertRaises(staff_identity.CarlosServiceAuthenticationError):
                self.verify(FIXTURE["vectors"][0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
