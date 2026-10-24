#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Special characters and field lengths on the provider record forms (wave 6, boundary values).
 * User path: Schedule > Administration > User Management > Add a Provider Record > Search/Edit Provider Records >
 * the provider's number link > Update Record.
 * Asserts: a provider name with an apostrophe, accents, CJK, an emoji, "&amp;" and quotes, and an address with
 * "%41", "+", ";" and a backslash, is stored byte for byte; Search/Edit Provider Records finds it by the apostrophe
 * surname; the edit form redisplays every value identically and an unchanged Update leaves the columns as they were.
 * Last (the defect): an e-mail address one character past the 60-character column is refused or visibly limited.
 * Fixtures: one provider row created through the UI under an unused number; cleanup deletes the rows carrying that
 * number from provider and its child tables and asserts they are gone.
 * Implements the wave-6 "boundary values" pattern, Part 1 (provider name, address, e-mail).
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const b = require('./lib/boundary-values');
const { clickOpensPopupOrNavigates, dataTableRows } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const CHILD_TABLES = ['providerbillcenter', 'providersite', 'providerArchive', 'property', 'secUserRole', 'security'];

function pickUnusedProviderNo(sql) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const candidate = String(randomInt(700000, 999000));
    const quoted = h.sqlString(candidate);
    const used = sql.value(`SELECT ${['provider', ...CHILD_TABLES].map(table => `(SELECT COUNT(*) FROM ${table} WHERE provider_no=${quoted})`).join(' + ')}`);
    if (used === '0') return candidate;
  }
  h.assert(false, 'No unused provider number was found in the fixture range');
  return null;
}

async function openSection(admin, name, selector) {
  const link = admin.locator('#adminNav').getByRole('link', { name, exact: true, includeHidden: true });
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, `The ${name} iframe did not load`);
  await frame.locator(selector).waitFor();
  return frame;
}

async function navigateFrame(admin, frame, locator) {
  const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: TIMEOUT });
  navigated.catch(() => {});
  await locator.click();
  await navigated;
  await frame.waitForLoadState('networkidle', { timeout: TIMEOUT });
}

async function workflow(s) {
  const { sql, context, recorder, marker } = s;
  const T = b.TOKENS;
  const providerNo = pickUnusedProviderNo(sql);
  const no = h.sqlString(providerNo);
  const tag = `FAKE-PW${marker.slice(-6)}`;
  s.cleanup(() => {
    const owned = sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${no} AND last_name LIKE ${h.sqlString(`${tag}%`)}`);
    h.assert(owned === '1' || sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${no}`) === '0', 'Provider fixture ownership changed; refusing to clean up');
    for (const table of CHILD_TABLES) sql.execute(`DELETE FROM ${table} WHERE provider_no=${no}`);
    sql.execute(`DELETE FROM provider WHERE provider_no=${no} AND last_name LIKE ${h.sqlString(`${tag}%`)}`);
    h.assert(sql.value(`SELECT ${['provider', ...CHILD_TABLES].map(table => `(SELECT COUNT(*) FROM ${table} WHERE provider_no=${no})`).join('+')}`) === '0', 'Owned provider rows were not removed');
  });
  const typed = {
    last_name: tag + T.apostrophe,
    first_name: `${T.latin} ${T.cjk}`.slice(0, 30),
    address: `1 Rue Zoë ${T.entity} ${T.quotes} ${T.percent} ${T.plus} ${T.semicolon}`.slice(0, 40),
    specialty: `${T.emoji} ${T.backslash} GP`,
  };
  const providerRow = `provider_no=${no}`;
  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label: 'provider-admin', timeout: TIMEOUT });
  let frame;

  await s.step('Add a Provider Record stores a name, address and specialty with special characters byte for byte', async () => {
    frame = await openSection(admin, 'Add a Provider Record', 'form[name="searchprovider"] input[name="last_name"]');
    const form = frame.locator('form[name="searchprovider"]');
    if ((await form.locator('input[name="provider_no"]').getAttribute('readonly')) !== null) {
      throw new h.SkipCheck('AUTO_GENERATE_PROVIDER_NO is on: the add form assigns numbers, so a chosen number cannot be exercised');
    }
    await form.locator('input[name="provider_no"]').fill(providerNo);
    await form.locator('input[name="last_name"]').fill(typed.last_name);
    await form.locator('input[name="first_name"]').fill(typed.first_name);
    await form.locator('select[name="provider_type"]').selectOption('doctor');
    await form.locator('input[name="specialty"]').fill(typed.specialty);
    await form.locator('input[name="address"]').fill(typed.address);
    await form.locator('select[name="sex"]').selectOption('M');
    await navigateFrame(admin, frame, form.locator('input[type="submit"]'));
    h.assert((await frame.locator('h1').first().innerText()).trim() === 'Successful Addition of a Provider Record.', 'The add did not report success');
    // The provider form upper-cases some boxes on blur (upCaseCtrl), by design: accept the typed text or its upper-case form.
    for (const [column, value] of Object.entries(typed)) {
      const stored = b.readStored(sql, 'provider', column, providerRow);
      h.assert(stored.hex === b.hex(value) || stored.hex === b.hex(value.toUpperCase()), `Provider ${column}: stored value differs from what was typed: ${b.explainMismatch(value.toUpperCase(), stored)}`);
    }
  });

  await s.step('Search/Edit Provider Records finds the provider by the apostrophe surname', async () => {
    frame = await openSection(admin, 'Search/Edit Provider Records', 'input[name="keyword"]');
    await frame.locator('input[name="search_mode"][value="search_name"]').check();
    await frame.locator('input[name="keyword"]').fill(typed.last_name);
    await navigateFrame(admin, frame, frame.locator('input[name="button"], button[name="button"]').first());
    const { rows } = await dataTableRows(frame, '#tblResults', { timeout: TIMEOUT });
    h.assert(await rows.filter({ has: frame.getByRole('link', { name: providerNo, exact: true }) }).count() === 1,
      'Search by the provider surname (with its apostrophe) did not list the stored provider');
  });

  let before;
  await s.step('the edit form redisplays every value identically and an unchanged Update changes no column', async () => {
    await navigateFrame(admin, frame, frame.getByRole('link', { name: providerNo, exact: true }));
    const form = frame.locator('form[name="updatearecord"]');
    const wrong = [];
    for (const name of Object.keys(typed)) {
      const stored = Buffer.from(b.readStored(sql, 'provider', name, providerRow).hex, 'hex').toString('utf8');
      if (await form.locator(`[name="${name}"]`).first().inputValue() !== stored) wrong.push(name);
    }
    h.assert(wrong.length === 0, `The provider edit form does not redisplay what was stored: ${wrong.join(', ')}`);
    const snapshot = () => sql.rows(`SELECT HEX(last_name), HEX(first_name), HEX(address), HEX(specialty) FROM provider WHERE ${providerRow}`)[0].join('|');
    before = snapshot();
    await form.locator('#statusActive').check();
    await navigateFrame(admin, frame, form.locator('input[name="subbutton"]'));
    h.assert(/Update a Provider Record Successfully/.test(await frame.locator('h2').first().innerText()), 'The unchanged update did not report success');
    h.assert(snapshot() === before, 'An Update with no edits altered the stored name, address or specialty (re-encoding on save)');
  });

  await s.step('an e-mail address past the 60-character column is refused or visibly limited, never silently cut', async () => {
    const column = b.columnLength(sql, 'provider', 'email');
    const over = `${'e'.repeat(column + 1 - '@example.org'.length)}@example.org`;
    h.assert(b.cpLength(over) === column + 1, 'Test bug: the e-mail is not one past the column');
    frame = await openSection(admin, 'Search/Edit Provider Records', 'input[name="keyword"]');
    await frame.locator('input[name="search_mode"][value="search_providerno"]').check();
    await frame.locator('input[name="keyword"]').fill(providerNo);
    await navigateFrame(admin, frame, frame.locator('input[name="button"], button[name="button"]').first());
    await navigateFrame(admin, frame, frame.getByRole('link', { name: providerNo, exact: true }));
    const form = frame.locator('form[name="updatearecord"]');
    const original = b.readStored(sql, 'provider', 'email', providerRow).hex;
    await form.locator('input[name="email"]').fill(over);
    const shown = await form.locator('input[name="email"]').inputValue();
    await form.locator('#statusActive').check();
    await navigateFrame(admin, frame, form.locator('input[name="subbutton"]'));
    const after = b.readStored(sql, 'provider', 'email', providerRow).hex;
    if (b.cpLength(shown) <= column) {
      // Visibly limited: the box itself held the value to the column, so exactly that text must be stored.
      h.assert(after === b.hex(shown), `The e-mail box limited the text to ${b.cpLength(shown)} characters but the stored value differs from it`);
    } else if (after === original) {
      // Refused: the row must be unchanged and the page must say the save did not happen.
      const page = (await frame.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');
      // A crash page ("Data too long" 500) also leaves the row unchanged, so only a length-specific message counts.
      h.assert(!/Update a Provider Record Successfully/i.test(page) && b.lengthRefusal(page),
        `The e-mail box accepted ${b.cpLength(shown)} characters (provider.email holds ${column}) and the save changed nothing, but the page gave no visible refusal`);
    } else {
      b.assertNotSilentlyTruncated(sql, 'provider', 'email', providerRow, shown, 'Provider e-mail past the column');
      h.assert(false, `The e-mail box accepted ${b.cpLength(shown)} characters but provider.email holds ${column}, and nothing refused the save`);
    }
  });
}

if (require.main === module) runWorkflow('boundary-provider-text', workflow, { openPatient: false });
module.exports = { workflow };
