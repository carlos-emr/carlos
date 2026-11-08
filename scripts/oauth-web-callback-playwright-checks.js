#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
// #5806: a real loopback receiver observes the browser's registered callback.
const http=require('node:http'),crypto=require('node:crypto');
const assert=require('node:assert/strict');
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {oauthHeader}=require('./oauth-rest-surfaces-playwright-checks');
const {cleanupClients}=require('./oauth-token-lifecycle-playwright-checks');
const {failureMark,consumeExpectedFailure,waitUntil}=require('./lib/concurrency-support');
const q=h.sqlString;
const MODES=['scoped','legacy-restricted','legacy-full'];
const fields=text=>Object.fromEntries(new URLSearchParams(text.trim()));

/** No request URL, credential or verifier is reflected in the receiver's output. */
function callbackReceiver() {
  const requests=[],errors=[];let opened=false,closed=false,closing,opening;
  const server=http.createServer((request,response)=>{
    try {
      const url=new URL(request.url,'http://127.0.0.1');
      if(url.pathname==='/favicon.ico'){response.writeHead(204);response.end();return;}
      requests.push({method:request.method,url});
      response.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store',
        'Referrer-Policy':'no-referrer','Content-Security-Policy':"default-src 'none'"});
      response.end('<!doctype html><title>Owned OAuth callback</title><p>Callback received</p>');
    }catch(error){errors.push(error);response.writeHead(400);response.end('Invalid callback request');}
  });
  server.on('error',error=>errors.push(error));
  server.on('clientError',(error,socket)=>{errors.push(error);socket.destroy();});
  return {requests,errors,async listen(){
    h.assert(!opening&&!opened&&!closed&&!closing,'Callback receiver cannot be reopened');
    opening=new Promise((resolve,reject)=>{
      const failed=error=>reject(error);server.once('error',failed);
      server.listen(0,'127.0.0.1',()=>{server.off('error',failed);opened=true;resolve();});
    });
    await opening;
    const address=server.address();h.assert(address&&typeof address==='object'&&address.port>0,'Callback receiver did not bind an isolated loopback port');
    return `http://127.0.0.1:${address.port}`;
  },async close(){
    if(closed)return;
    if(closing)return closing;
    closing=(async()=>{
      // A cancellation can arrive while the port is still being acquired.
      // Wait for that outcome before deciding whether a server needs closing.
      if(opening)await opening.catch(()=>{});
      if(opened) {
        const stopped=new Promise((resolve,reject)=>server.close(error=>error?reject(error):resolve()));
        // Stop accepting first, then retire existing/idle connections so no new
        // connection can appear between connection draining and listener close.
        server.closeAllConnections();
        await stopped;
      }
      h.assert(errors.length===0,'Owned OAuth callback receiver reported an error');closed=true;
    })();
    return closing;
  }};
}

/** Native consent, real redirect/exchange, precise denial and callback boundaries. */
async function callbackWorkflow(s,credentials) {
  const {sql,marker,patient,provider,config}=s;
  const mode=process.env.EXPECT_OAUTH_MODE||'legacy-restricted';
  h.assert(MODES.includes(mode),'Unknown configured OAuth mode');
  const receiver=callbackReceiver();s.cleanup(()=>receiver.close());
  const origin=await receiver.listen();
  const registered=origin+'/callback?fixture='+marker;
  const client={name:marker+' web callback',key:crypto.randomBytes(16).toString('hex'),secret:crypto.randomBytes(16).toString('hex')};
  credentials.add(client.key);credentials.add(client.secret);
  let clientNamespaceClaimed=false;
  s.cleanup(()=>{if(clientNamespaceClaimed)return cleanupClients(sql,[client]);});
  h.assert(sql.value(`SELECT COUNT(*) FROM ServiceClient WHERE clientKey=${q(client.key)} OR name=${q(client.name)}`)==='0','OAuth callback fixture namespace collision');
  clientNamespaceClaimed=true;
  client.id=sql.value(`INSERT INTO ServiceClient(name,clientKey,clientSecret,uri,lifetime)
    VALUES(${q(client.name)},${q(client.key)},${q(client.secret)},${q(registered)},3600); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(client.id),'Owned registered-callback client was not created');
  const anonymous=await h.newContext(s.context.browser(),config),api=anonymous.request;
  const app=route=>h.appUrl(config.baseUrl,route);
  const sign=(method,url,token,extra)=>oauthHeader({method,url,consumerKey:client.key,consumerSecret:client.secret,
    token:token?.oauth_token,tokenSecret:token?.oauth_token_secret,extra});
  const initiate=app('/ws/oauth/initiate?scope=demographic.read%20provider.read');
  const accessEndpoint=app('/ws/oauth/token');
  const counts=()=>sql.rows(`SELECT (SELECT COUNT(*) FROM ServiceRequestToken WHERE clientId=${client.id}),
    (SELECT COUNT(*) FROM ServiceAccessToken WHERE clientId=${client.id})`);
  const clinical=()=>sql.rows(`SELECT * FROM demographic WHERE demographic_no=${patient}`);
  const clinicalBefore=clinical();
  const defects=[];
  const finding=message=>{defects.push(message);console.log('  FAIL OAuth web callback: '+message);};
  async function requestToken(callback,{collectRefusal=false,label='registered callback'}={}) {
    const before=counts();
    const response=await api.post(initiate,{headers:{Authorization:sign('POST',initiate,null,{oauth_callback:callback})},maxRedirects:0});
    const status=response.status(),body=(await response.text()).trim();await response.dispose();
    const reason='HTTP '+status+' '+(/^[a-z_ ]{1,80}$/.test(body)?body:'non-protocol response');
    if(status!==200&&collectRefusal) {
      assert.deepEqual(counts(),before,'Refused callback initiation nevertheless issued tokens');
      h.assert(!/oauth_token(?:_secret)?=|oauth_verifier=/.test(body),'Failed callback initiation exposed credentials');
      finding(label+' initiation: '+reason+'; authorization/exchange stages unreached');return null;
    }
    h.assert(status===200,'Registered web callback initiation was refused: '+reason);
    const request=fields(body);
    for(const value of Object.values(request))if(value&&value!=='true')credentials.add(value);
    h.assert(request.oauth_token&&request.oauth_token_secret&&request.oauth_callback_confirmed==='true','Web callback request-token contract is incomplete');
    const stored=sql.rows(`SELECT callback,verifier,providerNo,scopes FROM ServiceRequestToken WHERE clientId=${client.id} AND tokenId=${q(request.oauth_token)}`);
    assert.deepEqual(stored,[[callback,null,null,'demographic.read provider.read']],'Registered callback/scopes or unapproved request attribution changed');
    return request;
  }
  async function consent(request) {
    const page=await s.context.newPage();
    await page.goto(app('/ws/oauth/authorize?oauth_token='+encodeURIComponent(request.oauth_token)),{waitUntil:'load'});
    h.assert((await page.locator('body').innerText()).includes(client.name),'Native consent names another client');
    for(const scope of ['demographic.read','provider.read'])h.assert((await page.locator('#scopeForm').innerText()).includes(scope),'Native consent omitted a requested scope');
    const full=(await page.locator('body').innerText()).includes('everything your account can');
    h.assert(full===(mode==='legacy-full'),'Native consent warning disagrees with the configured OAuth access mode');
    h.assert(await page.locator('#scopeForm input[name="oauth_token"]').inputValue()===request.oauth_token,'Native consent names another request token');
    return page;
  }
  const tokens=[];
  for(const [label,callback] of [['exact',registered],['extended query',registered+'&context=owned-value'],
    ['encoded query',registered+'&context=owned%20callback%20%26%20value']]) {
    const request=await requestToken(callback,{label,collectRefusal:label==='encoded query'});
    if(!request)continue;
    await s.step(label+': native authorization redirects to the real receiver and exchanges the exact verifier',async()=>{
      const page=await consent(request);
      const before=receiver.requests.length;
      const [response]=await Promise.all([
        page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/ws/oauth/authorize')),
        page.locator('#scopeForm button[type="submit"]').click(),
      ]);
      h.assert(response.status()===303,'Native registered-callback consent did not issue its documented 303 redirect');
      const location=response.headers().location;
      h.assert(location&&!location.includes(request.oauth_token_secret)&&!location.includes(client.secret),'Callback redirect exposed a secret');
      await page.waitForURL(url=>url.origin===origin&&url.pathname==='/callback');
      await waitUntil(()=>receiver.requests.length===before+1,'actual browser OAuth callback');
      const captured=receiver.requests[before];
      h.assert(captured.method==='GET'&&captured.url.pathname==='/callback','OAuth redirect did not reach the exact receiver path through GET');
      const received=captured.url.searchParams,wanted=new URL(callback).searchParams;
      for(const [key,value] of wanted)assert.deepEqual(received.getAll(key),[value],'OAuth callback changed preexisting query data');
      assert.deepEqual([...received.keys()].sort(),[...wanted.keys(),'oauth_token','oauth_verifier'].sort(),'Callback carried unexpected credentials or query fields');
      h.assert(received.get('oauth_token')===request.oauth_token&&received.get('oauth_verifier'),'Callback has the wrong request token or no verifier');
      const verifier=received.get('oauth_verifier');
      credentials.add(verifier);
      const exchanged=await api.post(accessEndpoint,{headers:{Authorization:sign('POST',accessEndpoint,request,{oauth_verifier:verifier})},maxRedirects:0});
      h.assert(exchanged.status()===200,'Callback verifier could not be exchanged');
      const token=fields(await exchanged.text());await exchanged.dispose();
      for(const value of Object.values(token))if(value)credentials.add(value);
      h.assert(token.oauth_token&&token.oauth_token_secret,'Callback exchange returned no usable access token');tokens.push(token);
      assert.deepEqual(sql.rows(`SELECT providerNo,scopes FROM ServiceAccessToken WHERE clientId=${client.id} AND tokenId=${q(token.oauth_token)}`),
        [[provider,'demographic.read provider.read']],'Callback access token has the wrong provider or scopes');
      h.assert(sql.value(`SELECT COUNT(*) FROM ServiceRequestToken WHERE tokenId=${q(request.oauth_token)}`)==='0','Successful callback exchange did not consume its request token');
      const infoUrl=app('/ws/services/oauth/info');
      const info=await api.get(infoUrl,{headers:{Accept:'application/json',Authorization:sign('GET',infoUrl,token)}});
      h.assert(info.status()===200&&String((await info.json()).login)===provider,'Web-callback token identifies another provider');await info.dispose();
      const readUrl=app('/ws/services/demographics/'+patient);
      const read=await api.get(readUrl,{headers:{Accept:'application/json',Authorization:sign('GET',readUrl,token)}});
      h.assert(read.status()===200,'Web-callback token did not authorize its demographic read');
      const data=await read.json();h.assert(String(data.demographicNo)===patient&&data.lastName===marker,'Web-callback token returned another patient');await read.dispose();
      const outsideUrl=app('/ws/services/tickler/mine');
      const outside=await api.get(outsideUrl,{headers:{Accept:'application/json',Authorization:sign('GET',outsideUrl,token)}});
      if(mode==='legacy-full')h.assert(outside.status()===200,'Configured full legacy token could not reach the allowed service');
      else h.assert(outside.status()===403&&(await outside.text()).trim()===(mode==='scoped'?'insufficient_scope':'restricted_endpoint'),'Web-callback token exceeded its configured access mode');
      await outside.dispose();await page.close();
    });
  }
  await s.step('native Deny returns authorization_denied with no callback, verifier or access token',async()=>{
    const request=await requestToken(registered),page=await consent(request),before=counts(),received=receiver.requests.length;
    const mark=failureMark(s.recorder);
    const [response]=await Promise.all([
      page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/ws/oauth/authorize')),
      page.locator('#scopeForm button[onclick="deny();"]').click(),
    ]);
    h.assert(response.status()===403&&(await response.text()).trim()==='authorization_denied','Native Deny did not produce its explicit protocol refusal');
    h.assert((await page.locator('body').innerText()).trim()==='authorization_denied','Native denial is not visible to the authorizing user');
    h.assert(!response.headers().location&&receiver.requests.length===received,'Native Deny redirected credentials to a callback');
    assert.deepEqual(counts(),before,'Native Deny issued or removed unrelated owned tokens');
    assert.deepEqual(sql.rows(`SELECT verifier,providerNo FROM ServiceRequestToken WHERE clientId=${client.id} AND tokenId=${q(request.oauth_token)}`),[[null,null]],'Denied request acquired approval or provider attribution');
    consumeExpectedFailure(s.recorder,mark,{status:403,method:'POST',path:/\/ws\/oauth\/authorize$/});
    // Deny leaves this request pending, not permanently revoked. It has no
    // verifier; only a subsequent explicit authorization can make it usable.
    const exchanged=await api.post(accessEndpoint,{headers:{Authorization:sign('POST',accessEndpoint,request,{oauth_verifier:'owned-unapproved-verifier'})},maxRedirects:0});
    h.assert(exchanged.status()===401&&(await exchanged.text()).trim()==='invalid_verifier','Denied attempt could mint an access token');await exchanged.dispose();
    assert.deepEqual(counts(),before,'Refused denied-token exchange changed token state');await page.close();
  });
  for(const [label,callback] of [['different host',registered.replace('127.0.0.1','127.0.0.2')],
    ['unsafe scheme',registered.replace('http:','ftp:')],['lookalike path',registered.replace('/callback?','/callback-evil?')]]) {
      const before=counts(),received=receiver.requests.length;
      const response=await api.post(initiate,{headers:{Authorization:sign('POST',initiate,null,{oauth_callback:callback})},maxRedirects:0});
      const status=response.status(),text=(await response.text()).trim();
      const redirected=Boolean(response.headers().location);await response.dispose();
      const isolated=()=>{
        h.assert(!redirected&&!/oauth_token|oauth_verifier|oauth_token_secret/.test(text),'Refused callback exposed credentials or redirected');
        assert.deepEqual(counts(),before,'Refused callback issued tokens');
        h.assert(receiver.requests.length===received,'Refused callback reached the receiver');
      };
      if(status!==400||text!==(callback.startsWith('ftp:')?'invalid_callback_scheme':'callback_uri not allowed')) {
        isolated();
        finding(label+' callback: expected application HTTP 400 protocol refusal, received HTTP '+status);continue;
      }
      await s.step(label+': application refuses the callback without credentials, issued tokens or receiver navigation',async()=>isolated());
  }
  await s.step('callback token accounting, receiver resources and clinical state remain isolated',async()=>{
    assert.deepEqual(clinical(),clinicalBefore,'OAuth callback workflows changed clinical records');
    h.assert(receiver.requests.length===tokens.length&&tokens.length>=2&&receiver.errors.length===0,'Callback receiver saw an extra request, missing positive case or an error');
    h.assert(sql.value(`SELECT COUNT(*) FROM ServiceAccessToken WHERE clientId=${client.id}`)===String(tokens.length),'Callback workflow issued an unintended access token');
    for(const token of tokens) {
      const url=app('/ws/services/oauth/info');
      const response=await api.get(url,{headers:{Accept:'application/json',Authorization:sign('GET',url,token)}});
      h.assert(response.status()===200&&String((await response.json()).login)===provider,'Denied or invalid callbacks altered a prior authorized token');await response.dispose();
    }
  });
  h.assert(defects.length===0,'OAuth web callback failures: '+defects.join('; '));
}
/** Preserve failure attribution while removing query strings and known credentials. */
function redactOAuthError(error,credentials) {
  const scrub=text=>{
    let clean=h.withoutQueryStrings(String(text));
    for(const secret of [...credentials].filter(value=>typeof value==='string'&&value).sort((a,b)=>b.length-a.length))clean=clean.replaceAll(secret,'[redacted OAuth credential]');
    return clean;
  };
  if(!error||typeof error!=='object')return new Error(scrub(error));
  if(typeof error.message==='string')error.message=scrub(error.message);
  if(typeof error.stack==='string')error.stack=scrub(error.stack);
  return error;
}
async function workflow(s) {
  const credentials=new Set();
  try{await callbackWorkflow(s,credentials);}catch(error){throw redactOAuthError(error,credentials);}
}
if(require.main===module)runWorkflow('oauth-web-callback',workflow,{openMaster:false,preflightConfig:()=>{
  if(!MODES.includes(process.env.EXPECT_OAUTH_MODE||'legacy-restricted'))throw new h.SkipCheck('Declare EXPECT_OAUTH_MODE as scoped, legacy-restricted or legacy-full');
}});
module.exports={callbackReceiver,redactOAuthError,workflow};
