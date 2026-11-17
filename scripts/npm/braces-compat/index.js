/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// Keep the actual scoped dependency visible to security scanners: GitHub treats
// an npm alias named "braces" as an upstream braces release, losing its identity.
// The unchanged export preserves every consumer API. See the remediation note
// for source review, integrity, regression coverage, and replacement criteria.
module.exports = require('@dieub/braces-depth-guard');
