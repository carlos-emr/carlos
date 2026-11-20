#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * CSRF negative matrix: a mutation replayed WITHOUT its CSRF token is refused and writes nothing,
 * for each family of state-changing request a clinic makes every day.
 *
 * WHY. CSRFGuard is configured once (Owasp.CsrfGuard.properties) and trusted everywhere; nothing
 * proved that the requests the pages really send are covered by it. A route that a mapping, an
 * unprotected-path rule or a filter change left outside CSRFGuard would accept a cross-site form
 * post riding the clinician's session, and the pages would keep working. So each family's write
 * is performed through the real UI by the logged-in clinician, captured, and replayed by the same
 * session with ONLY the CSRF token removed (header, form parameter, query) -- the X-Requested-With
 * marker, Origin and Referer stay as the page sent them -- and aimed at a new marker so a write
 * would be seen (lib/mutation-replay.js, lib/mutation-families.js). The same replay WITH the token
 * must write: the refusal was about the token, not a malformed request.
 *
 * Asserted, per family (appointment add, update and delete; demographic add and update; Ontario
 * bill save; eform/addEForm; messenger send; consultation request; AddPrevention; measurement
 * save): the UI write lands; the tokenless replay passes h.assertRefused (the application's 403
 * and an unchanged marker-keyed COUNT(*)); the replay with the session's token writes.
 *
 * And for /ws/rs, which CSRFGuard does not cover (unprotected.Rest): its session-cookie mutations
 * are defended only by SameSite=Lax on the session cookie and by accepting JSON alone. The check
 * uses POST /ws/rs/tickler/add, which eForms built with the eForm Generator's tickler option call
 * from the browser with the session cookie (eformGenerator.jsp, the generated `$.ajax` with
 * contentType application/json). It asserts the session cookie is SameSite=Lax (or Strict); that
 * the body sent with the session cookie as application/json and NO CSRF material at all (no
 * CSRF-TOKEN header or parameter, no X-Requested-With: sent by the API client, not by a page whose
 * CSRFGuard script would stamp a token on the XHR) writes one tickler -- the control that the
 * endpoint and the session work and that no token is needed, so the content type is the only
 * difference from the probes; and that the same body sent as application/x-www-form-urlencoded and
 * as multipart/form-data writes nothing, refused by the application with 415 (alsoRefusedBy).
 * text/plain, the third type a cross-site form can send without a preflight, is sent too: behind
 * the packaged front door the WAF answers it before CARLOS is reached, so it is printed as a NOTE
 * (application not reached) and only its unchanged rows are asserted; without the front door it
 * must be the application's 415 like the others.
 *
 * Fixtures: the owned FAKE patient (runWorkflow), an owned eForm template, owned appointments,
 * and every row each family writes, marker-keyed (`<marker>-<family>-<tag>` text or the owned
 * patient / appointment). Cleanup removes them by key and asserts they are gone. No clinic-wide
 * state is changed. Ontario only: the bill family posts the Ontario bill save.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const F = require('./lib/mutation-families');
const R = require('./lib/mutation-replay');

const NAME = 'csrf-negative-matrix';
const REST_TICKLER = '/ws/rs/tickler/add';

// One literal label per family, so a manifest expectedFailure could name the step it fails at.
const STEPS = {
  'appointment-add': 'appointment add: a replay without the CSRF token is refused and writes nothing, and with the token it writes',
  'appointment-update': 'appointment update: a replay without the CSRF token is refused and writes nothing, and with the token it writes',
  'appointment-delete': 'appointment delete: a replay without the CSRF token is refused and deletes nothing, and with the token it deletes',
  'demographic-add': 'demographic add: a replay without the CSRF token is refused and writes nothing, and with the token it writes',
  'demographic-update': 'demographic update: a replay without the CSRF token is refused and writes nothing, and with the token it writes',
  'billing-on-save': 'Ontario bill save: a replay without the CSRF token is refused and writes nothing, and with the token it writes',
  'eform-add': 'eForm submit: a replay without the CSRF token is refused and writes nothing, and with the token it writes',
  'messenger-send': 'messenger send: a replay without the CSRF token is refused and writes nothing, and with the token it writes',
  'consultation-request': 'consultation request: a replay without the CSRF token is refused and writes nothing, and with the token it writes',
  'prevention-add': 'AddPrevention: a replay without the CSRF token is refused and writes nothing, and with the token it writes',
  'measurement-save': 'measurement save: a replay without the CSRF token is refused and writes nothing, and with the token it writes',
};

async function workflow(s) {
  const { sql, config, patient, provider, marker } = s;
  const families = [F.appointmentAdd(s), F.appointmentUpdate(s), F.appointmentDelete(s), F.demographicAdd(s), F.demographicUpdate(s),
    F.billingOnSave(s), F.eformAdd(s), F.messengerSend(s), F.consultationRequest(s), F.preventionAdd(s), F.measurementSave(s)];
  for (const family of families) s.cleanup(() => family.cleanup());
  let token;

  for (const family of families) {
    h.assert(STEPS[family.key], `${family.key} has no step label`);
    await s.step(STEPS[family.key], async () => {
      if (!token) token = await R.sessionToken(s.schedule, config.baseUrl);
      if (family.prepare) await family.prepare();
      const captured = await R.uiWrite(s, family, 'UI');
      const carriers = R.tokenCarriers(captured);
      h.assert(carriers.header || carriers.body || carriers.query, `${family.label}: the page sent no CSRF token at all`);
      const { response, before } = await R.replayAimed(s, family, captured, { tag: 'NT', context: s.context, token: null, write: false });
      const refusal = await h.assertRefused(s, {
        response, table: family.table, where: family.where('NT'), before, label: `${family.label} replayed without its CSRF token`,
      }).catch((error) => error);
      const written = await R.proveReplayWrites(s, family, captured, { tag: 'WT', context: s.context, token, who: 'the session with its token' });
      if (refusal instanceof Error) throw refusal;
      const carried = Object.entries(carriers).filter(([, present]) => present).map(([where]) => where).join('+');
      console.log(`  probe ${NAME}: ${family.key} UI -> HTTP ${captured.status} (token in ${carried}); tokenless replay -> ${refusal.evidence}, `
        + `${family.table} unchanged (${refusal.rows}); with the token -> HTTP ${written}, wrote`);
    });
  }

  const prefix = `${marker}-WS-`;
  const restWhere = (tag) => `demographic_no=${patient} AND message=${h.sqlString(prefix + tag)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM tickler WHERE demographic_no=${patient} AND message LIKE ${h.sqlString(`${prefix}%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient} AND message LIKE ${h.sqlString(`${prefix}%`)}`) === '0',
      'Owned REST ticklers were not removed');
  });

  await s.step('a session-cookie /ws/rs mutation sent form-encoded or multipart (and text/plain) is refused, where the same body as JSON with no token writes', async () => {
    const session = (await s.context.cookies(config.baseUrl.toString())).find((cookie) => cookie.name === 'JSESSIONID');
    h.assert(session, 'The session has no JSESSIONID cookie');
    h.assert(['Lax', 'Strict'].includes(session.sameSite), `The session cookie is SameSite=${session.sameSite}, not Lax or Strict`);
    const url = h.appUrl(config.baseUrl, REST_TICKLER);
    // The body the eForm Generator's tickler script builds (eformGenerator.jsp, ticklerToSend).
    const body = (tag) => JSON.stringify({ demographicNo: patient, message: prefix + tag, taskAssignedTo: provider,
      serviceDate: F.futureDate(30), priority: 'Normal' });

    // Control: the generated eForm's request (application/json), sent with the session cookie and no
    // CSRF material, so the probes below differ from it in their content type alone.
    const before = R.count(sql, 'tickler', restWhere('JS'));
    const control = await s.context.request.fetch(url, {
      method: 'POST', headers: { 'content-type': 'application/json', accept: 'application/json' }, data: body('JS'),
      maxRedirects: 0, failOnStatusCode: false,
    });
    h.assert(control.status() === 200, `Control: the tokenless JSON call answered HTTP ${control.status()}`);
    await R.expectCount(sql, 'tickler', restWhere('JS'), before + 1, 'Control: the tokenless JSON call did not write its tickler');

    const outcomes = [];
    const notes = [];
    for (const [tag, type, send] of [
      ['TP', 'text/plain', { headers: { 'content-type': 'text/plain' }, data: body('TP') }],
      ['FE', 'application/x-www-form-urlencoded', { headers: { 'content-type': 'application/x-www-form-urlencoded' }, data: body('FE') }],
      ['MP', 'multipart/form-data', { multipart: (() => { const form = new FormData(); form.append('ticklerToSend', body('MP')); return form; })() }],
    ]) {
      const rows = String(R.count(sql, 'tickler', restWhere(tag)));
      const response = await s.context.request.fetch(url, { method: 'POST', maxRedirects: 0, failOnStatusCode: false, ...send });
      const text = await response.text().catch(() => '');
      if (tag === 'TP' && config.expectFrontDoor && h.isWafPage(response.status(), text)) {
        // The front door's CRS rejects a text/plain body before CARLOS sees it. That is not the
        // application's refusal and is not counted among them; only the unchanged rows are asserted.
        h.assert(String(R.count(sql, 'tickler', restWhere(tag))) === rows, `${type} to ${REST_TICKLER} changed tickler`);
        notes.push(`${type} -> application not reached (WAF front door, HTTP 403); tickler unchanged`);
        continue;
      }
      const verdict = await h.assertRefused(s, {
        response: { status: response.status(), headers: response.headers(), body: text }, table: 'tickler', where: restWhere(tag),
        before: rows, label: `${type} POST to ${REST_TICKLER}`, alsoRefusedBy: [415],
      });
      outcomes.push(`${type} -> ${verdict.evidence}; tickler unchanged (${verdict.rows})`);
    }
    h.assert(outcomes.length >= 2, 'Fewer than two content types reached the application, so its own refusal was not shown');
    console.log(`  probe ${NAME}: session cookie SameSite=${session.sameSite}; tokenless JSON -> HTTP 200, wrote; refused by the application: ${outcomes.join('; ')}`);
    for (const note of notes) console.log(`  NOTE ${NAME}: ${note}`);
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
