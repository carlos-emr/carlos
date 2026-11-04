#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Exercise the installed shared JAXB provider: JSON-only clinical date DTOs must not
 * prevent unrelated XML resources from answering (issue #5624).
 * Environment: BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(session) {
  const { context, config, patient, provider, schedule, sql, marker } = session;
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

  // Nonempty responses exercise the adapted date values in the installed JSON provider.
  session.cleanup(() => {
    sql.execute(`DELETE FROM allergies WHERE demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient})`) === '0',
    'Owned clinical fixtures were not removed');
  });
  sql.execute(`INSERT INTO allergies (demographic_no,entry_date,start_date,DESCRIPTION,TYPECODE,reaction,
      nonDrug,archived,position,lastUpdateDate,providerNo)
    VALUES (${patient},'2020-02-29','2020-02-29',${h.sqlString(marker)},0,'FAKE rash',1,0,0,NOW(),${h.sqlString(provider)});
    INSERT INTO preventions (demographic_no,creation_date,prevention_date,provider_no,prevention_type,
      deleted,refused,never,creator,lastUpdateDate)
    VALUES (${patient},NOW(),'2020-02-29 00:00:00',${h.sqlString(provider)},'Inf','0','0','0',${h.sqlString(provider)},NOW())`);

  function assertDateInfo(info, valueKey) {
    h.assert(info && info.precision === 'FULL_DATE' && info[valueKey] === '2020-02-29'
      && info.intervalStart === '2020-02-29' && info.intervalEnd === '2020-02-29',
    'Clinical date JSON changed the owned fixture date');
    h.assert(JSON.stringify(Object.keys(info).sort())
      === JSON.stringify(['precision', valueKey, 'intervalStart', 'intervalEnd'].sort()),
    'Clinical date JSON changed its four-field shape');
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
        h.assert(Array.isArray(body[resource]) && body[resource].length === 1,
          `${resource} JSON did not return the owned clinical fixture`);
        const record = body[resource][0];
        const allergy = resource === 'allergies';
        h.assert(String(allergy ? record.demographicNo : record.demographicId) === patient,
          'Clinical JSON returned a different patient');
        h.assert(typeof record[allergy ? 'startDate' : 'preventionDate'] === 'number',
          'Clinical JSON changed the legacy epoch date');
        const info = record[allergy ? 'startDateInfo' : 'preventionDateInfo'];
        if (suffix.endsWith('/precision')) {
          assertDateInfo(info, allergy ? 'value' : 'originalValue');
          if (allergy) assertDateInfo(record.entryDateInfo, 'value');
        } else {
          h.assert(info === null && (!allergy || record.entryDateInfo === null),
            'Legacy clinical JSON gained precision-aware fields');
        }
      });
    }
  }
}

if (require.main === module) runWorkflow('rest-xml', workflow, { openMaster: false });
module.exports = { workflow };
