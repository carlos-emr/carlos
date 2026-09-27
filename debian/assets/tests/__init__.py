# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Packaging-contract tests for the carlos-emr Debian packaging.

Run from the repository root:
    python3 -m unittest discover -s debian/assets/tests -t .

They read debian/ and the release workflow only; nothing here imports
carlos_ctl (the CLI is its own repository and package, carlos-emr/carlos-ctl,
whose suite carlos's CI also runs against this checkout with CARLOS_SRC).
"""
