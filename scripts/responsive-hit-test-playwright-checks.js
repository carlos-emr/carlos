#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {eachUiQualitySurface}=require('./lib/ui-quality-surfaces');
async function workflow(s) {
  for(const viewport of [{width:1280,height:720},{width:1366,height:768}]) {
    await eachUiQualitySurface(s,async(label,page,selector)=>s.step(`${label} controls receive pointer hits at ${viewport.width}×${viewport.height}`,async()=>{
      await page.setViewportSize(viewport);const controls=page.locator(selector).filter({visible:true});
      const count=await controls.count();h.assert(count>0,`${label} has no primary controls to hit-test`);
      for(let index=0;index<Math.min(count,12);index++) {
        const control=controls.nth(index);await control.scrollIntoViewIfNeeded();
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
