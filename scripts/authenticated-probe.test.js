/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { authenticatedProbe } = require('./lib/authenticated-probe');
const cookie = { name: 'JSESSIONID', value: 'synthetic', domain: '127.0.0.1', path: '/carlos', secure: true, expires: -1 };
function withHeartbeat(fetch) {return async(url,options)=>new URL(url).pathname.endsWith('/status/SessionHeartbeat')?{status:()=>200,json:async()=>({valid:true}),dispose:async()=>{}}:fetch(url,options);}
function fixture(cookies = [cookie], status = 405) {
  const calls = [];
  const session = { config: { baseUrl: new URL('http://127.0.0.1/carlos') }, context: {
    cookies: async () => cookies,
    request: { fetch: withHeartbeat(async (...args) => { calls.push(args); return { status: () => status }; }) },
  } };
  return { session, calls };
}
for (const method of ['GET', 'HEAD']) {
  test(`${method} explicitly carries the Secure browser session over HTTP loopback`, async () => {
    const { session, calls } = fixture();
    await authenticatedProbe(session, '/carlos/save', { method, maxRedirects: 5 });
    assert.equal(calls[0][1].headers.Cookie, 'JSESSIONID=synthetic');
    assert.equal(calls[0][1].maxRedirects, 0);
  });
}
test('does not forward unrelated, expired or wrong-path cookies', async () => {
  const { session, calls } = fixture([cookie,
    { ...cookie, name: 'other', domain: 'other.invalid' },
    { ...cookie, name: 'wrongpath', path: '/carlosx' },
    { ...cookie, name: 'expired', expires: 1 },
  ]);
  await authenticatedProbe(session, '/carlos/save');
  assert.equal(calls[0][1].headers.Cookie, 'JSESSIONID=synthetic');
});
test('rejects missing session, foreign origins and unsupported methods before sending', async () => {
  const { session, calls } = fixture([]);
  await assert.rejects(authenticatedProbe(session, '/carlos/save'), /session cookie/);
  await assert.rejects(authenticatedProbe(session, 'https://other.invalid/carlos/save'), /origin/);
  await assert.rejects(authenticatedProbe(session, '/carlos/save', { method: 'PUT', allowPost: true }), /GET, HEAD and POST/);
  assert.equal(calls.length, 0);
});
test('rejects login redirects even for callers checking only unchanged rows', async () => {
  const { session } = fixture([cookie], 302);
  await assert.rejects(authenticatedProbe(session, '/carlos/save?secret=private', {method: 'HEAD'}), error => {
    assert.match(error.message, /HEAD \/carlos\/save \(HTTP 302\)/);
    assert.doesNotMatch(error.message, /secret|private/);
    return true;
  });
});

test('real Playwright HTTP transport sends the Secure cookie to a local refusal endpoint', async t => {
  const http = require('node:http');
  const { request } = require('playwright');
  const seen = [];
  let live=true;
  const server = http.createServer((req, res) => {
    seen.push({method: req.method, cookie: req.headers.cookie, path:req.url});
    if(req.url==='/carlos/pending'||req.url==='/carlos/loggedout'){res.writeHead(302,{'x-permitted-cross-domain-policies':'none',location:req.url==='/carlos/pending'?'/carlos/select_facility':'/carlos/logoutPage'});res.end();return;}
    if(req.url.endsWith('/status/SessionHeartbeat')){res.writeHead(200,{'content-type':'application/json'});res.end(JSON.stringify({valid:live&&req.headers.cookie==='JSESSIONID=synthetic'}));return;}
    res.writeHead(req.headers.cookie === 'JSESSIONID=synthetic' ? 405 : 302,
      {'x-permitted-cross-domain-policies': 'none', location: '/login'});
    res.end();
  });
  await new Promise((resolve, reject) => {server.once('error', reject); server.listen(0, '127.0.0.1', resolve);});
  t.after(() => new Promise(resolve => server.close(resolve)));
  const baseUrl = new URL(`http://127.0.0.1:${server.address().port}/carlos`);
  const context = await request.newContext({storageState: {cookies: [{...cookie, httpOnly: true, sameSite: 'Lax'}], origins: []}});
  t.after(() => context.dispose());
  const session = {config: {baseUrl}, context: {
    cookies: async () => (await context.storageState()).cookies,
    request: context,
  }};
  for (const method of ['GET', 'HEAD']) {
    const response = await authenticatedProbe(session, '/carlos/save', {method});
    assert.equal(response.status(), 405);
    await response.dispose();
  }
  const {probe,classify}=require('./lib/authz-read-probe');
  const roleRead=await probe(session.context,new URL('/carlos/save',baseUrl).href,{baseUrl});
  assert.equal(classify(roleRead),'refused');
  assert.deepEqual(seen.filter(row=>row.path==='/carlos/save').map(({method,cookie})=>({method,cookie})), ['GET', 'HEAD','GET'].map(method => ({method, cookie: 'JSESSIONID=synthetic'})));
  const heartbeats=seen.filter(row=>row.path.endsWith('/status/SessionHeartbeat'));
  assert.equal(heartbeats.length,3);assert(heartbeats.every(row=>row.cookie==='JSESSIONID=synthetic'));
  live=false;
  await assert.rejects(authenticatedProbe(session,'/carlos/save'),/session is invalid/);
  assert.equal(seen.filter(row=>row.path==='/carlos/save').length,3,'An invalidated session must not reach the always-405 mutator');
  const {probeAuthenticationState}=require('./lib/authz-read-probe');
  const heartbeatCount=seen.filter(row=>row.path.endsWith('/status/SessionHeartbeat')).length;
  const pending=await probeAuthenticationState(session.context,new URL('/carlos/pending',baseUrl).href,{baseUrl});
  assert.equal(pending.status,302);assert.equal(pending.location,'/carlos/select_facility');
  const loggedOut=await probeAuthenticationState({...session.context,cookies:async()=>[]},new URL('/carlos/loggedout',baseUrl).href,{baseUrl});
  assert.equal(loggedOut.status,302);assert.equal(loggedOut.location,'/carlos/logoutPage');
  assert.equal(seen.filter(row=>row.path.endsWith('/status/SessionHeartbeat')).length,heartbeatCount,'Authentication state reads must not require a live login');
  assert(!seen.some(row=>row.path==='/carlos/logoutPage'||row.path==='/carlos/select_facility'),'Authentication state reads followed a redirect');
});


test('never sends a Secure cookie to a non-loopback HTTP target', async () => {
  const { session, calls } = fixture();
  session.config.baseUrl = new URL('http://192.168.1.2/carlos');
  await assert.rejects(authenticatedProbe(session, '/carlos/save'), /HTTPS outside loopback/);
  assert.equal(calls.length, 0);
});
test('replaces caller Cookie headers regardless of casing', async () => {
  const { session, calls } = fixture();
  await authenticatedProbe(session, '/carlos/save', {headers: {cookie: 'wrong', COOKIE: 'also-wrong', Accept: 'text/html'}});
  assert.deepEqual(calls[0][1].headers, {Accept: 'text/html', Cookie: 'JSESSIONID=synthetic'});
});

test('explicit security-redirect preconditions accept only the exact local security route', async () => {
  const {session,calls}=fixture([cookie],302);
  for(const location of ['/carlos/login','/login','https://other.invalid/carlos/securityError','/carlos/securityError/other','https://other.invalid/carlos/noRights.html','/carlos/noRights.html/other']) {
    session.context.request.fetch=withHeartbeat(async()=>({status:()=>302,headers:()=>({location})}));
    await assert.rejects(authenticatedProbe(session,'/carlos/admin',{allowSecurityErrorRedirect:true}),/redirected/);
  }
  session.context.request.fetch=withHeartbeat(async(url,options)=>{calls.push([url,options]);return {status:()=>302,headers:()=>({location:'/carlos/securityError?type=_admin'})};});
  assert.equal((await authenticatedProbe(session,'/carlos/admin',{allowSecurityErrorRedirect:true})).status(),302);
  assert.equal(calls.at(-1)[1].headers.Cookie,'JSESSIONID=synthetic');
  assert.equal(Object.hasOwn(calls.at(-1)[1],'allowSecurityErrorRedirect'),false);
});

test('explicit refusal redirect option accepts the exact application noRights route without following it',async()=>{
  const {session,calls}=fixture();
  session.context.request.fetch=withHeartbeat(async(url,options)=>{calls.push([url,options]);return {status:()=>302,headers:()=>({location:'/carlos/noRights.html'})};});
  const response=await authenticatedProbe(session,'/carlos/billing',{allowSecurityErrorRedirect:true});
  assert.equal(response.status(),302);assert.equal(calls[0][1].maxRedirects,0);
});

test('a retained cookie cannot turn a malformed or logged-out heartbeat into authenticated evidence',async()=>{
  for(const result of [{status:302,body:{valid:true}},{status:200,body:{valid:false}},{status:200,body:{}},{status:200,body:{valid:'true'}}]) {
    const {session}=fixture();let disposed=false,count=0;
    session.context.request.fetch=async url=>{count++;assert.match(url,/status\/SessionHeartbeat$/);return {status:()=>result.status,json:async()=>result.body,dispose:async()=>{disposed=true;}};};
    await assert.rejects(authenticatedProbe(session,'/carlos/save'),/session is invalid/);assert.equal(count,1);assert.equal(disposed,true);
  }
});

test('authentication-state transport remains origin-bound and read-only without requiring login',async()=>{
  const {authenticationStateProbe}=require('./lib/authenticated-probe'),{session,calls}=fixture([],302);
  const response=await authenticationStateProbe(session,'/carlos/login');assert.equal(response.status(),302);assert.equal(calls.length,1);assert.equal(calls[0][1].maxRedirects,0);
  await assert.rejects(authenticationStateProbe(session,'https://other.invalid/carlos/login'),/origin/);
  await assert.rejects(authenticationStateProbe(session,'/carlos/login',{method:'POST'}),/GET and HEAD/);
  await assert.rejects(authenticationStateProbe(session,'/carlos/login',{method:'POST',allowPost:true}),/GET and HEAD/);
  assert.equal(calls.length,1);
  await authenticationStateProbe(session,'/carlos/login',{allowPost:true});
  assert.equal(Object.hasOwn(calls.at(-1)[1],'allowPost'),false);
});

test('shared authenticated read probes reject POST before network activity unless explicitly opted in', async () => {
  const { probe } = require('./lib/authz-read-probe');
  for (const allowPost of [undefined, false, 'true', 1]) {
    const { session } = fixture();
    let requests = 0;
    session.context.request.fetch = async () => { requests++; throw new Error('No request should be sent'); };
    await assert.rejects(authenticatedProbe(session, '/carlos/save', { method: 'POST', allowPost }), /GET and HEAD.*allowPost: true/);
    const answer = await probe(session.context, 'http://127.0.0.1/carlos/save', {
      method: 'POST', allowPost, baseUrl: session.config.baseUrl,
    });
    assert.equal(answer.status, 0);
    assert.match(answer.error, /GET and HEAD/);
    assert.equal(requests, 0, 'An unapproved POST must not even fetch heartbeat or token prerequisites');
  }
});

test('POST carries the live session and its CSRF token without trusting caller headers', async () => {
  const { session, calls } = fixture();
  let disposed = false;
  session.context.request.fetch = withHeartbeat(async (url, options) => {
    calls.push([url, options]);
    if (new URL(url).pathname === '/carlos/csrfguard') return { status: () => 200,
      text: async () => 'var masterTokenValue = "synthetic-token";', dispose: async () => { disposed = true; } };
    return { status: () => 403 };
  });
  await authenticatedProbe(session, '/carlos/print', { method: 'POST', allowPost: true, headers: {
    'csrf-token': 'stale-token', 'x-requested-with': 'wrong', Cookie: 'unrelated',
  } });
  assert.equal(calls.length, 2);
  assert.equal(calls[0][1].headers.Cookie, 'JSESSIONID=synthetic');
  assert.equal(calls[0][1].maxRedirects, 0);
  assert(calls.every(([, options]) => !Object.hasOwn(options, 'allowPost')), 'The probe capability flag must never reach fetch');
  assert.deepEqual(calls[1][1].headers, {
    Cookie: 'JSESSIONID=synthetic', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': 'synthetic-token',
  });
  assert(disposed, 'The token response must be disposed');
});

for (const [status, body] of [[302, 'var masterTokenValue="irrelevant";'], [403, 'Forbidden'], [200, '<html>Login</html>']]) {
  test(`POST never sends its target when CSRF bootstrap fails: HTTP ${status}, token=${body.includes('masterTokenValue')}`, async () => {
    const { session } = fixture();
    let disposed = false, count = 0;
    session.context.request.fetch = withHeartbeat(async url => {
      count++;
      assert.equal(new URL(url).pathname, '/carlos/csrfguard');
      return { status: () => status, text: async () => body, dispose: async () => { disposed = true; } };
    });
    await assert.rejects(authenticatedProbe(session, '/carlos/print', { method: 'POST', allowPost: true }), /CSRF/);
    assert.equal(count, 1);
    assert(disposed);
  });
}

test('real Playwright POST reaches patient authorization with the session and CSRF header', async t => {
  const http = require('node:http');
  const { request } = require('playwright');
  const seen = [];
  const server = http.createServer((req, res) => {
    seen.push({ method: req.method, url: req.url, cookie: req.headers.cookie, token: req.headers['csrf-token'] });
    if (req.url === '/carlos/status/SessionHeartbeat') {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ valid: req.headers.cookie === 'JSESSIONID=synthetic' }));
    } else if (req.url === '/carlos/csrfguard') {
      res.writeHead(200, { 'content-type': 'text/javascript' });
      res.end('var masterTokenValue = "current-token";');
    } else {
      const valid = req.method === 'POST' && req.headers.cookie === 'JSESSIONID=synthetic'
        && req.headers['csrf-token'] === 'current-token' && req.headers['x-requested-with'] === 'XMLHttpRequest';
      res.writeHead(valid ? 403 : 400, { 'x-permitted-cross-domain-policies': 'none', 'content-type': 'text/html' });
      res.end(valid ? 'Security Exception' : 'Invalid transport');
    }
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  t.after(() => new Promise(resolve => server.close(resolve)));
  const baseUrl = new URL(`http://127.0.0.1:${server.address().port}/carlos`);
  const api = await request.newContext({ storageState: { cookies: [{ ...cookie, httpOnly: true, sameSite: 'Lax' }], origins: [] } });
  t.after(() => api.dispose());
  const context = { cookies: async () => (await api.storageState()).cookies, request: api };
  const { probe, forbiddenByApp } = require('./lib/authz-read-probe');
  const result = await probe(context, new URL('/carlos/print', baseUrl).href, { method: 'POST', allowPost: true, baseUrl });
  assert.equal(forbiddenByApp(result), true);
  assert.deepEqual(seen.map(row => row.url), ['/carlos/status/SessionHeartbeat', '/carlos/csrfguard', '/carlos/print']);
  assert(seen.every(row => row.cookie === 'JSESSIONID=synthetic'));
  assert.equal(seen.at(-1).token, 'current-token');
});

test('probe failures preserve their transport diagnostic instead of implying a server HTTP status', async () => {
  const { probe } = require('./lib/authz-read-probe');
  const { session } = fixture([]);
  const answer = await probe(session.context, 'http://127.0.0.1/carlos/print', { method: 'POST', allowPost: true, baseUrl: session.config.baseUrl });
  assert.equal(answer.status, 0);
  assert.match(answer.error, /requires an application session cookie/);
});

test('probe transport diagnostics redact every URL query in multiline Playwright call logs before printing', async () => {
  const h = require('./lib/playwright-harness');
  const { probe } = require('./lib/authz-read-probe');
  const { session } = fixture();
  const message = 'net::ERR_FAILED /carlos/print?demographic_no=PRIVATE123&name=PRIVATE-NAME\n'
    + 'Call log:\n  - GET http://127.0.0.1/carlos/second?secret=PRIVATE456#PRIVATE-FRAGMENT\n'
    + '  - referer: "https://127.0.0.1/carlos/third?patient=PRIVATE789"';
  const sanitized = h.withoutQueryStrings(message);
  assert.match(sanitized, /\/carlos\/print\n/);
  assert.match(sanitized, /http:\/\/127\.0\.0\.1\/carlos\/second\n/);
  assert.match(sanitized, /"https:\/\/127\.0\.0\.1\/carlos\/third"/);
  assert.doesNotMatch(sanitized, /PRIVATE|demographic_no|patient=|secret=|name=|[?#]/);
  session.context.request.fetch = withHeartbeat(async () => { throw new Error(message); });
  const answer = await probe(session.context, 'http://127.0.0.1/carlos/print?demographic_no=PRIVATE123', {
    baseUrl: session.config.baseUrl,
  });
  assert.equal(answer.status, 0);
  assert.match(answer.error, /net::ERR_FAILED \/carlos\/print/);
  assert.doesNotMatch(answer.error, /PRIVATE|demographic_no|patient=|secret=|name=|[?#]/);
  assert(answer.error.length <= 80);
});
