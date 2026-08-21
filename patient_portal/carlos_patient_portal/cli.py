# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project

import json
import logging
from argparse import ArgumentParser
from collections.abc import Sequence
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from statistics import median
from time import monotonic, sleep

from alembic import command
from alembic.config import Config
from argon2 import PasswordHasher
from sqlalchemy.engine import make_url
from sqlalchemy.exc import SQLAlchemyError

from carlos_patient_portal.config import get_settings
from carlos_patient_portal.database import (
    create_portal_engine,
    create_session_factory,
    session_scope,
)
from carlos_patient_portal.delivery_outbox import process_one_delivery
from carlos_patient_portal.email_delivery import build_portal_email_sender
from carlos_patient_portal.maintenance import (
    DEFAULT_AUDIT_PRUNE_BATCH_SIZE,
    DEFAULT_TRANSIENT_RETENTION_DAYS,
    audit_retention_cutoff,
    backup_sqlite_database,
    cleanup_transient_auth_rows,
    count_prunable_audit_events,
    export_audit_events,
    prune_audit_events,
    restore_sqlite_database,
    summarize_outbox,
)
from carlos_patient_portal.unlock_secrets import reencrypt_unlock_secrets

logger = logging.getLogger(__name__)


def build_alembic_config() -> Config:
    settings = get_settings()
    config = Config()
    config.set_main_option("script_location", "carlos_patient_portal:migrations")
    config.set_main_option("sqlalchemy.url", settings.database_url.replace("%", "%%"))
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
    export_parser = subparsers.add_parser(
        "export-audit",
        help="Write one ordered JSONL batch for an append-only external audit sink.",
    )
    export_parser.add_argument("--after-id", type=int, default=0)
    export_parser.add_argument(
        "--batch-size",
        type=int,
        default=DEFAULT_AUDIT_PRUNE_BATCH_SIZE,
    )
    subparsers.add_parser(
        "outbox-status",
        help="Report queued outbound deliveries by kind and status.",
    )
    benchmark_parser = subparsers.add_parser(
        "benchmark-password-hashing",
        help="Measure configured Argon2 capacity on the deployment host.",
    )
    benchmark_parser.add_argument("--operations", type=int, default=8)

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

    if args.command == "benchmark-password-hashing":
        if args.operations < 1 or args.operations > 1000:
            parser.error("--operations must be between 1 and 1000")
        hasher = PasswordHasher(
            time_cost=settings.password_hash_time_cost,
            memory_cost=settings.password_hash_memory_kib,
            parallelism=settings.password_hash_parallelism,
            hash_len=32,
            salt_len=16,
        )
        started = monotonic()
        with ThreadPoolExecutor(max_workers=settings.password_hash_max_concurrency) as executor:
            durations = list(
                executor.map(
                    lambda operation: _time_password_hash(hasher, operation),
                    range(args.operations),
                )
            )
        elapsed = monotonic() - started
        peak_memory_mib = (
            settings.password_hash_memory_kib * settings.password_hash_max_concurrency / 1024
        )
        print(
            f"operations={args.operations} concurrency={settings.password_hash_max_concurrency} "
            f"elapsed_seconds={elapsed:.3f} median_seconds={median(durations):.3f} "
            f"operations_per_second={args.operations / elapsed:.2f} "
            f"estimated_peak_memory_mib={peak_memory_mib:.0f}"
        )
        return

    database_url = settings.database_url
    if args.command == "prune-audit" and not args.dry_run:
        if settings.maintenance_database_url is None:
            parser.error(
                "PATIENT_PORTAL_MAINTENANCE_DATABASE_URL is required for audit deletion"
            )
        runtime_url = make_url(settings.database_url)
        maintenance_url = make_url(settings.maintenance_database_url)
        same_database_and_role = (
            runtime_url.get_backend_name() == maintenance_url.get_backend_name()
            and runtime_url.host == maintenance_url.host
            and runtime_url.port == maintenance_url.port
            and runtime_url.database == maintenance_url.database
            and runtime_url.username == maintenance_url.username
        )
        if same_database_and_role:
            parser.error("maintenance and runtime database URLs must use separate roles")
        database_url = settings.maintenance_database_url
    database_engine = create_portal_engine(
        database_url,
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
            if args.command == "outbox-status":
                summary = summarize_outbox(session)
                if not summary:
                    print("outbox is empty")
                    return
                for row in summary:
                    print(json.dumps(row, separators=(",", ":"), sort_keys=True))
                return
            if args.command == "cleanup-transient-auth":
                cutoff = audit_retention_cutoff(args.retention_days)
                cleanup_result = cleanup_transient_auth_rows(
                    session,
                    before=cutoff,
                    batch_size=args.batch_size,
                    dry_run=args.dry_run,
                )
                action = "dry run completed" if args.dry_run else "cleanup completed"
                print(
                    # Every interpolated value is an aggregate row count, never a token or hash.
                    # codeql[py/clear-text-logging-sensitive-data]
                    "transient authentication "
                    f"{action}: sessions={cleanup_result.sessions} "
                    f"mfa_challenges={cleanup_result.mfa_challenges} "
                    f"reset_records={cleanup_result.reset_records} "
                    f"email_change_requests={cleanup_result.email_change_requests} "
                    f"invites={cleanup_result.invites} "
                    f"outbound_deliveries={cleanup_result.outbound_deliveries} "
                    f"total={cleanup_result.total}"
                )
                return

            if args.command == "export-audit":
                for event in export_audit_events(
                    session,
                    after_id=args.after_id,
                    batch_size=args.batch_size,
                ):
                    print(json.dumps(event, sort_keys=True, separators=(",", ":")))
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


def _time_password_hash(hasher: PasswordHasher, operation: int) -> float:
    started = monotonic()
    hasher.hash(f"Capacity-test-password-{operation}-A!")
    return monotonic() - started


def outbox_worker(argv: Sequence[str] | None = None) -> None:
    """Run the durable outbound-delivery worker."""
    parser = ArgumentParser(
        prog="carlos-patient-portal-outbox-worker",
        description="Deliver queued CARLOS patient portal email with retry-safe leases.",
    )
    parser.add_argument("--once", action="store_true", help="Drain available work and exit.")
    parser.add_argument(
        "--max-deliveries",
        type=int,
        default=0,
        help="Exit after this many deliveries; zero means no limit.",
    )
    args = parser.parse_args(argv)
    if args.max_deliveries < 0:
        parser.error("--max-deliveries must not be negative")

    settings = get_settings()
    if settings.outbox_encryption_secret is None:
        parser.error("PATIENT_PORTAL_OUTBOX_ENCRYPTION_SECRET must be configured")
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
    # Built once. Rebuilding per iteration re-read configuration and re-created the provider
    # client on every poll of an idle queue.
    email_sender = build_portal_email_sender(settings)
    delivered = 0
    try:
        while args.max_deliveries == 0 or delivered < args.max_deliveries:
            try:
                result = process_one_delivery(
                    session_factory,
                    email_sender=email_sender,
                    encryption_secret=settings.outbox_encryption_secret.get_secret_value(),
                    encryption_keys=settings.resolved_outbox_keyring,
                    max_attempts=settings.outbox_max_attempts,
                    lease_seconds=settings.outbox_lease_seconds,
                )
            except SQLAlchemyError as exc:
                # Only KeyboardInterrupt used to be handled, so a single lock-contention event
                # or connection reset - _claim_delivery takes with_for_update(skip_locked=True)
                # under a 5s lock_timeout - ended the process. Every queued password-reset link
                # and contact-change notice for the clinic then stopped being delivered, with
                # nothing to detect it: no service unit ships here, and the row the worker had
                # claimed sat in `processing` until its lease expired with no worker left to
                # reclaim it.
                #
                # A transient database fault is the condition this loop exists to survive.
                # Process exit stays reserved for unrecoverable configuration faults, which are
                # raised before the loop starts.
                # nosemgrep: python-logger-credential-disclosure -- logs only type(exc).__name__
                logger.error(  # NOSONAR - traceback details can contain database values
                    "Outbox worker iteration failed, retrying after %ss: %s",
                    settings.outbox_poll_seconds,
                    type(exc).__name__,
                )
                if args.once:
                    return
                sleep(settings.outbox_poll_seconds)
                continue
            if result is None:
                if args.once:
                    return
                sleep(settings.outbox_poll_seconds)
                continue
            delivered += 1
    except KeyboardInterrupt:
        return
    finally:
        database_engine.dispose()
