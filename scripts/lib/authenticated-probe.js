/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

function cookiesForUrl(browserCookies, destination, now = Date.now() / 1000) {
  return browserCookies.filter(cookie => {
    const domain = cookie.domain.toLowerCase(),host=destination.hostname.toLowerCase();
    const domainMatches = domain.startsWith('.') ? host === domain.slice(1) || host.endsWith(domain) : host === domain;
    const path = cookie.path || '/';
    const pathMatches = destination.pathname === path || (destination.pathname.startsWith(path)
      && (path.endsWith('/') || destination.pathname[path.length] === '/'));
    return domainMatches && pathMatches && (cookie.expires === -1 || cookie.expires > now);
  }).sort((a,b)=>(b.path||'/').length-(a.path||'/').length);
}

/**
 * Playwright's APIRequestContext omits Secure cookies on HTTP loopback even when
 * Chromium sends them. Replay only this application's cookies explicitly, with
 * redirects disabled so a refusal probe cannot leak them to another origin.
 */
async function prepareRead(session, target, options, requireSessionCookie) {
  const {allowSecurityErrorRedirect = false, ...requestOptions} = options;
  const base = session.config.baseUrl;
  const url = new URL(target, base);
  if (url.origin !== base.origin || url.username || url.password) {
    throw new Error('Authenticated probe must stay on the application origin');
  }
  const host = url.hostname.replace(/^\[|\]$/g, '').toLowerCase();
  const loopback = ['localhost', '127.0.0.1', '::1'].includes(host);
  if (!['http:', 'https:'].includes(url.protocol) || (url.protocol === 'http:' && !loopback)) {
    throw new Error('Authenticated probes require HTTPS outside loopback');
  }
  if (!['GET', 'HEAD'].includes(options.method || 'GET')) {
    throw new Error('Authenticated refusal probes support only GET and HEAD');
  }
  const now = Date.now() / 1000;
  const browserCookies=await session.context.cookies();
  const cookieHeader=destination=>{
    const cookies=cookiesForUrl(browserCookies, destination, now);
    if(requireSessionCookie&&!cookies.some(cookie=>cookie.name==='JSESSIONID'&&cookie.value))throw new Error('Authenticated probe requires an application session cookie');
    return cookies.map(cookie=>`${cookie.name}=${cookie.value}`).join('; ');
  };
  const targetCookie=cookieHeader(url);
  return {base,url,cookieHeader,allowSecurityErrorRedirect,requestOptions,
    headers:{...Object.fromEntries(Object.entries(options.headers||{}).filter(([name])=>name.toLowerCase()!=='cookie')),
      ...(targetCookie?{Cookie:targetCookie}:{})}};
}

/** Authentication protocol assertions may deliberately precede login or facility selection. */
async function authenticationStateProbe(session,target,options={}) {
  const {url,headers,requestOptions}=await prepareRead(session,target,options,false);
  return session.context.request.fetch(url.href,{...requestOptions,headers,maxRedirects:0,failOnStatusCode:false});
}

async function authenticatedProbe(session,target,options={}) {
  const {base,url,cookieHeader,allowSecurityErrorRedirect,requestOptions,headers}=await prepareRead(session,target,options,true);
  // A retained cookie is not proof of a live server-side session. The method
  // guard can answer 405 even after logout or a service restart. Verify the
  // actual session immediately before EACH probe using this same transport.
  const heartbeat=new URL(base.pathname.replace(/\/$/,'')+'/status/SessionHeartbeat',base);
  const proof=await session.context.request.fetch(heartbeat.href,{
    method:'GET',headers:{Accept:'application/json',Cookie:cookieHeader(heartbeat)},maxRedirects:0,failOnStatusCode:false,
    ...(requestOptions.timeout===undefined?{}:{timeout:requestOptions.timeout}),
  });
  try {
    if(proof.status()!==200||(await proof.json()).valid!==true)throw new Error('Authenticated probe session is invalid; refusal evidence is inconclusive');
  } finally {await proof.dispose();}
  const response = await session.context.request.fetch(url.href, {
    ...requestOptions,
    headers,
    maxRedirects: 0,
    failOnStatusCode: false,
  });
  // Even no-write/include assertions must not accept a logged-out redirect.
  const status = response.status();
  if (status >= 300 && status < 400) {
    // A few least-privilege preconditions deliberately inspect the application's
    // security-error redirects. Accept only the exact local routes when requested;
    // a login redirect must still fail even in those callers.
    const location = response.headers?.().location;
    if (allowSecurityErrorRedirect && location) {
      const destination = new URL(location, url);
      if (destination.origin === base.origin && !destination.username && !destination.password
          && ['/securityError','/noRights.html'].some(path=>destination.pathname===base.pathname.replace(/\/$/,'')+path)) return response;
    }
    throw new Error(`Authenticated probe redirected for ${options.method || 'GET'} ${url.pathname} `
      + `(HTTP ${status}); session/refusal evidence is inconclusive`);
  }
  return response;
}

module.exports = { authenticatedProbe, authenticationStateProbe, cookiesForUrl };
