from pathlib import Path

PACKAGE_ROOT = Path(__file__).parents[1] / "carlos_patient_portal"


def test_reference_proxy_omits_raw_request_target_and_limits_expensive_routes() -> None:
    configuration = (PACKAGE_ROOT / "deploy" / "nginx.conf").read_text()

    assert "$request_uri" not in configuration
    assert "$uri" not in configuration
    assert "$args" not in configuration
    assert "access_log /var/log/nginx/patient-portal-access.log portal_safe" in configuration
    for route in (
        "/auth/login",
        "/auth/password-reset/request",
        "/auth/activate",
        "/auth/mfa/",
    ):
        assert route in configuration
    assert configuration.count("limit_req zone=") == 8
    assert "location ^~ /internal/carlos/" in configuration
    assert "allow 10.0.0.0/8" not in configuration
    assert "allow 127.0.0.1/32" in configuration
    assert "deny all" in configuration
    assert "proxy_set_header X-Forwarded-Proto $scheme" in configuration
    assert configuration.count('proxy_set_header X-CARLOS-Provider-ID ""') == 1
    assert configuration.count("proxy_set_header Host $host") == 2
    assert "location ^~ /patient/" in configuration
    assert "proxy_pass http://carlos_patient_portal/;" in configuration


def test_reference_proxy_restricts_every_internal_prefix_not_just_the_carlos_one() -> None:
    """The source-address restriction must cover /internal/** and be unreachable via /patient/.

    nginx matches prefix locations against the start of the URI, so `^~ /internal/carlos/` never
    matched `/patient/internal/carlos/...` and its `deny all` did not apply there. The trailing
    slash on the `/patient/` proxy_pass then stripped the prefix, handing the application the
    internal route with no source check. Separately, the probe and telemetry endpoints matched only
    `location /` and were served to the public internet.
    """
    configuration = (PACKAGE_ROOT / "deploy" / "nginx.conf").read_text()

    assert "location ^~ /internal/ {" in configuration
    assert "location ^~ /patient/internal/ {" in configuration
    # Both /internal/ and /internal/carlos/ carry their own allow + deny pair.
    # Directives only -- the surrounding comments mention the same words.
    assert configuration.count("allow 127.0.0.1/32;") == 2
    assert configuration.count("deny all;") == 2
    # The patient deployment prefix must refuse the internal API outright rather than proxy it.
    patient_internal_block = configuration.split("location ^~ /patient/internal/ {", 1)[1]
    patient_internal_block = patient_internal_block.split("}", 1)[0]
    assert "return 404;" in patient_internal_block
    assert "proxy_pass" not in patient_internal_block


def test_audit_role_policy_keeps_runtime_append_only_and_pruning_separate() -> None:
    policy = (PACKAGE_ROOT / "deploy" / "postgresql-audit-roles.sql").read_text()

    assert "ALTER TABLE public.patient_portal_audit_events OWNER TO" in policy
    assert "REVOKE UPDATE, DELETE, TRUNCATE" in policy
    assert "GRANT SELECT, INSERT ON public.patient_portal_audit_events" in policy
    assert "GRANT SELECT, DELETE ON public.patient_portal_audit_events" in policy
    assert "REVOKE ALL ON ALL TABLES IN SCHEMA public" in policy
