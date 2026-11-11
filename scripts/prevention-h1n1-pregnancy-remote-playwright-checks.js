#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5279 section 2: the pandemic-flu (H1N1) form's Pregnant and Remote Setting boxes.
 * User path: E-Chart ▸ Preventions ▸ H1N1 ▸ Completed, date, lot, comment, Pregnant ▸ Save; the record ▸ Pregnant off,
 * Remote Setting on ▸ Save; the record ▸ both on ▸ Save; the record ▸ both off ▸ Save.
 * Asserts, for each case: each ticked box is stored as "true" and each clear box has no row at all (clearing a box
 * leaves no stale value behind); the record it replaced is archived with its own boxes unchanged; the record reopens
 * with exactly the same boxes ticked and every other box on the form as it was; the lot and comment are kept.
 * Fixtures: the runWorkflow FAKE- patient; its preventions and their ext rows are removed in cleanup.
 */
const { flagWorkflow } = require('./lib/prevention-h1n1-options');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  await flagWorkflow(s, {
    flags: ['pregnant', 'remote'],
    cases: [
      { label: 'Pregnant alone is stored and reopens ticked, with Remote Setting clear', ticked: ['pregnant'] },
      { label: 'clearing Pregnant and ticking Remote Setting leaves no pregnancy value behind', ticked: ['remote'] },
      { label: 'Pregnant and Remote Setting together are both stored and reopen ticked', ticked: ['pregnant', 'remote'] },
      { label: 'clearing both leaves neither value stored and both boxes clear', ticked: [] },
    ],
  });
}

if (require.main === module) runWorkflow('prevention-h1n1-pregnancy-remote', workflow);
module.exports = { workflow };
