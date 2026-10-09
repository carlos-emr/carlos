/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/*
 * CLAIMS: which (form, concern) pairs one manifest entry of a table-driven form check asserts.
 *
 * WHY THIS EXISTS. A table-driven check (form-catalog-smoke, clinical-forms-save-reopen) runs many
 * forms in one workflow, and a script stops at its first failing step. Pinned to one finding, the
 * entry would hide every defect behind the first; unpinned, it would stay red for ever. So one
 * script runs under several manifest entries and each entry names, in an environment variable, the
 * pairs it asserts. Every run still executes the flow of every selected form and records the
 * outcome of every concern; the entry then asserts, in fixed order and one labelled step per pair,
 * only the pairs it claims. The entries together must claim each pair exactly once
 * (partitionProblems(), enforced by scripts/form-claims.test.js), so:
 *   - a broken form is claimed by a variant entry pinned on its own finding, and the default entry
 *     leaves that pair out (FORM_CATALOG_EXCEPT), which keeps the default entry green and keeps the
 *     rest of the form asserted;
 *   - when a finding is fixed the variant stops failing at its pin and the runner reports an
 *     unexpected pass, which is the prompt to remove the pin (the entry then simply asserts the pair).
 *
 * GRAMMAR. A comma-separated list of tokens. `<form>` claims every concern the form has;
 * `<form>.<concern>` claims one. Names are lower case. An unknown form or concern, or a concern the
 * form does not have, is an error of the RUN (judged when the check runs, never when the module is
 * required), so a mistyped token cannot silently claim nothing.
 */

const TOKEN = /^[a-z0-9]+(?:\.[a-z]+)?$/;

/** The pair's name as it appears in tokens, labels and the partition test. */
function claimKey(form, concern) {
  return `${form}.${concern}`;
}

/**
 * @param {string} variable  environment variable name, for the error message
 * @param {string|undefined} spec  its value
 * @param {Array<{key: string, concerns: string[]}>} forms  every form and the concerns it has
 * @returns {Set<string>} claim keys named by the spec (empty for a blank spec)
 */
function parseSpec(variable, spec, forms) {
  const named = new Set();
  const text = String(spec === undefined || spec === null ? '' : spec).trim();
  if (!text) return named;
  const byKey = new Map(forms.map((form) => [form.key, form]));
  for (const raw of text.split(',')) {
    const token = raw.trim();
    if (!TOKEN.test(token)) {
      throw new Error(`${variable} must be unset or a comma-separated list of <form> or <form>.<concern>, not ${JSON.stringify(text)}`);
    }
    const [key, concern] = token.split('.');
    const form = byKey.get(key);
    if (!form) throw new Error(`${variable} names an unknown form ${JSON.stringify(key)}; the forms are ${forms.map((f) => f.key).join(', ')}`);
    if (concern === undefined) {
      form.concerns.forEach((name) => named.add(claimKey(key, name)));
    } else if (form.concerns.includes(concern)) {
      named.add(claimKey(key, concern));
    } else {
      throw new Error(`${variable} names ${token}, but ${key} has only these concerns: ${form.concerns.join(', ')}`);
    }
  }
  return named;
}

/**
 * The pairs a run asserts: the `only` list (every pair when blank) minus the `except` list.
 *
 * @param {{only?: string, except?: string, onlyVariable?: string, exceptVariable?: string}} selection
 * @returns {string[]} claim keys, in table order (forms) and concern order, never empty
 */
function effectiveClaims(selection, forms) {
  const onlyVariable = selection.onlyVariable || 'ONLY';
  const exceptVariable = selection.exceptVariable || 'EXCEPT';
  const only = parseSpec(onlyVariable, selection.only, forms);
  const except = parseSpec(exceptVariable, selection.except, forms);
  const every = forms.flatMap((form) => form.concerns.map((concern) => claimKey(form.key, concern)));
  const chosen = every.filter((key) => (only.size ? only.has(key) : true) && !except.has(key));
  if (!chosen.length) throw new Error(`${onlyVariable} and ${exceptVariable} leave nothing to assert`);
  return chosen;
}

/**
 * Whether a set of manifest entries claims every pair exactly once.
 *
 * @param {Array<{name: string, claims: string[]}>} entries  each entry's effective claims
 * @param {string[]} universe  every claim key
 * @returns {string[]} problems, empty when the entries partition the universe
 */
function partitionProblems(entries, universe) {
  const owners = new Map(universe.map((key) => [key, []]));
  for (const entry of entries) {
    for (const key of entry.claims) {
      if (!owners.has(key)) owners.set(key, []);
      owners.get(key).push(entry.name);
    }
  }
  const problems = [];
  for (const [key, names] of owners) {
    if (!universe.includes(key)) problems.push(`${key} is claimed by ${names.join(', ')} but is not a pair of the table`);
    else if (names.length === 0) problems.push(`${key} is claimed by no entry, so nothing asserts it`);
    else if (names.length > 1) problems.push(`${key} is claimed by ${names.join(' and ')}; a pair must have one owner`);
  }
  return problems;
}

/*
 * WHAT A FAILURE MEANS FOR A PIN. A manifest pin says "this step fails because of finding N", so the step must fail
 * for that reason and no other. Three things can make a concern fail without the defect being the cause:
 *   - a PRECONDITION: the thing the concern needs is not there (the Forms menu does not list the registration, the
 *     saved-form entry cannot be clicked, the Print button is missing);
 *   - NOT REACHED: an earlier concern failed, so this one never ran;
 *   - OTHER BROWSER PROBLEMS: the concern passed its own assertion, but the browser reported problems, and the
 *     pair declares the signature of the one it is pinned to, so anything else is a new problem.
 * classify() sorts a recorded outcome into "the pair's own failure" and "something else", and the assertion phase
 * throws the second kind under a different step label (`<label> (precondition)`), which the runner reads as a
 * failure elsewhere, not as the known failure.
 */

/** An error that says a step could not be attempted, as opposed to the thing under test being wrong. */
class Precondition extends Error {
  constructor(message) {
    super(message);
    this.name = 'Precondition';
  }
}

function precondition(condition, message) {
  if (!condition) throw new Precondition(message);
}

/** Run `body`; whatever it throws becomes a Precondition (its first line, prefixed when a prefix is given). */
async function asPrecondition(body, prefix) {
  try {
    return await body();
  } catch (error) {
    if (error instanceof Precondition) throw error;
    const first = String(error && error.message || error).split('\n')[0];
    throw new Precondition(prefix ? `${prefix}: ${first}` : first);
  }
}

/** What Playwright says when it could not find, scroll to or click a control: the control, not the app, is the problem. */
const LOCATOR_FAILURE = /^locator\.|Target page|detached|not stable|waiting for/;

/** Run `body`; a failure to reach a control or window (see LOCATOR_FAILURE) becomes a Precondition, anything else passes through. */
async function reaching(body) {
  try {
    return await body();
  } catch (error) {
    if (!(error instanceof Precondition) && LOCATOR_FAILURE.test(String(error && error.message))) {
      throw new Precondition(String(error.message).split('\n')[0]);
    }
    throw error;
  }
}

/**
 * The record of one concern. `error` is what its body threw (if anything) and `problems` the browser problems taken
 * while it ran. kind: assertion (the body threw), precondition, problems (the body passed but the browser reported
 * problems), blocked (see blockedOutcome).
 */
function outcomeOf(error, problems = []) {
  const assertion = error ? String(error.message).split('\n')[0] : null;
  if (!assertion && !problems.length) return { ok: true };
  const parts = [assertion, problems.length ? `${problems.length} browser problem(s): ${problems.join(' | ')}` : null].filter(Boolean);
  return {
    ok: false,
    kind: error ? (error instanceof Precondition ? 'precondition' : 'assertion') : 'problems',
    assertion, problems, message: parts.join(' | '),
  };
}

function blockedOutcome(why) {
  return { ok: false, kind: 'blocked', assertion: null, problems: [], message: `not reached: ${why}` };
}

/**
 * Judge a recorded outcome for the assertion phase.
 *
 * @param {object|undefined} outcome  outcomeOf() / blockedOutcome() result
 * @param {RegExp} [known]  the signature of the browser problem the pair is pinned to; without one, any problem
 *   that ends a concern is that concern's own failure
 * @returns {null|{pinned: boolean, reason?: string, message: string}} null when the concern passed; `pinned` false
 *   means the step must be reported under a label of its own (`reason`), never the pair's pinned label
 */
function classify(outcome, known) {
  if (!outcome) return { pinned: false, reason: 'no outcome', message: 'no outcome was recorded for this concern' };
  if (outcome.ok) return null;
  if (outcome.kind === 'blocked') return { pinned: false, reason: 'not reached', message: outcome.message };
  if (outcome.kind === 'precondition') return { pinned: false, reason: 'precondition', message: outcome.message };
  if (outcome.kind === 'problems' && known) {
    const others = outcome.problems.filter((problem) => !known.test(problem));
    if (others.length) {
      return { pinned: false, reason: 'other browser problems',
        message: `${others.length} browser problem(s) besides the known one: ${others.join(' | ')}` };
    }
  }
  return { pinned: true, message: outcome.message };
}

/**
 * The step a claim phase throws for a recorded outcome, or null when the concern passed.
 *
 * This is the one call a table-driven check makes per claimed pair: only the pair's own failure keeps the pinned
 * `label` (the text a manifest's expectedFailure.step names); a precondition, a concern that was not reached and
 * browser problems beyond the known one come back as `<label> (precondition)`, `<label> (not reached)` and
 * `<label> (other browser problems)`, which no pin names, so the runner reads them as a failure elsewhere.
 *
 * @param {object|undefined} outcome  outcomeOf() / blockedOutcome() result for the pair
 * @param {string} label  the pair's step label
 * @param {RegExp} [known]  signature of the browser problem the pair is pinned to
 * @returns {null|{label: string, message: string}} throw `h.markFailedStep(new Error(message), label)`
 */
function claimFailure(outcome, label, known) {
  const verdict = classify(outcome, known);
  if (!verdict) return null;
  return { label: verdict.pinned ? label : `${label} (${verdict.reason})`, message: verdict.message };
}

module.exports = {
  Precondition, asPrecondition, blockedOutcome, claimFailure, claimKey, classify, effectiveClaims, outcomeOf, parseSpec,
  partitionProblems, precondition, reaching,
};
