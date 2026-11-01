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
      let previous='',since=Date.now(),ready=false;
      const deadline=Date.now()+15000;
      while(Date.now()<deadline) {
        const signature=JSON.stringify(await page.locator(selector).filter({visible:true}).evaluateAll(nodes=>nodes.map(e=>{
          const r=e.getBoundingClientRect();return [e.id,e.name,e.tagName,r.x,r.y,r.width,r.height];
        })));
        if(signature!==previous){previous=signature;since=Date.now();}
        else if(signature!=='[]'&&Date.now()-since>=750){ready=true;break;}
        await new Promise(resolve=>setTimeout(resolve,100));
      }
      h.assert(ready,`${label} controls did not reach a stable visible layout`);
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
