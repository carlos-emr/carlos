#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Report by Template ▸ template editor through the packaged front door (carlos-emr/carlos#4133).
 *
 * User path: Report by Template ▸ Add Template ▸ (editor textarea) Save; then the template's
 * Edit Template ▸ change ▸ Done (oscarReport/reportByTemplate/addEditTemplatesAction).
 *
 * Before #4133 saving any template whose XML carried a <param> was answered 403 (CRS 941160
 * reads the markup as HTML injection; the <query> body scores as SQL), although uploading the
 * same document as a file worked. Packaged exclusion 1402 unhooks ARGS:xmltext from the SQLi
 * and XSS families on POST action=add|edit only, and the application now enforces the SQL
 * shape itself (ReportTemplateSqlValidator) and encodes what the editor writes back. Asserts:
 *   - a parameterised template (text, list and a <param-query> choice list) saves from the
 *     textarea and is stored with its SQL;
 *   - Edit ▸ Done saves the change and returns to the configuration page with the new
 *     parameters rendered (the param-query ran and filled its list);
 *   - a template whose <param-query> is a DELETE is REFUSED at save with a message, the stored
 *     template is unchanged, and the editor keeps what the author typed;
 *   - markup stored inside a template (a choice label reading "</textarea><b id=...>") is
 *     shown as text in the editor, never as page markup;
 *   - add/edit/delete refuse GET (405) and the template survives a GET delete;
 *   - with EXPECT_FRONT_DOOR=true, markup in xmltext on any OTHER operation is still refused (403).
 *
 * Fixtures: none beyond the login; cleanup removes every reportTemplates row whose title
 * carries the run marker.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 30000;

async function csrfToken(page, baseUrl) {
  return page.evaluate(async (tokenUrl) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- tokenUrl is a Playwright argument built by appUrl from the validated base URL
    const response = await fetch(tokenUrl, { credentials: 'same-origin' });
    const match = (await response.text()).match(/masterTokenValue\s*=\s*["']([^"']+)["']/);
    return match ? match[1] : '';
  }, h.appUrl(baseUrl, '/csrfguard'));
}

function templateXml(title, description, { paramQuery = 'SELECT provider_no, last_name FROM provider' } = {}) {
  return `<report title="${title}" description="${description}" active="1">`
    + `<query>SELECT demographic_no, last_name FROM demographic WHERE sex = '{sexpick}' AND last_name LIKE '%{who}%'`
    // Authors type comparison operators raw: the editor escapes a stray '<' or '>' itself
    // (UtilXML.escapeXML), so an already-escaped '&gt;' would reach the SQL as text.
    + ` AND year_of_birth > '1900'</query>`
    + '<param id="who" type="text" description="Surname"></param>'
    + `<param id="sexpick" type="list" description="Sex"><choice id="F">Female</choice><choice id="M">Male</choice></param>`
    + `<param id="prov" type="list" description="Provider"><param-query>${paramQuery}</param-query></param>`
    + '</report>';
}

async function workflow(s) {
  const title = `${s.marker} RBT`;
  const like = h.sqlString(`%${s.marker}%`);
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM reportTemplates WHERE templatetitle LIKE ${like}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM reportTemplates WHERE templatetitle LIKE ${like}`) === '0',
      'Report templates created by the run were not removed');
  });
  const stored = () => s.sql.rows(`SELECT templateid, templatedescription, templatesql, templatexml
    FROM reportTemplates WHERE templatetitle=${h.sqlString(title)}`);

  const page = await s.context.newPage();
  const editor = page.locator('textarea#xmltext');
  const submit = async (locator, label) => {
    const [response] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'POST' && r.request().isNavigationRequest()
        && new URL(r.url()).pathname.endsWith('/oscarReport/reportByTemplate/addEditTemplatesAction'), { timeout: TIMEOUT }),
      locator.click(),
    ]);
    await page.waitForLoadState('domcontentloaded');
    h.assert(response.status() === 200, `${label} answered HTTP ${response.status()} (403 is the WAF refusing the template)`);
  };
  let templateId;

  await s.step('Add Template saves a parameterised template typed into the editor', async () => {
    await h.gotoApp(page, s.config.baseUrl, '/oscarReport/reportByTemplate/ViewAddEditTemplate?opentext=1');
    await editor.fill(templateXml(title, `${s.marker} roster`));
    await submit(page.locator('input[type="submit"][value="Save"]'), 'Saving the new template');
    const outcome = await page.locator('.alert-success, .alert-danger').first().innerText({ timeout: TIMEOUT }).catch(() => '');
    h.assert(outcome.includes('Saved Successfully'), `Saving the new template reported: ${outcome.trim() || 'nothing'}`);
    const rows = stored();
    h.assert(rows.length === 1 && rows[0][1] === `${s.marker} roster` && rows[0][2].includes("sex = '{sexpick}'"),
      'The template was not stored with its SQL');
    templateId = rows[0][0];
  });

  await s.step('Edit Template ▸ Done saves the change and the configuration page runs the param-query', async () => {
    await h.gotoApp(page, s.config.baseUrl, `/oscarReport/reportByTemplate/ViewAddEditTemplate?templateid=${templateId}&opentext=1`);
    h.assert((await editor.inputValue()).includes('<param id="prov"'), 'The editor did not load the stored XML');
    await editor.fill(templateXml(title, `${s.marker} edited roster`));
    await submit(page.locator('input[type="submit"][name="done"]'), 'Edit ▸ Done');
    await page.locator('select#prov').waitFor({ timeout: TIMEOUT });
    h.assert(await page.locator('select#prov option').count() > 0, 'The provider choice list (param-query) is empty');
    await expectValue(s.sql, `SELECT templatedescription FROM reportTemplates WHERE templateid=${templateId}`,
      `${s.marker} edited roster`, 'The edit was not stored');
  });

  await s.step('a template whose param-query is a DELETE is refused at save and nothing changes', async () => {
    const before = JSON.stringify(stored());
    await h.gotoApp(page, s.config.baseUrl, `/oscarReport/reportByTemplate/ViewAddEditTemplate?templateid=${templateId}&opentext=1`);
    const refused = templateXml(title, `${s.marker} write probe`,
      { paramQuery: "DELETE FROM provider WHERE provider_no = 'FAKE-PW'" });
    await editor.fill(refused);
    await submit(page.locator('input[type="submit"][name="done"]'), 'Saving the DELETE param-query');
    await page.locator('.alert-danger', { hasText: 'was refused' }).waitFor({ timeout: TIMEOUT });
    h.assert(JSON.stringify(stored()) === before, 'A template with a DELETE param-query was stored');
    h.assert((await editor.inputValue()) === refused, 'The editor did not keep what the author typed');
  });

  await s.step('markup stored in a template is shown as text in the editor, not as page markup', async () => {
    // The editor's own parse path cannot store a raw "</textarea>" (it escapes a lone '<' and
    // rejects an unmatched close tag), but templates saved by older releases or written to the
    // table directly can hold one, so the probe is seeded the way such a row would exist.
    const probe = '<report title="x"><query>SELECT 1</query><!-- </textarea><b id="rbt-injected">FAKE</b> --></report>';
    s.sql.execute(`UPDATE reportTemplates SET templatexml=${h.sqlString(probe)} WHERE templateid=${templateId}`);
    await h.gotoApp(page, s.config.baseUrl, `/oscarReport/reportByTemplate/ViewAddEditTemplate?templateid=${templateId}&opentext=1`);
    await editor.waitFor();
    h.assert(await page.locator('#rbt-injected').count() === 0, 'Stored template text broke out of the editor textarea');
    h.assert((await editor.inputValue()) === probe, 'The editor did not show the stored text verbatim');
  });

  await s.step('add, edit and delete refuse GET, and a GET delete leaves the template', async () => {
    const url = h.appUrl(s.config.baseUrl, '/oscarReport/reportByTemplate/addEditTemplatesAction');
    for (const params of [{ action: 'delete', templateid: templateId }, { action: 'edit', templateid: templateId, xmltext: 'x' },
      { action: 'add', xmltext: 'x' }]) {
      const response = await s.context.request.get(url, { params, maxRedirects: 0 });
      h.assert(response.status() === 405, `GET action=${params.action} answered HTTP ${response.status()}, not 405`);
    }
    h.assert(stored().length === 1, 'A GET removed or duplicated the template');
  });

  await s.step('the WAF exclusion stays narrow: markup on any other operation is still refused', async () => {
    if (!s.config.expectFrontDoor) {
      console.log('  NOTE report-by-template-editor-front-door: WAF narrowness not asserted (EXPECT_FRONT_DOOR is not true)');
      return;
    }
    const token = await csrfToken(page, s.config.baseUrl);
    // A fixed attack-shaped string the front door must refuse; it is sent only to the local
    // test deployment and never rendered by this script.
    const probe = '<script>alert(document.cookie)</script>';
    const route = h.appUrl(s.config.baseUrl, '/oscarReport/reportByTemplate/addEditTemplatesAction');
    const response = await s.context.request.post(route, { // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- deliberate WAF probe payload posted to the local test deployment; the check asserts it is refused (403) and never renders it
      headers: { 'CSRF-TOKEN': token }, maxRedirects: 0,
      form: { 'CSRF-TOKEN': token, action: 'delete', templateid: '0', xmltext: probe } });
    h.assert(response.status() === 403, `Markup on action=delete answered HTTP ${response.status()}, not 403`);
    h.assert(stored().length === 1, 'The probe changed the template');
  });
  await page.close();
}

if (require.main === module) runWorkflow('report-by-template-editor-front-door', workflow, { openPatient: false, openMaster: false });
module.exports = { workflow };
