/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Owned referrals and the clinician's paths through them, for the consultation checks of issues
 * #5272-#5278 (follow-up date, appointment instructions, multisite site, letterhead title, the status
 * matrix, the referral journey and the fax account).
 *
 * Every row is the run's own: a FAKE service and consultant named with the run marker, referrals for
 * owned FAKE patients only (the workflow's patient and any extra patient made here), and their archive,
 * extension, attachment and stamped-signature rows. cleanup() removes them by key and asserts they are
 * gone. The service and consultant are in the clinic-wide pickers while the run lasts, so a check that
 * uses them must not run concurrently with other checks (live wrapper: EXCLUSIVE=1).
 *
 * The browser paths are the clinician's: Schedule ▸ Consultations ▸ a row (ViewRequest popup), and
 * E-Chart ▸ Consultations "+" for a new request. Saving waits for the confirmation page and asserts it.
 */
const h = require('./playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./playwright-ui');
const { CONSULTATION_ROWS, createOwnedPatient, newOwnedMarker, removeOwnedPatient } = require('./owned-patient');

const q = h.sqlString;
const POSITIVE = /^[1-9]\d*$/;
const TIMEOUT = 30000;

/** The tables that hang off a request, deleted before the request itself. */
const REQUEST_CHILDREN = ['consultdocs', 'consultationRequestExtArchive', 'consultationRequestsArchive', 'consultationRequestExt'];

function key(value, what) {
  h.assert(POSITIVE.test(String(value)), `The owned ${what} key is invalid`);
  return String(value);
}

/**
 * The run's own service and consultant, and the referrals made for its patients.
 *
 * @param s the runWorkflow session
 * @param {object} [options]
 * @param {string} [options.consultantFax] the consultant's fax number (the fax cover page's recipient)
 */
function ownedReferrals(s, { consultantFax = '555-0161' } = {}) {
  const { sql, marker, patient, provider } = s;
  const serviceName = `${marker}-Svc`;
  const lastName = `${marker}-Spec`;
  const patients = [key(patient, 'patient')];
  let serviceId;
  let specId;

  const requestsOf = demographic => sql.rows(`SELECT requestId FROM consultationRequests WHERE demographicNo=${demographic}`)
    .map(([id]) => id).filter(id => POSITIVE.test(id));
  s.cleanup(() => {
    for (const demographic of patients) {
      for (const id of requestsOf(demographic)) {
        sql.execute(`${REQUEST_CHILDREN.map(table => `DELETE FROM ${table} WHERE requestId=${id}`).join(';\n')};
          DELETE FROM consultationRequests WHERE requestId=${id} AND demographicNo=${demographic}`);
      }
      sql.execute(`DELETE FROM consultationRequestsArchive WHERE demographicNo=${demographic};
        DELETE FROM DigitalSignature WHERE demographicId=${demographic} AND ModuleType='CONSULTATION'`);
    }
    if (serviceId) sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${serviceId}`);
    sql.execute(`DELETE FROM consultationServices WHERE serviceDesc=${q(serviceName)};
      DELETE FROM professionalSpecialists WHERE lName=${q(lastName)}`);
    const left = patients.map(demographic => `(SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${demographic})
      + (SELECT COUNT(*) FROM consultationRequestsArchive WHERE demographicNo=${demographic})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${demographic} AND ModuleType='CONSULTATION')`).join(' + ');
    h.assert(sql.value(`SELECT ${left} + (SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${q(serviceName)})
      + (SELECT COUNT(*) FROM professionalSpecialists WHERE lName=${q(lastName)})`) === '0',
    'An owned referral, its history or signature, or the owned service or consultant remains');
  });
  serviceId = h.insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${q(serviceName)},'1')`, 'service');
  specId = h.insertId(sql, `INSERT INTO professionalSpecialists
    (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
    VALUES('Ann',${q(lastName)},'555-0160',${q(consultantFax)},'2 Synthetic Way',NOW(),0,0,0,0)`, 'consultant');
  sql.execute(`INSERT INTO serviceSpecialists(serviceId,specId) VALUES(${serviceId},${specId})`);

  return {
    serviceName,
    lastName,
    get serviceId() { return serviceId; },
    get specId() { return specId; },
    consultantFax,

    /** A second FAKE patient owned by this run; its referrals are removed with it. */
    extraPatient(firstName = 'Second') {
      const extraMarker = newOwnedMarker();
      const extra = createOwnedPatient(sql, { marker: extraMarker, provider, firstName });
      s.cleanup(() => removeOwnedPatient(sql, extra, extraMarker, CONSULTATION_ROWS));
      patients.push(extra);
      return { demographicNo: extra, marker: extraMarker };
    },

    /**
     * A saved referral for an owned patient, seeded by SQL the way the edit-status check seeds one.
     *
     * @param {object} values the reason (required) and any consultationRequests column to override
     */
    seed({ reason, demographicNo = patient, ...columns }) {
      h.assert(reason && patients.includes(String(demographicNo)), 'A seeded referral needs a reason and an owned patient');
      const row = {
        referalDate: 'CURDATE()', serviceId, specId, providerNo: q(provider), demographicNo, status: q('1'), urgency: q('2'),
        reason: q(reason), clinicalInfo: q('Synthetic clinical information'), currentMeds: q(''), allergies: q(''),
        concurrentProblems: q(''), statusText: q(''), sendTo: q(''), patientWillBook: 0, lastUpdateDate: 'NOW()',
      };
      for (const [column, value] of Object.entries(columns)) {
        h.assert(/^[A-Za-z_]+$/.test(column), `Not a column name: ${column}`);
        row[column] = value === null ? 'NULL' : q(value);
      }
      return h.insertId(sql, `INSERT INTO consultationRequests (${Object.keys(row).join(',')})
        VALUES (${Object.values(row).join(',')})`, 'referral');
    },

    /** One column of a referral as text ('' for NULL). */
    field(requestId, column) {
      h.assert(/^[A-Za-z_]+$/.test(column), `Not a column name: ${column}`);
      return sql.value(`SELECT IFNULL(${column},'') FROM consultationRequests WHERE requestId=${key(requestId, 'referral')}`);
    },

    /** Whether a column is SQL NULL, as opposed to an empty string. */
    isNull(requestId, column) {
      h.assert(/^[A-Za-z_]+$/.test(column), `Not a column name: ${column}`);
      return sql.value(`SELECT ${column} IS NULL FROM consultationRequests WHERE requestId=${key(requestId, 'referral')}`) === '1';
    },

    /** The archived versions of a referral, oldest first, as rows of the named columns. */
    archived(requestId, columns = ['status']) {
      for (const column of columns) h.assert(/^[A-Za-z_]+$/.test(column), `Not a column name: ${column}`);
      // The row id leads each row: a row of one empty value would otherwise come back as an empty line.
      return sql.rows(`SELECT Id, ${columns.map(column => `IFNULL(${column},'')`).join(',')} FROM consultationRequestsArchive
        WHERE requestId=${key(requestId, 'referral')} ORDER BY Id`).map(row => row.slice(1));
    },

    /** The extension rows of a referral as { key: value }. */
    ext(requestId) {
      return Object.fromEntries(sql.rows(`SELECT name, IFNULL(value,'') FROM consultationRequestExt
        WHERE requestId=${key(requestId, 'referral')} ORDER BY id`));
    },

    /**
     * A FAKE document in an owned patient's chart (database rows only: no file is needed to list or attach
     * it). Removed, with every attachment row naming it, by cleanup.
     */
    document(description, demographicNo = patient) {
      h.assert(patients.includes(String(demographicNo)), 'A document fixture needs an owned patient');
      const id = h.insertId(sql, `INSERT INTO document (doctype,docdesc,docfilename,doccreator,responsible,status,contenttype,
        public1,number_of_pages,restrictToProgram,observationdate,updatedatetime,contentdatetime)
        VALUES ('consult',${q(description)},${q(`${description}.pdf`)},${q(provider)},${q(provider)},'A','application/pdf',
        0,1,0,CURDATE(),NOW(),NOW())`, 'document');
      sql.execute(`INSERT INTO ctl_document (module,module_id,document_no,status) VALUES ('demographic',${demographicNo},${id},'A')`);
      s.cleanup(() => {
        sql.execute(`DELETE FROM consultdocs WHERE document_no=${id} AND doctype='D';
          DELETE FROM ctl_document WHERE document_no=${id} AND module='demographic' AND module_id=${demographicNo};
          DELETE FROM document WHERE document_no=${id} AND docdesc=${q(description)}`);
        h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM document WHERE document_no=${id})
          + (SELECT COUNT(*) FROM ctl_document WHERE document_no=${id} AND module='demographic')
          + (SELECT COUNT(*) FROM consultdocs WHERE document_no=${id} AND doctype='D')`) === '0', 'The owned document remains');
      });
      return id;
    },

    /** Attaches an owned document to a referral the way a saved attachment is stored. */
    attach(requestId, documentNo) {
      sql.execute(`INSERT INTO consultdocs (requestId,document_no,doctype,deleted,attach_date,provider_no)
        VALUES (${key(requestId, 'referral')},${key(documentNo, 'document')},'D',NULL,CURDATE(),${q(provider)})`);
    },

    /** The referral's live attachments as "D:<document_no>" / "L:<lab_no>" strings, sorted. */
    attachments(requestId) {
      return sql.rows(`SELECT CONCAT(doctype,':',document_no) FROM consultdocs WHERE requestId=${key(requestId, 'referral')}
        AND (deleted IS NULL OR deleted<>'Y') ORDER BY 1`).map(([entry]) => entry);
    },

    /** Every referral of an owned patient, oldest first. */
    requestsFor(demographicNo = patient) {
      h.assert(patients.includes(String(demographicNo)), 'Not an owned patient');
      return requestsOf(demographicNo);
    },
  };
}

/** Schedule ▸ Consultations, the clinic's referral list (a popup or the same window). */
async function openConsultationsList(s) {
  const { page } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
    { context: s.context, recorder: s.recorder, label: 'consultations', timeout: TIMEOUT });
  await h.assertNotErrorPage(page, 'the Consultations list');
  return page;
}

/** The list's row for a referral; its click opens ViewRequest. */
function listRow(list, requestId) {
  return list.locator(`tr[onclick*="requestId=${key(requestId, 'referral')}'"]`);
}

/** Re-runs the list's own filter form, so it shows what is stored now. */
async function refreshList(list) {
  await clickAndAwaitReload(list, list.locator('form[action$="/encounter/ViewConsultation"] input[type="submit"]'),
    { label: 'the Consultations list filter' });
}

/** Opens a referral from its list row and checks it is the expected one. */
async function openFromList(s, list, requestId, reason, label = 'consultation-form') {
  const row = listRow(list, requestId);
  await row.waitFor({ state: 'visible', timeout: TIMEOUT });
  const form = await s.popup(list, row, label);
  await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: TIMEOUT });
  h.assert(await form.locator('[name="reasonForConsultation"]').inputValue() === reason,
    'The Consultations row opened a referral other than the owned one');
  return form;
}

/**
 * E-Chart ▸ the Consultations module's entry for a referral of the chart's patient. The chart is reopened first: its
 * module lists the referrals it loaded with. The chart opens referrals in a window of the patient's own
 * ("Consultation<demographic_no>"), while Schedule ▸ Consultations opens every referral in one shared window, so a
 * referral from the chart and another from the list are two windows at once.
 */
async function openFromChart(s, requestId, reason, label = 'consultation-from-chart') {
  const stale = await s.chart();
  await stale.close();
  const chart = await s.chart();
  const entry = chart.locator(`a[onclick*="requestId=${key(requestId, 'referral')}'"]`).first();
  await entry.waitFor({ state: 'attached', timeout: TIMEOUT });
  const form = await s.popup(chart, entry, label);
  await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: TIMEOUT });
  h.assert(await form.locator('[name="reasonForConsultation"]').inputValue() === reason, 'The chart opened another referral');
  return form;
}

/** E-Chart ▸ Consultations "+": a new request for the chart's patient. */
async function newFromChart(s, label = 'consultation-new') {
  const chart = await s.chart();
  const form = await s.popup(chart, chart.locator('a[onclick*="ViewConsultationFormRequest?de="]').first(), label);
  await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: TIMEOUT });
  h.assert(await form.locator('#demographicNo').inputValue() === String(s.patient),
    'The new consultation form is not for the owned patient');
  return form;
}

/** Types into a service or consultant picker and chooses the entry that carries `text`. */
async function pick(form, input, text) {
  await form.locator(input).fill(text);
  const item = form.locator('ul.ui-autocomplete:visible li.ui-menu-item').filter({ hasText: text }).first();
  await item.waitFor({ state: 'visible', timeout: 15000 });
  await item.click();
  await form.waitForTimeout(250);
}

/** Picks the owned service and consultant through the pickers, and asserts the posted ids. */
async function pickOwnedServiceAndConsultant(form, referrals) {
  await pick(form, '#serviceInput', referrals.serviceName);
  h.assert(await form.locator('#service').inputValue() === referrals.serviceId, 'The service picker did not choose the owned service');
  await pick(form, '#specialistInput', referrals.lastName);
  h.assert(await form.locator('#specialist').inputValue() === referrals.specId, 'The consultant picker did not choose the owned consultant');
}

/**
 * Presses a save button (Update, Submit) and asserts the confirmation the action answers with.
 *
 * @param form the consultation window
 * @param {string} button the button's name attribute (update, submitSaveOnly)
 * @param {RegExp} confirmed what the confirmation page must say
 */
async function save(form, button, confirmed) {
  await clickAndAwaitReload(form, form.locator(`input[name="${button}"]`), { label: `the ${button} button` });
  const text = await form.locator('body').innerText();
  h.assert(confirmed.test(text), `Pressing ${button} did not confirm the save: ${JSON.stringify(text.slice(0, 200))}`);
}

/** Expands a collapsed section of the form (Letterhead starts collapsed). */
async function expand(form, id) {
  const section = form.locator(`#${id}`);
  if (!(await section.isVisible())) {
    await form.locator(`a[href="#${id}"]`).click();
    await section.waitFor({ state: 'visible', timeout: TIMEOUT });
  }
}

const UPDATED = /has been\s+Updated/i;
const CREATED = /has been\s+Created/i;

module.exports = {
  CREATED, UPDATED, expand, listRow, newFromChart, openConsultationsList, openFromChart, openFromList, ownedReferrals, pick,
  pickOwnedServiceAndConsultant, refreshList, save,
};
