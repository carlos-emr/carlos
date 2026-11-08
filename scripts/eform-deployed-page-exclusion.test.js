/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {verify}=require('./eform-deployed-page-exclusion-playwright-checks');

// A handwritten recipient view, independent of the HTML fixture/producer. Each
// mutation represents output a broken deployed renderer must never certify.
function recipientView() {
  const pages=[1,2].map(n=>({width:540,height:705,words:[
    {text:`P${n}TOP`,xMin:15,yMin:16,xMax:45,yMax:22},
    {text:`P${n}RIGHT`,xMin:472.5,yMin:16,xMax:510,yMax:22},
    {text:`P${n}LOWER`,xMin:15,yMin:676,xMax:50,yMax:682},
    {text:`P${n}CORNER`,xMin:472.5,yMin:676,xMax:515,yMax:682},
    {text:`P${n}CLINICAL`,xMin:187.5,yMin:338.5,xMax:235,yMax:344.5},
  ]}));
  const images=pages.map(page=>{
    const pixels=Buffer.alloc(540*705,255);
    for(const word of page.words)for(let x=Math.ceil(word.xMin);x<Math.floor(word.xMax);x++)pixels[Math.ceil(word.yMin)*540+x]=0;
    return {width:540,height:705,pixels};
  });
  return {pages,images};
}
test('complete two-page recipient output passes the independent text/geometry/ink oracle',()=>{
  const {pages,images}=recipientView();assert.equal(verify(pages,images).length,10);
});
for(const [name,mutate] of [
  ['one entire clinical page suppressed',({pages,images})=>{pages.pop();images.pop();}],
  ['extra blank page',({pages,images})=>{pages.push({width:540,height:705,words:[]});images.push(images[0]);}],
  ['clinical pages reversed',({pages})=>pages.reverse()],
  ['lower-edge content missing',({pages})=>pages[1].words.splice(2,1)],
  ['duplicated clinical token replacing a corner',({pages})=>{pages[1].words[3]={...pages[1].words[0]};}],
  ['interstitial printed',({pages})=>pages[0].words.push({text:'NONPAGEINTERSTITIAL',xMin:10,yMin:100,xMax:100,yMax:110})],
  ['page shifted by interstitial height',({pages})=>{pages[1].words[0].yMin+=67.5;}],
  ['right edge clipped',({pages})=>{pages[1].words[3].xMax=541;}],
  ['incorrect paper scale',({pages})=>{pages[1].width=612;}],
  ['invisible text despite extraction',({images})=>images[1].pixels.fill(255)],
])test('reject renderer output with '+name,()=>{
  const view=recipientView();mutate(view);assert.throws(()=>verify(view.pages,view.images));
});
