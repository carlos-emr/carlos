/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const assert=require('node:assert/strict'),{PNG}=require('pngjs');
const pdf=require('./pdf-graph');
// Calibrated from the printed WHO Canada 2006 chart grid, in 72-dpi pixels.
// No servlet, graphic-class or active graphic-properties value is loaded here.
const AXES=Object.freeze({
  length:{x:[[0,125],[24,517]],y:[[20,525.5],[100,113.3]]},
  weight:{x:[[0,125],[24,517]],y:[[2,678.8],[16,318.5]]},
  head:{x:[[0,125],[24,517]],y:[[30,330.6],[50,146.7]]},
  weightLength:{x:[[50,155.2],[100,457.5]],y:[[2,688.4],[24,262.4]]},
});
const ASSETS=Object.freeze({
  F:{growthLength:['growthGirlLength0_36.pdf','c57266a9f48ddf3d9cf1a4b6b1dcf8a6fda1d88f5b731d89c794630fcc3be4db'],
    length:['GrowthChartRourke2009Girls.pdf','a10a8a1da7bff8bee8afeef6983eddd414516a9aeab904c6c451bad824fef47e'],
    head:['growthGirlHead0_36.pdf','ff23fa80930ef146beb2716892d54204e24daec021bebc3be822b6d46b76929c']},
  M:{growthLength:['growthBoyLength0_36.pdf','07ce4860387ef5620365c5ea3bf2d85e03c7896def8c6f0eafe100fe9dd64804'],
    length:['GrowthChartRourke2009Boys.pdf','1e574adcdc244de1ac7764ccb87ce7b40763340c323b1e0f5df8be891f32ee2a'],
    head:['growthBoyHead0_36.pdf','966ff59d2dde8f5aa14042612ac79e94b1c9ea419ee08598805661ea13865932']},
});
const TOLERANCE=2; // grid/line centre measurement (<0.6 pt) and fractional-calendar age (<1.4 pt)
function interpolate(value,[[a,x],[b,y]]){assert(Number.isFinite(value));return x+(value-a)*(y-x)/(b-a);}
function expectedPoint({kind,age,length,value},sex='F'){
  let axis=AXES[kind];assert(axis,'Unknown clinical growth series');assert(['F','M'].includes(sex),'Unknown growth chart sex');
  // The boy head-circumference sheet's printed panels are vertically offset.
  // These are separate calibrations from its visible WHO grid, not the girl axes.
  if(sex==='M'&&kind==='head')axis={x:axis.x,y:[[30,353.36],[50,169.46]]};
  if(sex==='M'&&kind==='weightLength')axis={x:axis.x,y:[[2,685.8],[24,259.8]]};
  return {x:interpolate(kind==='weightLength'?length:age,axis.x),y:interpolate(value,axis.y),kind};
}
/** Read the actual vector circle paths in each page's own overlay stream. */
function circles(bytes){
  const own=pdf.contentStreams(bytes).filter(stream=>/\/Xf\d+\s+Do\b/.test(stream));
  assert(own.length,'Growth output has no page overlay');
  return own.map((stream,page)=>{
    const height=pdf.pageWords(bytes,page+1).height,result=[];
    const number='-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)';
    const token=new RegExp('('+number+')\\s+('+number+')\\s+('+number+')\\s+RG\\b|('+number+')\\s+('+number+')\\s+m\\s+((?:'+number+'\\s+){6}c\\s+(?:'+number+'\\s+){6}c\\s+(?:'+number+'\\s+){6}c\\s+(?:'+number+'\\s+){6}c)\\s+S\\b','g');
    let colour,match;
    while((match=token.exec(stream))){
      if(match[1]!==undefined){colour=[+match[1],+match[2],+match[3]].join(',');continue;}
      const values=match[6].match(new RegExp(number,'g')).map(Number),xs=[+match[4]],ys=[+match[5]];
      for(let index=0;index<values.length;index+=2){xs.push(values[index]);ys.push(values[index+1]);}
      const left=Math.min(...xs),right=Math.max(...xs),bottom=Math.min(...ys),top=Math.max(...ys);
      assert(Math.abs(right-left-3)<0.03&&Math.abs(top-bottom-3)<0.03,'Growth overlay contains an unfamiliar curve instead of a 1.5-point observation');
      const x=(left+right)/2,y=(bottom+top)/2;
      assert(Math.abs(+match[4]-right)<0.03&&Math.abs(+match[5]-y)<0.03,'Growth circle path is malformed');
      result.push({x,y:height-y,colour});
    }
    const curveCount=(stream.match(/\bc\b/g)||[]).length;
    assert.equal(curveCount,result.length*4,'Growth overlay contains unclassified Bezier paths');return result;
  });
}
function verifyPoints(actual,observations,{blueSeries='weight',sex='F'}={}){
  assert.equal(actual.length,observations.length,'Growth observation count differs (including duplicated source observations)');
  const remaining=actual.slice();let maximumDifference=0;
  for(const observation of observations){
    const expected=expectedPoint(observation,sex),colour=observation.kind===blueSeries?'0,0,1':'1,0,0';
    const candidates=remaining.map((point,index)=>({point,index,distance:Math.hypot(point.x-expected.x,point.y-expected.y)}))
      .filter(candidate=>candidate.point.colour===colour).sort((a,b)=>a.distance-b.distance);
    assert(candidates.length&&candidates[0].distance<=TOLERANCE,
      `${observation.kind}: wrong age/value coordinate (expected ${expected.x.toFixed(2)},${expected.y.toFixed(2)}; nearest ${candidates[0]?.distance.toFixed(2)??'missing'} pt)`);
    maximumDifference=Math.max(maximumDifference,candidates[0].distance);remaining.splice(candidates[0].index,1);
  }
  return {points:observations.length,maximumDifferencePt:+maximumDifference.toFixed(3),tolerancePt:TOLERANCE};
}
function reference(sex,kind){
  assert(ASSETS[sex]?.[kind],'Unknown sex/reference chart');const [name,digest]=ASSETS[sex][kind];
  const bytes=fs.readFileSync(path.join(__dirname,'../../src/main/resources/oscar/form/prop',name));
  assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),digest,'WHO reference asset changed; independently review and recalibrate it');return bytes;
}
function verifyCurves(output,referenceBytes,points,{head=false,page=1}={}){
  const a=PNG.sync.read(pdf.renderPng(output,{dpi:72,page})),b=PNG.sync.read(pdf.renderPng(referenceBytes,{dpi:72,page}));
  assert.equal(a.width,b.width,'Reference page width differs');assert.equal(a.height,b.height,'Reference page height differs');
  let compared=0,different=0;
  for(let y=20;y<735;y++)for(let x=50;x<590;x++){
    // Printed form-value tables are not reference curves. Preserve every other
    // grid/curve/unit region and exclude only each verified observation's ink.
    if(y>=50&&y<=85&&x>=360)continue; // native patient identity overlay
    if(y>=(head?623:604)&&x>=(head?264:254))continue;
    if(points.some(point=>Math.hypot(x-point.x,y-point.y)<=5))continue;
    const offset=(y*a.width+x)*4;compared++;
    if([0,1,2].some(channel=>Math.abs(a.data[offset+channel]-b.data[offset+channel])>3))different++;
  }
  assert(compared>150000,'Insufficient independently compared reference chart area');
  assert(different===0,`Sex-specific WHO reference grid/curves/units/age-axis changed (${different}/${compared} pixels)`);
  return {referencePixels:compared,differentPixels:different};
}
module.exports={AXES,ASSETS,TOLERANCE,expectedPoint,circles,verifyPoints,reference,verifyCurves};
