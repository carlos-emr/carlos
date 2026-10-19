#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Hold the real Qty/directions blur request, discard its card, then deliver the
// stale callback. A discarded draft must be handled without a server exception.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { prepareRx } = require('./lib/rx-ui-workflow');
const { openRx, stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');
const { waitUntil, failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { serverLogFromEnvironment, splitLogEvents } = require('./lib/server-log');

const mode = process.env.RX_DISCARD_CALLBACK || 'quantity';
const variants = {
  quantity: { name: 'rx-quantity-delete-race', action: 'updateQty', field: 'quantity', value: '42' },
  instructions: { name: 'rx-instructions-delete-race', action: 'parseInstructions', field: 'instructions', value: '1 tab PO BID x 14 days' },
};

async function workflow(s) {
  h.assert(variants[mode], 'RX_DISCARD_CALLBACK must be quantity or instructions');
  const log = serverLogFromEnvironment();
  if (!log.configured) throw new h.SkipCheck('A readable LAB_UPLOAD_JOURNAL_UNIT or LAB_UPLOAD_SERVER_LOG is required');
  const variant = variants[mode], fixture = await prepareRx(s);
  const control = fixture.seed('untouched control');
  const stored = () => JSON.stringify(s.sql.rows(`SELECT * FROM drugs WHERE demographic_no=${s.patient} ORDER BY drugid`));
  const before = stored(), rx = await fixture.open();
  const key = await stageCustomDrug(rx, s.marker + ' discarded callback');
  // A directions parse uses synchronous XHR in the originating window. The
  // same patient's second Rx window can still discard that pending draft.
  const deleting = await openRx(s, s.patient);
  const card = deleting.locator(`#set_${key}`);
  await card.waitFor({ state: 'visible' });
  const isCallback = request => {
    const params = new URLSearchParams(request.postData() || '');
    return request.method() === 'POST' && /\/rx\/(WriteScript|UpdateScript)$/.test(new URL(request.url()).pathname)
      && params.get('parameterValue') === 'updateDrug' && params.get('action') === variant.action
      && params.get('randomId') === key;
  };
  let release, captured, response, blur, routingError;
  const held = new Promise(resolve => { release = resolve; });
  const pattern = /\/rx\/(WriteScript|UpdateScript)(?:\?|$)/;
  const handler = async route => {
    if (!isCallback(route.request())) return route.continue();
    captured = route.request();
    await held;
    try { await route.continue(); } catch (error) { routingError = error; }
  };
  const logMark = log.mark(), browserMark = failureMark(s.recorder);
  await rx.route(pattern, handler);
  try {
    await s.step('discarding the card is acknowledged while its own calculation request is delayed', async () => {
      await rx.locator(`#${variant.field}_${key}`).fill(variant.value);
      // Do not await the synchronous directions XHR before using the other tab.
      blur = rx.locator(`#${variant.field}_${key}`).blur();
      blur.catch(() => {});
      await waitUntil(() => captured, 'the real field blur request to be held');
      const patientValues = [
        ...new URL(captured.url()).searchParams.getAll('demographicNo'),
        ...new URLSearchParams(captured.postData()).getAll('demographicNo'),
      ];
      h.assert(patientValues.length === 1 && patientValues[0] === s.patient,
        'The delayed callback does not name exactly its owned patient');
      const [deleted] = await Promise.all([
        deleting.waitForResponse(reply => reply.request().method() === 'POST'
          && new URL(reply.url()).pathname.endsWith('/rx/rxStashDelete')
          && new URLSearchParams(reply.request().postData()).get('randomId') === key),
        card.locator("a[onclick^='removePrescribingDrug']").first().click(),
      ]);
      h.assert(deleted.ok(), 'The card deletion was refused');
      await card.waitFor({ state: 'detached' });
      h.assert(stored() === before, 'Discarding the draft changed an existing medication');
    });
    await s.step('the delayed callback cannot restore the discarded card or change clinical rows', async () => {
      const answered = rx.waitForResponse(reply => isCallback(reply.request()));
      release();
      response = await answered;
      await blur;
      h.assert(!routingError, 'The held calculation could not be delivered');
      await response.finished();
      await rx.waitForLoadState('networkidle');
      if ([400, 404, 409].includes(response.status())) {
        consumeExpectedFailure(s.recorder, browserMark, { status: response.status(), path: /\/rx\/(WriteScript|UpdateScript)$/ });
      }
      await rx.reload({ waitUntil: 'domcontentloaded' });
      await rx.locator('#drugProfile[aria-busy="false"]').waitFor({ state: 'attached' });
      h.assert(await rx.locator('[id^="drugName_"]').count() === 0, 'The discarded draft returned after the callback');
      h.assert(stored() === before && s.sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${s.patient}`) === '0',
        'The stale callback changed clinical records');
      h.assert(await rx.locator(`#hidecpp_${control}`).count() === 1, 'The untouched control medication disappeared');
    });
    await s.step('a discarded draft calculation is handled without a server exception', async () => {
      await new Promise(resolve => setTimeout(resolve, 2000));
      const errors = splitLogEvents(log.since(logMark))
        .filter(event => /^(ERROR|FATAL)$/.test(event.level) && /RxWriteScript2Action/.test(event.text)
          && /NullPointerException/.test(event.text));
      h.assert([200, 204, 400, 404, 409].includes(response.status()) && errors.length === 0,
        `The discarded ${mode} callback answered HTTP ${response.status()} and logged ${errors.length} NullPointerException event(s)`);
    });
  } finally {
    release();
    if (blur) await blur.catch(() => {});
    await rx.unroute(pattern, handler);
  }
}

if (require.main === module) runWorkflow(variants[mode]?.name || 'rx-discarded-card-callback', workflow);
module.exports = { workflow };
