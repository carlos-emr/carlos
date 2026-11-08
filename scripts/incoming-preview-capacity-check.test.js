/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {recoveredPreviewBytes, capacityTarget, expectedCapacityConsole} = require('./lib/incoming-preview-capacity-check');
const {checkIncomingPreviewCapacity}=require('./lib/incoming-preview-capacity-check');
const destination = 'https://127.0.0.1/carlos/documentManager/ManageDocument?method=viewIncomingDocPageAsPdf&pdfName=owned.pdf';
const pdf = Buffer.from('%PDF-1.7\n' + 'x'.repeat(120) + '\n%%EOF\n');
function fixture({status = 200, mime = 'application/pdf', bytes = pdf, failBody = false} = {}) {
  let disposed = 0;
  const gets = [];
  return {gets, disposed: () => disposed, navigation: {url: () => destination,
    body: async () => Buffer.from('<html>Chromium PDF viewer document</html>')},
    context: {request: {get: async (url, options) => {
      gets.push({url, options});
      return {status: () => status, headers: () => ({'content-type': mime}),
        body: async () => {if (failBody) throw new Error('transport failed');return bytes;},
        dispose: async () => {disposed++;}};
    }}}};
}
test('native viewer HTML requires complete PDF bytes from its same authorized destination', async () => {
  const f = fixture();
  assert.deepEqual(await recoveredPreviewBytes(f.context, f.navigation, 'Pdf'), pdf);
  assert.equal(f.gets.length, 1);
  assert.equal(f.gets[0].url, destination);
  assert.equal(f.gets[0].options.maxRedirects, 0);
  assert.equal(f.disposed(), 1);
});
for (const [name, values] of [
  ['redirect', {status: 302}], ['busy', {status: 503}], ['HTML login', {mime: 'text/html'}],
  ['mislabelled HTML', {bytes: Buffer.from('<html>' + 'x'.repeat(200) + '</html>')}],
  ['truncated PDF', {bytes: pdf.subarray(0, -7)}], ['empty body', {bytes: Buffer.alloc(0)}],
  ['transport failure', {failBody: true}]
]) test(`${name} cannot stand in for a recovered PDF and disposes the response`, async () => {
  const f = fixture(values);
  await assert.rejects(recoveredPreviewBytes(f.context, f.navigation, 'Pdf'));
  assert.equal(f.disposed(), 1);
});
const {PNG}=require('pngjs');const image=new PNG({width:20,height:30});image.data.fill(255);const png=PNG.sync.write(image);
test('compatibility image read avoids the charset-corrupted CDP body and verifies the authorized PNG destination', async () => {
  const f=fixture({mime:'image/png;charset=UTF-8',bytes:png});f.navigation.url=()=>destination.replace('AsPdf','AsImage');
  f.navigation.body=async()=>Buffer.from(png.toString('utf8'),'utf8');
  assert.deepEqual(await recoveredPreviewBytes(f.context,f.navigation,'Image'),png);
  assert.equal(f.gets.length,1);assert.equal(f.gets[0].url,f.navigation.url());assert.equal(f.disposed(),1);
});
for(const bytes of [Buffer.from('not PNG'),Buffer.from(png.toString('utf8'),'utf8'),png.subarray(0,-5)])test('invalid or truncated binary preview cannot pass as an image: '+bytes.length,async()=>{
  const f=fixture({mime:'image/png',bytes});await assert.rejects(recoveredPreviewBytes(f.context,f.navigation,'Image'));assert.equal(f.disposed(),1);
});
test('unknown preview mode fails before a compatibility network read',async()=>{const f=fixture();await assert.rejects(recoveredPreviewBytes(f.context,f.navigation,'other'),/Unsupported/);assert.equal(f.gets.length,0);});

test('only a proven injected503 for the exact preview may ignore its client-only PDF-view fragment', () => {
  const targets = new Set([capacityTarget(destination)]);
  const issue = {type: 'error', text: 'Failed to load resource: the server responded with a status of 503 (Service Unavailable)',
    location: {url: destination + '#view=fitV'}};
  assert(expectedCapacityConsole(issue, targets));
  for (const change of [{text: 'Failed to load resource: 500'}, {type: 'warning'},
    {text: 'Unrelated 503 problem'}, {location: {url: destination.replace('owned.pdf', 'other.pdf')}},
    {location: {url: destination.replace('127.0.0.1', 'other.invalid')}}, {location: {}}, {location: null}]) {
    assert.equal(expectedCapacityConsole({...issue, ...change}, targets), false);
  }
  assert.equal(capacityTarget('not a URL'), null);
});

for(const forwardFailure of [false,true])test('preview cleanup preserves the primary error and unroute failure'+(forwardFailure?' and drains failed forwarding':''),async()=>{
  const primary=new Error('native preview assertion'),cleanup=new Error('unroute failed'),transport=new Error('upstream failed');
  let handler,release,drained=false;
  const gate=new Promise(resolve=>{release=resolve;});
  const page={
    route:async(_pattern,fn)=>{handler=fn;},
    unroute:async()=>{release();throw cleanup;},
    waitForResponse:()=>Promise.resolve({}),waitForEvent:()=>Promise.resolve(),
    locator:()=>({selectOption:async()=>{
      if(!forwardFailure)return;
      for(let index=0;index<6;index++){
        const operation=handler({request:()=>({url:()=>destination,method:()=> 'GET'}),
          fulfill:async()=>{},abort:async()=>{},fetch:async()=>{await gate;drained=true;throw transport;}});
        if(index<5)await operation;else operation.catch(()=>{});
      }
    }}),
    frameLocator:()=>({locator:()=>({waitFor:async()=>{throw primary;}})}),
  };
  await assert.rejects(checkIncomingPreviewCapacity({context:{},config:{baseUrl:'https://127.0.0.1/carlos'}},page,'owned.pdf'),error=>{
    assert(error instanceof AggregateError);assert(error.errors.includes(primary));assert(error.errors.includes(cleanup));
    assert.equal(error.errors.includes(transport),forwardFailure);return true;
  });
  assert.equal(drained,forwardFailure);
});
