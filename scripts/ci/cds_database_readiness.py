# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Wait for the final daemon of an owned MariaDB container, not its init server."""
import time


def database_client(docker, database, defaults_file):
    # MariaDB's temporary initialization server accepts socket connections and
    # then exits. Only the final daemon accepts TCP on the container loopback.
    return list(docker) + ['exec', database, 'mariadb',
                          '--defaults-extra-file=' + defaults_file,
                          '--protocol=TCP', '--host=127.0.0.1']


def wait_for_database(client, execute, *, monotonic=time.monotonic, sleep=time.sleep):
    if '--protocol=TCP' not in client or '--host=127.0.0.1' not in client:
        raise RuntimeError('Owned database readiness requires its final TCP listener')
    deadline = monotonic() + 90
    while execute(client + ['-e', 'SELECT 1'], check=False).returncode:
        if monotonic() > deadline:
            raise RuntimeError('Owned MariaDB startup timeout')
        sleep(1)
