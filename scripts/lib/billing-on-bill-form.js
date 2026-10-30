/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The Ontario bill form (billingON.jsp) for a workflow's owned patient.
 *
 * The schedule's billing link opens /billing with the appointment's identifiers and
 * billRegion=ON (billing-on-third-party-playwright-checks.js builds the same query from
 * its configured appointment). A workflow owns no appointment, so this seeds one for its
 * patient on the test login's provider (gap-billing-support's seedAppointment, which
 * registers its own cleanup) and opens the form from it by that URL.
 */
const h = require('./playwright-harness');
const { seedAppointment } = require('./gap-billing-support');

const START_TIME = '10:00:00';

async function openOntarioBillForm(s, label) {
  const today = s.sql.value('SELECT CURDATE()');
  const appointmentNo = seedAppointment(s, { time: START_TIME, tag: 'Bill form' });
  const params = new URLSearchParams({
    billRegion: 'ON',
    hotclick: '',
    appointment_no: appointmentNo,
    demographic_no: s.patient,
    apptProvider_no: s.provider,
    providerview: s.provider,
    appointment_date: today,
    status: 't',
    start_time: START_TIME,
    bNewForm: '1',
  });
  const page = await s.context.newPage();
  h.wireStrictPage(page, label, s.recorder);
  await page.goto(`${s.config.baseUrl}/billing?${params.toString()}`);
  await page.locator('input[name="serviceCode0"]').waitFor();
  // The type-aheads are attached by the page's own script once jQuery UI is up.
  await page.waitForFunction(() => window.jQuery && !!jQuery('input[name="serviceCode0"]').data('ui-autocomplete'));
  return { page, appointmentNo };
}

/* The GET the form's type-ahead sends for a term (jQuery.getJSON with {term}). */
function suggestionsFor(page, route, term, timeout = 20000) {
  return page.waitForResponse(response => {
    if (response.request().method() !== 'GET') return false;
    const url = new URL(response.url());
    return url.pathname.endsWith(route) && url.searchParams.get('term') === term;
  }, { timeout });
}

/* Clicks the visible jQuery UI suggestion whose text contains `text`. */
async function pickSuggestion(page, text) {
  const item = page.locator('.ui-autocomplete:visible .ui-menu-item').filter({ hasText: text }).first();
  await item.waitFor();
  await item.click();
}

/*
 * Counts requests to `route` while `body` runs and the type-ahead's delay passes: the settle
 * time is well over the longest configured delay (300 ms for the referral fields), so a
 * search that was going to be sent has been.
 */
async function requestsWhile(page, route, body, settleMs = 800) {
  let count = 0;
  const listener = request => { if (new URL(request.url()).pathname.endsWith(route)) count += 1; };
  page.on('request', listener);
  try {
    await body();
    await page.waitForTimeout(settleMs);
  } finally {
    page.off('request', listener);
  }
  return count;
}

module.exports = { openOntarioBillForm, suggestionsFor, pickSuggestion, requestsWhile };
