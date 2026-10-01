#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Patient search results: does every sortable column header put the owned rows in order? (wave 7, search-sort)
 * User path: Schedule > Search > name search for the run tag > Search > each column header of the results table
 * (Demographic no., Name, Chart no., Sex, DOB, Doctor, Roster status, Patient status, Phone; demographicsearchresults.jsp).
 * Asserts, on five owned patients that differ in every sortable column (one surname starts in lower case, as "van Dyk" or
 * "de Souza" do in real data; one patient has no MRP, no chart number and no phone): the default order is alphabetical;
 * each header returns all five owned rows, once each, ordered by the column it names (numbers as numbers, text without
 * regard to case); and sorting by Doctor with a patient who has no MRP still lists the patients. Defects are collected
 * and asserted together in the last step, so every header is exercised before the check fails.
 * Fixtures: five synthetic patients inserted by SQL whose surname starts with the run tag; cleanup removes them and
 * asserts none remain. Nothing is changed by the searches.
 * Implements the wave-7 "search-sort" pattern (sort order by every sortable header, nulls, case, numeric vs text).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const k = require('./lib/search-sort-helpers');

async function workflow(s) {
  const tag = k.nameTag(s.marker);
  k.registerPatientCleanup(s, tag);
  const ids = {
    zulu: k.insertPatient(s, { last: `${tag}-Zulu`, first: 'Ann', sex: 'F', chart: 'C30', dob: '1990-03-05', roster: 'RO', status: 'AC', phone: '9055550003' }),
    vanDyk: k.insertPatient(s, { last: `${tag}-van Dyk`, first: 'Bob', sex: 'M', chart: 'C100', dob: '1980-12-25', roster: 'NR', status: 'AC', phone: '9055550001' }),
    bravo: k.insertPatient(s, { last: `${tag}-Bravo`, first: 'Cy', sex: 'F', chart: 'C2', dob: '1985-01-15', roster: 'RO', status: 'AC', phone: '9055550002' }),
    aaron: k.insertPatient(s, { last: `${tag}-Aaron`, first: 'Di', sex: 'M', chart: 'C7', dob: '1975-06-30', roster: 'NR', status: 'AC', phone: '9055550004' }),
    noMrp: k.insertPatient(s, { last: `${tag}-Mulligan`, first: 'Ed', sex: 'F', chart: null, dob: '1999-09-09', roster: 'NR', status: 'AC', phone: null, provider: null }),
  };
  const all = Object.values(ids);
  const defects = [];

  const popup = await k.openSearchPopup(s);
  await s.step('searching by the run tag lists exactly the five owned patients', async () => {
    await k.submitSearch(popup, 'search_name', tag);
    const rows = await k.readResultRows(popup);
    h.assert(rows.length === 5 && k.ownedRows(rows, all).length === 5,
      `The tag search listed ${rows.length} rows, owned ${k.ownedRows(rows, all).length} of 5 (fixture or search problem)`);
  });

  // Every header is clicked from the same result set; each check reads only the owned rows.
  const read = async () => k.ownedRows(await k.readResultRows(popup), all);
  const expectOnce = (rows, label) => {
    if (rows.length !== 5 || new Set(rows.map(r => r.id)).size !== 5) {
      defects.push(`${label}: lists ${rows.length} of the 5 owned patients`);
      return false;
    }
    return true;
  };
  const compareText = (a, b) => k.collator.compare(a, b);
  const compareNum = (a, b) => Number(a) - Number(b);

  await s.step('the default order is alphabetical by surname', async () => {
    const rows = await read();
    if (expectOnce(rows, 'default order') && !k.nonDecreasing(rows.map(r => r.name), compareText)) {
      defects.push(`default order is not alphabetical: ${rows.map(r => r.name.split(',')[0].replace(/^.*-/, '')).join(' | ')}`);
    }
  });

  const columns = [
    ['demographic_no', 'id', compareNum, 'Demographic no.'],
    ['last_name', 'name', compareText, 'Name'],
    ['chart_no', 'chart', compareText, 'Chart no.'],
    ['sex', 'sex', compareText, 'Sex'],
    ['dob', 'dob', compareText, 'DOB'],
    ['roster_status', 'roster', compareText, 'Roster status'],
    ['patient_status', 'status', compareText, 'Patient status'],
    ['phone', 'phone', compareText, 'Phone'],
  ];
  for (const [orderby, field, cmp, label] of columns) {
    await s.step(`the ${label} header lists the owned patients ordered by ${label}`, async () => {
      await k.clickSort(popup, orderby);
      const rows = await read();
      if (!expectOnce(rows, `${label} header`)) return;
      const values = rows.map(r => r[field]);
      if (!k.nonDecreasing(values, cmp)) {
        defects.push(`${label} header: rows are not ordered by ${label} (${values.map(v => v || '(blank)').join(' | ')})`);
      }
    });
  }

  await s.step('the Doctor header still lists every patient when one has no most-responsible provider', async () => {
    const { failed } = await k.collectHttpFailures(s, async () => { await k.clickSort(popup, 'provider_no'); });
    if (failed.length) {
      defects.push(`Doctor header: the results page failed (${failed.join(', ')}) when an owned patient has no provider`);
      return;
    }
    expectOnce(await read(), 'Doctor header');
  });

  await s.step('every header ordered the owned patients correctly', async () => {
    h.assert(defects.length === 0, `Patient search sort defects: ${defects.join('; ')} `
      + '(demographicsearchresults.jsp:375-394 re-sorts each page with the case-sensitive, null-intolerant comparators of Demographic.java:1714-1777)');
  });
}

if (require.main === module) runWorkflow('search-sort-patient-order', workflow, { openPatient: false });
module.exports = { workflow };
