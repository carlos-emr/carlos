import json

import pytest

from carlos_patient_portal.config import (
    MIN_PRODUCTION_SECRET_LENGTH,
    parse_unlock_secret_keyring,
)


def test_parse_unlock_secret_keyring_normalizes_values() -> None:
    secret = "s" * MIN_PRODUCTION_SECRET_LENGTH

    assert parse_unlock_secret_keyring(json.dumps({" active ": f" {secret} "})) == {
        "active": secret
    }


@pytest.mark.parametrize(
    ("encoded_keyring", "expected_message"),
    [
        ("not-json", "must be a JSON object"),
        ("[]", "must be a non-empty JSON object"),
        ("{}", "must be a non-empty JSON object"),
        (
            json.dumps({"": "s" * MIN_PRODUCTION_SECRET_LENGTH}),
            "key IDs must contain 1 to 64 characters",
        ),
        (
            json.dumps({"active": "too-short"}),
            f"must be at least {MIN_PRODUCTION_SECRET_LENGTH} characters",
        ),
    ],
)
def test_parse_unlock_secret_keyring_rejects_invalid_configuration(
    encoded_keyring: str,
    expected_message: str,
) -> None:
    with pytest.raises(ValueError, match=expected_message):
        parse_unlock_secret_keyring(encoded_keyring)
