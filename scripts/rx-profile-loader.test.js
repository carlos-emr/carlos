/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const loader = require('../src/main/webapp/share/javascript/rx-profile-loader');

function fixture({abortable = true} = {}) {
    const requests = [];
    const state = {hidden: false, contents: [], failures: 0};
    const profile = loader.create({url:'/clinic/rx/ViewListDrugs', demographicNo:'123',
        loading(value) { state.hidden = value; },
        failed() { state.contents = ['error']; state.failures++; },
        updater(target, url, options) {
            const xhr = {};
            if (abortable) xhr.abort = () => { xhr.aborted = true; };
            const request = {target, url, options, xhr, finish(status = 200) {
                if (xhr.aborted) return;
                // Use CarlosAjax's real callback contract: insert before onComplete.
                if (status >= 200 && status < 300) {
                    const part = new URL(url, 'https://localhost').searchParams.get('heading') || url;
                    state.contents = options.insertion ? [...state.contents, part] : [part];
                }
                options.onComplete({status});
            }};
            requests.push(request);
            return xhr;
        }});
    return {profile, requests, state};
}

test('combined sections are requested in order and remain hidden until all are inserted', () => {
    const {profile, requests, state} = fixture();
    profile.select('combined');
    const expected = ['Long Term Meds','Acute','Inactive','External'];
    for (let i = 0; i < 4; i++) {
        assert.equal(requests.length, i + 1);
        assert.equal(state.hidden, true);
        assert.deepEqual(requests[i].target, {success:'drugProfile'});
        assert.equal(requests[i].options.parameters.demographicNo, '123');
        assert.equal(requests[i].options.evalScripts, true);
        requests[i].finish();
        assert.deepEqual(state.contents, expected.slice(0, i + 1));
    }
    assert.equal(state.hidden, false);
    assert.match(requests[1].url, /status=active/);
    assert.match(requests[2].url, /status=inactive/);
    assert.match(requests[3].url, /drugLocation=external/);
});

test('rapid selections discard queued old additions and display only the latest replacement', () => {
    const {profile, requests, state} = fixture();
    profile.select('combined');
    profile.select('all');
    profile.select('inactive');
    assert.equal(requests.length, 3);
    assert.equal(requests[0].xhr.aborted, true);
    assert.equal(requests[1].xhr.aborted, true);
    requests[0].finish(); requests[1].finish();
    assert.equal(state.hidden, true);
    assert.match(requests[2].url, /status=inactive$/);
    assert.equal(requests[2].options.insertion, undefined);
    requests[2].finish();
    assert.equal(state.hidden, false);
    assert.deepEqual(state.contents, ['/clinic/rx/ViewListDrugs?status=inactive']);
});

test('changing selection while an addition is pending cannot append into the newer view', () => {
    const {profile, requests, state} = fixture();
    profile.select('combined');
    requests[0].finish();
    profile.select('all');
    assert.equal(requests[1].xhr.aborted, true);
    requests[1].finish();
    assert.equal(requests.length, 3);
    assert.equal(state.hidden, true);
    requests[2].finish();
    assert.deepEqual(state.contents, ['/clinic/rx/ViewListDrugs?show=all']);
});

test('cancelling a stale request detaches its DOM insertion handler before abort', () => {
    const {profile, requests, state} = fixture();
    profile.select('combined');
    const old = requests[0];
    old.xhr.onload = () => { throw new Error('stale markup inserted'); };
    old.xhr.onerror = () => { throw new Error('stale error displayed'); };
    old.xhr.abort = () => {
        assert.equal(old.xhr.onload, null);
        assert.equal(old.xhr.onerror, null);
        assert.equal(old.xhr.onabort, null);
        assert.equal(old.xhr.ontimeout, null);
        old.xhr.aborted = true;
    };
    profile.select('inactive');
    assert.equal(requests.length, 2);
    requests[1].finish();
    old.options.onComplete({status:0});
    assert.equal(requests.length, 2);
    assert.equal(state.hidden, false);
    assert.equal(state.failures, 0);
    assert.deepEqual(state.contents, ['/clinic/rx/ViewListDrugs?status=inactive']);
});

test('refresh retains the full selected view instead of resetting to current', () => {
    const {profile, requests, state} = fixture();
    profile.select('longTermAcute');
    requests[0].finish(); requests[1].finish();
    profile.refresh();
    assert.equal(state.hidden, true);
    assert.equal(requests[2].url, requests[0].url);
    requests[2].finish(); requests[3].finish();
    assert.deepEqual(state.contents, ['Long Term Meds','Acute']);
});

for (const status of [0, 403, 500, undefined]) {
    test(`failed section (${status}) clears the partial list and can retry the same view`, () => {
        const {profile, requests, state} = fixture();
        profile.select('combined'); requests[0].finish();
        requests[1].options.onComplete({status});
        assert.equal(requests.length, 2);
        assert.deepEqual(state, {hidden:false, contents:['error'], failures:1});
        profile.refresh();
        for (let i = 2; i < 6; i++) requests[i].finish();
        assert.deepEqual(state.contents, ['Long Term Meds','Acute','Inactive','External']);
        assert.equal(state.hidden, false);
    });
}

test('a stale failure continues the newer selection without displaying an old error', () => {
    const {profile, requests, state} = fixture({abortable:false});
    profile.select('combined'); profile.select('active');
    requests[0].finish(500); requests[1].finish();
    assert.equal(state.failures, 0);
    assert.deepEqual(state.contents, ['/clinic/rx/ViewListDrugs?status=active']);
});

for (const event of ['ontimeout','onabort']) {
    test(`${event} releases the queue and duplicate completion does not advance it twice`, () => {
        const {profile, requests, state} = fixture({abortable:false});
        profile.select('combined'); profile.select('inactive');
        assert.equal(requests[0].xhr.timeout, 30000);
        requests[0].xhr[event]();
        requests[0].options.onComplete({status:0});
        assert.equal(requests.length, 2);
        requests[1].finish();
        assert.equal(state.hidden, false);
        assert.equal(state.failures, 0);
    });
}

test('a synchronous transport failure displays an error without leaving loading stuck', () => {
    let hidden;
    let failures = 0;
    const profile = loader.create({url:'/clinic/rx/ViewListDrugs', demographicNo:'1',
        updater() { throw new Error('transport unavailable'); },
        failed() { failures++; }, loading(value) { hidden = value; }});
    profile.refresh(); profile.refresh();
    assert.equal(failures, 2);
    assert.equal(hidden, false);
});

test('unknown views cannot change the active selection', () => {
    const {profile, requests} = fixture();
    assert.throws(() => profile.select('__proto__'), /Unknown/);
    profile.refresh();
    assert.equal(requests[0].url, '/clinic/rx/ViewListDrugs');
});

test('all legend links and refresh callbacks use the controller, including a hidden Current link', () => {
    const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'), 'utf8');
    assert.equal((jsp.match(/onclick="selectDrugProfile\(/g) || []).length, 6);
    assert.equal((jsp.match(/refreshDrugProfile\(\);/g) || []).length, 6);
    assert.doesNotMatch(jsp, /call(?:Replacement|Addition)WebService/);
    const start = jsp.indexOf('function CngClass(');
    const end = jsp.indexOf('function toggleStartDateUnknown(', start);
    const link = {};
    const context = vm.createContext({document:{getElementById() { return null; }}, Lst:null, link});
    vm.runInContext(jsp.slice(start, end) + '\nCngClass(link);', context);
    assert.equal(link.className, 'selected');
});
