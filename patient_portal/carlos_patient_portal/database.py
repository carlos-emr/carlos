from collections.abc import Generator

from sqlalchemy import Engine, create_engine, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker


class Base(DeclarativeBase):
    """Base class for future portal-owned SQLAlchemy models."""


def create_portal_engine(database_url: str) -> Engine:
    connect_args: dict[str, object] = {}
    if database_url.startswith("sqlite"):
        connect_args["check_same_thread"] = False
    return create_engine(database_url, pool_pre_ping=True, connect_args=connect_args)


def create_session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, autoflush=False, autocommit=False)


def session_scope(session_factory: sessionmaker[Session]) -> Generator[Session, None, None]:
    with session_factory() as session:
        yield session


def check_database(session: Session) -> None:
    session.execute(text("SELECT 1"))
