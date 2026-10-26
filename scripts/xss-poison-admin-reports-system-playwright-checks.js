#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Stored-markup walk of the Administration panel, part 2 of 2 (Administration (reports, schedule, system, data)).
// User path: Schedule > Administration > every item of this half of the menu (the same click catalogue as
// admin-index-links). Implementation, fixtures and cleanup: scripts/lib/xss-poison-admin-walk.js.
// Fixtures (INSERTed, so the WAF is bypassed like legacy/imported data): FAKE rows carrying inert markup in
// provider, group, role, job, lookup, quick-list, document-template, queue, eForm, report-template,
// measurement, appointment-type, billing and similar tables; cleanup deletes exactly those rows by key.
// Asserted per item: the literal text is visible, no `[data-xp]` element exists in any frame, and the strict
// recorder saw no script error. Implements: wave-6 xss-poison (stored markup / output-encoding walk).
const { runWorkflow } = require('./lib/workflow-session');
const { workflow: walk } = require('./lib/xss-poison-admin-walk');

const workflow = s => walk(s, 2);

module.exports = { workflow };
if (require.main === module) runWorkflow('xss-poison-admin-reports-system', workflow, { openPatient: false });
