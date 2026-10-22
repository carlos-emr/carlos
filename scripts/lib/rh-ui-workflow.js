/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./playwright-harness');
const ui = require('./playwright-ui');

// Register the optional form only on the disposable test install. RHWorkFlow's
// queue link looks it up by this catalog name, so a marker name would test a
// different configuration from the one offered to clinicians.
async function prepareRhWorkflow(s) {
  const definition = s.sql.rows("SELECT form_name,hidden FROM encounterForm WHERE form_table='formRhImmuneGlobulin'");
  if (!definition.length) {
    const value = '../form/formRhImmuneGlobulin.jsp?demographic_no=';
    h.assert(s.sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_name='RH Form'
      OR form_value=${h.sqlString(value)}`) === '0', 'The RH catalog name or path is already registered');
    const order = s.sql.value('SELECT COALESCE(MAX(hidden),0)+1 FROM encounterForm');
    s.sql.execute(`INSERT INTO encounterForm(form_name,form_value,form_table,hidden)
      VALUES('RH Form',${h.sqlString(value)},'formRhImmuneGlobulin',${Number(order)})`);
    s.cleanup(() => {
      h.assert(s.sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_name='RH Form'
        AND form_value=${h.sqlString(value)} AND hidden=${Number(order)}`) === '1', 'Owned RH registration changed');
      s.sql.execute(`DELETE FROM encounterForm WHERE form_name='RH Form' AND form_value=${h.sqlString(value)}
        AND hidden=${Number(order)}`);
    });
    definition.push(['RH Form', order]);
  }
  h.assert(definition.length === 1, 'RH catalog registration is ambiguous');
  if (Number(definition[0][1]) <= 0) throw new h.SkipCheck('Enable the RH Form in Administration > Select Forms');
  s.cleanup(() => {
    s.sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id
      WHERE p.demographic_no=${s.patient}; DELETE FROM preventions WHERE demographic_no=${s.patient};
      DELETE FROM formRhImmuneGlobulin WHERE demographic_no=${s.patient};
      DELETE FROM workflow WHERE demographic_no=${h.sqlString(s.patient)};
      DELETE FROM eChart WHERE demographicNo=${s.patient}`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM formRhImmuneGlobulin WHERE demographic_no=${s.patient})
      +(SELECT COUNT(*) FROM workflow WHERE demographic_no=${h.sqlString(s.patient)})
      +(SELECT COUNT(*) FROM preventions WHERE demographic_no=${s.patient})`) === '0', 'RH fixture rows remain');
  });
  const chart = await s.chart();
  async function openNew() {
    await ui.openFormsMenu(chart);
    const page = await s.popup(chart, chart.getByRole('link', { name: definition[0][0], exact: true }), 'rh-form');
    await page.locator('[name="edd"]').waitFor();
    return page;
  }
  async function save(page) {
    const [response] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/form/RHPrevention')),
      page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      page.getByRole('button', { name: 'Save', exact: true }).click(),
    ]);
    h.assert(response.status() === 200, `RH Save answered HTTP ${response.status()}`);
    await page.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(page, 'RH Save');
  }
  const latest = () => s.sql.rows(`SELECT ID,workflowId,comments FROM formRhImmuneGlobulin
    WHERE demographic_no=${s.patient} ORDER BY ID DESC LIMIT 1`)[0];
  const page = await openNew();
  // Date relative to the database avoids an overdue fixture changing the rules
  // and its colour as the suite ages.
  const edd = s.sql.value('SELECT DATE_ADD(CURDATE(),INTERVAL 90 DAY)');
  await page.locator('[name="edd"]').fill(edd);
  await page.locator('[name="comments"]').fill(`${s.marker} first pregnancy`);
  await save(page);
  const row = latest();
  h.assert(row && /^[1-9]\d*$/.test(row[1]), 'RH Save did not create a workflow');
  h.assert(await page.locator('[name="formId"]').inputValue() === row[0], 'RH Save displayed another record');
  return { page, workflowId: row[1], formId: row[0], edd, openNew, save, latest };
}

module.exports = { prepareRhWorkflow };
