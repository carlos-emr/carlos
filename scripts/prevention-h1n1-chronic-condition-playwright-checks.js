#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5279 section 1: the pandemic-flu (H1N1) form's chronic-condition categories.
 * User path: E-Chart ▸ Preventions ▸ H1N1 ▸ Completed, date, lot, comment, a Chronic Condition ▸ Save; then for each
 * further category the record ▸ Chronic Condition ▸ Save; last the record ▸ Chronic Condition No ▸ Save.
 * Asserts: a new H1N1 opens at No and offers exactly No, Yes and the nine named categories; every category reaches
 * preventionsExt as its exact option value and reopens selected, and each change archives the record it replaced with
 * that record's category still on it; going back to No stores "false" while the archived record keeps the category;
 * the lot and comment are never lost. Every category is compared, and all mismatches are reported together.
 * Fixtures: the runWorkflow FAKE- patient; its preventions and their ext rows are removed in cleanup.
 */
const { CHRONIC_CONDITIONS, categorySelectWorkflow } = require('./lib/prevention-h1n1-options');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  await categorySelectWorkflow(s, {
    field: 'chronic', label: 'Chronic Condition', noun: 'chronic-condition category', expected: CHRONIC_CONDITIONS,
  });
}

if (require.main === module) runWorkflow('prevention-h1n1-chronic-condition', workflow);
module.exports = { workflow };
