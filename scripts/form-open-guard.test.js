/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/webapp/js/formOpenGuard.js', 'utf8');

function fixture(result = {exists: false}, status = 200) {
  const calls = [], requests = [];
  let choices;
  const reserved = {closed: false, location: {href: 'about:blank'}, close() { this.closed = true; }};
  const dialog = {text(value) { calls.push(['text', value]); return this; }, dialog(options) {
    if (typeof options === 'object') choices = options;
    else if (options === 'close') choices.close();
    return this;
  }, remove() { return this; }};
  const root = {location: new URL('https://fixture.invalid/clinic/casemgmt/CaseManagementView?demographicNo=123'),
    ctx: '/clinic', reloadWindows: {},
    document: {addEventListener() {}, getElementById() { return {querySelector() { return {value: '/clinic/encounter/displayForms?demographicNo=123'}; }}; }},
    open() { calls.push(['reserve']); return reserved; },
    popupPage(...args) { calls.push(['open', ...args]); },
    alert(message) { calls.push(['alert', message]); }, jQuery() { return dialog; },
    async fetch(url) { requests.push(new URL(url)); return {ok: status === 200, async json() { return result; }}; },
  };
  vm.runInNewContext(source, {window: root, URL});
  const start = () => root.openEncounterForm('/clinic/form/formcaregiver?demographic_no=123&formId=0&appointmentNo=11', 'new123', 'Caregiver');
  return {root, calls, requests, reserved, start, get choices() { return choices; }};
}

test('a fresh form opens blank and registers refresh using the clicked chart identity', async () => {
  const f = fixture();
  await f.start();
  assert.equal(f.requests[0].searchParams.get('demographicNo'), '123');
  assert.equal(f.requests[0].searchParams.get('appointmentNo'), '11');
  assert.equal(f.requests[0].pathname, '/clinic/encounter/formExists');
  assert.equal(f.calls.at(-1)[0], 'open');
  assert.equal(f.calls.at(-1)[3], 'new123');
  assert.equal(f.root.reloadWindows.new123div, 'forms');
});

for (const option of ['Open the existing version', 'Create a new blank version', 'Cancel']) {
  test(`saved form choice: ${option}`, async () => {
    const f = fixture({exists: true, url: '/clinic/form/forwardshortcutname?formId=latest&demographic_no=123', windowName: 'saved123'});
    await f.start();
    assert.equal(f.reserved.closed, true);
    assert.equal(f.calls.filter(call => call[0] === 'open').length, 0);
    f.choices.buttons.find(button => button.text === option).click();
    const opened = f.calls.filter(call => call[0] === 'open');
    if (option === 'Cancel') assert.equal(opened.length, 0);
    else assert.equal(opened[0][3], option.startsWith('Open') ? 'saved123' : 'new123');
    // Cancelling or choosing a version releases the double-click guard.
    await f.start();
    assert.equal(f.requests.length, 2);
  });
}

test('a failed check closes the reserved blank window and never silently starts a duplicate', async () => {
  const f = fixture({}, 403);
  await f.start();
  assert.equal(f.reserved.closed, true);
  assert.equal(f.calls.at(-1)[0], 'alert');
  assert.equal(f.calls.filter(call => call[0] === 'open').length, 0);
});

test('rapid repeated clicks issue one check while a decision is pending', async () => {
  const f = fixture({exists: true});
  const first = f.start();
  f.start();
  await first;
  assert.equal(f.requests.length, 1);
});

test('the legacy encounter uses its own configured context for the check', async () => {
  const f = fixture();
  delete f.root.ctx;
  f.root.encounterConfig = {ctx: '/clinic', demographicNo: '456'};
  await f.start();
  assert.equal(f.requests[0].pathname, '/clinic/encounter/formExists');
  assert.equal(f.requests[0].searchParams.get('demographicNo'), '123');
});

test('both encounter layouts load the guard and its dialog dependency', () => {
  for (const file of ['casemgmt/newEncounterLayout.jsp', 'encounter/includes/encounter-head.jspf']) {
    const page = fs.readFileSync(`src/main/webapp/WEB-INF/jsp/${file}`, 'utf8');
    assert.match(page, /jquery-ui-1\.14\.2\.min\.js/);
    assert.match(page, /\/js\/formOpenGuard\.js/);
  }
});
