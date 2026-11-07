#!/usr/bin/env node
/*
 * PR 5705 before/after visual check for the Growth 0-36 month form.
 * Runs inside the deb-docker-validation container (. /root/suite-env.sh first).
 *
 *   PHASE=base|pr  DEMO=<demographic_no>  OUT_DIR=<dir>  [SOURCE_FORM=<id saved in base>]
 *
 * base: open a new form, fill it, save, redisplay, reopen, list.  Writes saved.json.
 * pr:   reopen the base-saved row first (old row read by the new code), list, then
 *       repeat the same new/fill/save/reopen flow and compare stored rows.
 * Synthetic data only.
 */
const h = require('/root/carlos/scripts/lib/playwright-harness');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const PHASE = process.env.PHASE;
const DEMO = process.env.DEMO;
const OUT = process.env.OUT_DIR;
const SOURCE_FORM = process.env.SOURCE_FORM || '';
if (!PHASE || !DEMO || !OUT) throw new Error('PHASE, DEMO and OUT_DIR are required');
fs.mkdirSync(OUT, { recursive: true });

const sql = (q) => execFileSync('mariadb', ['-u', 'root', 'carlos', '-N', '-B', '-e', q]).toString();
const rowJson = (id) => {
  const cols = sql("SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='carlos' AND TABLE_NAME='formGrowth0_36'").trim().split(',');
  const vals = sql(`SELECT ${cols.map((c) => `IFNULL(\`${c}\`,'<NULL>')`).join(',')} FROM formGrowth0_36 WHERE ID=${Number(id)}`).replace(/\n$/, '').split('\t');
  return Object.fromEntries(cols.map((c, i) => [c, vals[i]]));
};

const HEADER = { recordNo: 'R-5705', motherStature: '165', gestationalAge: '39', fatherStature: '180', edc: '' };
const ROWS = [
  ['2025/05/07', '0.0', '3.4', '50.5', '34.5', 'birth'],
  ['2025/06/07', '1.0', '4.5', '54.0', '37.0', 'well baby'],
  ['2025/07/07', '2.0', '5.6', '58.0', '39.0', ''],
  ['2025/09/07', '4.0', '6.4', '62.5', '41.0', 'vaccines'],
  ['2025/11/07', '6.0', '7.3', '66.0', '43.0', '  padded comment '],
  ['2026/02/07', '9.0', '8.2', '70.5', '44.5', 'solids'],
  ['2026/05/07', '12.0', '9.0', '74.0', '45.5', ''],
  ['2026/08/07', '15.0', '9.8', '77.5', '46.2', 'walking'],
];

const formUrl = (id) => `/form/formGrowth0_36?demographic_no=${DEMO}&formId=${id}&provNo=`;

async function shot(page, name) {
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  const file = path.join(OUT, `${name}.png`);
  await page.screenshot({ path: file, fullPage: true });
  console.log(`screenshot ${file}`);
}

async function openForm(context, id) {
  const page = await context.newPage();
  await page.setViewportSize({ width: 1280, height: 900 });
  page.on('dialog', (d) => d.accept());
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const resp = await h.gotoApp(page, h.readConfig().baseUrl, formUrl(id));
  h.assert(resp && resp.status() === 200, `form ${id} answered ${resp && resp.status()}`);
  await h.assertNotErrorPage(page, `Growth form ${id}`);
  return { page, errors };
}

async function readFields(page) {
  return page.evaluate(() => {
    const f = document.forms[0];
    const out = {};
    for (const el of f.elements) if (el.name && el.type !== 'submit' && el.name !== 'CSRF-TOKEN') out[el.name] = el.value;
    return out;
  });
}

async function fill(page) {
  for (const [name, value] of Object.entries(HEADER)) await page.locator(`input[name="${name}"]`).fill(value);
  await page.evaluate((rows) => {
    rows.forEach((r, i) => {
      const n = i + 1;
      const set = (name, v) => { document.forms[0].elements[`${name}_${n}`].value = v; };
      set('date', r[0]); set('age', r[1]); set('weight', r[2]); set('length', r[3]); set('headCirc', r[4]); set('comment', r[5]);
    });
  }, ROWS);
}

async function save(page) {
  const before = Number(sql(`SELECT IFNULL(MAX(ID),0) FROM formGrowth0_36 WHERE demographic_no=${Number(DEMO)}`).trim());
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'load', timeout: 60000 }),
    page.locator('input[type="submit"]').first().click(),
  ]);
  await h.assertNotErrorPage(page, 'Growth form after Save');
  const after = Number(sql(`SELECT IFNULL(MAX(ID),0) FROM formGrowth0_36 WHERE demographic_no=${Number(DEMO)}`).trim());
  h.assert(after > before, `Save stored no new row (max ID ${before} -> ${after})`);
  return after;
}

async function main() {
  const config = h.readConfig();
  const browser = await h.launchBrowser(config);
  const context = await h.newContext(browser, config, { timezoneId: 'America/Toronto' });
  const recorder = h.createRecorder();
  const report = { phase: PHASE, demo: DEMO };
  try {
    const login = await h.login(context, config, recorder);
    await login.close();

    if (PHASE === 'pr') {
      h.assert(SOURCE_FORM, 'SOURCE_FORM (the base-saved ID) is required in the pr phase');
      const source = await openForm(context, SOURCE_FORM);
      await shot(source.page, '05-reopen-saved');
      report.sourceFieldsBeforePrSave = await readFields(source.page);
      report.sourceErrors = source.errors;
      await source.page.close();
      const list = await context.newPage();
      await list.setViewportSize({ width: 1280, height: 900 });
      await h.gotoApp(list, config.baseUrl, `/encounter/ViewFormlist?demographic_no=${DEMO}`);
      await h.assertNotErrorPage(list, 'Forms list');
      await shot(list, '04-formlist');
      await list.close();
    }

    const fresh = await openForm(context, 0);
    await shot(fresh.page, '01-new-form');
    report.newFormFields = await readFields(fresh.page);
    await fill(fresh.page);
    await shot(fresh.page, '02-filled');
    const id = await save(fresh.page);
    report.savedId = id;
    report.redisplayUrl = fresh.page.url().replace(/^https?:\/\/[^/]+/, '');
    await shot(fresh.page, '03-saved-redisplay');
    report.redisplayFields = await readFields(fresh.page);
    report.newFormErrors = fresh.errors;
    await fresh.page.close();

    if (PHASE === 'base') {
      const list = await context.newPage();
      await list.setViewportSize({ width: 1280, height: 900 });
      await h.gotoApp(list, config.baseUrl, `/encounter/ViewFormlist?demographic_no=${DEMO}`);
      await h.assertNotErrorPage(list, 'Forms list');
      await shot(list, '04-formlist');
      await list.close();
    }

    const reopen = await openForm(context, id);
    await shot(reopen.page, PHASE === 'base' ? '05-reopen-saved' : '06-reopen-new');
    report.reopenFields = await readFields(reopen.page);
    await reopen.page.close();

    if (PHASE === 'pr') {
      const list = await context.newPage();
      await list.setViewportSize({ width: 1280, height: 900 });
      await h.gotoApp(list, config.baseUrl, `/encounter/ViewFormlist?demographic_no=${DEMO}`);
      await shot(list, '07-formlist-after-pr-save');
      await list.close();
      report.sourceRowAfterPrSave = rowJson(SOURCE_FORM);
    }
    report.savedRow = rowJson(id);
    fs.writeFileSync(path.join(OUT, 'report.json'), `${JSON.stringify(report, null, 2)}\n`);
    console.log(`PASS growth-visual ${PHASE} saved ID ${id}`);
  } finally {
    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
}

main().catch((e) => { console.error(`FAIL growth-visual: ${e && e.stack || e}`); process.exitCode = 1; });
