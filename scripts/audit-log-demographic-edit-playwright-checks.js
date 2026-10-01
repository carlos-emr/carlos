#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of a demographic edit: right row, right patient, no PHI (wave 7 sweep `audit-log`).
 *
 * User path: Schedule > Search > Master Record > Edit > change city, postal code, phone and e-mail > Update Record
 * > Master Record reloads.
 *
 * Asserts, scoped to the owned patient: the edit wrote exactly one update/demographic row with the provider,
 * the client address, demographic_no = the patient and contentId = the patient; the new city, postal code,
 * phone and e-mail (and the patient's name) appear in none of the content, contentId and data columns of any
 * audit row about the patient. The violated expectations are reported together in the last step.
 *
 * Fixtures: the harness's owned synthetic patient (FAKE- name) and its audit rows; cleanup removes the
 * rows scoped to the patient and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { openEditForm } = require('./demographic-edit-update-playwright-checks');
const { auditProbe, phiLeaks, incomplete } = require('./lib/audit-log-helpers');

async function workflow(s) {
  const { sql, marker, patient, provider, master } = s;
  const probe = auditProbe({ sql, patient });
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  s.cleanup(() => probe.cleanup());
  const values = { city: `CITY${marker.slice(-8)}`, postal: 'X0X0X0', phone: '555-0142', email: `pw-${marker.slice(-8).toLowerCase()}@example.invalid` };
  let before;

  await s.step('Update Record saves the new contact details', async () => {
    await probe.settle(2500);
    before = probe.mark();
    await openEditForm(master, 20000);
    for (const [name, value] of Object.entries(values)) {
      const input = master.locator(`[name="${name}"]`).first();
      await input.scrollIntoViewIfNeeded().catch(() => {});
      await input.fill(value);
    }
    await ui.clickAndAwaitReload(master, master.locator('#updateButton input[type="submit"]').first(), { timeout: 20000, label: 'Update Record' });
    h.assert(sql.value(`SELECT CONCAT(city,'|',phone) FROM demographic WHERE demographic_no=${patient}`) === `${values.city}|${values.phone}`,
      'The edit did not reach the demographic row');
  });

  await s.step('the edit wrote exactly one complete update/demographic row', async () => {
    const rows = await probe.waitFor(all => all.some(r => r.action === 'update' && r.content === 'demographic'), 'demographic edit', { after: before });
    const updates = rows.filter(r => r.action === 'update' && r.content === 'demographic' && r.provider === provider);
    h.assert(updates.length === 1, `The edit wrote ${updates.length} update/demographic rows, expected 1`);
    const problems = incomplete(updates, { provider, patient });
    h.assert(!problems.length, `The update/demographic row is incomplete: ${problems.join(', ')}`);
    h.assert(updates[0].contentId === String(patient), 'The update/demographic row names another contentId');
  });

  await s.step('no audit row about the patient carries the new contact details or the name', async () => {
    const leaks = phiLeaks(probe.rows(), [values.city, values.phone, values.email, values.postal, marker]);
    expect(!leaks.length, `Audit rows carry patient data typed into the edit: ${leaks.join(', ')}`);
  });

  await s.step('every expectation of the demographic edit audit trail held', async () => {
    h.assert(!defects.length, `Audit-trail defects on the demographic edit path:\n  - ${defects.join('\n  - ')}`);
  });
}

if (require.main === module) runWorkflow('audit-log-demographic-edit', workflow);
module.exports = { workflow };
