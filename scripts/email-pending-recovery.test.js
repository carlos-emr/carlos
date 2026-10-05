// Exercise the production compose handlers with deterministic transport-result fixtures.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/email/emailCompose.jsp'), 'utf8');
const ready = jsp.slice(jsp.indexOf('    document.addEventListener("DOMContentLoaded"'),
  jsp.indexOf('    document.addEventListener("keydown"'));
function handler(name) {
  const start = jsp.indexOf(`    function ${name}()`);
  assert.notEqual(start, -1);
  return jsp.slice(start, jsp.indexOf('\n    }', start) + 6);
}
function fixture({ accepted = false, recorded = false, uncertain = false, pendingCopy = false, followUp = false,
  confirm = true, valid = true, portalPending = false } = {}) {
  const calls = { submitted: 0, closed: 0, confirmed: 0, initialized: 0, opened: 0 };
  const fields = {
    isEmailError: { value: 'false' }, isEmailSuccessful: { value: String(accepted) },
    isEmailStatusRecorded: { value: String(recorded) },
    emailComposeForm: { submit() { calls.submitted++; } },
    ...(followUp ? { emailFollowUpWarning: {} } : {}),
    ...(uncertain ? { deliveryUnconfirmedWarning: {} } : {}),
    ...(pendingCopy ? { emailResendWarningMessage: { value: 'May already be delivered' } } : {}),
  };
  const context = vm.createContext({
    document: { getElementById: id => fields[id] || null, querySelectorAll: () => [],
      addEventListener: (_name, callback) => { context.ready = callback; } },
    window: { close() { calls.closed++; }, confirm() { calls.confirmed++; return confirm; } },
    setTimeout: callback => callback(), validateForm: () => valid,
    ShowSpin() {}, applyEncryptionState() {}, showErrorAndClose() {},
    openEFormAfterSend() { calls.opened++; },
    convertAttachmentSize() { calls.initialized++; }, displayErrorOnInvalidEmail() {},
    selectPatientChartOption() {}, showAdditionalParamOption() {}, toggleInternalTextArea() {},
  });
  vm.runInContext(handler('validateEmailForm') + handler('autoSendEmail')
    .replace('${carlos:forJavaScript(isEmailAutoSend)}', 'true') + ready.replace('${portalDeliveryNeedsRecovery}', String(portalPending)), context);
  return { context, calls };
}

test('accepted send closes only after its outcome is recorded, with no repeat send', () => {
  for (const recorded of [true, false]) {
    const { context, calls } = fixture({ accepted: true, recorded });
    context.ready();
    assert.equal(calls.closed, Number(recorded));
    assert.equal(calls.opened, 1);
    assert.equal(calls.submitted, 0);
  }
});
test('unconfirmed send remains open and never auto-sends', () => {
  const { context, calls } = fixture({ uncertain: true });
  context.ready();
  assert.equal(calls.closed, 0);
  assert.equal(calls.submitted, 0);
});
test('a stale pending copy always requires a manual, confirmed send', () => {
  for (const confirm of [false, true]) {
    const { context, calls } = fixture({ pendingCopy: true, confirm });
    context.ready();
    assert.equal(calls.submitted, 0);
    assert.equal(calls.initialized, 1);
    assert.equal(context.validateEmailForm(), confirm);
    assert.equal(calls.confirmed, 1);
  }
});
test('invalid drafts cannot send automatically', () => {
  const { context, calls } = fixture({ valid: false });
  context.ready();
  assert.equal(calls.submitted, 0);
});
test('a new valid automatic send is submitted once', () => {
  const { context, calls } = fixture();
  context.ready();
  assert.equal(calls.submitted, 1);
});

test('post-send follow-up warning keeps accepted mail open without resending', () => {
  const { context, calls } = fixture({ accepted: true, recorded: true, followUp: true });
  context.ready();
  assert.equal(calls.closed, 0);
  assert.equal(calls.submitted, 0);
});

test('pending Portal publication keeps accepted mail open without sending again', () => {
  const { context, calls } = fixture({ accepted: true, recorded: true, portalPending: true });
  context.ready();
  assert.equal(calls.closed, 0);
  assert.equal(calls.submitted, 0);
});

test('Portal mode validates an encrypted email without requiring the password fields', () => {
  for (const portalEnabled of [true, false]) {
    const fields = {
      subjectEmail: { value: 'Test subject' }, message: { value: 'Test message' },
      encryptionSwitch: { checked: true }, encryptAttachmentSwitch: { checked: true },
      emailPDFPassword: { value: '' }, emailPDFPasswordClue: { value: '' },
      totalSenderEmails: { value: 1 }, totalRecipintEmails: { value: 1 },
    };
    const context = vm.createContext({
      document: { getElementById: id => fields[id] || null, querySelectorAll: () => [] },
      emailComposeSubjectRequiredMsg: '', emailComposeMessageRequiredMsg: '',
      emailComposePasswordRequiredMsg: '', emailComposeClueRequiredMsg: '',
      validateField: (field, _message, errors, key) => { if (!field.value) errors[key] = true; },
      clearError() {},
    });
    vm.runInContext(handler('validateForm').replace('${portalEmailEnabled}', String(portalEnabled))
      .replace('${portalEmailMisconfigured}', 'false'), context);
    assert.equal(context.validateForm(), portalEnabled);
  }
});

test('a malformed Portal setting stops an encrypted send on the page, but not an unencrypted one', () => {
  for (const encrypted of [true, false]) {
    const shown = [];
    const fields = {
      subjectEmail: { value: 'Test subject' }, message: { value: 'Test message' },
      encryptionSwitch: { checked: encrypted }, encryptAttachmentSwitch: { checked: encrypted },
      emailPDFPassword: { value: '' }, emailPDFPasswordClue: { value: '' },
      totalSenderEmails: { value: 1 }, totalRecipintEmails: { value: 1 },
    };
    const context = vm.createContext({
      document: { getElementById: id => fields[id] || null, querySelectorAll: () => [] },
      emailComposeSubjectRequiredMsg: '', emailComposeMessageRequiredMsg: '',
      emailComposePasswordRequiredMsg: '', emailComposeClueRequiredMsg: '',
      emailComposePortalMisconfiguredMsg: 'setting not valid',
      validateField: (field, _message, errors, key) => { if (!field.value) errors[key] = true; },
      displayError: (_id, message) => shown.push(message),
      clearError() {},
    });
    // A malformed setting renders portalEmailEnabled as true, so no manual password is asked for.
    vm.runInContext(handler('validateForm').replace('${portalEmailEnabled}', 'true')
      .replace('${portalEmailMisconfigured}', 'true'), context);
    assert.equal(context.validateForm(), !encrypted);
    assert.deepEqual(shown, encrypted ? ['setting not valid'] : []);
  }
});
