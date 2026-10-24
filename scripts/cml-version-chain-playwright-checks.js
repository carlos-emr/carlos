#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The versions of a CML report (issue #4568, finding 197 of docs/ui-tests/app-findings-log.md).
 *
 * WHY THIS CHECK EXISTS. A lab arrives as a chain of versions (preliminary, final, corrected) that share one
 * accession number. For the old CML vendor, MDSResultsData.getMatchingCMLLabs resolves that chain from
 * LabPatientPhysicianInfoDao.findLabServiceDatesByLabId, whose two date columns are the service_date varchar, the
 * vendor's YYYYMMDD. It cast them to Date, threw a ClassCastException on every CML view and every acknowledgement,
 * logged it, and fell back to a chain of one report: the "Version:" row never appeared, and acknowledging a newer
 * version never filed the older one, which stayed New in the Inbox. legacy-lab-display cannot see this (a lab with a
 * single version has nothing to chain), and no unit test could: they mocked the lookup with the types the consumer
 * expected.
 *
 * WHAT IS SEEDED. One accession number, three CML reports, each routed to the test provider as New and linked to the
 * owned patient:
 *   v1        the preliminary report: service date 20260101, printed 20260101 08:00
 *   v2        the final report:       service date 20260103, printed 20260103 09:30
 *   recycled  the same accession number two years earlier (service date 20240101). Vendors recycle accession
 *             numbers, and the four month window is all that keeps such a report out of the chain. It is the
 *             report that must NOT be linked, filed or forwarded with v2.
 *
 * STEPS, in order.
 *   seed         the three reports and their routing.
 *   Display      v2's page has a "Version:" row of exactly v1 and v2, where v1 is a link carrying the chain
 *                ("multiID=<v1>,<v2>") and v2, the page's own version, is plain text; nothing on the page names the
 *                recycled report; and the page shows v2's result, not v1's.
 *   Older        following v1's link opens v1, which shows v1's result and now links to v2.
 *   Acknowledge  Acknowledge on v2 asks for a comment, closes its window and reloads the patient lab list; v2's
 *                routing row is then A with the comment, v1's is F (filed), the recycled report's row is untouched,
 *                and v2 opened again shows v1 as "Filed but not acknowledged".
 *   Match        A second accession, seeded UNMATCHED: two versions and a recycled report. Choosing the owned patient
 *                in the matching popup (oscarMDS/SearchPatient -> oscarMDS/PatientMatch) answers 200 and links BOTH
 *                versions to the patient and not the recycled report. This is the strict form of the chain
 *                (MrpRoutingService), which threw for every CML report before the fix: the match answered HTTP 500.
 *   Forward      the provider picker routes both versions to an owned recipient, preserving sender status
 *                and leaving the recycled accession untouched.
 *   Ambiguity    a conflicting source patient identifier produces a visible warning and no version links.
 *
 * HOW THE LAB IS OPENED. As in legacy-lab-display: the lists that link CML labs show them only when CML_LABS is
 * "yes" (off in the package), so the check opens the owned patient's lab list, adds the row link that list
 * renders for a CML lab, and CLICKS it. The report is therefore a popup with a window.opener, which matters:
 * Acknowledge ends in oscarMDS/Close.jsp, whose first statement is `opener.location.reload()`.
 *
 * Fixtures: the owned FAKE- patient (runWorkflow, no master record opened) and six reports seeded by SQL with
 * synthetic names and a synthetic HIN, under random 10-digit numbers verified unoccupied. Cleanup removes exactly
 * those rows by key, after proving each report's header still carries this run's marker, and asserts them gone: the
 * lab tables, patientLabRouting, providerLabRouting, providerLabRoutingLock and the audit rows the pages and the
 * patient match wrote for the owned report numbers.
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture, bundleMessage } = require('./lib/throwaway-login-fixture');

const NAME = 'cml-version-chain';
const TIMEOUT = 30000;
const q = h.sqlString;
const LAB_TYPE = 'CML';

/*
 * The step labels are literals on purpose: the manifest's expectedFailure.step is checked against the script's own
 * text, and a label assembled at run time cannot be found there.
 */
const STEP = Object.freeze({
  seed: 'two versions of one CML report and a report that recycled its accession number are seeded and routed to the test provider',
  display: 'the newest version lists the older one as a Version link and does not mention the recycled report',
  older: 'the older version opens from its link, shows its own result and links back to the newest',
  acknowledge: 'acknowledging the newest version files the older one and leaves the recycled report untouched',
  forward: 'forwarding the final report routes both versions and leaves the recycled report untouched',
  ambiguous: 'a conflicting source patient identity shows an incomplete-chain warning and no sibling link',
  match: 'matching one unmatched version to the owned patient links every version of it and not the recycled report',
});

/** Whitespace-collapsed text, for comparing what a page renders. */
const squash = (text) => String(text).replace(/\s+/g, ' ').trim();

/**
 * One unoccupied number. `held(id)` is the SQL that counts every row the number could already belong to; no number
 * is handed out twice, because routing rows are keyed by the report number alone.
 */
function freeId(sql, taken, held) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const id = randomInt(1000000000, 1800000000);
    if (!taken.has(id) && sql.value(held(id)) === '0') {
      taken.add(id);
      return id;
    }
  }
  throw new Error('No unoccupied report number was found after 20 attempts');
}

const counts = (id, tables) => tables.map(([table, column]) => `(SELECT COUNT(*) FROM ${table} WHERE ${column}=${id})`);
const requestLink = (id) => `(SELECT COUNT(*) FROM labRequestReportLink WHERE report_table='labPatientPhysicianInfo' AND report_id=${id})`;
const reportTables = [['labPatientPhysicianInfo', 'id'], ['labTestResults', 'labPatientPhysicianInfo_id'],
  ['patientLabRouting', 'lab_no'], ['providerLabRouting', 'lab_no'], ['providerLabRoutingLock', 'lab_no']];

/** The three seeded reports, with their numbers allocated; nothing is written yet. */
function describeReports({ sql, marker }) {
  const taken = new Set();
  // The marker is FAKE-PW<16 hex>; the accession number is vendor-shaped (a short alphanumeric) and unique per run.
  const accession = `CV${marker.slice('FAKE-PW'.length + 8)}`;
  const matchAccession = `CM${marker.slice('FAKE-PW'.length + 8)}`;
  // The accession number is what chains reports, so a report that already carries one of these would be acted on.
  h.assert(sql.value(`SELECT COUNT(*) FROM labPatientPhysicianInfo WHERE accession_num IN (${q(accession)},${q(matchAccession)})`) === '0',
    'A report already carries this run\'s accession number');
  const report = (key, spec) => ({
    key,
    accession,
    id: freeId(sql, taken, (id) => `SELECT ${[...counts(id, reportTables), requestLink(id)].join('+')}`),
    infoId: freeId(sql, taken, (id) => `SELECT ${counts(id, [['labReportInformation', 'id']]).join('+')}`),
    ...spec,
  });
  return {
    v1: report('v1', {
      label: 'preliminary', status: 'P', serviceDate: '20260101', printDate: '20260101', printTime: '08:00',
      collectionDate: '01 JAN 26', test: `${marker} Sodium preliminary`, value: '150', abn: 'A',
    }),
    v2: report('v2', {
      label: 'final', status: 'F', serviceDate: '20260103', printDate: '20260103', printTime: '09:30',
      collectionDate: '03 JAN 26', test: `${marker} Sodium final`, value: '142', abn: 'N',
    }),
    recycled: report('recycled', {
      label: 'recycled accession', status: 'F', serviceDate: '20240101', printDate: '20240101', printTime: '08:00',
      collectionDate: '01 JAN 24', test: `${marker} Potassium recycled`, value: '4.1', abn: 'N',
    }),
    // A second accession, seeded UNMATCHED (no patient, no routing): the Match step links it to the owned patient.
    matchFirst: report('matchFirst', {
      accession: matchAccession, matched: false, label: 'first unmatched', status: 'P', serviceDate: '20260105',
      printDate: '20260105', printTime: '08:00', collectionDate: '05 JAN 26', test: `${marker} Chloride first`, value: '99', abn: 'N',
    }),
    matchSecond: report('matchSecond', {
      accession: matchAccession, matched: false, label: 'second unmatched', status: 'F', serviceDate: '20260107',
      printDate: '20260107', printTime: '09:30', collectionDate: '07 JAN 26', test: `${marker} Chloride second`, value: '101', abn: 'N',
    }),
    matchRecycled: report('matchRecycled', {
      accession: matchAccession, matched: false, label: 'recycled unmatched', status: 'F', serviceDate: '20240105',
      printDate: '20240105', printTime: '08:00', collectionDate: '05 JAN 24', test: `${marker} Bicarbonate recycled`, value: '24', abn: 'N',
    }),
  };
}

function seedReport({ sql, marker, patient, provider, report }) {
  sql.execute(`INSERT INTO labReportInformation(id,location_id,print_date,print_time,total_BType,total_CType,total_DType)
    VALUES(${report.infoId},'70',${q(report.printDate)},${q(report.printTime)},'1','1','0')`);
  sql.execute(`INSERT INTO labPatientPhysicianInfo(id,labReportInfo_id,accession_num,physician_account_num,service_date,patient_first_name,
      patient_last_name,patient_sex,patient_health_num,patient_dob,lab_status,doc_num,doc_name,doc_addr1,doc_addr2,doc_addr3,doc_postal,
      doc_route,comment1,comment2,patient_phone,doc_phone,collection_date,lastUpdateDate)
    VALUES(${report.id},${report.infoId},${q(report.accession)},'FAKEACCT',${q(report.serviceDate)},'Workflow',${q(marker)},'F','0000000000',
      '19800102',${q(report.status)},'000000','Fake Doctor','','','','','','','','555-0100','555-0101',${q(report.collectionDate)},NOW());
    INSERT INTO labTestResults(labPatientPhysicianInfo_id,line_type,title,notUsed1,notUsed2,test_name,abn,minimum,maximum,units,result,
      description,location_id,last)
    VALUES(${report.id},'C','FAKE CHEM','','',${q(report.test)},${q(report.abn)},'135','145','mmol/L',${q(report.value)},'','70','Y')`);
  // An unmatched report has no patient and no routing: the match writes both.
  if (report.matched === false) return;
  sql.execute(`INSERT INTO patientLabRouting(demographic_no,lab_no,lab_type,created) VALUES(${patient},${report.id},${q(LAB_TYPE)},NOW());
    INSERT INTO providerLabRouting(provider_no,lab_no,lab_type,status) VALUES(${q(provider)},${report.id},${q(LAB_TYPE)},'N')`);
}

/**
 * A seed that failed between the header and the report leaves the header alone, and nothing marks it as this run's.
 * Its number was proved unoccupied when it was allocated, so a header with that number, this report's location, print
 * date and time and no report pointing at it is this run's; if anything else remains under the report's number the
 * rows are not understood, and the ownership refusal in removeReport stands.
 */
function removeOrphanHeader({ sql, report, remaining }) {
  const header = sql.value(`SELECT COUNT(*) FROM labReportInformation WHERE id=${report.infoId} AND location_id='70'
    AND print_date=${q(report.printDate)} AND print_time=${q(report.printTime)}
    AND NOT EXISTS (SELECT 1 FROM labPatientPhysicianInfo WHERE labReportInfo_id=${report.infoId})`);
  if (header !== '1' || remaining() !== '1') return false;
  sql.execute(`DELETE FROM labReportInformation WHERE id=${report.infoId}`);
  h.assert(remaining() === '0', `The orphaned ${report.label} report header was not removed`);
  return true;
}

/**
 * Remove one report's rows by key and prove them gone. Ownership comes first: the header row must still carry this
 * run's marker. A report that was never written (the seed did not reach it) has nothing to remove.
 */
function removeReport({ sql, report, patient, provider }) {
  const id = report.id;
  const owned = sql.value(`SELECT COUNT(*) FROM labPatientPhysicianInfo WHERE id=${id} AND patient_last_name=${q(report.marker)}`);
  // The pages write content 'lab' rows keyed by the bare number; a patient match writes its rows keyed "CML:<number>".
  const matchAudit = `content IN ('labPatientRouting','providerLinkingRules') AND contentId=${q(`${LAB_TYPE}:${id}`)}`;
  const remaining = () => sql.value(`SELECT ${[...counts(id, reportTables), requestLink(id),
    ...counts(report.infoId, [['labReportInformation', 'id']]),
    `(SELECT COUNT(*) FROM log WHERE content='lab' AND contentId=${q(String(id))})`,
    `(SELECT COUNT(*) FROM log WHERE ${matchAudit})`].join('+')}`);
  if (owned === '0' && remaining() === '0') return;
  if (owned === '0' && removeOrphanHeader({ sql, report, remaining })) return;
  h.assert(owned === '1', `The owned ${report.label} report header changed; refusing to delete rows keyed by its number`);
  sql.execute([
    // By number alone, as for the provider routing below: the number was proved unoccupied when it was allocated, so
    // every row under it is this run's, including one that a regression made the match write for another patient.
    `DELETE FROM patientLabRouting WHERE lab_type=${q(LAB_TYPE)} AND lab_no=${id}`,
    `DELETE FROM providerLabRouting WHERE lab_type=${q(LAB_TYPE)} AND lab_no=${id}`,
    `DELETE FROM providerLabRoutingLock WHERE lab_no=${id}`,
    // The audit rows the page and the acknowledgement wrote carry the report number as their content id.
    `DELETE FROM log WHERE content='lab' AND contentId=${q(String(id))} AND provider_no=${q(provider)}
      AND (demographic_no=${patient} OR demographic_no IS NULL)`,
    `DELETE FROM log WHERE ${matchAudit} AND provider_no=${q(provider)} AND demographic_no=${patient}`,
    `DELETE FROM labTestResults WHERE labPatientPhysicianInfo_id=${id}`,
    `DELETE FROM labRequestReportLink WHERE report_table='labPatientPhysicianInfo' AND report_id=${id}`,
    `DELETE FROM labPatientPhysicianInfo WHERE id=${id}`,
    `DELETE FROM labReportInformation WHERE id=${report.infoId}`,
  ].join(';'));
  h.assert(remaining() === '0', `The owned ${report.label} report fixture was not removed`);
}

async function workflow(s) {
  const { sql, config, recorder, marker, patient, provider } = s;
  h.assert(/^FAKE-PW[0-9a-f]{16}$/.test(marker), 'The run marker is not in the FAKE-PW<hex> form this check relies on');
  const { v1, v2, recycled, matchFirst, matchSecond, matchRecycled } = describeReports({ sql, marker });
  const reports = [v1, v2, recycled, matchFirst, matchSecond, matchRecycled];
  const recipient = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => recipient.cleanup());
  const favouritesBefore = sql.rows(`SELECT id,route_to_provider_no FROM providerLabRoutingFavorites WHERE provider_no=${q(provider)} ORDER BY id`);
  s.cleanup(() => {
    const currentFavorites = () => sql.rows(`SELECT id,route_to_provider_no FROM providerLabRoutingFavorites WHERE provider_no=${q(provider)} ORDER BY id`);
    if (JSON.stringify(currentFavorites()) === JSON.stringify(favouritesBefore)) return;
    sql.execute(`DELETE FROM providerLabRoutingFavorites WHERE provider_no=${q(provider)}`);
    for (const [id, to] of favouritesBefore) {
      h.assert(/^[1-9]\d*$/.test(id), 'Invalid stored favorite key');
      sql.execute(`INSERT INTO providerLabRoutingFavorites(id,provider_no,route_to_provider_no) VALUES(${id},${q(provider)},${q(to)})`);
    }
    h.assert(JSON.stringify(currentFavorites()) === JSON.stringify(favouritesBefore), 'Sender forwarding favorites were not restored');
  });
  for (const report of reports) report.marker = marker;
  const comment = `${marker} version chain acknowledged`;
  const promptText = bundleMessage('oscarMDS.segmentDisplay.msgComment', 'Please enter a comment (max. 255 characters)');

  // The routing of one report, exactly as stored: "unchanged" means no write touched the row (id, status, comment, timestamp).
  const rowsOf = (report) => JSON.stringify(sql.rows(`SELECT id, provider_no, status, IFNULL(comment,'<null>'), IFNULL(timestamp,'<null>')
    FROM providerLabRouting WHERE lab_type=${q(LAB_TYPE)} AND lab_no=${report.id} ORDER BY id`));
  const statusOf = (report) => sql.value(`SELECT IFNULL(GROUP_CONCAT(status ORDER BY id),'') FROM providerLabRouting
    WHERE lab_type=${q(LAB_TYPE)} AND lab_no=${report.id} AND provider_no=${q(provider)}`);

  // Cleanup runs last-registered first. Everything is registered before its first write.
  s.cleanup(() => {
    const failures = [];
    for (const report of [...reports].reverse()) {
      try { removeReport({ sql, report, patient, provider }); } catch (error) { failures.push(error.message); }
    }
    h.assert(failures.length === 0, failures.join('; '));
  });

  const opener = await s.context.newPage();

  /** The patient lab list, the page a clinician opens these labs from. */
  async function openList() {
    await h.gotoApp(opener, config.baseUrl, `/lab/ViewDemographicLab?demographicNo=${patient}`);
    await h.assertNotErrorPage(opener, 'the patient lab list');
    await opener.locator('#labResultsTbl').waitFor({ state: 'attached', timeout: TIMEOUT });
  }

  /**
   * Add the row link DemographicLab.jsp renders for a CML lab (it calls the page's own reportWindow()) and click
   * it. Returns the report popup, which has the list as its opener.
   */
  async function openReport(report, label) {
    const url = h.appUrl(config.baseUrl, `/lab/CA/ON/ViewCMLDisplay?demographicId=${patient}&segmentID=${report.id}`
      + `&providerNo=${provider}&searchProviderNo=${provider}&status=N`);
    const anchor = `cml-version-chain-${report.key}`;
    await opener.evaluate(({ id, href }) => {
      const old = document.getElementById(id);
      if (old) old.remove();
      const link = document.createElement('a');
      link.id = id;
      link.href = `javascript:reportWindow(${JSON.stringify(href)})`;
      link.textContent = 'CML report';
      document.body.prepend(link);
    }, { id: anchor, href: url });
    const popup = await s.popup(opener, opener.locator(`#${anchor}`), label);
    await popup.waitForLoadState('load', { timeout: TIMEOUT });
    const form = popup.locator('form[name="acknowledgeForm"]');
    await form.waitFor({ state: 'attached', timeout: 10000 }).catch(() => {});
    await h.assertNotErrorPage(popup, `the ${report.label} CML report`);
    h.assert(await form.count() === 1, `The ${report.label} CML report page has no acknowledge form`);
    return popup;
  }

  /**
   * A window that closes, or navigates, ends with a script load Chromium abandons (the CSRFGuard client script the
   * response filter injects). It is the browser giving up a load because its document went away, not an application
   * failure; excuse exactly that, proven by navigatedAway(), and nothing else.
   */
  function excuseAbandonedLoads() {
    for (let i = recorder.requestFailures.length - 1; i >= 0; i -= 1) {
      const entry = recorder.requestFailures[i];
      if (entry.resourceType === 'script' && /ERR_ABORTED/.test(entry.errorText || '')
        && new URL(entry.url).pathname.endsWith('/csrfguard')
        && typeof entry.navigatedAway === 'function' && entry.navigatedAway()) {
        recorder.requestFailures.splice(i, 1);
      }
    }
  }

  /** The "Version:" row of a report page: its text, and its links as { text, segmentID, multiID } (own version is not a link). */
  async function versionRow(popup, where) {
    const rows = popup.locator('div.Field2').filter({ hasText: /^\s*Version:/ });
    h.assert(await rows.count() === 1, `${where} shows ${await rows.count()} "Version:" rows, expected 1 `
      + '(the report is chained to its other versions only when the chain lookup returns more than itself)');
    const row = rows.first();
    const links = [];
    for (const link of await row.locator('a').all()) {
      const url = new URL(await link.getAttribute('href'), popup.url());
      links.push({ text: squash(await link.innerText()), segmentID: url.searchParams.get('segmentID'), multiID: url.searchParams.get('multiID') });
    }
    return { text: squash(await row.innerText()), links };
  }

  /** The page shows this report's own result row and not the other version's. */
  async function expectOwnResult(popup, report, other, where) {
    const own = await popup.locator('#tblDiscs tr').filter({ hasText: report.test }).count();
    h.assert(own === 1, `${where} shows ${own} result rows for the ${report.label} report's test, expected 1`);
    const foreign = await popup.locator('#tblDiscs tr').filter({ hasText: other.test }).count();
    h.assert(foreign === 0, `${where} shows the ${other.label} report's result as well`);
  }

  // ---- Fixtures ---------------------------------------------------------------------------------------------
  await s.step(STEP.seed, async () => {
    recipient.create();
    for (const report of reports) {
      seedReport({ sql, marker, patient, provider, report });
      h.assert(sql.value(`SELECT COUNT(*) FROM labPatientPhysicianInfo WHERE id=${report.id} AND patient_last_name=${q(marker)}`) === '1',
        `The ${report.label} report was not seeded`);
      if (report.matched === false) {
        h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type=${q(LAB_TYPE)} AND lab_no=${report.id}`) === '0',
          `The ${report.label} report is not unmatched`);
      } else {
        h.assert(statusOf(report) === 'N', `The ${report.label} report is not routed to the test provider as New`);
      }
    }
    for (const group of [[v1, v2, recycled], [matchFirst, matchSecond, matchRecycled]]) {
      h.assert(sql.value(`SELECT COUNT(DISTINCT accession_num) FROM labPatientPhysicianInfo WHERE id IN (${group.map((r) => r.id).join(',')})`) === '1',
        'The seeded reports of one order do not share an accession number');
    }
    await openList();
  });

  // ---- Display ----------------------------------------------------------------------------------------------
  await s.step(STEP.display, async () => {
    excuseAbandonedLoads();
    const listLinks = opener.locator('#labResultsTbl a[href*="ViewCMLDisplay"]');
    // Packaged installs can disable CML lists. When enabled, validate the real source-aware grouping too.
    if (await listLinks.count()) {
      const listedIds = await listLinks.evaluateAll((links) => links.map((link) => new URL(link.href).searchParams.get('segmentID')));
      h.assert(listedIds.includes(String(v2.id)) && listedIds.includes(String(recycled.id)) && !listedIds.includes(String(v1.id)),
        'The patient list did not collapse exactly the two current CML versions while retaining the recycled report');
    }
    const popup = await openReport(v2, `${NAME}-v2-display`);
    try {
      const row = await versionRow(popup, 'The final report page');
      h.assert(row.text === 'Version: v1 v2', `The final report page's Version row reads "${row.text}", expected "Version: v1 v2"`);
      h.assert(row.links.length === 1 && row.links[0].text === 'v1',
        `The final report page links ${row.links.map((l) => l.text).join(', ') || 'no version'}; only v1 is another version, and v2 is the page itself`);
      h.assert(row.links[0].segmentID === String(v1.id), 'The v1 link does not open the preliminary report');
      h.assert(row.links[0].multiID === `${v1.id},${v2.id}`,
        `The v1 link carries the chain "${row.links[0].multiID}", expected the two versions in print order`);
      h.assert(!(await popup.content()).includes(String(recycled.id)),
        'The final report page names the report that recycled the accession number');
      await expectOwnResult(popup, v2, v1, 'The final report page');
    } finally {
      await popup.close().catch(() => {});
    }
  });

  // ---- The older version ------------------------------------------------------------------------------------
  await s.step(STEP.older, async () => {
    const popup = await openReport(v2, `${NAME}-v2-to-v1`);
    try {
      const link = popup.locator('div.Field2').filter({ hasText: /^\s*Version:/ }).first().locator('a', { hasText: /^\s*v1\s*$/ });
      h.assert(await link.count() === 1, 'The final report page has no v1 link to follow');
      // waitForURL, not waitForLoadState: the page being left is already loaded, so that would return before the click lands.
      await Promise.all([
        popup.waitForURL((url) => url.searchParams.get('segmentID') === String(v1.id), { timeout: TIMEOUT }),
        link.click({ timeout: TIMEOUT }),
      ]);
      excuseAbandonedLoads();
      await h.assertNotErrorPage(popup, 'the preliminary CML report');
      h.assert(new URL(popup.url()).searchParams.get('segmentID') === String(v1.id), 'The v1 link did not open the preliminary report');
      await expectOwnResult(popup, v1, v2, 'The preliminary report page');
      const row = await versionRow(popup, 'The preliminary report page');
      h.assert(row.text === 'Version: v1 v2', `The preliminary report page's Version row reads "${row.text}", expected "Version: v1 v2"`);
      h.assert(row.links.length === 1 && row.links[0].text === 'v2' && row.links[0].segmentID === String(v2.id),
        'The preliminary report page does not link back to the final report');
    } finally {
      await popup.close().catch(() => {});
    }
  });

  // ---- Acknowledge ------------------------------------------------------------------------------------------
  await s.step(STEP.acknowledge, async () => {
    excuseAbandonedLoads();
    const recycledBefore = rowsOf(recycled);
    const popup = await openReport(v2, `${NAME}-v2-acknowledge`);
    try {
      const sentinel = await ui.markOpener(opener);
      const reloaded = opener.waitForEvent('load', { timeout: TIMEOUT });
      reloaded.catch(() => {});
      const dialogs = await h.withExpectedDialogs(popup, async () => {
        const closed = popup.waitForEvent('close', { timeout: TIMEOUT });
        closed.catch(() => {});
        await popup.locator('form[name="acknowledgeForm"] input[type="submit"]').first().click({ timeout: TIMEOUT });
        await closed;
      }, { promptText: comment });
      h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt' && dialogs[0].text === promptText,
        `Acknowledge did not ask for a comment with the expected prompt (${dialogs.map((d) => d.type).join(',') || 'no dialog'})`);
      await reloaded;
      await opener.waitForLoadState('load', { timeout: TIMEOUT });
      h.assert(await opener.evaluate((name) => window[name], sentinel.marker) === undefined, // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the sentinel name is a module constant
        'The patient lab list was not reloaded after the acknowledgement (Close.jsp refreshes its opener)');
      await expectValue(sql, `SELECT CONCAT(COUNT(*),':',IFNULL(MIN(status),''),':',IFNULL(MIN(comment),'')) FROM providerLabRouting
        WHERE lab_type=${q(LAB_TYPE)} AND lab_no=${v2.id} AND provider_no=${q(provider)}`, `1:A:${comment}`,
      'Acknowledging the final report did not leave exactly one routing row, status A, with the comment');
      await expectValue(sql, `SELECT CONCAT(COUNT(*),':',IFNULL(MIN(status),'')) FROM providerLabRouting
        WHERE lab_type=${q(LAB_TYPE)} AND lab_no=${v1.id} AND provider_no=${q(provider)}`, '1:F',
      'Acknowledging the final report did not file the preliminary report (its routing row is not F): '
        + 'the Inbox would keep it New next to the acknowledged one');
      h.assert(rowsOf(recycled) === recycledBefore,
        'Acknowledging the final report touched the report that recycled the accession number: a report of another order was filed');
      await opener.waitForTimeout(500);
      excuseAbandonedLoads();
    } finally {
      await popup.close().catch(() => {});
    }
    const again = await openReport(v2, `${NAME}-v2-acknowledged`);
    try {
      const text = squash(await again.locator('body').innerText({ timeout: TIMEOUT }));
      h.assert(text.includes('Acknowledged') && text.includes(`comment : ${comment}`),
        'The final report opened again does not show it Acknowledged with the comment');
      h.assert(text.includes('Filed but not acknowledged'),
        'The final report opened again does not show the preliminary version as filed');
    } finally {
      await again.close().catch(() => {});
    }
  });

  await s.step(STEP.forward, async () => {
    const before = [statusOf(v1), statusOf(v2), rowsOf(recycled)];
    const page = await openReport(v2, `${NAME}-forward-report`);
    let picker;
    try {
      picker = await s.popup(page, page.getByRole('button', { name: 'Forward', exact: true }).first(), `${NAME}-forward-picker`);
      await picker.locator('#autocompleteprov').fill(`${marker}, Throwaway`);
      const offered = picker.locator('.ui-autocomplete:visible .ui-menu-item').filter({ hasText: marker }).first();
      await offered.waitFor({ state: 'visible', timeout: TIMEOUT });
      await offered.click();
      h.assert(JSON.stringify(await picker.locator('#fwdProviders option').evaluateAll((options) => options.map((o) => o.value)))
        === JSON.stringify([recipient.providerNo]), 'The forward list does not contain exactly the owned recipient');
      const response = page.waitForResponse((r) => r.request().method() === 'POST' && /\/lab\/CA\/ON\/Forward$/.test(new URL(r.url()).pathname), { timeout: TIMEOUT });
      await picker.locator('#submitButton').click();
      const posted = await response;
      h.assert(posted.status() >= 200 && posted.status() < 400, `Forward returned HTTP ${posted.status()}`);
      await expectValue(sql, `SELECT GROUP_CONCAT(lab_no ORDER BY lab_no) FROM providerLabRouting
        WHERE lab_type='CML' AND provider_no=${q(recipient.providerNo)}`, [v1.id, v2.id].sort((a, b) => a - b).join(','),
      'Forward did not route exactly both versions to the recipient');
      h.assert(JSON.stringify([statusOf(v1), statusOf(v2), rowsOf(recycled)]) === JSON.stringify(before),
        'Forward changed sender statuses or the recycled report');
    } finally {
      if (picker) await picker.close().catch(() => {});
      await page.close().catch(() => {});
    }
    excuseAbandonedLoads();
  });

  // ---- Match ------------------------------------------------------------------------------------------------
  await s.step(STEP.match, async () => {
    excuseAbandonedLoads();
    const matchLabel = `${NAME}-match-search`;
    const page = await openReport(matchSecond, `${NAME}-match-report`);
    let matching;
    try {
      // The button's label is a missing-key string on this page (finding 196), so it is found by the popup it opens.
      matching = await s.popup(page, page.locator('input[type="button"][onclick*="/oscarMDS/SearchPatient"]').first(), matchLabel);
      await matching.locator('#keyword').waitFor({ state: 'visible', timeout: TIMEOUT });
      await matching.locator('#keyword').fill(marker);
      await matching.locator('button[name="displaymode"], button:has-text("Search")').first().click();
      const rows = matching.locator('#patientsTable tbody tr').filter({ hasText: marker });
      await rows.first().waitFor({ timeout: TIMEOUT });
      h.assert(await rows.count() === 1 && (await rows.first().innerText()).includes(patient),
        'The matching popup does not list exactly the owned patient');
      const [post] = await Promise.all([
        matching.waitForResponse((r) => r.request().method() === 'POST' && /\/oscarMDS\/PatientMatch/.test(r.url()), { timeout: TIMEOUT }),
        rows.first().click(),
      ]);
      h.assert(post.status() === 200, `Choosing the patient made PatientMatch answer HTTP ${post.status()}, expected 200 `
        + '(the match resolves the report\'s versions through the strict chain lookup, which fails when the chain cannot be built)');
      const versions = [matchFirst.id, matchSecond.id].sort((a, b) => a - b).join(',');
      await expectValue(sql, `SELECT IFNULL(GROUP_CONCAT(lab_no ORDER BY lab_no),'') FROM patientLabRouting
        WHERE lab_type=${q(LAB_TYPE)} AND demographic_no=${patient}
          AND lab_no IN (${[matchFirst, matchSecond, matchRecycled].map((r) => r.id).join(',')})`, versions,
      'Matching the patient did not link exactly the two versions of the order to the owned patient');
      h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type=${q(LAB_TYPE)} AND lab_no=${matchRecycled.id}`) === '0',
        'Matching the patient linked the report that recycled the accession number');
      // The match answers with a redirect to the chart that the popup's fetch() follows and window.close() then
      // aborts (PatientMatch2Action, tracked with the matching popup's other quirks in
      // gap-records-lab-patient-link-playwright-checks.js). It is the browser giving up that request, not a
      // failure of the match; excuse exactly that and nothing else.
      await page.waitForTimeout(1500);
      for (let i = recorder.requestFailures.length - 1; i >= 0; i -= 1) {
        const failure = recorder.requestFailures[i];
        if (failure.label === matchLabel && /\/oscarMDS\/ViewOpenEChart/.test(failure.url) && /ERR_ABORTED/.test(failure.errorText || '')) {
          recorder.requestFailures.splice(i, 1);
        }
      }
    } finally {
      if (matching) await matching.close().catch(() => {});
      await page.close().catch(() => {});
    }
    // The OLDER version, which was never the report the match was started from, is now the patient's too.
    const older = await openReport(matchFirst, `${NAME}-match-first`);
    try {
      const msg = older.locator('input[type="button"][value="Msg"]').first();
      h.assert(await msg.count() === 1 && (await msg.getAttribute('onclick') || '').includes(`demographic_no=${patient}`),
        'The first version of the matched order is not linked to the owned patient (no Msg button names demographic_no)');
    } finally {
      await older.close().catch(() => {});
    }
  });
  await s.step(STEP.ambiguous, async () => {
    sql.execute(`UPDATE labPatientPhysicianInfo SET patient_health_num='OTHER-TEST' WHERE id=${v1.id}`);
    const before = [rowsOf(v1), rowsOf(v2)];
    const page = await openReport(v2, `${NAME}-ambiguous-report`);
    try {
      await page.getByRole('alert').filter({ hasText: 'The complete report version chain could not be verified' }).waitFor({ timeout: TIMEOUT });
      h.assert(await page.locator(`a[href*="segmentID=${v1.id}"]`).count() === 0, 'An ambiguous sibling is still linked');
      h.assert(JSON.stringify([rowsOf(v1), rowsOf(v2)]) === JSON.stringify(before), 'Viewing ambiguity changed routing');
    } finally {
      await page.close().catch(() => {});
    }
    excuseAbandonedLoads();
  });

}

if (require.main === module) {
  runWorkflow(NAME, workflow, { openMaster: false });
}
module.exports = { workflow, STEP, removeReport };
