#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// A document received inside the search dates must survive Hibernate's native date
// conversion. Its observation date is deliberately outside that same interval.
// Run on a disposable database: the date preference is restored after the check.
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { clickOpensPopupOrNavigates, pickDate } = require('./lib/playwright-ui');
const { settle, shownRows } = require('./inboxhub-filters-playwright-checks');

async function workflow(s) {
  const timeout = 60000;
  // Enter list mode before seeding: this check exercises inbox metadata and does
  // not open an attachment viewer or require a physical document file.
  const { page: inbox } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink'), {
    context: s.context, label: 'document-date-inbox', recorder: s.recorder, timeout,
  });
  await inbox.locator('#btnViewMode2').waitFor({ state: 'attached', timeout });
  if (await inbox.locator('#btnViewMode2').isChecked()) await inbox.locator('#btnViewModeLabel').click();
  await settle(inbox, timeout);

  const preferenceName = "name='inboxDateSearchType'";
  const previous = s.sql.rows(`SELECT id, IFNULL(HEX(value),'NULL') FROM SystemPreferences WHERE ${preferenceName}`);
  let insertedPreference;
  s.cleanup(() => {
    if (insertedPreference) s.sql.execute(`DELETE FROM SystemPreferences WHERE id=${insertedPreference} AND ${preferenceName}`);
    for (const [id, hex] of previous) {
      assert(/^\d+$/.test(id) && (hex === 'NULL' || /^[0-9A-F]*$/i.test(hex)), 'Unexpected preference snapshot');
      s.sql.execute(`UPDATE SystemPreferences SET value=${hex === 'NULL' ? 'NULL' : `UNHEX('${hex}')`} WHERE id=${id} AND ${preferenceName}`);
    }
  });
  if (!previous.length) {
    insertedPreference = s.sql.value(`INSERT INTO SystemPreferences (name,value,updateDate)
      VALUES ('inboxDateSearchType','receivedCreated',NOW()); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(insertedPreference), 'Date preference fixture was not inserted');
  }
  const setPreference = value => s.sql.execute(`UPDATE SystemPreferences SET value=${sqlString(value)} WHERE ${preferenceName}`);
  setPreference('receivedCreated');

  let documentNo;
  s.cleanup(() => {
    if (!documentNo) return;
    assert(s.sql.value(`SELECT COUNT(*) FROM document WHERE document_no=${documentNo} AND docdesc=${sqlString(s.marker)}`) === '1',
      'Document fixture ownership changed');
    s.sql.execute(`DELETE FROM providerLabRouting WHERE lab_no=${documentNo} AND lab_type='DOC';
      DELETE FROM ctl_document WHERE document_no=${documentNo} AND module='demographic' AND module_id=${s.patient};
      DELETE FROM document WHERE document_no=${documentNo} AND docdesc=${sqlString(s.marker)}`);
  });
  documentNo = s.sql.value(`INSERT INTO document
    (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime)
    VALUES ('lab',${sqlString(s.marker)},${sqlString(`${s.marker}.pdf`)},${sqlString(s.provider)},${sqlString(s.provider)},
      'A','application/pdf',0,1,0,'2026-02-01','2026-03-04 12:00:00','2026-03-04 12:00:00'); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(documentNo), 'Document fixture was not inserted');
  s.sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${s.patient},${documentNo},'A');
    INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${sqlString(s.provider)},${documentNo},'DOC','N')`);

  // A search submits the form and reloads the Inbox, whose sidebar starts collapsed on every
  // load, so each search reopens it and fills the criteria again.
  const search = async () => {
    if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
    await inbox.locator('#anyProvider').check();
    await inbox.locator('#statusNew').check();
    await inbox.locator('#specificPatients').check();
    await inbox.locator('#inputLastName').fill(s.marker);
    await pickDate(inbox, '#startDate', '2026-03-03');
    await pickDate(inbox, '#endDate', '2026-03-05');
    await inbox.locator('#inboxhubFormSearchBtn').click();
    await settle(inbox, timeout);
    return shownRows(inbox);
  };
  await s.step('received-date search includes the document with its exact identity', async () => {
    assert((await search()).includes(`DOC:${documentNo}`), 'Received-date search silently omitted the matching document');
  });
  await s.step('observation-date search excludes the same document', async () => {
    setPreference('serviceObservation');
    assert(!(await search()).includes(`DOC:${documentNo}`), 'Observation-date search ignored its date interval');
  });
  await s.step('switching back restores the received document', async () => {
    setPreference('receivedCreated');
    assert((await search()).includes(`DOC:${documentNo}`), 'Received-date preference did not restore the result');
  });
}

if (require.main === module) runWorkflow('inbox-document-dates', workflow, { openMaster: false });
module.exports = { workflow };
