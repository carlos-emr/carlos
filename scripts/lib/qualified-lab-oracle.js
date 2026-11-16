/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {execFileSync}=require('node:child_process');
const {PNG}=require('pngjs');
const {calibrate}=require('./measurement-plot');
function assertMessage(encoded,{marker,accession,date,time='09:00',tests}){
  const text=Buffer.from(encoded,'base64').toString('utf8'),segments=text.split(/[\r\n]+/).filter(Boolean).map(line=>line.split('|'));
  const pid=segments.filter(row=>row[0]==='PID'),orc=segments.filter(row=>row[0]==='ORC'),obx=segments.filter(row=>row[0]==='OBX');
  assert.equal(pid.length,1,'Stored HL7 patient identity is ambiguous');assert.equal(pid[0][5],marker+'^Workflow');
  assert.equal(orc.length,1);assert.equal(orc[0][2],accession,'Stored HL7 accession changed');assert.equal(obx.length,tests.length,'Stored HL7 lost or duplicated observations');
  for(const [index,test] of tests.entries()){
    const row=obx[index];assert.equal(row[1],String(index+1));assert.deepEqual(row[3].split('^').slice(0,2),[test.code,test.name]);
    assert.deepEqual(row.slice(5,9),[test.value,test.unit,test.low+' - '+test.high,test.flag],'Stored HL7 literal, units, range or flag changed');
    assert.equal(row[11],'F','Stored HL7 status changed');assert.equal(row[14],date.replaceAll('-','')+time.replace(':','')+'00','Stored observation timestamp changed');
  }
  return text;
}
function distance(point,a,b){const dx=b.x-a.x,dy=b.y-a.y,t=Math.max(0,Math.min(1,((point.x-a.x)*dx+(point.y-a.y)*dy)/(dx*dx+dy*dy)));return Math.hypot(point.x-a.x-t*dx,point.y-a.y-t*dy);}
function verifyGeometry({points,x,y},observations){
  const expected=observations.map(row=>({x:x.at(Date.parse(row.date+'T00:00:00Z')),y:y.at(Number(row.value))})).sort((a,b)=>a.x-b.x);
  assert(expected.length>=2&&expected.every((point,index)=>Number.isFinite(point.x)&&Number.isFinite(point.y)&&(!index||point.x>expected[index-1].x)),'Lab control requires independently dated finite numeric projections');
  assert(points.length>20,'Native lab graph contains no visible numeric series');
  for(const point of expected)assert(points.some(actual=>Math.hypot(actual.x-point.x,actual.y-point.y)<=6),'Native lab graph omitted or displaced a numeric control');
  const segments=expected.slice(1).map((point,index)=>[expected[index],point]);
  assert(points.every(point=>segments.some(([a,b])=>distance(point,a,b)<=6)),'Native lab graph contains an undeclared numeric projection or another patient');
  return {numericPoints:expected.length,tolerancePixels:6,redPixels:points.length};
}
function printedDateAxis(candidates){
  // A date printed beside a low observation can OCR as a bare year too. Use
  // the common axis-text row so that label cannot crop a valid plotted point.
  let dates=[];
  for(const candidate of candidates){
    const row=candidates.filter(item=>Math.abs(item.word.y-candidate.word.y)<=2);
    if(row.length>dates.length)dates=row;
  }
  assert(dates.length>=3&&dates.length>=candidates.length*0.8,
    'Native printed lab date ticks do not establish an unambiguous axis row');
  return dates;
}
function inspectPlot(bytes){
  const png=PNG.sync.read(bytes,{checkCRC:true});assert.equal(png.width,1000);assert.equal(png.height,600);
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'qualified-lab-plot-'));
  let tsv;try{const file=path.join(dir,'native.png');fs.writeFileSync(file,bytes,{mode:0o600});tsv=execFileSync('tesseract',[file,'stdout','-l','eng','--psm','11','tsv'],{encoding:'utf8',timeout:30000,maxBuffer:2*1024*1024,stdio:['ignore','pipe','pipe']});}finally{fs.rmSync(dir,{recursive:true,force:true});}
  const words=tsv.trim().split('\n').slice(1).map(line=>line.split('\t')).filter(row=>row[0]==='5'&&row[11]?.trim()).map(row=>({x:+row[6],y:+row[7],w:+row[8],h:+row[9],text:row.slice(11).join('\t').trim()}));
  const months=['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
  const dates=printedDateAxis(words.map(word=>({word,match:/^(?:(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)-(\d{4})|(\d{4}))$/.exec(word.text)})).filter(row=>row.match&&row.word.y>400));
  const baseline=Math.min(...dates.map(row=>row.word.y));
  let ticks=words.filter(word=>word.x+word.w<80&&word.y+word.h/2<baseline-8&&/^-?\d+(?:\.\d+)?$/.test(word.text));
  assert(ticks.length>=3,'Native lab value ticks are unreadable');
  // Magnify the printed number strip without changing pixels or guessing lost
  // decimal points. Small 6.5 labels otherwise OCR as 65 at native resolution.
  const leftEdge=Math.max(0,Math.floor(Math.min(...ticks.map(word=>word.x))-3));
  const rightEdge=Math.ceil(Math.max(...ticks.map(word=>word.x+word.w))+3);
  const topEdge=Math.max(0,Math.floor(Math.min(...ticks.map(word=>word.y))-3));
  const bottomEdge=Math.ceil(Math.max(...ticks.map(word=>word.y+word.h))+3),scale=4;
  const axis=new PNG({width:(rightEdge-leftEdge)*scale,height:(bottomEdge-topEdge)*scale});
  for(let ay=0;ay<axis.height;ay++)for(let ax=0;ax<axis.width;ax++){const source=((topEdge+Math.floor(ay/scale))*png.width+leftEdge+Math.floor(ax/scale))*4;png.data.copy(axis.data,(ay*axis.width+ax)*4,source,source+4);}
  const axisDir=fs.mkdtempSync(path.join(os.tmpdir(),'qualified-lab-axis-'));let axisTsv;
  try{const file=path.join(axisDir,'printed.png');fs.writeFileSync(file,PNG.sync.write(axis),{mode:0o600});axisTsv=execFileSync('tesseract',[file,'stdout','-l','eng','--psm','6','-c','tessedit_char_whitelist=0123456789.-','tsv'],{encoding:'utf8',timeout:30000,maxBuffer:1024*1024,stdio:['ignore','pipe','pipe']});}finally{fs.rmSync(axisDir,{recursive:true,force:true});}
  ticks=axisTsv.trim().split('\n').slice(1).map(line=>line.split('\t')).filter(row=>row[0]==='5'&&/^-?\d+(?:\.\d+)?$/.test(row[11]||'')).map(row=>({x:leftEdge+Number(row[6])/scale,y:topEdge+Number(row[7])/scale,w:Number(row[8])/scale,h:Number(row[9])/scale,text:row[11]}));
  const x=calibrate(dates.map(({word,match})=>({pixel:word.x+word.w/2,value:Date.UTC(+(match[2]||match[3]),match[1]?months.indexOf(match[1]):0,1)})),3,1);
  const y=calibrate(ticks.map(word=>({pixel:word.y+word.h/2,value:Number(word.text)})),3,-1);
  const left=Math.max(...ticks.map(word=>word.x+word.w))+5,points=[];
  for(let py=20;py<baseline-8;py++)for(let px=Math.ceil(left);px<png.width-10;px++){const offset=(py*png.width+px)*4,r=png.data[offset],g=png.data[offset+1],b=png.data[offset+2];if(r>=180&&r>g+45&&r>b+45)points.push({x:px,y:py});}
  return {points,x,y};
}
function assertNoNumericPoints(bytes){const png=PNG.sync.read(bytes,{checkCRC:true});assert.equal(png.width,1000);assert.equal(png.height,600);let points=0;for(let y=20;y<530;y++)for(let x=80;x<990;x++){const i=(y*png.width+x)*4;if(png.data[i]>=180&&png.data[i]>png.data[i+1]+45&&png.data[i]>png.data[i+2]+45)points++;}assert.equal(points,0,'Native lab graph invented a numeric point for a wholly qualitative result');}
/** Read each actual PDF row and column, including wrapped names/units/ranges. */
function assertPdfWords(words,{tests,date}){
  const clean=value=>value.replace(/\s+/g,'');
  const first=tests[0],start=words.filter(word=>word.text===first.name.split(' ')[0]);
  assert.equal(start.length,1,'PDF first clinical row identity is ambiguous');
  const initial=start[0],line=words.filter(word=>Math.abs(word.yMin-initial.yMin)<2).sort((a,b)=>a.xMin-b.xMin);
  function anchor(text,after){const matches=line.filter(word=>word.text===text&&word.xMin>after);assert.equal(matches.length,1,'PDF column anchor is missing or ambiguous: '+text);return matches[0].xMin;}
  const left=[initial.xMin];
  for(const text of [first.value,first.flag,first.low,first.unit,date,'Final'])left.push(anchor(text,left.at(-1)+1));
  assert(left.every((value,index)=>!index||value>left[index-1]),'PDF clinical columns are out of order');
  const starts=tests.map(test=>{const matches=words.filter(word=>word.xMin>=left[0]-1&&word.xMax<left[1]&&word.yMin>=initial.yMin-1&&word.text===test.name.split(' ')[0]);assert.equal(matches.length,1,'PDF clinical row lost/duplicated its test name');return matches[0].yMin;});
  assert(starts.every((value,index)=>!index||value>starts[index-1]),'PDF reordered clinical associations');
  const footer=words.filter(word=>word.text==='END'&&word.yMin>starts.at(-1)).map(word=>word.yMin);
  assert(footer.length===1,'PDF report has no unambiguous completion boundary');
  for(const [index,test] of tests.entries()){
    const bottom=index+1<starts.length?starts[index+1]:footer[0];
    const row=words.filter(word=>word.yMin>=starts[index]-1&&word.yMin<bottom-1);
    const expected=[test.name,test.value,test.flag,test.low+' - '+test.high,test.unit,date+' 09:00:00','Final'];
    for(let column=0;column<expected.length;column++){
      const content=row.filter(word=>word.xMin>=left[column]-1&&(column===left.length-1||word.xMin<left[column+1]-1)).sort((a,b)=>Math.abs(a.yMin-b.yMin)>2?a.yMin-b.yMin:a.xMin-b.xMin).map(word=>word.text).join('');
      assert.equal(clean(content),clean(expected[column]),'PDF changed the literal/name/flag/range/unit/time/status association in '+test.name+' column '+column);
    }
  }
}
function assertPdf(bytes,expected){const graph=require('./pdf-graph');assert.equal(graph.pageCount(bytes),1,'Known five-row lab fixture was clipped or repaginated');const words=graph.pageWords(bytes,1).words;assertPdfWords(words,expected);const text=words.map(word=>word.text).join('');assert(text.includes(expected.marker)&&!text.includes(expected.controlMarker)&&!text.includes('999.37'),'PDF changed the patient or disclosed its control');}
module.exports={assertMessage,inspectPlot,verifyGeometry,printedDateAxis,assertPdfWords,assertPdf,assertNoNumericPoints};
