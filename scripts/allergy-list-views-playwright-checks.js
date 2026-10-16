#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// allergy-list-views: the allergy list's Active / All / Inactive views and Reactivate.
// User path: E-Chart ▸ Allergies (rx/showAllergy) ▸
//   1. the default Active view lists the active allergy only, with its Inactivate and Modify links;
//   2. Inactive lists the inactive allergy only, marked Inactive with a Reactivate link;
//   3. All lists both;
//   4. Reactivate on the inactive allergy opens the dialogue with its details, and Add Allergy makes it
//      active again: one active copy with the same allergen and reaction, nothing refused.
// Step 4 runs last: Reactivate posts the inactive allergy as allergyToArchive, which the stale-
// amendment guard of #4410 (RxAddAllergy2Action) answers with 409 because that allergy is archived.
// allergy-custom-lifecycle covers Inactivate; allergy-stale-amend-refused covers the 409 for a form
// that went stale. Neither uses the views or Reactivate.
// Fixtures: the owned FAKE- patient and two allergies seeded by SQL, one active and one inactive.
// Cleanup deletes the patient's allergies and their partial_date rows and asserts none remain.
const { assert, sqlString } = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const owned = `SELECT allergyid FROM allergies WHERE demographic_no=${patient}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM partial_date WHERE table_name=1 AND table_id IN (${owned});
      DELETE FROM allergies WHERE demographic_no=${patient}`);
    assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}`) === '0', 'Owned allergies were not removed');
  });
  const seed = (description, reaction, archived) => {
    const id = sql.value(`INSERT INTO allergies (demographic_no,entry_date,start_date,DESCRIPTION,TYPECODE,reaction,nonDrug,
        severity_of_reaction,onset_of_reaction,archived,position,lastUpdateDate,providerNo)
      VALUES (${patient},CURDATE(),'2024-03-04',${sqlString(description)},0,${sqlString(reaction)},1,'2','1',${archived},0,NOW(),${sqlString(provider)});
      SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(id || ''), `The ${archived ? 'inactive' : 'active'} allergy fixture was not created`);
    return id;
  };
  const activeName = `${marker.slice(-8).toUpperCase()} ACTIVE`;
  const inactiveName = `${marker.slice(-8).toUpperCase()} GONE`;
  const inactiveReaction = `${marker} FAKE wheeze`;
  const active = seed(activeName, `${marker} FAKE rash`, 0);
  const inactive = seed(inactiveName, inactiveReaction, 1);

  const chart = await s.chart();
  const page = await s.popup(chart, chart.locator('a[onclick*="showAllergy"]').first(), 'allergy-list');
  const row = id => page.locator(`#allergy_${id}`);
  /** Switch the list to one of its views through the view menu, the way the clinician does. */
  async function view(name) {
    await clickAndAwaitReload(page, page.locator('span.view_menu a', { hasText: new RegExp(`^${name}$`) }));
    assert((await page.locator('span.view_selected').innerText()).trim() === name, `The ${name} view is not selected`);
  }

  await s.step('the Active view lists the active allergy only', async () => {
    assert((await page.locator('span.view_selected').innerText()).trim() === 'Active', 'The list does not open on the Active view');
    await row(active).waitFor({ state: 'visible' });
    assert(await row(inactive).count() === 0, 'The Active view lists the inactive allergy');
    assert(await row(active).locator('a.deleteAllergyLink', { hasText: 'Inactivate' }).count() === 1
      && await row(active).locator('a.modifyAllergyLink', { hasText: 'Modify' }).count() === 1,
    'The active allergy does not offer Inactivate and Modify');
  });

  await s.step('the Inactive view lists the inactive allergy only, with Reactivate', async () => {
    await view('Inactive');
    await row(inactive).waitFor({ state: 'visible' });
    assert(await row(active).count() === 0, 'The Inactive view lists the active allergy');
    assert(/^\s*Inactive\b/.test(await row(inactive).locator('td').first().innerText()), 'The inactive allergy is not marked Inactive');
    assert(await row(inactive).locator('a.modifyAllergyLink', { hasText: 'Reactivate' }).count() === 1
      && await row(inactive).locator('a.deleteAllergyLink').count() === 0,
    'The inactive allergy does not offer Reactivate alone');
  });

  await s.step('the All view lists both', async () => {
    await view('All');
    await row(active).waitFor({ state: 'visible' });
    await row(inactive).waitFor({ state: 'visible' });
  });

  await s.step('Reactivate makes the inactive allergy active again', async () => {
    await row(inactive).locator('a.modifyAllergyLink', { hasText: 'Reactivate' }).click();
    const form = page.locator('#RxAddAllergyForm');
    await form.waitFor({ state: 'visible' });
    assert(await form.locator('#reactionDescription').inputValue() === inactiveReaction, 'Reactivate did not open the allergy\'s reaction');
    const [response] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/rx/addAllergy2')),
      form.locator('input[type="submit"]').click(),
    ]);
    const activeCopies = `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND archived=0
      AND DESCRIPTION=${sqlString(inactiveName)} AND reaction=${sqlString(inactiveReaction)}`;
    if (response.status() !== 200 && response.status() !== 302) {
      const status = (await page.locator('[role="alert"]:visible').allInnerTexts().catch(() => [])).join(' | ').replace(/\s+/g, ' ').slice(0, 200);
      assert(false, `Reactivate was refused with HTTP ${response.status()} (${sql.value(activeCopies)} active copies; the page says ${JSON.stringify(status)})`);
    }
    await expectValue(sql, activeCopies, '1', 'Reactivate did not leave one active copy of the allergy');
    assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND archived=0`) === '2',
      'Reactivate changed the patient\'s other allergies');
  });
}

if (require.main === module) runWorkflow('allergy-list-views', workflow);
module.exports = { workflow };
