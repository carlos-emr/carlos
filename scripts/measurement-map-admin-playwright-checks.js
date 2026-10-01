#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
//
// Browser check for lab-to-measurement (LOINC) mapping admin.
//
// User path: Schedule ▸ Administration ▸ Customize Measurements (admin iframe) ▸
// View Mapping ▸ map ▸ Add New Loinc Code; View Mapping ▸ map (owned code) ▸ Update
// Measurement Mapping; Add Measurement Mapping (search); Remove/Remap Measurement
// Mapping ▸ REMAP / DELETE.
//
// Asserts the measurementMap rows each save writes: two owned LOINC definitions
// (and a duplicate refused), a FLOWSHEET mapping of a measurement type to the owned
// code, its remap to the second code and every deletion, with the recyclebin rows
// the removals record; plus what View Mapping and the search lists show.
//
// Fixtures: owned X<hex> LOINC codes named with the run marker. The measurement type
// is an existing unmapped type (types are listed from an application cache that a
// SQL seed cannot reach); only the new mapping row referencing it is created.
// Cleanup deletes rows for the owned codes and their recyclebin entries, and asserts
// it. Implements coverage plan §3.7 admin-misc (Customize Measurements ▸ Mappings).
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const ROUTE = '/encounter/oscarMeasurements/';

async function landOn(page, route, act) {
  const landed = page.waitForEvent('framenavigated', {
    timeout: 20000,
    predicate: frame => frame === page.mainFrame() && new URL(frame.url()).pathname.endsWith(ROUTE + route),
  });
  landed.catch(() => {});
  await act();
  await landed;
  await page.waitForLoadState('load');
  await h.assertNotErrorPage(page, route);
}

async function workflow(s) {
  const {sql, marker, provider} = s;
  const q = h.sqlString;
  const hex = marker.slice('FAKE-PW'.length).toUpperCase();
  const codes = [1, 2].map(n => ({loinc: `X${hex.slice(0, 11)}${n}`, name: `${marker} loinc ${n}`}));
  const [first, second] = codes;
  const owned = codes.map(c => q(c.loinc)).join(',');
  const recycled = `table_name='measurementMap' AND (${codes.map(c =>
    `table_content LIKE ${q(`%<loinc_code>${c.loinc}</loinc_code>%`)}`).join(' OR ')})`;
  const mapRows = () => sql.rows(`SELECT loinc_code,ident_code,name,lab_type FROM measurementMap
    WHERE loinc_code IN (${owned}) ORDER BY loinc_code,lab_type,ident_code`);

  s.cleanup(() => {
    sql.execute(`DELETE FROM measurementMap WHERE loinc_code IN (${owned}); DELETE FROM recyclebin WHERE ${recycled}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurementMap WHERE loinc_code IN (${owned}) OR ident_code IN (${owned}))
      + (SELECT COUNT(*) FROM recyclebin WHERE ${recycled})`) === '0', 'Owned measurement mappings were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM measurementMap WHERE loinc_code IN (${owned}) OR ident_code IN (${owned})`) === '0',
    'Per-run LOINC codes already exist');
  // An unmapped type with a resolvable validation: the type list only shows those.
  const [type, display] = sql.rows(`SELECT t.type,t.typeDisplayName FROM measurementType t JOIN validations v ON v.id=t.validation
    WHERE t.type REGEXP '^[A-Z][A-Z0-9]{2,7}$' AND t.typeDisplayName REGEXP '^[A-Za-z][A-Za-z0-9 ]{2,40}$'
      AND t.type NOT IN (SELECT ident_code FROM measurementMap) AND t.type NOT LIKE 'PW%'
    GROUP BY t.type,t.typeDisplayName HAVING COUNT(*)=1 ORDER BY t.type LIMIT 1`)[0] || [];
  if (!type) throw new h.SkipCheck('No unmapped measurement type exists to map');

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'measurement-map-administration', timeout: 20000});
  const customize = admin.getByRole('link', {name: 'Customize Measurements', exact: true, includeHidden: true});
  await revealAuditLink(admin, customize, 20000);
  await customize.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const menu = await (await iframe.elementHandle()).contentFrame();
  h.assert(menu, 'Customize Measurements did not load in the administration frame');
  await menu.getByRole('link', {name: 'View Mapping', exact: true}).waitFor();

  // Customize Measurements opens every link into one window name; close each popup
  // so the next link opens a fresh page rather than reusing it.
  async function open(linkName, label) {
    const since = s.recorder.badResponses.length;
    try {
      const popup = await s.popup(admin, menu.getByRole('link', {name: linkName, exact: true}), label);
      await popup.waitForLoadState('load');
      return popup;
    } catch (error) {
      const refused = s.recorder.badResponses.slice(since).find(entry => entry.resourceType === 'document');
      h.assert(!refused, `${linkName} opened an HTTP ${refused && refused.status} page for ${refused && refused.method} `
        + `${refused && new URL(refused.url).pathname}; the Customize Measurements link cannot reach its page`);
      throw error;
    }
  }
  const codeRow = (page, code) => page.locator('tr').filter({has: page.locator('td', {hasText: new RegExp(`^\\s*${code}\\s*$`)})});
  async function addLoinc(host, code, expected) {
    const popup = await s.popup(host, host.getByRole('button', {name: 'Add New Loinc Code', exact: true}), 'new-loinc-code');
    await popup.locator('input[name="loinc_code"]').fill(code.loinc);
    await popup.locator('input[name="name"]').fill(code.name);
    const closed = expected === 'success' ? popup.waitForEvent('close', {timeout: 20000}) : null;
    // A successful add reloads the opener before the popup closes itself.
    const reloaded = closed && host.waitForEvent('framenavigated', {timeout: 20000, predicate: f => f === host.mainFrame()});
    const dialogs = await h.withExpectedDialogs(popup, async () => {
      if (closed) {
        await popup.getByRole('button', {name: 'Add Loinc Code', exact: true}).click();
        await closed;
      } else {
        await landOn(popup, 'NewMeasurementMap', () => popup.getByRole('button', {name: 'Add Loinc Code', exact: true}).click());
      }
    });
    const message = expected === 'success' ? 'Successfully added loinc code'
      : 'Unable to add code: The specified code already exists in the database';
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text === message,
      `Add New Loinc Code did not answer "${message}" exactly once`);
    if (closed) {
      await reloaded;
      await host.waitForLoadState('load');
    } else await popup.close();
  }

  await s.step('Add New Loinc Code stores two owned codes and refuses a duplicate', async () => {
    const view = await open('View Mapping', 'view-mapping');
    await landOn(view, 'ViewAddMeasurementMap2', () => view.getByRole('link', {name: 'map', exact: true}).first().click());
    await addLoinc(view, first, 'success');
    await addLoinc(view, second, 'success');
    h.assert(JSON.stringify(mapRows()) === JSON.stringify(codes.map(c => [c.loinc, c.loinc, c.name, 'PATHL7'])),
      'The owned LOINC definitions were not stored as code=identifier PATHL7 rows');
    await addLoinc(view, first, 'failedcheck');
    h.assert(mapRows().length === 2, 'A duplicate LOINC code was stored');
    await view.close();
  });

  await s.step('View Mapping lists the owned code and map assigns a measurement type to it', async () => {
    const view = await open('View Mapping', 'view-mapping-owned');
    const row = codeRow(view, first.loinc);
    h.assert(await row.count() === 1, 'View Mapping does not list the owned code exactly once');
    h.assert(await row.getByText(first.name, {exact: true}).count() === 1, 'View Mapping does not describe the owned code');
    await landOn(view, 'ViewAddMeasurementMap2', () => row.getByRole('link', {name: 'map', exact: true}).click());
    h.assert((await view.locator('input[name="loinc_code"]').inputValue()) === first.loinc, 'The map page targets another code');
    await view.locator('select[name="identifier"]').selectOption(`${type},FLOWSHEET,${display}`);
    const dialogs = await h.withExpectedDialogs(view, () => landOn(view, 'AddMeasurementMap',
      () => view.getByRole('button', {name: 'Update Measurement Mapping', exact: true}).click()));
    h.assert(dialogs.length === 1 && dialogs[0].text === 'Successfully updated the measurement mappings',
      'Update Measurement Mapping did not report success exactly once');
    h.assert(JSON.stringify(mapRows().filter(r => r[3] === 'FLOWSHEET')) === JSON.stringify([[first.loinc, type, display, 'FLOWSHEET']]),
      'The FLOWSHEET mapping row does not match the selected type and code');
    await view.close();
  });

  await s.step('View Mapping shows the mapped type against the owned code', async () => {
    const view = await open('View Mapping', 'view-mapping-mapped');
    const row = codeRow(view, first.loinc);
    h.assert((await row.locator('td').first().innerText()).trim() === `${display}: ${type}`,
      'The MEAS column does not show the mapped type');
    h.assert(await row.getByRole('link', {name: 'map', exact: true}).count() === 0, 'A mapped code still offers map');
    h.assert(await codeRow(view, second.loinc).getByRole('link', {name: 'map', exact: true}).count() === 1,
      'The unmapped second code does not offer map');
    await view.close();
  });

  // Every step from here opens a page through a link that currently answers 405.
  await s.step('Add Measurement Mapping offers the owned codes when searched by name', async () => {
    const page = await open('Add Measurement Mapping', 'add-measurement-mapping');
    await page.locator('input[name="searchstring"]').fill(marker);
    await landOn(page, 'ViewAddMeasurementMap', () => page.getByRole('button', {name: 'Search', exact: true}).click());
    const offered = (await page.locator('select[name="loinc_code"] option').allInnerTexts()).map(t => t.trim()).filter(t => t !== 'None Selected');
    h.assert(JSON.stringify(offered) === JSON.stringify(codes.map(c => `${c.loinc} - ${c.name}`)),
      'The code search does not offer exactly the owned codes');
    await page.close();
  });

  await s.step('REMAP moves the type mapping to the second code and records the old row', async () => {
    const page = await open('Remove/Remap Measurement Mapping', 'remove-measurement-mapping');
    const oldId = sql.value(`SELECT id FROM measurementMap WHERE loinc_code=${q(first.loinc)} AND lab_type='FLOWSHEET'`);
    await page.locator('input[name="searchstring"]').fill(display);
    await landOn(page, 'ViewRemoveMeasurementMap', () => page.getByRole('button', {name: 'Search', exact: true}).click());
    const row = page.locator('tr').filter({has: page.locator('td', {hasText: new RegExp(`^\\s*${first.loinc}\\s*$`)})});
    h.assert(await row.count() === 1, 'The owned FLOWSHEET mapping is not listed once');
    const remap = await s.popup(page, row.getByRole('button', {name: 'REMAP', exact: true}), 'remap-measurement-mapping');
    await remap.locator('input[name="searchstring"]').fill(marker);
    await landOn(remap, 'ViewRemapMeasurementMap', () => remap.getByRole('button', {name: 'Search', exact: true}).click());
    await remap.locator('select[name="loinc_code"]').selectOption(second.loinc);
    const closed = remap.waitForEvent('close', {timeout: 20000});
    const dialogs = await h.withExpectedDialogs(remap, async () => {
      await remap.getByRole('button', {name: 'Remap Measurement', exact: true}).click();
      await closed;
    });
    h.assert(dialogs.length === 1 && dialogs[0].text === 'Successfully remapped the measurement', 'Remap did not report success once');
    h.assert(JSON.stringify(mapRows().filter(r => r[3] === 'FLOWSHEET')) === JSON.stringify([[second.loinc, type, display, 'FLOWSHEET']]),
      'The type is not mapped to exactly the second code');
    h.assert(sql.value(`SELECT COUNT(*) FROM recyclebin WHERE table_name='measurementMap' AND keyword=${q(oldId)}
      AND table_content LIKE ${q(`%<loinc_code>${first.loinc}</loinc_code>%`)}`) === '1', 'The remapped row was not recorded in the recycle bin');
    await page.close();
  });

  await s.step('DELETE removes every owned mapping after confirmation and records each', async () => {
    const page = await open('Remove/Remap Measurement Mapping', 'delete-measurement-mapping');
    for (const term of [marker, display]) {
      await page.locator('input[name="searchstring"]').fill(term);
      await landOn(page, 'ViewRemoveMeasurementMap', () => page.getByRole('button', {name: 'Search', exact: true}).click());
      for (;;) {
        const row = page.locator('tr').filter({has: page.locator('td', {hasText: new RegExp(`^\\s*(${codes.map(c => c.loinc).join('|')})\\s*$`)})}).first();
        if (!await row.count()) break;
        const before = mapRows().length;
        const dialogs = await h.withExpectedDialogs(page, () => landOn(page, 'RemoveMeasurementMap',
          () => row.getByRole('button', {name: 'DELETE', exact: true}).click()));
        h.assert(dialogs.map(d => d.text).join('|') === 'Are you sure you want to delete the mapping?|Successfully deleted the mapping',
          'DELETE did not confirm and then report success');
        h.assert(mapRows().length === before - 1, 'DELETE did not remove exactly one owned mapping');
        await page.locator('input[name="searchstring"]').fill(term);
        await landOn(page, 'ViewRemoveMeasurementMap', () => page.getByRole('button', {name: 'Search', exact: true}).click());
      }
    }
    h.assert(mapRows().length === 0, 'Owned mappings remain after DELETE');
    h.assert(sql.value(`SELECT COUNT(*) FROM recyclebin WHERE ${recycled} AND provider_no=${q(provider)}`) === '3',
      'DELETE did not record each removed mapping against the provider');
    await page.close();
  });
}

if (require.main === module) runWorkflow('measurement-map-admin', workflow, {openPatient: false});
module.exports = {workflow};
