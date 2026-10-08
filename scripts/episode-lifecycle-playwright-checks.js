#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5: an episode survives create/edit/complete/reopen/delete.
// All writes follow the chart's controls; SQL only seeds, asserts and cleans up.
const { SkipCheck, assert, sqlString, withExpectedDialogs } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

// EctDisplayEpisode2Action renders the chart's Episode module only when one of
// the login's roles (or its provider number) holds a right on this object. The
// seeded `doctor` role holds `o` on it, so on a fresh install the module is
// hidden and the first click below would time out on a menu that is not there:
// a fixture gap reported as an application failure (issue #3682).
const EPISODE_OBJECT = '_newCasemgmt.episode';

/**
 * True when any of these privilege strings satisfies the module's read check.
 *
 * Mirrors OscarRoleObjectPrivilege.checkPrivilege for its default right "r":
 * a stored value is a `|`-separated list, and x, r, u or w each grant read
 * (PRIVILEGE_HIERARCHY "ruw", with x as all). Anything else, `o` included,
 * grants nothing.
 */
function episodeModuleGranted(privileges) {
  return privileges.some((value) => String(value || '').split('|')
    .some((right) => ['x', 'r', 'u', 'w'].includes(right.trim().toLowerCase())));
}

function requireEpisodeModule({ sql, provider }) {
  const privileges = sql.rows(`SELECT p.privilege FROM secObjPrivilege p
    WHERE p.objectName=${sqlString(EPISODE_OBJECT)}
      AND (p.roleUserGroup=${sqlString(provider)}
        OR p.roleUserGroup IN (SELECT role_name FROM secUserRole WHERE provider_no=${sqlString(provider)}))`)
    .map(([privilege]) => privilege);
  if (!episodeModuleGranted(privileges)) {
    throw new SkipCheck(`the test login holds no right on ${EPISODE_OBJECT}, so the chart hides its Episode `
      + 'module. Grant one of its roles r or x on that object for this run (and restore it afterwards); see '
      + 'docs/ui-tests/deb-install-validation.md section 4');
  }
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => sql.execute(`DELETE FROM Episode WHERE demographicNo=${patient}`));
  const chart = await s.chart();
  let editor;
  let id;
  const row = () => chart.locator(`a[onclick*="episode.id=${id}"]`).first();
  const state = () => `SELECT CONCAT(description,'|',status,'|',DATE(startDate),'|',COALESCE(DATE(endDate),''))
    FROM Episode WHERE id=${id} AND demographicNo=${patient}`;
  async function save() {
    const closed = editor.waitForEvent('close', { timeout: 20000 });
    closed.catch(() => {});
    await editor.locator('input[type="submit"]').click();
    await closed;
  }
  // The chart's Episodes list truncates the title and floats the start date over
  // the right of the title link, so the link's centre is under the date. Click
  // the start of the title, which is the part a clinician can see and click.
  async function edit() { editor = await s.popup(chart, row(), 'episode-editor', { position: { x: 4, y: 4 } }); }
  await s.step('empty description is refused without writing', async () => {
    editor = await s.popup(chart, chart.locator('#menuTitleepisode a').first(), 'episode-editor');
    const dialogs = await withExpectedDialogs(editor, () => editor.locator('input[type="submit"]').click());
    assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text === 'Description Required',
      'Empty episode description did not produce the expected validation');
    assert(sql.value(`SELECT COUNT(*) FROM Episode WHERE demographicNo=${patient}`) === '0',
      'Invalid episode was written');
  });
  await s.step('create persists and refreshes the chart without reload', async () => {
    await editor.locator('#description').fill(marker);
    await editor.locator('#startDate').fill('2026-01-02');
    await editor.locator('#startDate').press('Tab');
    await save();
    id = sql.value(`SELECT id FROM Episode WHERE demographicNo=${patient} AND description=${sqlString(marker)}`);
    assert(/^[1-9]\d*$/.test(id), 'Episode save did not create a row');
    await expectValue(sql, state(), `${marker}|Current|2026-01-02|`, 'Episode fields did not persist');
    await row().waitFor({ state: 'visible' });
  });
  await s.step('completing without an end date is refused without changing the stored episode', async () => {
    await edit();
    await editor.locator('select[name="episode.status"]').selectOption('Complete');
    const dialogs = await withExpectedDialogs(editor, () => editor.locator('input[type="submit"]').click());
    assert(dialogs.length === 1 && dialogs[0].type === 'alert' && /end date/i.test(dialogs[0].text),
      'Completed episode without an end date did not show validation');
    await expectValue(sql, state(), `${marker}|Current|2026-01-02|`, 'Invalid completion changed the episode');
    await editor.close();
  });
  await s.step('reopen, edit and complete round-trip', async () => {
    await edit();
    assert(await editor.locator('#description').inputValue() === marker, 'Reopened episode lost its description');
    await editor.locator('#description').fill(`${marker}-EDIT`);
    await editor.locator('#endDate').fill('2026-02-03');
    await editor.locator('#endDate').press('Tab');
    await editor.locator('select[name="episode.status"]').selectOption('Complete');
    await save();
    await expectValue(sql, state(), `${marker}-EDIT|Complete|2026-01-02|2026-02-03`, 'Completed episode lost fields');
    await row().waitFor({ state: 'detached' });
  });
  await s.step('completed episode can be reopened and deleted with history retained', async () => {
    // The module heading's handler sits on the link inside its <h3> (it moved off
    // the <h3> itself in 1c3d1433), so select the link a clinician clicks.
    const list = await s.popup(chart, chart.locator('#episode .nav-menu-title h3 > a[onclick*="/Episode?method=list"]').first(), 'episode-list');
    const link = list.getByRole('link', { name: `${marker}-EDIT`, exact: true });
    await link.click();
    editor = list;
    assert(await editor.locator('select[name="episode.status"]').inputValue() === 'Complete', 'Completed status did not reopen');
    await editor.locator('select[name="episode.status"]').selectOption('Current');
    await save();
    await row().waitFor({ state: 'visible' });
    await edit();
    await editor.locator('select[name="episode.status"]').selectOption('Deleted');
    await save();
    await expectValue(sql, state(), `${marker}-EDIT|Deleted|2026-01-02|2026-02-03`, 'Deleted episode history was lost');
    await row().waitFor({ state: 'detached' });
  });
}
if (require.main === module) runWorkflow('episode-lifecycle', workflow, { preflight: requireEpisodeModule });
module.exports = { EPISODE_OBJECT, episodeModuleGranted, requireEpisodeModule, workflow };
