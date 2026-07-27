import os
import sqlite3
import stat

import pytest

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
