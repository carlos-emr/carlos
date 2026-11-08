/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test=require('node:test');
const assert=require('node:assert/strict');
const {nativeGraphResponse}=require('./lib/native-graph-response');

function fixture(overrides={}) {
  let handler, matcher;
  const calls=[];
  const body=Buffer.from([137,80,78,71,13,10,26,10,255,128,0,254]);
  const response={body:async()=>body,status:()=>200,headers:()=>({'content-type':'image/png;charset=UTF-8'}),
    dispose:async()=>calls.push('dispose'),...overrides.response};
  const request={url:()=> 'https://example.test/carlos/encounter/GraphMeasurements?demographic_no=123&type=WT',
    method:()=> 'GET',isNavigationRequest:()=>true,frame:()=>({page:()=> 'native-page'}),...overrides.request};
  const route={request:()=>request,fetch:async options=>{calls.push(['fetch',options]);return response;},
    fulfill:async options=>calls.push(['fulfill',options]),abort:async()=>calls.push('abort'),...overrides.route};
  const context={route:async(m,h)=>{matcher=m;handler=h;calls.push('install');},unroute:async(m,h)=>{
    assert.equal(m,matcher);assert.equal(h,handler);calls.push('remove-exact-handler');
  }};
  const session={config:{baseUrl:new URL('https://example.test/carlos')},context,patient:'123'};
  return {session,body,response,calls,trigger:()=>handler(route),matches:url=>matcher(new URL(url))};
}

test('native binary capture fulfills the one UI request with identical bytes, status and charset header',async()=>{
  const f=fixture();
  const result=await nativeGraphResponse(f.session,async()=>{
    assert.equal(f.matches('https://elsewhere.test/carlos/encounter/GraphMeasurements'),false);
    assert.equal(f.matches('https://example.test/carlos/other'),false);
    await f.trigger();
  },{type:'WT'});
  assert.deepEqual(result.body,f.body);
  assert.equal(result.page,'native-page');
  assert.equal(result.headers['content-type'],'image/png;charset=UTF-8');
  assert.equal(f.calls.filter(c=>c[0]==='fetch').length,1);
  assert.deepEqual(f.calls.find(c=>c[0]==='fetch')[1],{maxRedirects:0,timeout:15000});
  assert.deepEqual(f.calls.find(c=>c[0]==='fulfill')[1],{response:f.response,body:f.body});
  assert.deepEqual(f.calls.filter(c=>typeof c==='string'),['install','dispose','remove-exact-handler']);
});

for(const [name,request] of [
  ['another patient',{url:()=> 'https://example.test/carlos/encounter/GraphMeasurements?demographic_no=456&type=WT'}],
  ['another type',{url:()=> 'https://example.test/carlos/encounter/GraphMeasurements?demographic_no=123&type=BP'}],
  ['ambiguous patient',{url:()=> 'https://example.test/carlos/encounter/GraphMeasurements?demographic_no=123&demographic_no=456&type=WT'}],
  ['non-navigation',{isNavigationRequest:()=>false}],['write method',{method:()=> 'POST'}],
])test('capture refuses '+name+' without fetching',async()=>{
  const f=fixture({request});
  await assert.rejects(nativeGraphResponse(f.session,f.trigger,{type:'WT'}),/unexpected patient, type or method/);
  assert.equal(f.calls.some(c=>c[0]==='fetch'),false);
  assert.ok(f.calls.includes('abort')&&f.calls.includes('remove-exact-handler'));
});

test('capture preserves fetch/body/fulfill failures and removes the exact handler',async()=>{
  for(const mode of ['fetch','body','fulfill']) {
    const error=new Error('controlled '+mode+' failure');
    const fail=async()=>{throw error;};
    const f=fixture(mode==='body'?{response:{body:fail}}:{route:{[mode]:fail}});
    await assert.rejects(nativeGraphResponse(f.session,f.trigger,{type:'WT'}),value=>value===error);
    assert.ok(f.calls.includes('remove-exact-handler'));
    if(mode!=='fetch')assert.ok(f.calls.includes('dispose'));
  }
});

test('missing native navigation and a failed click both fail and release interception',async()=>{
  let f=fixture();
  await assert.rejects(nativeGraphResponse(f.session,async()=>{},{type:'WT',timeout:10}),/not captured within its budget/);
  assert.ok(f.calls.includes('remove-exact-handler'));
  f=fixture();const error=new Error('click failure');
  await assert.rejects(nativeGraphResponse(f.session,async()=>{throw error;},{type:'WT'}),value=>value===error);
  assert.ok(f.calls.includes('remove-exact-handler'));
});

test('multiple graph navigations cannot silently replace the first capture',async()=>{
  const f=fixture();
  await assert.rejects(nativeGraphResponse(f.session,async()=>{await f.trigger();await f.trigger();},{type:'WT'}),/more than one/);
  assert.equal(f.calls.filter(c=>c[0]==='fetch').length,1);
  assert.ok(f.calls.includes('remove-exact-handler'));
});
