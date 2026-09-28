/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const h = require('./playwright-harness');

/**
 * Two separately authenticated providers, owned patient/document fixtures, no persisted edits.
 * An active UI-only fax account must be available to the cloned provider roles so the
 * FaxDocument denial reaches patient authorization. No fax send is performed here.
 */
async function checkAnnotationSessions(browser, config, fixtureFile) {
  const fixture = JSON.parse(fs.readFileSync(fixtureFile, 'utf8'));
  for (const key of ['allowedDocumentId', 'deniedDocumentId', 'deniedDemographicNo', 'providerNo']) {
    h.assert(/^[1-9]\d*$/.test(String(fixture[key])), `Invalid annotation fixture ${key}`);
  }
  h.assert(typeof fixture.user === 'string' && fixture.user !== config.testUser,
    'Multi-user annotation validation requires a different provider login');
  h.assert(typeof fixture.password === 'string' && typeof fixture.pin === 'string', 'Missing private second-user credentials');
  const count = Number(process.env.ANNOTATION_SESSION_COUNT || '2');
  h.assert(Number.isInteger(count) && count >= 2 && count <= 8, 'ANNOTATION_SESSION_COUNT must be an integer from 2 to 8');
  const timeout = 120000;
  const contexts = [];
  const sessions = [];
  let activeTotal = 0;
  let peakTotal = 0;
  try {
    const second = {...config, testUser: fixture.user, testPassword: fixture.password, testPin: fixture.pin};
    const users = Array.from({length: count}, (_, index) => index % 2 ? second : config);
    const documents = users.map((user, index) => index % 2 ? fixture.allowedDocumentId : fixture.deniedDocumentId);
    for (let index = 0; index < users.length; index++) {
      const context = await h.newContext(browser, users[index]);
      context.setDefaultTimeout(timeout);
      context.setDefaultNavigationTimeout(timeout);
      contexts.push(context);
      const page = await h.login(context, users[index], h.createRecorder());
      const session = {page, document: Number(documents[index]), active: new Set(), peak: 0, failures: [], busy: 0};
      sessions.push(session);
      const resource = request => /\/DocumentTextBoxes\?|method=showPage/.test(request.url());
      page.on('request', request => {
        if (!resource(request)) return;
        const url = new URL(request.url());
        const requestedDocument = url.searchParams.get('docId') || url.searchParams.get('doc_no');
        if (requestedDocument !== String(session.document)) session.failures.push('Cross-document request');
        session.active.add(request);
        activeTotal++;
        session.peak = Math.max(session.peak, session.active.size);
        peakTotal = Math.max(peakTotal, activeTotal);
      });
      const completed = request => { if (session.active.delete(request)) activeTotal--; };
      page.on('requestfinished', completed);
      page.on('requestfailed', request => { completed(request); if (resource(request)) session.failures.push('Transport failure'); });
      page.on('response', response => {
        if (!resource(response.request())) return;
        if (response.status() === 503) session.busy++;
        else if (response.status() >= 400) session.failures.push(`HTTP ${response.status()}`);
      });
    }
    // One Playwright workload deliberately overlaps independent sessions. Each browser's
    // queue must remain bounded while both share the server's unchanged global budget.
    await Promise.all(sessions.map(async session => {
      await session.page.goto(h.appUrl(config.baseUrl, `/documentManager/AnnotateDocument?docId=${session.document}`),
        {waitUntil: 'domcontentloaded'});
      await session.page.waitForFunction(() => document.querySelector('.page img')?.naturalWidth > 0);
      await session.page.locator('.tool[data-tool="highlight"]').click();
      await session.page.locator('.page').last().evaluate(element => element.scrollIntoView({block: 'start'}));
      await session.page.waitForFunction(() => {
        const last = [...document.querySelectorAll('.page')].at(-1);
        return last.querySelector('img').naturalWidth > 0 || last.classList.contains('load-failed');
      });
      await session.page.waitForLoadState('networkidle');
      h.assert(await session.page.locator('.page img').last().evaluate(image => image.naturalWidth > 0),
        'Concurrent viewer failed to render its last page');
      h.assert(await session.page.evaluate(() => window.CARLOS_ANNOTATE.docId) === session.document,
        'Concurrent viewer changed document identity');
      h.assert(session.peak <= 4 && session.failures.length === 0,
        `Concurrent viewer request failure: ${JSON.stringify({peak: session.peak, failures: session.failures})}`);
    }));
    const cookies = await Promise.all(contexts.map(context => context.cookies()));
    const ids = cookies.map(jar => jar.find(cookie => cookie.name === 'JSESSIONID')?.value);
    h.assert(ids.every(Boolean) && new Set(ids).size === count, 'Providers did not receive independent sessions');
    h.assert(peakTotal <= 4 * count, 'Concurrent viewers exceeded their combined request budget');

    // The first provider has warmed the denied patient's rendered-page cache. A second
    // provider's chart restriction must still precede image/text reads, Split metadata,
    // annotation opening and fax staging. The active UI-only fax account prerequisite
    // prevents the earlier no-account refusal from hiding a missing patient guard.
    const forbidden = [
      ['cached image', `/documentManager/ManageDocument?method=showPage&doc_no=${fixture.deniedDocumentId}&page=1&dpi=96`],
      ['text boxes', `/documentManager/DocumentTextBoxes?docId=${fixture.deniedDocumentId}&page=1`],
      ['annotation viewer', `/documentManager/AnnotateDocument?docId=${fixture.deniedDocumentId}`],
      ['Split viewer', `/oscarMDS/ViewSplit?document=${fixture.deniedDocumentId}&queueID=0&demoName=`],
      ['fax handoff', `/documentManager/FaxDocument?docId=${fixture.deniedDocumentId}`],
    ];
    for (const [label, endpoint] of forbidden) {
      const response = await contexts[1].request.get(h.appUrl(config.baseUrl, endpoint), {maxRedirects: 0});
      h.assert(response.status() === 403, `Denied patient ${label} must return 403 directly; received ${response.status()}`);
      await response.body();
    }
    // Unsaved annotations belong to their own page and session; nothing is filed here.
    const first = sessions[0].page;
    const overlay = first.locator('.page').last().locator('svg.overlay');
    const box = await overlay.boundingBox();
    await first.mouse.move(box.x + 80, box.y + 220);
    await first.mouse.down();
    await first.mouse.move(box.x + 190, box.y + 240, {steps: 8});
    await first.mouse.up();
    h.assert(await first.locator('#markCount').textContent() === '1', 'First provider could not place its own annotation');
    h.assert(await sessions[1].page.locator('#markCount').textContent() === '0', 'Unsaved annotation crossed provider sessions');
    return {independentProviders: true, independentSessions: true, deniedCachedReads: forbidden.length,
      sessionCount: count, peakTotal, sessions: sessions.map(({peak, busy}) => ({peak, busy}))};
  } finally {
    const closed = await Promise.allSettled(contexts.map(context => context.close()));
    const failures = closed.filter(result => result.status === 'rejected').map(result => result.reason);
    if (failures.length) throw new AggregateError(failures, 'Could not close annotation test sessions');
  }
}
module.exports = {checkAnnotationSessions};
