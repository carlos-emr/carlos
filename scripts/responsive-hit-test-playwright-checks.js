#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {eachUiQualitySurface}=require('./lib/ui-quality-surfaces');
async function workflow(s) {
  for(const viewport of [{width:1280,height:720},{width:1366,height:768}]) {
    await eachUiQualitySurface(s,async(label,page,selector)=>s.step(`${label} controls receive pointer hits at ${viewport.width}×${viewport.height}`,async()=>{
      await page.setViewportSize(viewport);
      await page.waitForLoadState('networkidle');
      // Resize handlers and widget replacement can run after network idle.
      await page.waitForFunction(selector=>{
        const visible=[...document.querySelectorAll(selector)].filter(e=>e.getClientRects().length&&getComputedStyle(e).visibility!=='hidden');
        const signature=visible.map(e=>{const r=e.getBoundingClientRect();return [e.id,e.name,e.tagName,r.x,r.y,r.width,r.height].join(':');}).join('|');
        const previous=window.__carlosHitReadiness;
        if(!previous||previous.signature!==signature){window.__carlosHitReadiness={signature,since:performance.now()};return false;}
        return visible.length>0&&performance.now()-previous.since>=750;
      },selector,{timeout:15000,polling:100});
      // Native widgets hide/replace their original controls during initialization.
      // Freeze the visible identities after readiness instead of re-indexing a live list.
      const controls=await page.locator(selector).filter({visible:true}).elementHandles();
      h.assert(controls.length>0,`${label} has no primary controls to hit-test`);
      for(const [index,control] of controls.slice(0,12).entries()) {
        h.assert(await control.isVisible(),`${label} primary control ${index+1} became hidden after readiness`);
        await control.scrollIntoViewIfNeeded();
        const hit=await control.evaluate(element=>{
          const box=element.getBoundingClientRect(),x=box.x+box.width/2,y=box.y+box.height/2;
          const target=document.elementFromPoint(x,y);
          return {inside:x>=0&&x<innerWidth&&y>=0&&y<innerHeight,receives:target===element||element.contains(target)};
        });
        h.assert(hit.inside&&hit.receives,`${label} primary control ${index+1} is clipped or covered`);
      }
    }));
  }
}
if(require.main===module)runWorkflow('responsive-hit-test',workflow,{openPatient:true});
module.exports={workflow};
