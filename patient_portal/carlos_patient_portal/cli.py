from argparse import ArgumentParser
from collections.abc import Sequence
from pathlib import Path

from alembic import command
from alembic.config import Config

from carlos_patient_portal.config import get_settings
from carlos_patient_portal.database import (
    create_portal_engine,
    create_session_factory,
    session_scope,
)
from carlos_patient_portal.maintenance import (
    DEFAULT_AUDIT_PRUNE_BATCH_SIZE,
    DEFAULT_TRANSIENT_RETENTION_DAYS,
    audit_retention_cutoff,
    backup_sqlite_database,
    cleanup_transient_auth_rows,
    count_prunable_audit_events,
    prune_audit_events,
    restore_sqlite_database,
)
from carlos_patient_portal.unlock_secrets import reencrypt_unlock_secrets


def build_alembic_config() -> Config:
    settings = get_settings()
    config = Config()
    config.set_main_option("script_location", "carlos_patient_portal:migrations")
    config.set_main_option("sqlalchemy.url", settings.database_url)
    return config


def migrate(argv: Sequence[str] | None = None) -> None:
    parser = ArgumentParser(
        prog="carlos-patient-portal-migrate",
        description="Run packaged Alembic migrations for the CARLOS patient portal.",
    )
    parser.add_argument(
        "revision",
        nargs="?",
        default="head",
        help="Alembic revision target to upgrade to. Defaults to head.",
    )
    args = parser.parse_args(argv)

    command.upgrade(build_alembic_config(), args.revision)


def maintenance(argv: Sequence[str] | None = None) -> None:
    parser = ArgumentParser(
        prog="carlos-patient-portal-maintenance",
        description="Run CARLOS patient portal operational maintenance tasks.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    prune_parser = subparsers.add_parser(
        "prune-audit",
        help="Delete audit events older than PATIENT_PORTAL_AUDIT_RETENTION_DAYS.",
    )
    prune_parser.add_argument(
        "--batch-size",
        type=int,
        default=DEFAULT_AUDIT_PRUNE_BATCH_SIZE,
        help="Maximum audit events to delete in this run.",
    )
    prune_parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Count prunable audit events without deleting them.",
    )

    backup_parser = subparsers.add_parser(
        "backup-sqlite",
        help="Back up a file-backed SQLite portal database.",
    )
    backup_parser.add_argument("--output", required=True, help="Backup database path.")
    backup_parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Replace an existing backup path.",
    )

    restore_parser = subparsers.add_parser(
        "restore-sqlite",
        help="Restore a file-backed SQLite portal database from a backup.",
    )
    restore_parser.add_argument("--input", required=True, help="Backup database path.")
    restore_parser.add_argument(
        "--overwrite",
        action="store_true",
        help="Replace the configured SQLite database path.",
    )
    rotate_parser = subparsers.add_parser(
        "rotate-unlock-secrets",
        help="Re-encrypt one batch of stored unlock secrets with the active key.",
    )
    rotate_parser.add_argument(
        "--batch-size",
        type=int,
        default=100,
        help="Maximum unlock secrets to rotate in this run.",
    )
    cleanup_parser = subparsers.add_parser(
        "cleanup-transient-auth",
        help="Delete expired authentication rows after a conservative retention period.",
    )
    cleanup_parser.add_argument(
        "--retention-days",
        type=int,
        default=DEFAULT_TRANSIENT_RETENTION_DAYS,
    )
    cleanup_parser.add_argument(
        "--batch-size",
        type=int,
        default=DEFAULT_AUDIT_PRUNE_BATCH_SIZE,
    )
    cleanup_parser.add_argument("--dry-run", action="store_true")

    args = parser.parse_args(argv)
    settings = get_settings()

    if args.command == "backup-sqlite":
        backup_path = backup_sqlite_database(
            settings.database_url,
            Path(args.output),
            overwrite=args.overwrite,
        )
        print(f"backed up SQLite database to {backup_path}")
        return

    if args.command == "restore-sqlite":
        restored_path = restore_sqlite_database(
            settings.database_url,
            Path(args.input),
            overwrite=args.overwrite,
        )
        print(f"restored SQLite database to {restored_path}")
        return

    database_engine = create_portal_engine(
        settings.database_url,
        pool_size=settings.database_pool_size,
        max_overflow=settings.database_max_overflow,
        pool_timeout_seconds=settings.database_pool_timeout_seconds,
        connect_timeout_seconds=settings.database_connect_timeout_seconds,
        statement_timeout_ms=settings.database_statement_timeout_ms,
        lock_timeout_ms=settings.database_lock_timeout_ms,
        sqlite_busy_timeout_ms=settings.sqlite_busy_timeout_ms,
    )
    session_factory = create_session_factory(database_engine)
    try:
        with session_scope(session_factory) as session:
            if args.command == "rotate-unlock-secrets":
                rotated_count = reencrypt_unlock_secrets(
                    session,
                    encryption_keys=settings.resolved_unlock_secret_keyring,
                    active_key_id=settings.unlock_secret_active_key_id,
                    limit=args.batch_size,
                )
                print(f"rotated {rotated_count} unlock secrets")
                return
            if args.command == "cleanup-transient-auth":
                cutoff = audit_retention_cutoff(args.retention_days)
                cleanup_transient_auth_rows(
                    session,
                    before=cutoff,
                    batch_size=args.batch_size,
                    dry_run=args.dry_run,
                )
                action = "dry run completed" if args.dry_run else "cleanup completed"
                print(f"transient authentication {action}")
                return

            cutoff = audit_retention_cutoff(settings.audit_retention_days)
            if args.dry_run:
                prunable_count = count_prunable_audit_events(session, before=cutoff)
                print(f"{prunable_count} audit events older than retention")
                return

            deleted_count = prune_audit_events(
                session,
                before=cutoff,
                batch_size=args.batch_size,
            )
            print(f"deleted {deleted_count} audit events older than retention")
    finally:
        database_engine.dispose()
