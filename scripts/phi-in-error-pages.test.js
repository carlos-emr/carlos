/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Unit tests for the pure parts of scripts/phi-in-error-pages-playwright-checks.js: the leak detector,
// the "did the application write this" classifier and the shape of the probe table. The live behaviour
// (the answers a running install gives) is the check itself.
const assert = require('node:assert/strict');
const test = require('node:test');
const { detector, maskedContext, originOf, describeRoute, FAMILIES } = require('./phi-in-error-pages-playwright-checks');
const { APPLICATION_HEADER } = require('./lib/playwright-harness');

// Fictitious fixture: a 10-digit HIN, FAKE- names and a four-digit demographic_no.
const FIXTURE = {
  hin: '4837261950',
  names: ['FAKE-PW0123456789abcdef', 'FAKE-PWFirst01234567', 'FAKE-PW0123456789abcdef Fixture Lane', 'fake-pw0123456789abcdef@example.invalid'],
  patient: '3208',
};
const find = detector(FIXTURE);

test('shouldFindTheHin_inPlainDashedAndSpacedForm', () => {
  assert.deepEqual(find('<p>card 4837261950 not found</p>'), ['hin']);
  assert.deepEqual(find('card 4837-261-950'), ['hin']);
  assert.deepEqual(find('card 4837 261 950'), ['hin']);
  assert.deepEqual(find('card 5837261950'), []);
});

test('shouldFindEachName_whereverItAppears', () => {
  assert.deepEqual(find('hello FAKE-PW0123456789abcdef'), ['name']);
  assert.deepEqual(find('first FAKE-PWFirst01234567'), ['name']);
  assert.deepEqual(find('<input value="fake-pw0123456789abcdef@example.invalid">'), ['name']);
  assert.deepEqual(find('FAKE-PWffffffffffffffff'), [], 'another run marker is not this fixture');
});

test('shouldCountDemographicNo_onlyAsANumberOfItsOwn', () => {
  assert.deepEqual(find('demographic_no=3208&x=1'), ['demographic_no']);
  assert.deepEqual(find('{"demographicNo":3208}'), ['demographic_no']);
  assert.deepEqual(find('/demographics/3208/'), ['demographic_no']);
  assert.deepEqual(find('Demographic record not found: 3208999'), [], 'a longer number is another record');
  assert.deepEqual(find('id 13208'), []);
  assert.deepEqual(find('ref a3208'), []);
  assert.deepEqual(find('Reference: 3208abcd'), []);
});

test('shouldIgnoreANumberInsideAUuid_soAnIncidentReferenceCannotMatchByChance', () => {
  assert.deepEqual(find('Reference: <code>e07efc2a-3208-4f14-97ba-253cb85828f3</code>'), []);
  assert.deepEqual(find('Reference: 3208 and e07efc2a-da94-4f14-97ba-253cb85828f3'), ['demographic_no']);
});

test('shouldReportEveryKind_whenABodyCarriesSeveral', () => {
  assert.deepEqual(find('4837261950 FAKE-PW0123456789abcdef 3208'), ['hin', 'name', 'demographic_no']);
  assert.deepEqual(find(null), []);
  assert.deepEqual(find(''), []);
});

test('shouldClassifyTheWafPage_asWaf_andNeverAsTheApplication', () => {
  const waf = originOf({ status: 403, headers: {}, body: '<html><center>nginx</center></html>' });
  assert.equal(waf, 'waf');
  // Even if a WAF page somehow carried the header, a ModSecurity page is the front door's.
  assert.equal(originOf({ status: 403, headers: { [APPLICATION_HEADER]: 'none' }, body: '<h1>ModSecurity</h1>' }), 'waf');
});

test('shouldClassifyTheApplicationsOwnAnswers_byHeaderErrorPageOrSecurityPage', () => {
  assert.equal(originOf({ status: 404, headers: { [APPLICATION_HEADER]: 'none' }, body: '' }), 'application');
  assert.equal(originOf({ status: 400, headers: {}, body: '<title> Error Page </title>' }), 'application');
  assert.equal(originOf({ status: 403, headers: {}, body: '<h5>Security Exception</h5> You tried to access a resource with insufficient privileges.' }), 'application');
});

test('shouldNotCallAProxyOrContainerAnswer_theApplications', () => {
  assert.equal(originOf({ status: 400, headers: {}, body: '<html><center><h1>400 Bad Request</h1></center><hr><center>nginx</center></html>' }), 'other');
  assert.equal(originOf({ status: 404, headers: {}, body: '' }), 'other');
});

test('shouldMaskEveryNumberInTheRouteItPrints', () => {
  assert.equal(describeRoute('ws/rs/demographics/{N}999'), 'ws/rs/demographics/{n}');
  assert.equal(describeRoute('ws/rs/demographics/3208'), 'ws/rs/demographics/{n}');
  assert.equal(describeRoute('demographic/DemographicEdit'), 'demographic/DemographicEdit');
});

test('shouldProbeEveryFamilyWithAllFourStatuses_unlessItDeclaresOneNotProvoked', () => {
  const names = FAMILIES.map(family => family.name);
  assert.deepEqual(names.slice(0, 10), ['demographic', 'chart', 'rx', 'lab', 'document', 'tickler', 'billing', 'eform', 'messenger', 'admin']);
  for (const family of FAMILIES) {
    for (const status of [400, 403, 404, 405]) {
      const declaredNone = (family.optional || []).includes(status);
      const probes = family.probes.filter(probe => probe.status === status);
      assert.ok(declaredNone ? probes.length === 0 : probes.length > 0,
        `${family.name} ${status}: ${declaredNone ? 'declared not provoked but a probe exists' : 'no probe'}`);
    }
  }
});

test('shouldCarryThePatientInEveryProbe_andSendOnlyPlaceholdersNeverFixtureValues', () => {
  for (const family of FAMILIES) {
    for (const probe of family.probes) {
      const sent = JSON.stringify({ path: probe.path, query: probe.query, form: probe.form });
      assert.match(sent, /\{(N|HIN|LAST|FIRST)\}/, `${family.name} ${probe.status} ${probe.path} carries no patient placeholder`);
      assert.doesNotMatch(sent, /[0-9]{10}/, `${family.name} ${probe.path} hard-codes a number that could be a health number`);
    }
  }
});

test('shouldSendTheCsrfTokenOnlyWithAProbeMeantToFailBeforeItWrites', () => {
  for (const family of FAMILIES) {
    for (const probe of family.probes) {
      if ((probe.method || 'GET') !== 'POST' || !probe.token) continue;
      // A tokened POST reaches the action: it is for a validation refusal (400), or for a REST resource that
      // does not accept POST at all (405 from the container, before any method of the resource runs).
      const refusesFirst = probe.status === 400 || (probe.status === 405 && probe.path.startsWith('ws/rs/'));
      assert.ok(refusesFirst, `${family.name} ${probe.path}: a POST that carries the CSRF token must be one that cannot write`);
    }
  }
});

test('shouldMaskEveryFixtureValue_inTheContextItPrintsForADeveloper', () => {
  const body = '<div class="msg">no record for FAKE-PW0123456789abcdef: hin 4837261950, first FAKE-PWFirst01234567, id 3208 (e07efc2a-da94-4f14-97ba-253cb85828f3)</div>';
  const context = maskedContext(body, FIXTURE);
  assert.match(context, /<name>/);
  assert.doesNotMatch(context, /FAKE-PW|4837261950|3208|e07efc2a/);
  assert.equal(maskedContext('nothing here', FIXTURE), '');
});
