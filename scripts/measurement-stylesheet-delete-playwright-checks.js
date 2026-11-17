#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Drive the real Administration > Customize Measurements > View All Style Sheet form.
// A competing removal of an owned CSS location leaves the already-open form holding
// a valid mapping selection. Deleting that orphan must not remove a detached entity.
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

async function workflow(s) {
  const {sql, marker} = s;
  const q = h.sqlString;
  const cssName = `${marker}.css`;
  const peerName = `${marker}-peer.css`;
  const group = `${marker} orphan`;
  const peer = `${marker} peer`;
  const owned = () => sql.value(`SELECT
    (SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName IN (${q(group)},${q(peer)})) +
    (SELECT COUNT(*) FROM measurementCSSLocation WHERE location IN (${q(cssName)},${q(peerName)}))`);
  h.assert(owned() === '0', 'Per-run style fixtures already exist');
  s.cleanup(() => {
    sql.execute(`DELETE FROM measurementGroupStyle WHERE groupName IN (${q(group)},${q(peer)});
      DELETE FROM measurementCSSLocation WHERE location IN (${q(cssName)},${q(peerName)})`);
    h.assert(owned() === '0', 'Owned stylesheet fixtures remain after cleanup');
  });
  const css = sql.value(`INSERT INTO measurementCSSLocation(location) VALUES (${q(cssName)}); SELECT LAST_INSERT_ID()`);
  const peerCss = sql.value(`INSERT INTO measurementCSSLocation(location) VALUES (${q(peerName)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(css) && /^[1-9]\d*$/.test(peerCss), 'CSS fixture IDs are invalid');
  sql.execute(`INSERT INTO measurementGroupStyle(groupName,cssID) VALUES (${q(group)},${css}),(${q(peer)},${peerCss})`);
  const snapshot = () => sql.rows(`SELECT groupID,groupName,cssID FROM measurementGroupStyle
    WHERE groupName IN (${q(group)},${q(peer)}) ORDER BY groupID`);
  const initial = JSON.stringify(snapshot());
  const peerInitial = JSON.stringify(snapshot().filter(row => row[1] === peer));

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'stylesheet-administration', timeout: 20000});
  const customize = admin.getByRole('link', {name: 'Customize Measurements', exact: true, includeHidden: true});
  await revealAuditLink(admin, customize, 20000);
  await customize.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const menu = await (await iframe.elementHandle()).contentFrame();
  h.assert(menu, 'Customize Measurements frame is missing');
  const open = async () => {
    const page = await s.popup(admin, menu.getByRole('link', {name: 'View All Style Sheet', exact: true}), 'stylesheet-list');
    await page.waitForLoadState('load');
    await page.locator(`input[name="deleteCheckbox"][value="${css}"]`).check();
    return page;
  };
  const submit = async page => {
    const response = page.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/oscarMeasurements/DeleteMeasurementStyleSheet'));
    await page.getByRole('button', {name: 'Delete', exact: true}).click();
    const result = await response;
    const params = new URLSearchParams(result.request().postData() || '');
    h.assert(params.getAll('deleteCheckbox').join(',') === css, 'Form submitted an unowned selection');
    h.assert(result.status() < 400, `Stylesheet deletion returned HTTP ${result.status()}`);
    await page.waitForLoadState('load');
    await h.assertNotErrorPage(page, 'stylesheet deletion');
    return result;
  };
  await s.step('a stylesheet still referenced by a group is refused without changing either mapping', async () => {
    const page = await open();
    await submit(page);
    h.assert((await page.locator('.action-errors').innerText()).includes(cssName), 'Refusal does not identify the selected stylesheet');
    h.assert(JSON.stringify(snapshot()) === initial, 'Refusal changed a mapping');
    await page.close();
  });

  let postedUrl;
  let postedForm;
  await s.step('an already-open selection removes its orphan mapping without a detached-entity error', async () => {
    const page = await open();
    sql.execute(`DELETE FROM measurementCSSLocation WHERE cssID=${css} AND location=${q(cssName)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementCSSLocation WHERE cssID=${css}`) === '0', 'Competing removal did not occur');
    const result = await submit(page);
    postedUrl = result.url();
    postedForm = Object.fromEntries(new URLSearchParams(result.request().postData() || ''));
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName=${q(group)}`) === '0', 'The orphan mapping survived deletion');
    h.assert(JSON.stringify(snapshot()) === peerInitial, 'Deleting the orphan changed another mapping');
    await page.close();
  });

  await s.step('replayed deletion is harmless and GET/HEAD cannot remove a new orphan mapping', async () => {
    const replay = await s.context.request.post(postedUrl, {form: postedForm});
    h.assert(replay.status() < 400, `Repeated deletion returned HTTP ${replay.status()}`);
    h.assert(JSON.stringify(snapshot()) === peerInitial, 'Repeated deletion changed another mapping');
    sql.execute(`INSERT INTO measurementGroupStyle(groupName,cssID) VALUES (${q(group)},${css})`);
    const before = JSON.stringify(snapshot());
    for (const method of ['GET', 'HEAD']) {
      const response = await s.context.request.fetch(postedUrl, {method, params: {deleteCheckbox: css}, maxRedirects: 0});
      h.assert(response.status() === 405 && response.headers().allow === 'POST', `${method} must return 405 with Allow: POST`);
      h.assert(JSON.stringify(snapshot()) === before, `${method} changed stylesheet mappings`);
    }
  });
}

if (require.main === module) runWorkflow('measurement-stylesheet-delete', workflow, {openPatient: false});
module.exports = {workflow};
