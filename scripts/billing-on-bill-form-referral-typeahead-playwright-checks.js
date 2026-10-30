#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The Ontario bill form's referring-doctor type-ahead (billingON.jsp initRefDocAutocomplete):
 * ViewSearchRefDocAjax answers a name typed in the referral-doctor name field and a number
 * typed in the referral-number field, a deleted specialist is never suggested, and picking a
 * suggestion fills both fields (the referral number, and "Last, First"). Until now only
 * login-playwright-checks touched the route, for its unauthenticated 401.
 *
 * Fixtures: an owned appointment and two owned specialists named with the run's marker, one
 * of them deleted; all removed by their own keys.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { unusedNumber } = require('./lib/bc-billing-fixture');
const { openOntarioBillForm, suggestionsFor, pickSuggestion, requestsWhile } = require('./lib/billing-on-bill-form');

const REFDOC_SEARCH = '/billing/CA/ON/ViewSearchRefDocAjax';

async function workflow(s) {
  const q = h.sqlString;
  const referralTaken = value => s.sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE referralNo=${q(value)}`) !== '0';
  const specialists = [];
  s.cleanup(() => {
    for (const id of specialists) s.sql.execute(`DELETE FROM professionalSpecialists WHERE specId=${id}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM professionalSpecialists WHERE lName LIKE ${q(`${s.marker}%`)}`) === '0',
      'Owned specialists remain');
  });
  const lastName = s.marker;
  const goneLastName = `${s.marker}-gone`;
  const referralNo = unusedNumber('9', 6, referralTaken);
  // The deleted specialist shares the typed number prefix, so only the deleted filter keeps it
  // out of the number search.
  const goneReferralNo = unusedNumber(referralNo.slice(0, 4), 6, value => value === referralNo || referralTaken(value));
  for (const [name, number, deleted] of [[lastName, referralNo, 0], [goneLastName, goneReferralNo, 1]]) {
    specialists.push(h.insertId(s.sql, `INSERT INTO professionalSpecialists
        (fName,lName,referralNo,annotation,lastUpdated,institutionId,departmentId,hideFromView,deleted)
      VALUES ('FAKE',${q(name)},${q(number)},'',NOW(),0,0,0,${deleted})`, 'owned specialist'));
  }

  const { page } = await openOntarioBillForm(s, 'on-bill-form-referral');
  const code = page.locator('input[name="referralCode"]');
  const name = page.locator('input[name="referralDocName"]');
  const expectFilled = async label => {
    h.assert(await code.inputValue() === referralNo, `${label}: the referral number was not filled`);
    h.assert(await name.inputValue() === `${lastName}, FAKE`, `${label}: the referral name was not filled as "Last, First"`);
  };

  await s.step('a one-character term sends no referring-doctor search', async () => {
    const sent = await requestsWhile(page, REFDOC_SEARCH, async () => {
      await name.click();
      await name.fill(lastName.slice(0, 1));
    });
    h.assert(sent === 0, `A one-character term sent ${sent} search(es); the type-ahead starts at two`);
    await name.fill('');
  });

  await s.step('a name finds the owned specialist, never the deleted one, and picking fills number and name', async () => {
    const term = lastName.slice(0, 12);
    const response = suggestionsFor(page, REFDOC_SEARCH, term);
    await name.fill(term);
    const items = await (await response).json();
    const owned = items.find(item => item.referralNo === referralNo);
    h.assert(owned && owned.lastName === lastName && owned.firstName === 'FAKE',
      `The name search did not return the owned specialist: ${JSON.stringify(items.map(item => item.lastName))}`);
    h.assert(!items.some(item => item.lastName === goneLastName || item.referralNo === goneReferralNo),
      'The name search suggested a deleted specialist');
    await pickSuggestion(page, lastName);
    await expectFilled('name pick');
  });

  await s.step('a referral number finds the owned specialist and picking fills number and name', async () => {
    await code.fill('');
    await name.fill('');
    const term = referralNo.slice(0, 4);
    const response = suggestionsFor(page, REFDOC_SEARCH, term);
    await code.fill(term);
    const items = await (await response).json();
    h.assert(items.some(item => item.referralNo === referralNo), 'The number search did not return the owned specialist');
    h.assert(!items.some(item => item.referralNo === goneReferralNo), 'The number search suggested a deleted specialist');
    // Suggestions are rendered by name (renderRefDocItem), not by number.
    await pickSuggestion(page, lastName);
    await expectFilled('number pick');
  });
}

if (require.main === module) runWorkflow('billing-on-bill-form-referral-typeahead', workflow, { openMaster: false });
module.exports = { workflow };
