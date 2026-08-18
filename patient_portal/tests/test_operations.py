import json
import os
import sqlite3
import stat

import pytest
from sqlalchemy.orm import Session

from carlos_patient_portal import cli
from carlos_patient_portal.audit import record_audit_event
from carlos_patient_portal.config import Settings
from carlos_patient_portal.database import Base, create_portal_engine
from carlos_patient_portal.maintenance import (
    BackupUnavailableError,
    backup_sqlite_database,
    restore_sqlite_database,
)


def create_sqlite_database(path, value: str) -> None:
    with sqlite3.connect(path) as connection:
        connection.execute("CREATE TABLE sample (value TEXT NOT NULL)")
        connection.execute("INSERT INTO sample (value) VALUES (?)", (value,))


def read_sqlite_value(path) -> str:
    with sqlite3.connect(path) as connection:
        row = connection.execute("SELECT value FROM sample").fetchone()
    assert row is not None
    return str(row[0])


def test_failed_backup_overwrite_preserves_previous_backup(tmp_path) -> None:
    database_path = tmp_path / "corrupt.db"
    backup_path = tmp_path / "known-good.db"
    database_path.write_bytes(b"not a SQLite database")
    create_sqlite_database(backup_path, "known-good")

    with pytest.raises(BackupUnavailableError):
        backup_sqlite_database(
            f"sqlite+pysqlite:///{database_path}",
            backup_path,
            overwrite=True,
        )

    assert read_sqlite_value(backup_path) == "known-good"


def test_failed_restore_preserves_live_database(tmp_path) -> None:
    database_path = tmp_path / "live.db"
    backup_path = tmp_path / "corrupt-backup.db"
    create_sqlite_database(database_path, "live")
    backup_path.write_bytes(b"not a SQLite database")

    with pytest.raises(BackupUnavailableError):
        restore_sqlite_database(
            f"sqlite+pysqlite:///{database_path}",
            backup_path,
            overwrite=True,
        )

    assert read_sqlite_value(database_path) == "live"


def test_backup_and_restore_files_are_private(tmp_path) -> None:
    database_path = tmp_path / "live.db"
    backup_path = tmp_path / "backup.db"
    restored_path = tmp_path / "restored.db"
    create_sqlite_database(database_path, "private")

    previous_umask = os.umask(0o022)
    try:
        backup_sqlite_database(f"sqlite+pysqlite:///{database_path}", backup_path)
        restore_sqlite_database(f"sqlite+pysqlite:///{restored_path}", backup_path)
    finally:
        os.umask(previous_umask)

    assert stat.S_IMODE(backup_path.stat().st_mode) == 0o600
    assert stat.S_IMODE(restored_path.stat().st_mode) == 0o600
    assert read_sqlite_value(restored_path) == "private"


@pytest.mark.parametrize("punctuation", ["?", "#"])
def test_backup_and_restore_accept_sqlite_paths_with_uri_punctuation(
    tmp_path,
    punctuation: str,
) -> None:
    database_path = tmp_path / "live.db"
    backup_path = tmp_path / f"backup{punctuation}portal.db"
    restored_path = tmp_path / "restored.db"
    create_sqlite_database(database_path, "punctuation-safe")

    backup_sqlite_database(f"sqlite+pysqlite:///{database_path}", backup_path)
    restore_sqlite_database(f"sqlite+pysqlite:///{restored_path}", backup_path)

    assert read_sqlite_value(restored_path) == "punctuation-safe"


def test_backup_rejects_symlink_destination(tmp_path) -> None:
    database_path = tmp_path / "live.db"
    target_path = tmp_path / "target.db"
    symlink_path = tmp_path / "backup.db"
    create_sqlite_database(database_path, "live")
    create_sqlite_database(target_path, "target")
    symlink_path.symlink_to(target_path)

    with pytest.raises(BackupUnavailableError, match="symlink"):
        backup_sqlite_database(
            f"sqlite+pysqlite:///{database_path}",
            symlink_path,
            overwrite=True,
        )

    assert read_sqlite_value(target_path) == "target"


def test_restore_rejects_live_wal_sidecars_instead_of_reporting_success(tmp_path) -> None:
    database_path = tmp_path / "live.db"
    backup_path = tmp_path / "backup.db"
    create_sqlite_database(database_path, "live")
    create_sqlite_database(backup_path, "backup")

    connection = sqlite3.connect(database_path)
    try:
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute("UPDATE sample SET value = 'live-wal'")
        connection.commit()
        assert (tmp_path / "live.db-wal").exists()

        with pytest.raises(BackupUnavailableError, match="portal to be stopped"):
            restore_sqlite_database(
                f"sqlite+pysqlite:///{database_path}",
                backup_path,
                overwrite=True,
            )

        assert connection.execute("SELECT value FROM sample").fetchone() == ("live-wal",)
    finally:
        connection.close()


def test_restore_rejects_dangling_wal_sidecar_symlink(tmp_path) -> None:
    database_path = tmp_path / "live.db"
    backup_path = tmp_path / "backup.db"
    create_sqlite_database(database_path, "live")
    create_sqlite_database(backup_path, "backup")
    (tmp_path / "live.db-wal").symlink_to(tmp_path / "missing-wal")

    with pytest.raises(BackupUnavailableError, match="portal to be stopped"):
        restore_sqlite_database(
            f"sqlite+pysqlite:///{database_path}",
            backup_path,
            overwrite=True,
        )


def test_transient_cleanup_cli_reports_each_table_count(
    tmp_path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    database_path = tmp_path / "cleanup.db"
    settings = Settings(
        environment="development",
        database_url=f"sqlite+pysqlite:///{database_path}",
    )
    engine = create_portal_engine(settings.database_url)
    Base.metadata.create_all(engine)
    engine.dispose()
    monkeypatch.setattr(cli, "get_settings", lambda: settings)

    cli.maintenance(["cleanup-transient-auth", "--dry-run"])

    output = capsys.readouterr().out
    assert "sessions=0" in output
    assert "mfa_challenges=0" in output
    assert "reset_records=0" in output
    assert "invites=0" in output
    assert "total=0" in output


def test_audit_pruning_refuses_runtime_database_credentials(
    tmp_path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    database_url = f"sqlite+pysqlite:///{tmp_path / 'audit.db'}"
    settings = Settings(environment="development", database_url=database_url)
    monkeypatch.setattr(cli, "get_settings", lambda: settings)

    with pytest.raises(SystemExit):
        cli.maintenance(["prune-audit"])

    assert "PATIENT_PORTAL_MAINTENANCE_DATABASE_URL" in capsys.readouterr().err


def test_audit_export_cli_emits_ordered_jsonl(
    tmp_path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    database_url = f"sqlite+pysqlite:///{tmp_path / 'audit-export.db'}"
    settings = Settings(environment="development", database_url=database_url)
    engine = create_portal_engine(database_url)
    Base.metadata.create_all(engine)
    with Session(engine) as session, session.begin():
        event = record_audit_event(
            session,
            event_type="login",
            outcome="success",
            actor_type="system",
            clinic_id="clinic-a",
        )
        event_id = event.id
    engine.dispose()
    monkeypatch.setattr(cli, "get_settings", lambda: settings)

    cli.maintenance(["export-audit", "--after-id", "0", "--batch-size", "10"])

    exported = json.loads(capsys.readouterr().out)
    assert exported["id"] == event_id
    assert exported["event_type"] == "login"
    assert exported["clinic_id"] == "clinic-a"
