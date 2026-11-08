/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),{PNG}=require('pngjs');
const pdf=require('./lib/incoming-mixed-pdf');
const {verifyPreview}=require('./incoming-mixed-pdf-playwright-checks');
function raster(width,height,colour=[255,255,255]) {
  const image=new PNG({width,height});for(let i=0;i<image.data.length;i+=4){for(let c=0;c<3;c++)image.data[i+c]=colour[c];image.data[i+3]=255;}return image;
}
test('independent clockwise rotations preserve every asymmetric raster pixel',()=>{
  const image=raster(2,3);for(let i=0;i<6;i++)image.data[i*4]=i;
  const reds=png=>Array.from({length:png.width*png.height},(_,i)=>png.data[i*4]);
  const cw=pdf.rotateRaster(image,1);assert.equal(cw.width,3);assert.equal(cw.height,2);assert.deepEqual(reds(cw),[4,2,0,5,3,1]);
  assert.deepEqual(reds(pdf.rotateRaster(image,2)),[5,4,3,2,1,0]);
  assert.deepEqual(reds(pdf.rotateRaster(image,3)),[1,3,5,0,2,4]);
  assert.deepEqual(reds(pdf.rotateRaster(image,4)),[0,1,2,3,4,5]);
});
test('raster oracle rejects wrong geometry and visible dropped/changed content',()=>{
  const expected=raster(20,20),actual=raster(20,20);
  assert.throws(()=>pdf.compareRaster(raster(10,40),expected,'geometry'),/dimensions changed/);
  for(let i=0;i<3;i++)actual.data[i*4]=0;
  assert.throws(()=>pdf.compareRaster(actual,expected,'omission'),/content\/orientation differs/);
  assert.doesNotThrow(()=>pdf.compareRaster(raster(20,20,[249,249,249]),expected,'antialias'));
  const transparent=raster(20,20);for(let i=3;i<transparent.data.length;i+=4)transparent.data[i]=0;
  assert.throws(()=>pdf.compareRaster(transparent,expected,'transparent'),/content\/orientation/);
});
test('page oracle rejects omission, reordered identity, rotation, crops, text and annotation loss',()=>{
  const baseline=pdf.PAGES.map((page,i)=>({...page,text:i===2?'Mixed':'',urls:i===1?['https://example.invalid/owned']:[],raster:raster(20,30,[50+i*60,0,0])}));
  const expected=[{id:1},{id:2},{id:3}];assert.doesNotThrow(()=>pdf.verifyPages(baseline,expected,baseline));
  assert.throws(()=>pdf.verifyPages(baseline.slice(1),expected,baseline),/missing\/extra/);
  assert.throws(()=>pdf.verifyPages([baseline[2],baseline[1],baseline[0]],expected,baseline),/rotation|content/);
  for(const patch of [{rotate:90},{crop:[0,0,348,462]},{text:'changed'},{urls:['https://example.invalid/other']}])
    assert.throws(()=>pdf.verifyPages([{...baseline[0],...patch},...baseline.slice(1)],expected,baseline),/rotation|crop|text content|annotation action/);
  assert.throws(()=>pdf.verifyPages([baseline[0],{...baseline[1],urls:[]},baseline[2]],expected,baseline),/annotation action/);
});
function preview(scale=1) {
  const image=raster(100*scale,150*scale);
  for(let y=10*scale;y<35*scale;y++)for(let x=8*scale;x<35*scale;x++){const i=(y*image.width+x)*4;image.data[i]=220;image.data[i+1]=30;image.data[i+2]=40;}
  for(let y=80*scale;y<115*scale;y++)for(let x=55*scale;x<65*scale;x++){const i=(y*image.width+x)*4;image.data[i]=240;image.data[i+1]=150;image.data[i+2]=20;}
  return image;
}
test('native preview oracle accepts independent resolution but rejects absent colour identities and wrong orientation',()=>{
  assert.doesNotThrow(()=>verifyPreview(preview(2),preview(),1));
  assert.throws(()=>verifyPreview(raster(100,150),preview(),1),/missing.*colour identity/);
  assert.throws(()=>verifyPreview(pdf.rotateRaster(preview(),2),preview(),1),/content\/orientation/);
  assert.throws(()=>verifyPreview(preview(),preview(),2),/missing.*colour identity/);
  const transparent=preview();for(let i=3;i<transparent.data.length;i+=4)transparent.data[i]=0;
  assert.throws(()=>verifyPreview(transparent,preview(),1),/missing.*colour identity/);
});
test('raster edge tolerance accepts one-pixel quantization but rejects shifted or dropped thin content',()=>{
  function block(shift){const image=raster(100,100);for(let y=20;y<80;y++)for(let x=20+shift;x<60+shift;x++)for(let c=0;c<3;c++)image.data[(y*100+x)*4+c]=0;return image;}
  assert.doesNotThrow(()=>pdf.compareRaster(block(1),block(0),'one pixel'));
  assert.throws(()=>pdf.compareRaster(block(3),block(0),'three pixels'),/content\/orientation/);
  const thin=raster(100,100);for(let y=10;y<90;y++)for(let c=0;c<3;c++)thin.data[(y*100+50)*4+c]=0;
  assert.throws(()=>pdf.compareRaster(raster(100,100),thin,'lost thin line'),/content\/orientation/);
});
