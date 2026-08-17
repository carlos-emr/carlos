import os
import sqlite3
import stat
import tempfile
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from pathlib import Path

from sqlalchemy import and_, delete, func, or_, select
from sqlalchemy.engine import make_url
from sqlalchemy.orm import Session

from carlos_patient_portal.models import (
    INVITE_STATUS_PENDING,
    INVITE_STATUS_REVOKED,
    INVITE_STATUS_SUPERSEDED,
    PatientPortalAuditEvent,
    PatientPortalInvite,
    PatientPortalMfaChallenge,
    PatientPortalPasswordResetToken,
    PatientPortalSession,
    utc_now,
)

DEFAULT_AUDIT_PRUNE_BATCH_SIZE = 1000
MIN_AUDIT_PRUNE_BATCH_SIZE = 1
MAX_AUDIT_PRUNE_BATCH_SIZE = 10000
DEFAULT_TRANSIENT_RETENTION_DAYS = 30


@dataclass(frozen=True)
class TransientCleanupResult:
    sessions: int
    mfa_challenges: int
    reset_records: int
    invites: int

    @property
    def total(self) -> int:
        return self.sessions + self.mfa_challenges + self.reset_records + self.invites


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


def export_audit_events(
    session: Session,
    *,
    after_id: int = 0,
    batch_size: int = DEFAULT_AUDIT_PRUNE_BATCH_SIZE,
) -> list[dict[str, object]]:
    """Return an ordered JSON-safe batch for an append-only external audit sink."""
    if after_id < 0:
        raise ValueError("after_id must not be negative")
    normalized_batch_size = normalize_prune_batch_size(batch_size)
    events = session.scalars(
        select(PatientPortalAuditEvent)
        .where(PatientPortalAuditEvent.id > after_id)
        .order_by(PatientPortalAuditEvent.id)
        .limit(normalized_batch_size)
    )
    field_names = tuple(column.name for column in PatientPortalAuditEvent.__table__.columns)
    return [
        {
            field_name: (
                value.isoformat() if isinstance(value, datetime) else value
            )
            for field_name in field_names
            if (value := getattr(event, field_name)) is not None
        }
        for event in events
    ]


def cleanup_transient_auth_rows(
    session: Session,
    *,
    before: datetime,
    batch_size: int = DEFAULT_AUDIT_PRUNE_BATCH_SIZE,
    dry_run: bool = False,
) -> TransientCleanupResult:
    normalized_batch_size = normalize_prune_batch_size(batch_size)
    predicates = (
        (
            PatientPortalSession,
            or_(
                PatientPortalSession.expires_at < before,
                PatientPortalSession.revoked_at < before,
            ),
        ),
        (PatientPortalMfaChallenge, PatientPortalMfaChallenge.expires_at < before),
        (PatientPortalPasswordResetToken, PatientPortalPasswordResetToken.expires_at < before),
        (
            PatientPortalInvite,
            and_(
                PatientPortalInvite.expires_at < before,
                PatientPortalInvite.status.in_(
                    (
                        INVITE_STATUS_PENDING,
                        INVITE_STATUS_REVOKED,
                        INVITE_STATUS_SUPERSEDED,
                    )
                ),
            ),
        ),
    )
    # Keyed by field name rather than built positionally: these counts are what the operator reads
    # to decide whether cleanup did what they expected, and a reordering of `predicates` must not be
    # able to silently relabel them.
    counts: dict[str, int] = {}
    for field_name, (model, predicate) in zip(
        ("sessions", "mfa_challenges", "reset_records", "invites"),
        predicates,
        strict=True,
    ):
        record_ids = list(
            session.scalars(
                select(model.id).where(predicate).order_by(model.id).limit(normalized_batch_size)
            )
        )
        if dry_run or not record_ids:
            counts[field_name] = len(record_ids)
            continue
        # The DELETE re-applies the predicate to close the resend-vs-cleanup race, so the
        # selected count can overstate; report what was actually removed, like prune_audit_events.
        result = session.execute(
            delete(model).where(
                model.id.in_(record_ids),
                predicate,
            )
        )
        counts[field_name] = int(result.rowcount or 0)
    return TransientCleanupResult(**counts)


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


def require_regular_file(path: Path, *, description: str) -> None:
    if path.is_symlink() or not path.is_file():
        raise BackupUnavailableError(f"{description} must be a regular file: {path}")


def validate_sqlite_database(path: Path) -> None:
    require_regular_file(path, description="SQLite database")
    try:
        with sqlite3.connect(f"file:{path}?mode=ro", uri=True) as connection:
            integrity_result = connection.execute("PRAGMA integrity_check").fetchone()
    except sqlite3.DatabaseError as exc:
        raise BackupUnavailableError(f"SQLite database is invalid: {path}") from exc
    if integrity_result != ("ok",):
        raise BackupUnavailableError(f"SQLite database failed integrity check: {path}")


def fsync_path(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def fsync_directory(path: Path) -> None:
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def atomic_sqlite_copy(source_path: Path, destination_path: Path) -> Path:
    destination_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{destination_path.name}.",
        suffix=".tmp",
        dir=destination_path.parent,
    )
    temporary_path = Path(temporary_name)
    try:
        os.fchmod(temporary_descriptor, stat.S_IRUSR | stat.S_IWUSR)
        os.close(temporary_descriptor)
        temporary_descriptor = -1
        with sqlite3.connect(f"file:{source_path}?mode=ro", uri=True) as source_connection:
            with sqlite3.connect(temporary_path) as destination_connection:
                source_connection.backup(destination_connection)
                integrity_result = destination_connection.execute(
                    "PRAGMA integrity_check"
                ).fetchone()
                if integrity_result != ("ok",):
                    raise BackupUnavailableError("copied SQLite database failed integrity check")
        os.chmod(temporary_path, stat.S_IRUSR | stat.S_IWUSR)
        fsync_path(temporary_path)
        os.replace(temporary_path, destination_path)
        fsync_directory(destination_path.parent)
        return destination_path
    except (OSError, sqlite3.DatabaseError) as exc:
        raise BackupUnavailableError("SQLite database copy failed") from exc
    finally:
        if temporary_descriptor >= 0:
            os.close(temporary_descriptor)
        temporary_path.unlink(missing_ok=True)


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
    require_regular_file(source_path, description="database")
    if destination_path.is_symlink():
        raise BackupUnavailableError(
            f"backup destination must not be a symlink: {destination_path}"
        )
    if destination_path.exists() and not destination_path.is_file():
        raise BackupUnavailableError(
            f"backup destination must be a regular file: {destination_path}"
        )
    if destination_path.exists() and not overwrite:
        raise BackupDestinationExistsError(f"backup destination already exists: {destination_path}")
    validate_sqlite_database(source_path)
    return atomic_sqlite_copy(source_path, destination_path)


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
    require_regular_file(source_path, description="backup source")
    if destination_path.is_symlink():
        raise BackupUnavailableError(
            f"restore destination must not be a symlink: {destination_path}"
        )
    if destination_path.exists() and not destination_path.is_file():
        raise BackupUnavailableError(
            f"restore destination must be a regular file: {destination_path}"
        )
    if destination_path.exists() and not overwrite:
        raise BackupDestinationExistsError(
            f"restore destination already exists: {destination_path}"
        )
    active_sidecars = [
        sidecar
        for sidecar in (
            Path(f"{destination_path}-wal"),
            Path(f"{destination_path}-shm"),
        )
        if sidecar.exists()
    ]
    if active_sidecars:
        raise BackupUnavailableError(
            "SQLite restore requires the portal to be stopped and WAL sidecars removed "
            "after a clean checkpoint"
        )
    validate_sqlite_database(source_path)
    restored_path = atomic_sqlite_copy(source_path, destination_path)
    validate_sqlite_database(restored_path)
    return restored_path
