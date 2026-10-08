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
 * the JSON call from a page of the session writes one tickler (control: the endpoint and the
 * session work, and no token is needed); and that the same body sent as text/plain, as
 * application/x-www-form-urlencoded and as multipart/form-data -- the three types a cross-site
 * form can send without a preflight -- writes nothing: the application's 415 (alsoRefusedBy) or,
 * for text/plain behind the packaged front door, the WAF's 403, which is reported as the front
 * door's refusal because CARLOS is never reached (the form-encoded and multipart probes still
 * have to reach CARLOS and be refused by it).
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

  await s.step('a session-cookie /ws/rs mutation sent as text/plain, form-encoded or multipart is refused, where JSON from the page writes', async () => {
    const session = (await s.context.cookies(config.baseUrl.toString())).find((cookie) => cookie.name === 'JSESSIONID');
    h.assert(session, 'The session has no JSESSIONID cookie');
    h.assert(['Lax', 'Strict'].includes(session.sameSite), `The session cookie is SameSite=${session.sameSite}, not Lax or Strict`);
    const url = h.appUrl(config.baseUrl, REST_TICKLER);
    // The body the eForm Generator's tickler script builds (eformGenerator.jsp, ticklerToSend).
    const body = (tag) => JSON.stringify({ demographicNo: patient, message: prefix + tag, taskAssignedTo: provider,
      serviceDate: F.futureDate(30), priority: 'Normal' });

    // Control: the call as the generated eForm makes it, from a page of the session (jQuery $.ajax, JSON).
    const chart = await s.chart();
    const before = R.count(sql, 'tickler', restWhere('JS'));
    const answered = await chart.evaluate(({ target, data }) => new Promise((resolve) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the URL comes from appUrl over the validated base URL and the body is the check's own marker JSON
      if (!window.jQuery) { resolve({ status: 0 }); return; }
      window.jQuery.ajax({ type: 'POST', url: target, dataType: 'json', contentType: 'application/json', data,
        success: (result, text, xhr) => resolve({ status: xhr.status }), error: (xhr) => resolve({ status: xhr.status }) });
    }), { target: url, data: body('JS') });
    h.assert(answered.status === 200, `Control: the JSON call from the chart answered HTTP ${answered.status}`);
    await R.expectCount(sql, 'tickler', restWhere('JS'), before + 1, 'Control: the JSON call from the chart did not write its tickler');

    const outcomes = [];
    for (const [tag, type, send] of [
      ['TP', 'text/plain', { headers: { 'content-type': 'text/plain' }, data: body('TP') }],
      ['FE', 'application/x-www-form-urlencoded', { headers: { 'content-type': 'application/x-www-form-urlencoded' }, data: body('FE') }],
      ['MP', 'multipart/form-data', { multipart: (() => { const form = new FormData(); form.append('ticklerToSend', body('MP')); return form; })() }],
    ]) {
      const rows = String(R.count(sql, 'tickler', restWhere(tag)));
      const response = await s.context.request.fetch(url, { method: 'POST', maxRedirects: 0, failOnStatusCode: false, ...send });
      const text = await response.text().catch(() => '');
      if (tag === 'TP' && config.expectFrontDoor && h.isWafPage(response.status(), text)) {
        // The front door's CRS rejects a text/plain body before CARLOS sees it: a refusal, but not
        // the application's, so it is reported as the front door's and the rows are still compared.
        h.assert(String(R.count(sql, 'tickler', restWhere(tag))) === rows, `${type} to ${REST_TICKLER} changed tickler`);
        outcomes.push(`${type} -> refused by the WAF front door with HTTP 403, CARLOS not reached; tickler unchanged`);
        continue;
      }
      const verdict = await h.assertRefused(s, {
        response: { status: response.status(), headers: response.headers(), body: text }, table: 'tickler', where: restWhere(tag),
        before: rows, label: `${type} POST to ${REST_TICKLER}`, alsoRefusedBy: [415],
      });
      outcomes.push(`${type} -> ${verdict.evidence}; tickler unchanged (${verdict.rows})`);
    }
    console.log(`  probe ${NAME}: session cookie SameSite=${session.sameSite}; JSON from the page -> HTTP 200, wrote; ${outcomes.join('; ')}`);
  });
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
