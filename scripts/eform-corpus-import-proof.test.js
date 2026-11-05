'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const proof = require('./lib/eform-corpus-import-proof');
const item = { zip: 'fixture.zip', formName: 'Clinical form', sha256: 'a'.repeat(64) };
const ok = '<div class="alert alert-success">Your eform was imported.</div>';
const old = [{name: 'Clinical form older', fid: '10'}];
const added = [...old, {name: item.formName, fid: '11'}];
test('substring similarity cannot select a preexisting form', () => {
  proof.preflight([item], old, false);
  assert.equal(proof.importedId(old, added, item.formName, 200, ok), '11');
  assert.throws(() => proof.existingId(old, item.formName));
});
test('exact existing name refuses actual import, but explicit reuse pins its ID', () => {
  assert.throws(() => proof.preflight([item], added, false));
  proof.preflight([item], added, true);
  assert.equal(proof.existingId(added, item.formName), '11');
});
test('duplicate package names refuse before any import', () => assert.throws(() => proof.validatePackages([item, {...item, zip:'other.zip'}])));
test('missing names and invalid ZIP digests refuse', () => {
  assert.throws(() => proof.validatePackages([{...item, formName:null}]));
  assert.throws(() => proof.validatePackages([{...item, sha256:'unknown'}]));
});
for (const [name, status, body] of [['HTTP error',500,ok],['duplicate rejection',200,'Skipped form, form already exists'],['login redirect body',200,'Login']]) {
  test(name + ' cannot adopt an old or newly appearing ID', () => assert.throws(() => proof.importedId(old, added, item.formName, status, body)));
}
test('missing, wrong-name and concurrent additional IDs refuse', () => {
  for (const rows of [old, [...old,{name:'Other',fid:'11'}], [...added,{name:'Other',fid:'12'}]]) {
    assert.throws(() => proof.importedId(old, rows, item.formName, 200, ok));
  }
});
test('deleted or renamed baseline rows invalidate import provenance', () => {
  assert.throws(() => proof.importedId(old,[{name:item.formName,fid:'11'}],item.formName,200,ok));
  assert.throws(() => proof.importedId(old,[{name:'renamed',fid:'10'},added[1]],item.formName,200,ok));
});
test('manager uses exact title and validates source-qualified positive IDs', () => {
  const base = new URL('https://127.0.0.1/carlos');
  const row = {name:item.formName,href:'/carlos/eform/efmformmanageredit?fid=11'};
  assert.deepEqual(proof.managerRows([row],base),[added[1]]);
  for (const href of ['https://example.com/carlos/eform/efmformmanageredit?fid=11','/carlos/other?fid=11','/carlos/eform/efmformmanageredit?fid=0','/carlos/eform/efmformmanageredit?fid=11&fid=12']) {
    assert.throws(() => proof.managerRows([{...row,href}],base));
  }
  assert.throws(() => proof.managerRows([row,row],base));
});
test('receipts retain exact package digest and ID, and refuse overwrite', () => {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'corpus-proof-'));
  try {
    const record={package:item.zip,sha256:item.sha256,fid:'11'};
    proof.receipt(dir,'0-imported',record);
    assert.deepEqual(JSON.parse(fs.readFileSync(path.join(dir,'0-imported.json'))),record);
    assert.equal(fs.statSync(path.join(dir,'0-imported.json')).mode & 0o777,0o600);
    assert.throws(() => proof.receipt(dir,'0-imported',{fid:'12'}));
    assert.throws(() => proof.receipt(dir,'../escape',record));
    assert.deepEqual(JSON.parse(fs.readFileSync(path.join(dir,'0-imported.json'))),record);
  } finally { fs.rmSync(dir,{recursive:true,force:true}); }
});
test('authored decisions stay distinct; transport and invalid PDF outcomes fail', () => {
  for(const outcome of ['FORM REQUIRES INPUT: consent','PDF WITHHELD']) assert.equal(proof.failedOutcome({outcome}),false);
  assert.equal(proof.failedOutcome({outcome:'PDF OK',fid:'11',pdfBytes:200}),false);
  for(const outcome of ['NOT IMPORTED: refused','NO PDF: timeout','NOT A PDF','SKIPPED: missing form','UNKNOWN']) assert.equal(proof.failedOutcome({outcome}),true);
  assert.equal(proof.failedOutcome({outcome:'PDF OK',fid:'11',pdfBytes:0}),true);
});
