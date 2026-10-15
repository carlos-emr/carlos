/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const dir = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx');
for (const name of ['ManagePharmacy2.jsp', 'SelectPharmacy2.jsp']) {
    const source = fs.readFileSync(path.join(dir, name), 'utf8');
    const start = source.indexOf('function reportPharmacyFailure(');
    const end = source.indexOf('jQuery(document).ajaxError(reportPharmacyFailure);', start);
    const code = source.slice(start, end)
        .replace('${carlos:forJavaScript(msg_pharmacyIncomplete)}', 'review pharmacies before retrying')
        .replace('${carlos:forJavaScript(msg_pharmacyRefused)}', 'request refused');
    test(`${name} explains failed writes and releases its spinner without reloading`, () => {
        const alerts = [];
        let released = 0;
        const context = {alert: message => alerts.push(message), HideSpin() {released++;}};
        vm.runInNewContext(code, context);
        context.reportPharmacyFailure(null, {status: 500, responseJSON: {error: 'INCOMPLETE_PHARMACY_UPDATE'}},
            {url: '/carlos/rx/managePharmacy?method=setPreferred'});
        context.reportPharmacyFailure(null, {status: 403}, {url: '/carlos/rx/managePharmacy?method=save'});
        context.reportPharmacyFailure(null, {status: 500}, {url: '/unrelated'});
        assert.deepEqual(alerts, ['review pharmacies before retrying', 'request refused']);
        assert.equal(released, 2);
    });
}
test('discontinuation failure keeps the dialog and explains partial persistence instead of signalling success', () => {
    const source = fs.readFileSync(path.join(dir, 'SearchDrug3.jsp'), 'utf8');
    const start = source.indexOf('function Discontinue2(');
    const code = source.slice(start, source.indexOf('//represcribe long term meds', start))
        .replace(/<%[\s\S]*?%>/g, '42');
    const requests = [];
    const alerts = [];
    const context = {
        ctx: '/carlos', CarlosAjax: {request(url, options) { requests.push(options); }},
        jsMsg: {discontinueIncomplete: 'review medication and encounter note before retrying'},
        alert: message => alerts.push(message), reportRefusedRequest() {alerts.push('request refused');},
        document: { getElementById() {assert.fail('failure must not hide the dialog or mark the drug discontinued');}},
    };
    vm.runInNewContext(code, context);
    context.Discontinue2('42', 'allergy', 'owned note', 'drug');
    requests[0].onFailure({status: 500, responseJSON: {error: 'INCOMPLETE_RX_DISCONTINUE'}});
    requests[0].onFailure({status: 403});
    requests[0].onSuccess({status: 200, responseText: '<html>login</html>'});
    requests[0].onSuccess({status: 200, responseText: '{"id":99}'});
    assert.deepEqual(alerts, ['review medication and encounter note before retrying',
        'request refused', 'request refused', 'request refused']);
});
