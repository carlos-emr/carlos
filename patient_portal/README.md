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
- Basic tests for app wiring, template rendering, and database health dependency override.

## Local Setup

```bash
cd patient_portal
python3 -m venv /tmp/carlos-patient-portal-venv
. /tmp/carlos-patient-portal-venv/bin/activate
pip install -e ".[dev]"
```

Use the checked-in lock file when you need reproducible dependency versions:

```bash
pip install -c requirements.lock -e ".[dev]"
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
export PATIENT_PORTAL_DATABASE_URL="postgresql+psycopg://portal:portal@localhost:5432/carlos_portal"
```

The default database URL targets local PostgreSQL because PostgreSQL is the intended MVP database.
Tests pass a SQLite database URL into the app factory so the foundation test suite does not require a
running PostgreSQL instance.

Production deployments must also set `PATIENT_PORTAL_SESSION_SECRET` and
`PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN`. The internal readiness endpoint expects the health token as a
Bearer token:

```bash
curl -H "Authorization: Bearer $PATIENT_PORTAL_INTERNAL_HEALTH_TOKEN" \
  http://127.0.0.1:8090/internal/health/db
```

Expose `/internal/health/db` only to trusted infrastructure such as a load balancer or orchestrator
health probe.

## Migrations

```bash
cd patient_portal
alembic -c alembic.ini upgrade head
```

This PR only adds the migration scaffold. Patient, invite, membership, audit, and unlock-secret
tables should be added in later vertical slices.

## Tests

```bash
cd patient_portal
pytest
ruff check .
```
