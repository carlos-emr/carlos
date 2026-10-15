#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #3437: enter both prevention dialogs through the real CVC picker.
// Owns a synthetic patient and two temporary catalogue mappings; never saves a prevention.
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { verifyFooterPopup } = require('./page-health-footer-links-playwright-checks');

async function checkPair(s, page) {
  const sourceUrl = page.url();
  const popups = [];
  try {
    for (const text of ['About', 'License']) {
      const link = page.locator(`a[href*="/encounter/View${text}"]`);
      h.assert(await link.count() === 1, `The dialog has no unique ${text} popup link`);
      popups.push({ text, page: await s.popup(page, link, `prevention footer ${text}`) });
      h.assert(await link.getAttribute('target') === '_blank', 'No-JS fallback can replace the editor');
      h.assert((await link.getAttribute('rel') || '').split(/\s+/).includes('noopener'),
        'Native modified-click fallback lost opener isolation');
    }
    h.assert(popups.every(popup => !popup.page.isClosed()) && popups[0].page !== popups[1].page,
      'About and License did not remain open independently');
    for (const popup of popups) await verifyFooterPopup(s, page, popup.page, popup.text, sourceUrl, 20000);
  } finally {
    for (const popup of popups) await popup.page.close().catch(() => {});
  }
}

async function workflow(s) {
  const { sql, marker, patient } = s;
  s.cleanup(() => h.assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`) === '0',
    'Unexpected prevention rows retained with their owned patient for investigation'));
  // Initialize the application's cached display catalogue before adding temporary
  // mappings, so cleanup cannot leave synthetic entries in its in-memory list.
  const index = await s.popup(s.master,
    s.master.locator('a').filter({ hasText: /^\s*Preventions\s*$/ }).first(), 'preventions');
  h.assert(await index.locator('a[onclick*="prevention=Tdap&"]').count() > 0,
    'The installed catalogue does not offer Tdap');
  const concepts = [0, 1].map(index => `9${Date.now()}${randomInt(100, 999)}${index}`);
  const ids = concepts.map(h.sqlString).join(',');
  const label = h.sqlString(marker);
  h.assert(sql.value(`SELECT COUNT(*) FROM CVCImmunization WHERE snomedConceptId IN (${ids})`) === '0',
    'Owned catalogue identifiers already exist');
  h.assert(sql.value(`SELECT COUNT(*) FROM CVCMapping WHERE cvcSnomedId IN (${ids})`) === '0',
    'Owned catalogue mappings already exist');
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM CVCImmunization WHERE snomedConceptId IN (${ids})
      AND displayName<>${label}`) === '0', 'Catalogue fixture ownership changed');
    sql.execute(`DELETE FROM CVCMapping WHERE oscarName='Tdap' AND cvcSnomedId IN (${ids});
      DELETE FROM CVCImmunization WHERE snomedConceptId IN (${ids}) AND displayName=${label}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM CVCMapping WHERE cvcSnomedId IN (${ids}))
      +(SELECT COUNT(*) FROM CVCImmunization WHERE snomedConceptId IN (${ids}))`) === '0',
    'Owned catalogue fixtures were not removed');
  });
  for (const concept of concepts) {
    sql.execute(`INSERT INTO CVCImmunization
      (versionId,snomedConceptId,displayName,picklistName,generic,parentConceptId,ispa)
      VALUES (0,${h.sqlString(concept)},${label},${label},1,NULL,0);
      INSERT INTO CVCMapping (oscarName,cvcSnomedId,preferCVC)
      VALUES ('Tdap',${h.sqlString(concept)},0)`);
  }

  // Reload through the browser to expose the now-ambiguous mapping in the real UI.
  await index.reload({ waitUntil: 'domcontentloaded' });
  await h.assertNotErrorPage(index, 'preventions after fixture setup');
  const pickerLink = index.locator('a[onclick*="ViewAddPreventionDataDisambiguate"][onclick*="prevention=Tdap&"]').first();
  if (await pickerLink.count() !== 1) {
    console.log('Prevention links:', await index.locator('a[onclick*="PreventionData"]').evaluateAll(links =>
      links.map(link => ({ text: link.textContent.trim(), onclick: link.getAttribute('onclick') }))));
  }
  h.assert(await pickerLink.count() === 1, 'The Preventions UI did not offer the owned vaccine picker');
  const picker = await s.popup(index, pickerLink, 'prevention vaccine picker');
  h.assert(new URL(picker.url()).pathname.endsWith('/ViewAddPreventionDataDisambiguate'),
    'The UI did not reach the vaccine picker');
  await s.step('vaccine picker opens independent About/License popups and their Close controls work',
    () => checkPair(s, picker));

  const choice = picker.locator(`a[href*="snomedId=${concepts[0]}"]`);
  h.assert(await choice.count() === 1, 'The picker omitted the owned vaccine choice');
  await ui.clickAndAwaitReload(picker, choice, { label: 'owned vaccine choice', timeout: 20000 });
  h.assert(new URL(picker.url()).pathname.endsWith('/ViewAddPreventionData'), 'The choice did not open the entry form');
  await h.assertNotErrorPage(picker, 'prevention entry form');
  const comments = picker.locator('[name="comments"]').first();
  const draft = `${marker} unsaved prevention draft`;
  await comments.fill(draft);
  await s.step('entry-form popup Close leaves the unsaved prevention intact', async () => {
    await checkPair(s, picker);
    h.assert(await comments.inputValue() === draft, 'The footer discarded the unsaved prevention');
  });
  await s.step('blocked popups leave the form and its draft intact', async () => {
    const sourceUrl = picker.url();
    const pageCount = s.context.pages().length;
    await picker.evaluate(() => {
      window.pwOriginalPopupOpen = window.open;
      window.pwBlockedPopupCalls = 0;
      window.open = () => { window.pwBlockedPopupCalls++; return null; };
    });
    try {
      for (const text of ['About', 'License']) {
        await picker.locator(`a[href$="/encounter/View${text}"]`).click();
      }
      h.assert(await picker.evaluate(() => window.pwBlockedPopupCalls) === 2, 'Blocked-popup path was not exercised');
      h.assert(picker.url() === sourceUrl && await comments.inputValue() === draft, 'A blocked popup discarded the form');
      h.assert(s.context.pages().length === pageCount, 'A blocked popup opened an unintended fallback tab');
    } finally {
      if (!picker.isClosed()) await picker.evaluate(() => {
        window.open = window.pwOriginalPopupOpen;
        delete window.pwOriginalPopupOpen;
        delete window.pwBlockedPopupCalls;
      });
    }
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient}`) === '0',
    'The footer workflow unexpectedly saved a prevention');
}

if (require.main === module) runWorkflow('prevention-footer-popups', workflow);
module.exports = { workflow };
