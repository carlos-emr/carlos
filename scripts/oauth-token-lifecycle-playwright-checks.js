#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
// #5724: three isolated SQL client prerequisites; real OAuth handshake/consent,
// bounded expiry observations and native Administration deletion. No secret is
// logged or captured as an artifact. Existing replay/scope checks remain separate.
const crypto=require('node:crypto');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const {runWorkflow}=require('./lib/workflow-session');
const {oauthHeader}=require('./oauth-rest-surfaces-playwright-checks');
const q=h.sqlString;
const TIMEOUT=30000;
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const fields=text=>Object.fromEntries(new URLSearchParams(text.trim()));

/** Reconcile partial setup and clean each owned client independently. */
function cleanupClients(sql,clients) {
  const failures=[];
  for(const client of clients)try {
    if(!client.id) {
      const ids=sql.rows(`SELECT id FROM ServiceClient WHERE clientKey=${q(client.key)} AND name=${q(client.name)}`).flat();
      h.assert(ids.length<=1,'OAuth setup has ambiguous client ownership');
      if(!ids.length)continue;
      [client.id]=ids;
    }
    h.assert(/^[1-9]\d*$/.test(client.id),'Invalid owned OAuth client identity');
    const current=sql.rows(`SELECT clientKey,name FROM ServiceClient WHERE id=${client.id}`);
    h.assert(!current.length||(current.length===1&&current[0][0]===client.key&&current[0][1]===client.name),'OAuth client ownership changed; retain associated tokens');
    sql.execute(`DELETE FROM ServiceAccessToken WHERE clientId=${client.id}; DELETE FROM ServiceRequestToken WHERE clientId=${client.id};
      DELETE FROM ServiceOAuthNonce WHERE consumerKey=${q(client.key)}; DELETE FROM ServiceClient WHERE id=${client.id} AND clientKey=${q(client.key)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ServiceClient WHERE id=${client.id})+(SELECT COUNT(*) FROM ServiceAccessToken WHERE clientId=${client.id})
      +(SELECT COUNT(*) FROM ServiceRequestToken WHERE clientId=${client.id})+(SELECT COUNT(*) FROM ServiceOAuthNonce WHERE consumerKey=${q(client.key)})`)==='0','Owned OAuth clients, tokens or nonces remain');
  } catch(error){failures.push(error);}
  if(failures.length)throw new AggregateError(failures,'OAuth fixture cleanup failed: '+failures.map(error=>error.message).join('; '));
}

async function workflow(s) {
  const {sql,marker,patient,provider,config}=s;
  const app=route=>h.appUrl(config.baseUrl,route);
  const clients=[];
  s.cleanup(()=>cleanupClients(sql,clients));
  for(const [suffix,lifetime] of [['expiry',8],['delete',3600],['control',3600]]) {
    const c={name:`${marker}-${suffix}`,key:crypto.randomBytes(8).toString('hex'),secret:crypto.randomBytes(8).toString('hex'),lifetime};
    clients.push(c);
    h.assert(sql.value(`SELECT COUNT(*) FROM ServiceClient WHERE clientKey=${q(c.key)} OR name=${q(c.name)}`)==='0','OAuth fixture key/name collision');
    c.id=sql.value(`INSERT INTO ServiceClient(name,clientKey,clientSecret,uri,lifetime) VALUES(${q(c.name)},${q(c.key)},${q(c.secret)},'oob',${lifetime}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(c.id),'OAuth fixture was not created');
  }
  const [expiry,deleted,control]=clients;
  const anonymous=await h.newContext(s.context.browser(),config);
  const api=anonymous.request;
  const sign=(c,method,url,token,extra)=>oauthHeader({method,url,consumerKey:c.key,consumerSecret:c.secret,
    token:token?.oauth_token,tokenSecret:token?.oauth_token_secret,extra});
  const clinical=()=>JSON.stringify({patient:sql.rows(`SELECT * FROM demographic WHERE demographic_no=${patient}`),
    ext:sql.rows(`SELECT * FROM demographicExt WHERE demographic_no=${patient} ORDER BY id`),
    children:sql.value(`SELECT (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})+(SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      +(SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`)});
  const beforeClinical=clinical();
  async function authorize(c) {
    const initiate=app('/ws/oauth/initiate?scope=demographic.read%20provider.read');
    let response=await api.post(initiate,{headers:{Authorization:sign(c,'POST',initiate,null,{oauth_callback:'oob'})}});
    h.assert(response.status()===200,'OAuth request-token initiation failed');
    const request=fields(await response.text());
    h.assert(request.oauth_token&&request.oauth_token_secret&&request.oauth_callback_confirmed==='true','OAuth request-token contract is incomplete');
    const page=await s.context.newPage();
    await page.goto(app(`/ws/oauth/authorize?oauth_token=${encodeURIComponent(request.oauth_token)}`),{waitUntil:'load'});
    h.assert((await page.locator('body').innerText()).includes(c.name),'OAuth consent names another client');
    const [consent]=await Promise.all([
      page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/ws/oauth/authorize')),
      page.locator('#scopeForm button[type="submit"]').click(),
    ]);
    h.assert(consent.status()===200,'Native OAuth consent was refused');
    const verifier=fields(await consent.text()).oauth_verifier;
    h.assert(verifier,'Native consent returned no verifier');await page.close();
    const endpoint=app('/ws/oauth/token');
    response=await api.post(endpoint,{headers:{Authorization:sign(c,'POST',endpoint,request,{oauth_verifier:verifier})}});
    h.assert(response.status()===200,'OAuth access-token exchange failed');
    const token=fields(await response.text());h.assert(token.oauth_token&&token.oauth_token_secret,'OAuth access-token contract is incomplete');
    const rows=sql.rows(`SELECT issued,lifetime,providerNo FROM ServiceAccessToken WHERE clientId=${c.id} AND tokenId=${q(token.oauth_token)}`);
    h.assert(rows.length===1&&rows[0][1]===String(c.lifetime)&&rows[0][2]===provider,'Issued access token has incorrect lifetime or provider attribution');
    token.issued=Number(rows[0][0]);h.assert(Number.isSafeInteger(token.issued)&&token.issued>0,'Token issuance time is invalid');
    return token;
  }
  const dataUrl=app(`/ws/services/demographics/${patient}`);
  const read=(c,token)=>api.get(dataUrl,{headers:{Accept:'application/json',Authorization:sign(c,'GET',dataUrl,token)},maxRedirects:0});
  async function positive(c,token) {
    const response=await read(c,token);
    h.assert(response.status()===200&&/^application\/json/i.test(response.headers()['content-type']||''),'Active OAuth token did not return typed JSON');
    const data=await response.json();h.assert(String(data.demographicNo)===patient&&data.lastName===marker,'OAuth read returned another patient');
  }
  async function refused(response,reasons,label) {
    h.assert(response.status()===401,`${label}: expected application authentication refusal, got HTTP ${response.status()}`);
    const text=(await response.text()).trim();
    h.assert(reasons.includes(text),`${label}: refusal did not carry the expected OAuth authentication reason`);
    h.assert(!text.includes(marker)&&!text.includes('demographicNo'),`${label}: refused credentials returned patient data`);
  }
  async function ordinarySession() {
    const response=await s.context.request.get(app(`/ws/rs/demographics/${patient}`),{headers:{Accept:'application/json'}});
    h.assert(response.status()===200&&String((await response.json()).demographicNo)===patient,'OAuth lifecycle altered browser-session access');
  }
  let controlToken,deleteToken;
  await s.step('independent active tokens name the correct patient and authorizing provider',async()=>{
    controlToken=await authorize(control);deleteToken=await authorize(deleted);
    await positive(control,controlToken);await positive(deleted,deleteToken);await ordinarySession();
  });
  await s.step('finite access is valid at its persisted epoch-second boundary and refused immediately afterward',async()=>{
    const now=()=>Number(sql.value('SELECT FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6)))'));
    let contained=false;
    for(let attempt=0;attempt<3&&!contained;attempt++) {
      const token=await authorize(expiry);await positive(expiry,token);
      const boundary=token.issued+expiry.lifetime,deadline=Date.now()+15000;
      while(now()<boundary&&Date.now()<deadline)await pause(50);
      const lower=now();
      if(lower===boundary) {
        // Authentication/data failures always fail. Only a successful request
        // whose observation straddles the second may retry with a fresh token.
        await positive(expiry,token);contained=now()===boundary;
      }
      h.assert(lower>=boundary,'Timed out before the token expiry boundary');
      while(now()<=boundary&&Date.now()<deadline)await pause(50);
      h.assert(now()>boundary&&now()<=boundary+3,'Expiry observation missed its bounded post-boundary window');
      await refused(await read(expiry,token),['invalid_signature','unknown_provider'],'Expired access token');
      if(!contained)console.log('  Timing miss: retrying an exact-second observation with a fresh native-authorized token.');
    }
    h.assert(contained,'Boundary request missed the exact expiry second in all three bounded attempts');
    await positive(control,controlToken);await ordinarySession();
    h.assert(clinical()===beforeClinical,'OAuth expiry changed clinical records');
  });
  await s.step('native client deletion revokes its existing token and new handshakes while controls continue',async()=>{
    const {page:admin}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('#admin-panel,#admin2').first(),
      {context:s.context,recorder:s.recorder,label:'oauth-client-administration',timeout:TIMEOUT});
    await admin.waitForLoadState('load');
    const link=admin.getByRole('link',{name:'REST Clients',exact:true,includeHidden:true});await revealAuditLink(admin,link,TIMEOUT);await link.click();
    const element=admin.locator('#dynamic-content iframe#myFrame[src*="/admin/ViewApiClients"]');await element.waitFor();
    const frame=await (await element.elementHandle()).contentFrame();h.assert(frame,'REST Clients frame is missing');
    const row=frame.locator('#clientTable tbody tr',{hasText:deleted.name});await row.waitFor();
    const controlsBefore=JSON.stringify(sql.rows(`SELECT id,name,clientKey,uri,lifetime FROM ServiceClient WHERE id IN (${expiry.id},${control.id}) ORDER BY id`));
    await positive(deleted,deleteToken);
    const [response]=await Promise.all([
      admin.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/admin/api/clientManage')
        &&new URLSearchParams(r.request().postData()||'').get('method')==='delete'),
      row.getByRole('img',{name:'Delete'}).click(),
    ]);
    h.assert(response.status()===200&&(await response.json()).success===true,'Native client deletion did not succeed');
    await row.waitFor({state:'detached'});h.assert(sql.value(`SELECT COUNT(*) FROM ServiceClient WHERE id=${deleted.id}`)==='0','Deleted client persists');
    await refused(await read(deleted,deleteToken),['invalid_consumer'],'Deleted-client access token');
    const initiate=app('/ws/oauth/initiate?scope=demographic.read');
    await refused(await api.post(initiate,{headers:{Authorization:sign(deleted,'POST',initiate,null,{oauth_callback:'oob'})}}),['invalid_consumer'],'Deleted-client initiation');
    await positive(control,controlToken);await ordinarySession();
    h.assert(JSON.stringify(sql.rows(`SELECT id,name,clientKey,uri,lifetime FROM ServiceClient WHERE id IN (${expiry.id},${control.id}) ORDER BY id`))===controlsBefore,'Deleting one client changed another client');
    h.assert(clinical()===beforeClinical,'OAuth revocation changed clinical records');await admin.close();
  });
}
if(require.main===module)runWorkflow('oauth-token-lifecycle',workflow,{openMaster:false});
module.exports={workflow,cleanupClients};
