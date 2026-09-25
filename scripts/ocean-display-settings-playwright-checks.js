#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Administration > eChart Display Settings (2026.08.0-alpha14, Ocean toolbar integration).
 *
 * The page owns one system-wide toggle, SystemPreferences.echart_show_ocean, which gates the
 * #ocean_placeholder div the Ocean toolbar script attaches to at the bottom of the encounter
 * (newCaseManagementView.jsp). The default when the row is absent is ON, matching OSCAR19.
 * This check drives the page the way an administrator does -- Administration panel, the
 * eChart section link, the switch, Save -- and proves each save reaches the database and
 * changes what the next chart render emits, in both directions. It also proves the action
 * refuses a GET carrying the save intent, so a bookmarked or prefetched URL cannot flip the
 * setting (the GET/HEAD rejection contract for mutator 2Actions).
 *
 * Owned state: the check records the pre-existing preference row (or its absence) and the
 * OceanSetting singleton and restores both in its cleanup, so a run leaves the install as it
 * found it. It opens one FAKE- patient of its own (runWorkflow) to render the encounter.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const PREF = 'echart_show_ocean';
const PREF_QUERY = `SELECT \`value\` FROM SystemPreferences WHERE name='${PREF}' ORDER BY id LIMIT 1`;
const PREF_COUNT = `SELECT COUNT(*) FROM SystemPreferences WHERE name='${PREF}'`;

async function openSettings(s) {
  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'ocean-admin' });
  const link = admin.locator('a[rel$="/admin/EchartDisplaySettings"], a[href$="/admin/EchartDisplaySettings"]');
  await link.first().waitFor({ state: 'attached' });
  const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
  if (await panel.count() && !await panel.isVisible()) await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
  await link.first().click();
  const frame = await (await admin.locator('#dynamic-content iframe').elementHandle()).contentFrame();
  h.assert(frame, 'eChart Display Settings did not open in the Administration iframe');
  await frame.locator('#echart_show_ocean').waitFor({ state: 'visible' });
  return { admin, frame };
}

async function save(s, frame, enabled) {
  const box = frame.locator('#echart_show_ocean');
  if (await box.isChecked() !== enabled) await box.click();
  h.assert(await box.isChecked() === enabled, 'The Ocean switch did not take the requested state');
  await Promise.all([
    frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
    frame.locator('input[name="saveEchartDisplaySettings"]').click(),
  ]);
  await expectValue(s.sql, PREF_QUERY, String(enabled), `Saving Ocean display=${enabled} did not reach SystemPreferences`);
  h.assert(await frame.locator('#echart_show_ocean').isChecked() === enabled, 'The page re-rendered with the wrong switch state after Save');
  h.assert(await frame.locator('span', { hasText: /saved/i }).count() >= 1, 'Save did not confirm itself on the page');
  h.assert(s.sql.value(PREF_COUNT) === '1', 'Saving the Ocean preference did not keep a single SystemPreferences row');
  h.assert(s.sql.value('SELECT COUNT(*) FROM OceanSetting WHERE id=1') === '1', 'Saving did not create the OceanSetting singleton');
}

/**
 * Renders the encounter afresh and counts #ocean_placeholder.
 *
 * A fresh page, not chart.reload(): leaving the encounter fires its unload beacon to
 * CaseManagementEntry, which the browser reports as an aborted "ping" request and the strict
 * page recorder treats as a failure. The pages stay open until the browser closes.
 */
async function placeholderCount(s) {
  const chart = await s.chart();
  const page = await s.context.newPage();
  await page.goto(chart.url(), { waitUntil: 'domcontentloaded' });
  await page.locator('#notCPP').waitFor({ state: 'attached' });
  const count = await page.locator('#ocean_placeholder').count();
  const hidden = count === 0 || !await page.locator('#ocean_placeholder').isVisible();
  return { count, hidden };
}

async function workflow(s) {
  const originalCount = s.sql.value(PREF_COUNT);
  const originalValue = originalCount === '0' ? null : s.sql.value(PREF_QUERY);
  const hadSingleton = s.sql.value('SELECT COUNT(*) FROM OceanSetting WHERE id=1') === '1';
  s.cleanup(() => {
    if (originalValue === null) {
      s.sql.execute(`DELETE FROM SystemPreferences WHERE name='${PREF}'`);
    } else {
      s.sql.execute(`UPDATE SystemPreferences SET \`value\`=${h.sqlString(originalValue)} WHERE name='${PREF}'`);
    }
    if (!hadSingleton) s.sql.execute('DELETE FROM OceanSetting WHERE id=1 AND settings IS NULL');
    h.assert(s.sql.value(PREF_COUNT) === (originalValue === null ? '0' : originalCount), 'Ocean preference restore failed');
  });

  const { admin, frame } = await openSettings(s);
  await s.step('the settings page reflects the stored preference (absent row means ON)', async () => {
    const expected = originalValue === null || originalValue === 'true';
    h.assert(await frame.locator('#echart_show_ocean').isChecked() === expected,
      `The Ocean switch shows ${!expected} but the database says ${expected}`);
  });
  await s.step('turning Ocean off persists and removes the encounter placeholder', async () => {
    await save(s, frame, false);
    h.assert((await placeholderCount(s)).count === 0, 'The encounter still renders #ocean_placeholder with Ocean turned off');
  });
  await s.step('turning Ocean on persists and restores the hidden placeholder', async () => {
    await save(s, frame, true);
    const rendered = await placeholderCount(s);
    h.assert(rendered.count === 1, 'The encounter does not render #ocean_placeholder with Ocean turned on');
    h.assert(rendered.hidden, 'The Ocean placeholder must stay hidden until the toolbar script shows it');
  });
  await s.step('a GET carrying the save intent is refused and changes nothing', async () => {
    const response = await s.context.request.get(`${s.config.baseUrl}/admin/EchartDisplaySettings?dboperation=Save&${PREF}=false`,
      { maxRedirects: 0, failOnStatusCode: false });
    h.assert(response.status() === 405, `Save-intent GET answered ${response.status()}, expected 405`);
    h.assert(s.sql.value(PREF_QUERY) === 'true', 'A save-intent GET changed the stored preference');
  });
  await admin.close();
}

if (require.main === module) runWorkflow('ocean-display-settings', workflow);
module.exports = { workflow };
