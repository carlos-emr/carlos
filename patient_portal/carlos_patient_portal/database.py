from collections.abc import Generator

from sqlalchemy import Engine, create_engine, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from carlos_patient_portal.config import get_settings


class Base(DeclarativeBase):
    """Base class for future portal-owned SQLAlchemy models."""


def create_portal_engine(database_url: str | None = None) -> Engine:
    url = database_url or get_settings().database_url
    connect_args: dict[str, object] = {}
    if url.startswith("sqlite"):
        connect_args["check_same_thread"] = False
    return create_engine(url, pool_pre_ping=True, connect_args=connect_args)


engine = create_portal_engine()
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False)


def get_database_session() -> Generator[Session, None, None]:
    with SessionLocal() as session:
        yield session


def check_database(session: Session) -> None:
    session.execute(text("SELECT 1"))
