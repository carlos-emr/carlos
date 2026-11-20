#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Master Record ▸ Print / Labels: every field a printed patient label carries, against the patient row.
 * User path: Schedule ▸ Search ▸ Master Record ▸ Print / Labels ▸ PDF Label / PDF Address Label / PDF Chart Label /
 * PDF Envelope / Client Lab Label (each answers a PDF built by a Jasper template from the demographic row).
 * demographic-label-content proves the surname and address reach the PDF; this reads the rest of the label, because a
 * label is stuck on a physical chart and a specimen, so a missing digit or the word "null" is a mislabelled patient.
 *
 * Asserts (pdftotext of each label PDF): the surname, first name, HIN with version, sex, birth date, phone, chart
 * number, street address, city and postal code appear on the labels that are meant to carry them, with the accent in
 * the name intact; no label prints the literal word "null"; a patient with a middle name that has a letter outside
 * Latin-1 gets it printed; the chart label's age is a whole number; and (last) a patient with NO middle name
 * (NULL, as imports and the REST API create them) does not get "null" printed after the first name.
 * Fixtures: two FAKE- patients with realistic (short) names and fields, written by SQL; the runWorkflow patient is
 * only the owner of the session. Cleanup deletes the two patients and asserts it. Needs pdftotext.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openPrintMenu } = require('./demographic-labels-playwright-checks');
const { resolvePdfUrl } = require('./demographic-label-content-playwright-checks');
const x = require('./lib/export-content-helpers');

const q = h.sqlString;
const LABELS = ['PDF Label', 'PDF Address Label', 'PDF Chart Label', 'PDF Envelope', 'Client Lab Label'];

async function workflow(s) {
  const { sql, provider, marker, context, recorder } = s;
  const tag = marker.slice(-6);
  const owned = [];
  s.cleanup(() => {
    for (const id of owned) {
      sql.execute(`DELETE FROM casemgmt_note_lock WHERE demographic_no=${id}; DELETE FROM casemgmt_tmpsave WHERE demographic_no=${id};
        DELETE FROM demographicExt WHERE demographic_no=${id}; DELETE FROM demographicArchive WHERE demographic_no=${id};
        DELETE FROM demographic WHERE demographic_no=${id} AND last_name LIKE 'FAKE-%${tag}'`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name LIKE 'FAKE-%${tag}' AND demographic_no<>${s.patient}`) === '0',
      'Owned label patients were not removed');
  });
  const make = fields => {
    const id = sql.value(`INSERT INTO demographic (last_name,first_name,middleNames,year_of_birth,month_of_birth,date_of_birth,sex,
        patient_status,provider_no,hc_type,ver,hin,province,address,city,postal,phone,chart_no,roster_status,lastUpdateDate)
      VALUES (${q(fields.last)},${q(fields.first)},${fields.middle === null ? 'NULL' : q(fields.middle)},'1962','07','09','F','AC',${q(provider)},
        'ON','AB',${q(fields.hin)},'ON',${q('42 Rue de l’Église')},${q('Montréal')},'H2X1Y4','514-555-0142',${q(fields.chart)},'NR',NOW());
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'The label patient was not created');
    owned.push(id);
    return { ...fields, id };
  };
  const full = make({ last: `FAKE-Dubois${tag}`, first: 'Zoë-Marie', middle: 'Łukasz', hin: '9876543217', chart: 'CH-0042' });
  const bare = make({ last: `FAKE-Tremblay${tag}`, first: 'Anne', middle: null, hin: '9876543225', chart: 'CH-0043' });

  async function labelTexts(patient, labels = LABELS) {
    const { masterPage, searchPage } = await openMasterRecord(context, s.schedule, recorder, { searchTerm: patient.last, preferredDemographicNo: patient.id, timeout: 20000 });
    h.assert(new URL(masterPage.url()).searchParams.get('demographic_no') === patient.id, 'The Master Record is not the owned label patient');
    const texts = {};
    for (const label of labels) {
      const menu = await openPrintMenu(masterPage, 20000);
      const failuresBefore = recorder.requestFailures.length;
      let pdfVerified = false;
      const produced = await ui.clickDownloadsOrOpens(masterPage, menu.getByRole('link', { name: label, exact: true }),
        { context, recorder, label: 'owned-label', timeout: 30000 });
      try {
        let src = null;
        if (produced.page) { const frame = produced.page.locator('iframe#pdf'); src = await frame.count() ? await frame.first().getAttribute('src') : null; }
        const response = await context.request.get(resolvePdfUrl(produced.url, src, s.config.baseUrl), { maxRedirects: 0 });
        h.assert(response.status() === 200, `${label} answered HTTP ${response.status()}`);
        const file = require('node:path').join(scratch, `${patient.id}-${label.replace(/\W/g, '_')}.pdf`);
        require('node:fs').writeFileSync(file, await response.body());
        texts[label] = x.pdfText(file);
        pdfVerified = true;
      } finally {
        if (produced.page) await produced.page.close();
        // Closing Chromium's built-in PDF viewer can abort its extension UI request.
        // The application PDF was independently fetched (HTTP 200) and parsed above.
        if (pdfVerified) {
          for (let i = recorder.requestFailures.length - 1; i >= failuresBefore; i--) {
            const failure = recorder.requestFailures[i];
            if (failure.label === 'owned-label' && failure.resourceType === 'other'
                && failure.errorText === 'net::ERR_ABORTED'
                && failure.url.startsWith('chrome-extension://mhjfbmdgcfjbbpaeojofohoefgiehjai/')) {
              recorder.requestFailures.splice(i, 1);
            }
          }
        }
      }
    }
    await masterPage.close();
    // The search window is reused by name: close it so the next search opens a fresh one.
    if (searchPage && !searchPage.isClosed()) await searchPage.close();
    return texts;
  }
  const scratch = x.scratchDir();
  s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));

  let fullTexts;
  await s.step('the five label PDFs of the full patient are produced', async () => {
    fullTexts = await labelTexts(full);
    for (const label of LABELS) h.assert(fullTexts[label].trim().length > 0, `${label} is empty`);
  });

  const has = (label, value) => x.squash(fullTexts[label]).includes(x.squash(value));
  await s.step('each label carries the name, address, city and postal code of the patient', async () => {
    const wanted = {
      'PDF Label': [full.last, 'Zoë-Marie', '42 Rue de l’Église', 'Montréal', 'H2X1Y4'],
      'PDF Address Label': [full.last, 'Zoë-Marie', '42 Rue de l’Église', 'Montréal', 'H2X1Y4'],
      'PDF Chart Label': [full.last, 'Zoë-Marie', '42 Rue de l’Église', 'Montréal', 'H2X1Y4'],
      'PDF Envelope': [full.last, 'Zoë-Marie', '42 Rue de l’Église', 'Montréal', 'H2X1Y4'],
      'Client Lab Label': [full.last, 'Zoë-Marie'],
    };
    for (const [label, tokens] of Object.entries(wanted)) {
      for (const token of tokens) h.assert(has(label, token), `${label} does not carry "${token.replace(full.last, '<surname>')}"`);
    }
  });

  await s.step('the identifying fields (HIN with version, sex, birth date, phone, chart number) are on the labels that print them', async () => {
    const dob = label => /1962/.test(fullTexts[label]) && /07/.test(fullTexts[label]) && /09/.test(fullTexts[label]);
    h.assert(has('Client Lab Label', 'Gender:F'), 'Client Lab Label does not carry the gender');
    for (const label of ['PDF Label', 'PDF Chart Label', 'Client Lab Label']) {
      h.assert(has(label, '9876543217'), `${label} does not carry the health number`);
      h.assert(dob(label), `${label} does not carry the birth date`);
    }
    for (const label of ['PDF Label', 'PDF Chart Label']) {
      h.assert(has(label, 'SEX:F'), `${label} does not carry the sex`);
      h.assert(has(label, 'AB'), `${label} does not carry the version code`);
      h.assert(x.squash(fullTexts[label]).replace(/\D/g, '').includes('5145550142'), `${label} does not carry the phone number`);
    }
    h.assert(has('PDF Chart Label', 'CH-0042'), 'The chart label does not carry the whole chart number CH-0042 (it is clipped at the label edge)');
    h.assert(has('PDF Label', 'CH-0042'), 'The PDF label does not carry the whole chart number CH-0042 (it is clipped at the label edge)');
  });

  await s.step('no label of the full patient prints the word null', async () => {
    for (const label of LABELS) h.assert(!/\bnull\b/i.test(fullTexts[label]), `${label} prints the literal word "null"`);
  });

  await s.step('the labels print the whole middle name, a whole-number age, and no "null" for a patient without a middle name', async () => {
    const problems = [];
    const lost = ['PDF Label', 'PDF Chart Label'].filter(label => !has(label, '\u0141ukasz'));
    if (lost.length) problems.push(`${lost.join(' and ')} drop(s) the middle name \u0141ukasz (the label font has no glyph for \u0141)`);
    const age = /AGE:\s*(\S+)/.exec(fullTexts['PDF Chart Label']);
    if (!age) problems.push('the chart label prints no AGE: field');
    else if (!/^\d+$/.test(age[1])) problems.push(`the chart label prints the age as "${age[1]}" instead of a whole number of years`);
    const bareTexts = await labelTexts(bare);
    const dirty = LABELS.filter(label => /\bnull\b/i.test(bareTexts[label]));
    if (dirty.length) problems.push(`${dirty.join(', ')} print(s) the literal word "null" after the first name of a patient whose middle name is NULL (label.xml concatenates $F{middle_name})`);
    h.assert(!problems.length, `The patient labels are wrong: ${problems.join('; ')}`);
  });
  await s.step('Chart Label leaves age blank when the birth date is incomplete', async () => {
    const partial = make({ last: `FAKE-Partial${tag}`, first: 'Anne', middle: null, hin: '9876543233', chart: 'CH-0044' });
    sql.execute(`UPDATE demographic SET month_of_birth=NULL WHERE demographic_no=${partial.id}`);
    const text = (await labelTexts(partial, ['PDF Chart Label']))['PDF Chart Label'];
    // Match horizontal whitespace only: the next PDF line can begin with a street number.
    h.assert(text.includes('AGE:') && !/AGE:[ \t]*(?:null|\d)/i.test(text),
      `An incomplete birth date produced a nonempty or null age: ${JSON.stringify(text.match(/AGE:[^\r\n]*/)?.[0])}`);
    h.assert(!/\bnull\b/i.test(text), 'The partial birth date printed a literal null on Chart Label');
  });

  await s.step('Chart Label calculates whole years on either side of a birthday', async () => {
    for (const daysUntilBirthday of [0, 1]) {
      const birthday = `DATE_SUB(DATE_ADD(CURDATE(), INTERVAL ${daysUntilBirthday} DAY), INTERVAL 40 YEAR)`;
      sql.execute(`UPDATE demographic SET year_of_birth=DATE_FORMAT(${birthday},'%Y'),
        month_of_birth=DATE_FORMAT(${birthday},'%m'),date_of_birth=DATE_FORMAT(${birthday},'%d') WHERE demographic_no=${bare.id}`);
      const text = (await labelTexts(bare, ['PDF Chart Label']))['PDF Chart Label'];
      const age = /AGE:\s*(\d+)/.exec(text);
      h.assert(age && Number(age[1]) === 40 - daysUntilBirthday, `Chart Label has the wrong age for a birthday ${daysUntilBirthday} day(s) away`);
    }
  });

}

if (require.main === module) {
  runWorkflow('export-content-patient-labels', workflow, { openPatient: true, openMaster: false, preflight: () => x.requirePoppler('pdftotext') });
}
module.exports = { workflow };
