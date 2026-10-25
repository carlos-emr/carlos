/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./playwright-harness');
const { authzReadFixture } = require('./authz-read-fixture');
const { signIn } = require('./authz-read-probe');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;

/** The CAISI owner action must apply the same linked-chart message policy as the modern chart. */
async function checkLegacyMessageHandoff(s, { patient, otherPatient, trackEditorModules }) {
  const { sql, marker, provider, config, context } = s;
  h.assert([patient, otherPatient].every(value => /^[1-9]\d*$/.test(String(value))),
    'Legacy message handoff needs two owned patients');
  h.assert(typeof trackEditorModules === 'function', 'Legacy handoff needs the actual editor module tracker');
  for (const id of [patient, otherPatient]) h.assert(sql.value(`SELECT COUNT(*) FROM demographic
    WHERE demographic_no=${id} AND last_name=${q(marker)}`) === '1', 'Legacy message patient ownership changed');
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const messages = [];
  s.cleanup(() => {
    if (!messages.length) return;
    const ids = messages.map(message => message.id).join(',');
    h.assert(sql.value(`SELECT COUNT(*) FROM messagetbl WHERE messageid IN (${ids})
      AND thesubject NOT LIKE ${q(`${marker}%`)}`) === '0', 'Legacy handoff message ownership changed');
    sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${ids});
      DELETE FROM messagelisttbl WHERE message IN (${ids});
      DELETE FROM messagetbl WHERE messageid IN (${ids}) AND thesubject LIKE ${q(`${marker}%`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE messageid IN (${ids}))
      + (SELECT COUNT(*) FROM messagelisttbl WHERE message IN (${ids}))
      + (SELECT COUNT(*) FROM msgDemoMap WHERE messageID IN (${ids}))`) === '0',
    'Legacy handoff messages survived cleanup');
  });
  const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'Legacy handoff needs a current messenger location');
  const makeMessage = (label, linkedPatient, participant) => {
    const body = `${marker}-${label}-clinical-message H&P + é`;
    const id = h.insertId(sql, `INSERT INTO messagetbl
      (thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type)
      VALUES (CURDATE(),CURTIME(),${q(body)},${q(`${marker} ${label}`)},'FAKE-PW sender',
        'FAKE-PW recipient',${q(participant ? provider : '0')},${location},0)`, 'legacy handoff message');
    const message = { id, body };
    messages.push(message);
    if (participant) sql.execute(`INSERT INTO messagelisttbl
      (message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
      VALUES (${id},${q(provider)},'new',${location},0,0)`);
    if (linkedPatient) sql.execute(`INSERT INTO msgDemoMap(messageID,demographic_no)
      VALUES (${id},${linkedPatient})`);
    return message;
  };
  const local = makeMessage('linked-local', patient, true);
  const foreign = makeMessage('foreign-patient', otherPatient, true);
  const unlinked = makeMessage('unlinked-local', null, true);
  const shared = makeMessage('linked-shared', patient, false);
  const vanished = makeMessage('linked-vanished', patient, false);
  // The link check deliberately precedes the message-body lookup. Only a linked
  // message whose body disappeared can reach the typed missing-message response.
  h.assert(sql.value(`DELETE FROM messagetbl WHERE messageid=${vanished.id}
    AND thesubject=${q(`${marker} linked-vanished`)} AND themessage=${q(vanished.body)};
    SELECT ROW_COUNT()`) === '1', 'The owned linked-message disappearance was not staged');
  h.assert(sql.value(`SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${vanished.id}
    AND demographic_no=${patient}`) === '1', 'The vanished message lost its owned patient link');
  const snapshot = id => ({ ...readPatientState(sql, id),
    locks: sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${id} ORDER BY id`) });
  const before = [patient, otherPatient].map(snapshot);
  const assertUnchanged = label => [patient, otherPatient].forEach((id, index) =>
    assertPatientStateUnchanged(before[index], snapshot(id), label));
  const address = (id, providerNo, messageIds) => {
    const query = new URLSearchParams({ demographicNo: String(id), providerNo, curProviderNo: providerNo,
      casetoEncounter: 'true' });
    for (const messageId of messageIds) query.append('msgId', String(messageId));
    return h.appUrl(config.baseUrl, `/encounter/IncomingEncounter?${query}`);
  };
  const nativeEditor = async (browserContext, id, providerNo, messageIds, message) => {
    const page = await browserContext.newPage();
    const modulesLoaded = trackEditorModules(page);
    try {
      const response = await page.goto(address(id, providerNo, messageIds));
      h.assert(response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
        'Allowed legacy message handoff did not render through the application');
      const editor = page.locator('textarea[name="enTextarea"]');
      await editor.waitFor({ state: 'visible' });
      await modulesLoaded('Legacy message handoff editor');
      const text = await editor.inputValue();
      h.assert(message
        ? text.includes(message.body) && !messages.some(item => item !== message && text.includes(item.body))
        : !messages.some(item => text.includes(item.body)),
        'Legacy editor did not retain only its allowed message handoff');
    } finally { await page.close(); }
  };
  const refused = async (browserContext, id, providerNo, messageIds, expected, label) => {
    const response = await browserContext.request.get(address(id, providerNo, messageIds), { maxRedirects: 0 });
    try {
      h.assert(response.status() === expected && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
        `${label}: legacy message handoff answered HTTP ${response.status()}, expected ${expected}`);
      const text = await response.text();
      h.assert(!messages.some(message => text.includes(message.body)), `${label}: refused handoff exposed a message`);
    } finally { await response.dispose(); }
    assertUnchanged(label);
  };
  await s.step('the native CAISI editor accepts linked local and shared messages with canonical repeated identity', async () => {
    await nativeEditor(context, patient, provider, [local.id], local);
    assertUnchanged('Linked local legacy message handoff');
    await nativeEditor(context, patient, provider, [shared.id], shared);
    assertUnchanged('Shared-chart legacy message handoff');
    await nativeEditor(context, patient, provider, [local.id, `0${local.id}`], local);
    assertUnchanged('Canonical repeated legacy message identity');
  });
  await s.step('the legacy handoff refuses foreign and unlinked messages plus malformed or missing identities', async () => {
    await refused(context, patient, provider, [foreign.id], 403, 'Foreign linked message');
    await refused(context, patient, provider, [unlinked.id], 403, 'Unlinked local message');
    for (const ids of [['not-a-message'], ['0'], [local.id, foreign.id], ['', local.id]]) {
      await refused(context, patient, provider, ids, 400, 'Invalid legacy message identity');
    }
    const missing = Number(sql.value('SELECT COALESCE(MAX(messageid),0) FROM messagetbl')) + 100;
    h.assert(Number.isSafeInteger(missing) && missing < 2147483647, 'Missing-message probe key is invalid');
    await refused(context, patient, provider, [missing], 403, 'Unlinked missing legacy message');
    await refused(context, patient, provider, [vanished.id], 404, 'Linked vanished legacy message');
  });
  await s.step('legacy message handoff independently enforces messaging rights and patient record access', async () => {
    const chartRole = fixture.addRole({ _demographic: 'r', _appointment: 'r', _eChart: 'r', '_casemgmt.notes': 'r' });
    const chartOnly = fixture.addLogin(chartRole);
    fixture.joinTestLoginPrograms(chartOnly);
    const chartReader = await signIn(s, chartOnly);
    try {
      await nativeEditor(chartReader.context, patient, chartOnly.providerNo, [], null);
      assertUnchanged('Chart-only legacy positive control');
      await refused(chartReader.context, patient, chartOnly.providerNo, [shared.id], 403,
        'Chart reader without messaging rights');
    } finally { await chartReader.context.close(); }
    const messageRole = fixture.addRole({ _demographic: 'r', _appointment: 'r', _eChart: 'r',
      '_casemgmt.notes': 'r', _msg: 'r' });
    const recordLogin = fixture.addLogin(messageRole);
    fixture.joinTestLoginPrograms(recordLogin);
    fixture.lockPatient(recordLogin, patient, ['_demographic']);
    const recordReader = await signIn(s, recordLogin);
    try {
      await nativeEditor(recordReader.context, otherPatient, recordLogin.providerNo, [foreign.id], foreign);
      assertUnchanged('Unlocked record legacy message control');
      await refused(recordReader.context, patient, recordLogin.providerNo, [shared.id], 403,
        'Record-restricted legacy message reader');
    } finally { await recordReader.context.close(); }
  });
}

module.exports = { checkLegacyMessageHandoff };
