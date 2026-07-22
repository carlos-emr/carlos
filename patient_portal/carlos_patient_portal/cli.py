from argparse import ArgumentParser
from collections.abc import Sequence

from alembic import command
from alembic.config import Config

from carlos_patient_portal.config import get_settings


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
