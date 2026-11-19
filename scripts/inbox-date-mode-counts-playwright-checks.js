#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Inbox Hub category counts under both inbox date modes (issue #5772).
 * User path: Schedule > Inbox (#inboxLink) > list mode > "Any provider", status "New", "Specific
 *   patients" with the run's FAKE- surname, Start Date 2026-04-01 / End Date 2026-04-30 > Search; once
 *   with the system preference inboxDateSearchType = serviceObservation and once = receivedCreated.
 * Why: the counts (InboxCategoryDao through CategoryData) used to re-read that preference for every
 *   category count. They now read it once per inbox load and pass the mode to every count, so this
 *   check pins that the counts follow the configured mode and agree with the rows the list shows.
 * Asserts, per mode: exactly the fixture rows dated inside the window by that mode's date are listed
 *   (documents by observation date or content date/time; labs by OBR observation time or message
 *   receipt time); the Documents, Labs and total category counts and the patient's sidebar count equal
 *   the rows listed. Every fixture is inside the window by exactly one of its two dates, so the two modes
 *   list different sets (observation: 3 documents, 1 lab; received: 1 document, 2 labs).
 * Fixtures: four documents (document + ctl_document + providerLabRouting) and three labs (hl7TextMessage +
 *   hl7TextInfo + providerLabRouting + patientLabRouting) for the run's FAKE- patient, whose empty HIN is
 *   set so the patient search judges dates only; cleanup removes every row by id and marker and asserts
 *   none remain. The global SystemPreferences row inboxDateSearchType is snapshotted and restored exactly; it
 *   changes every user's Inbox date mode while the check runs, so the check must not run concurrently with
 *   other checks (live wrapper: EXCLUSIVE=1).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { clickOpensPopupOrNavigates, pickDate } = require('./lib/playwright-ui');
const { settle, shownRows } = require('./inboxhub-filters-playwright-checks');

const TIMEOUT = 60000;
const START = '2026-04-01';
const END = '2026-04-30';
const PREFERENCE = "name='inboxDateSearchType'";
const SERVICE = 'DATEMODE';

// Each item is inside April by exactly one of its two dates. Document receipt times stay off the window's
// edges: the Inbox Hub document list binds its End Date as that day's midnight while the counts include the
// whole day, a list-side boundary outside what this check judges. Lab bounds are the same on both sides.
const DOCUMENTS = {
  observed1: { observed: '2026-04-10', created: '2026-05-20 12:00:00' },
  observed2: { observed: '2026-04-11', created: '2026-05-21 12:00:00' },
  observed3: { observed: '2026-04-30', created: '2026-03-31 12:00:00' },
  received: { observed: '2026-03-01', created: '2026-04-12 12:00:00' },
};
const LABS = {
  observed: { observed: '2026-04-10 10:00:00', created: '2026-05-20 10:00:00' },
  received1: { observed: '2026-03-01 10:00:00', created: '2026-04-12 10:00:00' },
  received2: { observed: '2026-05-01 00:00:00', created: '2026-04-30 23:59:59' },
};
const EXPECTED = {
  serviceObservation: { documents: ['observed1', 'observed2', 'observed3'], labs: ['observed'] },
  receivedCreated: { documents: ['received'], labs: ['received1', 'received2'] },
};

/**
 * Snapshots the clinic-wide inboxDateSearchType rows and registers a cleanup that puts them back exactly:
 * every row the check added is deleted (restoring the original absence when there was no row) and every
 * original row gets its original bytes or NULL back. The cleanup reconciles by preference name rather than
 * by a captured id, because an INSERT can commit while the SQL client loses its reply and never returns the
 * new id; the run is exclusive (EXCLUSIVE=1), so any row the snapshot did not hold was added by this run.
 * It then asserts that the rows equal the snapshot, so a restore that silently failed fails the check.
 * @param {object} s workflow session with `sql` and `cleanup`
 * @returns {(value: string) => void} sets the preference for every inboxDateSearchType row
 */
function guardDatePreference(s) {
  const { sql } = s;
  // sql.rows() reads the text 'NULL' as JavaScript null, so a SQL NULL value is carried by a separate
  // `value IS NULL` flag rather than a 'NULL' sentinel in the hex column.
  const snapshot = () => sql.rows(`SELECT id, IFNULL(HEX(value),''), value IS NULL FROM SystemPreferences
    WHERE ${PREFERENCE} ORDER BY id`);
  const previous = snapshot();
  for (const [id, hex, isNull] of previous) {
    h.assert(/^\d+$/.test(id) && /^[0-9A-F]*$/i.test(hex || '') && (isNull === '0' || isNull === '1'),
      'Unexpected preference snapshot');
  }
  s.cleanup(() => {
    const kept = previous.map(([id]) => id);
    sql.execute(`DELETE FROM SystemPreferences WHERE ${PREFERENCE}${kept.length ? ` AND id NOT IN (${kept.join(',')})` : ''}`);
    for (const [id, hex, isNull] of previous) {
      sql.execute(`UPDATE SystemPreferences SET value=${isNull === '1' ? 'NULL' : `UNHEX('${hex || ''}')`} WHERE id=${id} AND ${PREFERENCE}`);
    }
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(previous),
      'inboxDateSearchType was not restored to its state before the check');
  });
  if (!previous.length) {
    const inserted = sql.value(`INSERT INTO SystemPreferences (name,value,updateDate)
      VALUES ('inboxDateSearchType','serviceObservation',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(inserted), 'Date preference fixture was not inserted');
  }
  return value => sql.execute(`UPDATE SystemPreferences SET value=${h.sqlString(value)} WHERE ${PREFERENCE}`);
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const surname = marker.slice(0, 30);
  const docs = {};
  const labs = {};

  const setPreference = guardDatePreference(s);

  s.cleanup(() => {
    // Captured ids plus every row carrying this run's marker: a timed-out mysql call can commit an INSERT
    // before sql.value returns its id, and such a row must still be removed with its links.
    const owned = (captured, query) => [...new Set([...captured, ...sql.rows(query).map(([id]) => id)])]
      .filter(id => /^[1-9]\d*$/.test(String(id)));
    const docIds = owned(Object.values(docs), `SELECT document_no FROM document WHERE docdesc=${q(marker)}`);
    const labIds = owned(Object.values(labs), `SELECT lab_id FROM hl7TextMessage WHERE serviceName=${q(SERVICE)} AND message=${q(marker)}`);
    if (docIds.length) {
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no IN (${docIds.join(',')});
        DELETE FROM ctl_document WHERE module='demographic' AND module_id=${patient} AND document_no IN (${docIds.join(',')});
        DELETE FROM document WHERE document_no IN (${docIds.join(',')}) AND docdesc=${q(marker)}`);
    }
    if (labIds.length) {
      sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${labIds.join(',')});
        DELETE FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${labIds.join(',')});
        DELETE FROM hl7TextInfo WHERE lab_no IN (${labIds.join(',')}) AND last_name=${q(surname)};
        DELETE FROM hl7TextMessage WHERE lab_id IN (${labIds.join(',')}) AND serviceName=${q(SERVICE)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM document WHERE docdesc=${q(marker)})
      + (SELECT COUNT(*) FROM hl7TextInfo WHERE last_name=${q(surname)})
      + (SELECT COUNT(*) FROM hl7TextMessage WHERE serviceName=${q(SERVICE)} AND message=${q(marker)})`) === '0',
    'Owned inbox fixtures were not removed');
  });

  // UI-created patients carry an empty health number; the runWorkflow fixture leaves it NULL. Give the owned
  // patient the empty HIN so the patient search judges dates only.
  sql.execute(`UPDATE demographic SET hin='' WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  for (const [name, dates] of Object.entries(DOCUMENTS)) {
    docs[name] = sql.value(`INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime)
      VALUES ('lab',${q(marker)},${q(`${marker}-${name}.pdf`)},${q(provider)},${q(provider)},'A','application/pdf',0,1,0,${q(dates.observed)},${q(dates.created)},${q(dates.created)});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(docs[name]), `Document fixture ${name} was not inserted`);
    sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${patient},${docs[name]},'A');
      INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${q(provider)},${docs[name]},'DOC','N')`);
  }
  for (const [name, dates] of Object.entries(LABS)) {
    const id = sql.value(`INSERT INTO hl7TextMessage (fileUploadCheck_id,message,type,serviceName,created)
      VALUES (0,${q(marker)},'PATHL7',${q(SERVICE)},${q(dates.created)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `Lab message fixture ${name} was not inserted`);
    labs[name] = id;
    sql.execute(`INSERT INTO hl7TextInfo (lab_no,sex,health_no,result_status,final_result_count,obr_date,priority,requesting_client,discipline,last_name,first_name,report_status,accessionNum,label)
      VALUES (${id},'F','',NULL,1,${q(dates.observed)},'R','','DATEMODE',${q(surname)},'Datemode','F',${q(`${marker}-${name}`)},${q(name)});
      INSERT INTO providerLabRouting (provider_no,lab_no,lab_type,status) VALUES (${q(provider)},${id},'HL7','N');
      INSERT INTO patientLabRouting (demographic_no,lab_no,lab_type,created) VALUES (${patient},${id},'HL7',NOW())`);
    h.assert(sql.value(`SELECT created FROM hl7TextMessage WHERE lab_id=${id}`) === dates.created,
      `Lab fixture ${name} did not preserve its receipt time`);
  }

  const { page: inbox } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#inboxLink'), {
    context: s.context, label: 'inbox-date-mode-counts', recorder: s.recorder, timeout: TIMEOUT,
  });
  await inbox.locator('#btnViewMode2').waitFor({ state: 'attached', timeout: TIMEOUT });
  if (await inbox.locator('#btnViewMode2').isChecked()) await inbox.locator('#btnViewModeLabel').click();
  await settle(inbox, TIMEOUT);

  // Each mode is a fresh inbox load, so each reads the preference as it now stands.
  async function search() {
    // The sidebar collapses after a search, hiding the filters.
    if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
    await inbox.locator('#anyProvider').check();
    await inbox.locator('#statusNew').check();
    await inbox.locator('#specificPatients').check();
    await inbox.locator('#inputLastName').fill(marker);
    await pickDate(inbox, '#startDate', START);
    await pickDate(inbox, '#endDate', END);
    await inbox.locator('#inboxhubFormSearchBtn').click();
    await settle(inbox, TIMEOUT);
    return shownRows(inbox);
  }

  const count = async selector => {
    const value = await inbox.locator(selector).inputValue();
    h.assert(/^\d+$/.test(value), `${selector} holds "${value}", not a count`);
    return Number(value);
  };

  for (const mode of ['serviceObservation', 'receivedCreated']) {
    await s.step(`${mode}: the listed rows and every category count follow the ${mode} dates`, async () => {
      setPreference(mode);
      const rows = await search();
      const want = [
        ...EXPECTED[mode].documents.map(name => `DOC:${docs[name]}`),
        ...EXPECTED[mode].labs.map(name => `HL7:${labs[name]}`),
      ].sort();
      h.assert(JSON.stringify(rows) === JSON.stringify(want),
        `${mode} listed [${rows.join(', ')}], expected [${want.join(', ')}] (${JSON.stringify(EXPECTED[mode])})`);
      const documents = rows.filter(row => row.startsWith('DOC:')).length;
      const labRows = rows.filter(row => row.startsWith('HL7:')).length;
      h.assert(await count('#totalDocsCount') === documents,
        `${mode}: Documents count ${await count('#totalDocsCount')} does not match the ${documents} document rows listed`);
      h.assert(await count('#totalLabsCount') === labRows,
        `${mode}: Labs count ${await count('#totalLabsCount')} does not match the ${labRows} lab rows listed`);
      h.assert(await count('#totalResultsCount') === rows.length,
        `${mode}: total count ${await count('#totalResultsCount')} does not match the ${rows.length} rows listed`);
      const sidebar = inbox.locator(`#patientNumDocs${patient}`);
      h.assert(await sidebar.count() === 1, `${mode}: the owned patient is not in the matched-patient sidebar`);
      const patientCount = (await sidebar.innerText()).trim();
      h.assert(patientCount === String(rows.length),
        `${mode}: the sidebar counts ${patientCount} items for the patient, but ${rows.length} rows are listed`);
    });
  }
}

if (require.main === module) runWorkflow('inbox-date-mode-counts', workflow, { openMaster: false });
module.exports = { workflow, guardDatePreference };
