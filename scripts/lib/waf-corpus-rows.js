/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * The rows of waf-clinical-text-corpus that need no browser to describe: what each is called, which
 * exclusion(s) it is meant to test, its route and method, and the prose argument(s) it fills.
 *
 * Read by the check (scripts/waf-clinical-text-corpus-playwright-checks.js) and by
 * scripts/waf-clinical-text-corpus.test.js, which holds this table against the packaged exclusion files: a
 * route or rule renamed in the conf, or a row that fills an argument its rule does not unhook, fails there
 * before it fails against a live install.
 *
 *   corpusStep the label of the row's corpus step, as written in the step (see corpusStepLabel)
 *   rules     ids in REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf that are meant to let `args` through `route`
 *   afterCrs  the config-time pattern in RESPONSE-999-EXCLUSION-RULES-AFTER-CRS.conf a row without a numbered rule relies on
 *   args      the prose arguments the row fills, as the exclusion names them (comments-<n> stands for the row-indexed names)
 *
 * Exclusions this check does not drive, and why:
 *   1100 and 1131       the consultation request and the master record's Alert/Notes: clinical-freetext
 *   1400, 1401, 1402    report authoring: report-query-by-example-front-door and report-by-template-editor-front-door
 *   1137                BC billing reprocess notes: no British Columbia work is in scope (issue #4439)
 *   1010 beyond the note body: the CPP editor (value and the six prose boxes), the draft autosave (note) and the
 *                       save-on-switch (noteTxt) name arguments no row here posts
 *   1020, 1050, 1060, 1070, 1080  signature and eForm-editor routes whose arguments are data URIs and HTML documents, not prose
 *   1046-1049, 1088-1090  label layouts and the Host header rule: not prose
 *   1101-1103, 1106, 1110-1112, 1114, 1115, 1119, 1121, 1122, 1124, 1127-1133, 1138-1140, 1142
 *                       prose routes outside this plan's route list, still open for a later row
 */

/** @type {Record<string, {title: string, rules: string[], afterCrs?: string[], route: string, method: string, args: string[]}>} */
const FACTS = {
  'tickler-add': {
    title: 'tickler add',
    corpusStep: 'tickler add: the clinical corpus saves through /tickler/DbTicklerAdd and lands exactly',
    rules: ['1104'], route: 'tickler/DbTicklerAdd', method: 'POST', args: ['ticklerMessage'],
  },
  'tickler-edit': {
    title: 'tickler edit (append a comment)',
    corpusStep: 'tickler edit (append a comment): the clinical corpus saves through /tickler/EditTickler and lands exactly',
    rules: ['1105'], route: 'tickler/EditTickler', method: 'POST', args: ['newMessage'],
  },
  'tickler-list': {
    title: 'tickler list search (shows the saved message)',
    corpusStep: 'tickler list search (shows the saved message): the clinical corpus saves through /tickler/ListTicklers and lands exactly',
    rules: ['1141'], route: 'tickler/ListTicklers', method: 'GET', args: ['search[value]'],
  },
  'rx-special-instruction': {
    title: 'Rx special instruction (staged, then Save Only)',
    corpusStep: 'Rx special instruction (staged, then Save Only): the clinical corpus saves through /rx/WriteScript and lands exactly',
    rules: ['1108'], route: 'rx/WriteScript', method: 'POST', args: ['specialInstruction'],
  },
  // 1107 exempts customName on rx/writeScript; the page posts it to rx/WriteScript, where only `name` is exempt (finding 210).
  'rx-custom-drug-name': {
    title: 'Rx custom drug name (staged, renamed, then Save Only)',
    corpusStep: 'Rx custom drug name (staged, renamed, then Save Only): the clinical corpus saves through /rx/WriteScript and lands exactly',
    rules: ['1108'], route: 'rx/WriteScript', method: 'POST', args: ['name'],
  },
  'rx-update-script': {
    title: 'Rx sig (parsed on blur, then Save Only)',
    corpusStep: 'Rx sig (parsed on blur, then Save Only): the clinical corpus saves through /rx/UpdateScript and lands exactly',
    rules: ['1109'], route: 'rx/UpdateScript', method: 'POST', args: ['instruction'],
  },
  'allergy-add': {
    title: 'allergy add',
    corpusStep: 'allergy add: the clinical corpus saves through /rx/addAllergy2 and lands exactly',
    rules: ['1113'], route: 'rx/addAllergy2', method: 'POST', args: ['reactionDescription'],
  },
  'measurement-data': {
    title: 'measurement dialog (instruction)',
    corpusStep: 'measurement dialog (instruction): the clinical corpus saves through /encounter/MeasurementData and lands exactly',
    rules: ['1116'], route: 'encounter/MeasurementData', method: 'POST', args: ['instruction'],
  },
  'measurement-comment': {
    title: 'measurement comment (Vitals)',
    corpusStep: 'measurement comment (Vitals): the clinical corpus saves through /encounter/Measurements and lands exactly',
    rules: [], afterCrs: ['!ARGS:/^comments-[0-9]+$/'], route: 'encounter/Measurements', method: 'POST', args: ['comments-<n>'],
  },
  'prevention-add': {
    title: 'prevention add',
    corpusStep: 'prevention add: the clinical corpus saves through /prevention/AddPrevention and lands exactly',
    rules: ['1117'], route: 'prevention/AddPrevention', method: 'POST', args: ['comments', 'reason'],
  },
  'manage-document': {
    title: 'document description (viewer Save)',
    corpusStep: 'document description (viewer Save): the clinical corpus saves through /documentManager/ManageDocument and lands exactly',
    rules: ['1118'], route: 'documentManager/ManageDocument', method: 'POST', args: ['documentDescription'],
  },
  'lab-status': {
    title: 'lab acknowledge with a comment',
    corpusStep: 'lab acknowledge with a comment: the clinical corpus saves through /oscarMDS/UpdateStatus and lands exactly',
    rules: ['1120'], route: 'oscarMDS/UpdateStatus', method: 'POST', args: ['comment'],
  },
  'messenger-send': {
    title: 'messenger send',
    corpusStep: 'messenger send: the clinical corpus saves through /messenger/CreateMessage and lands exactly',
    rules: ['1123'], route: 'messenger/CreateMessage', method: 'POST', args: ['subject', 'message'],
  },
  'appointment-add': {
    title: 'appointment add',
    corpusStep: 'appointment add: the clinical corpus saves through /appointment/AddRecord and lands exactly',
    rules: ['1125'], route: 'appointment/AddRecord', method: 'POST', args: ['reason', 'notes'],
  },
  'appointment-update': {
    title: 'appointment update',
    corpusStep: 'appointment update: the clinical corpus saves through /appointment/UpdateRecord and lands exactly',
    rules: ['1126'], route: 'appointment/UpdateRecord', method: 'POST', args: ['reason', 'notes'],
  },
  'chart-note': {
    title: 'chart note (case-management entry)',
    corpusStep: 'chart note (case-management entry): the clinical corpus saves through /CaseManagementEntry and lands exactly',
    rules: ['1010'], route: 'CaseManagementEntry', method: 'POST', args: ['caseNote_note'],
  },
  'eform-letter': {
    title: 'eForm save (Rich Text Letter)',
    corpusStep: 'eForm save (Rich Text Letter): the clinical corpus saves through /eform/addEForm and lands exactly',
    rules: ['1030', '1040', '1045'], route: 'eform/addEForm', method: 'POST', args: ['Letter'],
  },
  'billing-on-save': {
    title: 'Ontario bill save (invoice comment)',
    corpusStep: 'Ontario bill save (invoice comment): the clinical corpus saves through /billing/CA/ON/BillingONSave and lands exactly',
    rules: ['1134'], route: 'billing/CA/ON/BillingONSave', method: 'POST', args: ['comment'],
  },
  'billing-on-correction': {
    title: 'Ontario bill correction (billing notes)',
    corpusStep: 'Ontario bill correction (billing notes): the clinical corpus saves through /billing/CA/ON/UpdateBillingONCorrection and lands exactly',
    rules: ['1135'], route: 'billing/CA/ON/UpdateBillingONCorrection', method: 'POST', args: ['comment'],
  },
  'billing-on-display': {
    title: 'Ontario invoice display (history comment, display only)',
    corpusStep: 'Ontario invoice display (history comment, display only): the clinical corpus saves through /billing/CA/ON/ViewBillingONDisplay and lands exactly',
    rules: ['1136'], route: 'billing/CA/ON/ViewBillingONDisplay', method: 'POST', args: ['comment'],
  },
};

/** The order the rows run in and report in: the plan's route list. */
const ROW_ORDER = [
  'tickler-add', 'tickler-edit', 'tickler-list', 'rx-special-instruction', 'rx-custom-drug-name', 'rx-update-script', 'allergy-add',
  'measurement-data', 'measurement-comment', 'prevention-add', 'manage-document', 'lab-status', 'messenger-send',
  'appointment-add', 'appointment-update', 'chart-note', 'eform-letter', 'billing-on-save', 'billing-on-correction',
  'billing-on-display',
];

const names = (value) => (value || '').split(',').map((name) => name.trim()).filter(Boolean);

/**
 * The rows a run covers: ROW_ORDER, narrowed by WAF_CORPUS_ONLY and WAF_CORPUS_SKIP. A name that is not a row
 * is an error rather than a silent skip, or a typo would turn a pinned entry into one that runs nothing.
 */
function selectedKeys(env = process.env) {
  const only = names(env.WAF_CORPUS_ONLY);
  const skip = names(env.WAF_CORPUS_SKIP);
  for (const key of [...only, ...skip]) {
    if (!ROW_ORDER.includes(key)) throw new Error(`unknown row "${key}"; the rows are ${ROW_ORDER.join(', ')}`);
  }
  const keys = ROW_ORDER.filter((key) => (!only.length || only.includes(key)) && !skip.includes(key));
  if (!keys.length) throw new Error('WAF_CORPUS_ONLY / WAF_CORPUS_SKIP leave no row to run');
  return keys;
}

/** The label of a row's control step: the real UI save with plain text, before the corpus (never pinned). */
const controlStepLabel = (key) => `${FACTS[key].title}: the real UI save goes through the front door (control)`;

/**
 * The label of a row's corpus step, which is what a manifest `expectedFailure.step` names: the runner matches a
 * known failure on the step label alone. It is a literal in FACTS (`corpusStep`), not built here, because
 * playwright-suite-manifest.test.js proves a pin by finding the label's text in the script or the modules it requires;
 * waf-clinical-text-corpus.test.js holds each literal to `<title>: the clinical corpus saves through /<route> and lands exactly`.
 */
const corpusStepLabel = (key) => FACTS[key].corpusStep;

module.exports = { FACTS, ROW_ORDER, controlStepLabel, corpusStepLabel, selectedKeys };
