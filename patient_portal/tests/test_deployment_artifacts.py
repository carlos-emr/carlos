from pathlib import Path

PACKAGE_ROOT = Path(__file__).parents[1] / "carlos_patient_portal"


def test_reference_proxy_omits_raw_request_target_and_limits_expensive_routes() -> None:
    configuration = (PACKAGE_ROOT / "deploy" / "nginx.conf").read_text()

    # The property being guarded is that the *access log* carries no raw request target, so the
    # assertion is scoped to the log_format directive. A file-wide ban also caught the port-80
    # redirect, where echoing the path back to the patient's own browser is both correct and
    # necessary - and banning it there would have meant dropping the path on redirect.
    log_format_block = configuration.split("limit_req_zone", 1)[0]
    assert "$request_uri" not in log_format_block
    assert "$uri" not in log_format_block
    assert "$args" not in log_format_block
    safe_access_log = "access_log /var/log/nginx/patient-portal-access.log portal_safe"
    assert configuration.count(safe_access_log) == 2
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


def test_reference_proxy_sets_forwarded_for_in_every_block_that_breaks_inheritance() -> None:
    """The proxy must write X-Forwarded-For rather than pass a client-supplied one through.

    parse_trusted_client_ip_header gates on the *peer* being a trusted proxy, which this nginx
    always is, then walks the chain right-to-left. That is only sound if the proxy appends the real
    peer. Forwarding the client's header untouched makes every client-keyed throttle and every
    recorded source address attacker-chosen.

    Asserted twice because defining any proxy_set_header inside a location disables inheritance of
    the whole server-level set, so the /internal/carlos/ block needs its own copy.
    """
    configuration = (PACKAGE_ROOT / "deploy" / "nginx.conf").read_text()

    assert configuration.count("proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;") == 2
    # Never the bare client value, which is the spoofable form.
    assert "proxy_set_header X-Forwarded-For $http_x_forwarded_for" not in configuration

    internal_block = configuration.split("location ^~ /internal/carlos/ {", 1)[1]
    internal_block = internal_block.split("\n  }", 1)[0]
    assert "proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;" in internal_block


def test_audit_role_policy_keeps_runtime_append_only_and_pruning_separate() -> None:
    policy = (PACKAGE_ROOT / "deploy" / "postgresql-audit-roles.sql").read_text()

    assert "ALTER TABLE public.patient_portal_audit_events OWNER TO" in policy
    assert "REVOKE UPDATE, DELETE, TRUNCATE" in policy
    assert "GRANT SELECT, INSERT ON public.patient_portal_audit_events" in policy
    assert "GRANT SELECT, DELETE ON public.patient_portal_audit_events" in policy
    assert "REVOKE ALL ON ALL TABLES IN SCHEMA public" in policy


def test_reference_proxy_terminates_tls_and_bounds_request_resources() -> None:
    """An operator copying this file must get a working, PHI-appropriate TLS edge.

    `listen 443 ssl` shipped with no certificate, no TLS floor and no port-80 listener, so
    `nginx -t` failed with "no ssl_certificate is defined for the listen ... ssl directive"
    and the operator improvised TLS from scratch for a PHIPA-regulated service. The header
    comment described upstreams, CIDRs and rates but never mentioned certificates.
    """
    configuration = (PACKAGE_ROOT / "deploy" / "nginx.conf").read_text()

    assert "ssl_certificate " in configuration
    assert "ssl_certificate_key " in configuration
    assert "ssl_protocols TLSv1.2 TLSv1.3;" in configuration
    assert "TLSv1.1" not in configuration
    assert "TLSv1;" not in configuration
    assert "listen 80;" in configuration
    assert "return 301 https://" in configuration

    # HSTS stays the application's job; duplicating it here is what the review warned against.
    assert "Strict-Transport-Security" not in configuration

    for bound in (
        "client_max_body_size",
        "client_body_timeout",
        "client_header_timeout",
        "proxy_connect_timeout",
        "proxy_send_timeout",
        "proxy_read_timeout",
        "limit_conn portal_conn",
    ):
        assert bound in configuration, f"{bound} left at the nginx default"

    # limit_conn_zone is http-context; declaring it inside server{} does not load.
    zone_line = "limit_conn_zone $binary_remote_addr zone=portal_conn:10m;"
    assert zone_line in configuration
    assert configuration.index(zone_line) < configuration.index("server {")
