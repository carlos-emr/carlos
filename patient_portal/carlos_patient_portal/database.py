from collections.abc import Generator
from contextlib import contextmanager
from pathlib import Path
from typing import Any

from alembic.config import Config
from alembic.runtime.migration import MigrationContext
from alembic.script import ScriptDirectory
from sqlalchemy import Engine, create_engine, event, text
from sqlalchemy.engine import make_url
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.pool import StaticPool

DEFAULT_DATABASE_POOL_SIZE = 5
DEFAULT_DATABASE_MAX_OVERFLOW = 5
DEFAULT_DATABASE_POOL_TIMEOUT_SECONDS = 5
DEFAULT_DATABASE_CONNECT_TIMEOUT_SECONDS = 5
DEFAULT_DATABASE_STATEMENT_TIMEOUT_MS = 15_000
DEFAULT_DATABASE_LOCK_TIMEOUT_MS = 5_000
DEFAULT_SQLITE_BUSY_TIMEOUT_MS = 5_000
MIGRATIONS_DIRECTORY = Path(__file__).resolve().parent / "migrations"


class DatabaseSchemaMismatchError(RuntimeError):
    """Raised when the connected database is not at the packaged Alembic head."""


class Base(DeclarativeBase):
    """Base class for future portal-owned SQLAlchemy models."""


def create_portal_engine(
    database_url: str,
    *,
    pool_size: int = DEFAULT_DATABASE_POOL_SIZE,
    max_overflow: int = DEFAULT_DATABASE_MAX_OVERFLOW,
    pool_timeout_seconds: int = DEFAULT_DATABASE_POOL_TIMEOUT_SECONDS,
    connect_timeout_seconds: int = DEFAULT_DATABASE_CONNECT_TIMEOUT_SECONDS,
    statement_timeout_ms: int = DEFAULT_DATABASE_STATEMENT_TIMEOUT_MS,
    lock_timeout_ms: int = DEFAULT_DATABASE_LOCK_TIMEOUT_MS,
    sqlite_busy_timeout_ms: int = DEFAULT_SQLITE_BUSY_TIMEOUT_MS,
) -> Engine:
    parsed_url = make_url(database_url)
    connect_args: dict[str, object] = {}
    engine_options: dict[str, object] = {"pool_pre_ping": True}
    is_sqlite = parsed_url.drivername.startswith("sqlite")
    is_postgresql = parsed_url.drivername.startswith("postgresql")
    if is_sqlite:
        connect_args["check_same_thread"] = False
        connect_args["timeout"] = sqlite_busy_timeout_ms / 1000
        if parsed_url.database in {None, "", ":memory:"}:
            engine_options["poolclass"] = StaticPool
    else:
        engine_options.update(
            pool_size=pool_size,
            max_overflow=max_overflow,
            pool_timeout=pool_timeout_seconds,
        )
    if is_postgresql:
        connect_args.update(
            connect_timeout=connect_timeout_seconds,
            options=(
                f"-c statement_timeout={statement_timeout_ms} "
                f"-c lock_timeout={lock_timeout_ms}"
            ),
        )
    engine = create_engine(database_url, connect_args=connect_args, **engine_options)

    if is_sqlite:
        use_wal = parsed_url.database not in {None, "", ":memory:"}

        @event.listens_for(engine, "connect")
        def set_sqlite_transaction_mode(dbapi_connection: Any, _: Any) -> None:
            dbapi_connection.isolation_level = None
            dbapi_connection.execute("PRAGMA foreign_keys=ON")
            if use_wal:
                dbapi_connection.execute("PRAGMA journal_mode=WAL")

        @event.listens_for(engine, "begin")
        def begin_sqlite_transaction(connection: Any) -> None:
            connection.execute(text("BEGIN"))

    return engine


def create_session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, autoflush=False, autocommit=False)


@contextmanager
def session_scope(session_factory: sessionmaker[Session]) -> Generator[Session, None, None]:
    with session_factory() as session:
        with session.begin():
            yield session


def check_database(session: Session) -> None:
    session.execute(text("SELECT 1"))


def check_database_schema_current(session: Session) -> None:
    alembic_config = Config()
    alembic_config.set_main_option("script_location", str(MIGRATIONS_DIRECTORY))
    expected_heads = set(ScriptDirectory.from_config(alembic_config).get_heads())
    current_heads = set(MigrationContext.configure(session.connection()).get_current_heads())
    if current_heads != expected_heads:
        raise DatabaseSchemaMismatchError(
            "database schema revision does not match the packaged migration head"
        )
