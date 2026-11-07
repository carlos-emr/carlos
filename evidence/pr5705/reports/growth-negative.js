#!/usr/bin/env node
/*
 * PR 5705 negative paths: a Growth row ID that belongs to another patient must not be
 * shown or used as a save source.  Patient OWNER owns row SOURCE; patient OTHER is asked
 * for it.  Synthetic data only.  Runs inside the validation container after suite-env.sh.
 */
const h = require('/root/carlos/scripts/lib/playwright-harness');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const OWNER = Number(process.env.OWNER);
const OTHER = Number(process.env.OTHER);
const SOURCE = Number(process.env.SOURCE);
const OUT = process.env.OUT_DIR;
fs.mkdirSync(OUT, { recursive: true });
const sql = (q) => execFileSync('mariadb', ['-u', 'root', 'carlos', '-N', '-B', '-e', q]).toString().trim();
const rowHash = (id) => sql(`SELECT * FROM formGrowth0_36 WHERE ID=${Number(id)}`);
const count = (p) => Number(sql(`SELECT COUNT(*) FROM formGrowth0_36 WHERE demographic_no=${p}`));
const OWNER_MARKERS = ['R-5705', 'padded comment', 'well baby', 'Visual FAKE-GROWTH'];

async function main() {
  const config = h.readConfig();
  const browser = await h.launchBrowser(config);
  const context = await h.newContext(browser, config);
  const report = {};
  try {
    const login = await h.login(context, config, h.createRecorder());
    await login.close();
    const sourceBefore = rowHash(SOURCE);
    const otherBefore = count(OTHER);

    // 1. Load another patient's row through this patient's chart URL.
    const view = await context.newPage();
    await view.setViewportSize({ width: 1280, height: 600 });
    const resp = await h.gotoApp(view, config.baseUrl, `/form/formGrowth0_36?demographic_no=${OTHER}&formId=${SOURCE}`);
    const viewText = await view.locator('body').innerText().catch(() => '');
    const viewHtml = await view.content();
    report.crossPatientLoad = { status: resp && resp.status(), leaked: OWNER_MARKERS.filter((m) => viewHtml.includes(m)), text: viewText.slice(0, 300) };
    await view.screenshot({ path: path.join(OUT, '08-cross-patient-load.png'), fullPage: true });
    h.assert(report.crossPatientLoad.leaked.length === 0, `cross-patient load leaked ${report.crossPatientLoad.leaked}`);
    await view.close();

    // 2. Submit a save for OTHER that names OWNER's row as its source.
    const page = await context.newPage();
    await page.setViewportSize({ width: 1280, height: 600 });
    page.on('dialog', (d) => d.accept());
    await h.gotoApp(page, config.baseUrl, `/form/formGrowth0_36?demographic_no=${OTHER}&formId=0`);
    await h.assertNotErrorPage(page, 'new form for the other patient');
    await page.evaluate((source) => {
      document.forms[0].elements.formId.value = String(source);
      document.forms[0].elements.date_1.value = '2025/06/07';
      document.forms[0].elements.age_1.value = '1.0';
      document.forms[0].elements.weight_1.value = '4.4';
    }, SOURCE);
    const [saveResp] = await Promise.all([
      page.waitForNavigation({ waitUntil: 'load', timeout: 60000 }),
      page.locator('input[type="submit"]').first().click(),
    ]);
    report.crossPatientSave = {
      status: saveResp && saveResp.status(),
      url: page.url().replace(/^https?:\/\/[^/]+/, ''),
      text: (await page.locator('body').innerText().catch(() => '')).slice(0, 300),
      otherRowsBefore: otherBefore, otherRowsAfter: count(OTHER),
      sourceUnchanged: rowHash(SOURCE) === sourceBefore,
    };
    await page.screenshot({ path: path.join(OUT, '09-cross-patient-save.png'), fullPage: true });
    h.assert(report.crossPatientSave.otherRowsAfter === otherBefore, 'a refused cross-patient save still wrote a row');
    h.assert(report.crossPatientSave.sourceUnchanged, 'the source row changed');

    // 3. Positive control: the same patient's own new form still saves.
    await h.gotoApp(page, config.baseUrl, `/form/formGrowth0_36?demographic_no=${OTHER}&formId=0`);
    await page.evaluate(() => { document.forms[0].elements.date_1.value = '2025/06/07'; document.forms[0].elements.age_1.value = '1.0'; });
    await Promise.all([page.waitForNavigation({ waitUntil: 'load', timeout: 60000 }), page.locator('input[type="submit"]').first().click()]);
    await h.assertNotErrorPage(page, 'own save');
    report.ownSave = { otherRowsAfter: count(OTHER), url: page.url().replace(/^https?:\/\/[^/]+/, '') };
    h.assert(report.ownSave.otherRowsAfter === otherBefore + 1, 'own save did not store one row');
    fs.writeFileSync(path.join(OUT, 'negative.json'), `${JSON.stringify(report, null, 2)}\n`);
    console.log(JSON.stringify(report, null, 2));
    console.log('PASS growth-negative');
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
}
main().catch((e) => { console.error(`FAIL growth-negative: ${e && e.stack || e}`); process.exitCode = 1; });
