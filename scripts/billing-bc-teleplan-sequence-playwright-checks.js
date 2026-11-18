#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Manage Teleplan's Manually Set Sequence #: the number the next Teleplan claim file goes out with
 * (property teleplan_sequence). billing-bc-teleplan-file reads and restores the property; no check
 * presses the page's sequence save. Only that save is pressed: the page's username/password, change
 * password, Get Sequence # and code-list buttons log in to Teleplan (live MSP) and are never touched.
 * User path: Schedule ▸ Administration ▸ Billing ▸ Manage Teleplan (billing/CA/BC/ManageTeleplan, in the
 * Administration panel) ▸ Manually Set Sequence # ▸ save (method=setSequenceNumber, POST). The form is
 * shown only to the BILLING_SUPERUSER login; SKIP when the test login is not it.
 * Asserted: the page shows the stored number as its Last Sequence #; a saved number is stored exactly
 * and shown; a number that is not one, or is out of range (below 0 or above 9999999), stores nothing;
 * LAST, such a refused number says why. No save writes a Teleplan log line (log_teleplantx).
 * Fixtures: none of its own. The sequence is clinic-wide: the check records every teleplan_sequence row
 * first and puts them back exactly (removing a row it added), and asserts it, so it must not run
 * concurrently with other checks (live wrapper: EXCLUSIVE=1). BC installs only (billregion=BC).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { openAdminPage, submitInFrame } = require('./lib/admin-frame');

const ROUTE = 'billing/CA/BC/ManageTeleplan';
const SEQUENCE = 'teleplan_sequence';
const REFUSED = ['12a', '-1', '10000000'];

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Administration ▸ Billing ▸ Manage Teleplan shows the stored sequence and the Manually Set Sequence # form',
  save: 'save with a number stores exactly that number as the sequence and shows it',
  refused: 'save with a number that is not one, or is out of range, stores nothing',
  says: 'a refused sequence number says why',
};

async function workflow(s) {
  const { sql } = s;
  const q = h.sqlString;
  const rows = () => sql.rows(`SELECT id,value,IF(value IS NULL,1,0) FROM property WHERE name=${q(SEQUENCE)} ORDER BY id`);
  const before = rows();
  const floor = Number(sql.value('SELECT IFNULL(MAX(id), 0) FROM property'));
  const logFloor = Number(sql.value('SELECT IFNULL(MAX(log_no), 0) FROM log_teleplantx'));
  s.cleanup(() => {
    h.assert(before.every(([id]) => /^[1-9]\d*$/.test(id)), 'Invalid sequence row identity');
    sql.execute(`DELETE FROM property WHERE name=${q(SEQUENCE)} AND id > ${floor}`);
    for (const [id, value, isNull] of before) {
      sql.execute(`UPDATE property SET value=${isNull === '1' ? 'NULL' : q(value)} WHERE id=${id} AND name=${q(SEQUENCE)}`);
    }
    h.assert(JSON.stringify(rows()) === JSON.stringify(before), 'The Teleplan sequence is not as the check found it');
  });
  // What the page reads (TeleplanSequenceDAO.getLastSequenceNumber): the last row's number, or 0 without one.
  const stored = () => {
    const list = rows();
    return list.length ? list[list.length - 1][1] : '0';
  };
  const shown = async frame => {
    const text = await frame.locator('.alert-info').first().innerText();
    return (/Last Sequence # = (-?\d+)/.exec(text) || [])[1];
  };
  const noTeleplanLog = () => h.assert(sql.value(`SELECT COUNT(*) FROM log_teleplantx WHERE log_no > ${logFloor}`) === '0',
    'Saving the sequence wrote a Teleplan log line');

  let admin;
  let frame;
  const form = () => frame.locator('form', { has: frame.locator('input[name="method"][value="setSequenceNumber"]') });
  async function saveSequence(value) {
    await form().locator('input[name="num"]').fill(value);
    let response;
    ({ response, frame } = await submitInFrame(admin, form().locator('input[type="submit"]'), ROUTE));
    h.assert(response.status() === 200, `The sequence save answered HTTP ${response.status()}`);
    await h.assertNotErrorPage(frame, 'manage-teleplan');
  }

  await s.step(STEP.open, async () => {
    ({ admin, frame } = await openAdminPage(s, ROUTE, { label: 'manage-teleplan',
      skipReason: 'The Administration menu has no Manage Teleplan item (not a BC install)' }));
    if (await form().count() === 0) {
      throw new h.SkipCheck('Manually Set Sequence # is shown only to the BILLING_SUPERUSER login, and the test login is not it');
    }
    h.assert(await shown(frame) === stored(), `Manage Teleplan shows sequence ${await shown(frame)}, not the stored ${stored()}`);
  });

  let saved;
  await s.step(STEP.save, async () => {
    saved = stored() === '4321' ? '4322' : '4321';
    await saveSequence(saved);
    const list = rows();
    h.assert(list.length === Math.max(before.length, 1) && list.every(([, value]) => value === saved),
      `The save stored ${JSON.stringify(list)}, not ${saved} as the sequence`);
    h.assert(await shown(frame) === saved, `After the save Manage Teleplan shows sequence ${await shown(frame)}, not ${saved}`);
    noTeleplanLog();
  });

  await s.step(STEP.refused, async () => {
    for (const value of REFUSED) {
      await saveSequence(value);
      const list = rows();
      h.assert(list.length === Math.max(before.length, 1) && list.every(([, kept]) => kept === saved),
        `Saving "${value}" changed the stored sequence: ${JSON.stringify(list)}`);
      h.assert(await shown(frame) === saved, `After saving "${value}" Manage Teleplan shows sequence ${await shown(frame)}`);
    }
    noTeleplanLog();
  });

  await s.step(STEP.says, async () => {
    for (const value of REFUSED) {
      await saveSequence(value);
      const said = (await frame.locator('.alert-danger').allInnerTexts()).map(text => text.trim()).filter(Boolean);
      h.assert(said.length > 0, `Saving "${value}" as the sequence stored nothing and said nothing`);
    }
  });
}

if (require.main === module) runWorkflow('billing-bc-teleplan-sequence', workflow, { openPatient: false });
module.exports = { workflow, STEP };
