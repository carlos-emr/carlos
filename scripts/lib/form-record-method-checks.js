/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const h = require('./playwright-harness');
const { signIn } = require('./authz-read-probe');
const { vascularTrackerCatalogFixture } = require('./vascular-tracker-catalog-fixture');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;
const same = (left, right) => JSON.stringify(left) === JSON.stringify(right);

function formState(sql, patient) {
  h.assert(/^[1-9]\d*$/.test(String(patient)), 'The form snapshot needs an owned patient');
  return { ...readPatientState(sql, patient),
    locks: sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`),
    drugs: sql.rows(`SELECT * FROM drugs WHERE demographic_no=${patient} ORDER BY drugid`),
    prescriptions: sql.rows(`SELECT * FROM prescription WHERE demographic_no=${patient} ORDER BY script_no`),
    allergies: sql.rows(`SELECT * FROM allergies WHERE demographic_no=${patient} ORDER BY allergyid`),
    admissions: sql.rows(`SELECT * FROM admission WHERE client_id=${patient} ORDER BY am_id`),
    referrals: sql.rows(`SELECT * FROM program_queue WHERE client_id=${patient} ORDER BY queue_id`),
    documents: sql.rows(`SELECT * FROM ctl_document WHERE module='demographic' AND module_id=${patient} ORDER BY document_no`),
    groupLinks: sql.rows(`SELECT * FROM GroupNoteLink WHERE demographicNo=${patient} ORDER BY id`) };
}

/** Validate the actual native request, including its rendered identities and redirect. */
function assertNativeFormPost(result, { patient, previousId, baseUrl }) {
  const address = new URL(result.url);
  const sent = new URLSearchParams(result.data);
  const expected = new URL(h.appUrl(baseUrl, '/form/SubmitForm'));
  h.assert(result.method === 'POST' && address.origin === expected.origin && address.pathname === expected.pathname,
    'VT Update did not use its native POST action');
  for (const [name, value] of [['demographic_no', patient], ['value(formName)', 'VTForm'],
    ['value(formId)', previousId], ['submit', 'save']]) {
    h.assert(same(sent.getAll(name), [String(value)]), 'VT Update submitted a different or repeated form identity');
  }
  h.assert(sent.getAll('CSRF-TOKEN').length > 0 && sent.getAll('CSRF-TOKEN').every(value => value && value === sent.get('CSRF-TOKEN')),
    'VT Update omitted its rendered CSRF authority');
  h.assert(result.status === 302 && Object.hasOwn(result.headers, h.APPLICATION_HEADER),
    'The form-only native Update was not accepted by the application');
  const redirected = new URL(result.headers.location, baseUrl);
  const setup = new URL(h.appUrl(baseUrl, '/form/SetupForm'));
  h.assert(redirected.origin === setup.origin && redirected.pathname === setup.pathname
    && redirected.searchParams.get('formName') === 'VTForm'
    && redirected.searchParams.get('demographic_no') === String(patient)
    && /^[1-9]\d*$/.test(redirected.searchParams.get('formId') || ''),
  'VT Update did not return its own saved form');
  return { parameters: sent, id: redirected.searchParams.get('formId') };
}

function assertFormRefusal(result, { status, tokens = [] }) {
  h.assert(result.status === status && Object.hasOwn(result.headers, h.APPLICATION_HEADER)
    && !h.isWafPage(result.status, result.body), 'The form refusal was not the exact application response');
  if (status === 405) h.assert(result.headers.allow === 'POST', 'SubmitForm did not advertise its POST-only contract');
  h.assert(!tokens.some(token => result.body.includes(token)) && !/name=["']value\(ExerValue\)["']/.test(result.body),
    'A refused form request exposed its clinical form');
}

/** API refusals are not page-recorder allowances; always release the exact response. */
async function requestFormRefusal(context, baseUrl, parameters, { method, status, tokens, setup = false }) {
  const values = new URLSearchParams(parameters);
  const address = new URL(h.appUrl(baseUrl, setup ? '/form/SetupForm' : '/form/SubmitForm'));
  const options = { method, maxRedirects: 0, failOnStatusCode: false };
  if (method === 'GET' || method === 'HEAD') address.search = values.toString();
  else options.form = Object.fromEntries(values);
  const response = await context.request.fetch(address.href, options);
  try {
    const result = { status: response.status(), headers: response.headers(), body: await response.text() };
    assertFormRefusal(result, { status, tokens });
    return result;
  } finally { await response.dispose(); }
}

/** One native Update may add its exact form and one reading, preserving all older rows. */
function assertFormSaveDelta(before, after, { formId, measurementId }) {
  const old = structuredClone(after);
  for (const [domain, id] of [['forms', formId], ['measurements', measurementId]]) {
    h.assert(after[domain].length === before[domain].length + 1
      && after[domain].filter(row => row[0] === id).length === 1
      && !before[domain].some(row => row[0] === id), 'Native VT Update did not add exactly one new owned row');
    old[domain] = after[domain].filter(row => row[0] !== id);
  }
  assertPatientStateUnchanged(before, old, 'Native form-only Update');
}

/** All parents, domains and child identities are checked before any fixture deletion. */
function assertOwnedFormRows({ state, forms, measurements, patient, provider, intents }) {
  const allowed = new Set(['demographic', 'demographicExtensions', 'forms', 'measurements']);
  for (const [domain, rows] of Object.entries(state)) h.assert(allowed.has(domain) || rows.length === 0,
    `Form fixture has unexpected ${domain}; retaining all its rows`);
  h.assert(state.demographic.length === 1 && forms.length === state.forms.length
    && measurements.length === state.measurements.length, 'Form fixture ownership snapshot is incomplete');
  const owns = (comment, value) => intents.some(intent => intent.patient === patient
    && intent.comment === comment && intent.value === value);
  for (const [id, author, value, comment] of forms) h.assert(/^[1-9]\d*$/.test(id)
    && author === provider && owns(comment, value), 'Form fixture contains an unowned form; retaining all its rows');
  for (const [id, author, type, value, comment] of measurements) h.assert(/^[1-9]\d*$/.test(id)
    && author === provider && type === 'Exer' && owns(comment, value),
  'Form fixture contains an unowned measurement; retaining all its rows');
}

/** Record exclusions and POST-only saves must preserve the existing form-only workflow. */
async function checkFormRecordMethods(s, { fixture }) {
  const { sql, provider, marker, config } = s;
  const catalog = vascularTrackerCatalogFixture(sql);
  // Reverse-order cleanup removes every owned clinical reference before lazy catalog rows.
  s.cleanup(() => catalog.cleanup());
  const patients = [];
  const intents = [];
  const owner = `${marker}-FM`;
  let login;
  s.cleanup(() => {
    // Recover exact marker-owned parents even if INSERT succeeded but its ID response was lost.
    for (const patient of patients) {
      const rows = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(owner)} AND first_name=${q(patient.name)}`);
      h.assert(rows.length <= 1 && (!patient.id || rows.length === 1 && rows[0][0] === patient.id),
        'Form fixture patient ownership changed; retaining all fixture rows');
      if (!patient.id && rows.length) patient.id = rows[0][0];
    }
    for (const patient of patients.filter(patient => patient.id)) {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient.id}
        AND last_name=${q(owner)} AND first_name=${q(patient.name)} AND provider_no=${q(provider)}`) === '1', 'Form fixture parent changed');
      assertOwnedFormRows({ state: formState(sql, patient.id), patient: patient.id,
        provider: login?.providerNo, intents,
        forms: sql.rows(`SELECT ID,provider_no,ExerValue,ExerComments FROM formVTForm WHERE demographic_no=${patient.id} ORDER BY ID`),
        measurements: sql.rows(`SELECT id,providerNo,type,dataField,comments FROM measurements WHERE demographicNo=${patient.id} ORDER BY id`) });
    }
    for (const patient of patients.filter(patient => patient.id)) {
      sql.execute(`DELETE FROM formVTForm WHERE demographic_no=${patient.id};
        DELETE FROM measurements WHERE demographicNo=${patient.id};
        DELETE FROM log WHERE demographic_no=${patient.id};
        DELETE FROM demographicExt WHERE demographic_no=${patient.id};
        DELETE FROM demographicArchive WHERE demographic_no=${patient.id};
        DELETE FROM demographic WHERE demographic_no=${patient.id} AND last_name=${q(owner)} AND first_name=${q(patient.name)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient.id})
        +(SELECT COUNT(*) FROM formVTForm WHERE demographic_no=${patient.id})
        +(SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient.id})
        +(SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient.id})
        +(SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient.id})
        +(SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient.id})`) === '0',
      'Form fixture or unexpected clinical data survived cleanup');
    }
  });
  for (const name of ['FormLocked', 'FormOpen']) {
    const patient = { name };
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${q(owner)} AND first_name=${q(name)}`) === '0',
      'The form fixture marker is already in use');
    patients.push(patient);
    patient.id = h.insertId(sql, `INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${q(owner)},${q(name)},'1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW())`, 'form access patient');
    h.assert(/^[1-9]\d*$/.test(patient.id), 'The form fixture patient key is invalid');
    const initialized = await s.context.request.get(h.appUrl(config.baseUrl,
      `/demographic/DemographicEdit?demographic_no=${patient.id}`), { maxRedirects: 0 });
    try { h.assert(initialized.status() === 200 && Object.hasOwn(initialized.headers(), h.APPLICATION_HEADER),
      'The owner could not initialize the synthetic form patient'); }
    finally { await initialized.dispose(); }
  }
  const [locked, open] = patients.map(patient => patient.id);
  const role = fixture.addRole({ _demographic: 'w', _form: 'w', _appointment: 'r' });
  login = fixture.addLogin(role);
  // Do not join programs or add chart rights: this is the existing standalone form contract.
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM program_provider WHERE provider_no=${q(login.providerNo)})
    +(SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)})`) === '0',
  'The form-only positive login unexpectedly has a program');
  const seedText = `${marker}-FORM-LOCKED-STORED`;
  intents.push({ patient: locked, comment: seedText, value: '150' });
  const seedId = h.insertId(sql, `INSERT INTO formVTForm
    (demographic_no,provider_no,formCreated,visitCod,ExerValue,ExerDate,ExerComments)
    VALUES (${locked},${q(login.providerNo)},CURDATE(),'2026-10-25','150','2026-10-25',${q(seedText)})`,
  'locked form access control');
  h.assert(/^[1-9]\d*$/.test(seedId), 'The stored form control key is invalid');
  h.assert(same(fixture.lockPatient(login, locked, ['_eChart']), [`_eChart$${locked}`]),
    'The form fixture did not install its record-only exclusion');
  const reader = await signIn(s, login);
  s.cleanup(() => reader.context.close());
  const recorder = h.createRecorder();
  reader.context.on('page', page => h.wireStrictPage(page, 'form-record-method', recorder, { baseline: [] }));
  const page = await reader.context.newPage();
  const snapshot = () => patients.map(patient => formState(sql, patient.id));
  const unchanged = (before, operation) => patients.forEach((patient, index) =>
    assertPatientStateUnchanged(before[index], formState(sql, patient.id), operation));
  const setupUrl = (patient, id = '0') => h.appUrl(config.baseUrl,
    `/form/SetupForm?${new URLSearchParams({ formName: 'VTForm', demographic_no: patient, formId: id })}`);
  let payload;
  let openId = '0';
  const nativeSave = async (previousId, text) => {
    intents.push({ patient: open, comment: text, value: '150' });
    const before = snapshot();
    await page.locator('[name="value(ExerValue)"]').fill('150');
    await page.locator('[name="value(ExerComments)"]').fill(text);
    const [response] = await Promise.all([
      page.waitForResponse(answer => answer.request().method() === 'POST'
        && new URL(answer.url()).pathname === new URL(h.appUrl(config.baseUrl, '/form/SubmitForm')).pathname),
      page.waitForURL(address => address.pathname === new URL(setupUrl(open)).pathname
        && address.searchParams.get('demographic_no') === open && address.searchParams.get('formName') === 'VTForm'
        && /^[1-9]\d*$/.test(address.searchParams.get('formId') || '')
        && address.searchParams.get('formId') !== previousId, { waitUntil: 'load' }),
      page.locator('form[action$="/form/SubmitForm"] input[type="submit"][onclick*="onSave();"]').first().click(),
    ]);
    await response.finished();
    const result = assertNativeFormPost({ url: response.url(), method: response.request().method(),
      data: response.request().postData() || '', status: response.status(), headers: response.headers() },
    { patient: open, previousId, baseUrl: config.baseUrl });
    openId = result.id;
    payload = result.parameters;
    await page.waitForLoadState('load');
    const returned = new URL(page.url());
    h.assert(returned.origin === config.baseUrl.origin
      && returned.pathname === new URL(setupUrl(open, openId)).pathname
      && returned.searchParams.get('formName') === 'VTForm'
      && returned.searchParams.get('demographic_no') === open && returned.searchParams.get('formId') === openId
      && await page.locator('[name="value(ExerComments)"]').inputValue() === text,
    'Native form-only save did not reopen its exact current text');
    h.assert(sql.value(`SELECT COUNT(*) FROM formVTForm WHERE ID=${openId} AND demographic_no=${open}
      AND provider_no=${q(login.providerNo)} AND ExerValue='150' AND ExerComments=${q(text)}`) === '1',
    'Native form-only save did not persist its exact form');
    const readings = sql.rows(`SELECT id FROM measurements WHERE demographicNo=${open}
      AND providerNo=${q(login.providerNo)} AND type='Exer' AND dataField='150' AND comments=${q(text)}`);
    h.assert(readings.length === 1, 'Native form-only save did not persist its exact exercise reading');
    const after = snapshot();
    assertPatientStateUnchanged(before[0], after[0], 'Native form-only save bystander');
    assertFormSaveDelta(before[1], after[1], { formId: openId, measurementId: readings[0][0] });
  };
  try {
    await s.step('a genuine standalone form-only login opens and natively saves a new VT without chart or program grants', async () => {
      catalog.begin();
      const response = await page.goto(setupUrl(open));
      await page.waitForLoadState('load');
      catalog.capture();
      h.assert(response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER)
        && await page.locator('[name="demographic_no"]').inputValue() === open
        && await page.locator('[name="value(formId)"]').inputValue() === '0',
      'The standalone form-only login could not open its canonical new form');
      await nativeSave('0', `${marker}-FORM-OPEN-FIRST`);
    });
    await s.step('new and stored VT setup and POST refuse a separately excluded record without exposing or changing clinical data', async () => {
      for (const id of ['0', seedId]) {
        const before = snapshot();
        await requestFormRefusal(reader.context, config.baseUrl,
          new URLSearchParams({ formName: 'VTForm', demographic_no: locked, formId: id }),
          { method: 'GET', status: 403, tokens: [seedText], setup: true });
        unchanged(before, 'Refused record-only VT setup');
        for (const submit of ['save', 'exit']) {
          const parameters = new URLSearchParams(payload);
          parameters.set('demographic_no', locked); parameters.set('value(formId)', id);
          parameters.set('submit', submit);
          const text = `${marker}-FORM-REFUSED-${id === '0' ? 'NEW' : 'STORED'}-${submit}`;
          parameters.set('value(ExerComments)', text);
          intents.push({ patient: locked, comment: text, value: '150' });
          await requestFormRefusal(reader.context, config.baseUrl, parameters,
            { method: 'POST', status: 403, tokens: [seedText, text] });
          unchanged(before, 'Refused record-only VT POST');
        }
      }
    });
    await s.step('GET HEAD and PUT cannot save or exit new or stored VTs even with valid form authority', async () => {
      for (const method of ['GET', 'HEAD', 'PUT']) for (const id of ['0', openId]) for (const submit of ['save', 'exit']) {
        const before = snapshot();
        const text = `${marker}-FORM-METHOD-${method}-${id === '0' ? 'NEW' : 'STORED'}-${submit}`;
        // Keep GET/HEAD below proxy URI limits; all values come from the accepted native POST.
        const parameters = new URLSearchParams([...payload].filter(([name]) =>
          ['demographic_no', 'value(formName)', 'value(formId)', 'value(ExerValue)', 'value(ExerDate)',
            'value(visitCod)', 'CSRF-TOKEN', 'submit'].includes(name)));
        parameters.set('value(formId)', id); parameters.set('submit', submit);
        parameters.set('value(ExerComments)', text);
        intents.push({ patient: open, comment: text, value: '150' });
        await requestFormRefusal(reader.context, config.baseUrl, parameters,
          { method, status: 405, tokens: [text] });
        unchanged(before, 'Refused non-POST VT mutation');
      }
    });
    await s.step('the same form-only login still reopens and natively updates its stored VT after all refusals', async () => {
      const before = snapshot();
      const response = await page.goto(setupUrl(open, openId));
      await page.waitForLoadState('load');
      h.assert(response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER)
        && await page.locator('[name="value(formId)"]').inputValue() === openId,
      'The stored VT positive control was not served');
      unchanged(before, 'Allowed stored VT setup');
      await nativeSave(openId, `${marker}-FORM-OPEN-LAST`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM program_provider WHERE provider_no=${q(login.providerNo)})
        +(SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)})`) === '0',
      'Standalone form operations acquired unexpected program state');
      h.assertStrictPage(recorder);
    });
  } finally { await reader.context.close(); }
}

module.exports = { checkFormRecordMethods, formState, assertNativeFormPost, assertFormRefusal,
  requestFormRefusal, assertFormSaveDelta, assertOwnedFormRows };
