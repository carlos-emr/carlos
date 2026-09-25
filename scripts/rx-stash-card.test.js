/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// Regression checks for the staged-card helpers in SearchDrug3.jsp:
//   #3871 - the card X button must send the card's stash random id, not the drug id;
//   #3872 - unticking ReRx must find the card through data-drug-ref-id, not set_<drugId>.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.join(__dirname, '..');
const jsp = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'), 'utf8');
const prescribe = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/rx/prescribe.jsp'), 'utf8');

function slice(startMarker, endMarker) {
    const start = jsp.indexOf(startMarker);
    const end = jsp.indexOf(endMarker, start);
    assert.ok(start >= 0 && end > start, `could not find ${startMarker}`);
    return jsp.slice(start, end);
}

const helpers = slice('function removeDrugFromReRxList(', 'function updateQty(');

function card(id, drugRefId) {
    return {
        id,
        removed: false,
        getAttribute(name) { return name === 'data-drug-ref-id' ? drugRefId : null; },
        remove() { this.removed = true; },
    };
}

function run(script, cards) {
    const requests = [];
    const alerts = [];
    const byId = Object.fromEntries(cards.map(c => [c.id, c]));
    const pane = { querySelectorAll() { return cards.filter(c => c.id.startsWith('set_')); } };
    const context = {
        document: { getElementById(id) { return id === 'rxText' ? pane : (byId[id] || null); } },
        jQuery(selector) { return { remove() { const el = byId[selector.slice(1)]; if (el) el.remove(); } }; },
        CarlosAjax: { request(url, options) { requests.push({url, options}); } },
        ctx: '/carlos',
        jsMsg: { removeRefused: 'Removal failed' },
        alert: message => alerts.push(message),
        selectedReRxIDs: [],
        updateReRxStageConfirmBoxVisibility() {},
        String,
    };
    const deleteHelper = slice('function deletePrescribe(', '    skipParseInstr = false;');
    const removeHelper = slice('function removeReRxDrugId(', '//represcribe a drug');
    vm.runInNewContext(`${deleteHelper}\n${removeHelper}\n${helpers}\n${script}`, context);
    return { requests, alerts, context };
}

test('card X waits for deletion success before hiding the card and unchecking ReRx', () => {
    const staged = card('set_734512', '4242');
    const checkbox = { id: 'reRxCheckBox_4242', checked: true };
    const { requests } = run('removePrescribingDrug(document.getElementById("set_734512"), 4242);', [staged, checkbox]);
    assert.equal(requests.length, 1);
    assert.match(requests[0].options.parameters, /^randomId=734512&/);
    assert.equal(staged.removed, false);
    assert.equal(checkbox.checked, true);
    requests[0].options.onSuccess();
    assert.equal(staged.removed, true);
    assert.equal(checkbox.checked, false);
});

test('a refused card deletion stays visible and can be retried', () => {
    const staged = card('set_900001', '0');
    const { requests, alerts, context } = run('removePrescribingDrug(document.getElementById("set_900001"), 0);', [staged]);
    requests[0].options.onFailure({status: 409});
    assert.equal(staged.removed, false);
    assert.deepEqual(alerts, ['Removal failed']);
    context.removePrescribingDrug(staged, 0);
    requests[1].options.onSuccess();
    assert.equal(staged.removed, true);
});

test('unticking ReRx waits for success before removing every card for the source', () => {
    const other = card('set_111', '77');
    const target = card('set_222', '4242');
    const duplicate = card('set_333', '4242');
    const { requests } = run('removeDrugFromReRxList(4242);', [other, target, duplicate]);
    assert.equal(target.removed, false);
    assert.match(requests[0].options.parameters, /^reRxDrugId=4242&action=removeFromReRxDrugIdList/);
    requests[0].options.onSuccess();
    assert.equal(target.removed, true);
    assert.equal(duplicate.removed, true);
    assert.equal(other.removed, false);
});

test('a refused ReRx removal restores its checkbox and retains the staged card', () => {
    const staged = card('set_222', '4242');
    const checkbox = { id: 'reRxCheckBox_4242', checked: false };
    const { requests, alerts } = run('removeDrugFromReRxList(4242);', [staged, checkbox]);
    requests[0].options.onFailure({status: 403});
    assert.equal(staged.removed, false);
    assert.equal(checkbox.checked, true);
    assert.deepEqual(alerts, ['Removal failed']);
});

test('archive-on-close mechanism is gone (#3871)', () => {
    assert.ok(!jsp.includes('deleteOnCloseRxBox'));
    assert.ok(!jsp.includes('DeleteRxOnCloseRxBox'));
});

test('staged cards carry an encoded data-drug-ref-id (#3872)', () => {
    assert.match(prescribe, /<fieldset[^>]*id="<%=fieldSetId%>"[^>]*data-drug-ref-id="<carlos:encode value='<%= String\.valueOf\(DrugReferenceId\) %>' context="htmlAttribute"\/>"/);
});


test('card X resolves its fieldset explicitly without named-window globals', () => {
    assert.match(prescribe, /onclick="removePrescribingDrug\(this\.closest\('fieldset'\), <%=DrugReferenceId%>\);"/);
});

test('declining a discontinued drug keeps the card until removal succeeds and skips focus', () => {
    const staged = card('set_901', '4242');
    const checkbox = { id: 'reRxCheckBox_4242', checked: true };
    const { requests, alerts, context } = run('', [staged, checkbox]);
    const start = prescribe.indexOf('            var isDiscontinuedLatest=');
    const end = prescribe.indexOf('</script>', start);
    let script = prescribe.slice(start, end)
        .replace(/<carlos:encode value='<%= archivedReason %>' context="javaScriptBlock"\/>/g, 'adverse reaction')
        .replace(/<carlos:encode value='<%= archivedDate %>' context="javaScriptBlock"\/>/g, '2026-01-01');
    const values = {isDiscontinuedLatest: 'true', fieldSetId: 'set_901', DrugReferenceId: '4242',
        'listRxDrugs.size()': '1', gcnCode: '0', rand: '901'};
    script = script.replace(/<%=\s*(.*?)\s*%>/g, (_, key) => {
        assert.ok(Object.hasOwn(values, key), `unexpected JSP expression: ${key}`);
        return values[key];
    });
    context.confirm = () => false;
    context.counterRx = 0;
    vm.runInNewContext(script, context);
    assert.equal(requests.length, 1);
    assert.match(requests[0].options.parameters, /^randomId=901&/);
    assert.equal(staged.removed, false);
    requests[0].options.onFailure({ status: 409 });
    assert.equal(staged.removed, false);
    assert.deepEqual(alerts, ['Removal failed']);
    requests[0].options.onSuccess();
    assert.equal(staged.removed, true);
    assert.equal(checkbox.checked, false);
});

test('discontinued confirmation encodes stored reason and date as JavaScript data', () => {
    for (const value of ['archivedReason', 'archivedDate']) {
        assert.ok(prescribe.includes(`<carlos:encode value='<%= ${value} %>' context="javaScriptBlock"/>`));
        assert.ok(!prescribe.includes(`<%=${value}%>`));
    }
});


for (const helper of ['updateQty', 'parseIntr']) {
    test(`${helper} ignores a response after its staged card was removed`, () => {
        let request;
        let present = true;
        let touchedMissingField = false;
        const context = {
            ctx: '/carlos',
            CarlosAjax: { request(url, options) { request = options; } },
            document: { getElementById(id) {
                if (id === 'set_901') return present ? {} : null;
                touchedMissingField = true;
                throw new Error('removed card fields must not be read');
            } },
        };
        vm.runInNewContext(slice('function updateQty(', '    function addLuCode('), context);
        context[helper]({id: helper === 'updateQty' ? 'quantity_901' : 'instructions_901', value: '30'});
        present = false;
        assert.doesNotThrow(() => request.onSuccess({responseText: '{"policyViolations":["retired card"]}'}));
        assert.equal(touchedMissingField, false);
    });
}

test('complete reset keeps cards and ReRx selection until the server accepts it', () => {
    const requests = [];
    const alerts = [];
    const pane = { textContent: 'staged prescription' };
    const checkbox = { checked: true };
    let focused = false;
    const context = {
        ctx: '/carlos', selectedReRxIDs: [42],
        document: {
            getElementById(id) { return id === 'rxText' ? pane : { focus() { focused = true; } }; },
            querySelectorAll() { return [checkbox]; },
        },
        CarlosAjax: { request(url, options) { requests.push({ url, options }); } },
        renderRxStage() {}, updateReRxStageConfirmBoxVisibility() {},
        reportRefusedRemoval() { alerts.push('refused'); },
    };
    vm.runInNewContext(slice('function clearStashDisplay(', 'function iterateStash('), context);
    context.resetStash();
    assert.equal(requests.length, 1);
    assert.equal(pane.textContent, 'staged prescription');
    assert.equal(checkbox.checked, true);
    requests[0].options.onFailure();
    assert.equal(pane.textContent, 'staged prescription');
    assert.equal(checkbox.checked, true);
    assert.deepEqual(alerts, ['refused']);
    context.resetStash();
    requests[1].options.onSuccess();
    assert.equal(pane.textContent, '');
    assert.equal(checkbox.checked, false);
    assert.equal(context.selectedReRxIDs.length, 0);
    assert.equal(focused, true);
});

const printJsp = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp'), 'utf8');
const resetStart = printJsp.indexOf('function resetStash(');
const printReset = printJsp.slice(resetStart, printJsp.indexOf('\n\n            /*', resetStart))
    .replace(/<%[\s\S]*?%>/g, '42');
for (const result of ['success', 'refused', 'network failure']) {
    test(`preview reset preserves cards and modal until acknowledgement: ${result}`, async () => {
        let resolve;
        let reject;
        let cleared = 0;
        let hidden = 0;
        let cancelled = 0;
        const alerts = [];
        const context = {
            cancelPendingFax() { cancelled++; }, getCsrfToken() { return 'token'; },
            fetch() { return new Promise((ok, fail) => { resolve = ok; reject = fail; }); },
            parent: {
                clearStashDisplay() { cleared++; },
                document: { getElementById() { return {}; } },
                bootstrap: { Modal: { getInstance() { return { hide() { hidden++; } }; } } },
            },
            alert(message) { alerts.push(message); },
        };
        vm.runInNewContext(printReset, context);
        const pending = context.resetStash();
        assert.equal(cancelled, 1);
        assert.equal(cleared, 0);
        assert.equal(hidden, 0);
        if (result === 'network failure') reject(new Error('offline'));
        else resolve({ ok: result === 'success' });
        assert.equal(await pending, result === 'success');
        assert.equal(cleared, result === 'success' ? 1 : 0);
        assert.equal(hidden, result === 'success' ? 1 : 0);
        assert.equal(alerts.length, result === 'success' ? 0 : 1);
    });
}

test('save and reset flows do not send a second unordered ReRx clear request', () => {
    assert.doesNotMatch(jsp, /resetReRxDrugList|parameterValue=clearReRxDrugList/);
    assert.doesNotMatch(printJsp, /resetReRxDrugList|parameterValue=clearReRxDrugList/);
    assert.match(printJsp, /onClick="resetStash\(\);"/);
});

for (const bootstrapLocation of ['iframe', 'unavailable']) {
    test(`acknowledged preview reset remains successful when modal API is ${bootstrapLocation}`, async () => {
        let cleared = 0;
        let hidden = 0;
        const alerts = [];
        const context = {
            cancelPendingFax() {}, getCsrfToken() { return 'token'; },
            fetch() { return Promise.resolve({ ok: true }); },
            parent: {
                clearStashDisplay() { cleared++; },
                document: { getElementById() { return {}; } },
            },
            alert(message) { alerts.push(message); },
        };
        if (bootstrapLocation === 'iframe') {
            context.bootstrap = { Modal: { getInstance() { return { hide() { hidden++; } }; } } };
        }
        vm.runInNewContext(printReset, context);
        assert.equal(await context.resetStash(), true);
        assert.equal(cleared, 1);
        assert.equal(hidden, bootstrapLocation === 'iframe' ? 1 : 0);
        assert.equal(alerts.length, 0);
    });
}
