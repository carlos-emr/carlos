#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check: a stale demographics edit on a patient whose extension rows were never created.
 * Same user path and race as concurrency-demographic-edit (see that file), but the patient has never
 * been saved through the edit form, so session A's stale form carries empty demographicExt ids and
 * re-INSERTs the rows session B's save created: uk_demo_ext (demographic_no, key_val) is violated,
 * the action answers HTTP 500 and A's whole edit is rolled back. Fails at "answered normally".
 * Fixtures: the owned FAKE- patient only. Wave-7 sweep "concurrency".
 */
const { runWorkflow } = require('./lib/workflow-session');
const { makeWorkflow } = require('./concurrency-demographic-edit-playwright-checks');

const workflow = makeWorkflow({ prime: false });
module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-demographic-ext-insert', workflow, { openPatient: true });
