#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Manage Faxes queue. User path: Schedule ▸ Administration ▸ Faxes ▸ Manage Faxes (the
 * /administration shell loads admin/ViewManageFaxes into #myFrame; Fetch Faxes posts
 * method=fetchFaxStatus to admin/ManageFaxes and injects the table into #results).
 * No fax provider is configured and nothing is transmitted: the check stages its own SRFax-typed
 * fax_config row (rx-fax-account-fixture, fake account, polling off) because cancel and resend
 * only act on a fax line that has an active account, keeps it INACTIVE except around the three
 * mutating steps, and the owned jobs point at files that do not exist, so even a scheduler cycle
 * during that window fails before any provider call (SRFaxProviderClient reads the file first).
 * Asserts: seeded owned rows (WAITING, ERROR x2, SENT, COMPLETE, inbound RECEIVED) render with
 * type, status, message, timestamp and the right action buttons under the patient filter; the
 * status, date, provider and team filters partition the list exactly like SQL; cancel (confirm)
 * moves WAITING -> CANCELLED; resend (prompt) moves ERROR -> RESENT and queues one WAITING clone
 * with no provider job id; resolve (confirm) moves ERROR -> RESOLVED; a refetch shows the stored
 * statuses; GET and a tokenless POST on the mutators change nothing.
 * Fixtures: faxes rows for the session's FAKE- patient (marker in statusString/recipient) and the
 * fax_config row; cleanup removes them and any FaxClientLog rows and asserts they are gone.
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md §2.6 fax-queue-admin.
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates, typeAutocomplete} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');
const {stageRxFaxAccount, cleanupRxFaxAccount} = require('./rx-fax-account-fixture');

const TIMEOUT = 20000;
const STATUSES = ['RECEIVED', 'SENT', 'COMPLETE', 'ERROR', 'WAITING', 'CANCELLED', 'RESOLVED', 'UNKNOWN', 'RESENT'];
const DESTINATION = '5555550123';

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

async function workflow(s) {
  const {sql, provider, patient, marker, context, config} = s;
  const faxNumber = `555${String(parseInt(marker.slice(-6), 16) % 10000000).padStart(7, '0')}`;
  const day = offset => sql.value(`SELECT DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL ${offset} DAY), '%Y-%m-%d')`);
  const today = day(0);
  // Noon stamps keep every row inside its calendar day for any session/JVM time zone offset.
  const seeds = [
    {key: 'waiting', status: 'WAITING', days: 0, file: `${marker}-eform.pdf`, type: 'EFORM', direction: 'OUT'},
    {key: 'error1', status: 'ERROR', days: 1, file: `${marker}-Consult.pdf`, type: 'CONSULT', direction: 'OUT'},
    {key: 'error2', status: 'ERROR', days: 0, file: `${marker}-prescription.pdf`, type: 'PRESCRIPTION', direction: 'OUT'},
    {key: 'sent', status: 'SENT', days: 3, file: `${marker}-eform2.pdf`, type: 'EFORM', direction: 'OUT'},
    {key: 'complete', status: 'COMPLETE', days: 10, file: `${marker}-eform3.pdf`, type: 'EFORM', direction: 'OUT'},
    {key: 'received', status: 'RECEIVED', days: 8, file: `${marker}-inbound.pdf`, type: 'RECEIVED FAX', direction: 'IN'},
  ];
  let account;
  const ownedIds = () => sql.rows(`SELECT id FROM faxes WHERE demographicNo=${patient}`).map(row => row[0]);
  s.cleanup(() => {
    const ids = ownedIds();
    for (const id of ids) h.assert(/^[1-9]\d*$/.test(id), 'Owned fax row id is invalid');
    if (ids.length) {
      sql.execute(`DELETE FROM FaxClientLog WHERE faxId IN (${ids.join(',')})`);
      h.assert(sql.value(`SELECT COUNT(*) FROM FaxClientLog WHERE faxId IN (${ids.join(',')})`) === '0', 'Owned fax log rows were not removed');
    }
    sql.execute(`DELETE FROM faxes WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM faxes WHERE demographicNo=${patient}`) === '0', 'Owned fax rows were not removed');
    cleanupRxFaxAccount(sql, account);
  });
  // The patient typeahead on this page posts no outofdomain flag, so SearchDemographic only
  // offers patients admitted to one of the provider's programs: admit the owned patient to an
  // owned program the way the chart checks do.
  const programName = `${marker}-faxes`;
  s.cleanup(() => {
    const program = sql.value(`SELECT id FROM program WHERE name=${h.sqlString(programName)}`);
    if (!program) return;
    h.assert(/^[1-9]\d*$/.test(program), 'Owned program id is invalid');
    sql.execute(`DELETE FROM admission WHERE client_id=${patient} AND program_id=${program};
      DELETE FROM program_provider WHERE program_id=${program} AND provider_no=${h.sqlString(provider)};
      DELETE FROM program WHERE id=${program} AND name=${h.sqlString(programName)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE id=${program}`) === '0', 'Owned program was not removed');
  });
  const program = sql.value(`INSERT INTO program
    (facilityId,name,type,maxAllowed,programStatus,transgender,firstNation,alcohol,
     physicalHealth,mentalHealth,housing,exclusiveView,ageMin,ageMax)
    SELECT id,${h.sqlString(programName)},'service',1,'active',0,0,0,0,0,0,'none',0,150
    FROM Facility ORDER BY id LIMIT 1;
    SELECT id FROM program WHERE name=${h.sqlString(programName)}`);
  h.assert(/^[1-9]\d*$/.test(program), 'Owned program could not be created');
  sql.execute(`INSERT INTO program_provider (program_id,provider_no) VALUES (${program},${h.sqlString(provider)});
    INSERT INTO admission (client_id,program_id,provider_no,admission_date,admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)
    VALUES (${patient},${program},${h.sqlString(provider)},NOW(),0,0,'current',NOW())`);
  account = stageRxFaxAccount(sql, faxNumber);
  // The fixture stages the account active; nothing owned is queued yet, so deactivate it
  // until the mutating steps need it (FaxSender only serves active accounts).
  const setActive = active => sql.execute(`UPDATE fax_config SET active=${active ? 1 : 0} WHERE id=${account.id} AND faxNumber=${h.sqlString(faxNumber)}`);
  setActive(false);
  const faxUser = sql.value(`SELECT faxUser FROM fax_config WHERE id=${account.id}`);
  for (const seed of seeds) {
    seed.id = sql.value(`INSERT INTO faxes (filename,faxline,destination,recipient,status,statusString,numPages,stamp,user,jobId,oscarUser,demographicNo,direction)
      VALUES (${h.sqlString(seed.file)},${h.sqlString(faxNumber)},${seed.direction === 'IN' ? "''" : h.sqlString(DESTINATION)},${h.sqlString(marker + ' recipient')},
        ${h.sqlString(seed.status)},${h.sqlString(`${marker} seed ${seed.key}`)},1,${h.sqlString(`${day(seed.days)} 12:00:00`)},${h.sqlString(faxUser)},NULL,
        ${h.sqlString(provider)},${patient},${h.sqlString(seed.direction)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(seed.id), `Fax fixture ${seed.key} was not created`);
  }
  const byKey = key => seeds.find(seed => seed.key === key);
  const stored = id => sql.value(`SELECT CONCAT(status,'|',COALESCE(statusString,''),'|',jobId IS NULL) FROM faxes WHERE id=${id}`);
  const ownedCount = where => sql.value(`SELECT COUNT(*) FROM faxes WHERE demographicNo=${patient} AND ${where}`);

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder: s.recorder, label: 'fax-administration', timeout: TIMEOUT});
  const link = admin.getByRole('link', {name: 'Manage Faxes', exact: true, includeHidden: true});
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const queue = await (await iframe.elementHandle()).contentFrame();
  h.assert(queue, 'The Manage Faxes frame did not load');
  await queue.locator('#reportForm').waitFor({timeout: TIMEOUT});

  const isMutation = method => response => new URL(response.url()).pathname.endsWith('/admin/ManageFaxes')
    && response.request().method() === 'POST' && (response.request().postData() || '').includes(`method=${method}`);
  async function fetchQueue({begin = day(30), end = today, status = '-1', oscarUser = '-1', team = '-1'} = {}) {
    // The pickers allow typing; Tab commits the typed value through flatpickr's blur handler
    // and closes the calendar it opened on focus, which otherwise overlays the buttons.
    for (const [selector, value] of [['#dateBegin', begin], ['#dateEnd', end]]) {
      await queue.locator(selector).fill(value);
      await queue.locator(selector).press('Tab');
    }
    await queue.locator('select[name="status"]').selectOption(status);
    await queue.locator('select[name="oscarUser"]').selectOption(oscarUser);
    await queue.locator('select[name="team"]').selectOption(team);
    const validity = await queue.locator('#reportForm').evaluate(form => ({
      valid: form.checkValidity(),
      dates: [form.dateBegin.value, form.dateEnd.value],
      messages: [form.dateBegin.validationMessage, form.dateEnd.validationMessage],
    }));
    h.assert(validity.valid && validity.dates[0] === begin && validity.dates[1] === end,
      `The search form would not submit (${validity.messages.join(' / ') || 'date values were rewritten'})`);
    await queue.locator('#results table').evaluateAll(tables => tables.forEach(table => table.setAttribute('data-stale', '1')));
    const [response] = await Promise.all([
      admin.waitForResponse(isMutation('fetchFaxStatus'), {timeout: TIMEOUT}),
      queue.locator('#reportForm input[type="submit"]').click(),
    ]);
    h.assert(response.status() === 200, `Fetch Faxes answered HTTP ${response.status()}`);
    const table = queue.locator('#results table#content:not([data-stale])');
    await table.waitFor({timeout: TIMEOUT});
    return table;
  }
  // Every rendered row, keyed by the fax id the Patient cell carries.
  async function renderedRows(table) {
    return table.locator('tbody tr:has([id^="patientName_"])').evaluateAll(trs => trs.map(tr => {
      const cells = [...tr.querySelectorAll('td')];
      return {
        id: cells[2].id.slice('patientName_'.length), type: cells[0].textContent.trim(), status: cells[4].textContent.trim(),
        message: cells[5].textContent.trim(), date: cells[6].textContent.trim(),
        actions: [...tr.querySelectorAll('button')].map(button => button.textContent.trim()),
      };
    }));
  }
  const ownedRendered = async table => (await renderedRows(table)).filter(row => ownedIds().includes(row.id));

  await s.step('the patient filter lists every seeded row with its type, status, message, timestamp and actions', async () => {
    const chosen = await typeAutocomplete(queue, '#autocompletedemo', marker,
      // escapeRegExp quotes all regex metacharacters in the generated fixture marker.
      // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
      {option: new RegExp(escapeRegExp(marker), 'i'), hidden: '#demographic_no', timeout: TIMEOUT});
    h.assert(chosen === patient, 'The patient typeahead did not select the owned patient');
    const rows = await ownedRendered(await fetchQueue());
    h.assert(rows.length === seeds.length, `Expected ${seeds.length} owned rows, the queue rendered ${rows.length}`);
    for (const seed of seeds) {
      const row = rows.find(candidate => candidate.id === seed.id);
      h.assert(row, `Seeded ${seed.key} row is missing from the queue`);
      h.assert(row.type === seed.type && row.status === seed.status && row.message === `${marker} seed ${seed.key}`,
        `Seeded ${seed.key} row rendered a different type, status or message`);
      h.assert(row.date === sql.value(`SELECT DATE_FORMAT(stamp,'%Y-%m-%d %H:%i:%s') FROM faxes WHERE id=${seed.id}`),
        `Seeded ${seed.key} row rendered a different timestamp than it stores`);
      // Resend renders only while some fax account is active; the owned one is not, but a
      // clinic account left active by an operator (or fax-configure) still counts.
      const resend = sql.value('SELECT COUNT(*) FROM fax_config WHERE active=1') !== '0' ? ['resend'] : [];
      const expected = {WAITING: ['view', 'cancel'], SENT: ['view', 'cancel'], ERROR: ['view', ...resend, 'resolve'],
        COMPLETE: ['view', ...resend], RECEIVED: ['view']}[seed.status];
      h.assert(JSON.stringify(row.actions) === JSON.stringify(expected),
        `Seeded ${seed.key} row offers ${row.actions.join('/')}; expected ${expected.join('/')}`);
    }
  });

  await s.step('status, date, provider and team filters partition the owned rows exactly like SQL', async () => {
    for (const status of STATUSES) {
      const rows = await ownedRendered(await fetchQueue({status}));
      const expected = ownedCount(`status=${h.sqlString(status)}`);
      h.assert(String(rows.length) === expected && rows.every(row => row.status === status),
        `Status filter ${status} rendered ${rows.length} owned rows, SQL holds ${expected}`);
    }
    for (const [begin, end] of [[day(1), today], [day(10), day(3)], [day(2), day(2)]]) {
      const rows = await ownedRendered(await fetchQueue({begin, end}));
      const expected = ownedCount(`DATE(stamp) BETWEEN ${h.sqlString(begin)} AND ${h.sqlString(end)}`);
      h.assert(String(rows.length) === expected, `Date filter ${begin}..${end} rendered ${rows.length} owned rows, SQL holds ${expected}`);
    }
    const otherProvider = await queue.locator('select[name="oscarUser"] option').evaluateAll((options, mine) =>
      options.map(option => option.value).find(value => value !== '-1' && value !== mine), provider);
    h.assert(otherProvider, 'The provider filter offers no second provider');
    for (const [oscarUser, expected] of [[provider, ownedCount(`oscarUser=${h.sqlString(provider)}`)], [otherProvider, ownedCount(`oscarUser=${h.sqlString(otherProvider)}`)]]) {
      const rows = await ownedRendered(await fetchQueue({oscarUser}));
      h.assert(String(rows.length) === expected, `Provider filter rendered ${rows.length} owned rows, SQL holds ${expected}`);
    }
    const rows = await ownedRendered(await fetchQueue({team: faxUser}));
    h.assert(String(rows.length) === ownedCount(`user=${h.sqlString(faxUser)}`), 'The team filter did not partition like SQL');
  });

  // Read-only: is the outbound scheduler running at all? Only then can a cycle touch an
  // owned WAITING row while the account is active. Reported, never asserted.
  let schedulerRunning = null;
  const probe = await context.request.get(h.appUrl(config.baseUrl, '/admin/ManageFax'), {params: {method: 'getFaxSchedularStatus'}, maxRedirects: 0});
  try {
    if (probe.status() === 200) {
      const json = await probe.json().catch(() => null);
      if (json && typeof json.isRunning === 'boolean') schedulerRunning = json.isRunning;
    }
  } finally { await probe.dispose(); }
  console.log(`  fax scheduler running: ${schedulerRunning === null ? 'unknown' : schedulerRunning}`);
  setActive(true);
  let table = await fetchQueue();
  const maxSeeded = Math.max(...seeds.map(seed => Number(seed.id)));

  await s.step('cancel on the WAITING row is refused when dismissed and moves it to CANCELLED when confirmed', async () => {
    const waiting = byKey('waiting');
    const cancel = table.locator(`#cancel_${waiting.id}`);
    h.assert(await table.locator(`#resend_${byKey('error1').id}`).count() === 1, 'An active fax account did not reveal the resend control on the ERROR row');
    let posts = 0;
    const record = request => { if (request.method() === 'POST' && (request.postData() || '').includes('method=CancelFax')) posts++; };
    context.on('request', record);
    let dismissed;
    try {
      dismissed = await h.withExpectedDialogs(admin, async () => {
        await cancel.click();
        await admin.waitForLoadState('networkidle', {timeout: TIMEOUT});
      }, {accept: false});
    } finally { context.off('request', record); }
    h.assert(dismissed.length === 1 && dismissed[0].type === 'confirm', 'Cancel did not ask for confirmation exactly once');
    h.assert(posts === 0 && stored(waiting.id) === `WAITING|${marker} seed waiting|1`, 'A dismissed cancel still posted or changed the row');
    let response;
    const accepted = await h.withExpectedDialogs(admin, async () => {
      [response] = await Promise.all([admin.waitForResponse(isMutation('CancelFax'), {timeout: TIMEOUT}), cancel.click()]);
    });
    h.assert(accepted.length === 1 && accepted[0].type === 'confirm', 'The accepted cancel did not raise exactly one confirm');
    h.assert(response.status() === 200 && (await response.json()).success === true, 'CancelFax did not acknowledge success');
    await cancel.waitFor({state: 'detached', timeout: TIMEOUT});
    h.assert((await table.locator(`tr:has(#patientName_${waiting.id}) td`).nth(4).innerText()).trim() === 'CANCELLED', 'The row did not show CANCELLED');
    h.assert(stored(waiting.id) === 'CANCELLED|Cancelled before transmission|1', 'The cancel did not reach the database as a local cancellation');
  });

  let cloneId;
  await s.step('resend on the ERROR row moves it to RESENT and queues one WAITING clone with no provider job id', async () => {
    const error = byKey('error1');
    const resend = table.locator(`#resend_${error.id}`);
    let response;
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      [response] = await Promise.all([admin.waitForResponse(isMutation('ResendFax'), {timeout: TIMEOUT}), resend.click()]);
    }, {promptText: DESTINATION});
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Resend did not prompt for the destination exactly once');
    h.assert(response.status() === 200 && (await response.json()).success === true, 'ResendFax did not acknowledge success');
    await resend.filter({hasText: 're-sent'}).waitFor({timeout: TIMEOUT});
    h.assert(await resend.isDisabled() && await table.locator(`#complete_${error.id}`).count() === 0, 'The resent row still offers its old controls');
    h.assert((await table.locator(`tr:has(#patientName_${error.id}) td`).nth(4).innerText()).trim() === 'RE-SENT', 'The row did not show RE-SENT');
    const clones = sql.rows(`SELECT id,status,jobId IS NULL,oscarUser,faxline,direction,filename,destination,COALESCE(statusString,'')
      FROM faxes WHERE demographicNo=${patient} AND id>${maxSeeded}`);
    h.assert(clones.length === 1, `Resend queued ${clones.length} rows, expected exactly one clone`);
    const [id, cloneStatus, noJobId, oscarUser, faxline, direction, filename, destination, message] = clones[0];
    cloneId = id;
    h.assert(cloneStatus === 'WAITING' && noJobId === '1' && oscarUser === provider && faxline === faxNumber && direction === 'OUT'
      && filename === error.file && destination.endsWith(DESTINATION) && message === `Fax RE-SENT by provider ${provider}`,
    'The queued clone does not carry the expected status, owner, fax line, direction, file, destination or message');
    h.assert(stored(error.id) === `RESENT|Fax RE-SENT as fax id ${cloneId} by provider ${provider}|1`, 'The source row was not marked RESENT with the clone id');
  });

  await s.step('resolve on the second ERROR row moves it to RESOLVED after confirmation', async () => {
    const error = byKey('error2');
    const resolve = table.locator(`#complete_${error.id}`);
    let response;
    const dialogs = await h.withExpectedDialogs(admin, async () => {
      [response] = await Promise.all([admin.waitForResponse(isMutation('SetCompleted'), {timeout: TIMEOUT}), resolve.click()]);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Resolve did not ask for confirmation exactly once');
    h.assert(response.status() === 200, `SetCompleted answered HTTP ${response.status()}`);
    await resolve.waitFor({state: 'detached', timeout: TIMEOUT});
    h.assert((await table.locator(`tr:has(#patientName_${error.id}) td`).nth(4).innerText()).trim() === 'RESOLVED', 'The row did not show RESOLVED');
    h.assert(stored(error.id) === `RESOLVED|${marker} seed error2|1`, 'The resolve did not reach the database');
    setActive(false);
  });

  await s.step('a refetch shows every owned row with its stored status and nothing was handed to a provider', async () => {
    table = await fetchQueue();
    const rows = await ownedRendered(table);
    h.assert(rows.length === seeds.length + 1, `Expected ${seeds.length + 1} owned rows after resend, the queue rendered ${rows.length}`);
    for (const row of rows) {
      h.assert(row.status === sql.value(`SELECT status FROM faxes WHERE id=${row.id}`), `Row ${row.id === cloneId ? 'clone' : row.id} shows a status other than the stored one`);
    }
    const clone = rows.find(row => row.id === cloneId);
    h.assert(clone && clone.type === 'CONSULT' && clone.message === `Fax RE-SENT by provider ${provider}`, 'The clone did not render as the re-sent consult');
    // While the account was active the sender could only fail locally on the missing file.
    const cloneStatus = sql.value(`SELECT status FROM faxes WHERE id=${cloneId}`);
    h.assert(cloneStatus === 'WAITING' || (schedulerRunning === true && cloneStatus === 'ERROR'),
      `The clone is ${cloneStatus}; expected WAITING (or ERROR only when the scheduler is running)`);
    if (cloneStatus !== 'WAITING') console.log('  note: the running fax scheduler failed the clone locally on its missing file');
    h.assert(ownedCount('jobId IS NOT NULL') === '0' && ownedCount(`status IN ('SENT','COMPLETE') AND id>${maxSeeded}`) === '0',
      'An owned job was handed to a fax provider');
    h.assert(sql.value(`SELECT COUNT(*) FROM FaxClientLog WHERE faxId IN (${ownedIds().join(',')})`) === '0', 'Queue administration created fax client log rows');
  });

  await s.step('GET and a tokenless POST on the mutators are refused and change nothing', async () => {
    const sent = byKey('sent');
    const complete = byKey('complete');
    const before = [stored(sent.id), stored(complete.id)].join(';');
    for (const [method, jobId] of [['CancelFax', sent.id], ['ResendFax', complete.id], ['SetCompleted', sent.id]]) {
      const response = await context.request.get(h.appUrl(config.baseUrl, '/admin/ManageFaxes'),
        {params: {method, jobId, faxNumber: DESTINATION}, maxRedirects: 0});
      h.assert(response.status() === 405 && response.headers().allow === 'POST', `GET ${method} was not rejected with Allow: POST`);
    }
    // SetCompleted resolves any row unconditionally (no account or status gate), so only the
    // CSRF gate can stop this POST from turning the SENT row RESOLVED with the account inactive.
    const tokenless = await context.request.post(h.appUrl(config.baseUrl, '/admin/ManageFaxes'),
      {form: {method: 'SetCompleted', jobId: sent.id}, maxRedirects: 0});
    await tokenless.dispose();
    h.assert([stored(sent.id), stored(complete.id)].join(';') === before, 'A POST without a CSRF token changed a fax row');
    h.assert(ownedCount(`id>${maxSeeded}`) === '1', 'A refused request queued a fax');
  });
}
if (require.main === module) runWorkflow('fax-queue-admin', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
