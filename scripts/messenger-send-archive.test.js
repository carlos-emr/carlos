/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Send and Archive (#6042): the compose page submits the reply once, with archiveAfterSend=1, and the server archives
// the message replied to only after the reply is sent. The page used to archive it first with a background POST to
// messenger/DisplayMessages, so a refused reply left it archived with no reply.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/messenger/CreateMessage.jsp', 'utf8');
const script = jsp.slice(jsp.indexOf('function sendAndArchive()'), jsp.indexOf('// Opens demographic attachment popup'));

test('the rendered form template carries the same encoded optional ID as the archive button, and an empty archive flag', () => {
  const field = jsp.match(/<input\b[^>]*name="archiveMessageId"[^>]*>/g);
  assert.equal(field?.length, 1);
  assert.match(field[0], /type="hidden"/);
  assert.match(field[0], /value="\$\{carlos:forHtmlAttribute\(archiveMessageId\)\}"/);
  assert.match(jsp, /data-message-id="\$\{carlos:forHtmlAttribute\(archiveMessageId\)\}"/);
  const flag = jsp.match(/<input\b[^>]*name="archiveAfterSend"[^>]*>/g);
  assert.equal(flag?.length, 1);
  assert.match(flag[0], /type="hidden"/);
  assert.match(flag[0], /value=""/);
});

test('the actual recipient validator permits the disabled checked control used by the server-refusal probe', () => {
  const source = jsp.slice(jsp.indexOf('function validateCheckBoxes('), jsp.indexOf('// Send and Archive (#6042)'));
  const context = vm.createContext({});
  vm.runInContext(source, context);
  assert.equal(context.validateCheckBoxes({ provider: { checked: true, disabled: true } }), '1');
  assert.equal(context.validateCheckBoxes({ provider: { checked: false, disabled: true } }), '0');
});

test('the page no longer archives in the background before the reply is accepted', () => {
  assert.doesNotMatch(jsp, /btnDelete=archive/);
  assert.doesNotMatch(jsp, /XMLHttpRequestSendnArch/);
  assert.match(jsp, /id="sendArchive"[^>]*onclick="writeToMessage\(\); sendAndArchive\(\);"/);
});

test('the plain Send clears the archive flag, so a page restored after Send and Archive never archives on Send', () => {
  assert.match(jsp, /<button type="submit"[^>]*onclick="writeToMessage\(\); this\.form\.archiveAfterSend\.value = '';"/);
});

function compose(messageId, valid = true) {
  let sent = 0;
  const requests = [];
  const form = { submit: () => sent++, archiveAfterSend: { value: '' } };
  const context = {
    document: { forms: [form], getElementById: () => ({ dataset: { messageId } }) },
    validateFields: () => valid,
    XMLHttpRequest: function () { requests.push(this); },
  };
  vm.createContext(context);
  vm.runInContext(script, context);
  context.sendAndArchive();
  return { requests, sent: () => sent, flag: () => form.archiveAfterSend.value };
}

test('Send and Archive submits the reply once, asking the server to archive after the send', () => {
  const f = compose('123');
  assert.equal(f.requests.length, 0);
  assert.equal(f.flag(), '1');
  assert.equal(f.sent(), 1);
});
for (const id of [undefined, '', '0', '123&messageNo=999']) {
  test(`ordinary compose or invalid ID ${JSON.stringify(id)} sends without asking to archive`, () => {
    const f = compose(id);
    assert.equal(f.requests.length, 0);
    assert.equal(f.flag(), '');
    assert.equal(f.sent(), 1);
  });
}
test('invalid composition neither asks to archive nor sends', () => {
  const f = compose('123', false);
  assert.equal(f.requests.length, 0);
  assert.equal(f.flag(), '');
  assert.equal(f.sent(), 0);
});
