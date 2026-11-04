#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Exercise the installed shared JAXB provider: JSON-only clinical date DTOs must not
 * prevent unrelated XML resources from answering (issue #5624).
 * Environment: BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(session) {
  const { context, config, patient, provider, schedule } = session;
  const probes = [
    { path: `/ws/rs/demographics/${patient}`, accept: 'application/xml', field: 'demographicNo', value: patient },
    { path: '/ws/rs/providerService/providers', accept: 'application/xml' },
    { path: `/ws/rs/providerService/provider/${encodeURIComponent(provider)}`, accept: 'application/xml', field: 'providerNo', value: provider },
    { path: `/ws/rs/providerService/provider/${encodeURIComponent(provider)}`, accept: '*/*', field: 'providerNo', value: provider },
  ];
  for (const probe of probes) {
    await session.step(`XML REST ${probe.path.replace(/\/[^/]+$/, '/resource')} accepts ${probe.accept}`, async () => {
      const response = await context.request.get(h.appUrl(config.baseUrl, probe.path), {
        headers: { Accept: probe.accept }, timeout: 30000,
      });
      h.assert(response.status() === 200, `XML REST answered HTTP ${response.status()}`);
      h.assert(/application\/xml/i.test(response.headers()['content-type'] || ''), 'REST did not return XML');
      const result = await schedule.evaluate(({ xml, field, value }) => {
        const document = new DOMParser().parseFromString(xml, 'application/xml');
        return {
          valid: document.querySelector('parsererror') === null && document.documentElement.localName !== 'html',
          matches: !field || [...document.getElementsByTagName(field)].some(node => node.textContent === String(value)),
        };
      }, { xml: await response.text(), field: probe.field, value: probe.value });
      h.assert(result.valid, 'REST response was not a well-formed XML document');
      h.assert(result.matches, 'XML REST returned a different fixture');
    });
  }

  for (const resource of ['allergies', 'preventions']) {
    for (const suffix of ['active', 'active/precision']) {
      await session.step(`${resource} ${suffix} retains JSON`, async () => {
        const response = await context.request.get(h.appUrl(config.baseUrl,
          `/ws/rs/${resource}/${suffix}?demographicNo=${patient}`), {
          headers: { Accept: 'application/json' }, timeout: 30000,
        });
        h.assert(response.status() === 200, `${resource} JSON answered HTTP ${response.status()}`);
        const body = await response.json();
        h.assert(Array.isArray(body[resource]) && body[resource].length === 0,
          `${resource} JSON changed for the owned empty fixture`);
      });
    }
  }
}

if (require.main === module) runWorkflow('rest-xml', workflow, { openMaster: false });
module.exports = { workflow };
