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
- Basic tests for app wiring, template rendering, database readiness, invite lifecycle, and
  activation behavior.

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
```

The default database URL targets local PostgreSQL because PostgreSQL is the intended MVP database.
Tests pass a SQLite database URL into the app factory so the foundation test suite does not require a
running PostgreSQL instance.

The portal defaults to `production`, so deployments fail closed unless required secrets are set.
Local development should explicitly set `PATIENT_PORTAL_ENVIRONMENT=development`.

`PATIENT_PORTAL_CLINIC_ID` defaults to `default`; set a stable clinic identifier before using a
shared or persistent database so CARLOS `demographic_no` values are scoped correctly.

Non-development deployments must set `PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN`,
`PATIENT_PORTAL_SESSION_SECRET`, and `PATIENT_PORTAL_IDENTITY_PROOF_SECRET`. The internal readiness
endpoint expects the health token as a Bearer token:

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
initial audit event table.
Membership and unlock-secret tables should be added in later vertical slices.

## Development Invite API

The staff invite skeleton is available only when `PATIENT_PORTAL_ENVIRONMENT=development` and
`PATIENT_PORTAL_ENABLE_DEV_ADMIN=true`. It also requires a development-only Bearer token. It is
intentionally hidden outside development until real CARLOS staff authentication is wired in.

```bash
curl -X POST http://127.0.0.1:8090/dev/admin/invites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -d '{
    "demographic_no": 1234,
    "actor": "Dr example",
    "email": "example.patient@example.com",
    "date_of_birth": "1980-05-20",
    "health_card_number": "ABCD 1234-5678"
  }'

curl -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  http://127.0.0.1:8090/dev/admin/invites?demographic_no=1234

curl -X POST http://127.0.0.1:8090/dev/admin/invites/1/resend \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -d '{"actor":"Dr example"}'

curl -X POST http://127.0.0.1:8090/dev/admin/invites/1/revoke \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -d '{"actor":"Dr example"}'
```

Invite tokens are shown only on create/resend responses. The database stores only the token hash.
When identity proof is supplied, the database stores only keyed hashes of email, date of birth, and
HCN/HIN values. Invites carry a seven-day `expires_at` timestamp so the activation endpoint has a
clear server-side expiry boundary. Invite list responses default to 10 records and are capped at 100
records per request.

The current development API requires email, date of birth, and HCN/HIN at invite creation time so it
cannot create invites that patients are unable to activate. The future CARLOS-backed staff action
should populate those proof hashes from CARLOS demographics instead of staff-entered JSON fields.

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
activation attempts are audited and rate-limited without storing raw HCN/HIN or date-of-birth values.

## Tests

```bash
cd patient_portal
pytest
ruff check .
```
