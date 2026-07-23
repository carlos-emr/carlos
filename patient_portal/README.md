# CARLOS Patient Portal

This directory contains the Python/FastAPI foundation for the CARLOS patient credential portal.

The first slice is intentionally small:

- FastAPI application factory.
- Pydantic settings with `PATIENT_PORTAL_` environment variables.
- SQLAlchemy session configuration.
- Alembic migration scaffold with the initial invite and account tables.
- Minimal public `/health` liveness endpoint.
- Internal `/internal/health/db` database readiness endpoint.
- Server-rendered responsive sign-in shell.
- Development-only staff invite API for creating, listing, resending, and revoking invites.
- Seven-day invite expiry metadata, refreshed on resend.
- Patient invite activation using invite code, email, date of birth, and HCN/HIN proof.
- Activation attempt throttling backed by portal audit events.
- Patient login with Argon2id password verification, MFA challenge/verify, opaque bearer sessions,
  logout, password reset, lockout, staff unlock, and forced reset after unlock.
- Authenticated dashboard shell with Account, Email passwords, and Help modules.
- Minimal FHIR R4 Patient and HL7 v2.5.1 patient-registration validation helpers for the MVP
  CARLOS integration contract.
- Basic tests for app wiring, template rendering, database readiness, invite lifecycle, and
  activation/auth behavior.

## Local Setup

```bash
cd patient_portal
python3 -m venv /tmp/carlos-patient-portal-venv
. /tmp/carlos-patient-portal-venv/bin/activate
pip install --require-hashes -r requirements.lock
pip install --no-build-isolation --no-deps -e .
```

The local lock file includes runtime, development, and build dependencies with package hashes.
Production deployments should install a prebuilt portal wheel with the runtime-only lock file:

```bash
pip install --require-hashes -r requirements-runtime.lock
pip install --no-deps dist/carlos_patient_portal-0.1.0-py3-none-any.whl
carlos-patient-portal-migrate
```

Refresh the lock files after dependency changes with:

```bash
pip-compile --generate-hashes --strip-extras \
  --output-file requirements-runtime.lock pyproject.toml
pip-compile --extra dev --all-build-deps --generate-hashes --allow-unsafe --strip-extras \
  --output-file requirements.lock pyproject.toml
```

## Run

```bash
cd patient_portal
export PATIENT_PORTAL_ENVIRONMENT=development
uvicorn carlos_patient_portal.main:create_app --factory --reload --host 127.0.0.1 --port 8090
```

Open `http://127.0.0.1:8090/`.

## Configuration

Settings use the `PATIENT_PORTAL_` environment prefix.

Common development variables:

```bash
export PATIENT_PORTAL_ENVIRONMENT=development
export PATIENT_PORTAL_ENABLE_DEV_ADMIN=true
export PATIENT_PORTAL_CLINIC_NAME="Maple Creek Medical"
export PATIENT_PORTAL_DATABASE_URL="postgresql+psycopg://localhost:5432/carlos_portal"
# Set PATIENT_PORTAL_DEV_ADMIN_TOKEN to a 32+ character random value before using
# the development invite API.
# Set PATIENT_PORTAL_IDENTITY_PROOF_SECRET to a 32+ character random value when
# seeded invites must survive app restarts.
# Set PATIENT_PORTAL_AUDIT_HASH_SECRET to a separate 32+ character random value
# when activation throttling/audit hashes must survive app restarts.
```

The default database URL targets local PostgreSQL because PostgreSQL is the intended MVP database.
Tests pass a SQLite database URL into the app factory so the foundation test suite does not require a
running PostgreSQL instance.

The portal defaults to `production`, so deployments fail closed unless required secrets are set.
Local development should explicitly set `PATIENT_PORTAL_ENVIRONMENT=development`.

`PATIENT_PORTAL_CLINIC_ID` defaults to `default`; set a stable clinic identifier before using a
shared or persistent database so CARLOS `demographic_no` values are scoped correctly.
If the same value is used in HL7 v2 messages, keep it to letters, numbers, dots, underscores, or
hyphens, and 20 characters or fewer.

Non-development deployments must set `PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN`,
`PATIENT_PORTAL_SESSION_SECRET`, `PATIENT_PORTAL_IDENTITY_PROOF_SECRET`, and
`PATIENT_PORTAL_AUDIT_HASH_SECRET`. The internal readiness endpoint expects the health token as a
Bearer token:

```bash
curl -H "Authorization: Bearer $PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN" \
  http://127.0.0.1:8090/internal/health/db
```

Expose `/internal/health/db` only to trusted infrastructure such as a load balancer or orchestrator
health probe.

Non-development secrets must be set explicitly and be at least 32 characters.
`PATIENT_PORTAL_ENVIRONMENT` accepts `development`, `staging`, `test`, or `production`; `dev` and
`prod` are normalized aliases.

The development invite API requires `PATIENT_PORTAL_DEV_ADMIN_TOKEN` when
`PATIENT_PORTAL_ENABLE_DEV_ADMIN=true`. Keep that token local to development machines and pass it as a
Bearer token.

Activation throttling defaults to 10 failed attempts per invite code and 50 failed attempts per client
within a one-hour window. The deployment can tune this with
`PATIENT_PORTAL_ACTIVATION_FAILURE_WINDOW_SECONDS`,
`PATIENT_PORTAL_ACTIVATION_MAX_FAILURES_PER_INVITE`, and
`PATIENT_PORTAL_ACTIVATION_MAX_FAILURES_PER_CLIENT`.

Authentication defaults:

- MFA is required by default and cannot be disabled in production.
- Password login locks the account after 50 failed password attempts.
- MFA verification locks the account after 10 failed code attempts.
- MFA codes expire after 10 minutes.
- Email MFA resend is limited to once per minute.
- SMS MFA resend is limited to once per five minutes.
- Patient sessions expire after 12 hours.
- Password reset tokens expire after one hour and are one-time use.

The deployment can tune these with `PATIENT_PORTAL_REQUIRE_MFA`,
`PATIENT_PORTAL_AUTH_MAX_FAILED_PASSWORD_ATTEMPTS`, `PATIENT_PORTAL_MFA_MAX_FAILED_ATTEMPTS`,
`PATIENT_PORTAL_SESSION_TTL_SECONDS`, `PATIENT_PORTAL_MFA_CODE_TTL_SECONDS`,
`PATIENT_PORTAL_MFA_EMAIL_RESEND_COOLDOWN_SECONDS`,
`PATIENT_PORTAL_MFA_SMS_RESEND_COOLDOWN_SECONDS`, and
`PATIENT_PORTAL_PASSWORD_RESET_TOKEN_TTL_SECONDS`.

By default, client throttling uses the direct peer address reported by the ASGI server. If the portal
runs behind a trusted proxy that strips and sets forwarding headers, set
`PATIENT_PORTAL_TRUSTED_CLIENT_IP_HEADER` to `x-forwarded-for` or `x-real-ip`. Do not enable this for
untrusted direct internet traffic, because clients can spoof those headers unless a trusted upstream
controls them.

## Migrations

```bash
cd patient_portal
alembic -c alembic.ini upgrade head
```

Installed wheel deployments can run packaged migrations without a source checkout:

```bash
carlos-patient-portal-migrate
```

This PR adds the portal foundation, initial staff invite table, initial patient account table, and
initial audit event table. It also adds portal-owned session, MFA challenge, and password reset token
tables. Unlock-secret tables should be added in later vertical slices.

## Development Invite API

The staff invite skeleton is available only when `PATIENT_PORTAL_ENVIRONMENT=development` and
`PATIENT_PORTAL_ENABLE_DEV_ADMIN=true`. It also requires a development-only Bearer token. It is
intentionally hidden outside development until real CARLOS staff authentication is wired in.

```bash
curl -X POST http://127.0.0.1:8090/dev/admin/invites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -H "X-CARLOS-Staff-Actor: Dr example" \
  -d '{
    "demographic_no": 1234,
    "email": "example.patient@example.com",
    "date_of_birth": "1980-05-20",
    "health_card_number": "ABCD 1234-5678"
  }'

curl -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -H "X-CARLOS-Staff-Actor: Dr example" \
  http://127.0.0.1:8090/dev/admin/invites?demographic_no=1234

curl -X POST http://127.0.0.1:8090/dev/admin/invites/1/resend \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -H "X-CARLOS-Staff-Actor: Dr example"

curl -X POST http://127.0.0.1:8090/dev/admin/invites/1/revoke \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -H "X-CARLOS-Staff-Actor: Dr example"

curl -X POST http://127.0.0.1:8090/dev/admin/accounts/1/unlock \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -H "X-CARLOS-Staff-Actor: Dr example"
```

Invite tokens are shown only on create/resend responses. The database stores only the token hash.
When identity proof is supplied, the database stores only per-invite salted keyed hashes of email,
date of birth, and HCN/HIN values. Invites carry a seven-day `expires_at` timestamp so the activation
endpoint has a clear server-side expiry boundary. Invite list responses default to 10 records and are
capped at 100 records per request.

The current development API requires email, date of birth, and HCN/HIN at invite creation time so it
cannot create invites that patients are unable to activate. The future CARLOS-backed staff action
should populate those proof hashes from CARLOS demographics instead of staff-entered JSON fields.
The development API derives the staff actor from `X-CARLOS-Staff-Actor`; the production CARLOS
integration should derive it from authenticated CARLOS provider context instead of client JSON.
Creating a new pending invite for the same patient revokes older pending invites. Identity proof
validation happens before older invites are revoked, so invalid replacement attempts leave the
current pending invite usable. Creating an invite after the patient already has a portal account
returns a conflict. Staff create, list, resend, and revoke actions write audit events.
The development unlock endpoint clears lockout counters, revokes active sessions/MFA challenges, and
sets `force_password_reset=true`; the patient must then complete the reset flow before sign-in.

## Patient Activation API

```bash
curl -X POST http://127.0.0.1:8090/auth/activate \
  -H "Content-Type: application/json" \
  -d '{
    "invite_code": "<invite_token>",
    "email": "example.patient@example.com",
    "date_of_birth": "1980-05-20",
    "health_card_number": "ABCD-1234 5678",
    "username": "patient.username",
    "password": "Stronger1!word"
  }'
```

Activation checks the invite code, email, date of birth, and HCN/HIN together and returns a generic
failure when they do not match. Usernames are normalized to lowercase and must be unique. Passwords
are hashed with Argon2id before storage.

Activation requests must use `application/json` and are capped at 16 KiB before validation. Failed
activation attempts are audited and rate-limited without storing raw HCN/HIN, date-of-birth, or raw
client address values. Client address hashes use `PATIENT_PORTAL_AUDIT_HASH_SECRET`.

## Patient Auth API

Login accepts either the server-rendered form with CSRF or JSON:

```bash
curl -X POST http://127.0.0.1:8090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "patient.username", "password": "Stronger1!word"}'
```

When MFA is required, login returns an opaque `mfa_challenge_token`. Verify the emailed or texted code
to create a session:

```bash
curl -X POST http://127.0.0.1:8090/auth/mfa/verify \
  -H "Content-Type: application/json" \
  -d '{"mfa_challenge_token": "<challenge>", "code": "123456"}'

curl -H "Authorization: Bearer <session_token>" \
  http://127.0.0.1:8090/auth/session

curl -X POST -H "Authorization: Bearer <session_token>" \
  http://127.0.0.1:8090/auth/logout
```

MFA resend supports switching between `email` and `sms` when the account has the selected channel:

```bash
curl -X POST http://127.0.0.1:8090/auth/mfa/resend \
  -H "Content-Type: application/json" \
  -d '{"mfa_challenge_token": "<challenge>", "mfa_delivery_method": "sms"}'
```

Password reset uses a generic request response and a one-time token:

```bash
curl -X POST http://127.0.0.1:8090/auth/password-reset/request \
  -H "Content-Type: application/json" \
  -d '{"username": "patient.username", "email": "example.patient@example.com"}'

curl -X POST http://127.0.0.1:8090/auth/password-reset/complete \
  -H "Content-Type: application/json" \
  -d '{"reset_token": "<reset_token>", "new_password": "Changed1!word"}'
```

Development responses include `development_mfa_code` and `development_reset_token` only when needed
for local testing. Production responses do not expose raw MFA codes or reset tokens; real delivery
integration should send those values through the configured email/SMS provider without storing them.
The database stores keyed hashes of MFA codes, reset tokens, and session tokens. Sign-in, MFA,
reset, lockout, unlock, and logout write audit events.

Successful login/MFA responses also set an HttpOnly portal session cookie scoped to `/portal` so the
server-rendered dashboard can be used without putting bearer tokens in page scripts. API clients may
still use the returned bearer `session_token`.

## Patient Dashboard

```bash
curl -H "Cookie: carlos_portal_session=<session_token>" \
  http://127.0.0.1:8090/portal
```

Dashboard routes:

- `/portal` and `/portal/account` show the Account module shell.
- `/portal/email-passwords` shows the Email passwords module shell with an empty table until the
  unlock-secret slice is implemented.
- `/portal/help` shows clinic help details.
- `POST /portal/logout` clears the portal session cookie and writes a logout audit event.

The dashboard is server-rendered and responsive. Desktop uses a left module rail; mobile uses a
horizontal module bar with logout kept in the top-right header area.

## Interoperability Contract

The MVP interoperability scope is intentionally narrow: this package validates the patient identity
data shape this portal slice owns against concrete FHIR and HL7 targets without claiming a complete
general-purpose exchange server:

- FHIR target: R4 `Patient`, using `fhir.resources==5.1.1`.
- HL7 v2 target: v2.5.1 ADT A04 patient-registration trigger using HL7apy validation. The message
  emits CARLOS demographic number and HCN/HIN as repeated `PID-3` identifiers and emits email using
  `PID-13` XTN email components.

Future CARLOS integration work should reuse this module or replace it with a stricter CARLOS profile
before exposing additional clinical exchange endpoints.

## Tests

```bash
cd patient_portal
pytest
ruff check .
```
