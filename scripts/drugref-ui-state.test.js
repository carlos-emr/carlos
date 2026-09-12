/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '..', 'src/main/webapp/WEB-INF/jsp');

for (const [url, accepted] of [
  ['http://127.0.0.1:8080/carlos/admin/ViewUpdateDrugref', true],
  ['https://example.invalid/carlos/admin/ViewUpdateDrugref', false],
  ['http://127.0.0.1:8081/carlos/admin/ViewUpdateDrugref', false],
  ['http://127.0.0.1:8080/other/admin/ViewUpdateDrugref', false],
  ['http://127.0.0.1:8080/carlos/admin/ViewUpdateDrugref/extra', false],
]) {
  test(`DrugRef popup validation ${accepted ? 'accepts' : 'rejects'} ${url}`, () => {
    const source = fs.readFileSync(path.join(__dirname, 'drugref-update-playwright-checks.js'), 'utf8');
    const start = source.indexOf('const popupUrl = new URL(popup.url());');
    const end = source.indexOf('// Step 1:', start);
    assert.ok(start >= 0 && end > start);
    const check = () => vm.runInNewContext(source.slice(start, end), {
      URL, assert, popup: { url: () => url }, config: { baseUrl: new URL('http://127.0.0.1:8080/carlos/') },
    });
    if (accepted) assert.doesNotThrow(check);
    else assert.throws(check, /unexpected origin or path/);
    assert.doesNotMatch(source, /page\.goto\(pageUrl\)/);
    assert.equal((source.match(/gotoApp\(page, config\.baseUrl, '\/admin\/ViewUpdateDrugref'\)/g) || []).length, 2);
  });
}

function adminState() {
  const source = fs.readFileSync(path.join(root, 'admin/updateDrugref.jsp'), 'utf8');
  const script = source.slice(source.indexOf('var POLL_MS'), source.indexOf('document.addEventListener("DOMContentLoaded"'))
    .replace('${canTriggerUpdate ? \'true\' : \'false\'}', 'true');
  const nodes = new Map();
  const timers = new Map();
  let sequence = 0;
  const context = vm.createContext({
    URLSearchParams, console: { error() {}, warn() {} },
    document: { getElementById(id) {
      if (!nodes.has(id)) nodes.set(id, { style: {}, textContent: '' });
      return nodes.get(id);
    } },
    setTimeout(fn) { const id = ++sequence; timers.set(id, fn); return id; },
    clearTimeout(id) { timers.delete(id); },
  });
  vm.runInContext(script, context);
  context.baselineKnown = true;
  context.lastVerifiedUpdate = '2026-09-10';
  return { context, nodes, timers };
}

for (const outcome of ['transport', 'null-result']) {
  test(`DrugRef ${outcome} outcome locks retries and does not trust a stale success`, async () => {
    const { context, nodes, timers } = adminState();
    let posts = 0;
    context.callDrugref = async (method) => {
      if (method === 'updateDB') {
        posts++;
        if (outcome === 'transport') throw new Error('response lost');
        return { result: null };
      }
      return method === 'status' ? { state: 'SUCCEEDED' } : { lastUpdate: '2026-09-10', version: 'fixture', drugDatabase: 'DPD' };
    };
    context.startUpdate();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(context.updateRequestUncertain, true);
    assert.equal(nodes.get('updateButton').style.display, 'none');
    context.updateDB();
    context.startUpdate();
    assert.equal(posts, 1);
    await context.pollStatus();
    assert.equal(context.updateRequestUncertain, true);
    assert.equal(nodes.get('updateButton').style.display, 'none');
    assert.equal(timers.size, 1);
  });
}

for (const finalState of ['SUCCEEDED', 'FAILED']) {
  test(`DrugRef permits retry only after observing the uncertain run then ${finalState}`, async () => {
    const { context, nodes } = adminState();
    context.baselineKnownBeforeRun = true;
    context.lastUpdateBeforeRun = '2026-09-10';
    context.markUpdateUncertain();
    context.callDrugref = async method => method === 'status' ? { state: 'RUNNING' } : { lastUpdate: 'updating' };
    await context.pollStatus();
    assert.equal(context.observedUncertainRun, true);
    assert.equal(nodes.get('updateButton').style.display, 'none');
    context.callDrugref = async method => method === 'status' ? { state: finalState } : { lastUpdate: '2026-09-10', version: 'fixture', drugDatabase: 'DPD' };
    await context.pollStatus();
    assert.equal(context.updateRequestUncertain, false);
    assert.equal(nodes.get('updateButton').style.display, 'block');
  });
}

test('DrugRef can confirm a completed missed run from a changed known timestamp', async () => {
  const { context } = adminState();
  context.baselineKnownBeforeRun = true;
  context.lastUpdateBeforeRun = '2026-09-10';
  context.markUpdateUncertain();
  context.callDrugref = async method => method === 'status' ? { state: 'UNAVAILABLE' } : { lastUpdate: '2026-09-12', version: 'fixture', drugDatabase: 'DPD' };
  await context.pollStatus();
  assert.equal(context.updateRequestUncertain, false);
});

for (const timestamp of ['2026-09-09', 'not-a-timestamp']) {
  test(`DrugRef does not confirm an uncertain request from ${timestamp}`, async () => {
    const { context } = adminState();
    context.baselineKnownBeforeRun = true;
    context.lastUpdateBeforeRun = '2026-09-10';
    context.markUpdateUncertain();
    context.callDrugref = async method => method === 'status' ? { state: 'SUCCEEDED' } : { lastUpdate: timestamp, version: 'fixture', drugDatabase: 'DPD' };
    await context.pollStatus();
    assert.equal(context.updateRequestUncertain, true);
  });
}

for (const outage of ['transport', 'updating', 'unavailable']) {
  test(`prescription header retains its metadata nodes across ${outage} and recovery`, async () => {
    const source = fs.readFileSync(path.join(root, 'rx/TopLinks2.jspf'), 'utf8');
    const script = source.slice(source.indexOf('function getDrugRefStatus()'), source.indexOf('document.addEventListener("DOMContentLoaded"'));
    const nodes = Object.fromEntries(['drugrefHeaderAlert', 'drugrefHeaderMetadata', 'dbDateTime', 'drugDatabaseVersion', 'drugDatabase'].map(id => [id, { textContent: '', hidden: false }]));
    const context = vm.createContext({
      Intl, Date, msgDrugrefUpdating: 'updating', msgDrugrefUnavailable: 'unavailable', msgDrugrefUnavailableContact: 'contact',
      document: { querySelector() { return null; }, getElementById(id) {
        assert.ok(nodes[id], `must not replace metadata parent ${id}`);
        return nodes[id];
      } },
      fetch: async () => {
        if (outage === 'transport') throw new Error('offline');
        return { ok: true, json: async () => ({ lastUpdate: outage === 'updating' ? 'updating' : null }) };
      },
    });
    vm.runInContext(script, context);
    await context.getDrugRefStatus();
    assert.equal(nodes.drugrefHeaderMetadata.hidden, true);
    context.fetch = async () => ({ ok: true, json: async () => ({ lastUpdate: '2026-09-12', version: 'fixture', drugDatabase: 'DPD' }) });
    await context.getDrugRefStatus();
    assert.equal(nodes.drugrefHeaderAlert.hidden, true);
    assert.equal(nodes.drugrefHeaderMetadata.hidden, false);
    assert.equal(nodes.drugDatabase.textContent, 'DPD');
    assert.equal(nodes.drugDatabaseVersion.textContent, 'fixture');
    assert.ok(nodes.dbDateTime.textContent);
  });
}

test('prescription metadata keeps its flex layout without overriding the hidden state', () => {
  const source = fs.readFileSync(path.join(root, 'rx/SearchDrug3.jsp'), 'utf8');
  assert.match(source, /#statusDisplay, #drugrefHeaderMetadata:not\(\[hidden\]\)\s*\{[^}]*display:\s*flex/);
});
