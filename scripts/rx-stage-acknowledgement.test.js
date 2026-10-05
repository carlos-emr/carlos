/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'), 'utf8');
function between(start, end) {
    const from = jsp.indexOf(start);
    const to = jsp.indexOf(end, from);
    assert.ok(from >= 0 && to > from);
    return jsp.slice(from, to);
}
function fixture() {
    const requests = [];
    const alerts = [];
    const boxes = new Map([[11, {checked: true}], [22, {checked: true}]]);
    const context = {
        ctx: '/carlos', selectedReRxIDs: [11, 22],
        URL, window: {location: {href: 'https://example.test/carlos/rx/choosePatient'}},
        CarlosAjax: { updater(container, url, options) { requests.push({container, url, options}); } },
        removeReRxDrugId(drugId, onSuccess, onFailure) { requests.push({drugId, onSuccess, onFailure}); },
        getReRxCheckboxByUiRefId: id => boxes.get(id),
        updateReRxStageConfirmBoxVisibility() {}, renderRxStage() {},
        reportRefusedRequest() { alerts.push('stage refused'); },
        reportRefusedRemoval() { alerts.push('removal refused'); },
    };
    vm.runInNewContext(between('function cancelAndClearSelection(', '/**') + '\n'
        + between('function rePrescribeMulti(', '/**'), context);
    return {requests, alerts, boxes, context};
}
for (const status of [0, 403, 409, 500]) {
    test(`refused staging (${status}) retains pending selections and does not insert the error response`, () => {
        const f = fixture();
        f.context.stageSelectedReRxMedications();
        const request = f.requests[0];
        assert.equal(request.container.success, 'rxText');
        assert.equal(request.container.failure, undefined);
        assert.match(request.options.parameters, /drugIds=11%2C22/);
        request.options.onFailure({status});
        request.options.onComplete({status});
        assert.deepEqual(Array.from(f.context.selectedReRxIDs), [11, 22]);
        assert.deepEqual(f.alerts, ['stage refused']);
        f.context.stageSelectedReRxMedications();
        const accepted = {status: 200, responseURL: 'https://example.test/carlos/rx/rePrescribe2?demographicNo=42', responseText: '<fieldset></fieldset>'};
        f.requests[1].options.onSuccess(accepted);
        f.requests[1].options.onComplete(accepted);
        assert.deepEqual(Array.from(f.context.selectedReRxIDs), []);
    });
}
test('cancel retains refused selections and save does not proceed after a partial cancellation', () => {
    const f = fixture();
    let saved = 0;
    f.context.cancelAndClearSelection(() => saved++);
    assert.equal(f.boxes.get(11).checked, true);
    f.requests[0].onSuccess();
    f.requests[1].onFailure();
    assert.equal(saved, 0);
    assert.equal(f.boxes.get(11).checked, false);
    assert.equal(f.boxes.get(22).checked, true);
    assert.deepEqual(Array.from(f.context.selectedReRxIDs), [22]);
    f.context.cancelAndClearSelection(() => saved++);
    f.requests[2].onSuccess();
    assert.equal(saved, 1);
    assert.deepEqual(Array.from(f.context.selectedReRxIDs), []);
});

for (const [url, body] of [
    ['https://example.test/carlos/login', '<html>login</html>'],
    ['https://other.test/carlos/rx/rePrescribe2', '<fieldset></fieldset>'],
    ['https://example.test/carlos/rx/rePrescribe2', '<!doctype html><html>error</html>'],
]) {
    test(`successful error or redirected staging response retains selection: ${url}`, () => {
        const f = fixture();
        f.context.stageSelectedReRxMedications();
        const response = {status: 200, responseURL: url, responseText: body};
        f.requests[0].options.onSuccess(response);
        assert.equal(response.responseText, null, 'updater must receive no login/error HTML to insert');
        f.requests[0].options.onComplete(response);
        assert.deepEqual(Array.from(f.context.selectedReRxIDs), [11, 22]);
        assert.deepEqual(f.alerts, ['stage refused']);
    });
}
