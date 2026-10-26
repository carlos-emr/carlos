/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '..');
const jspRoot = path.join(root, 'src/main/webapp/WEB-INF/jsp/rx');
const jsp = fs.readFileSync(path.join(jspRoot, 'SearchDrug3.jsp'), 'utf8');
function slice(startMarker, endMarker) {
  const start = jsp.indexOf(startMarker);
  const end = jsp.indexOf(endMarker, start);
  assert.ok(start >= 0 && end > start);
  return jsp.slice(start, end);
}
function fixture() {
  const requests = [];
  const alerts = [];
  let restored = false;
  const context = {
    ctx: '/carlos', jsMsg: { requestRefused: 'Request refused' },
    alert(message) { alerts.push(message); }, renderRxStage() {},
    CarlosAjax: {
      updater(container, url, options) { requests.push({ container, url, options }); },
      request(url, options) { requests.push({ url, options }); },
    },
    checkFav() { assert.ok(restored, 'favorite started before existing cards were inserted'); context.useFav2('123'); },
    popForm2() { assert.fail('a refused reprint must not open a preview'); },
  };
  vm.runInNewContext([
    slice('function iterateStash(', 'function rxPageSizeSelect('),
    slice('function useFav2(', 'function calculateRxData('),
    slice('function reportRefusedRequest(', '//represcribe a drug'),
    slice('function reprint2(', '/**'),
  ].join('\n'), context);
  return { requests, alerts, context, restore() { restored = true; } };
}

test('favorite handoff waits until the initial stash response has been inserted', () => {
  const f = fixture();
  f.context.iterateStash();
  assert.equal(f.requests.length, 1);
  f.requests[0].options.onSuccess({ status: 200, responseText: 'existing cards' });
  assert.equal(f.requests.length, 1);
  f.restore();
  f.requests[0].options.onComplete({ status: 200 });
  assert.equal(f.requests.length, 2);
  assert.match(f.requests[1].url, /\/rx\/useFavorite$/);
  assert.match(f.requests[1].options.parameters, /favoriteId=123&/);
  assert.doesNotMatch(jsp, /onload="checkFav\(\);/);
});

for (const status of [409, 500]) {
  test(`refused initial stash ${status} does not stage a favorite or append an error page`, () => {
    const f = fixture();
    f.context.iterateStash();
    const request = f.requests[0];
    assert.equal(request.container.success, 'rxText');
    assert.equal(request.container.failure, undefined);
    request.options.onFailure({ status });
    request.options.onComplete({ status });
    assert.equal(f.requests.length, 1);
    assert.deepEqual(f.alerts, ['Request refused']);
  });
}

test('favorite and reprint failures explain the refusal without changing the displayed draft', () => {
  const f = fixture();
  f.context.useFav2('123');
  assert.equal(f.requests[0].container.success, 'rxText');
  assert.equal(f.requests[0].container.failure, undefined);
  f.requests[0].options.onFailure({ status: 409 });
  f.context.reprint2('45');
  f.requests[1].options.onFailure({ status: 404 });
  assert.deepEqual(f.alerts, ['Request refused', 'Request refused']);
});

for (const name of ['SideLinksEditFavorites2.jsp', 'SideLinksNoEditFavorites.jsp', 'SideLinksNoEditFavorites2.jsp']) {
  test(`${name} sends favorite handoff to the actual prescribing page`, () => {
    const source = fs.readFileSync(path.join(jspRoot, name), 'utf8');
    assert.match(source, /\/rx\/choosePatient\?demographicNo=.*?&usefav=true&favid=/);
    assert.doesNotMatch(source, /\/rx\/searchDrug\?demographicNo=.*?&usefav=true/);
  });
}

// The action has already authorised and selected this exact persisted script. Reusing that
// request-local snapshot keeps its display coherent and avoids loading it twice in one request.
test('reprint display uses the action snapshot before considering a persisted reload', () => {
  const view = fs.readFileSync(path.join(jspRoot, 'ViewScript2.jsp'), 'utf8');
  assert.match(view, /previewSnapshot = \(RxPreviewSnapshot\) request\.getAttribute\(RxPreviewSnapshot\.REQUEST_ATTRIBUTE\);\s*if \(previewSnapshot == null\) \{\s*previewSnapshot = RxPreviewSnapshot\.load\(viewScriptDemographicNo, scriptIdForFax\);/);
});
