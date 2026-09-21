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
function setup({elements = {}, windowShape = {}, noBroadcastChannel = false} = {}) {
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
    BroadcastChannel: noBroadcastChannel
      ? function () { throw new TypeError('BroadcastChannel is not defined'); }
      : function (name) {
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
  // The broadcast owns the modern Inboxhub. Driving it directly as well notified it twice:
  // with unloaded pages left the direct drop answers false and re-fetches, that re-fetch
  // clears the handled record, and the listener then re-fetches too — two whole searches.
  assert.deepEqual(dropped, [], 'a broadcast-reachable Inboxhub is not also driven directly');
  assert.equal(closed.length, 1, 'a popup closes itself after sign-off');
});

test('the direct route still runs when BroadcastChannel is unavailable', () => {
  const dropped = [];
  const opener = {
    dropAcknowledgedInboxhubItem: (id, type, count) => { dropped.push([id, type, count]); return true; },
    fetchInboxhubData: () => dropped.push(['refetch']),
  };
  const {context, requests} = setup({
    elements: {signoff7: element(), hrmdoc_7: inboxWindowCard()},
    windowShape: {opener},
    noBroadcastChannel: true,
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success', clearedCount: 1});

  assert.deepEqual(dropped, [['7', 'HRM', 1]],
    'with no channel the direct route is the only one, and an in-place drop needs no re-fetch');
});

test('the legacy inbox is refreshed directly even when the broadcast went out', () => {
  // oscarMDS has no listener on that channel, so the broadcast never reaches it.
  const calls = [];
  const {context, requests, broadcasts} = setup({
    elements: {signoff7: element()},
    windowShape: {opener: {refreshCategoryList: () => calls.push('refresh')}},
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success', clearedCount: 1});

  assert.equal(broadcasts.length, 1);
  assert.deepEqual(calls, ['refresh'], 'the legacy inbox is told directly regardless');
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
  // Carried by the broadcast, which owns the modern Inboxhub; the direct route stands down.
  assert.deepEqual(dropped, []);
});

test('the legacy oscarMDS inbox is refreshed, never asked to remove a row by bare id', () => {
  // Its removeReport() takes one id, ignores the type, and deletes "#labdoc_<id>" — an id
  // Page.jsp reuses for document, HL7 and HRM rows from independent key sequences. A collision
  // would take another patient's report off the screen as though it had been dealt with. Counts
  // still refresh, and the server filters the signed-off report out of the next list load.
  const refreshed = [];
  const removed = [];
  const opener = {
    refreshCategoryList: () => refreshed.push(true),
    removeReport: (id, type) => removed.push([id, type]),
  };
  const {context, requests, broadcasts} = setup({
    elements: {signoff7: element()},
    windowShape: {opener},
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success', clearedCount: 1});

  assert.deepEqual(refreshed, [true]);
  assert.deepEqual(removed, [], 'no bare-id row removal on the legacy inbox');
  assert.equal(broadcasts.length, 1);
});

test('revoking requests authoritative inbox state without removing the report or closing', () => {
  const button = element();
  const removed = [];
  const {context, requests, broadcasts, closed} = setup({
    elements: {signoff7: button},
    windowShape: {opener: {removeReport: (id, type) => removed.push([id, type])}},
  });

  context.revokeSignOffHrm('7');
  assert.match(requests[0].data, /signedOff=0/);

  requests[0].success({success: true, message: 'Success'});

  assert.equal(broadcasts.length, 1);
  assert.deepEqual(plain(broadcasts[0].message), {action: 'hrm-revoked', segmentID: '7', labType: 'HRM'});
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
  const refreshed = [];
  const card = element({attrs: {'data-inbox-inline': 'true', 'data-inbox-window': 'false'}});
  const {context, requests} = setup({
    elements: {signoff7: element(), hrmdoc_7: card},
  });
  context.window.refreshCategoryList = () => refreshed.push(true);

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success', clearedCount: 1});

  assert.deepEqual(refreshed, [true], 'the legacy counts must refresh');
  // The card this viewer owns is keyed by hrmdoc_<id>, which is unambiguous, so hiding it is safe.
  assert.equal(card.style.display, 'none');
});

test('a failed "not similar" save reports itself without eating the similar-report list', () => {
  const notice = element({textContent: 'CARLOS has also detected...'});
  const status = element();
  const {context, requests} = setup({
    elements: {similarNotice7: notice, similarstatus7: status},
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

test('a marked inbox popup closes even when COOP has severed window.opener', () => {
  // Cross-Origin-Opener-Policy can null out window.opener, which is the reason the broadcast
  // exists at all. Requiring an opener as well as the marker meant such a popup matched neither
  // this branch nor the inline one, so a signed-off report just sat there open. window.close()
  // is a no-op on a top-level page the script did not open, so the marker alone is guard enough.
  const {context, requests, closed} = setup({
    elements: {signoff7: element(), hrmdoc_7: inboxWindowCard()},
    windowShape: {opener: null},
  });

  context.signOffHrm('7');
  requests[0].success({success: true, message: 'Success', clearedCount: 1});

  assert.equal(closed.length, 1, 'the inbox popup must close even with no opener to notify');
});

test('a successful retry clears the error the previous sign-off left behind', () => {
  // A revoke keeps the page open, so a stale failure would sit beside a button that now says
  // the opposite of what just happened.
  const status = element();
  const button = element();
  const {context, requests} = setup({
    elements: {signoff7: button, signoffstatus7: status},
  });

  context.signOffHrm('7');
  requests[0].success({success: false, message: 'Error encountered'});
  assert.equal(status.textContent, 'Error encountered');

  context.revokeSignOffHrm('7');
  requests[1].success({success: true, message: 'Success'});

  assert.equal(status.textContent, '', 'the stale failure must not outlive the retry');
  assert.equal(button.value, 'Sign-Off');
});

test('a failed unlink keeps the patient link on screen instead of wiping it', () => {
  // demostatus holds the patient name AND the (remove) link. Clearing it before checking
  // result.success left a failed unlink with the database link still in place, no name on
  // screen, no way to retry, and the autocomplete still hidden.
  const linkedName = {marker: 'FAKE-Patient, Test'};
  const container = element({
    children: [linkedName],
    appendChild(node) { this.children.push(node); },
    querySelector() { return null; },
  });
  const {context, requests} = setup({
    elements: {demostatus7: container},
  });

  context.removeDemoFromHrm('7');
  requests[0].success({success: false, message: 'Error encountered'});

  assert.ok(container.children.includes(linkedName),
    'the linked patient must survive a failed unlink');
  assert.equal(container.textContent, '', 'and the container is not blanked');
  const notice = container.children.find(child => child.className === 'hrm-demo-status');
  assert.ok(notice, 'the failure is reported in its own node');
  assert.equal(notice.textContent, 'Error encountered');
});

test('a successful unlink still replaces the linked view', () => {
  const container = element({
    children: [{marker: 'FAKE-Patient, Test'}],
    appendChild(node) { this.children.push(node); },
    querySelector() { return null; },
  });
  const {context, requests} = setup({
    elements: {
      demostatus7: container,
      'autocompletedemo7hrm': element(),
      'demofind7hrm': element(),
    },
  });

  context.removeDemoFromHrm('7');
  requests[0].success({success: true, message: 'Success'});

  assert.equal(container.textContent, '', 'cleared before the new content is built');
  assert.ok(container.children.some(child => child.textContent === 'Not currently linked'));
});

test('marking a report independent clears only its own similar-report list', () => {
  // oscarMDS/Page.jsp <jsp:include>s this viewer once per inbox result, so a bare id appears
  // many times on that page and getElementById returns the FIRST one. An unqualified
  // similarNotice therefore let a later report erase an earlier report's similarity list.
  const ownNotice = element({textContent: 'similar to 41, 42'});
  const otherNotice = element({textContent: "another report's list"});
  const {context, requests} = setup({
    elements: {
      similarNotice7: ownNotice,
      similarNotice9: otherNotice,
      similarstatus7: element(),
    },
  });

  context.makeIndependent('7');
  requests[0].success({success: true, message: 'Success'});

  assert.equal(ownNotice.textContent, '', 'this report\'s list is cleared');
  assert.equal(otherNotice.textContent, "another report's list",
    "a sibling report's list must be left alone");
});


test('revoking without BroadcastChannel refreshes authoritative modern inbox state once', () => {
  let reloads = 0;
  const removed = [];
  const {context, requests, closed} = setup({
    noBroadcastChannel: true,
    elements: {signoff7: element()},
    windowShape: {opener: {
      dropAcknowledgedInboxhubItem: (...args) => removed.push(args),
      refreshInboxhubAfterHrmRevoke: () => { reloads += 1; },
    }},
  });
  context.revokeSignOffHrm('7');
  requests[0].success({success: true, message: 'Success'});
  assert.equal(reloads, 1);
  assert.deepEqual(removed, []);
  assert.deepEqual(closed, []);
});

test('an unparseable successful reply requires reconciliation before retry', () => {
  const status = element();
  const {context, requests, broadcasts, closed} = setup({
    elements: {signoff7: element(), hrmdoc_7: inboxWindowCard(), signoffstatus7: status},
  });
  context.signOffHrm('7');
  requests[0].error({status: 200, responseText: 'Success<script>oldDecorator()</script>'}, 'parsererror', 'Invalid JSON');
  assert.match(status.textContent, /Unable to confirm.*Refresh this report and inbox/);
  assert.equal(broadcasts.length, 0);
  assert.equal(closed.length, 0);
  assert(!status.textContent.includes('<script>'));
});
