#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Drug names containing quotes keep their exact spelling from the staged card through the saved
 * prescription to its reprint (#3952).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Rx (rx/choosePatient popup) ▸ type a name ▸
 * Custom Drug (confirm) ▸ the staged card (its "F" Add to Favorites link) ▸ drug search ▸ pick a
 * DrugRef product whose name has an apostrophe ▸ Instructions ▸ Save And Print (the print window,
 * rx/viewScript, and its ViewPreview2 preview) ▸ the Rx page's drug profile ▸ Reprint ▸ the saved
 * script's row ▸ the patient's E-Chart Medications panel (its item text and tooltip).
 * Asserts: a custom drug named with an apostrophe, a double-quote pair and an accent is staged,
 * offered to Add to Favorites, stored in drugs.customName and drugs.special, listed in the drug
 * profile, and shown on the print preview and on the reprint preview exactly as typed: no backslash
 * before a quote (prescribe.jsp escaped quotes by hand before encoding them again) and no
 * replacement character for the accent (it re-decoded the name from ISO-8859-1 as UTF-8). A DrugRef
 * product with an apostrophe (RX_QUOTE_DRUG_TERM / RX_QUOTE_DRUG_NAME, default CHILDREN'S BENADRYL
 * ALLERGY) goes the same way through drugs.BN: createNewRx ran the picked name through
 * Encode.forJava. When this install's DrugRef has no such product, that half is reported and left
 * to the custom drug; the custom half always runs.
 * Fixtures: the owned FAKE- patient; every drugs, prescription and signature row the save writes
 * is the owned patient's and is removed in cleanup, which asserts nothing remains.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');
const { assertStored, hex } = require('./lib/boundary-values');

const q = h.sqlString;
const CATALOGUE_TERM = process.env.RX_QUOTE_DRUG_TERM || 'BENADRYL ALLERGY';
const CATALOGUE_NAME = process.env.RX_QUOTE_DRUG_NAME || "CHILDREN'S BENADRYL ALLERGY";
const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);
// The two ways the name used to come back wrong: a backslash before a quote, and U+FFFD for an accent.
const mangled = text => /\\['"]/.test(text) || text.includes('�');
const squash = text => String(text).replace(/\s+/g, ' ').trim();

/** The print window is an iframe over the Rx page; poll Playwright's own frame list for it. */
async function viewScriptFrame(page, timeout = 60000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const frame = page.frames().find(f => /\/rx\/viewScript\?/.test(f.url()));
    if (frame) return frame;
    await page.waitForTimeout(250);
  }
  throw new Error('The print window (rx/viewScript) did not open');
}

/**
 * The printable preview nested in the print window, once it shows every expected name. The frame
 * can render its header before its prescription rows, so poll for the names; on timeout return the
 * last text seen so the caller's assertion names what was missing.
 */
async function previewText(script, expected, timeout = 30000) {
  const deadline = Date.now() + timeout;
  let last = null;
  while (Date.now() < deadline) {
    const preview = script.childFrames().find(f => /\/rx\/ViewPreview2\?/.test(f.url()));
    if (preview) {
      last = (await preview.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');
      if (expected.every(name => last.includes(name))) return last;
    }
    await script.waitForTimeout(250);
  }
  if (last === null) throw new Error('The print window has no rendered preview frame (rx/ViewPreview2)');
  return last;
}

/** What the card's "F" (Add to Favorites) link offers as the favourite's name; the prompt is cancelled. */
async function favouritePromptDefault(rx, key) {
  await rx.evaluate(() => {
    window.__rxQuotePrompts = [];
    window.__rxQuotePrompt = window.prompt;
    window.prompt = (message, value) => { window.__rxQuotePrompts.push(value); return null; };
  });
  try {
    await rx.locator(`#set_${key} a[onclick^="addFav("]`).first().click();
    return await rx.evaluate(() => window.__rxQuotePrompts[0]);
  } finally {
    await rx.evaluate(() => { window.prompt = window.__rxQuotePrompt; });
  }
}

/**
 * Stage the DrugRef product named `name` from the search box; returns its card key and the name the
 * autocomplete showed, or null when this install's DrugRef offers no such product.
 */
async function stageCatalogueDrug(rx, term, name) {
  const before = await rx.locator('[id^="drugName_"]').evaluateAll(nodes => nodes.map(node => node.id));
  await rx.locator('#searchString').fill('');
  const [searched] = await Promise.all([
    rx.waitForResponse(r => isPost('/rx/searchDrug')(r)
      && decodeURIComponent((r.request().postData() || '').replace(/\+/g, ' ')).includes(term), { timeout: 60000 }),
    rx.locator('#searchString').pressSequentially(term, { delay: 40 }),
  ]);
  h.assert(searched.ok(), `The DrugRef search for "${term}" answered HTTP ${searched.status()}`);
  const option = rx.locator('ul.ui-autocomplete li.ui-menu-item').filter({ hasText: name }).first();
  await option.waitFor({ state: 'visible', timeout: 20000 }).catch(() => {});
  if (await option.count() === 0) {
    // Absent, not broken: the search succeeded and its own answer does not list the product either.
    h.assert(!(await searched.text()).toUpperCase().includes(name.toUpperCase()),
      `The DrugRef search lists "${name}" but the autocomplete menu does not show it`);
    await rx.locator('#searchString').fill('');
    await rx.keyboard.press('Escape');
    return null;
  }
  const shown = (await option.innerText()).replace(/\s+/g, ' ').trim();
  const [staged] = await Promise.all([
    rx.waitForResponse(r => isPost('/rx/WriteScript')(r)
      && new URLSearchParams(r.request().postData() || '').get('parameterValue') === 'createNewRx'),
    option.click(),
  ]);
  h.assert(staged.ok(), `Staging "${shown}" answered HTTP ${staged.status()}`);
  await rx.waitForFunction(known => document.querySelectorAll('[id^="drugName_"]').length > known.length, before, { timeout: 30000 });
  const fresh = await rx.locator('[id^="drugName_"]').evaluateAll((nodes, known) => nodes.map(node => node.id)
    .filter(id => !known.includes(id)), before);
  h.assert(fresh.length === 1, `Staging "${shown}" added ${fresh.length} cards`);
  return { key: fresh[0].slice('drugName_'.length), shown };
}

async function workflow(s) {
  const { sql, patient } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient};
      DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND moduleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient} AND moduleType='PRESCRIPTION')`) === '0',
    'The owned prescription rows were not removed');
  });
  // Apostrophe, double-quote pair and an accent; the marker keeps it the owned patient's own drug.
  const customName = `${s.marker} O'Neil "Forte" Crème`;
  h.assert(customName.length <= 60, 'The custom drug name does not fit drugs.customName (varchar 60)');

  const rx = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');

  const cards = [];
  await s.step('a custom drug named with quotes and an accent is staged and offered to favourites exactly as typed', async () => {
    const key = await stageCustomDrug(rx, customName);
    const staged = await rx.locator(`#drugName_${key}`).inputValue();
    h.assert(staged === customName, `The staged card is named "${staged}", not "${customName}"`);
    const offered = await favouritePromptDefault(rx, key);
    h.assert(offered === customName, `Add to Favorites offered "${offered}", not "${customName}"`);
    cards.push({ key, name: customName, column: 'customName', row: `customName LIKE ${q(`${s.marker}%`)}` });
  });

  await s.step(`a DrugRef product with an apostrophe (${CATALOGUE_NAME}), when this install offers one, is staged and offered to favourites as the search showed it`, async () => {
    const picked = await stageCatalogueDrug(rx, CATALOGUE_TERM, CATALOGUE_NAME);
    if (!picked) {
      console.log(`  NOTE rx-drug-name-quotes: DrugRef offers no "${CATALOGUE_NAME}" for "${CATALOGUE_TERM}" on this install; `
        + 'the catalogue half is not exercised (set RX_QUOTE_DRUG_TERM / RX_QUOTE_DRUG_NAME)');
      return;
    }
    // The menu label is rendered text (whitespace collapsed); the card holds the stored name itself.
    const staged = await rx.locator(`#drugName_${picked.key}`).inputValue();
    h.assert(squash(staged) === picked.shown, `The search showed "${picked.shown}" but the staged card is named "${staged}"`);
    const offered = await favouritePromptDefault(rx, picked.key);
    h.assert(offered === staged, `Add to Favorites offered "${offered}", not "${staged}"`);
    cards.push({ key: picked.key, name: staged.trim(), column: 'BN', row: "(customName IS NULL OR customName='')" });
  });

  let scriptNo;
  let script;
  await s.step('Save And Print stores each name exactly and the print preview shows it without backslashes', async () => {
    for (const card of cards) {
      await rx.locator(`#instructions_${card.key}`).fill('1 tab PO daily');
      await rx.locator(`#instructions_${card.key}`).blur();
    }
    await rx.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await rx.locator('#saveButton').click();
    script = await viewScriptFrame(rx);
    scriptNo = new URL(script.url()).searchParams.get('scriptId');
    h.assert(/^[1-9]\d*$/.test(scriptNo), 'Save And Print opened no saved script');
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND script_no=${scriptNo}`,
      String(cards.length), `The saved script does not hold the ${cards.length} staged drug(s)`);
    for (const card of cards) {
      // Byte-exact: the column collation ignores case and accents, so an "=" match would miss a lost accent.
      card.drug = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient} AND script_no=${scriptNo} AND ${card.row}`);
      h.assert(/^[1-9]\d*$/.test(card.drug), `No single saved drug row for "${card.name}"`);
      assertStored(sql, 'drugs', card.column, `drugid=${card.drug}`, card.name, `drugs.${card.column}`);
      h.assert(sql.value(`SELECT HEX(LEFT(special, ${Array.from(card.name).length})) FROM drugs WHERE drugid=${card.drug}`)
        === hex(card.name), `drugs.special does not start with "${card.name}" exactly`);
    }
    const stored = sql.rows(`SELECT COALESCE(customName,''), COALESCE(BN,''), special FROM drugs WHERE demographic_no=${patient}`);
    h.assert(stored.every(row => row.every(value => !mangled(value))), `A saved drug row holds an escaped name: ${JSON.stringify(stored)}`);
    await script.locator('#preview').waitFor({ state: 'attached', timeout: 30000 });
    const shown = await previewText(script, cards.map(card => squash(card.name)));
    for (const card of cards) {
      h.assert(shown.includes(squash(card.name)), `The print preview does not show "${card.name}"`);
    }
    h.assert(!mangled(shown), 'The print preview shows a backslash before a quote or a replacement character');
  });

  await s.step('the drug profile lists the saved names as stored', async () => {
    await rx.reload({ waitUntil: 'networkidle' });
    for (const card of cards) {
      const listed = squash(await rx.locator(`#prescrip_${card.drug}`).innerText());
      h.assert(listed.includes(squash(card.name)) && !mangled(listed), `The drug profile lists "${listed}" for "${card.name}"`);
    }
  });

  await s.step('Reprint brings the saved script back with each name exactly as stored', async () => {
    await rx.locator('a').filter({ hasText: /^Reprint$/ }).first().click();
    const row = rx.locator(`#reprint a[onclick*="reprint2('${scriptNo}')"]`).first();
    await row.waitFor({ state: 'visible', timeout: 20000 });
    await row.click();
    const reprint = await viewScriptFrame(rx);
    h.assert(new URL(reprint.url()).searchParams.get('scriptId') === scriptNo, 'The reprint opened another script');
    await reprint.locator('#preview').waitFor({ state: 'attached', timeout: 30000 });
    await rx.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const shown = await previewText(reprint, cards.map(card => squash(card.name)));
    for (const card of cards) {
      h.assert(shown.includes(squash(card.name)), `The reprinted preview does not show "${card.name}"`);
    }
    h.assert(!mangled(shown), 'The reprinted preview shows a backslash before a quote or a replacement character');
    h.assert(sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient}`) === '1',
      'Reprinting wrote another prescription');
  });

  await s.step('the E-Chart Medications panel shows each saved name encoded once, in its text and its tooltip', async () => {
    // EctDisplayRx2Action builds the item; LeftNavBarDisplay.jsp encodes the tooltip itself, so a
    // second encoding in the action showed &#39; in place of the apostrophe.
    const chart = await s.chart();
    const names = cards.map(card => squash(card.name));
    const linksNaming = () => chart.locator('a.links').evaluateAll(anchors => anchors
      .map(anchor => ({ title: anchor.getAttribute('title') || '', text: anchor.textContent || '' })));
    await chart.waitForFunction(wanted => wanted.every(name => [...document.querySelectorAll('a.links')]
      .some(anchor => (anchor.getAttribute('title') || '').replace(/\s+/g, ' ').includes(name))), names, { timeout: 30000 })
      .catch(() => {});
    const links = await linksNaming();
    for (const card of cards) {
      const item = links.find(link => squash(link.title).includes(squash(card.name)));
      h.assert(item, `The Medications panel has no item titled with "${card.name}"`);
      // The visible label is the outline cropped to 45 characters (EctDisplayAction.CROP_LEN_TITLE), so
      // match a leading part of the name that still carries the apostrophe and the opening quote.
      const lead = squash(card.name).slice(0, 40);
      h.assert(squash(item.text).includes(lead), `The Medications item shows "${squash(item.text)}", not "${lead}…"`);
      h.assert(!/&#\d+;|&(amp|quot|lt|gt|apos);/.test(item.title + item.text) && !mangled(item.title + item.text),
        `The Medications panel shows "${card.name}" escaped: title "${item.title}", text "${squash(item.text)}"`);
    }
  });
}

if (require.main === module) runWorkflow('rx-drug-name-quotes', workflow, { openPatient: true });
module.exports = { workflow };
