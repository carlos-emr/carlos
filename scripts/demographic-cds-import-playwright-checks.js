#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Real menu/form, bounded raw-body upload, durable jobs and protected reports.
 * Required environment: disposable migrated database, enabled CDS importer,
 * approved source namespace/profile and maintenance admission. Missing feature
 * configuration fails visibly; it must never silently skip this release gate.
 * Synthetic owned clinical fixtures are removed only after their import holds
 * clear. Durable import receipts and audit evidence remain for investigation.
 */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');
const { createHash, randomUUID } = require('node:crypto');

const xmlText = value => String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;').replace(/'/g, '&apos;');
const personName = (first, last) => `<cdsd:FirstName>${xmlText(first)}</cdsd:FirstName>`
  + `<cdsd:LastName>${xmlText(last)}</cdsd:LastName>`;

/** The fixture's values, shared by the XML and the database assertions. */
function fixtureValues(marker) {
  return {
    lastName: marker,
    firstName: 'Import',
    dob: '1971-03-04',
    address: `${marker.slice(-6)} Fixture Import Rd`,
    city: 'Fixture City',
    postal: 'K1A0B1',
    phone: '613-555-0142',
    drug: `${marker} Fixture Med`,
    allergen: `${marker} Fixture Allergen`,
    note: `${marker} imported fixture encounter note`,
  };
}

/** A single-patient XML fixture, validated against the pinned OntarioMD migration schema. */
function buildCdsXml(v, provider) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<OmdCds xmlns="cds" xmlns:cdsd="cds_dt">
<PatientRecord>
<Demographics>
<Names><cdsd:LegalName namePurpose="L">
<cdsd:FirstName><cdsd:Part>${xmlText(v.firstName)}</cdsd:Part><cdsd:PartType>GIV</cdsd:PartType></cdsd:FirstName>
<cdsd:LastName><cdsd:Part>${xmlText(v.lastName)}</cdsd:Part><cdsd:PartType>FAMC</cdsd:PartType></cdsd:LastName>
</cdsd:LegalName></Names>
<DateOfBirth>${v.dob}</DateOfBirth>
<Gender>F</Gender>
<UniqueVendorIdSequence>${xmlText(v.lastName.slice(-20))}</UniqueVendorIdSequence>
<Address addressType="M"><cdsd:Structured><cdsd:Line1>${xmlText(v.address)}</cdsd:Line1>
<cdsd:City>${xmlText(v.city)}</cdsd:City><cdsd:CountrySubdivisionCode>CA-ON</cdsd:CountrySubdivisionCode>
<cdsd:PostalZipCode><cdsd:PostalCode>${v.postal}</cdsd:PostalCode></cdsd:PostalZipCode></cdsd:Structured></Address>
<PhoneNumber phoneNumberType="R"><cdsd:phoneNumber>${v.phone}</cdsd:phoneNumber></PhoneNumber>
<PrimaryPhysician><Name>${personName(provider.first, provider.last)}</Name></PrimaryPhysician>
<PersonStatusCode><PersonStatusAsEnum>A</PersonStatusAsEnum></PersonStatusCode>
</Demographics>
<AllergiesAndAdverseReactions>
<OffendingAgentDescription>${xmlText(v.allergen)}</OffendingAgentDescription>
<PropertyOfOffendingAgent>DR</PropertyOfOffendingAgent>
<ReactionType>AL</ReactionType>
<StartDate><cdsd:FullDate>2020-05-06</cdsd:FullDate></StartDate>
<Severity>MO</Severity>
<Reaction>Rash</Reaction>
<RecordedDate><cdsd:FullDate>2020-05-07</cdsd:FullDate></RecordedDate>
</AllergiesAndAdverseReactions>
<MedicationsAndTreatments>
<PrescriptionWrittenDate><cdsd:FullDate>2026-01-15</cdsd:FullDate></PrescriptionWrittenDate>
<StartDate><cdsd:FullDate>2026-01-15</cdsd:FullDate></StartDate>
<DrugName>${xmlText(v.drug)}</DrugName>
<NumberOfRefills>2</NumberOfRefills>
<Dosage>1</Dosage>
<DosageUnitOfMeasure>tab</DosageUnitOfMeasure>
<Route>PO</Route>
<Frequency>BID</Frequency>
<Duration>30</Duration>
<Quantity>60</Quantity>
<PastMedications><cdsd:ynIndicatorsimple>N</cdsd:ynIndicatorsimple></PastMedications>
<PrescriptionStatus>Active</PrescriptionStatus>
<NonAuthoritativeIndicator>N</NonAuthoritativeIndicator>
</MedicationsAndTreatments>
<ClinicalNotes>
<NoteType>Clinical</NoteType>
<MyClinicalNotesContent>${xmlText(v.note)}</MyClinicalNotesContent>
<EventDateTime><cdsd:FullDateTime>2026-02-03T10:20:00</cdsd:FullDateTime></EventDateTime>
<ParticipatingProviders><Name>${personName(provider.first, provider.last)}</Name>
<DateTimeNoteCreated><cdsd:FullDateTime>2026-02-03T10:25:00</cdsd:FullDateTime></DateTimeNoteCreated></ParticipatingProviders>
</ClinicalNotes>
</PatientRecord>
</OmdCds>
`;
}

/** The fixture must be a valid CDS file, or a refusal would prove nothing. */
function assertSchemaValid(xml) {
  try {
    execFileSync('xmllint', ['--nonet', '--noout', '--schema',
      path.join(__dirname, '../src/main/resources/cds-import/schema/EMR_Data_Migration_Schema.xsd'), '-'],
    { input: Buffer.from(xml, 'utf8'), stdio: ['pipe', 'pipe', 'pipe'], timeout: 30000 });
  } catch (error) {
    if (error.code === 'ENOENT') throw new Error('Required CDS regression prerequisite missing: xmllint (libxml2-utils)');
    throw new Error('The synthetic CDS fixture does not validate against the pinned OntarioMD migration schema');
  }
}

/**
 * The import log's first table: a header row of category names and one data
 * row of counts. Returns {category: count} for the data row.
 */
function parseImportLogCounts(text) {
  const rows = text.split(/\r?\n/).filter(line => line.includes('|'));
  h.assert(rows.length >= 3, 'The import event log has no summary table');
  const cells = row => row.split('|').slice(0, -1).map(cell => cell.trim());
  const header = cells(rows[0]);
  const counts = cells(rows[2]);
  return Object.fromEntries(header.map((name, i) => [name, counts[i]]));
}

// Every child row the importer can write for one patient, keyed only by that
// patient's demographic_no (or a row owned by one of its drugs/allergies/notes).
function childCleanup(id) {
  const drugs = `SELECT drugid FROM drugs WHERE demographic_no=${id}`;
  const allergies = `SELECT allergyid FROM allergies WHERE demographic_no=${id}`;
  const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${h.sqlString(id)}`;
  return [
    `DELETE FROM partial_date WHERE (table_name=2 AND table_id IN (${drugs})) OR (table_name=1 AND table_id IN (${allergies}))`,
    `DELETE FROM casemgmt_note_link WHERE note_id IN (SELECT note_id FROM (${notes}) n)`,
    `DELETE FROM casemgmt_issue_notes WHERE note_id IN (SELECT note_id FROM (${notes}) n)`,
    `DELETE FROM casemgmt_note WHERE demographic_no=${h.sqlString(id)}`,
    `DELETE FROM casemgmt_issue WHERE demographic_no=${h.sqlString(id)}`,
    `DELETE FROM drugReason WHERE demographicNo=${id}`,
    `DELETE FROM drugs WHERE demographic_no=${id}`,
    `DELETE FROM allergies WHERE demographic_no=${id}`,
    `DELETE FROM admission WHERE client_id=${id}`,
    `DELETE FROM demographiccust WHERE demographic_no=${id}`,
    `DELETE FROM demographicExt WHERE demographic_no=${id}`,
    `DELETE FROM demographicArchive WHERE demographic_no=${id}`,
    `DELETE FROM DemographicContact WHERE demographicNo=${id}`,
    `DELETE FROM demographicPharmacy WHERE demographic_no=${id}`,
  ];
}

function remainingChildren(sql, id) {
  return sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${id})
    + (SELECT COUNT(*) FROM allergies WHERE demographic_no=${id})
    + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${h.sqlString(id)})
    + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${h.sqlString(id)})
    + (SELECT COUNT(*) FROM drugReason WHERE demographicNo=${id})
    + (SELECT COUNT(*) FROM admission WHERE client_id=${id})
    + (SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${id})
    + (SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${id})
    + (SELECT COUNT(*) FROM demographicArchive WHERE demographic_no=${id})
    + (SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${id})
    + (SELECT COUNT(*) FROM demographicPharmacy WHERE demographic_no=${id})`);
}

/** Open the actual existing administration menu route, including its Struts/JSP binding. */
async function openImportFrame(s, label = 'cds-import-admin') {
  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label, timeout: 20000});
  const link = admin.locator('#adminNav a.xlink[rel$="/form/importUpload"]');
  await revealAuditLink(admin, link, 20000); await link.click();
  const iframe = admin.locator('#dynamic-content iframe#myFrame'); await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'Demographic Import frame did not load');
  await frame.locator('#cdsImportForm #importFile').waitFor();
  h.assert(!await frame.locator('#cdsOptions').isDisabled(), 'Required import feature/source registration is unavailable');
  await frame.waitForFunction(() => Boolean(document.querySelector('input[name="CSRF-TOKEN"]')?.value));
  const source = await frame.locator('#cdsSource option[data-profile]').evaluateAll(options => {
    const option = options.find(value => value.dataset.profile === 'pcon-2024-02') || options[0];
    return option ? option.value : null;
  });
  h.assert(source, 'Required approved import source/profile is missing');
  await frame.locator('#cdsSource').selectOption(source);
  h.assert(!await frame.locator('#mergeExisting').isChecked(), 'Existing-patient merge must default off');
  return {admin, frame};
}

/** Browser-authenticated API probes use the same CSRF guard and bounded DTO routes as the UI. */
async function importApi(frame, route, params = {}, method = 'GET') {
  const binding = await frame.evaluate(({route}) => {
    const root = document.getElementById('cdsImport');
    const token = document.querySelector('input[name="CSRF-TOKEN"]')?.value;
    if (!token) throw new Error('IMPORT_CSRF_TOKEN_UNAVAILABLE');
    const url = new URL(root.dataset.context + '/form/' + route, location.origin);
    return {url: url.href, token};
  }, {route});
  // Protocol/refusal probes use the real signed-in cookie jar and injected token. This follows
  // the existing authz probe pattern: intentional405/409 responses must not become page JS errors.
  // Upload, acceptance, polling and recovery still run through the actual UI event handlers.
  const url = new URL(binding.url);
  const options = {method, maxRedirects: 0, failOnStatusCode: false,
    headers: {'CSRF-TOKEN': binding.token, 'X-Requested-With': 'XMLHttpRequest'}};
  if (method === 'GET') for (const [key, value] of Object.entries(params)) url.searchParams.set(key, String(value));
  else options.form = Object.fromEntries(Object.entries(params).map(([key, value]) => [key, String(value)]));
  const response = await frame.page().context().request.fetch(url.href, options);
  const text = await response.text();
  h.assert(Buffer.byteLength(text, 'utf8') <= 2 * 1024 * 1024, 'Protocol metadata exceeded its bounded receipt budget');
  let body; try { body = JSON.parse(text); } catch (_) { body = null; }
  return {status: response.status(), body, type: response.headers()['content-type'],
    redirected: response.status() >= 300 && response.status() < 400};
}

async function awaitRunReceipt(frame, id, timeout = 180000) {
  await frame.waitForFunction(runId => {
    try {
      const value = JSON.parse(document.getElementById('runSummary').textContent);
      return value.job.id === runId && value.summary.operationTerminal === true;
    } catch (_) { return false; }
  }, id, {timeout, polling: 500});
  return JSON.parse(await frame.locator('#runSummary').innerText());
}

async function uploadAndAwaitRun(s, admin, frame, file, {dryRun = false} = {}) {
  if (!await frame.locator('#cdsSource').inputValue()) {
    const value=await frame.locator('#cdsSource option[data-profile]').evaluateAll(options =>
      (options.find(option=>option.dataset.profile==='pcon-2024-02') || options[0])?.value);
    h.assert(value,'Required registered source disappeared after page reload');
    await frame.locator('#cdsSource').selectOption(value);
  }
  await frame.locator('#dryRun').setChecked(dryRun);
  await frame.locator('#uploadResumeId').fill('');
  await frame.locator('#importFile').setInputFiles(file);
  const begin = admin.waitForResponse(response => new URL(response.url()).pathname.endsWith('/form/cdsUpload')
    && response.request().method() === 'POST' && response.request().postData()?.includes('operation=begin'), {timeout: 30000});
  const chunk = admin.waitForRequest(request => new URL(request.url()).pathname.endsWith('/form/cdsUpload')
    && new URL(request.url()).searchParams.get('operation') === 'chunk', {timeout: 30000});
  const acceptance = admin.waitForResponse(response => new URL(response.url()).pathname.endsWith('/form/cdsImportSubmit')
    && response.request().method() === 'POST', {timeout: 120000});
  await frame.locator('#cdsStart').click();
  const begun = await begin; h.assert(begun.status() === 200, 'Upload reservation did not create a durable owner receipt');
  const {upload} = await begun.json();
  h.assert((await chunk).headers()['content-type'] === 'application/octet-stream', 'Upload regressed to buffered multipart');
  const accepted = await acceptance; const result = await accepted.json();
  h.assert(accepted.status() === 202 && result.accepted === true && result.job?.id,
    'Submission did not return a distinct durable acceptance receipt');
  const receipt = await awaitRunReceipt(frame, result.job.id);
  await frame.locator('#cdsStart').waitFor({state: 'visible'});
  await frame.waitForFunction(() => !document.getElementById('cdsOptions').disabled);
  return {uploadId: upload.id, id: result.job.id, ...receipt};
}

function registerImportCleanup(s, owner) {
  const ids = () => s.sql.rows(`SELECT demographic_no FROM demographic WHERE ${owner} ORDER BY demographic_no`).flat();
  s.cleanup(() => {
    for (const id of ids()) {
      h.assert(/^[1-9]\d*$/.test(id), 'Imported fixture has an invalid identity');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM cds_import_patient WHERE target_demographic_no=${id} AND held=1`) === '0',
        'Incomplete/held fixture retained with its evidence; cleanup cannot hide failed import recovery');
      s.sql.execute(childCleanup(id).join(';\n'));
      h.assert(remainingChildren(s.sql, id) === '0', 'Owned imported clinical fixture cleanup is incomplete');
      s.sql.execute(`DELETE FROM demographic WHERE demographic_no=${id} AND (${owner})`);
    }
    h.assert(ids().length === 0, 'Owned imported fixture was not removed');
    // Audit, immutable source, import ledger and outcome receipts deliberately remain for review/retention.
  });
  return ids;
}

async function workflow(s) {
  const v = fixtureValues(s.marker); const mixedLast = `${s.marker}-B`;
  const owner = `first_name=${h.sqlString(v.firstName)} AND last_name IN (${h.sqlString(v.lastName)},${h.sqlString(mixedLast)})`;
  const importedIds = registerImportCleanup(s, owner);
  const [first, last] = s.sql.rows(`SELECT first_name,last_name FROM provider WHERE provider_no=${h.sqlString(s.provider)}`)[0] || [];
  h.assert(first && last, 'Test provider has no source-physician fixture name');
  const xml = buildCdsXml(v, {first, last}); assertSchemaValid(xml);
  const file = {name: `${s.marker}.xml`, mimeType: 'text/xml', buffer: Buffer.from(xml)};
  let admin, frame, dry, actual, id;
  await s.step('Existing menu and bookmark route render the secured durable import UI', async () => {
    ({admin, frame} = await openImportFrame(s));
    h.assert(await frame.locator('#cancelRun,#resumeRun,#eventReport,#errorReport,#receiptReport').count() === 5,
      'Operator controls or protected reports are missing');
  });
  await s.step('Empty input visibly refuses acceptance and leaves the database unchanged', async () => {
    await frame.locator('#cdsStart').click();
    await frame.locator('#cdsNotice.alert-danger').waitFor();
    h.assert(/select at least one/i.test(await frame.locator('#cdsNotice').innerText()), 'Missing-file refusal is not visible');
    h.assert(importedIds().length === 0, 'Empty input wrote clinical rows');
  });
  await s.step('Mutators reject GET and a stale synchronous form refuses before acceptance', async () => {
    for (const route of ['cdsUpload', 'cdsImportSubmit', 'cdsImportControl']) {
      h.assert((await importApi(frame, route)).status === 405, `${route} accepted GET`);
    }
    const stale = await importApi(frame, 'importUpload', {}, 'POST');
    h.assert(stale.status === 409 && stale.body?.code === 'IMPORT_CLIENT_REFRESH_REQUIRED', 'Old synchronous client was not explicitly refused');
    h.assert(importedIds().length === 0, 'A stale client wrote clinical rows');
  });
  await s.step('Raw chunk upload and dry run produce a terminal receipt without clinical writes or clinical-success styling', async () => {
    dry = await uploadAndAwaitRun(s, admin, frame, file, {dryRun: true});
    h.assert(dry.job.state === 'DRY_RUN_COMPLETE' && dry.summary.clinicalComplete === false, 'Dry run claimed clinical completion');
    h.assert(importedIds().length === 0, 'Dry run wrote a patient');
    h.assert(!await frame.locator('#runState').evaluate(node => node.classList.contains('text-success')), 'Dry run was styled as imported success');
  });
  await s.step('Execute the same immutable upload after preflight and reconnect to durable completion', async () => {
    await frame.locator('#dryRun').uncheck(); await frame.locator('#uploadResumeId').fill(dry.uploadId);
    const acceptance = admin.waitForResponse(response => new URL(response.url()).pathname.endsWith('/form/cdsImportSubmit')
      && response.request().method() === 'POST', {timeout: 120000});
    await frame.locator('#submitCompletedUpload').click();
    const accepted = await acceptance; const body = await accepted.json();
    h.assert(accepted.status() === 202 && body.accepted === true && body.job.id !== dry.id, 'Execute did not get its independent durable attempt');
    const bookmark = new URL(frame.url()); bookmark.searchParams.set('runId', body.job.id);
    await frame.goto(bookmark.href); await frame.locator('#cdsImport').waitFor();
    actual = await awaitRunReceipt(frame, body.job.id);
    h.assert(actual.summary.cleanComplete === true && actual.summary.clinicalComplete === true
      && actual.summary.reportsDelivered === true && actual.summary.cleanupComplete === true,
    'Completion receipt did not prove clinical writes, report publication and cleanup');
    h.assert(await frame.locator('#runState').evaluate(node => node.classList.contains('text-success')), 'Verified clinical completion is not visible');
    const ids = importedIds(); h.assert(ids.length === 1, `Expected one patient, found ${ids.length}`); [id] = ids;
  });
  await s.step('Native demographic and clinical fields preserve values without inventing source authors or signatures', async () => {
    h.assert(s.sql.value(`SELECT CONCAT_WS('|',year_of_birth,month_of_birth,date_of_birth,sex,patient_status,address,city,province,postal,phone)
      FROM demographic WHERE demographic_no=${id}`) === ['1971','03','04','F','AC',v.address,v.city,'ON',v.postal,v.phone].join('|'),
    'Native demographic values differ from the source');
    h.assert(s.sql.value(`SELECT CONCAT_WS('|',customName,rx_date,duration,quantity,\`repeat\`,route,freqcode,archived,cds_currentness)
      FROM drugs WHERE demographic_no=${id}`) === [v.drug,'2026-01-15','30','60','2','PO','BID','0','CURRENT'].join('|'),
    'Native medication values/currentness differ from explicit source evidence');
    h.assert(s.sql.value(`SELECT CONCAT_WS('|',DESCRIPTION,TYPECODE,reaction,severity_of_reaction,start_date,archived)
      FROM allergies WHERE demographic_no=${id}`) === [v.allergen,'0','Rash','2','2020-05-06','0'].join('|'),
    'Allergy values differ or DR was falsely classified as a brand');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${id} AND note=${h.sqlString(v.note)}
      AND (signing_provider_no IS NULL OR signing_provider_no='') AND signed=0`) === '1',
    'Source encounter note was omitted, duplicated or falsely signed');
    for (const table of ['drugs','allergies']) h.assert(s.sql.value(`SELECT COUNT(*) FROM ${table} WHERE demographic_no=${id}`) === '1', `${table} not exactly once`);
  });
  await s.step('An ordinary doctor cannot administer imports but retains authorized patient source-field access', async () => {
    const fixture = authzReadFixture({sql:s.sql,marker:s.marker,provider:s.provider,testUser:s.config.testUser});
    s.cleanup(() => fixture.cleanup());
    const login = fixture.addLogin('doctor'); fixture.joinTestLoginPrograms(login);
    s.sql.execute(`INSERT INTO provider_facility(provider_no,facility_id)
      SELECT ${h.sqlString(login.providerNo)},facility_id FROM provider_facility WHERE provider_no=${h.sqlString(s.provider)}`);
    const doctor = await signIn(s, login);
    try {
      const count = s.sql.value('SELECT COUNT(*) FROM cds_import_upload');
      const token = await doctor.page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
      const headers = {'CSRF-TOKEN':token,'X-Requested-With':'XMLHttpRequest'};
      for (const route of ['/form/importUpload', `/form/cdsImportStatus?runId=${actual.job.id}`]) {
        const denied = await doctor.context.request.get(h.appUrl(s.config.baseUrl,route), {maxRedirects:0,headers});
        const json = (denied.headers()['content-type'] || '').includes('application/json');
        h.assert(denied.status() === 403 && denied.headers()['x-permitted-cross-domain-policies'] !== undefined
          && (json ? (await denied.json()).code === 'IMPORT_ACCESS_DENIED' : route === '/form/importUpload'),
          'An ordinary doctor acquired import-administration or run access');
      }
      const denied = await doctor.context.request.post(h.appUrl(s.config.baseUrl,'/form/cdsUpload'),
        {maxRedirects:0,headers,
          form:{operation:'begin',fileName:'denied.xml',expectedBytes:'1'}});
      h.assert(denied.status() === 403 && (await denied.json()).code === 'IMPORT_ACCESS_DENIED',
        'An ordinary doctor reserved upload bytes');
      h.assert(s.sql.value('SELECT COUNT(*) FROM cds_import_upload') === count, 'Denied import administration changed durable uploads');
      const readable = await doctor.context.request.get(h.appUrl(s.config.baseUrl,
        `/demographic/cdsSourceFields?demographicNo=${id}&category=Demographics`), {maxRedirects:0,headers});
      h.assert(readable.status() === 200 && /CDS source fields/.test(await readable.text()),
        'Patient source reads incorrectly require import administration');
    } finally { await doctor.context.close(); }
  });
  await s.step('Run-scoped protected downloads deliver durable event, error and reconciliation evidence', async () => {
    for (const [selector, type] of [['eventReport','event'],['errorReport','error'],['receiptReport','receipt']]) {
      const link = frame.locator('#' + selector); await link.waitFor({state: 'visible'});
      const href = await link.getAttribute('href'); const url = new URL(href, frame.url());
      h.assert(url.searchParams.get('runId') === actual.job.id && url.searchParams.get('type') === type
        && !url.searchParams.has('importlog'), 'Report URL exposes a storage name rather than authorized run identity');
      const download = admin.waitForEvent('download', {timeout: 30000}); await link.click();
      const saved = await download; const contents = fs.readFileSync(await saved.path(), 'utf8');
      h.assert(!/<html/i.test(contents), `Protected ${type} report is a login page`);
      if (type === 'event') {
        const rows=contents.trim().split(/\r?\n/).filter(Boolean).map(row=>JSON.parse(row));
        h.assert(rows.length === 1 && rows[0].patientId === v.lastName.slice(-20) && rows[0].held === false
          && rows[0].inventoryComplete === true, 'Event log does not reconcile the exact source patient/copy');
        const counts=rows[0].importedOccurrences;
        h.assert(counts.Demographics === 1 && counts.MedicationsAndTreatments === 1
          && counts.AllergiesAndAdverseReactions === 1 && counts.ClinicalNotes === 1
          && Object.keys(counts).length === 17, 'Event log category occurrences omit, duplicate or count helper units');
        h.assert(Object.values(rows[0].notCommittedOccurrences).every(count=>count === 0), 'Successful event receipt contains uncommitted clinical occurrences');
      } else if (type === 'receipt') {
        const receipt=JSON.parse(contents);
        h.assert(receipt.runId === actual.job.id && receipt.clinicalComplete === true && receipt.reportsDelivered === true
          && receipt.cleanupComplete === true && receipt.manifestDigest === actual.job.manifestDigest,
        'Completion file does not reconcile its run, immutable manifest, clinical outcome and delivery');
      } else {
        const errors=contents.trim().split(/\r?\n/).filter(Boolean).map(row=>JSON.parse(row));
        h.assert(errors.every(row => !(row.blocking === true && row.resolved === false)
          && row.code !== 'SOURCE_UNIT_NOT_COMMITTED'), 'Clean completion has unreported blocking or uncommitted units');
      }
    }
  });
  await s.step('Exact concurrent submission retry returns one job and cannot duplicate clinical units', async () => {
    const options = await frame.evaluate(() => {
      const source = document.querySelector('#cdsSource option[data-profile="pcon-2024-02"]') || document.querySelector('#cdsSource option[data-profile]');
      return {providerNo: document.getElementById('providerNo').value, sourceNamespace: source.dataset.namespace,
        sourceProfile: source.dataset.profile, matchProviderNames: true, mergeExisting: false, dryRun: false,
        ...(document.getElementById('programId').value ? {programId:document.getElementById('programId').value} : {}),
        ...(document.getElementById('facilityId').value ? {facilityId:document.getElementById('facilityId').value} : {})};
    });
    const results = await Promise.all([1,2].map(() => importApi(frame, 'cdsImportSubmit', {...options, uploadId: dry.uploadId}, 'POST')));
    h.assert(results.every(result => result.status === 202 && result.body?.job?.id === actual.job.id), 'Identical acceptance retry changed run identity');
    h.assert(importedIds().length === 1 && s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${id}`) === '1'
      && s.sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${id}`) === '1', 'Acceptance retry duplicated clinical data');
  });
  await s.step('Exact committed chunk retry is idempotent and altered retry is refused', async () => {
    const reservation = await importApi(frame,'cdsUpload',{operation:'begin',fileName:file.name,expectedBytes:file.buffer.length},'POST');
    h.assert(reservation.status === 200 && reservation.body?.upload?.id, 'Chunk probe reservation failed');
    const probeId=reservation.body.upload.id;
    const token = await frame.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const post = async bytes => {
      const url = new URL(h.appUrl(s.config.baseUrl, '/form/cdsUpload'));
      for (const [key,value] of Object.entries({operation:'chunk',uploadId:probeId,offset:0,bytes:bytes.length,
        sha256:createHash('sha256').update(bytes).digest('hex')})) url.searchParams.set(key,String(value));
      const response = await s.context.request.post(url.href, {data:bytes, headers:{'CSRF-TOKEN':token,
        'X-Requested-With':'XMLHttpRequest','Content-Type':'application/octet-stream'}});
      const status=response.status(); await response.dispose(); return status;
    };
    const same = await Promise.all([post(file.buffer),post(file.buffer)]);
    h.assert(same.every(status => [200,409].includes(status)) && same.includes(200), 'Concurrent exact chunk receipt retries did not reconcile');
    const changed=Buffer.from(file.buffer); changed[0]^=1;
    h.assert(await post(changed) === 409, 'Altered committed chunk was accepted');
    h.assert((await importApi(frame,'cdsUpload',{operation:'abandon',uploadId:probeId},'POST')).status === 200, 'Unsubmitted probe cleanup failed');
  });
  await s.step('Multi-patient ZIP includes both copies and identical replay cannot insert duplicates', async () => {
    const other = buildCdsXml({...v,lastName:mixedLast},{first,last}); assertSchemaValid(other);
    const zip=execFileSync('python3',['-c','import io,json,sys,zipfile; out=io.BytesIO(); z=zipfile.ZipFile(out,"w",zipfile.ZIP_DEFLATED); [z.writestr(n,t) for n,t in json.load(sys.stdin)]; z.close(); sys.stdout.buffer.write(out.getvalue())'],
      {input:JSON.stringify([['new/patient.xml',other],['existing/patient.xml',xml],['associated/readme.txt','Synthetic ancillary bytes retained in manifest.']]),timeout:30000});
    const batch={name:`${s.marker}-batch.zip`,mimeType:'application/zip',buffer:zip};
    const run=await uploadAndAwaitRun(s,admin,frame,batch);
    h.assert(run.summary.cleanComplete === true && run.summary.clinicalComplete === true
      && run.summary.discoveryComplete === true && run.summary.expectedPatients === 2 && run.summary.pendingUnits === 0,
      'ZIP did not commit or verify both source patients completely');
    h.assert(importedIds().length === 2, 'ZIP omitted a new patient or duplicated the existing patient');
    const replay=await uploadAndAwaitRun(s,admin,frame,batch);
    h.assert(replay.summary.operationTerminal === true && replay.summary.cleanComplete === true
      && replay.summary.clinicalComplete === true && replay.summary.expectedPatients === 2
      && replay.summary.pendingUnits === 0 && replay.summary.unresolvedReviewItems === 0 && importedIds().length === 2,
      'ZIP replay failed verification, omitted completeness accounting or inserted duplicates');
    h.assert(await frame.locator('#patientRows tr').count() <= 50 && await frame.locator('#diagnosticRows tr').count() <= 50
      && await frame.locator('#uploadRows tr').count() <= 50, 'Operator DOM exceeded bounded page sizes');
  });
  await s.step('Foreign run and physical report-path probes are refused without revealing paths', async () => {
    const foreign=await importApi(frame,'cdsImportStatus',{runId:randomUUID()});
    h.assert([403,404,409].includes(foreign.status) && foreign.body?.ok === false, 'Unknown run was exposed or treated as success');
    const report=await importApi(frame,'cdsImportReport',{runId:actual.job.id,type:'../../etc/passwd'});
    h.assert(report.status === 400, 'Physical report-path probe was not refused');
  });
}

/** Full pinned corpus exercises native storage and the clinician's category-local source UI. */
async function nativeViewsWorkflow(s) {
  const name = `${s.marker}-CORPUS`;
  const sourceId = s.marker.slice(-20);
  const note = `${s.marker} imported corpus note`;
  const template = fs.readFileSync(path.join(__dirname, '../src/test/resources/demographic/cds-import-all-categories.xml'), 'utf8');
  h.assert(template.includes('FAKE-CDS-CORPUS-202608') && template.includes('E-CDS-CORPUS-202608'), 'Corpus identity markers changed');
  const xml = template.replaceAll('FAKE-CDS-CORPUS-202608', xmlText(name)).replaceAll('E-CDS-CORPUS-202608', xmlText(sourceId))
    .replaceAll('FAKE-PW0000000000004241 imported fixture encounter note', xmlText(note));
  assertSchemaValid(xml);
  let frame, admin, run, id;
  await s.step('Complete pinned corpus imports through the existing web menu with a clean durable receipt', async () => {
    ({frame, admin} = await openImportFrame(s));
    run = await uploadAndAwaitRun(s, admin, frame, {name:`${name}.xml`,mimeType:'text/xml',buffer:Buffer.from(xml)});
    h.assert(run.summary.cleanComplete === true && run.summary.expectedPatients === 1
      && run.summary.operationTerminal === true, 'Full corpus did not complete cleanly');
    const ids = s.sql.rows(`SELECT demographic_no FROM demographic WHERE first_name='Import' AND last_name=${h.sqlString(name)}`).flat();
    h.assert(ids.length === 1, 'Corpus native patient is missing or duplicated'); [id] = ids;
    h.assert(s.sql.value(`SELECT COUNT(*) FROM cds_import_patient WHERE target_demographic_no=${id} AND held=1`) === '0',
      'Corpus remained held after claimed completion');
  });
  await s.step('Native demographics and notes retain exact source text and unknown observation precision', async () => {
    h.assert(s.sql.value(`SELECT CONCAT_WS('|',spoken_lang,patient_status_date) FROM demographic WHERE demographic_no=${id}`)
      === 'French|2025-01-02', 'Canonical language/status date were not mapped natively');
    h.assert(s.sql.value(`SELECT content FROM demographiccust WHERE demographic_no=${id}`)
      === '<unotes>Complete source patient note including literal </unotes> and café 😀</unotes>', 'Native patient note was clipped or encoded twice');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${id} AND note=${h.sqlString(note)} AND observation_date IS NULL AND signed=0`) === '1',
      'Date-only source note invented an exact timestamp or signature');
    for (const table of ['drugs','allergies','preventions']) {
      h.assert(Number(s.sql.value(`SELECT COUNT(*) FROM ${table} WHERE demographic_no=${id}`)) > 0, `${table} native projection is absent`);
    }
    h.assert(Number(s.sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${id}`)) >= 3,
      'Contextual physicians/contact native associations are absent');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM demographicPharmacy WHERE demographic_no=${id}`) === '1', 'Preferred pharmacy native association is absent');
  });
  await s.step('Default master/chart, allergy and prevention screens expose their real patient source links', async () => {
    const {masterPage} = await openMasterRecord(s.context,s.schedule,s.recorder,
      {searchTerm:name,preferredDemographicNo:id,timeout:20000});
    let chart;
    const follow = async (page, category) => {
      const link = page.locator(`.cds-source-fields-link[href*="category=${category}"]`).first();
      await link.waitFor({state:'visible'});
      const {page:source} = await clickOpensPopupOrNavigates(page,link,
        {context:s.context,recorder:s.recorder,label:`native-source-${category}`,timeout:20000});
      await source.locator('#cdsSourceFields .cds-source-field').first().waitFor();
      h.assert(new URL(source.url()).searchParams.get('demographicNo') === id, 'Native screen source link changed patients');
      if(source !== page) await source.close();
    };
    try {
      await follow(masterPage,'Demographics');
      chart = await openChart(s.context,masterPage,s.recorder,20000); await waitForNavbars(chart,20000);
      await chart.getByText(note,{exact:false}).first().waitFor({state:'visible',timeout:30000});
      await follow(chart,'ClinicalNotes'); await follow(chart,'NewCategory');
      const allergy = await s.popup(chart,chart.locator('a[onclick*="showAllergy"]').first(),'native-allergy-source');
      await follow(allergy,'AllergiesAndAdverseReactions'); await allergy.close();
      const prevention = await s.popup(chart,chart.locator('a[onclick*="ViewPreventionIndex"]').first(),'native-prevention-source');
      await follow(prevention,'Immunizations'); await prevention.close();
    } finally {
      if(chart) { await releaseChartLocks(s.context,s.config.baseUrl,[chart]); await chart.close(); }
      await masterPage.close();
    }
  });
  const categories = ['Demographics','AllergiesAndAdverseReactions','MedicationsAndTreatments','Immunizations',
    'LaboratoryResults','Reports','ClinicalNotes','Appointments','FamilyHistory','PastHealth','ProblemList',
    'PersonalHistory','RiskFactors','AlertsAndSpecialNeeds','NewCategory','CareElements'];
  await s.step('Every clinical category renders bounded protected logical source fields without physical paths', async () => {
    const page = await s.context.newPage();
    try {
      for (const category of categories) {
        const response = await page.goto(h.appUrl(s.config.baseUrl, '/demographic/cdsSourceFields?'
          + new URLSearchParams({demographicNo:id,category})));
        h.assert(response.status() === 200, `${category} source view was refused after completion`);
        const root = page.locator('#cdsSourceFields'); await root.waitFor();
        const count = await root.locator('.cds-source-field').count();
        h.assert(count > 0 && count <= 50, `${category} source values are absent or unbounded`);
        h.assert(!/(?:\/workspace\/|\/var\/lib\/carlos|protectedDetailLocation|reportLocation)/.test(await root.innerText()),
          `${category} source view exposed a physical storage path`);
        const download = root.locator('.cds-source-field a[href*="cdsSourceFieldContent"]').first();
        const href = new URL(await download.getAttribute('href'), page.url());
        h.assert(href.searchParams.get('demographicNo') === id && href.searchParams.get('category') === category
          && href.searchParams.has('fieldId') && !href.searchParams.has('path'), 'Original download is not patient/category/opaque-field scoped');
        if (category === 'Demographics') {
          let noteField = root.locator('.cds-source-field').filter({has:page.locator('h2').filter({hasText:'/NoteAboutPatient[1]'})});
          for (let pages = 0; await noteField.count() === 0 && pages < 10; pages++) {
            const next = root.locator('a.btn-outline-primary[href*="after="]').first();
            h.assert(await next.count() === 1, 'Patient note source value is missing from all bounded pages');
            await page.goto(new URL(await next.getAttribute('href'),page.url()).href);
            h.assert(await root.locator('.cds-source-field').count() <= 50, 'Source pagination exceeded50fields');
            noteField = root.locator('.cds-source-field').filter({has:page.locator('h2').filter({hasText:'/NoteAboutPatient[1]'})});
          }
          h.assert(await noteField.count() === 1, 'Patient note source identity is not unique');
          const download = page.waitForEvent('download'); await noteField.locator('a[href*="cdsSourceFieldContent"]').click();
          const bytes = fs.readFileSync(await (await download).path());
          const expected = Buffer.from('Complete source patient note including literal </unotes> and café 😀');
          h.assert(createHash('sha256').update(bytes).digest('hex') === createHash('sha256').update(expected).digest('hex'),
            'Original logical patient-note download changed bytes or truncated at a literal closing tag');
        }

      }
    } finally { await page.close(); }
  });
  await s.step('Full corpus event/receipt reports reconcile all17 occurrence buckets and immutable manifest', async () => {
    const event = await importApi(frame,'cdsImportReport',{runId:run.job.id,type:'event'});
    // JSONL is intentionally not parsed as a single API object; use an authenticated download.
    h.assert(event.status === 200, 'Corpus event report unavailable');
    const download = admin.waitForEvent('download'); await frame.locator('#eventReport').click();
    const contents = fs.readFileSync(await (await download).path(), 'utf8');
    const rows = contents.trim().split(/\r?\n/).filter(Boolean).map(line => JSON.parse(line));
    h.assert(rows.length === 1 && rows[0].patientId === sourceId && rows[0].inventoryComplete === true && rows[0].held === false,
      'Corpus event receipt omits its patient/copy');
    h.assert(Object.keys(rows[0].importedOccurrences).length === 17
      && Object.values(rows[0].importedOccurrences).every(value => value > 0)
      && Object.values(rows[0].notCommittedOccurrences).every(value => value === 0), 'Corpus occurrence accounting is incomplete');
    const receipt = await importApi(frame,'cdsImportReport',{runId:run.job.id,type:'receipt'});
    h.assert(receipt.body?.manifestDigest === run.job.manifestDigest && receipt.body?.cleanComplete === true,
      'Corpus receipt hash/completeness proof differs from durable status');
  });
  // This complete synthetic chart and its immutable receipts are deliberately retained for native
  // clinician-view/recovery inspection in the disposable reference DB; no clinic data is involved.
}

if (require.main === module) runWorkflow(process.env.CDS_BROWSER_CORPUS === 'true' ? 'demographic-cds-native-views' : 'demographic-cds-import',
  process.env.CDS_BROWSER_CORPUS === 'true' ? nativeViewsWorkflow : workflow, {openPatient:false});
module.exports = {workflow,nativeViewsWorkflow,buildCdsXml,fixtureValues,parseImportLogCounts,assertSchemaValid,
  openImportFrame,importApi,awaitRunReceipt,uploadAndAwaitRun,registerImportCleanup};
