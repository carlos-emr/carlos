#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-schedule-keyboard-shortcuts — the day sheet's Alt+<letter> shortcuts
 * (appointmentprovideradminday.jsp document.onkeydown; key codes from global.*Shortcut in
 * oscarResources). schedule-views-playwright-checks drives Alt+T (today) only; the other shortcuts, the
 * ones a front desk uses instead of the mouse all day, had no check (coverage plan §1.3: 17 keys).
 *
 * User path: Schedule (day sheet) ▸ Alt+A Administration, Alt+I Tickler, Alt+M Messages, Alt+D eDocs,
 * Alt+L Lab inbox, Alt+O Consultations, Alt+R Reports, Alt+S Search, Alt+P Preferences, Alt+C Calendar,
 * Alt+W Workflow; Alt+N Month view; Alt+V provider view toggle. (Alt+E Resources and Alt+H Help open the
 * clinic's configured resource URL, which can be off-host, so they are not pressed.)
 * Asserted: each popup shortcut opens exactly one window on the right page, which loads without an
 * error page or any JavaScript/HTTP problem (the strict recorder watches every opened window); Alt+N
 * replaces the day sheet with the month view; Alt+V flips the all-providers/scheduled-providers view
 * (viewall) and a second press flips it back. Read-only: nothing is written, so there are no fixtures
 * and no cleanup. The month view's own shortcuts are dead (ISSUES L116) and are not asserted here.
 * Implements coverage plan §2.3 (schedule and appointments: day-sheet keyboard shortcuts).
 */
const h = require('./lib/playwright-harness');
const { pressShortcut } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const POPUP_SHORTCUTS = [
  ['KeyA', 'Administration', '/administration'],
  ['KeyI', 'Tickler', '/tickler/ViewTicklerMain'],
  ['KeyM', 'Messages', '/messenger/DisplayMessages'],
  ['KeyD', 'eDocs', '/documentManager/ViewDocumentReport'],
  ['KeyL', 'Lab inbox', '/web/inboxhub/Inboxhub'],
  ['KeyO', 'Consultations', '/encounter/IncomingConsultation'],
  ['KeyR', 'Reports', '/report/ViewReportindex'],
  ['KeyS', 'Patient search', '/demographic/ViewSearch'],
  ['KeyP', 'Provider preferences', '/provider/ViewProviderPreference'],
  ['KeyC', 'Calendar', '/share/CalendarPopup'],
  ['KeyW', 'Workflow list', '/oscarWorkflow/WorkFlowList'],
];

async function workflow(s) {
  const { schedule, context } = s;
  const TIMEOUT = 20000;

  for (const [code, label, route] of POPUP_SHORTCUTS) {
    await s.step(`Alt+${code.slice(3)} opens ${label} (${route})`, async () => {
      const before = new Set(context.pages());
      const opened = context.waitForEvent('page', { timeout: TIMEOUT });
      await pressShortcut(schedule, `Alt+${code}`);
      const popup = await opened;
      await popup.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
      await popup.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      await h.assertNotErrorPage(popup, `${label} opened by Alt+${code.slice(3)}`);
      h.assert(h.pathOnly(popup.url()).endsWith(route), `Alt+${code.slice(3)} opened ${h.pathOnly(popup.url())}, not ${route}`);
      const extra = context.pages().filter(page => !before.has(page) && page !== popup && !page.isClosed());
      h.assert(extra.length === 0, `Alt+${code.slice(3)} opened ${extra.length + 1} windows, not one`);
      await popup.close();
    });
  }

  await s.step('Alt+V flips between the all-providers and scheduled-providers view, and back', async () => {
    const viewall = () => new URL(schedule.url()).searchParams.get('viewall');
    const start = viewall();
    await Promise.all([schedule.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }), pressShortcut(schedule, 'Alt+KeyV')]);
    const flipped = viewall();
    h.assert(flipped !== start && ['0', '1'].includes(flipped), `Alt+V left viewall at ${start} (now ${flipped})`);
    await schedule.locator('a.adhour').first().waitFor({ state: 'attached', timeout: TIMEOUT });
    await Promise.all([schedule.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }), pressShortcut(schedule, 'Alt+KeyV')]);
    h.assert(viewall() === start, `A second Alt+V did not restore viewall=${start} (now ${viewall()})`);
  });

  await s.step('Alt+N replaces the day sheet with the month view', async () => {
    await Promise.all([schedule.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }), pressShortcut(schedule, 'Alt+KeyN')]);
    h.assert(new URL(schedule.url()).searchParams.get('displaymode') === 'month', 'Alt+N did not open the month view');
    await h.assertNotErrorPage(schedule, 'month view opened by Alt+N');
  });
}

if (require.main === module) runWorkflow('gap-provider-schedule-keyboard-shortcuts', workflow, { openPatient: false });
module.exports = { workflow };
