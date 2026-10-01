#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Date-range inclusivity of the Inbox Hub date filter (wave 6, boundary values, Part 2).
 * User path: Schedule > Inbox (#inboxLink) > Inbox Hub list mode > New status, patient surname > Start Date /
 * End Date > Search.
 * Asserts, for documents (service/observation date) and HL7 lab results (observation timestamp) dated exactly
 * on the Start Date, at 14:30 and 23:59:59 on the End Date, and one second past it: everything dated on the
 * Start Date through the whole End Date is listed and nothing dated before the Start Date or after the End Date.
 * Fixtures: two documents with ctl_document links and routing rows for the owned patient; five labs (hl7TextMessage
 * + hl7TextInfo + providerLabRouting) carrying the run marker as patient surname; cleanup removes every row by id
 * and asserts none remain. The inbox date preference must be the default service/observation date; it is only touched (snapshot, set, restore) when an install
 * has it on "received", in which case run the check with EXCLUSIVE=1.
 * Implements the wave-6 "boundary values" pattern, Part 2 (lab/inbox/document date filters, end-date inclusivity).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { settle, shownRows } = require('./inboxhub-filters-playwright-checks');

const START = '2026-03-03';
const END = '2026-03-05';

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const timeout = 60000;
  const docs = {};
  const labs = {};
  s.cleanup(() => {
    const docIds = Object.values(docs);
    const labIds = Object.values(labs);
    if (docIds.length) {
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${docIds.join(',')});
        DELETE FROM ctl_document WHERE module='demographic' AND module_id=${patient} AND document_no IN (${docIds.join(',')});
        DELETE FROM document WHERE document_no IN (${docIds.join(',')}) AND docdesc=${q(marker)}`);
    }
    if (labIds.length) {
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${labIds.join(',')});
        DELETE FROM hl7TextInfo WHERE lab_no IN (${labIds.join(',')}) AND last_name=${q(marker.slice(0, 30))};
        DELETE FROM hl7TextMessage WHERE lab_id IN (${labIds.join(',')}) AND type=${q('BOUNDARY')}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM document WHERE docdesc=${q(marker)})
      + (SELECT COUNT(*) FROM hl7TextInfo WHERE last_name=${q(marker.slice(0, 30))})
      + (SELECT COUNT(*) FROM hl7TextMessage WHERE type='BOUNDARY' AND message=${q(marker)})`) === '0', 'Owned inbox fixtures were not removed');
  });

  // The labs below are dated by observation time (hl7TextInfo.obr_date). With the "received" mode the same query filters on
  // hl7TextMessage.created, which these fixtures stamp NOW(), so none would fall in March 2026 and the check would blame the
  // date filter for a preference. Make sure observation mode is in force; touch the global row only when it is not.
  const preferenceName = "name='inboxDateSearchType'";
  const previous = sql.rows(`SELECT id, IFNULL(HEX(value),''), value IS NULL FROM SystemPreferences WHERE ${preferenceName}`);
  const inObservationMode = previous.length === 0 || (previous[0][2] === '0' && Buffer.from(previous[0][1] || '', 'hex').toString('utf8') === 'serviceObservation');
  s.cleanup(() => {
    if (inObservationMode) return;
    for (const [id, hex, isNull] of previous) {
      h.assert(/^\d+$/.test(id) && /^[0-9A-F]*$/i.test(hex || '') && (isNull === '0' || isNull === '1'), 'Unexpected preference snapshot');
      sql.execute(`UPDATE SystemPreferences SET value=${isNull === '1' ? 'NULL' : `UNHEX('${hex || ''}')`} WHERE id=${id} AND ${preferenceName}`);
    }
  });
  if (!inObservationMode) sql.execute(`UPDATE SystemPreferences SET value='serviceObservation' WHERE ${preferenceName}`);

  // UI-created patients carry an empty health number; the runWorkflow fixture leaves it NULL, which the Inbox patient filter
  // (d.hin like ...) never matches (known, ISSUES L207). Give the owned patient the empty HIN so this check judges dates only.
  sql.execute(`UPDATE demographic SET hin='' WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const docDates = { docStart: START, docMid: '2026-03-04', docEnd: END, docBefore: '2026-03-02', docAfter: '2026-03-06' };
  for (const [name, date] of Object.entries(docDates)) {
    docs[name] = sql.value(`INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime)
      VALUES ('lab',${q(marker)},${q(`${marker}-${name}.pdf`)},${q(provider)},${q(provider)},'A','application/pdf',0,1,0,${q(date)},${q(`${date} 12:00:00`)},${q(`${date} 12:00:00`)});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(docs[name]), `Document fixture ${name} was not inserted`);
    sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${docs[name]},'A');
      INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${q(provider)},${docs[name]},'DOC','N')`);
  }
  const labStamps = {
    labStart: `${START} 00:00:00`, labEndMid: `${END} 14:30:00`, labEndLast: `${END} 23:59:59`, labBefore: '2026-03-02 23:59:59', labAfter: '2026-03-06 00:00:00',
  };
  for (const [name, stamp] of Object.entries(labStamps)) {
    const id = sql.value(`INSERT INTO hl7TextMessage (fileUploadCheck_id,message,type,serviceName,created)
      VALUES (0,${q(marker)},'BOUNDARY','BOUNDARY',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `Lab message fixture ${name} was not inserted`);
    labs[name] = id;
    sql.execute(`INSERT INTO hl7TextInfo (lab_no,sex,health_no,result_status,final_result_count,obr_date,priority,requesting_client,discipline,last_name,first_name,report_status,accessionNum,label)
      VALUES (${id},'F','',NULL,1,${q(stamp)},'R','','BOUNDARY',${q(marker.slice(0, 30))},'Boundary','F',${q(`${marker}-${name}`)},${q(name)});
      INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${q(provider)},${id},'HL7','N')`);
  }

  const { page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink'),
    { context: s.context, label: 'boundary-inbox', recorder: s.recorder, timeout });
  await inbox.locator('#btnViewMode2').waitFor({ state: 'attached', timeout });
  if (await inbox.locator('#btnViewMode2').isChecked()) await inbox.locator('#btnViewModeLabel').click();
  await settle(inbox, timeout);
  if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
  await inbox.locator('#anyProvider').check();
  await inbox.locator('#statusNew').check();
  await inbox.locator('#specificPatients').check();
  await inbox.locator('#inputLastName').fill(marker);
  await ui.pickDate(inbox, '#startDate', START);
  await ui.pickDate(inbox, '#endDate', END);
  const search = async () => {
    await inbox.locator('#inboxhubFormSearchBtn').click();
    await settle(inbox, timeout);
    return shownRows(inbox);
  };

  const rows = await search();
  await s.step('documents dated on the Start Date and on the End Date are listed; the day before and the day after are not', async () => {
    const has = name => rows.includes(`DOC:${docs[name]}`);
    h.assert(has('docStart') && has('docMid') && has('docEnd'),
      `Documents dated on the Start Date (${has('docStart')}), between the boundaries (${has('docMid')}), and on the End Date (${has('docEnd')}) must all be listed`);
    h.assert(!has('docBefore') && !has('docAfter'), 'A document outside the date window was listed');
  });

  await s.step('labs observed on the Start Date and at any time on the End Date are listed; the day before and the day after are not', async () => {
    const listed = Object.entries(labs).filter(([, id]) => rows.some(row => row.endsWith(`:${id}`) && row.startsWith('HL7'))).map(([name]) => name).sort();
    h.assert(listed.length > 0, 'No lab fixture was listed by an unfiltered-by-time search; the fixture is not reachable, so the date boundary cannot be judged');
    const want = ['labEndLast', 'labEndMid', 'labStart'];
    h.assert(JSON.stringify(listed) === JSON.stringify(want),
      `Labs listed for ${START}..${END}: [${listed.join(', ')}], expected [${want.join(', ')}]. A lab observed later in the day on the End Date is dropped: `
      + 'LabDataController.convertDate turns the End Date into 00:00:00 and Hl7TextInfoDaoImpl compares the text observation time (info.obr_date <= :endDate)');
  });
}

if (require.main === module) runWorkflow('boundary-date-inbox', workflow, { openMaster: false });
module.exports = { workflow };
