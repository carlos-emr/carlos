#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5279 section 3: the pandemic-flu (H1N1) form's healthcare-worker categories.
 * User path: E-Chart ▸ Preventions ▸ H1N1 ▸ Completed, date, lot, comment, a Health Care Worker category ▸ Save; then
 * for each further category the record ▸ Health Care Worker ▸ Save; last the record ▸ Health Care Worker No ▸ Save.
 * Asserts: a new H1N1 opens at No and offers exactly No, Yes, Acute Care, Chronic Care, Ambulatory/Community Care,
 * Emergency Medical Services, Laboratory, Public Health, Pharmacy and Vaccine Mfr; every category reaches preventionsExt
 * as its exact option value and reopens selected, and each change between categories archives the record it replaced
 * with that record's category still on it; going back to No stores "false" while the archived record keeps the
 * category; the lot and comment are never lost. Every category is compared, and all mismatches are reported together.
 * Fixtures: the runWorkflow FAKE- patient; its preventions and their ext rows are removed in cleanup.
 */
const { HEALTHCARE_WORKER_CATEGORIES, categorySelectWorkflow } = require('./lib/prevention-h1n1-options');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  await categorySelectWorkflow(s, {
    field: 'healthcareworker', label: 'Health Care Worker', noun: 'healthcare-worker category', expected: HEALTHCARE_WORKER_CATEGORIES,
  });
}

if (require.main === module) runWorkflow('prevention-h1n1-healthcare-worker', workflow);
module.exports = { workflow };
