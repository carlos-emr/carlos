#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Merge Patient Records, driven the way an administrator does it.
 * User path: Schedule ▸ Administration ▸ Data Management ▸ Merge Patient Records
 * (popup, admin/DemographicMergeRecord) ▸ search by name ▸ tick both records and
 * the Main Record radio ▸ Merge Selected Records (confirm, MergeRecords, alert) ▸
 * Search Merged Records ▸ UnMerge Selected Records.
 * Asserts: the demographic_merged row (demographic_no, merged_to, deleted,
 * lastUpdateUser) and the _eChart$<id> secObjPrivilege row; the merge search and
 * the head's Master Record showing the duplicate as a tail; the schedule search
 * returning only the head; the unmerge marking the row deleted and parking the
 * privilege in the recycle bin; a self-merge, a forged head outside the selection
 * and a GET refused with no row. Fixtures: two owned FAKE- patients seeded by SQL;
 * cleanup removes their merge, privilege, recycle-bin and demographic rows and
 * asserts they are gone. Implements coverage plan §2.4 demographic-merge.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates, clickAndAwaitReload, csrfTokenPresent} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const CONFIRM_MERGE = 'You are about to merge duplicate patient records';
const MERGED = 'Records merged successfully';
const MERGE_FAILED = 'Failed to merge records';
const UNMERGED = 'Record(s) unmerged successfully';

function dialogTexts(dialogs) {
  return dialogs.map(entry => `${entry.type}:${entry.text}`);
}

/** The confirm() the merge form raises, then the alert the landing page raises. */
function assertMergeDialogs(dialogs, outcomeText, label) {
  const texts = dialogTexts(dialogs);
  h.assert(dialogs.length === 2 && dialogs[0].type === 'confirm' && dialogs[0].text.includes(CONFIRM_MERGE)
    && dialogs[1].type === 'alert' && dialogs[1].text.includes(outcomeText),
  `${label}: expected the merge confirm followed by the "${outcomeText}" alert, saw ${JSON.stringify(texts)}`);
}

async function workflow(s) {
  const {sql, marker, provider, context, recorder} = s;
  const ids = [];
  s.cleanup(() => {
    if (!ids.length) return;
    const list = ids.join(',');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no IN (${list})
      AND last_name=${h.sqlString(marker)}`) === String(ids.length), 'Merge patient fixture ownership changed');
    // Children first: demographic_merged.merged_to is a foreign key to demographic.
    sql.execute(`DELETE FROM demographic_merged WHERE demographic_no IN (${list}) OR merged_to IN (${list})`);
    for (const id of ids) {
      sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup='_all' AND objectName=${h.sqlString(`_eChart$${id}`)}`);
      sql.execute(`DELETE FROM recyclebin WHERE table_name='secObjPrivilege' AND keyword=${h.sqlString(`_all|_eChart$${id}`)}`);
    }
    sql.execute(`DELETE FROM demographic WHERE demographic_no IN (${list}) AND last_name=${h.sqlString(marker)}`);
    const remaining = [
      `(SELECT COUNT(*) FROM demographic_merged WHERE demographic_no IN (${list}) OR merged_to IN (${list}))`,
      `(SELECT COUNT(*) FROM secObjPrivilege WHERE objectName IN (${ids.map(id => h.sqlString(`_eChart$${id}`)).join(',')}))`,
      `(SELECT COUNT(*) FROM recyclebin WHERE keyword IN (${ids.map(id => h.sqlString(`_all|_eChart$${id}`)).join(',')}))`,
      `(SELECT COUNT(*) FROM demographic WHERE demographic_no IN (${list}))`,
    ];
    h.assert(sql.value(`SELECT ${remaining.join('+')}`) === '0', 'Owned merge fixtures were not removed');
  });
  const seed = firstName => sql.value(`INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
     provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(marker)},${h.sqlString(firstName)},'1980','01','02','F','AC',
      ${h.sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  const head = seed('Primary');
  h.assert(/^[1-9]\d*$/.test(head), 'The head patient fixture was not created');
  ids.push(head);
  const dup = seed('Duplicate');
  h.assert(/^[1-9]\d*$/.test(dup), 'The duplicate patient fixture was not created');
  ids.push(dup);
  const owned = `demographic_no IN (${head},${dup}) OR merged_to IN (${head},${dup})`;
  const mergeRows = () => sql.value(`SELECT COUNT(*) FROM demographic_merged WHERE ${owned}`);
  const liveRows = () => sql.value(`SELECT COUNT(*) FROM demographic_merged WHERE (${owned}) AND deleted=0`);
  const privilegeRows = () => sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup='_all'
    AND objectName=${h.sqlString(`_eChart$${dup}`)}`);

  // Schedule ▸ Administration ▸ Data Management ▸ Merge Patient Records (popupPage).
  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    {context, recorder, label: 'merge-administration', timeout: 20000});
  const link = admin.getByRole('link', {name: 'Merge Patient Records', exact: true, includeHidden: true});
  await revealAuditLink(admin, link, 20000);
  const merge = await s.popup(admin, link, 'merge-records');
  await merge.locator('form[name="titlesearch"] #merge-keyword').waitFor();

  async function search(merged = false) {
    const form = merge.locator('form[name="titlesearch"]');
    await form.locator('#merge-mode-name').check();
    await form.locator('#merge-keyword').fill(marker);
    const button = merged ? form.locator('button[name="dboperation"][value="demographic_search_merged"]')
      : form.locator('input[type="submit"][name="button"]');
    await clickAndAwaitReload(merge, button, {timeout: 20000, label: merged ? 'Search Merged Records' : 'the merge search'});
    await h.assertNotErrorPage(merge, 'merge search results');
  }
  const rows = () => merge.locator('form[name="mergeform"] table tr').filter({hasText: marker});
  const rowFor = firstName => rows().filter({hasText: firstName});
  async function submitMerge(button, outcomeText, label) {
    const dialogs = await h.withExpectedDialogs(merge, async () => {
      await clickAndAwaitReload(merge, merge.locator(`form[name="mergeform"] input[type="submit"][value="${button}"]`),
        {timeout: 20000, label});
    });
    assertMergeDialogs(dialogs, outcomeText, label);
    await h.assertNotErrorPage(merge, label);
    return new URL(merge.url()).searchParams.get('outcome');
  }

  await s.step('the merge page lists both owned records as independent head records', async () => {
    await search();
    h.assert(await rows().count() === 2, 'The merge search did not return exactly the two owned records');
    for (const name of ['Primary', 'Duplicate']) {
      h.assert(await rowFor(name).count() === 1 && await rowFor(name).locator('input[name="records"]').count() === 1
        && await rowFor(name).locator('input[name="head"]').count() === 1, `${name} record is not offered as a mergeable head record`);
    }
    h.assert(mergeRows() === '0', 'Owned records already carry merge history');
  });

  await s.step('merging the duplicate into the head writes one live demographic_merged row and the chart privilege', async () => {
    await rowFor('Primary').locator('input[name="records"]').check();
    await rowFor('Duplicate').locator('input[name="records"]').check();
    await rowFor('Primary').locator('input[name="head"]').check();
    h.assert(await submitMerge('Merge Selected Records', MERGED, 'merge') === 'success', 'The merge did not land on its success outcome');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic_merged WHERE demographic_no=${dup} AND merged_to=${head}
      AND deleted=0 AND lastUpdateUser=${h.sqlString(provider)}`) === '1', 'The merge did not write the duplicate -> head row');
    h.assert(mergeRows() === '1', 'The merge wrote more than one row for the owned pair');
    h.assert(sql.value(`SELECT privilege FROM secObjPrivilege WHERE roleUserGroup='_all'
      AND objectName=${h.sqlString(`_eChart$${dup}`)}`) === '|or|', 'The merge did not grant the merged chart read privilege');
  });

  await s.step('the merged record is shown as a tail of its head on the merge search and the Master Record', async () => {
    await search();
    h.assert(await rows().count() === 2, 'The merge search lost a record after the merge');
    h.assert(await rowFor('Primary').locator('input[name="head"]').count() === 1, 'The head record lost its Main Record radio');
    h.assert(await rowFor('Duplicate').locator('input[name="records"], input[name="head"]').count() === 0,
      'The merged duplicate is still offered as a mergeable head record');
    const toHead = rowFor('Duplicate').locator(`a[href*="demographic_no=${head}"]`);
    h.assert(await toHead.count() === 1, 'The merged duplicate does not link to its head record');
    const master = await s.popup(merge, toHead, 'merged-master-record');
    h.assert(new URL(master.url()).searchParams.get('demographic_no') === head, 'The duplicate opened a record other than its head');
    const toolbar = (await master.locator('.demo-toolbar-id').first().innerText()).replace(/\s+/g, ' ');
    h.assert(toolbar.includes(`#${head}`) && toolbar.includes(`#${dup}`), 'The head Master Record does not list the merged duplicate');
    await master.close();
  });

  await s.step('the schedule patient search returns only the head record for the merged pair', async () => {
    const {page: results} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#search a').first(),
      {context, recorder, label: 'merge-patient-search', timeout: 20000});
    await results.locator('#keyword, input[name="keyword"]').first().fill(marker);
    await clickAndAwaitReload(results, results.locator("input[type='submit']").first(), {timeout: 20000, label: 'the patient search'});
    const shown = (await results.locator('a[title="Master Demographic File"]').allTextContents())
      .map(text => text.trim()).filter(text => ids.includes(text));
    h.assert(shown.length === 1 && shown[0] === head, 'The patient search did not collapse the merged pair to its head record');
  });

  await s.step('unmerging from Search Merged Records marks the row deleted and parks the privilege in the recycle bin', async () => {
    await search(true);
    h.assert(await rows().count() === 1 && await rowFor('Duplicate').count() === 1, 'Search Merged Records did not list exactly the merged duplicate');
    h.assert(await merge.locator('form[name="mergeform"] input[name="head"]').count() === 0, 'The merged-records list offers a Main Record radio');
    await rowFor('Duplicate').locator('input[name="records"]').check();
    h.assert(await submitMerge('UnMerge Selected Records', UNMERGED, 'unmerge') === 'successUnMerge', 'The unmerge did not land on its success outcome');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic_merged WHERE demographic_no=${dup} AND merged_to=${head}
      AND deleted=1 AND lastUpdateUser=${h.sqlString(provider)}`) === '1' && liveRows() === '0', 'The unmerge did not mark the merge row deleted');
    h.assert(privilegeRows() === '0', 'The unmerge left the merged chart privilege in place');
    h.assert(sql.value(`SELECT COUNT(*) FROM recyclebin WHERE table_name='secObjPrivilege'
      AND keyword=${h.sqlString(`_all|_eChart$${dup}`)} AND provider_no=${h.sqlString(provider)}`) === '1',
    'The unmerge did not park the removed privilege in the recycle bin');
    await search();
    for (const name of ['Primary', 'Duplicate']) {
      h.assert(await rowFor(name).locator('input[name="head"]').count() === 1, `${name} is not a head record again after the unmerge`);
    }
  });

  await s.step('merging a record with itself is refused and writes nothing', async () => {
    const before = mergeRows();
    await rowFor('Primary').locator('input[name="records"]').check();
    await rowFor('Primary').locator('input[name="head"]').check();
    h.assert(await submitMerge('Merge Selected Records', MERGE_FAILED, 'self-merge') === 'failure', 'A self-merge did not land on the failure outcome');
    h.assert(mergeRows() === before && liveRows() === '0', 'A refused self-merge wrote a merge row');
  });

  await s.step('a forged head outside the selection and a GET are refused without a row', async () => {
    const before = mergeRows();
    await search();
    const token = await csrfTokenPresent(merge);
    const missing = sql.value('SELECT COALESCE(MAX(demographic_no),0)+100000 FROM demographic');
    const endpoint = h.appUrl(s.config.baseUrl, '/admin/MergeRecords');
    const body = new URLSearchParams({'CSRF-TOKEN': token, mergeAction: 'merge', provider_no: provider, head: missing});
    body.append('records', dup);
    body.append('records', head);
    const forged = await context.request.post(endpoint, {data: body.toString(), maxRedirects: 0,
      headers: {'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': token}});
    h.assert(forged.status() === 302 && /outcome=failure/.test(forged.headers().location || ''),
      `A merge towards a head outside the selection returned HTTP ${forged.status()} instead of the failure outcome`);
    const unsafeGet = await context.request.get(endpoint, {maxRedirects: 0,
      params: {mergeAction: 'merge', provider_no: provider, head, records: dup}});
    h.assert(unsafeGet.status() === 405 && unsafeGet.headers().allow === 'POST', 'MergeRecords must reject GET with Allow: POST');
    h.assert(mergeRows() === before && liveRows() === '0', 'A refused merge probe wrote a merge row');
    h.assert(privilegeRows() === '0', 'A refused merge probe granted a chart privilege');
  });
}

if (require.main === module) runWorkflow('demographic-merge', workflow, {openPatient: false});
module.exports = {workflow};
