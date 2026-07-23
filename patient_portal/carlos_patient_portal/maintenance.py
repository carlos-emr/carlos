import sqlite3
from datetime import UTC, datetime, timedelta
from pathlib import Path

from sqlalchemy import delete, func, select
from sqlalchemy.engine import make_url
from sqlalchemy.orm import Session

from carlos_patient_portal.models import PatientPortalAuditEvent, utc_now

DEFAULT_AUDIT_PRUNE_BATCH_SIZE = 1000
MIN_AUDIT_PRUNE_BATCH_SIZE = 1
MAX_AUDIT_PRUNE_BATCH_SIZE = 10000


class MaintenanceError(Exception):
    """Base error for operational maintenance tasks."""


class BackupUnsupportedError(MaintenanceError):
    """Raised when the configured database cannot use the built-in backup helper."""


class BackupUnavailableError(MaintenanceError):
    """Raised when a database backup or restore source is unavailable."""


class BackupDestinationExistsError(MaintenanceError):
    """Raised when a backup or restore target already exists and overwrite is disabled."""


def audit_retention_cutoff(retention_days: int, *, now: datetime | None = None) -> datetime:
    if retention_days < 1:
        raise ValueError("retention_days must be positive")
    reference_time = now or utc_now()
    if reference_time.tzinfo is None:
        reference_time = reference_time.replace(tzinfo=UTC)
    return reference_time - timedelta(days=retention_days)


def normalize_prune_batch_size(batch_size: int) -> int:
    if batch_size < MIN_AUDIT_PRUNE_BATCH_SIZE or batch_size > MAX_AUDIT_PRUNE_BATCH_SIZE:
        raise ValueError(
            "batch_size must be between "
            f"{MIN_AUDIT_PRUNE_BATCH_SIZE} and {MAX_AUDIT_PRUNE_BATCH_SIZE}"
        )
    return batch_size


def count_prunable_audit_events(session: Session, *, before: datetime) -> int:
    return int(
        session.scalar(
            select(func.count(PatientPortalAuditEvent.id)).where(
                PatientPortalAuditEvent.created_at < before
            )
        )
        or 0
    )


def prune_audit_events(
    session: Session,
    *,
    before: datetime,
    batch_size: int = DEFAULT_AUDIT_PRUNE_BATCH_SIZE,
) -> int:
    normalized_batch_size = normalize_prune_batch_size(batch_size)
    audit_event_ids = list(
        session.scalars(
            select(PatientPortalAuditEvent.id)
            .where(PatientPortalAuditEvent.created_at < before)
            .order_by(PatientPortalAuditEvent.created_at, PatientPortalAuditEvent.id)
            .limit(normalized_batch_size)
        )
    )
    if not audit_event_ids:
        return 0

    result = session.execute(
        delete(PatientPortalAuditEvent).where(PatientPortalAuditEvent.id.in_(audit_event_ids))
    )
    return int(result.rowcount or 0)


def sqlite_database_path(database_url: str) -> Path:
    parsed_url = make_url(database_url)
    if not parsed_url.drivername.startswith("sqlite"):
        raise BackupUnsupportedError(
            "built-in backup/restore supports SQLite only; use managed PostgreSQL backups "
            "or pg_dump/pg_restore for PostgreSQL deployments"
        )

    database = parsed_url.database
    if database is None or database in {"", ":memory:"}:
        raise BackupUnsupportedError("in-memory SQLite databases cannot be backed up or restored")
    return Path(database).expanduser()


def paths_match(path_a: Path, path_b: Path) -> bool:
    return path_a.resolve(strict=False) == path_b.resolve(strict=False)


def backup_sqlite_database(
    database_url: str,
    output_path: str | Path,
    *,
    overwrite: bool = False,
) -> Path:
    source_path = sqlite_database_path(database_url)
    destination_path = Path(output_path).expanduser()
    if paths_match(source_path, destination_path):
        raise BackupDestinationExistsError("backup destination must differ from database path")
    if not source_path.exists():
        raise BackupUnavailableError(f"database does not exist: {source_path}")
    if destination_path.exists() and not overwrite:
        raise BackupDestinationExistsError(f"backup destination already exists: {destination_path}")

    destination_path.parent.mkdir(parents=True, exist_ok=True)
    if destination_path.exists():
        destination_path.unlink()

    with sqlite3.connect(f"file:{source_path}?mode=ro", uri=True) as source_connection:
        with sqlite3.connect(destination_path) as destination_connection:
            source_connection.backup(destination_connection)
    return destination_path


def restore_sqlite_database(
    database_url: str,
    input_path: str | Path,
    *,
    overwrite: bool = False,
) -> Path:
    source_path = Path(input_path).expanduser()
    destination_path = sqlite_database_path(database_url)
    if paths_match(source_path, destination_path):
        raise BackupDestinationExistsError("restore source must differ from database path")
    if not source_path.exists():
        raise BackupUnavailableError(f"backup source does not exist: {source_path}")
    if destination_path.exists() and not overwrite:
        raise BackupDestinationExistsError(
            f"restore destination already exists: {destination_path}"
        )

    destination_path.parent.mkdir(parents=True, exist_ok=True)
    if destination_path.exists():
        destination_path.unlink()

    with sqlite3.connect(f"file:{source_path}?mode=ro", uri=True) as source_connection:
        with sqlite3.connect(destination_path) as destination_connection:
            source_connection.backup(destination_connection)
    return destination_path
