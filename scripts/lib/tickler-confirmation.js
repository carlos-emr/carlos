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

module.exports = { withTicklerConfirmations };
