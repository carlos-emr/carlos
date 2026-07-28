# CARLOS Patient Portal

This directory contains the Python/FastAPI foundation for the CARLOS patient credential portal.

The MVP foundation currently includes:

- FastAPI application factory.
- Pydantic settings with `PATIENT_PORTAL_` environment variables.
- SQLAlchemy session configuration.
- Alembic migration scaffold with the initial invite and account tables.
- Minimal public `/health` liveness endpoint.
- Internal `/internal/health/db` database readiness endpoint.
- Server-rendered responsive sign-in, activation, password-reset, lockout, and MFA screens.
- Authenticated CARLOS internal API for invites, unlock, contact review, and unlock-secret
  create/revoke operations, plus a development-only staff API for local testing.
- Seven-day invite expiry metadata; resend supersedes the old issuance and creates a linked record.
- Patient invite activation using invite code, email, date of birth, and HCN/HIN proof.
- Activation attempt throttling backed by portal audit events and PostgreSQL transaction locks.
- Patient login with Argon2id password verification, MFA challenge/verify, opaque bearer sessions,
  logout, password reset, lockout, staff unlock, and forced reset after unlock.
- Authenticated dashboard landing page with Account, Email passwords, and Help modules plus disabled
  placeholders for future Documents and Messages modules.
- Encrypted unlock-secret storage service for generated passphrases used by CARLOS encrypted email
  attachments.
- Pilot hardening hooks for readiness checks, maintenance mode, coarse request throttling, audit
  retention pruning, and local SQLite backup/restore drills.
- Patient-scoped FHIR R4 metadata/read/search endpoints with bounded paging and access auditing,
  plus an HL7 v2.5.1 patient-registration conformance artifact.
- Tests for app wiring, template rendering, database readiness, invite lifecycle,
  activation/auth/unlock-secret behavior, scoped patient APIs, FHIR/HL7 artifacts, PostgreSQL
  behavior, desktop/mobile Playwright workflows, and pilot hardening hooks.

## Current MVP Status

| Slice | Portal status | Remaining integration |
| --- | --- | --- |
| Foundation and activation | Portal implementation and tests present | CARLOS invite delivery and activation-link UI are not wired |
| Authentication and MFA | Portal implementation and account-scoped controls present | Durable reset delivery and shared edge throttling are pilot blockers |
| Dashboard and email passwords | Portal implementation and responsive tests present | CARLOS must confirm successful message delivery before publishing each password |
| Account settings | Portal contact changes are immediate; immutable sync reviews are queued | CARLOS must update eChart then idempotently confirm the reviewed revision |
| FHIR R4 and HL7 v2.5.1 | Scoped resources and conformance artifacts are validator-tested | This is a narrow portal profile, not a general-purpose exchange server |
| Pilot hardening | In-process controls and operator commands are present | External monitoring, audit export, managed backups, and restore drills remain required |

The current MVP deliberately uses one isolated portal deployment, database, origin, and configured
clinic identity per clinic. It is not a shared multi-clinic identity service. The portal-side API
contract is deployable only after the CARLOS Java application is wired to it.
Green portal CI does not by itself prove that staff can reach these actions from CARLOS.

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

CI installs both lock files with hash verification and audits their exact versions against the
Python Packaging Advisory Database. Keep both generated locks in the same dependency-change commit.

### Dependency decisions

- `fhir.resources` remains pinned to 5.1.1 because it models FHIR R4 4.0.1. Newer major versions
  provide R5 and R4B rather than exact R4. Portal FHIR routes are output-only, and CI validates
  generated resources with the official FHIR validator rather than treating this library as the
  conformance authority. Reassess the model library before accepting inbound FHIR resources.
- The runtime installs base `uvicorn` only. Development installs `uvicorn[standard]` for reload and
  optional local performance support without shipping WebSocket and file-watcher dependencies in
  production.
- The MVP uses `psycopg[binary]` because there is not yet a managed production portal image. Its
  bundled client libraries are patched by updating the locked Psycopg package and rebuilding the
  deployment. When a production image is defined, evaluate `psycopg[c]` linked to image-managed
  `libpq` and `libssl`.
- The CI PostgreSQL service uses a digest-pinned `postgres:16` image. Update the tag and digest
  together after reviewing upstream PostgreSQL image changes.

## Run

```bash
cd patient_portal
export PATIENT_PORTAL_ENVIRONMENT=development
uvicorn carlos_patient_portal.main:create_app --factory --reload --host 127.0.0.1 --port 8090
```

Open `http://127.0.0.1:8090/`.

Production launchers must disable Uvicorn's raw request-target access log; the application emits
its own route-template log:

```bash
uvicorn carlos_patient_portal.main:create_app --factory --no-access-log
```

## Configuration

Settings use the `PATIENT_PORTAL_` environment prefix.

Common development variables:

```bash
export PATIENT_PORTAL_ENVIRONMENT=development
export PATIENT_PORTAL_ENABLE_DEV_ADMIN=true
export PATIENT_PORTAL_CLINIC_NAME="Maple Creek Medical"
export PATIENT_PORTAL_PUBLIC_BASE_URL="http://127.0.0.1:8090"
export PATIENT_PORTAL_DATABASE_URL="postgresql+psycopg://localhost:5432/carlos_portal"
# The development Postfix capture service listens locally without TLS or authentication.
export PATIENT_PORTAL_SMTP_HOST=127.0.0.1
export PATIENT_PORTAL_SMTP_PORT=25
# Set PATIENT_PORTAL_DEV_ADMIN_TOKEN to a 32+ character random value before using
# the development invite API.
# Set PATIENT_PORTAL_IDENTITY_PROOF_SECRET to a 32+ character random value when
# seeded invites must survive app restarts.
# Set PATIENT_PORTAL_AUDIT_HASH_SECRET to a separate 32+ character random value
# when activation throttling/audit hashes must survive app restarts.
# Set PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET to a separate 32+ character
# random value when encrypted unlock secrets must survive app restarts.
```

The default database URL targets local PostgreSQL because PostgreSQL is the intended MVP database.
Most unit tests use SQLite; CI also runs security-critical concurrency and transaction behavior
against PostgreSQL 16. File-backed SQLite is supported for local development, not a shared pilot.
Production startup rejects SQLite and every database driver except `postgresql+psycopg`.
Database pool, connect, checkout, statement, and lock timeouts have explicit configuration fields.
Remote production PostgreSQL URLs must use `sslmode=verify-full`; deliver database credentials and
CA material through the deployment secret manager rather than committed URLs.

The portal defaults to `production`, so deployments fail closed unless required secrets are set.
Local development should explicitly set `PATIENT_PORTAL_ENVIRONMENT=development`.

Development SMTP defaults to `carlos-test@openo-dev.local`; override it with
`PATIENT_PORTAL_SMTP_FROM_ADDRESS` when needed. A sender address is always required outside
development. SMTP is required in production, and every non-development SMTP connection must enable
`PATIENT_PORTAL_SMTP_STARTTLS`. Relays can set
`PATIENT_PORTAL_SMTP_USERNAME` and `PATIENT_PORTAL_SMTP_PASSWORD`; the username and password must be
configured together. `PATIENT_PORTAL_SMTP_TIMEOUT_SECONDS` defaults to 10 seconds. MFA email bodies
contain only the verification code, expiry, service name, and clinic contact direction. They do not
include patient names or clinical information.
Outbound email and SMS wording is isolated in `outbound_messages.py`; English is the only enabled
outbound locale until account locale persistence and reviewed translations are added.

`PATIENT_PORTAL_PUBLIC_BASE_URL` is used to build password-reset email links and is required when
SMTP is configured outside development. It must use HTTPS outside development. Reset tokens are put
in the link fragment so they are not sent in the initial HTTP request or written to access logs.
Matched password-reset requests are limited to one email per account per minute by default; tune
this with `PATIENT_PORTAL_PASSWORD_RESET_REQUEST_COOLDOWN_SECONDS`.
Production delivery runs after the uniform HTTP response so SMTP latency does not disclose whether
the submitted identity matched an account. That handoff currently uses an in-process
`BackgroundTasks` callback and is not durable across worker loss. A production pilot must replace
it with a transactional outbox/queue with idempotent retry; until then, reset delivery remains a
documented pilot blocker.

Production SMS uses an authenticated HTTPS JSON webhook configured with
`PATIENT_PORTAL_SMS_WEBHOOK_URL` and `PATIENT_PORTAL_SMS_WEBHOOK_TOKEN`. The provider adapter receives
only the normalized destination, code, expiry, sender ID, and message purpose. Production startup
fails closed when SMS delivery is not configured.

The development container's capture-only Postfix setup stores messages locally instead of relaying
them externally. Use `/scripts/mail list` and `/scripts/mail read latest` to inspect captured MFA
and password-reset messages during testing.

Run the repeatable live browser smoke test from the repository root after seeding the development
account and starting the portal:

```bash
npm run test:patient-portal-playwright
```

The test clears the local capture inbox, signs in as the configurable development patient, retrieves
the MFA code through `/scripts/mail`, verifies the dashboard on desktop and mobile viewports, and
logs out. Override `PORTAL_BASE_URL`, `PORTAL_TEST_USER`, `PORTAL_TEST_PASSWORD`,
`PORTAL_MAIL_COMMAND`, or `PORTAL_SCREENSHOT_DIR` when the local setup differs from the defaults.
The test refuses public hosts unless `ALLOW_NON_LOCAL_BASE_URL=true` is deliberately set.

`PATIENT_PORTAL_CLINIC_ID` and `PATIENT_PORTAL_CLINIC_NAME` have development placeholders.
Non-development startup rejects those placeholders. The configured clinic is enforced on login
and on every CARLOS internal request; a request asserting another clinic is rejected.
`PATIENT_PORTAL_CLINIC_TIMEZONE` defaults to `America/Toronto`. Set the clinic's IANA timezone so
dashboard timestamps and date filters use the clinic's local calendar day; FHIR instants and
database storage remain UTC.
If the same value is used in HL7 v2 messages, keep it to letters, numbers, dots, underscores, or
hyphens, and 20 characters or fewer.

Non-development deployments must set `PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN`,
`PATIENT_PORTAL_SESSION_SECRET`, `PATIENT_PORTAL_IDENTITY_PROOF_SECRET`,
`PATIENT_PORTAL_AUDIT_HASH_SECRET`, `PATIENT_PORTAL_INTERNAL_API_TOKEN`, SMTP, SMS, and either
`PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET` or
`PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_KEYRING`.
The internal readiness endpoint expects the health token as a Bearer token:

```bash
curl -H "Authorization: Bearer $PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN" \
  http://127.0.0.1:8090/internal/health/db

curl -H "Authorization: Bearer $PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN" \
  http://127.0.0.1:8090/internal/readiness

curl -H "Authorization: Bearer $PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN" \
  http://127.0.0.1:8090/internal/metrics
```

Expose `/internal/health/db` and `/internal/readiness` only to trusted infrastructure such as a load
balancer or orchestrator health probe.

Non-development secrets must be set explicitly, be at least 32 characters, and use distinct values
for each authentication, identity-proof, audit, health, internal API, SMS, and encryption domain.
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
- Password login and account-setting step-up lock the account after 10 failed password attempts.
- MFA verification locks the account after 10 failed code attempts.
- MFA failures and delivery cooldowns are account-scoped across replacement challenges.
- MFA codes expire after 10 minutes.
- Email MFA resend is limited to once per minute.
- SMS MFA is available when the account has a valid phone number and the webhook sender is
  configured; delivery failures are audited and do not silently invalidate another method.
- Patient sessions have a one-hour absolute lifetime and expire after 10 minutes of inactivity.
- Password reset tokens expire after one hour and are one-time use.

The deployment can tune these with `PATIENT_PORTAL_REQUIRE_MFA`,
`PATIENT_PORTAL_AUTH_MAX_FAILED_PASSWORD_ATTEMPTS`, `PATIENT_PORTAL_MFA_MAX_FAILED_ATTEMPTS`,
`PATIENT_PORTAL_SESSION_TTL_SECONDS`, `PATIENT_PORTAL_SESSION_IDLE_TIMEOUT_SECONDS`,
`PATIENT_PORTAL_MFA_CODE_TTL_SECONDS`,
`PATIENT_PORTAL_MFA_EMAIL_RESEND_COOLDOWN_SECONDS`,
`PATIENT_PORTAL_MFA_SMS_RESEND_COOLDOWN_SECONDS`, and
`PATIENT_PORTAL_PASSWORD_RESET_TOKEN_TTL_SECONDS`.

By default, client throttling uses the direct peer address reported by the ASGI server. If the portal
runs behind a trusted proxy, set `PATIENT_PORTAL_TRUSTED_CLIENT_IP_HEADER` to `x-forwarded-for` or
`x-real-ip` and set `PATIENT_PORTAL_TRUSTED_PROXY_CIDRS` to the comma-separated CIDRs of proxies that
may supply that header. Forwarded values are ignored unless the direct peer is in that allowlist.
For `X-Forwarded-For`, the portal walks the chain from the right and uses the first untrusted hop.

Pilot hardening defaults:

- Coarse per-process request throttling allows 300 patient-facing requests per client per minute,
  while login is limited to 10 attempts per client per minute before Argon2 work is scheduled.
  Tune with `PATIENT_PORTAL_GLOBAL_RATE_LIMIT_WINDOW_SECONDS`,
  `PATIENT_PORTAL_GLOBAL_RATE_LIMIT_MAX_REQUESTS`,
  `PATIENT_PORTAL_AUTH_RATE_LIMIT_WINDOW_SECONDS`, and
  `PATIENT_PORTAL_AUTH_RATE_LIMIT_MAX_REQUESTS`. The bucket cache is bounded by
  `PATIENT_PORTAL_RATE_LIMIT_MAX_BUCKETS`. Keep shared edge/load-balancer throttles in front of
  every production deployment because worker-local counters are not distributed.
- `PATIENT_PORTAL_MAINTENANCE_MODE=true` returns `503` and `Retry-After` on patient-facing routes
  while keeping `/health`, `/internal/health/db`, and `/internal/readiness` available. Tune the
  retry hint with `PATIENT_PORTAL_MAINTENANCE_RETRY_AFTER_SECONDS`.
- `PATIENT_PORTAL_AUDIT_RETENTION_DAYS` defaults to a conservative 9,150 days and cannot be
  configured lower. This guarantees at least 25 complete calendar years, including leap years.
  Audit pruning is explicit so clinics can align the job with legal-retention processes.
- Requests emit PHI-safe structured log records containing a generated/canonical request ID,
  route template, method, status, and duration. `/internal/metrics` exposes aggregate status-class
  and delivery-failure counters without patient identifiers.
- Non-development application startup disables Uvicorn's raw access logger. Production proxies must
  also avoid raw request targets and patient-bearing paths: log only a normalized route/location,
  method, status, request ID, and timing. Never log query strings. Keep Uvicorn `--no-access-log`
  in the production launch command as defense in depth.

Minimum pilot alerts:

- Page the service owner when readiness is unavailable for 5 minutes or the schema is not current.
- Page on any sustained unlock-secret decryption failure or backup/restore failure.
- Alert on a 5xx rate above 1% for 5 minutes, repeated database 503s, or MFA/reset delivery failures.
- Alert security staff on an unusual increase in account lockouts or rate-limit responses.
- Ship application logs and audit-event exports to access-controlled, append-only centralized
  storage, with request IDs preserved for correlation.

The clinic/deployment operator owns SMTP/SMS delivery, database and backup alerts, restore drills,
and incident response. CARLOS maintainers own application regression alerts and migration
compatibility. Runbooks must identify both contacts before pilot traffic is enabled.

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
initial audit event table. It also adds portal-owned session, MFA challenge, password reset token,
and encrypted unlock-secret tables.

Migration `0003_portal_lifecycle_hardening` refuses to downgrade while v3-only encrypted records,
pending secrets, disabled accounts, superseded reviews, or v3 audit events exist. This prevents a
rollback from dropping encryption context or lifecycle evidence. Preserve or transform those rows
under an approved retention and key-management procedure before retrying a downgrade.
Migration `0002_staff_identity_audit` preflights FHIR audit events before changing schema, and
`0004_invite_issuance_history` similarly refuses to discard superseded invite history.

## Pilot Operations

Run audit retention pruning from an installed wheel:

```bash
carlos-patient-portal-maintenance prune-audit --dry-run
carlos-patient-portal-maintenance prune-audit --batch-size 1000
carlos-patient-portal-maintenance cleanup-transient-auth --dry-run
carlos-patient-portal-maintenance cleanup-transient-auth --retention-days 30
```

The built-in backup/restore helper is intentionally limited to file-backed SQLite databases for
local development and small pilot recovery drills:

```bash
carlos-patient-portal-maintenance backup-sqlite --output /secure/backups/portal.db
carlos-patient-portal-maintenance restore-sqlite --input /secure/backups/portal.db --overwrite
```

SQLite backup and restore are development-only. Stop every portal process and SQLite client before
restore. The restore command rejects an existing
destination `-wal` or `-shm` sidecar rather than risking a false-success restore over live WAL state;
checkpoint and close the database cleanly, verify the sidecars are gone, run restore, then restart
the application and readiness checks.

PostgreSQL deployments should use managed database snapshots, PITR, or `pg_dump`/`pg_restore` from
the deployment platform. Before a pilot, run and document at least one restore drill against a
non-production database.

Keep `PATIENT_PORTAL_SESSION_SECRET`, `PATIENT_PORTAL_IDENTITY_PROOF_SECRET`,
`PATIENT_PORTAL_AUDIT_HASH_SECRET`, and unlock-secret encryption keys as separate random values in
the deployment secret manager. For rotation, configure
`PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_KEYRING` as a JSON object containing the old and new keys,
set `PATIENT_PORTAL_UNLOCK_SECRET_ACTIVE_KEY_ID` to the new key, and run:

```bash
carlos-patient-portal-maintenance rotate-unlock-secrets --batch-size 100
```

Repeat until the command reports zero, verify reads and a backup, then retire the old key. New writes
always use the active key; reads select the retained key by each record's `encryption_key_id`.
The rotation command also upgrades legacy unlock-secret ciphertext to the record-bound v2
encryption context.

The remaining secrets have deliberately different rotation behavior:

- Rotating `PATIENT_PORTAL_SESSION_SECRET` invalidates all sessions, MFA challenges, reset tokens,
  and CSRF tokens. Schedule it as a patient sign-out event.
- Rotating `PATIENT_PORTAL_IDENTITY_PROOF_SECRET` invalidates pending invitations; reissue them
  after cutover.
- Rotating `PATIENT_PORTAL_AUDIT_HASH_SECRET` changes pseudonymous client/invite correlations;
  record the cutover time for investigations.
- Rotating `PATIENT_PORTAL_INTERNAL_API_TOKEN` requires a coordinated CARLOS/portal cutover.

## CARLOS Internal API

Set `PATIENT_PORTAL_INTERNAL_API_TOKEN` to enable the production staff/service contract. Requests
must include its Bearer token and CARLOS-authenticated `X-CARLOS-Provider-ID`,
`X-CARLOS-Provider-Name`, `X-CARLOS-Clinic-ID`, and `X-CARLOS-Permissions` headers. The reverse
proxy must strip externally supplied copies of these headers and allow this route family only from
CARLOS application instances.

Permissions are deliberately narrow:

- `portal.invite.manage`: create, list, resend, and revoke clinic-scoped invites.
- `portal.account.unlock`: unlock a patient and require a fresh password reset.
- `portal.account.manage`: read portal status and disable/re-enable patient access.
- `portal.secret.manage`: idempotently create, publish, and revoke generated email passphrases.
- `portal.contact.review`: list and approve/reject pending patient contact changes.

The service token authenticates CARLOS itself; provider ID and permissions must be derived from the
authenticated CARLOS session by the CARLOS server, never accepted from a browser. Every mutation
retains the stable provider ID, display snapshot, clinic scope, and target resource in the audit
trail. A duplicate unlock-secret `source_reference` returns the original record and plaintext
through the same audited disclosure path, so a CARLOS retry after a timeout is safe; a revoked
source reference returns a conflict.
The generated OpenAPI contract includes explicit request, response, pagination, one-time plaintext,
and error models for these routes.

Invite retention is state-specific: accepted invite records are retained with the long-term audit
record; expired pending, revoked, and superseded records are eligible for transient cleanup only
after the configured cleanup delay. Cleanup rechecks status and expiry in the delete statement so a
concurrent state transition cannot delete a renewed record.

## Development Invite API

The staff invite skeleton is available only when `PATIENT_PORTAL_ENVIRONMENT=development` and
`PATIENT_PORTAL_ENABLE_DEV_ADMIN=true`. It also requires a development-only Bearer token. It is
intentionally hidden outside development until real CARLOS staff authentication is wired in.

```bash
curl -X POST http://127.0.0.1:8090/dev/admin/invites \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -H "X-CARLOS-Staff-Actor: CarlosDoc" \
  -d '{
    "demographic_no": 1234,
    "email": "example.patient@example.com",
    "date_of_birth": "1980-05-20",
    "health_card_number": "ABCD 1234-5678"
  }'

curl -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -H "X-CARLOS-Staff-Actor: CarlosDoc" \
  http://127.0.0.1:8090/dev/admin/invites?demographic_no=1234

curl -X POST http://127.0.0.1:8090/dev/admin/invites/1/resend \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -H "X-CARLOS-Staff-Actor: CarlosDoc"

curl -X POST http://127.0.0.1:8090/dev/admin/invites/1/revoke \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -H "X-CARLOS-Staff-Actor: CarlosDoc"

curl -X POST http://127.0.0.1:8090/dev/admin/accounts/1/unlock \
  -H "Authorization: Bearer $PATIENT_PORTAL_DEV_ADMIN_TOKEN" \
  -H "X-CARLOS-Staff-Actor: CarlosDoc"
```

Invite tokens are shown only on create/resend responses. The database stores only the token hash.
The API reports `issued_count`, `last_issued_at`, and `last_issued_by`; these describe token
issuance, not successful email/SMS delivery. Resend invalidates the previous token immediately, so
CARLOS must deliver the returned replacement and record delivery outcome in its own durable
messaging workflow.
When identity proof is supplied, the database stores only per-invite salted keyed hashes of email,
date of birth, and HCN/HIN values. Invites carry a seven-day `expires_at` timestamp so the activation
endpoint has a clear server-side expiry boundary. Invite list responses default to 10 records and are
capped at 100 records per request.

The current development API requires email, date of birth, and HCN/HIN at invite creation time so it
cannot create invites that patients are unable to activate. The CARLOS-backed staff action should
populate those proof hashes from CARLOS demographics instead of staff-entered JSON fields.
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
    "password": "Stronger1!word",
    "mfa_delivery_method": "sms",
    "phone_number": "+16135550199"
  }'
```

Activation checks the invite code, email, date of birth, and HCN/HIN together and returns a generic
failure when they do not match. Usernames are normalized to lowercase and must be unique. Passwords
are hashed with Argon2id before storage. Activation chooses email MFA by default; SMS enrollment
requires a valid phone number and a configured SMS gateway, and the first delivered code verifies
control of that destination.

Activation accepts the CSRF-protected browser form or `application/json`; request bodies are capped
at 16 KiB before validation. Failed activation attempts are audited and rate-limited without storing
raw HCN/HIN, date-of-birth, invite-code, or raw client address values. Client address hashes use
`PATIENT_PORTAL_AUDIT_HASH_SECRET`.

## Patient Auth API

Login accepts either the server-rendered form with CSRF or JSON. Browser form submissions render the
MFA form when MFA is required, or redirect to `/portal` when sign-in is complete. JSON clients receive
the same opaque challenge/session tokens in the response body and should use the bearer token API.

```bash
curl -X POST http://127.0.0.1:8090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "patient.username", "password": "Stronger1!word"}'
```

When MFA is required, login returns an opaque `mfa_challenge_token`. Verify the emailed code
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

MFA resend supports email and SMS when their destinations and delivery providers are available:

```bash
curl -X POST http://127.0.0.1:8090/auth/mfa/resend \
  -H "Content-Type: application/json" \
  -d '{"mfa_challenge_token": "<challenge>", "mfa_delivery_method": "email"}'
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
for local testing. Production responses do not expose raw MFA codes or reset tokens. Configured SMTP
delivers MFA codes and one-time password-reset links without storing their raw values. The database
stores keyed hashes of MFA codes, reset tokens, and session tokens. Sign-in, MFA, reset, lockout,
unlock, and logout write audit events.

Successful browser login/MFA form submissions set an HttpOnly portal session cookie scoped to
`/portal` so the server-rendered dashboard can be used without putting bearer tokens in page scripts.
JSON API responses do not set that cookie; API clients should use the returned bearer
`session_token`.

## Patient Dashboard

```bash
curl -H "Cookie: carlos_portal_session=<session_token>" \
  http://127.0.0.1:8090/portal
```

Dashboard routes:

- `/portal` shows the module dashboard.
- `/portal/account` shows account, contact, password, and MFA settings.
- `/portal/email-passwords` shows searchable, provider/date-filtered, paginated generated email
  password records for the authenticated patient. Passphrases are decrypted, audited, and returned
  one at a time only after the patient selects Reveal.
- `/portal/help` shows clinic help details.
- `POST /portal/logout` clears the portal session cookie and writes a logout audit event.

The dashboard is server-rendered and responsive. Desktop uses a left module rail; mobile uses a
horizontal module bar with logout kept in the top-right header area.
Contact edits immediately update the portal account, revoke outstanding reset/MFA factors tied to
the old destination, notify both addresses, and create a new immutable CARLOS demographic-sync
review. CARLOS must update eChart first and then confirm the exact review `revision`; repeat
confirmations are idempotent and stale revisions return a conflict. Contact-change notices are sent
only after the database commit, but their delivery is not yet backed by a durable outbox; clinics
must treat the notice delivery metric as a pilot blocker until retryable delivery is wired.

## Unlock Secret Storage

`carlos_patient_portal.unlock_secrets` owns generated passphrase storage for encrypted CARLOS
emails.
It provides service functions to generate, create, list, read/decrypt, and revoke secrets. Stored
passphrases use AES-256-GCM with a key derived from
`PATIENT_PORTAL_UNLOCK_SECRET_ENCRYPTION_SECRET`; audit events record create/read/revoke without
storing the raw passphrase.

For production integration, `/internal/carlos/patients/{demographic_no}/unlock-secrets` generates a
clinic-scoped, permission-checked, idempotent password in `pending` state using `source_reference`.
After CARLOS successfully encrypts and sends the message it calls
`/internal/carlos/unlock-secrets/{id}/publish`; send failure calls
`/internal/carlos/unlock-secrets/{id}/revoke`. Patient and FHIR surfaces expose only published
records. The caller supplies a service Bearer token plus
authenticated CARLOS provider ID, display name, clinic ID, and permission headers. Stable provider
IDs and target secret IDs are retained in the audit trail.

The normal internal API never accepts caller-supplied plaintext. Generated values use the PR #3135
format `word-word-###-word-word-###`, selecting four words
uniformly from the reviewed 4096-word list and six independent decimal digits. This provides about
68 bits of entropy while remaining copyable and readable to patients.

The service functions require clinic scope and either account or demographic scope for reads and
revokes. Patient-facing routes should pass the authenticated account id; staff/CARLOS-side routes
can use the clinic id plus `demographic_no`.

## Interoperability Contract

The MVP interoperability scope is intentionally narrow: this package validates the patient identity
data shape this portal slice owns against concrete FHIR and HL7 targets without claiming a complete
general-purpose exchange server:

- FHIR target: R4 `CapabilityStatement`, `Patient`, `DocumentReference`, `Organization`,
  `Practitioner`, `OperationOutcome`, and search `Bundle` resources under `/fhir`. Resources are
  built with `fhir.resources==5.1.1`, and generated examples are checked in CI with the official
  HL7 FHIR validator CLI. Searches support the parameters declared in `/fhir/metadata`, including
  `_id`, `_count`, `_offset`, and `DocumentReference.subject`. Bundle totals cover all matches and
  canonical previous/next links use `PATIENT_PORTAL_PUBLIC_BASE_URL`. Authentication uses a
  patient-scoped portal Bearer session and is explicitly not SMART on FHIR.
- HL7 v2 target: v2.5.1 ADT A04 patient-registration trigger using HL7apy validation plus the
  packaged CARLOS profile artifact
  `carlos_patient_portal/interop_profiles/carlos_patient_registration_adt_a04_v251.json`. The
  message emits CARLOS demographic number and HCN/HIN as repeated `PID-3` identifiers and emits
  email using `PID-13` XTN email components.

Future CARLOS integration work should reuse this module or replace it with a stricter CARLOS profile
before exposing additional clinical exchange endpoints.

## Tests

```bash
cd patient_portal
pytest --cov=carlos_patient_portal --cov-report=term-missing --cov-report=xml:coverage.xml
ruff check .
```

The test command enforces the configured 85% minimum Python coverage and writes the report consumed
by SonarCloud.
