#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5279 section 5: the pandemic-flu (H1N1) form's First Dose and Second Dose boxes.
 * User path: E-Chart ▸ Preventions ▸ H1N1 ▸ Completed, date, lot, comment, First Dose ▸ Save; the record ▸ First Dose
 * off, Second Dose on ▸ Save; the record ▸ First Dose on as well ▸ Save; the record ▸ both off ▸ Save.
 * The semantics asserted are the form's: First Dose and Second Dose are two INDEPENDENT boxes on one record. Ticking
 * one never clears the other, both may be stored together, and neither schedules anything; CARLOS has no automated
 * vaccine-series feature, and this check does not invent one.
 * Asserts, for each case: each ticked box is stored as "true" and each clear box has no row (no stale value survives
 * clearing or changing); the archived record keeps its own boxes; the record reopens with exactly those boxes ticked;
 * the lot and comment are kept. LAST, recording and changing the dose boxes left one live H1N1 and no next date.
 * Fixtures: the runWorkflow FAKE- patient; its preventions and their ext rows are removed in cleanup.
 */
const h = require('./lib/playwright-harness');
const { flagWorkflow } = require('./lib/prevention-h1n1-options');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient } = s;
  const { id } = await flagWorkflow(s, {
    flags: ['dose1', 'dose2'],
    cases: [
      { label: 'First Dose alone is stored and reopens ticked, with Second Dose clear', ticked: ['dose1'] },
      { label: 'changing to Second Dose alone leaves no first-dose value behind', ticked: ['dose2'] },
      { label: 'First Dose and Second Dose are independent: both can be ticked and both are stored', ticked: ['dose1', 'dose2'] },
      { label: 'clearing both stores neither dose value', ticked: [] },
    ],
  });

  await s.step('the dose boxes schedule nothing: one live H1N1, no next date, no added record', async () => {
    const rows = sql.rows(`SELECT id, IFNULL(next_date,'none'), never FROM preventions WHERE demographic_no=${patient} AND deleted=0`);
    h.assert(rows.length === 1 && rows[0][0] === id(), `Expected only the edited H1N1 to be live, found ${rows.length} live record(s)`);
    h.assert(rows[0][1] === 'none' && rows[0][2] === '0', `The dose boxes set next date ${rows[0][1]} / never ${rows[0][2]}`);
    const scheduled = sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient} AND next_date IS NOT NULL`);
    h.assert(scheduled === '0', `${scheduled} record(s) of the patient carry a next date`);
  });
}

if (require.main === module) runWorkflow('prevention-h1n1-dose-flags', workflow);
module.exports = { workflow };
