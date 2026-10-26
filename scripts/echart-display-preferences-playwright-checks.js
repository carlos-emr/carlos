#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Per-provider eChart display settings (Preferences > eChart) and the OSCAR 19 header bar.
 *
 * WHY THIS ONE EXISTS. An alpha tester moving from OSCAR 19 asked for its configurable eChart header,
 * shown the way OSCAR 19 showed it, with the choices made by each provider rather than the clinic:
 *
 *   Display eChart Timer                         Yes (default)
 *   File Documents in Folders by Document Type   Yes
 *   Display Date of Birth (yyyy-mm-dd)           Yes
 *   Display Cell Phone Number if Available       Yes
 *   Display Health Insurance Number (HIN)        Yes
 *   Display Patient Email Address if Available   Yes
 *   Display Family Doctor                        Yes
 *   Display Referring Doctor                     No
 *   Display OCEAN UI                             No
 *
 * This check drives that end to end the way a provider does: it opens Preferences from the schedule,
 * reads the eChart section's defaults, opens the chart and asserts what each default shows, flips
 * every setting with the Yes/No radios and Save All, reads the rows back from the database, and opens
 * the chart again to assert that every surface followed: the header bar (date of birth or age, HIN,
 * cell or home phone, copy button or tel: click-to-dial link, email), the note panel's timer, the
 * Family Doctor and Referring Doctor panels at the top of the left column, the Documents panel's
 * folders, and the Ocean toolbar placeholder. It also proves a GET carrying the settings is refused
 * and changes nothing.
 *
 * OWNED STATE. The FAKE-PW patient (runWorkflow) gets a HIN, phones, an email, a Referral Doctor, a
 * Family Doctor contact and two documents of different types; all are removed with it. The test
 * login's own eChart display rows are snapshotted first and restored exactly (ids included) in
 * cleanup, so a run leaves the provider's choices as it found them. Because those rows belong to the
 * shared test login, the check must not run beside another chart check.
 *
 * NO PATIENT DATA IN THE OUTPUT: the patient is synthetic and messages quote only fixture values.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 *   SCREENSHOT_DIR (optional): saves the chart, its header bar and the Preferences section before and
 *                              after the change.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { ECHART_DISPLAY_SETTINGS, providerEchartDisplayRows } = require('./lib/echart-display-preferences');

const TIMEOUT = 30000;
const PATIENT = Object.freeze({
  hin: '9123456782', ver: 'PW', home: '905-555-0199', cell: '905-555-0100',
  email: 'fake.pw.echart@example.test', referralDoctor: 'FAKE-Referrer, FAKE-Rita', dob: '1980-01-02',
});
const DOCUMENT_TYPES = ['consult', 'lab'];

/** Whole years since an ISO yyyy-mm-dd date of birth, as the chart computes the age today. */
function ageInYears(isoDate, today = new Date()) {
  const [year, month, day] = isoDate.split('-').map(Number);
  const birthdayPassed = today.getMonth() + 1 > month || (today.getMonth() + 1 === month && today.getDate() >= day);
  return today.getFullYear() - year - (birthdayPassed ? 0 : 1);
}

/** The Save All page posts and closes itself; Chromium reports its CSRFGuard script as aborted. */
async function saveAndClose(recorder, page) {
  const closed = page.waitForEvent('close', { timeout: TIMEOUT });
  const since = recorder.requestFailures.length;
  await page.locator('.footer-bar button[type="submit"]').click({ noWaitAfter: true });
  await closed;
  const added = recorder.requestFailures.slice(since);
  const index = added.findIndex((entry) => entry.resourceType === 'script' && entry.errorText === 'net::ERR_ABORTED'
    && /\/csrfguard$/.test(new URL(entry.url).pathname));
  if (index >= 0) recorder.requestFailures.splice(since + index, 1);
}

async function openPreferences(s) {
  const prefs = await ui.clickOpensPopup(s.schedule, s.schedule.getByTitle(/Edit your personal setting/i).first(),
    { context: s.context, recorder: s.recorder, label: 'preferences', timeout: TIMEOUT });
  const first = prefs.locator(`#${ECHART_DISPLAY_SETTINGS[0].name}-true`);
  await first.waitFor({ state: 'attached', timeout: TIMEOUT });
  await revealAuditLink(prefs, first, TIMEOUT);
  return prefs;
}

/** Which radio of each pair is checked: { name: true|false }. */
async function readRadios(prefs) {
  const state = {};
  for (const { name } of ECHART_DISPLAY_SETTINGS) {
    const yes = await prefs.locator(`#${name}-true`).isChecked();
    const no = await prefs.locator(`#${name}-false`).isChecked();
    h.assert(yes !== no, `The ${name} radios are not a Yes/No pair with exactly one choice`);
    state[name] = yes;
  }
  return state;
}

/** A fresh render of the chart, its navigation modules loaded and its notes panel attached. */
async function renderChart(s, label) {
  const chart = await s.chart();
  const page = await s.context.newPage();
  await page.goto(chart.url(), { waitUntil: 'domcontentloaded' });
  await page.locator('#header-top-row').waitFor({ state: 'attached', timeout: TIMEOUT });
  await waitForNavbars(page, TIMEOUT);
  await page.locator('#notCPP #save-sign-bill-buttons').first().waitFor({ state: 'attached', timeout: TIMEOUT });
  await page.evaluate(() => document.fonts.ready);
  if (s.config.screenshotDir) {
    await h.screenshot(page, s.config.screenshotDir, `echart-display-${label}-chart`);
    await page.locator('#header').screenshot({ path: h.buildArtifactPath(s.config.screenshotDir, `echart-display-${label}-header`) });
  }
  return page;
}

async function text(page, selector) {
  const locator = page.locator(selector);
  return (await locator.count()) ? (await locator.first().innerText()).replace(/\s+/g, ' ').trim() : null;
}

async function workflow(s) {
  const { sql, patient, provider, recorder } = s;
  const owner = h.sqlString(provider);

  const rows = providerEchartDisplayRows(sql, provider);
  s.cleanup(() => rows.restore());

  // The patient's chart data. demographicExt rows go with the patient (runWorkflow); the contact and
  // the documents are removed here.
  sql.execute(`UPDATE demographic SET hin=${h.sqlString(PATIENT.hin)}, ver=${h.sqlString(PATIENT.ver)}, hc_type='ON',
      phone=${h.sqlString(PATIENT.home)}, phone2='', email=${h.sqlString(PATIENT.email)}, consentToUseEmailForCare=0,
      family_doctor=${h.sqlString(`<rdohip>123456</rdohip><rd>${PATIENT.referralDoctor}</rd>`)}
    WHERE demographic_no=${patient}`);
  sql.execute(`INSERT INTO demographicExt (demographic_no, provider_no, key_val, \`value\`, date_time, hidden)
    VALUES (${patient}, ${owner}, 'demo_cell', ${h.sqlString(PATIENT.cell)}, NOW(), '0')`);
  s.cleanup(() => sql.execute(`DELETE FROM DemographicContact WHERE demographicNo=${patient}`));
  sql.execute(`INSERT INTO DemographicContact (facilityId, creator, created, updateDate, deleted, demographicNo, contactId,
      role, type, category, consentToContact, active, mrp)
    VALUES (0, ${owner}, NOW(), NOW(), 0, ${patient}, ${owner}, 'Family Doctor', 0, 'professional', 1, 1, 0)`);
  const documents = [];
  s.cleanup(() => {
    if (!documents.length) return;
    sql.execute(`DELETE FROM ctl_document WHERE module='demographic' AND module_id=${patient} AND document_no IN (${documents.join(',')});
      DELETE FROM document WHERE document_no IN (${documents.join(',')})`);
  });
  for (const type of DOCUMENT_TYPES) {
    const id = h.insertId(sql, `INSERT INTO document (doctype, docdesc, docfilename, doccreator, responsible, updatedatetime,
        status, contenttype, contentdatetime, public1, observationdate, restrictToProgram, abnormal)
      VALUES (${h.sqlString(type)}, ${h.sqlString(`${s.marker} ${type}`)}, ${h.sqlString(`${s.marker}-${type}.pdf`)},
        ${owner}, ${owner}, NOW(), 'A', 'application/pdf', NOW(), 0, CURDATE(), 0, 0)`, 'document fixture');
    documents.push(id);
    sql.execute(`INSERT INTO ctl_document (module, module_id, document_no, status) VALUES ('demographic', ${patient}, ${id}, 'A')`);
  }
  const familyDoctorName = sql.value(`SELECT CONCAT(last_name, ', ', first_name) FROM provider WHERE provider_no=${owner}`);
  const clinicOcean = sql.rows(`SELECT \`value\` FROM SystemPreferences WHERE name='echart_show_ocean' ORDER BY id LIMIT 1`);
  const clinicAllowsOcean = clinicOcean.length === 0 || clinicOcean[0][0] === 'true';

  rows.clear();

  await s.step('the Preferences page offers the eChart section with OSCAR 19 defaults', async () => {
    const prefs = await openPreferences(s);
    h.assert(await prefs.locator('#secEchart').count() === 1, 'Preferences has no eChart section');
    const radios = await readRadios(prefs);
    for (const { name, defaultValue } of ECHART_DISPLAY_SETTINGS) {
      h.assert(radios[name] === defaultValue, `${name} shows ${radios[name] ? 'Yes' : 'No'} for a provider who never saved it`);
    }
    const labels = await prefs.locator('#secEchart .pref-label').allInnerTexts();
    h.assert(labels.some((label) => /Display eChart Timer/.test(label))
      && labels.some((label) => /File Documents in Folders by Document Type/.test(label))
      && labels.some((label) => /Display OCEAN UI/.test(label)), 'The eChart section is missing the OSCAR 19 labels');
    if (s.config.screenshotDir) {
      await prefs.locator('#secEchart').screenshot({ path: h.buildArtifactPath(s.config.screenshotDir, 'echart-display-preferences-defaults') });
    }
    await prefs.close();
  });

  await s.step('with the defaults the chart shows the OSCAR 19 header bar and panels', async () => {
    const page = await renderChart(s, 'defaults');
    const bar = await page.locator('#header-top-row').evaluate((el) => getComputedStyle(el).backgroundColor);
    h.assert(bar && bar !== 'rgba(0, 0, 0, 0)', 'The header bar has no provider colour');
    h.assert((await text(page, '#patient-full-name')).includes(s.marker), 'The header does not name the patient');
    // OSCAR 19's window title: "E - LAST, FIRST SEX AGE", the line above the bar in the popup.
    const title = await page.title();
    // The marker is random hex and may hold digits, so pin the age itself, right after the sex.
    h.assert(title.startsWith('E - ') && title.includes(s.marker) && title.includes(` F ${ageInYears(PATIENT.dob)} `),
      `The chart window title does not carry the patient's name, sex and age as OSCAR 19's did: ${JSON.stringify(title.replace(s.marker, '<patient>'))}`);
    h.assert(await page.locator('#header-top-row a[href*="ViewAbout"], #header-top-row a[href*="ViewLicense"]').count() === 0,
      'The header bar still carries About/License links');
    h.assert(await text(page, '#patient-dob') === PATIENT.dob, 'The header does not show the date of birth as yyyy-mm-dd');
    h.assert(await page.locator('#patient-age').count() === 0, 'The header shows the age although the date of birth is on');
    h.assert(/\d/.test(await page.locator('#patient-dob').getAttribute('title')), 'The date of birth does not offer the age as its tooltip');
    h.assert((await text(page, '#patient-hin')).includes(PATIENT.hin), 'The header does not show the HIN');
    h.assert((await text(page, '#patient-cell-phone')).includes(PATIENT.cell), 'The header does not show the cell phone');
    h.assert((await page.locator('#patient-cell-phone').getAttribute('title')).includes(PATIENT.home),
      'The cell phone does not offer the home number as its tooltip');
    h.assert(await page.locator('#patient-phone').count() === 0, 'The header shows the home phone although a cell is preferred');
    h.assert(await page.locator('#patient-cell-phone').evaluate((el) => el.tagName) === 'BUTTON'
      && await page.locator('#header-top-row a[href^="tel:"]').count() === 0,
    'The header phone is a tel: link although click-to-dial is off by default');
    h.assert(await text(page, '#patient-email') === PATIENT.email, 'The header does not show the email address');
    h.assert(await page.locator('#header a[href*="ViewCalculators"]').count() === 1, 'The header must offer exactly one calculators link');
    h.assert(await page.locator('#aTimer').count() === 1, 'The note panel has no timer with the timer on');
    h.assert(await page.locator('#fDoc').count() === 1, 'The left column has no Family Doctor panel');
    h.assert((await text(page, '#fDoc')).includes(familyDoctorName), 'The Family Doctor panel does not name the family doctor contact');
    h.assert(await page.locator('#leftNavBar > div').first().getAttribute('id') === 'fDoc', 'The Family Doctor panel is not first in the left column');
    h.assert(await page.locator('#rDoc').count() === 0, 'The Referring Doctor panel shows although it is off by default');
    const folders = await page.locator('#docs button.nav-folder-toggle').allInnerTexts();
    h.assert(JSON.stringify(folders.map((folder) => folder.trim())) === JSON.stringify(['consult (1)', 'lab (1)']),
      `The Documents panel did not file the documents by type: ${JSON.stringify(folders)}`);
    const consult = page.locator('#docs button.nav-folder-toggle').first();
    const listId = await consult.getAttribute('aria-controls');
    h.assert(!await page.locator(`[id="${listId}"]`).isVisible(), 'A document folder starts open');
    await consult.click();
    h.assert(await consult.getAttribute('aria-expanded') === 'true' && await page.locator(`[id="${listId}"]`).isVisible(),
      'Clicking a folder does not open it');
    h.assert((await text(page, `[id="${listId}"]`)).includes(`${s.marker} consult`), 'The consult folder does not hold the consult document');
    if (s.config.screenshotDir) {
      await page.locator('#leftNavBar').screenshot({ path: h.buildArtifactPath(s.config.screenshotDir, 'echart-display-defaults-left-column') });
    }
    h.assert(await page.locator('#ocean_placeholder').count() === 0, 'The Ocean placeholder renders although Ocean is off by default');

    // A patient who consented to email for care gets a mailto link, its "@" left literal.
    sql.execute(`UPDATE demographic SET consentToUseEmailForCare=1 WHERE demographic_no=${patient}`);
    try {
      const consented = await renderChart(s, 'defaults-email-consented');
      h.assert(await consented.locator('#patient-email-link').getAttribute('href') === `mailto:${PATIENT.email}`,
        'A consented patient\'s email is not a mailto link to the plain address');
      await consented.close();
    } finally {
      sql.execute(`UPDATE demographic SET consentToUseEmailForCare=0 WHERE demographic_no=${patient}`);
    }
  });

  await s.step('Save All stores the opposite choice for every setting', async () => {
    const prefs = await openPreferences(s);
    for (const { name, defaultValue } of ECHART_DISPLAY_SETTINGS) {
      await prefs.locator(`#${name}-${defaultValue ? 'false' : 'true'}`).check();
    }
    await saveAndClose(recorder, prefs);
    for (const { name, defaultValue } of ECHART_DISPLAY_SETTINGS) {
      await expectValue(sql, `SELECT \`value\` FROM property WHERE provider_no=${owner} AND name=${h.sqlString(name)} ORDER BY id LIMIT 1`,
        String(!defaultValue), `${name} was not saved`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE provider_no=${owner} AND name IN (${ECHART_DISPLAY_SETTINGS
      .map((setting) => h.sqlString(setting.name)).join(',')})`) === String(ECHART_DISPLAY_SETTINGS.length),
    'Saving wrote more than one row per setting');
  });

  await s.step('the chart follows every saved setting', async () => {
    const page = await renderChart(s, 'changed');
    h.assert(await page.locator('#patient-dob').count() === 0, 'The header still shows the date of birth with it turned off');
    h.assert(/\d/.test(await text(page, '#patient-age')), 'The header does not show the age with the date of birth off');
    h.assert(await page.locator('#patient-age').getAttribute('title') === PATIENT.dob, 'The age does not offer the date of birth as its tooltip');
    h.assert(await page.locator('#patient-hin').count() === 0, 'The header still shows the HIN with it turned off');
    h.assert((await text(page, '#patient-phone')).includes(PATIENT.home), 'The header does not fall back to the home phone');
    h.assert((await page.locator('#patient-phone').getAttribute('title')).includes(PATIENT.cell), 'The home phone does not offer the cell as its tooltip');
    h.assert(await page.locator('#patient-cell-phone').count() === 0, 'The header still shows the cell phone with it turned off');
    h.assert(await page.locator('#patient-phone').evaluate((el) => el.tagName) === 'A'
      && await page.locator('#patient-phone').getAttribute('href') === `tel:${PATIENT.home.replace(/\D/g, '')}`,
    'The home phone is not a tel: click-to-dial link with click-to-dial turned on');
    h.assert(await page.locator('#patient-email, #patient-email-link').count() === 0, 'The header still shows the email with it turned off');
    h.assert(await page.locator('#aTimer, #toggleTimer').count() === 0, 'The note panel still shows the timer with it turned off');
    h.assert(await page.locator('#fDoc').count() === 0, 'The Family Doctor panel still shows with it turned off');
    h.assert(await page.locator('#rDoc').count() === 1, 'The left column has no Referring Doctor panel with it turned on');
    h.assert((await text(page, '#rDoc')).includes(PATIENT.referralDoctor), 'The Referring Doctor panel does not name the referral doctor');
    h.assert(await page.locator('#docs button.nav-folder-toggle').count() === 0, 'The Documents panel still files by type with it turned off');
    h.assert((await text(page, '#docs')).includes(`${s.marker} consult`), 'The Documents panel no longer lists the documents');
    h.assert(await page.locator('#ocean_placeholder').count() === (clinicAllowsOcean ? 1 : 0),
      `The Ocean placeholder does not follow the provider's choice (clinic switch ${clinicAllowsOcean ? 'on' : 'off'})`);
  });

  await s.step('reopening Preferences shows the saved choices', async () => {
    const prefs = await openPreferences(s);
    const radios = await readRadios(prefs);
    for (const { name, defaultValue } of ECHART_DISPLAY_SETTINGS) {
      h.assert(radios[name] === !defaultValue, `${name} did not reopen with the saved choice`);
    }
    if (s.config.screenshotDir) {
      await prefs.locator('#secEchart').screenshot({ path: h.buildArtifactPath(s.config.screenshotDir, 'echart-display-preferences-changed') });
    }
    await prefs.close();
  });

  await s.step('a GET carrying the settings is refused and changes nothing', async () => {
    const response = await s.context.request.get(`${s.config.baseUrl}/provider/ViewProviderUpdatePreference?echart_show_HIN=true`,
      { maxRedirects: 0, failOnStatusCode: false });
    h.assert(response.status() === 405, `A GET to the preferences save answered ${response.status()}, expected 405`);
    h.assert(rows.value('echart_show_HIN') === 'false', 'A GET changed a stored eChart display setting');
  });
}

if (require.main === module) runWorkflow('echart-display-preferences', workflow);

module.exports = { PATIENT };
