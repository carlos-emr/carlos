#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Native-query value typing (Hibernate 7): the Inbox lab list under both date modes.
 * User path: Schedule > Inbox (#inboxLink) > list mode > "Any provider", status "New", "All patients",
 *   a one-day date window around the lab > Search; the Date column of the owned HL7 lab row, once with
 *   the system preference inboxDateSearchType = serviceObservation (the test date, a varchar
 *   column) and once = receivedCreated (hl7TextMessage.created, a DATETIME).
 * Why: Hl7TextInfoDaoImpl.findLabAndDocsViaMagic is a native query and Hl7textResultsData.
 *   populateHL7ResultsData(...) renders each column with String.valueOf(row[n]). Hibernate 7 hands a
 *   native DATETIME back as java.time.LocalDateTime, whose text is ISO-8601 with a "T"
 *   ("2026-03-04T12:34:56"), not the "2026-03-04 12:34:56" the document rows beside it show and
 *   that LabResultData.getDateObj() ("yyyy-MM-dd HH:mm:ss") can parse.
 * Asserts: the owned lab is listed in both modes; observation mode shows the stored test date
 *   verbatim; received mode shows hl7TextMessage.created as "YYYY-MM-DD HH:MM:SS" (no "T") and
 *   the same instant SQL stores. The failing step is the last one, so the baseline is proven first.
 * Fixtures: one synthetic lab (hl7TextMessage + hl7TextInfo + providerLabRouting + patientLabRouting)
 *   routed to the run's FAKE- patient. The global SystemPreferences row inboxDateSearchType is
 *   snapshotted and restored (EXCLUSIVE=1: it changes every user's Inbox date mode meanwhile).
 *   Cleanup deletes only rows carrying the run's lab number and asserts they are gone.
 * Coverage plan: risk sweep "native-cast" (Hibernate 7 native result typing), Inbox.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { clickOpensPopupOrNavigates, pickDate } = require('./lib/playwright-ui');
const { settle } = require('./inboxhub-filters-playwright-checks');

const TIMEOUT = 60000;
const OBSERVED = '2026-02-01 10:00:00';
const CREATED = '2026-03-04 12:34:56';
const PREFERENCE = "name='inboxDateSearchType'";

async function workflow(s) {
  const { sql, marker } = s;
  const { page: inbox } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink'), {
    context: s.context, label: 'native-cast-inbox', recorder: s.recorder, timeout: TIMEOUT,
  });
  await inbox.locator('#btnViewMode2').waitFor({ state: 'attached', timeout: TIMEOUT });
  if (await inbox.locator('#btnViewMode2').isChecked()) await inbox.locator('#btnViewModeLabel').click();
  await settle(inbox, TIMEOUT);

  // Snapshot the global preference BEFORE touching it; restore the exact bytes (or delete our insert).
  // sql.rows() reads the text 'NULL' as JavaScript null, so a SQL NULL value is carried by a separate
  // `value IS NULL` flag rather than a 'NULL' sentinel in the hex column.
  const previous = sql.rows(`SELECT id, IFNULL(HEX(value),''), value IS NULL FROM SystemPreferences WHERE ${PREFERENCE}`);
  let insertedPreference;
  s.cleanup(() => {
    if (insertedPreference) sql.execute(`DELETE FROM SystemPreferences WHERE id=${insertedPreference} AND ${PREFERENCE}`);
    for (const [id, hex, isNull] of previous) {
      h.assert(/^\d+$/.test(id) && /^[0-9A-F]*$/i.test(hex || '') && (isNull === '0' || isNull === '1'), 'Unexpected preference snapshot');
      sql.execute(`UPDATE SystemPreferences SET value=${isNull === '1' ? 'NULL' : `UNHEX('${hex || ''}')`} WHERE id=${id} AND ${PREFERENCE}`);
    }
  });
  if (!previous.length) {
    insertedPreference = sql.value(`INSERT INTO SystemPreferences (name,value,updateDate)
      VALUES ('inboxDateSearchType','serviceObservation',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(insertedPreference), 'Date preference fixture was not inserted');
  }
  const setPreference = value => sql.execute(`UPDATE SystemPreferences SET value=${h.sqlString(value)} WHERE ${PREFERENCE}`);

  let labNo;
  s.cleanup(() => {
    if (!labNo) return;
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${labNo} AND last_name=${h.sqlString(marker)}`) === '1',
      'Lab fixture ownership changed');
    sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=${labNo} AND lab_type='HL7';
      DELETE FROM patientLabRouting WHERE lab_no=${labNo} AND lab_type='HL7';
      DELETE FROM hl7TextInfo WHERE lab_no=${labNo} AND last_name=${h.sqlString(marker)};
      DELETE FROM hl7TextMessage WHERE lab_id=${labNo} AND serviceName='NATIVECAST'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${labNo})
      + (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${labNo})
      + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${labNo} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${labNo} AND lab_type='HL7')`) === '0', 'Lab fixture rows remain');
  });
  const message = Buffer.from(`MSH|^~\\&|NATIVECAST|CW|CARLOS|TEST|20260304123456||ORU^R01|${marker}|P|2.3\r`
    + `PID||9999999999|9999999999||${marker}^CHECK||19800101|F\r`, 'utf8').toString('base64');
  labNo = sql.value(`INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)
    VALUES (0, ${h.sqlString(message)}, 'PATHL7', 'NATIVECAST', ${h.sqlString(CREATED)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(labNo), 'Lab fixture message was not inserted');
  sql.execute(`INSERT INTO hl7TextInfo (lab_no, sex, health_no, result_status, final_result_count, obr_date, priority,
      requesting_client, discipline, last_name, first_name, report_status, accessionNum, filler_order_num, sending_facility)
    VALUES (${labNo}, 'F', '9999999999', 'A', 1, ${h.sqlString(OBSERVED)}, 'R', 'FAKE-ORDERING, DOC', 'CHEM',
      ${h.sqlString(marker)}, 'CHECK', 'F', ${h.sqlString(marker)}, ${h.sqlString(marker)}, 'CW');
    INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES (${h.sqlString(s.provider)}, ${labNo}, 'N', 'HL7');
    INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created) VALUES (${s.patient}, ${labNo}, 'HL7', NOW())`);

  if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
  await inbox.locator('#anyProvider').check();
  await inbox.locator('#statusNew').check();
  await inbox.locator('#allPatients').check();

  // The Date column is located by its header, not by position, so a re-ordered table cannot
  // make the check read the wrong cell.
  async function ownedLabDate(from, to) {
    // The sidebar collapses after a search, hiding the date pickers.
    if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
    await pickDate(inbox, '#startDate', from);
    await pickDate(inbox, '#endDate', to);
    await inbox.locator('#inboxhubFormSearchBtn').click();
    await settle(inbox, TIMEOUT);
    const row = inbox.locator(`#inboxhubListModeTableBody tr[data-segment-id="${labNo}"][data-lab-type="HL7"]`);
    h.assert(await row.count() === 1, 'The owned lab is not listed exactly once');
    const headers = await inbox.$$eval('table thead th', els => els.map(e => (e.textContent || '').replace(/\s+/g, ' ').trim()));
    const column = headers.findIndex(text => /date/i.test(text));
    h.assert(column >= 0, 'The Inbox list has no Date column header');
    const cells = await row.locator('td').allTextContents();
    return (cells[column] || '').replace(/\s+/g, ' ').trim();
  }

  await s.step('observation-date mode lists the owned lab with the stored test date verbatim', async () => {
    setPreference('serviceObservation');
    const shown = await ownedLabDate('2026-01-31', '2026-02-02');
    h.assert(shown === OBSERVED, `Observation-date mode shows "${shown}", expected the stored test date "${OBSERVED}"`);
  });

  await s.step('received-date mode lists the owned lab with the message creation time as plain text', async () => {
    setPreference('receivedCreated');
    const shown = await ownedLabDate('2026-03-03', '2026-03-05');
    h.assert(!/T/.test(shown),
      `Received-date mode shows the lab date as "${shown}": an ISO-8601 java.time text with a "T", not the "YYYY-MM-DD HH:MM:SS" `
      + 'the document rows and LabResultData.getDateObj() expect (native DATETIME arrives as LocalDateTime)');
    h.assert(/^2026-03-04 12:34:56(\.\d+)?$/.test(shown), `Received-date mode shows "${shown}", expected "${CREATED}"`);
  });
}

if (require.main === module) runWorkflow('native-cast-inbox-lab-dates', workflow, { openMaster: false });
module.exports = { workflow };
