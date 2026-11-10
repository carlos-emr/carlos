/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {assertMessage,verifyGeometry}=require('./lib/qualified-lab-oracle');
const marker='FAKE-PW0123456789abcdef',expected={marker,accession:'QLORACLE',date:'2021-01-01',tests:[{code:'2010',name:'HEMOGLOBIN',value:'<0.1',unit:'g/L',low:'120',high:'160',flag:'A'}]};
const lines=['MSH|^~\\&|SYNTHETIC','PID|1||||'+marker+'^Workflow','ORC|RE|QLORACLE','OBX|1|FT|2010^HEMOGLOBIN^|GENERAL|<0.1|g/L|120 - 160|A|||F|||20210101090000'];
const encode=rows=>Buffer.from(rows.join('\r'),'utf8').toString('base64');
test('stored-message oracle preserves literal qualifier and observation identities',()=>assertMessage(encode(lines),expected));
for(const [label,old,value] of [['qualifier','<0.1','0.1'],['fabricated zero','<0.1','0'],['units','g/L','mg/L'],['flag','|A|','|N|'],['range','120 - 160','0 - 100'],['timestamp','20210101090000','20210102090000'],['patient',marker,'FAKE-PWaaaaaaaaaaaaaaaa'],['accession','QLORACLE','FOREIGN'],['status','|F|','|P|'],['code','2010^','2013^']])test('stored-message oracle refuses '+label,()=>assert.throws(()=>assertMessage(encode(lines.map(line=>line.replace(old,value))),expected)));
test('stored-message oracle refuses missing/duplicate observations',()=>{assert.throws(()=>assertMessage(encode(lines.slice(0,-1)),expected));assert.throws(()=>assertMessage(encode([...lines,lines.at(-1)]),expected));});
const observations=[{date:'2021-01-01',value:'137'},{date:'2026-01-01',value:'145'}],dates=observations.map(row=>Date.parse(row.date+'T00:00:00Z'));
const x={at:value=>100+(value-dates[0])/(dates[1]-dates[0])*600},y={at:value=>500-(value-130)*20};
const points=Array.from({length:101},(_,i)=>({x:100+i*6,y:360-i*1.6}));
test('two numeric controls establish the complete visible series',()=>assert.equal(verifyGeometry({points,x,y},observations).numericPoints,2));
for(const [label,changed] of [['qualified comparator converted to an exact bound',[...points,{x:350,y:490}]],['qualitative fabricated zero',[...points,{x:550,y:500}]],['another patient',[...points,{x:700,y:40}]],['missing numeric control',points.slice(0,50)],['wrong observation date',points.map(point=>({...point,x:point.x+25}))]])test('native graph oracle refuses '+label,()=>assert.throws(()=>verifyGeometry({points:changed,x,y},observations)));
const legacyObservations=[{date:'2021-01-01',value:'0.1'},{date:'2023-12-29',value:'137'},
  {date:'2025-10-02',value:'100'},{date:'2026-11-01',value:'145'}];
const legacyStart=Date.parse('2021-01-01T00:00:00Z'),legacyEnd=Date.parse('2026-11-01T00:00:00Z');
const legacyX={at:value=>100+(value-legacyStart)/(legacyEnd-legacyStart)*600},legacyY={at:value=>500-value*2};
function legacyPixels(rows){
  const vertices=rows.map(row=>({x:legacyX.at(Date.parse(row.date+'T00:00:00Z')),y:legacyY.at(Number(row.value))}));
  return vertices.slice(1).flatMap((point,index)=>Array.from({length:101},(_,step)=>({
    x:vertices[index].x+(point.x-vertices[index].x)*step/100,
    y:vertices[index].y+(point.y-vertices[index].y)*step/100})));
}
test('declared legacy projections and numeric controls establish every polyline vertex',()=>
  assert.equal(verifyGeometry({points:legacyPixels(legacyObservations),x:legacyX,y:legacyY},legacyObservations).numericPoints,4));
for(const [label,changed] of [
  ['missing early qualified projection',legacyObservations.slice(1)],
  ['missing later qualified projection',legacyObservations.filter((_,index)=>index!==2)],
  ['incorrect qualified bound',legacyObservations.map((row,index)=>index===2?{...row,value:'10'}:row)],
])test('declared legacy projection oracle refuses '+label,()=>assert.throws(()=>
  verifyGeometry({points:legacyPixels(changed),x:legacyX,y:legacyY},legacyObservations)));
test('declared projection oracle still refuses another patient outside the polyline',()=>assert.throws(()=>
  verifyGeometry({points:[...legacyPixels(legacyObservations),{x:100,y:40}],x:legacyX,y:legacyY},legacyObservations)));
const {assertPdfWords}=require('./lib/qualified-lab-oracle');
const pdfPlan={date:'2021-01-01',tests:[expected.tests[0],{code:'3210',name:'SODIUM',value:'Negative',unit:'qual-Na',low:'negative',high:'negative',flag:'N'}]};
function pdfWords(){const words=[];const word=(text,x,y)=>words.push({text,xMin:x,yMin:y,xMax:x+Math.max(5,text.length*3),yMax:y+8});
  for(const [text,x] of [['HEMOGLOBIN',10],['<0.1',170],['A',240],['120',280],['-',305],['160',320],['g/L',370],['2021-01-01',450],['09:00:00',510],['Final',560]])word(text,x,200);
  for(const [text,x] of [['SODIUM',10],['Negative',170],['N',240],['negative',280],['-',310],['qual-',370],['2021-01-01',450],['09:00:00',510],['Final',560]])word(text,x,220);
  word('negative',280,230);word('Na',370,230);word('END',280,260);return words;
}
test('actual PDF row/column oracle reconstructs wrapped ranges and units without losing their associations',()=>assertPdfWords(pdfWords(),pdfPlan));
for(const [label,old,value] of [['qualifier','<0.1','0.1'],['qualitative case','Negative','negative'],['unit reference','qual-','foreign-']])test('PDF row/column oracle refuses changed '+label,()=>assert.throws(()=>assertPdfWords(pdfWords().map(word=>word.text===old?{...word,text:value}:word),pdfPlan)));
const {assertNativeIdentityFloor}=require('./lab-qualified-results-playwright-checks');
function allocator(change={}){return {value:query=>query.includes("TABLE_NAME='fileUploadCheck'")?String(change.checksumNext??100):query.includes('MAX(fileUploadCheck_id)')?String(change.checksumReference??99):query.includes('AUTO_INCREMENT')?String(change.labNext??200):String(change.labReference??199)};}
test('native lab/checksum allocators cannot inherit dangling clinical references',()=>assertNativeIdentityFloor(allocator()));
for(const change of [{checksumNext:99},{checksumReference:101},{labNext:199},{labReference:201},{checksumNext:NaN},{labNext:2147483647}])test('unsafe native identity range is rejected before clinical entry: '+JSON.stringify(change),()=>assert.throws(()=>assertNativeIdentityFloor(allocator(change))));
const {PNG}=require('pngjs');const {assertNoNumericPoints}=require('./lib/qualified-lab-oracle');
test('a wholly qualitative graph permits its legend but no fabricated zero or bound point',()=>{const png=new PNG({width:1000,height:600});png.data.fill(255);for(let x=400;x<415;x++)png.data.set([255,0,0,255],(580*1000+x)*4);assertNoNumericPoints(PNG.sync.write(png));png.data.set([255,0,0,255],(300*1000+500)*4);assert.throws(()=>assertNoNumericPoints(PNG.sync.write(png)));});
