const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createHash } = require('node:crypto');
const { prepareOwnedMasterPostal, assertOwnedMasterPostal } = require('./lib/owned-master-postal');
const { prepareTicklerAssignee } = require('./boundary-messaging-text-playwright-checks');
const { installSearchMonitorCleanup, registerExactSearchPatientCleanup } = require('./search-sort-appt-patient-pages-playwright-checks');
const searchHelpers = require('./lib/search-sort-helpers');

const BASE = process.env.PR5044_BASE_REPO || path.resolve(__dirname, '..');
const MARK = 'FAKE-PW0123456789abcdef';
const jsp = name => fs.readFileSync(path.join(BASE, 'src/main/webapp/WEB-INF/jsp', name), 'utf8');
// Execute the native validators rather than reproducing their conditions.
function nativePostal(form) {
  const source = jsp('demographic/demographiceditdemographic.js.jsp');
  const postal = source.slice(source.indexOf('function isPostalCode()'), source.indexOf('function isPostalCode2()'));
  const canadian = source.slice(source.indexOf('function isCanadian()'), source.indexOf('function updateProvinces('));
  const context = { document: { updatedelete: form }, alert() {} };
  vm.runInNewContext(`${postal}\n${canadian}\nresult = isPostalCode();`, context);
  return context.result;
}
function nativeTickler(form) {
  const source = jsp('tickler/ticklerAdd.jsp');
  const validator = source.slice(source.indexOf('function validateDemoNo()'), source.indexOf('function refresh()', source.indexOf('function validateDemoNo()')))
    .replace(/<%[\s\S]*?%>/g, '').replace(/<carlos:encode[\s\S]*?\/>/g, 'native-validation-message');
  const context = { document: { serviceform: form }, CarlosTicklerValidation: { show() {} }, IsDate: value => !Number.isNaN(new Date(value).getTime()) };
  vm.runInNewContext(`${validator}\nresult = validateDemoNo();`, context);
  return context.result;
}
function postalFixture() {
  const state = { owned: true, province: 'ON', postal: '', uiPostal: 'K1A0B1', uiProvince: 'ON', country: 'CA', iso: false,
    origin: '', writes: [], reloads: 0, store: true, url: 'https://127.0.0.1/carlos/demographic/DemographicEdit?demographic_no=3333' };
  const s = { patient: '3333', marker: MARK, sql: {
    rows(q) {
      assert.equal(q, `SELECT province, IFNULL(postal,'') FROM demographic WHERE demographic_no=3333 AND last_name='${MARK}'`);
      return state.owned ? [[state.province, state.postal]] : [];
    },
    execute(q) {
      assert.equal(q, `UPDATE demographic SET postal='K1A0B1' WHERE demographic_no=3333 AND last_name='${MARK}' AND province='ON'`);
      state.writes.push(q); if (state.store) state.postal = 'K1A0B1';
    },
  }, master: {
    url: () => state.url,
    async reload(options) { assert.deepEqual(options, { waitUntil: 'domcontentloaded' }); state.reloads++; },
    locator(selector) {
      if (selector === 'input[name="postal"]') return { inputValue: async () => state.uiPostal };
      if (selector === 'select[name="province"]') return { inputValue: async () => state.uiProvince,
        locator: () => ({ first: () => ({ waitFor: async () => {} }) }) };
      if (selector === 'select[name="country"]') return { count: async () => Number(state.iso), inputValue: async () => state.country };
      throw Error(`Unexpected postal locator ${selector}`);
    },
  } };
  return { s, state };
}
const postalForm = state => ({ postal: { value: state.postal, focus() {} }, province: { options: [{ value: state.uiProvince }], selectedIndex: 0 } });
test('original native Canadian form rejects the unseeded workflow postal fixture', () => {
  const { state } = postalFixture(); assert.equal(nativePostal(postalForm(state)), false);
});
test('owned postal setup makes native validation pass and leaves country of origin unchanged', async () => {
  const { s, state } = postalFixture(); await prepareOwnedMasterPostal(s);
  assert.equal(nativePostal(postalForm(state)), true); assert.equal(state.origin, '');
  assert.equal(state.writes.length, 1); assert.equal(state.reloads, 1);
});
for (const patient of ['0', '01', '-1', '2147483648', '1 OR 1=1']) test(`postal setup refuses invalid patient ${patient}`, async () => {
  const { s, state } = postalFixture(); s.patient = patient;
  await assert.rejects(prepareOwnedMasterPostal(s), /identity is invalid/); assert.equal(state.writes.length, 0);
});
test('postal setup refuses a foreign patient before mutation', async () => {
  const { s, state } = postalFixture(); state.owned = false;
  await assert.rejects(prepareOwnedMasterPostal(s), /ownership changed/); assert.equal(state.writes.length, 0);
});
test('postal setup refuses a bad marker before SQL', async () => {
  const { s, state } = postalFixture(); s.marker = "FAKE' OR 1=1";
  await assert.rejects(prepareOwnedMasterPostal(s), /marker is invalid/); assert.equal(state.writes.length, 0);
});
test('postal setup refuses a non-Canadian fixture instead of bypassing validation', async () => {
  const { s, state } = postalFixture(); state.province = 'US-NY';
  await assert.rejects(prepareOwnedMasterPostal(s), /Canadian province/); assert.equal(state.writes.length, 0);
});
test('postal setup detects a failed write before reload', async () => {
  const { s, state } = postalFixture(); state.store = false;
  await assert.rejects(prepareOwnedMasterPostal(s), /not stored/); assert.equal(state.reloads, 0);
});
test('postal setup refuses a redirected patient', async () => {
  const { s, state } = postalFixture(); state.url = state.url.replace('3333', '4444');
  await assert.rejects(prepareOwnedMasterPostal(s), /another patient/);
});
for (const field of ['postal', 'uiPostal', 'uiProvince', 'country']) test(`postal pre-submit assertion rejects changed ${field} without healing it`, async () => {
  const { s, state } = postalFixture(); state.iso = true; await prepareOwnedMasterPostal(s);
  state[field] = field === 'uiProvince' ? 'OT' : field === 'country' ? 'US' : '';
  await assert.rejects(assertOwnedMasterPostal(s)); assert.equal(state.writes.length, 1);
});
function ticklerFixture() {
  const state = { patient: '3333', provider: '999998', site: true, visible: true, names: ['Native clinic'], offered: ['none', 'Native clinic'],
    siteValue: 'none', authorized: ['Native clinic'], assignees: [], selected: '', writes: 0, selections: [], waited: false };
  const options = () => state.assignees.map(value => ({ value, disabled: false, closest: () => null }));
  const assignee = { locator() { return { filter: () => ({ first: () => ({ waitFor: async () => {} }) }),
    evaluateAll: async (fn, value) => fn(options(), value) }; },
    async selectOption(value) { assert.ok(state.assignees.includes(value)); state.selected = value; state.selections.push(['assignee', value]); } };
  const site = { count: async () => Number(state.site), isVisible: async () => state.visible,
    locator: () => ({ evaluateAll: async fn => fn(state.offered.map(value => ({ value, disabled: false, closest: () => null }))) }),
    async selectOption(value) { assert.ok(state.offered.includes(value)); state.siteValue = value;
      state.assignees = state.names.includes(value) ? [state.provider] : []; state.selections.push(['site', value]); } };
  const add = { locator(selector) {
    if (selector === 'form[name="serviceform"] input[name="demographic_no"]') return { last: () => ({ inputValue: async () => state.patient }) };
    if (selector === 'select[name="site"]') return site;
    if (selector === 'select[name="task_assigned_to"]') return { first: () => assignee };
    if (selector === '#nameWrapper a') return { click: async () => { state.visible = true; } };
    throw Error(`Unexpected tickler locator ${selector}`);
  }, async waitForFunction(fn, args) {
    const form = { elements: { namedItem: name => ({ value: name === 'demographic_no' ? state.patient : state.selected }) } };
    const result = vm.runInNewContext(`(${fn.toString()})(args)`, { document: { forms: { namedItem: () => form } }, args });
    assert.equal(result, true); state.waited = true;
  } };
  const s = { patient: '3333', provider: '999998', sql: {
    rows(q) { assert.match(q, /ps\.provider_no='999998' AND s\.status<>0/); return state.authorized.map(name => [name]); },
    execute() { state.writes++; throw Error('Tickler setup must be read-only'); },
  } };
  return { s, add, state };
}
const ticklerForm = state => ({ demographic_no: { value: state.patient }, xml_appointment_date: { value: '2026-10-21' },
  task_assigned_to: { options: state.assignees.map(value => ({ value, disabled: false, closest: () => null })), value: state.selected } });
test('original native multisite form rejects the omitted clinic and empty assignee', () => {
  const { state } = ticklerFixture(); assert.equal(nativeTickler(ticklerForm(state)), false);
});
test('native authorized clinic and selected login assignee satisfy the original validator', async () => {
  const { s, add, state } = ticklerFixture(); await prepareTicklerAssignee(s, add);
  assert.deepEqual(state.selections, [['site', 'Native clinic'], ['assignee', '999998']]);
  assert.equal(state.waited, true); assert.equal(state.writes, 0); assert.equal(nativeTickler(ticklerForm(state)), true);
});
test('native preference view is opened using its link before clinic selection', async () => {
  const { s, add, state } = ticklerFixture(); state.visible = false; await prepareTicklerAssignee(s, add);
  assert.equal(state.visible, true); assert.equal(state.waited, true);
});
test('tickler setup refuses a different patient before selecting anything', async () => {
  const { s, add, state } = ticklerFixture(); state.patient = '4444';
  await assert.rejects(prepareTicklerAssignee(s, add), /another patient/); assert.deepEqual(state.selections, []);
});
for (const mode of ['unauthorized', 'not-offered', 'ambiguous', 'duplicate-authorized']) test(`tickler setup refuses ${mode} clinic`, async () => {
  const { s, add, state } = ticklerFixture();
  if (mode === 'unauthorized') state.authorized = ['Other clinic'];
  if (mode === 'not-offered') state.offered = ['none'];
  if (mode === 'ambiguous') state.offered.push('Native clinic');
  if (mode === 'duplicate-authorized') state.authorized.push('Native clinic');
  await assert.rejects(prepareTicklerAssignee(s, add), /No unambiguous authorized clinic/); assert.deepEqual(state.selections, []);
});
test('tickler setup refuses a missing login assignee rather than choosing another provider', async () => {
  const { s, add, state } = ticklerFixture(); state.names = [];
  await assert.rejects(prepareTicklerAssignee(s, add), /configured login/); assert.equal(state.selected, ''); assert.equal(state.waited, false);
});
test('non-multisite form still explicitly selects the offered login assignee', async () => {
  const { s, add, state } = ticklerFixture(); state.site = false; state.assignees = ['999998'];
  await prepareTicklerAssignee(s, add); assert.deepEqual(state.selections, [['assignee', '999998']]); assert.equal(state.waited, true);
});

test('Field Note detail uses the native required bounded dates in the same shared positive/role-control URL', () => {
  const source = fs.readFileSync(path.join(__dirname, 'authz-read-admin-objects-playwright-checks.js'), 'utf8');
  const declaration = source.match(/const FIELD_NOTE = ([\s\S]*?);/)[1];
  const routes = vm.runInNewContext(declaration);
  const detail = new URL(routes[1], 'https://127.0.0.1/carlos/');
  assert.equal(detail.searchParams.get('residentId'), '0'); assert.equal(detail.searchParams.get('residentName'), 'x');
  assert.equal(detail.searchParams.get('date_start'), '2026-01-01'); assert.equal(detail.searchParams.get('date_end'), '2026-12-31');
  const native = jsp('eform/fieldNoteReport/fieldnotereportdetail.jsp');
  assert.match(native, /request\.getParameter\("date_start"\)/); assert.match(native, /request\.getParameter\("date_end"\)/);
  assert.match(native, /FieldNoteDateRange\.parse\(dateStart, dateEnd\)/);
  assert.match(source, /\[\.\.\.FIELD_NOTE,/); assert.match(source, /for \(const route of FIELD_NOTE\)/);
  assert.match(source, /classify\(await get\('full', route\)\) !== 'served'/);
  assert.match(source, /classify\(await get\('doctor', route\)\) === 'served'/);
});

function monitorFixture() {
  const cookie = 'synthetic-test-only-session';
  const hash = createHash('sha256').update(cookie).digest('hex');
  const state = { owned: true, connected: false, cookies: true, rows: [], callbacks: [], deleted: [],
    url: 'https://127.0.0.1/carlos/provider/providercontrol?displaymode=day', date: '2026-10-21', provider: '999998' };
  const patients = Array.from({ length: 19 }, (_, i) => String(3333 + i));
  const native = { providerNo: '999998', date: '2026-10-21', location: '', multisites: false };
  const pageId = '999998|2026-10-21|09:00|09:14';
  const scoped = (row, q) => {
    const idList = q.match(/id IN \(([\d,]+)\)/)?.[1].split(',');
    const session = q.match(/SHA2\(session,256\)='([a-f0-9]{64})'/)?.[1];
    const page = q.match(/AND pageId='([^']+)'/)?.[1];
    const provider = q.match(/AND providerNo='([^']+)'/)?.[1];
    return (!idList || idList.includes(row.id)) && (!session || row.hash === session)
      && (!page || row.pageId === page) && (!provider || row.provider === provider)
      && (!q.includes("pageName='addappointment'") || row.pageName === 'addappointment');
  };
  const s = { marker: MARK, provider: '999998', config: { baseUrl: new URL('https://127.0.0.1/carlos') },
    cleanup(fn) { state.callbacks.push(fn); }, context: {
      browser: () => ({ isConnected: () => state.connected }), cookies: async () => state.cookies ? [{ name: 'JSESSIONID', value: cookie, path: '/carlos' }] : [],
    }, schedule: {
      url: () => state.url, locator(selector) {
        if (selector === 'select#site[name="site"]') return { count: async () => 0 };
        const values = { '#scheduleLoggedInProviderNo': state.provider, '#scheduleCurrentDate': state.date, '#scheduleVisibleProviderNos': state.provider };
        assert.ok(Object.hasOwn(values, selector)); return { inputValue: async () => values[selector] };
      },
    }, sql: {
      value(q) {
        if (q.includes('FROM demographic WHERE')) { assert.ok(q.includes(`demographic_no IN (${patients.join(',')})`));
          assert.ok(q.includes("last_name LIKE 'FAKE-PWabcdef-%'")); assert.ok(q.includes("provider_no='999998'")); return state.owned ? '19' : '18'; }
        assert.match(q, /^SELECT COUNT\(\*\) FROM PageMonitor WHERE id IN/);
        return String(state.rows.filter(row => scoped(row, q)).length);
      },
      rows(q) {
        assert.match(q, /^SELECT (?:id|COUNT\(\*\)) FROM PageMonitor WHERE/);
        assert.ok(q.includes(`AND pageId='${pageId}'`)); assert.ok(q.includes("AND providerNo='999998'"));
        assert.ok(q.includes('monitor_owner)=19')); assert.ok(!q.includes(cookie));
        const matches = state.rows.filter(row => scoped(row, q));
        return q.startsWith('SELECT COUNT') ? [[String(matches.length)]] : matches.map(row => [row.id]);
      },
      execute(q) {
        assert.match(q, /^DELETE FROM PageMonitor WHERE id IN/); assert.ok(q.includes('monitor_owner)=19'));
        assert.ok(!q.includes(cookie)); const matches = state.rows.filter(row => scoped(row, q));
        state.deleted.push(...matches.map(row => row.id)); state.rows = state.rows.filter(row => !matches.includes(row));
      },
    } };
  const onclick = "window.open('/carlos/appointment/addappointment?provider_no=999998&year=2026&month=10&day=21&start_time=09:00&duration=15')";
  const row = (id, extra = {}) => ({ id, hash, pageId, provider: '999998', pageName: 'addappointment', ...extra });
  return { s, state, native, patients, onclick, row, pageId };
}
test('booking-search cleanup removes only captured native slot/session monitors, then permits parent cleanup', async () => {
  const m = monitorFixture(); const cleanup = installSearchMonitorCleanup(m.s, 'FAKE-PWabcdef', m.patients, m.native);
  await cleanup.rememberSlot(m.onclick); assert.equal(cleanup.canRelease(), false);
  m.state.rows = [m.row('27'), m.row('28', { hash: 'f'.repeat(64) }), m.row('29', { pageId: 'other-slot' }), m.row('30', { provider: '123' })];
  m.state.callbacks[0](); assert.deepEqual(m.state.deleted, ['27']); assert.deepEqual(m.state.rows.map(row => row.id), ['28', '29', '30']);
  assert.equal(cleanup.canRelease(), true);
});
test('booking-search cleanup retains all rows and parents while a browser is connected', async () => {
  const m = monitorFixture(); const cleanup = installSearchMonitorCleanup(m.s, 'FAKE-PWabcdef', m.patients, m.native);
  await cleanup.rememberSlot(m.onclick); m.state.rows = [m.row('27')]; m.state.connected = true;
  assert.throws(m.state.callbacks[0], /browser closure/); assert.equal(cleanup.canRelease(), false); assert.deepEqual(m.state.deleted, []);
});
test('booking-search cleanup retains all rows and parents when one of 19 patient owners changes', async () => {
  const m = monitorFixture(); const cleanup = installSearchMonitorCleanup(m.s, 'FAKE-PWabcdef', m.patients, m.native);
  await cleanup.rememberSlot(m.onclick); m.state.rows = [m.row('27')]; m.state.owned = false;
  assert.throws(m.state.callbacks[0], /ownership changed/); assert.equal(cleanup.canRelease(), false); assert.deepEqual(m.state.deleted, []);
});
test('booking-search pre-existing monitor is retained and blocks parent release', async () => {
  const m = monitorFixture(); m.state.rows = [m.row('27')]; const cleanup = installSearchMonitorCleanup(m.s, 'FAKE-PWabcdef', m.patients, m.native);
  await assert.rejects(cleanup.rememberSlot(m.onclick), /already has an owned-session monitor/);
  assert.throws(m.state.callbacks[0], /baseline is unconfirmed/); assert.equal(cleanup.canRelease(), false); assert.deepEqual(m.state.deleted, []);
});
test('booking-search missing session proof blocks monitor and parent cleanup', async () => {
  const m = monitorFixture(); m.state.cookies = false; const cleanup = installSearchMonitorCleanup(m.s, 'FAKE-PWabcdef', m.patients, m.native);
  await assert.rejects(cleanup.rememberSlot(m.onclick), /session/);
  assert.throws(m.state.callbacks[0], /baseline is unconfirmed/); assert.equal(cleanup.canRelease(), false);
});
test('booking-search captured monitor changing ownership blocks parent release', async () => {
  const m = monitorFixture(); const cleanup = installSearchMonitorCleanup(m.s, 'FAKE-PWabcdef', m.patients, m.native);
  await cleanup.rememberSlot(m.onclick); m.state.rows = [m.row('27')];
  const execute = m.s.sql.execute; m.s.sql.execute = q => { m.state.rows[0].hash = 'f'.repeat(64); execute(q); };
  assert.throws(m.state.callbacks[0], /remains or changed ownership/); assert.equal(cleanup.canRelease(), false); assert.deepEqual(m.state.deleted, []);
});
for (const mode of ['foreign-provider', 'stale-date', 'offhost']) test(`booking-search refuses ${mode} native slot before opening`, async () => {
  const m = monitorFixture(); const cleanup = installSearchMonitorCleanup(m.s, 'FAKE-PWabcdef', m.patients, m.native);
  const onclick = mode === 'foreign-provider' ? m.onclick.replace('provider_no=999998', 'provider_no=123')
    : mode === 'stale-date' ? m.onclick.replace('day=21', 'day=20') : m.onclick.replace('/carlos/appointment', 'https://example.invalid/carlos/appointment');
  await assert.rejects(cleanup.rememberSlot(onclick)); assert.deepEqual(m.state.deleted, []);
});

function parentCleanupFixture() {
  const patients = Array.from({ length: 19 }, (_, i) => String(3333 + i));
  const state = { callbacks: [], called: 0, released: true,
    rows: patients.map((id, i) => [id, `FAKE-PWabcdef-X${i}`, '999998']) };
  const ownership = { patients, monitors: { canRelease: () => state.released } };
  const s = { marker: MARK, provider: '999998', cleanup(fn) { state.callbacks.push(fn); }, sql: {
    rows(q) { assert.equal(q, "SELECT demographic_no,last_name,provider_no FROM demographic\n      WHERE last_name LIKE 'FAKE-PWabcdef%' ORDER BY demographic_no"); return state.rows; },
  } };
  const register = searchHelpers.registerPatientCleanup;
  searchHelpers.registerPatientCleanup = (scoped, tag) => { assert.equal(tag, 'FAKE-PWabcdef'); scoped.cleanup(() => state.called++); };
  try { registerExactSearchPatientCleanup(s, 'FAKE-PWabcdef', ownership); }
  finally { searchHelpers.registerPatientCleanup = register; }
  return { s, state, ownership };
}
test('exact complete 19-parent set permits the original tagged cleanup callback', () => {
  const m = parentCleanupFixture(); m.state.callbacks[0](); assert.equal(m.state.called, 1);
});
for (const mode of ['extra-same-tag', 'missing', 'replaced-id', 'changed-provider', 'changed-tag-name']) test(`parent cleanup retains all parents on ${mode}`, () => {
  const m = parentCleanupFixture();
  if (mode === 'extra-same-tag') m.state.rows.push(['9999', 'FAKE-PWabcdef-foreign', '999998']);
  if (mode === 'missing') m.state.rows.pop();
  if (mode === 'replaced-id') m.state.rows[18][0] = '9999';
  if (mode === 'changed-provider') m.state.rows[0][2] = '123';
  if (mode === 'changed-tag-name') m.state.rows[0][1] = 'FAKE-PWabcdefOther';
  assert.throws(m.state.callbacks[0], /complete tagged patient set or ownership changed/); assert.equal(m.state.called, 0);
});
test('parent cleanup requires completed monitor cleanup immediately before callback', () => {
  const m = parentCleanupFixture(); m.state.released = false;
  assert.throws(m.state.callbacks[0], /monitor cleanup is incomplete/); assert.equal(m.state.called, 0);
});
test('parent cleanup refuses an incomplete captured roster', () => {
  const m = parentCleanupFixture(); m.ownership.patients = m.ownership.patients.slice(1);
  assert.throws(m.state.callbacks[0], /exact 19 identities are unconfirmed/); assert.equal(m.state.called, 0);
});
test('parent cleanup refuses changed full marker even when the six-character suffix collides', () => {
  const m = parentCleanupFixture(); m.s.marker = 'FAKE-PWffffffff00abcdef';
  assert.throws(m.state.callbacks[0], /full marker or short tag changed/); assert.equal(m.state.called, 0);
});
for (const marker of ['other', 'FAKE-PWffffffff00abcdef']) test(`parent registration refuses invalid marker or mismatched tag ${marker}`, () => {
  const s = { marker, provider: '999998' };
  // The second marker is valid but its suffix differs from this deliberately mismatched short tag.
  assert.throws(() => registerExactSearchPatientCleanup(s, 'FAKE-PW000000', { patients: [], monitors: null }), /marker and short tag are inconsistent/);
});
