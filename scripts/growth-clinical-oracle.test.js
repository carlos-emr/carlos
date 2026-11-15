/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),zlib=require('node:zlib');
const oracle=require('./lib/growth-clinical-oracle');
const observations=[{kind:'weight',age:0,value:2},{kind:'length',age:24,value:100}];
const actual=[{x:125,y:678.8,colour:'0,0,1'},{x:517,y:113.3,colour:'1,0,0'}];
test('independently specified chart-grid endpoints and RGB series identify both observations',()=>{
  assert.equal(oracle.verifyPoints(actual,observations).maximumDifferencePt,0);
  assert.deepEqual(oracle.expectedPoint({kind:'weightLength',length:50,value:24}),{x:155.2,y:262.4,kind:'weightLength'});
});
for(const [label,change] of [
  ['missing point',points=>points.slice(1)],['extra point',points=>[...points,points[0]]],
  ['date/age shift',points=>points.map((point,index)=>index?point:{...point,x:point.x+5})],
  ['wrong value',points=>points.map((point,index)=>index?point:{...point,y:point.y+5})],
  ['wrong series',points=>points.map(point=>({...point,colour:point.colour==='0,0,1'?'1,0,0':'0,0,1'}))],
  ['mirrored axis',points=>points.map(point=>({...point,x:642-point.x}))],
])test('growth geometry rejects '+label,()=>assert.throws(()=>oracle.verifyPoints(change(actual),observations)));
test('duplicated source observations require exact multiplicity rather than a set comparison',()=>{
  const points=[actual[0],actual[0]],rows=[observations[0],observations[0]];assert.equal(oracle.verifyPoints(points,rows).points,2);
  assert.throws(()=>oracle.verifyPoints(points.slice(1),rows),/count differs/);
});
test('sex-specific independently pinned WHO chart assets remain distinct and readable',()=>{
  for(const kind of ['length','growthLength','head'])assert(!oracle.reference('F',kind).equals(oracle.reference('M',kind)));
  assert.throws(()=>oracle.reference('other','head'),/Unknown sex/);
});
test('the boy head/weight-for-length printed grids have their own calibration and reject the girl coordinates',()=>{
  const row={kind:'head',age:0,value:30},point={x:125,y:353.36,colour:'0,0,1'};
  assert.equal(oracle.verifyPoints([point],[row],{blueSeries:'head',sex:'M'}).maximumDifferencePt,0);
  assert.throws(()=>oracle.verifyPoints([point],[row],{blueSeries:'head',sex:'F'}),/wrong age\/value/);
  assert.deepEqual(oracle.expectedPoint({kind:'weightLength',length:50,value:24},'M'),{x:155.2,y:259.8,kind:'weightLength'});
});
test('reference pixel comparison accepts unchanged WHO curves and refuses a plausible opposite-sex chart',()=>{
  const girl=oracle.reference('F','head'),boy=oracle.reference('M','head');
  assert.equal(oracle.verifyCurves(girl,girl,[],{head:true}).differentPixels,0);
  assert.throws(()=>oracle.verifyCurves(boy,girl,[],{head:true}),/Sex-specific WHO reference/);
});
function graphPdf(stream){
  const zipped=zlib.deflateSync(Buffer.from('BT /F1 10 Tf 10 780 Td (Synthetic chart) Tj ET\n'+stream));
  const objects=['<< /Type /Catalog /Pages 2 0 R >>','<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> /XObject << /Xf1 5 0 R >> >> /Contents 4 0 R >>',
    Buffer.concat([Buffer.from('<< /Filter /FlateDecode /Length '+zipped.length+' >>\nstream\n'),zipped,Buffer.from('\nendstream')]),
    '<< /Type /XObject /Subtype /Form /BBox [0 0 612 792] /Length 0 >>\nstream\n\nendstream'];
  const parts=[Buffer.from('%PDF-1.4\n')],offsets=[0];let length=parts[0].length;
  objects.forEach((object,index)=>{offsets.push(length);const bytes=Buffer.concat([Buffer.from((index+1)+' 0 obj\n'),Buffer.from(object),Buffer.from('\nendobj\n')]);parts.push(bytes);length+=bytes.length;});
  parts.push(Buffer.from('xref\n0 '+(objects.length+1)+'\n0000000000 65535 f \n'+offsets.slice(1).map(offset=>String(offset).padStart(10,'0')+' 00000 n \n').join('')+'trailer\n<< /Size '+(objects.length+1)+' /Root 1 0 R >>\nstartxref\n'+length+'\n%%EOF\n'));
  return Buffer.concat(parts);
}
// Hand-specified four cubic arcs, centred at PDF (125,113.2), radius 1.5.
const circle='126.5 113.2 m\n126.5 114.0284 125.8284 114.7 125 114.7 c\n124.1716 114.7 123.5 114.0284 123.5 113.2 c\n123.5 112.3716 124.1716 111.7 125 111.7 c\n125.8284 111.7 126.5 112.3716 126.5 113.2 c\nS';
test('actual compressed PDF circle paths yield page coordinates without counting imported percentile curves',()=>{
  const result=oracle.circles(graphPdf('/Xf1 Do\n0 0 1 RG\n'+circle));
  assert.equal(result.length,1);assert.equal(result[0].length,1);assert.equal(result[0][0].x,125);assert(Math.abs(result[0][0].y-678.8)<0.001);assert.equal(result[0][0].colour,'0,0,1');
});
test('an unclassified Bezier path cannot silently disappear from growth output',()=>{
  assert.throws(()=>oracle.circles(graphPdf('/Xf1 Do\n'+circle+'\n0 0 m 0 0 1 1 2 2 c')),/unclassified/);
});
