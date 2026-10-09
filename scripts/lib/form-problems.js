/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const { pathOnly } = require('./playwright-harness');

/*
 * Attribute the JavaScript-layer problems the browser recorded to the form step that was running.
 *
 * WHY. The strict recorder (playwright-harness.js wireStrictPage) is one run-wide list, and
 * assertStrictPage() fails on everything in it, so in a check that walks many forms the first form to
 * raise a script error fails every later step, and a step cannot be pinned to the finding that explains it.
 * A table-driven form check labels each form's pages (form-<code>, reopen-<code>, print-<code>) and, at the end
 * of each concern, TAKES that form's entries out of the recorder and reports them with the concern. What is
 * left (the chart's own pages) is judged by runWorkflow at the end, so a problem no concern owns still fails
 * the run.
 */

const LISTS = ['pageErrors', 'consoleIssues', 'requestFailures', 'badResponses', 'unexpectedDialogs'];

/**
 * Remember which entries the recorder holds now, so that a later takeProblems(recorder, labels, mark) takes only
 * what arrived afterwards. Entries are compared by identity, because draining shifts positions.
 */
function markProblems(recorder) {
  return new Set(LISTS.flatMap((name) => recorder[name]));
}

/**
 * Remove, and describe, the entries of the recorder that belong to the given page labels.
 *
 * @param {object} recorder  harness recorder (createRecorder())
 * @param {string[]} labels  page labels to take
 * @param {Set<object>} [mark]  markProblems() result: leave the entries that were already there
 * @param {(description: string) => boolean} [leave]  leave in the recorder (untaken) the entries whose description
 *   this accepts, for the concern that owns them to take later
 * @returns {string[]} one description per problem, in the order pageErrors, consoleIssues, requestFailures,
 *   badResponses, unexpectedDialogs. Paths only: a query string can carry the patient number.
 */
function takeProblems(recorder, labels, mark, leave) {
  const own = (entry) => labels.includes(entry.label) && !(mark && mark.has(entry));
  const taken = [];
  const take = (list, describe, skip = () => false) => {
    for (let i = 0; i < list.length;) {
      if (!own(list[i]) || (leave && leave(describe(list[i])))) { i++; continue; }
      const [entry] = list.splice(i, 1);
      if (!skip(entry)) taken.push(describe(entry));
    }
  };
  take(recorder.pageErrors, (e) => `uncaught ${e.text.split('\n')[0]}`);
  take(recorder.consoleIssues, (e) => `console ${e.type}: ${e.text.split('\n')[0]}`);
  // A request the browser abandoned because its own document went away is not the application failing.
  take(recorder.requestFailures, (e) => `${e.resourceType} ${pathOnly(e.url)} failed (${e.errorText})`,
    (e) => /ERR_ABORTED/.test(e.errorText || '') && typeof e.navigatedAway === 'function' && e.navigatedAway());
  take(recorder.badResponses, (e) => `HTTP ${e.status} on ${e.resourceType || 'a resource'} ${pathOnly(e.url)}${e.reason ? ` (${e.reason})` : ''}`);
  take(recorder.unexpectedDialogs, (e) => `an unexpected ${e.type} dialog was raised and dismissed`);
  return taken;
}

/**
 * Take a form's pending problems for the concern that has just ended, reporting each only once per form.
 *
 * A problem that recurs on every page of a form (a missing stylesheet) belongs to the first concern that met it, so
 * later concerns do not report it again: `seen` holds what the form's earlier concerns reported, and is updated.
 * With `judge: false` the entries are taken out of the recorder (so the run-wide judgement does not trip on them) but
 * are neither reported nor remembered. That is for a concern judged on its own assertion alone, such as the catalogue's
 * `bare` run on a patient with NULL contact columns: the 500 it exists to pin is its assertion, and a problem the page
 * raises besides (the stylesheet that is missing for every patient) must stay unseen so that the concern which owns it
 * still meets it when it runs.
 *
 * @param {object} recorder  harness recorder (createRecorder())
 * @param {string[]} labels  the form's page labels
 * @param {Set<string>} seen  descriptions the form's earlier concerns reported
 * @param {{judge?: boolean}} [options]
 * @returns {string[]} the descriptions to report with the concern
 */
function settleProblems(recorder, labels, seen, { judge = true } = {}) {
  const taken = takeProblems(recorder, labels);
  if (!judge) return [];
  const fresh = taken.filter((problem) => !seen.has(problem));
  fresh.forEach((problem) => seen.add(problem));
  return fresh;
}

module.exports = { markProblems, settleProblems, takeProblems };
