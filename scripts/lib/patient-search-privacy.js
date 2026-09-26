/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./playwright-harness');
const ui = require('./playwright-ui');

async function settled(target) {
  await target.waitForLoadState('load');
  await target.waitForLoadState('networkidle', { timeout: 30000 });
  await target.evaluate(async () => { await document.fonts.ready; });
}

async function submitSearch(session, target, selector, route, expected) {
  await settled(target);
  const [request] = await Promise.all([
    session.context.waitForEvent('request', {
      predicate: request => request.isNavigationRequest()
        && new URL(request.url()).pathname.endsWith(route), timeout: 30000,
    }),
    target.waitForNavigation({ waitUntil: 'load', timeout: 30000 }),
    target.locator(selector).first().click(),
  ]);
  h.assert(request.method() === 'POST', 'Patient search transition did not use POST');
  h.assert(!new URL(request.url()).search, 'Patient search transition exposed state in its URL');
  const body = new URLSearchParams(request.postData());
  for (const [key, value] of Object.entries(expected)) {
    h.assert(body.get(key) === String(value), `Patient search transition lost ${key}`);
    h.assert(body.getAll(key).length === 1, `Patient search transition duplicated ${key}`);
  }
  await settled(target);
  h.assert(!new URL(target.url()).searchParams.has('keyword'), 'Patient search destination exposed its keyword');
}

/** Adds UI round trips to the REST protocol check, using only its owned patient/program. */
async function checkPatientSearchPrivacy(session, program) {
  const { sql, patient, marker, provider, schedule } = session;
  const keyword = marker.toLowerCase();
  const owned = () => sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${h.sqlString(marker)}
    AND demographic_no<>${patient}`).map(row => row[0]);
  session.cleanup(() => {
    const ids = owned();
    if (!ids.length) return;
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Invalid owned pagination fixture identity');
    const list = ids.join(',');
    sql.execute(`DELETE FROM demographic_merged WHERE demographic_no IN (${list}) AND merged_to=${patient};
      DELETE FROM admission WHERE client_id IN (${list}) AND program_id=${program};
      DELETE FROM recyclebin WHERE table_name='secObjPrivilege'
        AND keyword IN (${ids.map(id => h.sqlString(`_all|_eChart$${id}`)).join(',')});
      DELETE FROM demographic WHERE demographic_no IN (${list}) AND last_name=${h.sqlString(marker)}`);
    h.assert(owned().length === 0, 'Pagination fixtures were not removed');
  });
  // Insert in reverse name order: sorting only the current page will fail the first-row assertion.
  for (let index = 11; index >= 1; index--) {
    sql.execute(`INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      SELECT last_name,${h.sqlString(`Privacy${String(index).padStart(2, '0')}`)},year_of_birth,
       month_of_birth,date_of_birth,sex,'AC',provider_no,hc_type,province,roster_status,NOW()
      FROM demographic WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  }
  h.assert(owned().length === 11, 'Pagination fixture size is incorrect');
  sql.execute(`UPDATE demographic SET patient_status='AC' WHERE demographic_no=${patient};
    INSERT INTO admission (client_id,program_id,provider_no,admission_date,
      admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)
    SELECT demographic_no,${program},${h.sqlString(provider)},NOW(),0,0,'current',NOW()
    FROM demographic d WHERE last_name=${h.sqlString(marker)} AND NOT EXISTS
      (SELECT 1 FROM admission a WHERE a.client_id=d.demographic_no AND a.program_id=${program})`);
  const search = await session.popup(schedule, schedule.locator('a').filter({ hasText: /^Search$/ }), 'private-patient-search');
  await session.step('patient search, sorting and both pagination directions keep terms out of URLs', async () => {
    await search.locator('#search_mode').selectOption('search_name');
    await search.locator('#keyword').fill(keyword);
    await submitSearch(session, search, 'form[name="titlesearch"] input[type="submit"]',
      '/demographic/DemographicSearch', { keyword, search_mode: 'search_name' });
    h.assert(await search.locator('#patientResults tr').count() === 11, 'First patient search page has the wrong row count');
    await submitSearch(session, search, 'button[form="search-sort"][value="last_name"]',
      '/demographic/DemographicSearch', { keyword, orderby: 'last_name', limit1: 0 });
    await submitSearch(session, search, 'button[form="search-page"][value="10"]',
      '/demographic/DemographicSearch', { keyword, orderby: 'last_name', limit1: 10 });
    h.assert(await search.locator('#patientResults tr').count() === 3, 'Second patient search page has the wrong row count');
    await submitSearch(session, search, 'button[form="search-page"][value="0"]',
      '/demographic/DemographicSearch', { keyword, orderby: 'last_name', limit1: 0 });
    await submitSearch(session, search, 'form[action$="/demographic/ViewDemographicAddARecordHtm"] button',
      '/demographic/ViewDemographicAddARecordHtm', { keyword, search_mode: 'search_name' });
    h.assert(await search.locator('form[name="adddemographic"]').count() === 1, 'Patient creation handoff did not render its form');
  });

  for (const kind of ['appointment', 'report']) {
    await session.step(`${kind} picker paging and selection keep patient details in POST bodies`, async () => {
      const route = kind === 'appointment' ? '/demographic/DemographicSearch'
        : '/demographic/ViewDemographicSearch2ReportResults';
      // Protocol setup for the legacy callers; each subsequent transition uses the rendered UI.
      const url = new URL(h.appUrl(session.config.baseUrl, route));
      url.search = new URLSearchParams({ keyword, search_mode: 'search_name',
        displaymode: 'Search ', ptstatus: 'active',
        originalpage: new URL(h.appUrl(session.config.baseUrl,
          '/demographic/ViewDemographicAddARecordHtm')).pathname }).toString();
      await search.goto(url.href, { waitUntil: 'load' });
      const state = { keyword, search_mode: 'search_name' };
      await submitSearch(session, search, '#nextPageButton', route, { ...state, limit1: 10 });
      h.assert(await search.locator('input[name="pick_demographic"]').count() === 2,
        'Second picker page has the wrong row count');
      await submitSearch(session, search, '#prevPageButton', route, { ...state, limit1: 0 });
      const selected = await search.locator('input[name="pick_demographic"]').first().inputValue();
      h.assert([patient, ...owned()].includes(selected), 'Picker selected a patient outside its fixture');
      const [first, last] = sql.rows(`SELECT first_name,last_name FROM demographic
        WHERE demographic_no=${selected} AND last_name=${h.sqlString(marker)}`)[0];
      const details = kind === 'appointment' ? { name: `${last},${first}` }
        : { firstNameParam: first, lastNameParam: last, demographicNoParam: selected };
      await submitSearch(session, search, 'input[name="pick_demographic"]',
        '/demographic/ViewDemographicAddARecordHtm', { demographic_no: selected, chart_no: '', ...details });
      h.assert(await search.locator('form[name="adddemographic"]').count() === 1,
        'Picker POST handoff did not render its target');
      url.searchParams.set('keyword', `${marker}NoMatch`);
      await search.goto(url.href, { waitUntil: 'load' });
      const create = search.locator('form[action$="/demographic/ViewDemographicAddARecordHtm"] button');
      // The report picker intentionally hides new-patient creation in Caisi mode.
      if (kind === 'appointment' || await create.count() > 0) {
        await submitSearch(session, search, 'form[action$="/demographic/ViewDemographicAddARecordHtm"] button',
          '/demographic/ViewDemographicAddARecordHtm', { keyword: `${marker}NoMatch`, search_mode: 'search_name' });
        h.assert(await search.locator('form[name="adddemographic"]').count() === 1,
          'Picker new-patient handoff did not render its form');
      } else {
        h.assert(process.env.EXPECT_PROGRAM_DOMAIN_RESTRICTION !== 'false',
          'Unrestricted report picker unexpectedly omitted patient creation');
        console.log('  SKIP report new-patient handoff: this deployment does not offer the control');
      }
    });
  }

  sql.execute(`INSERT INTO demographic_merged (demographic_no,merged_to,deleted)
    SELECT demographic_no,${patient},0 FROM demographic WHERE last_name=${h.sqlString(marker)} AND demographic_no<>${patient}`);
  const { page: admin } = await ui.clickOpensPopupOrNavigates(schedule,
    schedule.locator('#admin-panel, #admin2').first(),
    { context: session.context, recorder: session.recorder, label: 'private-merge-admin' });
  await settled(admin);
  let merge;
  const menu = admin.locator('a.xlink[rel$="/admin/DemographicMergeRecord"]').first();
  if (await menu.count()) {
    const panel = menu.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
    if (await panel.count() && !await panel.isVisible()) {
      const id = await panel.getAttribute('id');
      await admin.locator(`[data-bs-target="#${id}"]`).click();
    }
    await menu.click();
    await admin.locator('#myFrame').waitFor();
    merge = await (await admin.locator('#myFrame').elementHandle()).contentFrame();
    await merge.locator('form[name="titlesearch"]').waitFor();
  } else {
    merge = await session.popup(admin, admin.locator('a[onclick*="DemographicMergeRecord"]'), 'private-merge-search');
  }
  await session.step('merged search sorts before paging and preserves its scope using POST', async () => {
    await merge.locator('input[name="search_mode"][value="search_name"]').check();
    await merge.locator('form[name="titlesearch"] input[name="keyword"]').fill(keyword);
    const state = { keyword, dboperation: 'demographic_search_merged' };
    await submitSearch(session, merge, 'form[name="titlesearch"] button[name="dboperation"]',
      '/admin/DemographicMergeRecord', state);
    await submitSearch(session, merge, 'button[form="search-sort"][value="first_name"]',
      '/admin/DemographicMergeRecord', { ...state, orderby: 'first_name', limit1: 0 });
    h.assert(await merge.locator('input[name="records"]').count() === 10, 'First merged page has the wrong row count');
    h.assert((await merge.locator('form[name="mergeform"] tr').nth(1).locator('td').nth(3).innerText()).trim() === 'Privacy01',
      'Merged search sorted only its current page');
    await submitSearch(session, merge, 'button[form="search-page"][value="10"]',
      '/admin/DemographicMergeRecord', { ...state, orderby: 'first_name', limit1: 10 });
    h.assert(await merge.locator('input[name="records"]').count() === 1, 'Second merged page has the wrong row count');
    h.assert((await merge.locator('form[name="mergeform"] tr').nth(1).locator('td').nth(3).innerText()).trim() === 'Privacy11',
      'Merged search lost its global ordering on the next page');
    await submitSearch(session, merge, 'button[form="search-page"][value="0"]',
      '/admin/DemographicMergeRecord', { ...state, orderby: 'first_name', limit1: 0 });
  });
  await session.step('unmerge returns through the search controller and displays its outcome', async () => {
    const box = merge.locator('input[name="records"]').first();
    const selected = await box.inputValue();
    h.assert(owned().includes(selected), 'Unmerge selected a patient outside its fixture');
    await box.check();
    const dialogPage = typeof merge.page === 'function' ? merge.page() : merge;
    const dialogs = await h.withExpectedDialogs(dialogPage, async () => {
      await submitSearch(session, merge, 'input[onclick="UnMerge()"]',
        '/admin/MergeRecords', { records: selected, mergeAction: 'unmerge' });
      h.assert(new URL(merge.url()).pathname.endsWith('/admin/DemographicMergeRecord'),
        'Unmerge did not return through the search controller');
      h.assert(new URL(merge.url()).searchParams.get('outcome') === 'successUnMerge',
        'Unmerge did not display its success outcome');
      h.assert(await merge.locator('form[name="titlesearch"]').count() === 1,
        'Unmerge returned a broken search page');
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic_merged
        WHERE demographic_no=${selected} AND merged_to=${patient} AND deleted=0`) === '0',
        'Unmerge did not update its owned record');
    });
    h.assert(dialogs.length === 2 && dialogs[0].type === 'confirm' && dialogs[1].type === 'alert',
      'Unmerge did not confirm and report its outcome');
    // Restore the search state through the UI before the domain negative control.
    await merge.locator('form[name="titlesearch"] input[name="keyword"]').fill(keyword);
  });
  if (process.env.EXPECT_PROGRAM_DOMAIN_RESTRICTION !== undefined) {
    await session.step('merged results obey the configured program domain after admission removal', async () => {
      sql.execute(`DELETE FROM admission WHERE program_id=${program} AND client_id IN (${owned().join(',')})`);
      await submitSearch(session, merge, 'form[name="titlesearch"] button[name="dboperation"]',
        '/admin/DemographicMergeRecord', { keyword, dboperation: 'demographic_search_merged' });
      const expected = process.env.EXPECT_PROGRAM_DOMAIN_RESTRICTION === 'true' ? 0 : 10;
      h.assert(await merge.locator('input[name="records"]').count() === expected,
        'Merged search did not enforce the configured program domain');
    });
  }

}

module.exports = { checkPatientSearchPrivacy, submitSearch };
