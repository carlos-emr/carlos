# CARLOS Patient Portal

This directory contains the Python/FastAPI foundation for the CARLOS patient credential portal.

The first slice is intentionally small:

- FastAPI application factory.
- Pydantic settings with `PATIENT_PORTAL_` environment variables.
- SQLAlchemy session configuration.
- Alembic migration scaffold.
- Minimal public `/health` liveness endpoint.
- Internal `/internal/health/db` database readiness endpoint.
- Server-rendered responsive sign-in shell.
- Basic tests for app wiring, template rendering, and database readiness behavior.

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
uvicorn carlos_patient_portal.main:create_app --factory --reload --host 127.0.0.1 --port 8090
```

Open `http://127.0.0.1:8090/`.

## Configuration

Settings use the `PATIENT_PORTAL_` environment prefix.

Common development variables:

```bash
export PATIENT_PORTAL_ENVIRONMENT=development
export PATIENT_PORTAL_CLINIC_NAME="Maple Creek Medical"
export PATIENT_PORTAL_DATABASE_URL="postgresql+psycopg://localhost:5432/carlos_portal"
```

The default database URL targets local PostgreSQL because PostgreSQL is the intended MVP database.
Tests pass a SQLite database URL into the app factory so the foundation test suite does not require a
running PostgreSQL instance.

Non-development deployments must set `PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN` and
`PATIENT_PORTAL_SESSION_SECRET`. The internal readiness endpoint expects the health token as a Bearer
token:

```bash
curl -H "Authorization: Bearer $PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN" \
  http://127.0.0.1:8090/internal/health/db
```

Expose `/internal/health/db` only to trusted infrastructure such as a load balancer or orchestrator
health probe.

Non-development secrets must be set explicitly and be at least 32 characters.
`PATIENT_PORTAL_ENVIRONMENT` accepts `development`, `staging`, `test`, or `production`; `dev` and
`prod` are normalized aliases.

## Migrations

```bash
cd patient_portal
alembic -c alembic.ini upgrade head
```

Installed wheel deployments can run packaged migrations without a source checkout:

```bash
carlos-patient-portal-migrate
```

This PR only adds the migration scaffold. Patient, invite, membership, audit, and unlock-secret
tables should be added in later vertical slices.

## Tests

```bash
cd patient_portal
pytest
ruff check .
```
