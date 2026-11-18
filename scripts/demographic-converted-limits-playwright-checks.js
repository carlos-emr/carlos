#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const fields = [
  ['title', 'title', 10], ['officialLanguage', 'official_lang', 60],
  ['spokenLanguage', 'spoken_lang', 60], ['countryOfOrigin', 'country_of_origin', 4],
  ['newsletter', 'newsletter', 32], ['anonymous', 'anonymous', 32],
];
function preflightConfig() {
  if (process.env.CARLOS_DISPOSABLE_HOST !== 'true' || process.env.EXCLUSIVE !== '1') {
    throw new h.SkipCheck('Requires a disposable exclusive application host');
  }
}
async function workflow(s) {
  const endpoint = h.appUrl(s.config.baseUrl, '/ws/rs/demographics');
  const createdName = `${s.marker}-REST`;
  const owned = () => s.sql.rows('SELECT demographic_no FROM demographic WHERE last_name=?', [createdName]);
  h.assert(owned().length === 0, 'Owned REST patient name already exists');
  s.cleanup(() => {
    for (const [id] of owned()) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned REST patient identity is invalid');
      for (const [table, column] of [['admission', 'client_id'], ['demographicExt', 'demographic_no'],
        ['demographiccust', 'demographic_no'], ['demographiccustArchive', 'demographic_no'],
        ['demographicArchive', 'demographic_no']]) {
        s.sql.execute(`DELETE FROM ${table} WHERE ${column}=?`, [Number(id)]);
      }
      s.sql.execute('DELETE FROM demographic WHERE demographic_no=? AND last_name=?', [Number(id), createdName]);
    }
    for (const table of ['demographiccust', 'demographiccustArchive']) {
      s.sql.execute(`DELETE FROM ${table} WHERE demographic_no=?`, [Number(s.patient)]);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM ${table} WHERE demographic_no=?`, [Number(s.patient)]) === '0',
        'Owned custom-field residue remains');
    }
    h.assert(owned().length === 0, 'Owned REST patient remains');
  });
  const initial = await s.context.request.get(`${endpoint}/${s.patient}`, {maxRedirects: 0});
  h.assert(initial.status() === 200, 'Owned REST patient cannot be read');
  const original = await initial.json();
  await initial.dispose();
  h.assert(String(original.demographicNo) === s.patient, 'REST returned another patient');
  const update = {...original};
  for (const key of ['alert', 'nurse', 'resident', 'midwife', 'notes', 'waitingListID', 'waitingListNote']) delete update[key];
  const program = s.sql.value('SELECT id FROM program ORDER BY id LIMIT 1');
  h.assert(/^[1-9]\d*$/.test(program), 'No admission program is available');
  const create = {firstName: 'FAKE', lastName: createdName, sex: 'F', dobYear: '2000', dobMonth: '01', dobDay: '01',
    hcType: s.config.province || 'ON', providerNo: s.provider, admissionProgramId: Number(program),
    address: {address: '1 Test Street', city: 'Fixture', province: s.config.province || 'ON', postal: 'A1A 1A1'}, extras: []};
  const state = () => JSON.stringify([
    s.sql.rows(`SELECT ${fields.map(field => field[1]).join(',')} FROM demographic WHERE demographic_no=?`, [Number(s.patient)]),
    s.sql.rows('SELECT cust1,cust2,cust3,cust4,content FROM demographiccust WHERE demographic_no=?', [Number(s.patient)]),
  ]);
  const before = state();
  for (const [property, column, limit] of fields) {
    await s.step(`${property} overflow is refused before create or update side effects`, async () => {
      for (const method of ['post', 'put']) {
        const payload = {...(method === 'post' ? create : update), [property]: 'X'.repeat(limit + 1), alert: `${s.marker}-REFUSED`};
        const response = await s.context.request[method](endpoint, {data: payload, maxRedirects: 0});
        h.assert(response.status() === 400, `${property} ${method} returned HTTP ${response.status()} instead of 400`);
        const message = await response.text();
        await response.dispose();
        h.assert(!message.includes(payload[property]), 'Refusal disclosed the submitted field value');
        h.assert(owned().length === 0 && state() === before, 'Refused write changed patient or custom fields');
      }
      h.assert(Number(s.sql.value(`SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name='demographic' AND column_name=?`, [column])) === limit,
      'Reviewed limit differs from the installed column');
    });
  }
  const boundary = Object.fromEntries(fields.map(([property, , limit]) => [property, '😀'.repeat(limit)]));
  await s.step('boundary values survive a real REST update without truncation', async () => {
    const response = await s.context.request.put(endpoint, {data: {...update, ...boundary}, maxRedirects: 0});
    h.assert(response.status() === 200, `Boundary update returned HTTP ${response.status()}`);
    await response.dispose();
    const stored = s.sql.rows(`SELECT ${fields.map(field => field[1]).join(',')} FROM demographic WHERE demographic_no=?`, [Number(s.patient)]);
    h.assert(stored.length === 1 && fields.every(([property], i) => stored[0][i] === boundary[property]),
      'Boundary update changed a Unicode field');
  });
  await s.step('boundary values survive a real REST create without truncation', async () => {
    const response = await s.context.request.post(endpoint, {data: {...create, ...boundary}, maxRedirects: 0});
    h.assert(response.status() === 200, `Boundary create returned HTTP ${response.status()}`);
    const result = await response.json();
    await response.dispose();
    const rows = owned();
    h.assert(rows.length === 1 && rows[0][0] === String(result.demographicNo), 'REST create lost its new identity');
    const stored = s.sql.rows(`SELECT ${fields.map(field => field[1]).join(',')} FROM demographic WHERE demographic_no=?`, [Number(result.demographicNo)]);
    h.assert(stored.length === 1 && fields.every(([property], i) => stored[0][i] === boundary[property]),
      'Boundary create changed a Unicode field');
  });
}
if (require.main === module) runWorkflow('demographic-converted-limits', workflow, {openMaster: false, preflightConfig});
module.exports = {fields, preflightConfig, workflow};
