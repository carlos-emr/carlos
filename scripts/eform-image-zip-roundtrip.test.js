/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {PNG}=require('pngjs');
const {zipStored,zipDeflated}=require('./lib/gap-encounter-zip');
const {unzip}=require('./lib/export-content-helpers');
const {verifyArchive,verifyRaster}=require('./eform-image-zip-roundtrip-playwright-checks');
const spec={name:'FAKE-PW0123456789abcdefZIP',filename:'owned.html',provider:'123',
  html:'<img src="${oscar_image_path}page.png"><img src="${oscar_image_path}tile.png">',
  assets:[{kind:'background',name:'page.png',bytes:Buffer.from('independent background bytes')},
    {kind:'inline',name:'tile.png',bytes:Buffer.from('different inline bytes')}]};
function entries() {
  return [{name:spec.name+'/eform.properties',data:'form.name='+spec.name+'\nform.htmlFilename=owned.html\nform.creator=123\n'},
    {name:spec.name+'/owned.html',data:spec.html},...spec.assets.map(a=>({name:spec.name+'/'+a.name,data:a.bytes}))];
}
test('stored ZIP writer retains its established bytes',()=>{
  assert.equal(zipStored([{name:'a.txt',data:'hello'}]).toString('base64'),
    'UEsDBBQAAAgAAAAAIQCGphA2BQAAAAUAAAAFAAAAYS50eHRoZWxsb1BLAQIUABQAAAgAAAAAIQCGphA2BQAAAAUAAAAFAAAAAAAAAAAAAAAAAAAAAABhLnR4dFBLBQYAAAAAAQABADMAAAAoAAAAAAA=');
});
test('deflated ZIP writer preserves UTF-8 names, content and empty members',()=>{
  const values=[{name:'Zoë.txt',data:'Ł 日本語'},{name:'empty',data:''},{name:'tail',data:'last member'}];
  const actual=unzip(zipDeflated(values));
  assert.deepEqual(Object.keys(actual),values.map(e=>e.name));
  for(const entry of values)assert.deepEqual(actual[entry.name],Buffer.from(entry.data));
});
test('compressed refusal fixture expands beyond the actual 16 MiB text budget',()=>{
  const data=Buffer.alloc((16<<20)+1,35),bytes=zipDeflated([{name:'eform.properties',data}]);
  assert.ok(bytes.length<100000);
  assert.deepEqual(unzip(bytes)['eform.properties'],data);
});
test('exact self-contained image package is accepted',()=>verifyArchive(zipStored(entries()),spec));
for(const [label,edit,reason] of [
  ['omitted background',list=>list.splice(2,1),/central-directory resource count/],
  ['foreign resource name',list=>list[3].name='foreign/tile.png',/resource names/],
  ['changed image bytes',list=>list[3].data=Buffer.from('substituted'),/image bytes: inline/],
  ['changed HTML reference',list=>list[1].data=spec.html.replace('tile.png','absent.png'),/template HTML/],
  ['wrong metadata',list=>list[0].data=String(list[0].data).replace('form.creator=123','form.creator=999'),/form.creator/],
  ['duplicate resource',list=>list.push({...list[3]}),/central-directory resource count/],
])test('image package refuses '+label,()=>{const list=entries();edit(list);assert.throws(()=>verifyArchive(zipStored(list),spec),reason);});

function baseline() {
  const png=new PNG({width:600,height:800});
  for(let y=0;y<800;y++)for(let x=0;x<600;x++) {
    let rgba=x<300?[190,220,250,255]:[250,225,180,255];
    if(y>=300&&y<360&&x>=200&&x<300)rgba=x<250?[220,30,40,255]:[20,20,20,255];
    png.data.set(rgba,(y*600+x)*4);
  }
  return png;
}
test('independently authored expected page and inline pixels are accepted',()=>verifyRaster(baseline()));
for(const [label,edit,reason] of [
  ['blank output',p=>p.data.fill(255),/image region/],
  ['missing inline image',p=>{for(let y=300;y<360;y++)for(let x=200;x<300;x++)p.data.set([190,220,250,255],(y*600+x)*4);},/220,320 image region/],
  ['swapped page regions',p=>{for(let y=0;y<100;y++)for(let x=0;x<600;x++)p.data.set(x<300?[250,225,180,255]:[190,220,250,255],(y*600+x)*4);},/50,50 image region/],
  ['wrong paper size',p=>p.width=700,/page geometry/],
  ['incomplete raster',p=>p.data=p.data.subarray(4),/raster is incomplete/],
])test('image output refuses '+label,()=>{const png=baseline();edit(png);assert.throws(()=>verifyRaster(png),reason);});
