/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// The patient portal staff page (demographic/portalManage): its decisions, and that every code the
// server can send has translated text on the page.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {createLogic} = require('../src/main/webapp/share/javascript/demographic/portal-manage');

const ROOT = path.join(__dirname, '..');
const read = file => fs.readFileSync(path.join(ROOT, file), 'utf8');
// A bundle's entries as key -> raw value, so keys are compared as plain strings.
function bundleEntries(locale) {
  const entries = new Map();
  for (const line of read(`src/main/resources/oscarResources_${locale}.properties`).split('\n')) {
    const separator = line.indexOf('=');
    if (line.startsWith('#') || separator < 0) continue;
    entries.set(line.slice(0, separator), line.slice(separator + 1));
  }
  return entries;
}

function logic(entries) {
  return createLogic(new Map(Object.entries(entries)));
}

test('describes a delivery by its state, its outcome, and a failed withdrawal', () => {
  const page = logic({
    'deliveries.state.abandoned': 'Stopped',
    'deliveries.outcome.commit_refused': 'The portal refused.',
    'deliveries.revokeFailed': 'It expires on its own.'
  });
  assert.deepEqual(page.describe({state: 'abandoned', outcome: 'commit_refused', revokeFailed: true}),
    ['Stopped', 'The portal refused.', 'It expires on its own.']);
  assert.deepEqual(page.describe({state: 'abandoned', outcome: null, revokeFailed: false}), ['Stopped']);
});

test('warns that an unconfirmed replacement may have taken the old code with it', () => {
  const page = logic({'deliveries.replacementMayBeLost': 'The earlier one may not work.'});
  const resend = {state: 'abandoned', outcome: 'commit_unconfirmed', supersededInviteId: 7};
  const first = {state: 'abandoned', outcome: 'commit_unconfirmed', supersededInviteId: null};
  // An attempt stopped before it queued cannot have activated the replacement.
  const stoppedBeforeActivation = {state: 'abandoned', outcome: 'abandoned_by_staff', supersededInviteId: 7};
  assert.ok(page.describe(resend).includes('The earlier one may not work.'));
  assert.ok(!page.describe(first).includes('The earlier one may not work.'));
  assert.ok(!page.describe(stoppedBeforeActivation).includes('The earlier one may not work.'));
});

test('shows a known refusal in the page language, and any other as the server worded it', () => {
  const page = logic({'refusal.missing_email': 'Kein E-Mail.', 'error.generic': 'Fehler.'});
  assert.equal(page.refusal({reason: 'missing_email', message: 'No email.'}), 'Kein E-Mail.');
  assert.equal(page.refusal({reason: 'consent_blocked', message: 'Consent blocks this email.'}),
    'Consent blocks this email.');
  assert.equal(page.refusal(null), 'Fehler.');
  assert.equal(page.refusal({}), 'Fehler.');
});

test('never builds a selector from a portal value: an odd status is looked up, not interpreted', () => {
  const page = logic({});
  assert.equal(page.text('invites.status."] , *'), 'invites.status."] , *');
});

test('calls what staff asked for good news, unless the code could not be withdrawn', () => {
  const page = logic({});
  assert.equal(page.isGoodNews({state: 'abandoned', outcome: 'abandoned_by_staff', revokeFailed: false}), true);
  assert.equal(page.isGoodNews({state: 'revoked', outcome: 'confirmed_not_sent', revokeFailed: false}), true);
  assert.equal(page.isGoodNews({state: 'abandoned', outcome: 'abandoned_by_staff', revokeFailed: true}), false);
  assert.equal(page.isGoodNews({state: 'abandoned', outcome: 'commit_unconfirmed', revokeFailed: false}), false);
  assert.equal(page.isGoodNews({state: 'revoking', outcome: 'send_unconfirmed', revokeFailed: false}), false);
});

test('shows no replacement question for a press made while a request is running', () => {
  const page = logic({});
  const pending = [{status: 'accepted'}, {status: 'pending'}];
  assert.equal(page.inviteStep(true, pending), 'ignore');
  assert.equal(page.inviteStep(true, []), 'ignore');
  assert.equal(page.inviteStep(false, pending), 'confirm');
  assert.equal(page.inviteStep(false, [{status: 'revoked'}]), 'send');
  assert.equal(page.inviteStep(false, undefined), 'send');
});

test('calls a delivered invitation good news unless its chart note failed', () => {
  const page = logic({});
  assert.equal(page.isGoodNews({state: 'sent', outcome: null}), true);
  assert.equal(page.isGoodNews({state: 'sent', outcome: 'confirmed_sent'}), true);
  assert.equal(page.isGoodNews({state: 'sent', outcome: 'chart_note_failed'}), false);
  assert.equal(page.isGoodNews({state: 'abandoned', outcome: 'commit_refused'}), false);
  assert.equal(page.isGoodNews({state: 'send_uncertain', outcome: 'send_unconfirmed'}), false);
  assert.equal(page.isGoodNews(undefined), true, 'a request that returns no delivery, such as a revoke');
});

test('says why an unfinished attempt offers no decision: still settling, or another portal connection', () => {
  const page = logic({
    'deliveries.waiting': 'Wait 15 minutes.',
    'refusal.portal_connection_changed': 'Restore that connection.'
  });
  assert.equal(page.waitingFor({finished: false, onCurrentConnection: true}), 'Wait 15 minutes.');
  assert.equal(page.waitingFor({finished: false, onCurrentConnection: false}), 'Restore that connection.');
});

test('offers withdrawing a stuck attempt once, never again on the retry', () => {
  const page = logic({});
  const stuck = {ok: false, reason: 'stale_attempt_exists'};
  assert.equal(page.offersWithdrawal(stuck, {method: 'create'}), true);
  assert.equal(page.offersWithdrawal(stuck, {method: 'create', withdrawStale: 'true'}), false);
  assert.equal(page.offersWithdrawal({ok: false, reason: 'missing_email'}, {}), false);
  assert.equal(page.offersWithdrawal(null, {}), false);
});

/** Constants of a Java enum, read from source, lower-cased as InviteDeliveryJson sends them. */
function enumCodes(file, enumName) {
  // Comments first: their prose can hold the ';' or '}' that ends the constant list.
  const source = read(file).replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  const start = source.indexOf(`enum ${enumName} {`);
  assert.ok(start >= 0, `${enumName} not found in ${file}`);
  const open = source.indexOf('{', start) + 1;
  const ends = [source.indexOf(';', open), source.indexOf('}', open)].filter(index => index >= 0);
  const constants = source.slice(open, Math.min(...ends));
  return constants.split(',').map(entry => entry.trim().split('(')[0].trim()).filter(Boolean)
    .map(name => name.toLowerCase());
}

function pageKeys() {
  const jsp = read('src/main/webapp/WEB-INF/jsp/demographic/portalManage.jsp');
  const lists = [...jsp.matchAll(/<c:forTokens var="key" delims="," items="([^"]+)">/g)].map(m => m[1]);
  return new Set(lists.flatMap(list => list.split(',')));
}

test('has page text for every delivery state and outcome the server can send', () => {
  const keys = pageKeys();
  const model = 'src/main/java/io/github/carlos_emr/carlos/commn/model/PatientPortalInviteDelivery.java';
  const states = enumCodes(model, 'State');
  const outcomes = enumCodes(model, 'Outcome');
  assert.ok(states.length >= 9 && outcomes.length >= 11, 'the enums were read');
  for (const state of states) assert.ok(keys.has(`deliveries.state.${state}`), `deliveries.state.${state}`);
  for (const outcome of outcomes) {
    assert.ok(keys.has(`deliveries.outcome.${outcome}`), `deliveries.outcome.${outcome}`);
  }
});

test('has page text for every refusal code, except the one that carries its own explanation', () => {
  const keys = pageKeys();
  const source = read('src/main/java/io/github/carlos_emr/carlos/integration/patientportal/PortalInviteException.java');
  const codes = [...source.matchAll(/^\s*[A-Z_]+\("([a-z_]+)"/gm)].map(match => match[1]);
  assert.ok(codes.length >= 10, 'the reason codes were read');
  for (const code of codes.filter(code => code !== 'consent_blocked')) {
    assert.ok(keys.has(`refusal.${code}`), `refusal.${code}`);
  }
});

// Words that are the same in English and the translation. Everything else must be translated.
const SAME_AS_ENGLISH = {
  fr: new Set(['invites.heading']),
  es: new Set(),
  pl: new Set(['account.field.status', 'invites.status']),
  pt_BR: new Set(['account.field.status', 'invites.status'])
};

test('has translated text in every bundle for every portal page key', () => {
  const english = bundleEntries('en');
  const prefix = 'demographic.portal.';
  const keys = [...english.keys()].filter(key => key.startsWith(prefix)).map(key => key.slice(prefix.length));
  assert.ok(keys.length >= 100, 'the portal keys were read');
  for (const key of pageKeys()) assert.ok(english.get(prefix + key), `en: ${key}`);
  for (const locale of Object.keys(SAME_AS_ENGLISH)) {
    const bundle = bundleEntries(locale);
    for (const key of keys) {
      const value = bundle.get(prefix + key);
      assert.ok(value, `${locale}: ${key}`);
      if (!SAME_AS_ENGLISH[locale].has(key)) {
        assert.notEqual(value, english.get(prefix + key), `${locale}: ${key} is not translated`);
      }
    }
  }
});

test('has text in every bundle for every label the page prints directly', () => {
  const jsp = read('src/main/webapp/WEB-INF/jsp/demographic/portalManage.jsp');
  const keys = [...new Set([...jsp.matchAll(/<fmt:message key="([A-Za-z0-9_.]+)"/g)].map(m => m[1]))];
  assert.ok(keys.length >= 20, 'the labels were read');
  for (const locale of ['en', 'es', 'fr', 'pl', 'pt_BR']) {
    const bundle = bundleEntries(locale);
    for (const key of keys) assert.ok(bundle.get(key), `${locale}: ${key}`);
  }
});

// Run the production closure with minimal DOM inputs. Expose its request boundary instead of
// performing the initial panel load, so these checks exercise the actual CSRF-to-fetch behavior.
function requestBoundary(ready, token) {
  const requests = [];
  const sandbox = {
    URLSearchParams,
    window: {csrfTokenReady: ready},
    document: {
      getElementById: id => id === 'portal-manage'
        ? {dataset: {context: '/carlos', demographicNo: '123'}} : {addEventListener() {}},
      querySelectorAll: () => [],
      querySelector: () => token
    },
    fetch: async (url, options) => {
      requests.push({url, options});
      return {status: 200, json: async () => ({ok: true})};
    }
  };
  const source = read('src/main/webapp/share/javascript/demographic/portal-manage.js');
  assert.match(source, /    load\(\);\s*}\(\)\);\s*$/);
  vm.runInNewContext(source.replace(/    load\(\);(?=\s*}\(\)\);\s*$)/,
    '    globalThis.portalRequest = call;'), sandbox);
  return {call: sandbox.portalRequest, requests};
}

test('waits for CSRF bootstrap before posting with its token', async () => {
  let resolve;
  const ready = new Promise(done => {resolve = done;});
  const token = {value: ''};
  const boundary = requestBoundary(ready, token);
  const pending = boundary.call('POST', '/demographic/portalInvite', {method: 'create'});
  await Promise.resolve();
  assert.equal(boundary.requests.length, 0);
  token.value = 'fixture-csrf';
  resolve();
  await pending;
  assert.equal(boundary.requests.length, 1);
  assert.equal(boundary.requests[0].options.headers['CSRF-TOKEN'], 'fixture-csrf');
});

test('sends no dependent POST when CSRF bootstrap rejects', async () => {
  const boundary = requestBoundary(Promise.reject(new Error('bootstrap refused')), {value: 'stale'});
  await assert.rejects(boundary.call('POST', '/demographic/portalInvite'), /bootstrap refused/);
  assert.equal(boundary.requests.length, 0);
});

test('sends no dependent POST when the CSRF input or value is unavailable', async () => {
  for (const token of [null, {value: ''}, {value: '  '}]) {
    const boundary = requestBoundary(Promise.resolve(), token);
    await assert.rejects(boundary.call('POST', '/demographic/portalInvite'), /CSRF token is unavailable/);
    assert.equal(boundary.requests.length, 0);
  }
});
