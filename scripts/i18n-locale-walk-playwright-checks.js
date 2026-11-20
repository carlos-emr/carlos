#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness');
const {providerNavigationFixture}=require('./lib/provider-navigation-fixture');
const {catalogueLinks,dedupe}=require('./lib/playwright-link-audit');
const {NEVER_OPEN}=require('./lib/playwright-surfaces');
const {assertHealthy,crawl,startSession}=require('./lib/page-health-engine');
async function main({cancellation}={}) {
  const config=h.readConfig(),sql=h.createSqlRunner(config.mysql);
  let navigation;
  try {
  navigation=providerNavigationFixture(sql,config.testUser,'i18n-locale-walk');
  for(const locale of ['fr','es','pl','pt-BR']) {
    const session=await startSession(config,{locale});
    try {
      h.assert(await session.schedulePage.evaluate(()=>navigator.language)===locale,'Browser locale differs from the requested walk');
      const missing=new Set(),add=session.ledger.add.bind(session.ledger);
      session.ledger.add=(page,kind,detail,why)=>{
        if(kind==='missing-resource-key')missing.add(detail);
        else add(page,kind,detail,why);
      };
      const items=dedupe(await catalogueLinks(session.schedulePage,{identity:true})).filter(item=>
        item.identity.ancestorIds.some(id=>['navlist','userSettingsMenu','dateAndCalendar'].includes(id)));
      h.assert(items.length>=10,`${locale}: schedule navigation inventory is incomplete`);
      const result=await crawl({context:session.context,hostPage:session.schedulePage,items,recorder:session.recorder,
        probe:session.probe,ledger:session.ledger,labelPrefix:locale,timeout:20000,skipRules:NEVER_OPEN});
      cancellation?.throwIfCancelled();
      console.log(JSON.stringify({locale,opened:result.opened.length,missingResourceKeys:[...missing].sort()}));
      assertHealthy(result,{surface:`${locale} schedule navigation`,minimumOpened:10});
    } finally {await session.close();}
  }
  } finally {try{if(navigation)navigation.cleanup();}finally{sql.dispose();}}
}
if(require.main===module)h.runCheck({name:'i18n-locale-walk',run:main});
module.exports={main};
