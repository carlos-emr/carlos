from collections.abc import Generator
from contextlib import contextmanager
from typing import Any

from sqlalchemy import Engine, create_engine, event, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.pool import StaticPool


class Base(DeclarativeBase):
    """Base class for future portal-owned SQLAlchemy models."""


def create_portal_engine(database_url: str) -> Engine:
    connect_args: dict[str, object] = {}
    engine_options: dict[str, object] = {"pool_pre_ping": True}
    is_sqlite = database_url.startswith("sqlite")
    if is_sqlite:
        connect_args["check_same_thread"] = False
        if ":memory:" in database_url:
            engine_options["poolclass"] = StaticPool
    engine = create_engine(database_url, connect_args=connect_args, **engine_options)

    if is_sqlite:

        @event.listens_for(engine, "connect")
        def set_sqlite_transaction_mode(dbapi_connection: Any, _: Any) -> None:
            dbapi_connection.isolation_level = None

        @event.listens_for(engine, "begin")
        def begin_sqlite_transaction(connection: Any) -> None:
            connection.exec_driver_sql("BEGIN")

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
