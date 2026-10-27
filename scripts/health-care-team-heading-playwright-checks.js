#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Health Care Team heading on the Master Record (issue #4684).
 * displayHealthCareTeam.jsp is embedded in the master record through <jsp:include>, which has its own page
 * scope, and it is also served on its own (view=detached). Its <fmt:setBundle> used to sit inside the
 * detached-only branch, so the embedded heading printed "???demographic.manageHealthCareTeam.heading???".
 * Asserts, in English and in French (the second language, negotiated from Accept-Language):
 *   1. the heading inside the master record's #healthCareTeam block is the translated text, no ???key???;
 *   2. the detached page shows the same translated heading in its title bar and in its body.
 * The panel is read-only here, so the workflow's FAKE- patient needs no contact rows and nothing is written.
 * SKIPs when DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM is off (the master record then renders no panel).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

// oscarResources key demographic.manageHealthCareTeam.heading
const LANGUAGES = [
  { tag: 'en-CA', acceptLanguage: 'en-CA,en;q=0.9', heading: 'Health Care Team' },
  { tag: 'fr-CA', acceptLanguage: 'fr-CA,fr;q=0.9', heading: 'Équipe de soins' },
];
const UNRESOLVED_KEY = /\?\?\?[A-Za-z0-9_.]+\?\?\?/;

async function openInLanguage(s, language, path) {
  const page = await s.context.newPage();
  // The server picks the locale per request from Accept-Language; no session state is involved.
  await page.setExtraHTTPHeaders({ 'Accept-Language': language.acceptLanguage });
  await h.gotoApp(page, s.config.baseUrl, path);
  return page;
}

async function workflow(s) {
  if (!await s.master.locator('#healthCareTeam').count()) {
    throw new h.SkipCheck('Enable DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM=true for the Health Care Team panel');
  }
  // gotoApp prefixes the configured application context (e.g. /carlos) itself, and the captured master record
  // URL already carries it, so hand over the root-relative remainder.
  const masterUrl = new URL(s.master.url());
  const contextPath = s.config.baseUrl.pathname.replace(/\/+$/, '');
  const masterPath = (contextPath && masterUrl.pathname.startsWith(`${contextPath}/`)
    ? masterUrl.pathname.slice(contextPath.length) : masterUrl.pathname) + masterUrl.search;
  const detachedPath = `/demographic/ViewDisplayHealthCareTeam?view=detached&demographicNo=${s.patient}`;

  for (const language of LANGUAGES) {
    await s.step(`${language.tag}: the master record's embedded heading is translated`, async () => {
      const page = await openInLanguage(s, language, masterPath);
      try {
        const title = page.locator('#healthCareTeam #tableTitle');
        await title.waitFor({ state: 'attached' });
        const text = (await title.innerText()).trim();
        h.assert(!UNRESOLVED_KEY.test(text), `The embedded Health Care Team heading is an unresolved key: ${text}`);
        h.assert(text === language.heading, `Embedded heading was "${text}", expected "${language.heading}"`);
      } finally {
        await page.close();
      }
    });

    await s.step(`${language.tag}: the detached view's headings are translated`, async () => {
      const page = await openInLanguage(s, language, detachedPath);
      try {
        const body = page.locator('body#detachedView');
        await body.waitFor();
        const bar = (await page.locator('.MainTableTopRowLeftColumn').innerText()).trim();
        const heading = (await page.locator('#healthCareTeam #tableTitle').innerText()).trim();
        for (const text of [bar, heading]) {
          h.assert(!UNRESOLVED_KEY.test(text), `The detached Health Care Team heading is an unresolved key: ${text}`);
          h.assert(text === language.heading, `Detached heading was "${text}", expected "${language.heading}"`);
        }
        h.assert(!UNRESOLVED_KEY.test(await body.innerText()), 'The detached view prints an unresolved message key');
      } finally {
        await page.close();
      }
    });
  }
}

module.exports = { workflow };
if (require.main === module) runWorkflow('health-care-team-heading', workflow, { openPatient: true });
