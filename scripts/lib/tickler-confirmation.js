/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The native confirmation the task list raises before Delete (#5683, finding 337).
 *
 * Delete changes the status of every ticked task, so ticklerMain.jsp and ticklerDemoMain.jsp ask first
 * (`confirmDeleteSelected()`); Complete and the legacy erase submission ask nothing. A check that clicks
 * Delete must answer that dialog or the strict harness dismisses it and the click changes nothing, and a
 * check that clicks anything else must not meet one. This helper runs the click, answers the prompt the
 * way the clinician would, and counts what was raised, so a Delete that stops asking (or asks twice, or
 * shows a missing-key `???tickler...???` placeholder) fails the step instead of passing unnoticed.
 */
const h = require('./playwright-harness');

/**
 * Runs `body` on `page`, answering any native dialog it raises, then asserts exactly `expected` real
 * confirmation prompts were shown.
 *
 * @param {import('playwright').Page} page a page wired by the strict harness
 * @param {number} expected 1 for the Delete button, 0 for every other list action
 * @param {() => Promise<unknown>} body the click (and any navigation wait) that submits the action
 * @param {{ accept?: boolean }} [options] accept=false dismisses the prompt (the cancel path)
 * @returns {Promise<Array<{type: string, text: string}>>} the dialogs seen, for further assertions
 */
async function withTicklerConfirmations(page, expected, body, options = {}) {
  const dialogs = await h.withExpectedDialogs(page, body, { accept: options.accept !== false });
  h.assert(dialogs.length === expected && dialogs.every(dialog => dialog.type === 'confirm'),
    `The task list raised ${JSON.stringify(dialogs.map(dialog => dialog.type))}; expected ${expected} confirm dialog(s)`);
  for (const dialog of dialogs) {
    h.assert(/\S/.test(dialog.text || '') && !/\?\?\?/.test(dialog.text),
      `The Delete confirmation has no usable text (${JSON.stringify(dialog.text)}); is its message key missing?`);
  }
  return dialogs;
}

/**
 * Dismisses Delete and verifies cancellation leaves the native form in place without a mutation request.
 * Storage assertions remain with the caller, which knows the owned task and history rows.
 *
 * @param {import('playwright').Page} page the wired task-list page
 * @param {() => Promise<unknown>} body the Delete click
 */
async function dismissTicklerDelete(page, body) {
  const snapshot = () => page.locator('form[name="ticklerform"]').evaluate(form => ({
    selected: Array.from(form.elements).filter(e => e.name === 'checkbox' && e.checked).map(e => e.value),
    action: form.elements.namedItem('submit_form').value,
  }));
  const before = await snapshot();
  h.assert(before.selected.length > 0, 'The cancellation check needs a selected task');
  const posts = [];
  const navigations = [];
  const onRequest = request => {
    if (request.method() === 'POST' && /\/tickler\/DbTickler(?:Demo)?Main$/.test(h.pathOnly(request.url()))) posts.push(request.url());
  };
  const onNavigation = frame => { if (frame === page.mainFrame()) navigations.push(frame.url()); };
  page.on('request', onRequest);
  page.on('framenavigated', onNavigation);
  try {
    await withTicklerConfirmations(page, 1, body, { accept: false });
    await page.waitForLoadState('networkidle');
    h.assert(posts.length === 0 && navigations.length === 0,
      'Dismissing task Delete submitted a mutation request or navigated the list');
    h.assert(JSON.stringify(await snapshot()) === JSON.stringify(before),
      'Dismissing task Delete changed the selection or submit action');
  } finally {
    page.off('request', onRequest);
    page.off('framenavigated', onNavigation);
  }
}

module.exports = { withTicklerConfirmations, dismissTicklerDelete };
