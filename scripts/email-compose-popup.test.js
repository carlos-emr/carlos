// Licensed under the repository LICENSE. Executes the production POST popup helper.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/share/javascript/email-compose-popup.js'), 'utf8');
function fixture({ token = 'csrf-token', blocked = false, ready } = {}) {
  const events = [];
  const csrf = { value: token };
  const context = vm.createContext({ URL,
    window: { location: { href: 'https://clinic.test/carlos/chart', origin: 'https://clinic.test' },
      csrfTokenReady: ready,
      open: () => { events.push('open'); return blocked ? null : { close: () => events.push('close') }; },
      alert: message => events.push(['error', message]) },
    document: {
      querySelector: () => csrf,
      createElement: () => ({ children: [], appendChild(child) { this.children.push(child); },
        remove() { events.push('remove'); } }),
      body: { appendChild() {} }
    },
    HTMLFormElement: { prototype: { submit() {
      events.push({ method: this.method, action: this.action,
        fields: Object.fromEntries(this.children.map(input => [input.name, input.value])) });
    } } }
  });
  vm.runInContext(source, context);
  return { csrf, events, open: url => context.openEmailCompose(url ||
    '/carlos/admin/ManageEmails?method=resendEmail&logId=42', 1100, 1000, 'Visible error') };
}
test('copy posts the selected log and CSRF token without putting tokens in the URL', async () => {
  const f = fixture(); await f.open();
  assert.deepEqual(f.events, ['open', { method: 'POST', action: 'https://clinic.test/carlos/admin/ManageEmails',
    fields: { method: 'resendEmail', logId: '42', 'CSRF-TOKEN': 'csrf-token' } }, 'remove']);
});
test('popup is reserved before awaiting CSRF initialization', async () => {
  let resolve; const ready = new Promise(r => { resolve = r; });
  const f = fixture({ token: '', ready }); const pending = f.open();
  assert.deepEqual(f.events, ['open']);
  f.csrf.value = 'ready-token'; resolve(); await pending;
  assert.equal(f.events[1].fields['CSRF-TOKEN'], 'ready-token');
});
test('missing token closes the empty popup and reports a visible error without POST', async () => {
  const f = fixture({ token: '' }); await f.open();
  assert.deepEqual(f.events, ['open', 'close', ['error', 'Visible error']]);
});
test('token initialization rejection prevents POST and reports a visible error', async () => {
  const f = fixture({ ready: Promise.reject(new Error('token fetch failed')) }); await f.open();
  assert.deepEqual(f.events, ['open', 'close', ['error', 'Visible error']]);
});
test('blocked popup reports a visible error', async () => {
  const f = fixture({ blocked: true }); await f.open();
  assert.deepEqual(f.events, ['open', ['error', 'Visible error']]);
});
test('cross-origin destinations cannot receive the CSRF token', async () => {
  const f = fixture(); await f.open('https://other.test/');
  assert.deepEqual(f.events, [['error', 'Visible error']]);
});
