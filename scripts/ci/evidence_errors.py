# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Shared refusal type for release evidence, independent of any file parser."""


class EvidenceError(ValueError):
    """Evidence is incomplete, ambiguous, or unsafe to accept."""
