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

module.exports = { claimKey, effectiveClaims, parseSpec, partitionProblems };
