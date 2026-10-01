#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The diagnostic (Dx) code search: do its results equal the codes that match what was typed? (wave 7, search-sort)
 * User path: Schedule > Preferences (Edit your personal setting) > Clinical > Default Billing Dx Code > Search > the Dx search window:
 * a code range, or a description > Search > a result row (billing/CA/ON/ViewBillingDigSearch, billingDigSearch.jsp).
 * Asserts, with two owned codes whose descriptions carry a letters-only run word, one of them with a digit in it
 * ("... diabetes type 2 review"): the 200-299 range lists exactly the codes starting with 2; the run word lists both owned
 * codes; the run word plus "asthma" lists only the asthma code, selects it into the Default Dx box and raises no script error;
 * "Cushing" (a shipped description) lists a row that reads as text; and the run word plus "diabetes type 2" lists the owned
 * diabetes code and nothing that does not match what was typed. Defects are collected and asserted together in the last step.
 * Fixtures: two diagnosticcode rows inserted by SQL (descriptions start with the run word); cleanup deletes them and asserts
 * none remain. The Default Dx box is only typed into, never saved.
 * Implements the wave-7 "search-sort" pattern (list search results equal the matching rows; single-result and mixed input).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const k = require('./lib/search-sort-helpers');

/** The Dx search iframe of the Preferences modal as a Frame. */
async function dxFrame(prefs) {
  const handle = await prefs.locator('#dxSearchFrame').elementHandle();
  const frame = await handle.contentFrame();
  h.assert(frame, 'The Dx search iframe has no content frame');
  return frame;
}

/** Submit the Dx search form in the iframe and wait for the iframe to reload. */
async function dxSearch(prefs, { range, text }) {
  const frame = await dxFrame(prefs);
  await frame.locator('form#codesearch').waitFor({ state: 'visible', timeout: 30000 });
  if (range !== undefined) await frame.locator('select[name="coderange"]').selectOption(range);
  await frame.locator('input[name="codedesc"]').fill(text || '');
  const navigated = prefs.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
  await frame.locator('input[name="search1"]').click();
  await navigated;
  await frame.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  await frame.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return frame;
}

/** Result rows as {code, text} (the description sits in a text input). */
async function dxRows(frame) {
  return frame.$$eval('#diagcode tbody tr', trs => trs.filter(tr => tr.querySelector('td a')).map(tr => ({
    code: tr.querySelector('td a').textContent.trim(),
    text: ((tr.querySelector('input.form-control') || {}).value || '').trim(),
  })));
}

async function workflow(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  // A letters-only token: digits in a search are read as a code prefix, so the run word must not carry any.
  const word = 'Qz' + marker.slice(-8).replace(/./g, c => 'abcdefghijklmnop'[parseInt(c, 16)]);
  const codeA = 'ZQ' + String(parseInt(marker.slice(-3), 16) % 1000).padStart(3, '0');
  const codeB = 'ZR' + codeA.slice(2);
  const owned = `description LIKE ${q(`${word}%`)}`;
  h.assert(sql.value(`SELECT COUNT(*) FROM diagnosticcode WHERE diagnostic_code IN (${q(codeA)},${q(codeB)})`) === '0', 'The fixture dx codes already exist');
  s.cleanup(() => {
    sql.execute(`DELETE FROM diagnosticcode WHERE ${owned}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM diagnosticcode WHERE ${owned}`) === '0', 'Owned dx codes were not removed');
  });
  sql.execute(`INSERT INTO diagnosticcode (diagnostic_code, description, status, region) VALUES
    (${q(codeA)}, ${q(`${word} diabetes type 2 review`)}, 'A', 'ON'), (${q(codeB)}, ${q(`${word} asthma review`)}, 'A', 'ON')`);
  const defects = [];
  // CSRFGuard's client throws on result inputs named with numeric dx codes (known, scratchpad L173 / finding 112): that
  // error is consumed here so it is not blamed on every search; anything else the search raises is kept.
  const search = async (prefs, opts) => {
    let frame;
    const { failed, pageErrors, thrown } = await k.collectHttpFailures(s, async () => {
      frame = await dxSearch(prefs, opts);
      await prefs.waitForTimeout(400);
    });
    if (thrown) throw thrown;
    return { frame, errors: [...failed, ...pageErrors.filter(e => !/csrfguard/.test(e) && !/reading 'name'/.test(e))] };
  };

  const prefs = await s.popup(s.schedule, s.schedule.getByTitle(/Edit your personal setting/i).first(), 'preferences');
  await prefs.locator('button[data-bs-target="#secClinical"]').click();
  await prefs.locator('#dxCode').waitFor({ state: 'visible', timeout: 30000 });
  await prefs.locator('button[data-bs-target="#dxSearchModal"]').click();
  await prefs.locator('#dxSearchModal.show').waitFor({ timeout: 30000 });

  await s.step('the 200-299 range lists exactly the codes that start with 2', async () => {
    const { frame } = await search(prefs, { range: '2', text: '' });
    const shown = (await dxRows(frame)).map(r => r.code).sort();
    const expected = sql.rows("SELECT DISTINCT diagnostic_code FROM diagnosticcode WHERE diagnostic_code LIKE '2%' AND TRIM(diagnostic_code)<>''")
      .map(r => r[0]).sort();
    h.assert(expected.length > 10 && JSON.stringify(shown) === JSON.stringify(expected),
      `The 200-299 range listed ${shown.length} codes, the table has ${expected.length} starting with 2`);
  });

  await s.step('the run word lists both owned codes; the run word plus "asthma" lists only the asthma code', async () => {
    let { frame } = await search(prefs, { text: word });
    const both = (await dxRows(frame)).map(r => r.code).sort();
    h.assert(JSON.stringify(both) === JSON.stringify([codeA, codeB].sort()), `The run word listed ${both.join(', ')}, not the two owned codes`);
    const single = await search(prefs, { text: `${word} asthma` });
    frame = single.frame;
    if (single.errors.length) {
      defects.push(`a search with exactly one match raised ${single.errors.join(' / ')} in the Dx search window (the single result is selected before the modal's handler is attached)`);
    }
    const rows = await dxRows(frame);
    h.assert(rows.length === 1 && rows[0].code === codeB, `The run word plus "asthma" listed ${rows.map(r => r.code).join(', ') || 'nothing'}, not only the asthma code`);
    const box = await prefs.locator('#dxCode').inputValue();
    if (box !== codeB.slice(0, 3) && box !== codeB) defects.push(`the single matching code was not selected into the Default Dx box (it reads "${box}")`);
  });

  await s.step('a shipped description with an apostrophe reads as text', async () => {
    const { frame } = await search(prefs, { text: 'Cushing' });
    const rows = await dxRows(frame);
    h.assert(rows.length >= 1, 'The search for "Cushing" found nothing');
    const raw = rows.filter(r => /&#\d+;|&[a-z]+;/i.test(r.text));
    if (raw.length) defects.push(`description of dx code ${raw[0].code} is shown as "${raw[0].text.slice(0, 60)}" with a literal HTML entity (the reference data stores &#146; for an apostrophe)`);
  });

  await s.step('a description with a digit lists the matching code and nothing that does not match', async () => {
    const { frame } = await search(prefs, { text: `${word} diabetes type 2` });
    const rows = await dxRows(frame);
    h.assert(rows.some(r => r.code === codeA), 'The owned diabetes code was not found by its own description');
    const stray = rows.filter(r => r.code !== codeA);
    if (stray.length) defects.push(`typing "... diabetes type 2" also lists ${stray.length} codes that do not match (every code starting with 2 is added)`);
  });

  await s.step('every Dx search result equalled the codes matching what was typed', async () => {
    h.assert(defects.length === 0, `Dx code search defects: ${defects.join('; ')} `
      + '(BillingDiagCodeViewModelAssembler.classify splits the input into its digits and its other characters and searches both; '
      + 'billingDigSearch.jsp:44 CodeAttach reads self.opener, which is null inside the Preferences iframe)');
  });
}

if (require.main === module) runWorkflow('search-sort-billing-dx-search', workflow, { openPatient: false });
module.exports = { workflow };
