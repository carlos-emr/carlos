/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {verify,producerOf,rendererConfirmed}=require('./eform-deployed-page-exclusion-playwright-checks');

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
for(const [name,mutate,pattern] of [
  ['one entire clinical page suppressed',({pages,images})=>{pages.pop();images.pop();},/exactly two pages/],
  ['extra blank page',({pages,images})=>{pages.push({width:540,height:705,words:[]});images.push(images[0]);},/exactly two pages/],
  ['clinical pages reversed',({pages})=>pages.reverse(),/wrong-page eForm clinical content/],
  ['lower-edge content missing',({pages})=>pages[1].words.splice(2,1),/extra or missing content/],
  ['duplicated clinical token replacing a corner',({pages})=>{pages[1].words[3]={...pages[1].words[0]};},/Missing, duplicated or wrong-page/],
  ['interstitial printed',({pages})=>pages[0].words.push({text:'NONPAGEINTERSTITIAL',xMin:10,yMin:100,xMax:100,yMax:110}),/extra or missing content/],
  ['page shifted by interstitial height',({pages})=>{pages[1].words[0].yMin+=67.5;},/Shifted eForm clinical content/],
  ['right edge clipped',({pages})=>{pages[1].words[3].xMax=541;},/Clipped eForm clinical content/],
  ['incorrect paper scale',({pages})=>{pages[1].width=612;},/changed authored paper dimensions/],
  ['invisible text despite extraction',({images})=>images[1].pixels.fill(255),/Invisible eForm clinical content/],
])test('reject renderer output with '+name,()=>{
  const view=recipientView();mutate(view);assert.throws(()=>verify(view.pages,view.images),pattern);
});

// Renderer proof. A default install logs at WARN, so the INFO receipts are absent rather than negative;
// the PDF's own Producer (Chromium's print pipeline) is required everywhere.
const SKIA='Skia/PDF m149';
const started=fdid=>`INFO EFormBrowserPdfService - Browser eForm renderer starting: fdid=${fdid} baseUrl=http://127.0.0.1`;
const completed=(fdid,pages)=>`INFO EFormBrowserPdfService - Browser eForm renderer completed: fdid=${fdid} pages=${pages} bytes=2048 elapsedMs=900`;
test('read the Producer line of pdfinfo output',()=>{
  assert.equal(producerOf('Title:          x\nProducer:       Skia/PDF m149\nPages:          2\n'),SKIA);
  assert.equal(producerOf('Pages:          2\n'),'');
});
test('accept a browser-rendered PDF from an install that logs at WARN',()=>{
  assert.equal(rendererConfirmed({producer:SKIA,log:'',fdid:'41'}),true);
  assert.equal(rendererConfirmed({producer:SKIA,log:started('7')+'\n'+completed('7',2),fdid:'41'}),true);
});
test('require the completion receipt once the renderer logged this instance at INFO',()=>{
  assert.equal(rendererConfirmed({producer:SKIA,log:started('41'),fdid:'41'}),false);
  assert.equal(rendererConfirmed({producer:SKIA,log:started('41')+'\n'+completed('41',2),fdid:'41'}),true);
  assert.equal(rendererConfirmed({producer:SKIA,log:started('41')+'\n'+completed('41',3),fdid:'41'}),false);
  assert.equal(rendererConfirmed({producer:SKIA,log:started('41')+'\n'+completed('41',2)+'\n'+completed('41',2),fdid:'41'}),false);
});
for(const producer of ['','OpenPDF 2.0.3','iText 2.1.7','openhtmltopdf.com','Skia/PDF','Skia/PDF m0','Skia/PDF m149 (forged)'])
  test('reject a PDF whose Producer is '+JSON.stringify(producer),()=>{
    assert.equal(rendererConfirmed({producer,log:'',fdid:'41'}),false);
  });
