#!/bin/sh
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
# Unit tests for the deb carlos-ctl (debian/assets/carlos_ctl). Standard
# library only — no pytest, no root, no MariaDB — so they run anywhere the
# package builds. pytest also discovers them if you prefer its output.
#
#   debian/tests/run-unit-tests.sh
#
# (This directory is not an autopkgtest suite: there is deliberately no
# debian/tests/control.)
set -eu
cd "$(dirname "$0")/../.."
exec python3 -m unittest discover -s debian/tests/unit -t debian/tests/unit -v
