/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
// The patient portal staff page (demographic/portalManage): its decisions, and that every code the
// server can send has translated text on the page.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {createLogic} = require('../src/main/webapp/share/javascript/demographic/portal-manage');

const ROOT = path.join(__dirname, '..');
const read = file => fs.readFileSync(path.join(ROOT, file), 'utf8');

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
  assert.ok(page.describe(resend).includes('The earlier one may not work.'));
  assert.ok(!page.describe(first).includes('The earlier one may not work.'));
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

test('calls only a delivered invitation good news', () => {
  const page = logic({});
  assert.equal(page.isGoodNews({state: 'sent', outcome: null}), true);
  assert.equal(page.isGoodNews({state: 'sent', outcome: 'confirmed_sent'}), true);
  assert.equal(page.isGoodNews({state: 'sent', outcome: 'chart_note_failed'}), false);
  assert.equal(page.isGoodNews({state: 'abandoned', outcome: 'commit_refused'}), false);
  assert.equal(page.isGoodNews({state: 'send_uncertain', outcome: 'send_unconfirmed'}), false);
  assert.equal(page.isGoodNews(undefined), true, 'a request that returns no delivery, such as a revoke');
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

test('has English text in the bundle for every key the page lists', () => {
  const bundle = read('src/main/resources/oscarResources_en.properties');
  for (const key of pageKeys()) {
    assert.match(bundle, new RegExp(`^demographic\\.portal\\.${key.replace(/\./g, '\\.')}=.+$`, 'm'), key);
  }
});
