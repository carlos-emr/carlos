#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection sweep, chart notes written through CaseManagementEntry. User paths:
 *   (a) Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Social History "+" (Add Item) ▸
 *       type ▸ Sign & Save  -> POST CaseManagementEntry?method=issueNoteSave
 *   (b) Master Record ▸ Tickler (tickler/ViewTicklerMain for the patient) ▸ row note icon ▸
 *       type ▸ Save         -> CaseManagementEntry?method=ticklerSaveNote
 *
 * The action enforces POST and patient write access for the complete note-save family;
 * the tickler dialog also submits POST. Each positive save is driven by the page and asserted in casemgmt_note;
 * the captured request is replayed as GET/HEAD with a new stamped text and must answer 405
 * and write nothing. The UI's own verb for (b) and every probe are asserted in the LAST step.
 *
 * Fixtures: two owned synthetic patients, a marker tickler for each, the native chart/tickler
 * notes and one private copied note. Cleanup verifies ownership and removes their clinical rows.
 * Risk sweep "get-reject" (STATE-CHANGING ACTIONS THAT ACCEPT GET).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { captureRequest, replayParams, createLedger } = require('./lib/get-reject-probe');
const { masterRecordLink } = require('./lib/master-record-link');
const { readPatientState } = require('./echart-two-patient-isolation-playwright-checks');
const { csrfToken } = require('./lib/front-door-checks');

const NAME = 'get-reject-chart-notes';
const TICKLER_NOTE_SAVE_FAILURE = 'Unable to save the note. Please try again.';

function assertTicklerFailureDialog(dialogs, variant) {
  h.assert(dialogs.length === 1 && dialogs[0].type === 'alert'
    && dialogs[0].text === TICKLER_NOTE_SAVE_FAILURE,
  `${variant} tickler save failed without exactly one explanatory alert`);
}

/** Attribute only the controlled HTTP 403 and its native error callback. */
function ticklerFailureDiagnostics(entries, {variant, pageUrl, requestUrl, csrfGuardUrl}) {
  // The synchronous XHR's browser diagnostic can point at CSRFGuard's shipping
  // XMLHttpRequest wrapper instead of the endpoint. Its response, body and UI
  // effects have already been proved before this diagnostic is acknowledged.
  const resources = entries.filter(entry => entry.type === 'error'
    && [requestUrl, csrfGuardUrl].includes(entry.location?.url)
    && /^Failed to load resource: the server responded with a status of 403(?: \([^)]*\))?$/.test(entry.text));
  const application = entries.filter(entry => variant === 'main' && entry.type === 'error'
    && entry.location?.url === pageUrl
    && /^\[ticklerMain\] Failed to save note \(HTTP 403\):/.test(entry.text));
  h.assert(resources.length === 1 && application.length === (variant === 'main' ? 1 : 0),
    'The controlled tickler failure emitted missing or ambiguous diagnostics');
  return [...resources, ...application];
}

/** Replay `params` with every occurrence of `from` in a value replaced by `to`. */
function substitute(params, from, to) {
  const out = new URLSearchParams();
  for (const [k, v] of params) out.append(k, v.split(from).join(to));
  return out;
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const ledger = createLedger(NAME);
  const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
  const message = `${marker} get-reject tickler`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient};
      DELETE FROM tickler WHERE demographic_no=${patient} AND message=${q(message)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient})`) === '0', 'Owned notes/issues/tickler were not removed');
  });
  const stamped = text => `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND LOCATE(${q(text)}, note) > 0`;
  const snapshot = () => JSON.stringify(readPatientState(sql, patient));
  let cpp;
  let tickler;
  let otherPatient;
  const cppText = `${marker} CPP social history item`;

  await s.step('E-Chart ▸ Social History ▸ Add Item ▸ Sign & Save stores the item (POST method=issueNoteSave)', async () => {
    const chart = await s.chart();
    const box = chart.locator('#divR1I1');
    await box.waitFor({ state: 'visible' });
    await box.locator('a[title="Add Item"]').first().click();
    await chart.locator('#showEditNote').waitFor({ state: 'visible' });
    await chart.locator('#noteEditTxt').fill(cppText);
    let button = null;
    for (const candidate of await chart.locator('#showEditNote input[type="image"]').all()) {
      if (/sign|save/i.test(await candidate.getAttribute('title') || '')) { button = candidate; break; }
    }
    h.assert(button, 'The CPP item editor offers no Sign/Save button');
    cpp = await captureRequest(chart, url => url.pathname.endsWith('/CaseManagementEntry')
      && url.searchParams.get('method') === 'issueNoteSave', () => button.click());
    h.assert(cpp.status > 0 && cpp.status < 400, `issueNoteSave answered ${cpp.status}`);
    await expectValue(sql, stamped(cppText), '1', 'Sign & Save did not store the Social History item');
  });

  await s.step('the issueNoteSave replayed as GET/HEAD with a new text is recorded', async () => {
    const probeText = `${marker} CPP replayed by GET`;
    await ledger.probe(s, { label: 'CaseManagementEntry?method=issueNoteSave', path: cpp.path,
      // reloadUrl carries the defining issue_code the action parses; its embedded "&cmd=<div>"
      // repaint hint trips the WAF's CRS 932110 on a query string, which would hide what the
      // APPLICATION does with the GET, so the replay keeps reloadUrl without that segment.
      params: replayParams(substitute(cpp.params, cppText, probeText), {
        reloadUrl: (cpp.params.get('reloadUrl') || '').split('&').filter(part => !part.startsWith('cmd=')).join('&') }),
      snapshot, expect405: true });
  });

  await s.step('the other note-save entry points refuse GET and HEAD before touching the chart', async () => {
    for (const operation of ['issueNoteSaveJson', 'ajaxsave', 'saveAndExit', 'save']) {
      await ledger.probe(s, {label: `CaseManagementEntry?method=${operation}`, path: cpp.path,
        params: replayParams(cpp.params, {method: operation, value: `${marker} refused ${operation}`,
          reloadUrl: null}), snapshot, expect405: true});
    }
  });

  const ticklerNo = sql.value(`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
    VALUES(${patient},${q(message)},'A',NOW(),DATE_SUB(CURDATE(),INTERVAL 1 DAY),${q(provider)},'Normal',${q(provider)});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(ticklerNo), 'The owned tickler was not created');
  const ticklerText = `${marker} tickler note`;

  await s.step('Master Record ▸ Tickler ▸ note icon ▸ Save stores a signed note linked to the owned tickler', async () => {
    const list = await s.popup(s.master, masterRecordLink(s.master, '/tickler/ViewTicklerMain').first(), 'tickler-list');
    const row = list.locator('#ticklerResults tbody tr').filter({ hasText: message }).first();
    await row.waitFor({ state: 'visible' });
    await row.locator('a.noteDialogLink').click();
    await list.locator('#tickler_note').waitFor({ state: 'visible' });
    await list.locator('#tickler_note').fill(ticklerText);
    tickler = await captureRequest(list, url => url.pathname.endsWith('/CaseManagementEntry'),
      () => list.locator('#note-form button.btn-primary').click(),
    { method: null });
    h.assert(tickler.params.get('method') === 'ticklerSaveNote', 'The note dialog submitted another operation');
    h.assert(tickler.status === 200, `The tickler note save returned HTTP ${tickler.status}`);
    await expectValue(sql, stamped(ticklerText), '1', 'The note dialog did not store the tickler note');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_link l JOIN casemgmt_note n ON n.note_id=l.note_id
      WHERE l.table_id=${ticklerNo} AND LOCATE(${q(ticklerText)}, n.note) > 0`) === '1', 'The tickler note is not linked to the tickler');
    await list.close();
  });

  await s.step('the ticklerSaveNote replayed as GET/HEAD with a new text is recorded', async () => {
    const probeText = `${marker} tickler note replayed by GET`;
    await ledger.probe(s, { label: 'CaseManagementEntry?method=ticklerSaveNote', path: tickler.path,
      params: replayParams(substitute(tickler.params, ticklerText, probeText)), snapshot, expect405: true });
  });

  await s.step('tickler writes and reads reject another patients tickler or note without copying private history', async () => {
    const B = h.insertId(sql, `INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,
        date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES(${q(marker)},'TicklerBravo','1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW())`,
    'second tickler patient');
    otherPatient = B;
    s.cleanup(() => {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B} AND last_name=${q(marker)}`) === '1',
        'The second tickler patient changed ownership');
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (SELECT note_id FROM casemgmt_note WHERE demographic_no=${B});
        DELETE FROM casemgmt_note WHERE demographic_no=${B};
        DELETE FROM tickler WHERE demographic_no=${B} AND message=${q(message)};
        DELETE FROM demographic WHERE demographic_no=${B} AND last_name=${q(marker)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${B})
        + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${B})
        + (SELECT COUNT(*) FROM tickler WHERE demographic_no=${B})`) === '0', 'Second tickler fixtures remain');
    });
    const otherTickler = h.insertId(sql, `INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
      VALUES(${B},${q(message)},'A',NOW(),CURDATE(),${q(provider)},'Normal',${q(provider)})`, 'second patient tickler');
    const originalNote = sql.value(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient} AND note=${q(ticklerText)}`);
    h.assert(/^[1-9]\d*$/.test(originalNote), 'The saved tickler note is missing or ambiguous');
    const columns = sql.rows('SHOW COLUMNS FROM casemgmt_note').map(([name]) => name).filter(name => name !== 'note_id');
    h.assert(columns.every(name => /^[A-Za-z_][A-Za-z0-9_]*$/.test(name)), 'Unexpected note schema identifier');
    const privateText = `${marker} private second-patient history`;
    const copied = columns.map(name => name === 'uuid' ? 'UUID()' : name === 'demographic_no' ? B
      : name === 'note' || name === 'history' ? q(privateText) : `\`${name}\``);
    const otherNote = h.insertId(sql, `INSERT INTO casemgmt_note (${columns.map(name => `\`${name}\``).join(',')})
      SELECT ${copied.join(',')} FROM casemgmt_note WHERE note_id=${originalNote}`, 'other patient note');
    const token = await csrfToken(s.schedule, s.config.baseUrl);
    const both = () => JSON.stringify([readPatientState(sql, patient), readPatientState(sql, B)]);
    const baseline = both();
    for (const overrides of [{ticklerNo: otherTickler, noteId: ''}, {ticklerNo, noteId: otherNote}]) {
      const fields = replayParams(tickler.params, {...overrides, value: `${marker} refused foreign tickler`, 'CSRF-TOKEN': token},
        {keepEmpty: true});
      const response = await s.context.request.post(new URL(tickler.path, s.config.baseUrl.origin).toString(), {
        data: fields.toString(), headers: {'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': token},
        maxRedirects: 0,
      });
      try {
        h.assert(response.status() === 403 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
          `Cross-patient tickler save was not refused by the application (HTTP ${response.status()})`);
        h.assert(!(await response.text()).includes(privateText), 'A refused tickler save disclosed another patients note');
        h.assert(both() === baseline, 'A refused cross-patient tickler save changed clinical data');
      } finally { await response.dispose(); }
    }

    // A corrupt preexisting association must not turn a tickler read into access to
    // the other patient's note. Keep the exact link key so cleanup removes only it.
    const badLink = h.insertId(sql, `INSERT INTO casemgmt_note_link(note_id,table_name,table_id)
      VALUES(${otherNote},10,${ticklerNo})`, 'cross-patient tickler link');
    s.cleanup(() => sql.execute(`DELETE FROM casemgmt_note_link WHERE id=${badLink} AND note_id=${otherNote}`));
    const linkedBaseline = both();
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/CaseManagementEntry?method=ticklerGetNote&ticklerNo=${ticklerNo}`), {maxRedirects: 0});
    try {
      h.assert(response.status() === 403 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
        `A cross-patient tickler link was not refused (HTTP ${response.status()})`);
      h.assert(!(await response.text()).includes(privateText), 'Tickler lookup disclosed another patients clinical history');
      h.assert(both() === linkedBaseline, 'Refusing a tickler read changed clinical rows');
    } finally {
      await response.dispose();
      sql.execute(`DELETE FROM casemgmt_note_link WHERE id=${badLink} AND note_id=${otherNote}`);
    }

    const list = await s.popup(s.master, masterRecordLink(s.master, '/tickler/ViewTicklerMain').first(), 'tickler-revision');
    try {
      await list.locator('#ticklerResults tbody tr').filter({hasText: message}).first().locator('a.noteDialogLink').click();
      await list.locator('#tickler_note').waitFor({state: 'visible'});
      h.assert(await list.locator('#tickler_note').inputValue() === ticklerText, 'Tickler lookup did not restore its own note');
      const revisedText = `${marker} authorized tickler revision`;
      await list.locator('#tickler_note').fill(revisedText);
      const saved = await captureRequest(list, url => url.pathname.endsWith('/CaseManagementEntry'),
        () => list.locator('#note-form button.btn-primary').click());
      h.assert(saved.status === 200 && saved.params.get('method') === 'ticklerSaveNote',
        'The authorized tickler revision did not submit its POST successfully');
      await expectValue(sql, stamped(revisedText), '1', 'The authorized tickler revision was not persisted');
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
        AND note=${q(revisedText)} AND LOCATE(${q(ticklerText)},history)>0 AND LOCATE(${q(privateText)},history)=0`) === '1',
      'The tickler revision lost its own history or copied another patients history');
    } finally { await list.close(); }
  });

  await s.step('both tickler note dialogs retain failed text and persist it only after an acknowledged retry', async () => {
    h.assert(/^[1-9]\d*$/.test(otherPatient), 'The failure probe needs its second owned patient');
    for (const variant of ['main', 'legacy']) {
      const page = variant === 'main'
        ? await s.popup(s.master, masterRecordLink(s.master, '/tickler/ViewTicklerMain').first(), 'tickler-failed-save')
        : await s.context.newPage();
      try {
        if (variant === 'legacy') {
          const response = await page.goto(h.appUrl(s.config.baseUrl,
            `/tickler/ViewTicklerDemoMain?demoview=${patient}`));
          h.assert(response.status() === 200, `Legacy tickler list returned HTTP ${response.status()}`);
          await h.assertNotErrorPage(page, 'legacy patient tickler list');
          await page.locator(`a[onclick^="openNoteDialog('${patient}','${ticklerNo}')"]`).click();
        } else {
          await page.locator('#ticklerResults tbody tr').filter({hasText: message}).first().locator('a.noteDialogLink').click();
        }
        const editor = page.locator('#tickler_note');
        await editor.waitFor({state: 'visible'});
        const text = `${marker} ${variant} failed note retained H&P + retry`;
        await editor.fill(text);
        const save = variant === 'main' ? page.locator('#note-form button.btn-primary')
          : page.locator('#note-form a[onclick*="saveNoteDialog"]');
        const baseline = JSON.stringify([readPatientState(sql, patient), readPatientState(sql, otherPatient)]);
        const beforeResponses = new Set(s.recorder.badResponses);
        const beforeConsole = new Set(s.recorder.consoleIssues);
        const matcher = address => address.pathname.endsWith('/CaseManagementEntry');
        let injected = 0;
        const fail = route => {
          const request = route.request();
          const fields = new URLSearchParams(request.postData() || '');
          if (request.method() !== 'POST' || fields.get('method') !== 'ticklerSaveNote') return route.continue();
          h.assert(injected++ === 0, 'The tickler failure probe sent more than one save');
          fields.set('demographicNo', otherPatient);
          return route.continue({postData: fields.toString()});
        };
        await page.route(matcher, fail);
        let failed;
        try {
          const refused = page.waitForResponse(response => response.request().method() === 'POST'
            && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
            && new URLSearchParams(response.request().postData() || '').get('method') === 'ticklerSaveNote');
          refused.catch(() => {});
          const dialogs = await h.withExpectedDialogs(page, async () => {
            failed = await captureRequest(page, address => address.pathname.endsWith('/CaseManagementEntry'), () => save.click());
            const response = await refused;
            h.assert(Object.hasOwn(response.headers(), h.APPLICATION_HEADER)
              && !h.isWafPage(response.status(), await response.text()),
            'The tickler failure probe did not reach the application');
            await editor.waitFor({state: 'visible'});
          });
          h.assert(injected === 1 && failed.status === 403 && failed.method === 'POST',
            `${variant} tickler failure did not reach its patient ownership guard (HTTP ${failed?.status})`);
          assertTicklerFailureDialog(dialogs, variant);
          h.assert(await editor.inputValue() === text && await editor.isVisible(),
            `${variant} tickler failure closed or cleared the clinical editor`);
          h.assert(JSON.stringify([readPatientState(sql, patient), readPatientState(sql, otherPatient)]) === baseline,
            `${variant} failed tickler save changed clinical data`);
          const responses = s.recorder.badResponses.filter(entry => !beforeResponses.has(entry)
            && entry.method === 'POST' && entry.status === 403
            && new URL(entry.url).pathname === failed.path);
          h.assert(responses.length === 1, 'The controlled tickler failure was not recorded exactly once');
          const diagnostics = ticklerFailureDiagnostics(
            s.recorder.consoleIssues.filter(entry => !beforeConsole.has(entry)), {
              variant, pageUrl: page.url(), requestUrl: responses[0].url,
              csrfGuardUrl: h.appUrl(s.config.baseUrl, '/csrfguard'),
            });
          s.recorder.badResponses = s.recorder.badResponses.filter(entry => !responses.includes(entry));
          s.recorder.consoleIssues = s.recorder.consoleIssues.filter(entry => !diagnostics.includes(entry));
        } finally { await page.unroute(matcher, fail); }
        const retried = await captureRequest(page, address => address.pathname.endsWith('/CaseManagementEntry'), () => save.click());
        h.assert(retried.method === 'POST' && retried.status === 200,
          `${variant} tickler retry did not succeed (HTTP ${retried.status})`);
        await expectValue(sql, stamped(text), '1', `${variant} retry did not save the retained text`);
        await editor.waitFor({state: 'hidden'});
      } finally { await page.close(); }
    }
  });

  await s.step('chart-note writes refuse GET/HEAD, and the tickler note dialog itself saves by POST', async () => {
    const problems = [];
    try { ledger.assertAllRefused(); } catch (error) { problems.push(error.message); }
    if (tickler.method !== 'POST') {
      problems.push(`the tickler note dialog itself saved the note with a ${tickler.method} (ticklerMain.jsp saveNoteDialog() `
        + 'jQuery.ajax has no type), so the write cannot be made POST-only without fixing the page');
    }
    h.assert(!problems.length, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow, assertTicklerFailureDialog, ticklerFailureDiagnostics };
