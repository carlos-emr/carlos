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
| Account settings | Email changes require confirmation from the new address; immutable sync reviews are queued | CARLOS must update eChart then idempotently confirm the reviewed revision; phone numbers are still unverified |
| FHIR R4 and HL7 v2.5.1 | Scoped resources and conformance artifacts are validator-tested | This is a narrow portal profile, not a general-purpose exchange server |
| Pilot hardening | In-process controls and operator commands are present | External monitoring, audit export, managed backups, and restore drills remain required |

Why this is a separate Python service with its own PostgreSQL database, what was traded away, and
the conditions under which the decision should be revisited are recorded in
[`docs/architecture/patient-portal-runtime.md`](../docs/architecture/patient-portal-runtime.md).

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
- `cryptography` tracks the current major line rather than a pinned one. 48.x carries
  PYSEC-2026-3552, 3553, and 3554; only 50.0.0 fixes all three, so the floor is 50.0.0. The portal
  uses it for AES-256-GCM and HKDF in `unlock_secrets`, so treat advisories against it as
  release-blocking rather than routine.
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

### Browser locales

The browser UI resolves a locale per request: an explicit choice in the `portal_locale` cookie
first, then the best supported match from `Accept-Language`, then English. `GET /locale/{code}`
records the choice and redirects back to a validated local path; it takes no CSRF token because the
cookie selects strings and a date format only, and carries no identity or authorization.

`SUPPORTED_LOCALES` advertises EN, FR, ES, PL, and PT-BR, and every one of them is selectable and
persists. **No translations are written yet**: `TEXT_CATALOG` holds an entry per locale, and
`portal_text()` merges the selected locale over English key by key, so an untranslated locale
renders English rather than failing. Adding a language therefore means adding keys to
`TEXT_CATALOG["<code>"]` and a date format to `DATETIME_FORMATS` — no route, template, or context
change — and a partially translated catalog renders correctly for the keys it does define.

`PATIENT_PORTAL_PUBLIC_BASE_URL` is used to build password-reset email links and is required when
SMTP is configured outside development. It must use HTTPS outside development.

**Subpath hosting.** The value may carry a path — `https://portal.example.test/patient` — and the
prefix is applied consistently: emailed reset and email-change links, FHIR canonical and pagination
URLs, static assets, and form actions all include it, because the path is passed to FastAPI as
`root_path`. The proxy contract is the standard ASGI one: **strip the prefix before forwarding**.
The portal routes on `/auth/login` and generates `/patient/auth/login` back out. A proxy that
forwards the prefix unstripped will 404, because the app is not mounted under it.

Setting it also makes that hostname the only `Host` header accepted for patient and FHIR traffic.
Health and readiness probes normally arrive under a different name (loopback, a pod IP, or a
Kubernetes service name), so `127.0.0.1`, `localhost`, and `[::1]` are accepted as probe aliases by
default. Add to that list with `PATIENT_PORTAL_PROBE_ALLOWED_HOSTS` (comma-separated) when probes
reach the service under another name — for example
`PATIENT_PORTAL_PROBE_ALLOWED_HOSTS="portal.svc.cluster.local,10.0.0.7"`. Configured aliases extend
the loopback defaults rather than replacing them; set
`PATIENT_PORTAL_PROBE_ALLOWED_HOSTS_EXCLUSIVE=true` if the deployment must not answer to loopback at
all. Entries must be literal hostnames — a wildcard is refused at startup, because Starlette's
`TrustedHostMiddleware` treats any `*` entry as "accept every host" and would disable canonical-Host
enforcement for the whole service. These aliases
only widen which `Host` headers are accepted; every generated patient-visible link continues to use
the canonical `PATIENT_PORTAL_PUBLIC_BASE_URL` origin. Reset tokens are put
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
- A delivery the provider reports as failed shortens that account cooldown to a five-second retry
  grace rather than clearing it. A reported failure is not proof nothing was sent — greylisting, an
  SMTP timeout after `DATA`, and a gateway that returns 5xx after queueing all report failure having
  delivered — so clearing it would let a degraded provider flood a patient's mailbox or SMS number
  at the cost of one request each. The grace keeps the patient's retry to seconds instead of a full
  window while keeping the per-account send rate bounded.
- MFA codes expire after 10 minutes.
- Email MFA resend is limited to once per minute.
- SMS MFA is available when the account has a valid phone number and the webhook sender is
  configured; delivery failures are audited and do not silently invalidate another method.
- Patient sessions have a one-hour absolute lifetime and expire after 10 minutes of inactivity.
- Password reset tokens expire after one hour and are one-time use.
- Email-change confirmation links expire after 24 hours and are one-time use.

The deployment can tune these with `PATIENT_PORTAL_REQUIRE_MFA`,
`PATIENT_PORTAL_AUTH_MAX_FAILED_PASSWORD_ATTEMPTS`, `PATIENT_PORTAL_MFA_MAX_FAILED_ATTEMPTS`,
`PATIENT_PORTAL_SESSION_TTL_SECONDS`, `PATIENT_PORTAL_SESSION_IDLE_TIMEOUT_SECONDS`,
`PATIENT_PORTAL_MFA_CODE_TTL_SECONDS`,
`PATIENT_PORTAL_MFA_EMAIL_RESEND_COOLDOWN_SECONDS`,
`PATIENT_PORTAL_MFA_SMS_RESEND_COOLDOWN_SECONDS`,
`PATIENT_PORTAL_PASSWORD_RESET_TOKEN_TTL_SECONDS`, and
`PATIENT_PORTAL_EMAIL_CHANGE_TOKEN_TTL_SECONDS`.

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
- `PATIENT_PORTAL_AUDIT_RETENTION_DAYS` defaults to a conservative 9,150 days, which guarantees at
  least 25 complete calendar years including leap years. Retention obligations run both ways —
  PHIPA/PIPEDA set a minimum, while privacy law and clinic policy can require deletion — so a
  shorter value is configurable, but only with `PATIENT_PORTAL_ALLOW_SHORT_AUDIT_RETENTION=true`
  and never below 30 days. A shortened retention is logged at startup as a warning and written to
  the audit trail as a `retention.policy_override` event, so narrowing the security log is itself
  visible in the security log. Audit pruning stays explicit so clinics can align the job with
  legal-retention processes.
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

Migration `0005_pending_email_confirmation` refuses to downgrade while pending email-change
requests or email-change audit events exist, so a rollback cannot silently discard evidence that a
patient asked to move the address their verification codes are delivered to.
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
  and CSRF tokens. Schedule it as a patient sign-out event. The portal does not use this value as a
  key directly: `token_keys.py` derives four independent HKDF keys from it, one each for CSRF
  signatures, session tokens, MFA challenge/code hashes, and password-reset tokens, so a value
  minted for one purpose cannot be valid in another. All four rotate together with the secret.
- Rotating `PATIENT_PORTAL_IDENTITY_PROOF_SECRET` invalidates pending invitations; reissue them
  after cutover.
- Rotating `PATIENT_PORTAL_AUDIT_HASH_SECRET` changes pseudonymous client/invite correlations;
  record the cutover time for investigations.
- Rotating `PATIENT_PORTAL_INTERNAL_API_TOKEN` does not require a synchronised restart. Set
  `PATIENT_PORTAL_INTERNAL_API_TOKEN` to the new value and
  `PATIENT_PORTAL_INTERNAL_API_TOKEN_PREVIOUS` to the outgoing one, restart the portal, cut CARLOS
  over to the new token, then clear `_PREVIOUS` and restart the portal again. Both values are
  accepted while `_PREVIOUS` is set, so leaving it configured permanently defeats the rotation;
  treat clearing it as part of the same change.

## CARLOS Internal API

Set `PATIENT_PORTAL_INTERNAL_API_TOKEN` to enable the production staff/service contract. Requests
must include its Bearer token and CARLOS-authenticated `X-CARLOS-Provider-ID`,
`X-CARLOS-Provider-Name`, `X-CARLOS-Clinic-ID`, and `X-CARLOS-Permissions` headers.

**Pilot blocker — the trust model here is deployment-enforced, not application-enforced.** The
service token authenticates *CARLOS as a system*; provider identity, clinic, and the entire
permission set are then read from plaintext request headers the caller chooses. Anything able to
present the token can therefore assert any provider and any permission, including reading generated
email passphrases, and the `actor_id` recorded in the audit trail is only as trustworthy as the
proxy configuration in front of the portal. Two controls are mandatory before pilot traffic:

- the reverse proxy must strip externally supplied copies of the four `X-CARLOS-*` headers, and
- the `/internal/carlos/` route family must be reachable only from CARLOS application instances.

The intended replacement is a short-lived signed assertion minted by CARLOS from the authenticated
provider session (carrying provider id, clinic, and permissions) or mutual TLS, so provider identity
is cryptographically attributable rather than proxy-attributable. That work is not in this
iteration; until it lands, treat the proxy configuration as a security control with the same review
weight as application code.

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
Changing the email address is a two-step flow. Submitting the contact form changes nothing on the
account: it records a pending request, emails a one-time confirmation link to the proposed address,
and notifies the current address that a change was asked for. Verification codes and password-reset
links keep going to the current address until the link is used, so a mistyped address strands
nothing and a stolen password alone cannot move the recovery channel. Opening the link applies the
new email and phone together, revokes reset/MFA factors tied to the old destination, notifies both
addresses, and creates the immutable CARLOS demographic-sync review.

The confirmation link expires after `PATIENT_PORTAL_EMAIL_CHANGE_TOKEN_TTL_SECONDS` (24 hours by
default) and is one-time. Requesting another change revokes the previous link, so a corrected typo
cannot leave the mistyped address able to take the account. A request whose confirmation email
cannot be delivered is revoked rather than left pending. A change that only touches the phone
number still applies immediately and opens its review straight away; **phone numbers are not
separately proven, so an SMS MFA destination can still be moved by a patient holding the password
alone** — closing that gap needs an SMS confirmation step and is outstanding work.

CARLOS must update eChart first and then confirm the exact review `revision`; repeat
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
