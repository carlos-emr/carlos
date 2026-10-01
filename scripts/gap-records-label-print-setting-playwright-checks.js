#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Legacy "Print Label" sheet (gap-records, §2.4 master record labels).
 * User path: Schedule ▸ Search ▸ Master Record ▸ Print / Labels ▸ "Print Label" (popup
 * demographic/ViewDemographicLabelPrintSetting) ▸ tick label types, type how many of each and the
 * Left / Top / Height / Gap offsets ▸ "Print Preview / Print" (demographic/ViewDemographicPrintDemographic).
 * Asserts: the settings popup previews the owned patient's name, health number and address; "New Patient
 * Label" ticks the first three label types with 1 / 6 / 0 copies; the print sheet holds exactly the
 * ticked number of blocks (2 + 3 + 1 for the ticked types, none for the unticked), each at the typed Left,
 * stacked downward from the typed Top without overlap, every block carrying the patient's name, the
 * address block only on the address/chart label types, and the sheet's Print button is present
 * (window.print is never invoked). Nothing is written.
 * Fixtures: the runWorkflow FAKE- patient, given an address, phones, chart number and a synthetic
 * Ontario HIN by SQL; removed with the patient by the harness.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { freshOntarioHin } = require('./lib/gap-records-fixtures');

const TIMEOUT = 20000;

async function workflow(s) {
  const { sql, patient, marker, master } = s;
  const hin = freshOntarioHin(sql);
  sql.execute(`UPDATE demographic SET address='9 Label Lane', city='Labelton', province='ON', postal='K1A0B1', phone='613-555-0111',
    phone2='613-555-0112', hin=${h.sqlString(hin)}, ver='AB', hc_type='ON', chart_no='LBL${marker.slice(-6)}' WHERE demographic_no=${patient}
    AND last_name=${h.sqlString(marker)}`);
  let setup;

  await s.step('Print / Labels ▸ Print Label opens the settings popup previewing the patient', async () => {
    await master.locator('button.dropdown-toggle', { hasText: /Print\s*\/\s*Labels/i }).first().click();
    const item = master.locator('ul.dropdown-menu a.dropdown-item', { hasText: /^\s*Print Label\s*$/ }).first();
    h.assert(await item.count() > 0, 'The Master Record offers no Print Label item');
    setup = await s.popup(master, item, 'label-print-setting');
    await setup.locator('form[name="labelprint"]').waitFor({ timeout: TIMEOUT });
    const text = await setup.locator('body').innerText();
    for (const part of [marker, 'Workflow', '9 Label Lane', 'Labelton', hin, 'LBL' + marker.slice(-6)]) {
      h.assert(text.includes(part), 'The label settings preview does not show the patient\'s own details');
    }
  });

  await s.step('"New Patient Label" ticks the first three types with 1 / 6 / 0 copies', async () => {
    await setup.getByRole('link', { name: /New Patient/i }).click();
    const state = await setup.evaluate(() => Object.fromEntries(['label1', 'label2', 'label3'].map(n => [n,
      [document.labelprint[n + 'checkbox'].checked, document.labelprint[n + 'no'].value]])));
    h.assert(JSON.stringify(state) === JSON.stringify({ label1: [true, '1'], label2: [true, '6'], label3: [true, '0'] }),
      `New Patient Label set ${JSON.stringify(state)} instead of 1 / 6 / 0 copies on the first three types`);
  });

  await s.step('Print Preview shows exactly the ticked labels, positioned from the typed offsets', async () => {
    const form = setup.locator('form[name="labelprint"]');
    const set = async (name, value) => form.locator(`[name="${name}"]`).fill(value);
    await form.locator('[name="label1checkbox"]').check();
    await set('label1no', '2');
    await form.locator('[name="label2checkbox"]').check();
    await set('label2no', '3');
    await form.locator('[name="label3checkbox"]').check();
    await set('label3no', '1');
    await form.locator('[name="label4checkbox"]').uncheck();
    await form.locator('[name="label5checkbox"]').uncheck();
    await set('left', '210'); await set('top', '30'); await set('height', '150'); await set('gap', '10');
    await Promise.all([setup.waitForURL(/ViewDemographicPrintDemographic/, { timeout: TIMEOUT }),
      form.locator('input[type="submit"]').click()]);
    await setup.locator('.label-block').first().waitFor({ timeout: TIMEOUT });
    const blocks = await setup.$$eval('.label-block', nodes => nodes.map(node => ({
      left: node.style.left, top: parseInt(node.style.top, 10), text: node.innerText.replace(/\s+/g, ' ').trim(),
    })));
    h.assert(blocks.length === 6, `The print sheet holds ${blocks.length} label blocks instead of 2 + 3 + 1`);
    h.assert(blocks.every(block => block.left === '210px'), 'A label block ignores the typed Left offset');
    h.assert(blocks[0].top === 30, 'The first label does not start at the typed Top offset');
    const tops = blocks.map(block => block.top);
    h.assert(tops.every((top, index) => index === 0 || top > tops[index - 1]), 'Label blocks are not stacked downward without overlap');
    // The sheet's own layout rule (demographicprintdemographic.jsp): the copies of one type step by height + gap/2 and each
    // later type starts at Top + (copies of the earlier types) * (height + gap). Top 30, height 150, gap 10, copies 2 / 3 / 1.
    const typed = { top: 30, height: 150, gap: 10 };
    const expectedTops = [];
    let before = 0;
    for (const copies of [2, 3, 1]) {
      for (let i = 0; i < copies; i++) expectedTops.push(typed.top + before * (typed.height + typed.gap) + i * (typed.height + typed.gap / 2));
      before += copies;
    }
    h.assert(JSON.stringify(tops) === JSON.stringify(expectedTops),
      `The label blocks start at Top ${JSON.stringify(tops)} instead of ${JSON.stringify(expectedTops)} for the typed Top / Height / Gap`);
    h.assert(blocks.every(block => block.text.includes(marker)), 'A label block does not carry the patient\'s name');
    h.assert(blocks.slice(0, 2).every(block => block.text.includes(hin) && !block.text.includes('9 Label Lane')),
      'Label type 1 must carry the health number and no address');
    h.assert(blocks.slice(2).every(block => block.text.includes('9 Label Lane')), 'Label types 2 and 3 must carry the address');
    h.assert(await setup.locator('.print-controls input[type="button"]').count() >= 1, 'The sheet has no Print control');
    await setup.close();
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('gap-records-label-print-setting', workflow, { openPatient: true });
