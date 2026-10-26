/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/messenger/CreateMessage.jsp', 'utf8');
const script = jsp.slice(jsp.indexOf('function XMLHttpRequestSendnArch()'), jsp.indexOf('// Opens demographic attachment popup'))
  .replaceAll('${pageContext.request.contextPath}', '/carlos');

test('the rendered form template carries the same encoded optional ID as the archive button', () => {
  const field = jsp.match(/<input\b[^>]*name="archiveMessageId"[^>]*>/g);
  assert.equal(field?.length, 1);
  assert.match(field[0], /type="hidden"/);
  assert.match(field[0], /value="\$\{carlos:forHtmlAttribute\(archiveMessageId\)\}"/);
  assert.match(jsp, /data-message-id="\$\{carlos:forHtmlAttribute\(archiveMessageId\)\}"/);
});

test('the actual recipient validator permits the disabled checked control used by the server-refusal probe', () => {
  const source = jsp.slice(jsp.indexOf('function validateCheckBoxes('), jsp.indexOf('// Archives the current message'));
  const context = vm.createContext({});
  vm.runInContext(source, context);
  assert.equal(context.validateCheckBoxes({ provider: { checked: true, disabled: true } }), '1');
  assert.equal(context.validateCheckBoxes({ provider: { checked: false, disabled: true } }), '0');
});

function compose(messageId, referrer = '', valid = true) {
  let sent = 0;
  const requests = [];
  const alerts = [];
  const context = {
    document: { referrer, forms: [{ submit: () => sent++ }],
      getElementById: () => ({ dataset: { messageId } }) },
    validateFields: () => valid,
    XMLHttpRequest: function () {
      requests.push(this);
      this.open = (method, url) => Object.assign(this, { method, url });
      this.setRequestHeader = () => {};
      this.send = body => { this.body = body; };
    },
    showAlert: (...args) => alerts.push(args),
    msgArchiveFailed: 'failed', msgArchiveError: 'error', msgArchiveTimeout: 'timeout',
    setTimeout: fn => fn(),
  };
  vm.createContext(context);
  vm.runInContext(script, context);
  context.XMLHttpRequestSendnArch();
  return { requests, alerts, sent: () => sent };
}

for (const referrer of ['', 'https://elsewhere.invalid/messenger/ViewMessage?messageID=999']) {
  test(`archives the server-supplied ID with referrer ${JSON.stringify(referrer)}`, () => {
    const f = compose('123', referrer);
    assert.equal(f.requests.length, 1);
    const xhr = f.requests[0];
    assert.equal(xhr.method, 'POST');
    assert.equal(xhr.url, '/carlos/messenger/DisplayMessages');
    assert.equal(xhr.body, 'btnDelete=archive&messageNo=123');
    assert.equal(f.sent(), 0);
    xhr.status = 200;
    xhr.onload();
    assert.equal(f.sent(), 1);
  });
}
for (const id of [undefined, '', '0', '123&messageNo=999']) {
  test(`ordinary compose or invalid ID ${JSON.stringify(id)} sends without archiving`, () => {
    const f = compose(id);
    assert.equal(f.requests.length, 0);
    assert.equal(f.sent(), 1);
  });
}
test('invalid composition neither archives nor sends', () => {
  const f = compose('123', '', false);
  assert.equal(f.requests.length, 0);
  assert.equal(f.sent(), 0);
});
for (const outcome of ['refused', 'network', 'timeout']) {
  test(`archive ${outcome} warns and still sends once`, () => {
    const f = compose('123');
    const xhr = f.requests[0];
    if (outcome === 'refused') { xhr.status = 403; xhr.onload(); }
    if (outcome === 'network') xhr.onerror();
    if (outcome === 'timeout') xhr.ontimeout();
    assert.equal(f.alerts.length, 1);
    assert.equal(f.sent(), 1);
  });
}
