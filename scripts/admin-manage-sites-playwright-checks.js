#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Satellite-site administration, entered the way an administrator enters it:
 *   Schedule ▸ Administration ▸ Satellite-sites Admin (admin/ManageSites, in the shell's iframe)
 *   ▸ Add New Site (POST method=add) ▸ Save (POST method=save) ▸ the site's name in the list
 *   (method=update&siteId=, or method=edit once #4700's release fix is forward-merged)
 *   ▸ change the telephone ▸ Save.
 * Asserts the owned `site` row after each save (name, short name, colour, active flag, telephone,
 * province), the list showing the new site, the edit page showing the stored values, the edit
 * updating that row rather than adding one, and that GET with a save flag is refused and stores
 * nothing.
 * The menu entry exists only with multisites=on (admin.jsp and administration/leftNav.jspf);
 * without it the check SKIPs, as on the Ontario and BC demo installs (multisites=off).
 * Finding 276 (#4700, fixed by #4875): Save used to fail because the detail form posted site.* fields
 * that SitesManage2Action did not bind, the list's name link was a GET with method=update that the
 * GET guard refuses, and the edit form showed no stored values.
 * Known noise: sitesAdmin.jsp and sitesAdminDetail.jsp link `${request.contextPath}/css/displaytag.css`,
 * which renders as the root-relative /css/displaytag.css and 404s (finding 100). Those entries are
 * set aside as each step ends and reported in the last step, so they cannot hide another error.
 * Fixtures: one owned site named with the run marker, removed in cleanup (with any providersite
 * link to it) and verified gone. No other site is read or changed.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow} = require('./lib/workflow-session');

const TIMEOUT = 20000;
const SECTION = '/admin/ManageSites';
// `${request.contextPath}` is not an EL implicit object, so the link loses the context path (finding 100).
const MISSING_STYLESHEET = '/css/displaytag.css';

/** Move the finding-100 stylesheet 404 entries out of the strict recorder into `deferred`. */
function deferMissingStylesheet(recorder, deferred) {
  const isMissing = url => { try { return new URL(url).pathname === MISSING_STYLESHEET; } catch { return false; } };
  const take = (list, predicate) => {
    for (let i = list.length - 1; i >= 0; i--) if (predicate(list[i])) deferred.push(...list.splice(i, 1));
  };
  take(recorder.requestFailures, entry => entry.resourceType === 'stylesheet' && isMissing(entry.url));
  take(recorder.badResponses, entry => entry.resourceType === 'stylesheet' && isMissing(entry.url));
  take(recorder.consoleIssues, entry => entry.location && isMissing(entry.location.url)
    && /Failed to load resource/.test(entry.text));
}

async function workflow(s) {
  const {sql, marker, context, recorder} = s;
  const q = h.sqlString;
  const shortName = `PW${marker.slice(-8)}`;
  const owned = `name=${q(marker)} OR short_name=${q(shortName)}`;
  h.assert(sql.value(`SELECT COUNT(*) FROM site WHERE ${owned}`) === '0', 'A site already carries this run\'s marker');
  s.cleanup(() => {
    sql.execute(`DELETE FROM providersite WHERE site_id IN (SELECT site_id FROM site WHERE ${owned});
      DELETE FROM site WHERE ${owned}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM site WHERE ${owned}`) === '0', 'The owned site was not removed');
  });
  const deferred = [];
  /** A step whose stylesheet noise (finding 100) is set aside before the strict page check runs. */
  const step = (label, body) => s.step(label, async () => {
    try { await body(); } finally { deferMissingStylesheet(recorder, deferred); }
  });
  const stored = () => sql.rows(`SELECT short_name,bg_color,status,phone,province FROM site WHERE name=${q(marker)} ORDER BY site_id`)
    .map(row => row.join(' ')).join('; ');

  const {page: admin} = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder, label: 'administration', timeout: TIMEOUT});
  await admin.waitForLoadState('load');
  const menuEntry = admin.locator(`a.xlink[rel$="${SECTION}"]`).first();
  if (await menuEntry.count() === 0) {
    throw new h.SkipCheck('Administration offers no Satellite-sites Admin: multisites is off on this install');
  }

  let frame;
  /** Do something inside the admin iframe that navigates it, and wait for the new document. */
  async function frameNavigation(action, label) {
    const navigated = admin.waitForEvent('framenavigated', {predicate: candidate => candidate === frame, timeout: TIMEOUT});
    navigated.catch(() => {});
    await action();
    await navigated;
    await frame.waitForLoadState('domcontentloaded', {timeout: TIMEOUT}).catch(() => {});
    await h.assertNotErrorPage(frame, label);
  }
  const addNew = () => frame.locator(`form[action$="${SECTION}"]:has(input[name="method"][value="add"]) input[type="submit"]`);
  const save = () => frame.locator(`form[action$="${SECTION}"] input[type="submit"]:not([onclick])`);
  // The name link is method=update today; #4700's release fix makes it method=edit. Match both so the check
  // works before and after that fix reaches develop.
  const siteLink = () => frame.locator(`a[href*="${SECTION}?method=update"], a[href*="${SECTION}?method=edit"]`,
    {hasText: marker});

  await step('Administration ▸ Satellite-sites Admin opens the site list with Add New Site', async () => {
    await revealAuditLink(admin, menuEntry, TIMEOUT);
    const loaded = admin.waitForEvent('framenavigated', {
      predicate: candidate => candidate !== admin.mainFrame() && new URL(candidate.url()).pathname.endsWith(SECTION), timeout: TIMEOUT,
    });
    loaded.catch(() => {});
    await menuEntry.click({timeout: TIMEOUT});
    frame = await loaded;
    await frame.waitForLoadState('domcontentloaded', {timeout: TIMEOUT}).catch(() => {});
    await h.assertNotErrorPage(frame, 'the site list');
    h.assert(await addNew().count() === 1, 'The site list offers no Add New Site');
    h.assert(await siteLink().count() === 0, 'The site list already shows a site named with this run\'s marker');
  });

  await step('GET with a save flag is refused and stores nothing', async () => {
    const response = await context.request.get(h.appUrl(s.config.baseUrl, SECTION), {
      params: {method: 'save', 'site.name': marker, 'site.shortName': shortName, 'site.bgColor': '#C0FFEE'}, maxRedirects: 0,
    });
    await h.assertRefused(s, {label: 'GET admin/ManageSites method=save', table: 'site', where: owned, before: '0', response});
  });

  await step('Add New Site ▸ Save stores the new site and lists it', async () => {
    await frameNavigation(() => addNew().click(), 'the new site form');
    for (const [name, value] of [['site.name', marker], ['site.shortName', shortName], ['site.bgColor', '#C0FFEE'],
      ['site.phone', '555-0100'], ['site.fax', '555-0101'], ['site.address', `${marker} street`], ['site.city', 'Testville'],
      ['site.postal', 'A1A 1A1']]) {
      await frame.locator(`input[name="${name}"]`).fill(value);
    }
    await frame.locator('input[name="site.status"]').check();
    await frame.locator('select[name="site.province"]').selectOption('ON');
    await frameNavigation(() => save().click(), 'the site list after Save');
    h.assert(stored() === `${shortName} #C0FFEE 1 555-0100 ON`, `Save did not store the new site as entered: "${stored()}"`);
    h.assert(await siteLink().count() === 1, 'The site list does not show the saved site');
  });

  await step('the site\'s name opens its details with the stored values filled in', async () => {
    await frameNavigation(() => siteLink().click(), 'the site details');
    const values = {};
    for (const name of ['site.name', 'site.shortName', 'site.bgColor', 'site.phone']) {
      values[name] = await frame.locator(`input[name="${name}"]`).inputValue();
    }
    h.assert(JSON.stringify(values) === JSON.stringify({'site.name': marker, 'site.shortName': shortName,
      'site.bgColor': '#C0FFEE', 'site.phone': '555-0100'}), `The site details do not show the stored values: ${JSON.stringify(values)}`);
    h.assert(await frame.locator('input[name="site.status"]').isChecked(), 'The site details do not show the site as active');
  });

  await step('Save after changing the telephone updates the same site', async () => {
    await frame.locator('input[name="site.phone"]').fill('555-0199');
    await frameNavigation(() => save().click(), 'the site list after the edit');
    h.assert(stored() === `${shortName} #C0FFEE 1 555-0199 ON`, `The edit did not update the one owned site: "${stored()}"`);
  });

  await s.step('the site pages load their stylesheets', async () => {
    h.assert(deferred.length === 0, `The site pages requested the root-relative ${MISSING_STYLESHEET} `
      + `${deferred.length} time(s) and it 404'd (finding 100: \${request.contextPath} is not an EL implicit object)`);
  });
}

if (require.main === module) runWorkflow('admin-manage-sites', workflow, {openPatient: false});
module.exports = {workflow};
