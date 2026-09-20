/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Regression coverage for the HRM report viewer's inline actions.
 *
 * Two tester-reported faults are pinned here:
 *  - adding a comment or a description printed JavaScript into the page, because the handlers
 *    rendered the raw response body and the endpoint replied in text/html, which the
 *    response-decorating filters append a <script> block to;
 *  - signing off did nothing visible, because the notify-the-inbox branch was gated on a
 *    `listView` flag that nothing ever sets.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

/** Objects built inside the VM realm have a foreign prototype, so compare them by value. */
const plain = value => JSON.parse(JSON.stringify(value));

const source = fs.readFileSync(
  path.join(__dirname, '../src/main/webapp/hospitalReportManager/hrmActions.js'), 'utf8');

/**
 * Builds a DOM/jQuery stub just rich enough for these handlers.
 *
 * @param {Object} options
 * @param {Object} options.elements id -> element stub
 * @param {Object} options.windowShape extra window/self properties (opener, parent, close)
 * @return {Object} the VM context, plus `requests` and `broadcasts` recorders
 */
function setup({elements = {}, windowShape = {}} = {}) {
  const requests = [];
  const broadcasts = [];
  const closed = [];

  const disabled = [];
  function jQuery(selector) {
    return {
      val: () => (elements[String(selector).replace(/^#/, '')] || {}).value,
      prop: (name, value) => { if (name === 'disabled' && value) { disabled.push(String(selector)); } },
      is: () => false,
      autocomplete: () => {},
    };
  }
  jQuery.ajax = options => { requests.push(options); };

  const document = {
    getElementById: id => elements[id] || null,
    createTextNode: text => ({nodeText: String(text)}),
    createElement: tag => ({tag, style: {}, addEventListener() {}}),
  };

  const windowStub = Object.assign({
    close: () => closed.push(true),
  }, windowShape);
  windowStub.parent = windowShape.parent || windowStub;

  const context = vm.createContext({
    jQuery,
    document,
    window: windowStub,
    self: windowStub,
    contextpath: '/carlos',
    console: {error() {}},
    BroadcastChannel: function (name) {
      this.postMessage = message => broadcasts.push({name, message});
      this.close = () => {};
    },
  });
  vm.runInContext(source, context);
  return {context, requests, broadcasts, closed, elements, disabled};
}

/** Minimal element stub with the properties the handlers read and write. */
function element(extra = {}) {
  const attrs = extra.attrs || {};
  const base = {textContent: '', value: '', style: {}, appendChild() {},
    getAttribute(name) { return Object.prototype.hasOwnProperty.call(attrs, name) ? attrs[name] : null; }};
  const {attrs: _ignored, ...rest} = extra;
  return Object.assign(base, rest);
}

/** The report container as the inbox renders it in a popup it opened itself. */
function inboxWindowCard() {
  return element({attrs: {'data-inbox-window': 'true', 'data-inbox-inline': 'false'}});
}

test('modify requests ask for JSON, so no HTML body can be rendered into the page', () => {
  const {context, requests} = setup({
    elements: {'commentField_7_hrm': element({value: 'follow up'})},
  });

  context.addComment('7');

  assert.equal(requests.length, 1);
  assert.equal(requests[0].type, 'POST');
  assert.equal(requests[0].url, '/carlos/hospitalReportManager/Modify');
  assert.equal(requests[0].dataType, 'json');
  assert.deepEqual(plain(requests[0].data),
    {method: 'addComment', reportId: '7', comment: 'follow up'});
});

test('comment status shows the server message, never the response body', () => {
  const status = element();
  const {context, requests} = setup({
    elements: {'commentField_7_hrm': element({value: 'follow up'}), 'commentstatus7': status},
  });

  context.addComment('7');
  requests[0].success({success: true, message: 'Success'});

  assert.equal(status.textContent, 'Success');
});

test('a transport failure reports it instead of leaving the box empty', () => {
  const status = element();
  const {context, requests} = setup({
    elements: {'descriptionField_7_hrm': element({value: 'CT chest'}), 'descriptionstatus7': status},
  });

  context.setDescription('7');
  requests[0].error({}, 'error', 'Internal Server Error');

  assert.match(status.textContent, /Error encountered/);
});

test('sign-off notifies the Inboxhub without needing any listView flag', () => {
  const dropped = [];
  const opener = {
    dropAcknowledgedInboxhubItem: (id, type, count) => dropped.push([id, type, count]),
    fetchInboxhubData: () => dropped.push(['refetch']),
  };
  const {context, requests, broadcasts, closed} = setup({
    elements: {signoff7: element(), hrmdoc_7: inboxWindowCard()},
    windowShape: {opener},
  });

  context.signOffHrm('7');
  assert.match(requests[0].data, /method=signOff&signedOff=1&reportId=7/);

  requests[0].success({success: true, message: 'Success', clearedCount: 1});

  assert.deepEqual(plain(broadcasts), [{
    name: 'inboxhub-refresh',
    message: {action: 'refresh', segmentID: '7', labType: 'HRM', clearedCount: 1},
  }]);
  assert.deepEqual(dropped, [['7', 'HRM', 1], ['refetch']]);
  assert.equal(closed.length, 1, 'a popup closes itself after sign-off');
});

test('a sign-off that cleared no routing row does not move the inbox badge', () => {
  // The server reports zero when the report was already signed off, or had no routing row in the
  // inbox the clinician is looking at. Zero is a real answer: the Inboxhub honours it and leaves
  // the badge alone. Sending nothing would be read as "one row" and walk the badge below truth.
  const dropped = [];
  const opener = {dropAcknowledgedInboxhubItem: (id, type, count) => dropped.push([id, type, count])};
  const {context, requests, broadcasts} = setup({
    elements: {signoff7: element()},
    windowShape: {opener},
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success', clearedCount: 0});

  assert.equal(plain(broadcasts)[0].message.clearedCount, 0);
  assert.deepEqual(dropped, [['7', 'HRM', 0]]);
});

test('the legacy oscarMDS inbox is reached even though it has no broadcast listener', () => {
  // The legacy inbox exposes only window.removeReport and never hears the broadcast, so the
  // direct call has to happen regardless of whether BroadcastChannel worked.
  const removed = [];
  const opener = {removeReport: (id, type) => removed.push([id, type])};
  const {context, requests, broadcasts} = setup({
    elements: {signoff7: element()},
    windowShape: {opener},
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success', clearedCount: 1});

  assert.deepEqual(removed, [['7', 'HRM']]);
  assert.equal(broadcasts.length, 1);
});

test('revoking a sign-off puts the report back and tells the inbox nothing', () => {
  const button = element();
  const removed = [];
  const {context, requests, broadcasts, closed} = setup({
    elements: {signoff7: button},
    windowShape: {opener: {removeReport: (id, type) => removed.push([id, type])}},
  });

  context.revokeSignOffHrm('7');
  assert.match(requests[0].data, /signedOff=0/);

  requests[0].success({success: true, message: 'Success'});

  assert.deepEqual(broadcasts, []);
  assert.deepEqual(removed, []);
  assert.equal(closed.length, 0);
  assert.equal(button.value, 'Sign-Off');
});

test('a failed sign-off neither clears the inbox nor closes the window', () => {
  const status = element();
  const removed = [];
  const {context, requests, broadcasts, closed} = setup({
    elements: {signoff7: element(), signoffstatus7: status},
    windowShape: {opener: {removeReport: (id, type) => removed.push([id, type])}},
  });

  context.signOffHrm('7');
  requests[0].success({success: false, message: 'Error encountered'});

  assert.deepEqual(broadcasts, []);
  assert.deepEqual(removed, []);
  assert.equal(closed.length, 0);
  assert.equal(status.textContent, 'Error encountered');
});

test('the inline inbox card is hidden when there is no window to close', () => {
  const card = element({getAttribute: name => (name === 'data-inbox-inline' ? 'true' : null)});
  const {context, requests} = setup({
    elements: {signoff7: element(), hrmdoc_7: card},
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success'});

  assert.equal(card.style.display, 'none');
});

test('a popup the inbox did not open stays open after sign-off', () => {
  // ticklerMain, ticklerDemoMain and the eChart's HRM shortcut all open this same route in a
  // popup. Closing those would take away the revoke affordance they rely on, so only a window
  // carrying the inbox's own inWindow marker closes itself.
  const button = element();
  const {context, requests, closed} = setup({
    elements: {signoff7: button, hrmdoc_7: element({attrs: {'data-inbox-window': 'false'}})},
    windowShape: {opener: {}},
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success', clearedCount: 1});

  assert.equal(closed.length, 0, 'a tickler/eChart popup must not close itself');
  assert.equal(button.value, 'Revoke Sign-Off', 'and revocation stays available');
});

test('the legacy inline inbox is refreshed even though it is this very window', () => {
  // oscarMDS/Page.jsp <jsp:include>s the viewer into the inbox page itself: no opener, and
  // window.parent is this window. Without a current-window fallback the card was hidden but the
  // legacy category list and counts kept counting a report already signed off.
  const removed = [];
  const refreshed = [];
  const card = element({attrs: {'data-inbox-inline': 'true', 'data-inbox-window': 'false'}});
  const {context, requests} = setup({
    elements: {signoff7: element(), hrmdoc_7: card},
  });
  context.window.removeReport = (id, type) => removed.push([id, type]);
  context.window.fetchInboxhubData = () => refreshed.push(true);

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success', clearedCount: 1});

  assert.deepEqual(removed, [['7', 'HRM']]);
  assert.equal(card.style.display, 'none');
});

test('a failed "not similar" save reports itself without eating the similar-report list', () => {
  const notice = element({textContent: 'CARLOS has also detected...'});
  const status = element();
  const {context, requests} = setup({
    elements: {similarNotice: notice, similarstatus7: status},
  });

  context.makeIndependent('7');
  requests[0].success({success: false, message: 'Error encountered'});

  assert.equal(status.textContent, 'Error encountered');
  assert.equal(notice.textContent, 'CARLOS has also detected...', 'the list must survive');
});

test('a failed category save says why, since the chooser stays open', () => {
  const select = {value: '3', selectedIndex: 0, options: [{text: 'Cardiology'}], textContent: 'untouched'};
  const status = element();
  const {context, requests} = setup({
    elements: {
      selectedCategory_7: select,
      hrmCategory_7: element(),
      chooseCategory_7: element(),
      showCategory_7: element(),
      categorystatus7: status,
    },
  });

  context.updateCategory('7');
  requests[0].success({success: false, message: 'Error encountered'});

  assert.equal(status.textContent, 'Error encountered');
});

test('the Inboxhub view-mode card is hidden when the report is framed', () => {
  // View mode renders each report in an iframe inside a .document-card; there is no window to
  // close and the card, not the report body, is what the clinician sees.
  const frameCard = {style: {}};
  const frameElement = {closest: selector => (selector === '.document-card.card' ? frameCard : null)};
  const {context, requests} = setup({
    elements: {signoff7: element()},
    windowShape: {frameElement, parent: {dropAcknowledgedInboxhubItem() {}}},
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success'});

  assert.equal(frameCard.style.display, 'none');
});

test('a standalone report page is not blanked by a sign-off', () => {
  const card = element();
  const {context, requests, closed} = setup({
    elements: {signoff7: element(), hrmdoc_7: card},
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success'});

  assert.equal(card.style.display, undefined);
  assert.equal(closed.length, 0);
});

test('the sign-off button rebinds without an inline javascript: attribute', () => {
  const button = element();
  const {context, requests} = setup({
    elements: {signoff7: button},
    windowShape: {opener: null},
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success'});

  assert.equal(button.value, 'Revoke Sign-Off');
  assert.equal(typeof button.onclick, 'function');
  assert.equal(button.onClick, undefined);
});

test('changing a category rewrites only the label, leaving the picker usable', () => {
  const select = {value: '3', selectedIndex: 0, options: [{text: 'Cardiology'}], textContent: 'untouched'};
  const label = element();
  const {context, requests, disabled} = setup({
    elements: {
      selectedCategory_7: select,
      hrmCategory_7: label,
      chooseCategory_7: element(),
      showCategory_7: element(),
    },
  });

  context.updateCategory('7');
  requests[0].success({success: true, message: 'Success'});

  assert.equal(label.textContent, 'Cardiology');
  assert.equal(select.textContent, 'untouched');
  // Filing under a category says nothing about the patient link, so the Msg/Tickler/eChart/
  // Master/Appt History buttons must not be disabled by it.
  assert.deepEqual(disabled, []);
});

test('a failed category update leaves the label and the picker as they were', () => {
  const select = {value: '3', selectedIndex: 0, options: [{text: 'Cardiology'}], textContent: 'untouched'};
  const label = element({textContent: 'Radiology'});
  const chooser = element();
  const {context, requests} = setup({
    elements: {
      selectedCategory_7: select,
      hrmCategory_7: label,
      chooseCategory_7: chooser,
      showCategory_7: element(),
    },
  });

  context.updateCategory('7');
  requests[0].success({success: false, message: 'Error encountered'});

  assert.equal(label.textContent, 'Radiology');
  assert.equal(chooser.style.display, undefined);
});
