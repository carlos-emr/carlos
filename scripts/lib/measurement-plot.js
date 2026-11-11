/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

// An output oracle, independent of GraphMeasurements' data and range code.
// Axis coordinates come from the actual printed tick labels; the expected
// observations come from the caller's fixed fixture. Tolerance is three PNG
// pixels (1000x600 native image), covering antialiasing and OCR box centres.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { PNG } = require('pngjs');
const { assert } = require('./playwright-harness');
const TOLERANCE = 3;
const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

function decode(bytes) {
  assert(bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])), 'Graph has invalid PNG bytes');
  const png = PNG.sync.read(bytes, { checkCRC: true });
  assert(png.width === 1000 && png.height === 600, 'Unexpected native graph dimensions');
  return png;
}

function ocr(bytes, args = ['--psm','11','tsv']) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-plot-'));
  try {
    const file = path.join(dir, 'graph.png');
    fs.writeFileSync(file, bytes, { mode: 0o600 });
    return execFileSync('tesseract', [file, 'stdout', '-l', 'eng', ...args],
      { encoding:'utf8', timeout:30000, maxBuffer:2*1024*1024, stdio:['ignore','pipe','pipe'] });
  } finally { fs.rmSync(dir, { recursive:true, force:true }); }
}

function words(tsv) {
  return tsv.trim().split('\n').slice(1).map(line => line.split('\t'))
    .filter(row => row[0] === '5' && row[11]?.trim()).map(row => ({
      x:+row[6], y:+row[7], w:+row[8], h:+row[9], confidence:+row[10], text:row.slice(11).join('\t').trim(),
    }));
}

function background(png) {
  const frequencies = new Map();
  for (let i=0; i<png.data.length; i+=4) {
    const [r,g,b] = png.data.subarray(i,i+3);
    if (r === g && g === b && r >= 120 && r < 245) frequencies.set(r, (frequencies.get(r)||0)+1);
  }
  const grey = [...frequencies].sort((a,b)=>b[1]-a[1])[0]?.[0];
  assert(grey !== undefined, 'Graph has no plot background');
  const same = (x,y) => { const i=(y*png.width+x)*4; return png.data[i]===grey && png.data[i+1]===grey && png.data[i+2]===grey; };
  const rows=[];
  for(let y=0;y<png.height;y++) {
    let count=0; for(let x=0;x<png.width;x++) if(same(x,y))count++;
    if(count>png.width*0.5)rows.push(y);
  }
  assert(rows.length>300, 'Graph plot background is missing or clipped');
  const top=rows[0],bottom=rows.at(-1),columns=[];
  for(let x=0;x<png.width;x++) {
    let count=0; for(let y=top;y<=bottom;y++)if(same(x,y))count++;
    if(count>(bottom-top)*0.5)columns.push(x);
  }
  assert(columns.length>700, 'Graph plot is too narrow');
  return {left:columns[0],right:columns.at(-1),top,bottom};
}

function leastSquares(points) {
  const meanX=points.reduce((n,p)=>n+p.value,0)/points.length, meanY=points.reduce((n,p)=>n+p.pixel,0)/points.length;
  const slope=points.reduce((n,p)=>n+(p.value-meanX)*(p.pixel-meanY),0)/points.reduce((n,p)=>n+(p.value-meanX)**2,0);
  return {slope,intercept:meanY-slope*meanX};
}

// A few small text glyphs can be misread by OCR (94 as 4, for example). Require
// at least 80% of all candidates and the stated minimum to agree within 2px.
// This does not move data points, alter fixture values or derive plot ranges.
function calibrate(points, minimum, direction) {
  let inliers=[];
  for(let i=0;i<points.length;i++)for(let j=i+1;j<points.length;j++) {
    if(points[i].value===points[j].value)continue;
    const fit=leastSquares([points[i],points[j]]);
    if(Math.sign(fit.slope)!==direction)continue;
    const candidate=points.filter(p=>Math.abs(p.pixel-(fit.slope*p.value+fit.intercept))<=2);
    if(candidate.length>inliers.length)inliers=candidate;
  }
  assert(inliers.length>=minimum && inliers.length>=points.length*0.8, 'Printed axis ticks do not establish a readable linear scale');
  const fit=leastSquares(inliers);
  assert(inliers.every(p=>Math.abs(p.pixel-(fit.slope*p.value+fit.intercept))<=2), 'Printed axis calibration is inconsistent');
  return { ...fit, ticks:inliers.length, at:value=>fit.slope*value+fit.intercept };
}

function redSeries(png, box) {
  const points=[];
  for(let x=box.left;x<=box.right;x++) {
    const ys=[];
    for(let y=box.top;y<=box.bottom;y++) {
      const i=(y*png.width+x)*4, r=png.data[i],g=png.data[i+1],b=png.data[i+2];
      if(r>=180 && r>g+45 && r>b+45)ys.push(y);
    }
    if(ys.length) {
      assert(ys.at(-1)-ys[0]<=4, 'Graph has multiple or thick unexpected series');
      points.push({x,y:ys.reduce((a,b)=>a+b,0)/ys.length});
    }
  }
  return points;
}

function distance(point, a, b) {
  const dx=b.x-a.x,dy=b.y-a.y;
  const t=Math.max(0,Math.min(1,((point.x-a.x)*dx+(point.y-a.y)*dy)/(dx*dx+dy*dy)));
  return Math.hypot(point.x-a.x-t*dx,point.y-a.y-t*dy);
}
function corners(points, tolerance=1.4) {
  if(points.length<=2)return points;
  let index=-1,maximum=0;
  for(let i=1;i<points.length-1;i++) {
    const d=distance(points[i],points[0],points.at(-1));
    if(d>maximum){maximum=d;index=i;}
  }
  return maximum<=tolerance?[points[0],points.at(-1)]
    :[...corners(points.slice(0,index+1),tolerance).slice(0,-1),...corners(points.slice(index),tolerance)];
}

function inspect(bytes) {
  const png=decode(bytes), box=background(png), labels=words(ocr(bytes));
  const dates=labels.filter(p=>p.y>box.bottom && p.y<box.bottom+30)
    .map(p=>({p,match:/^(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)-(\d{4})$/.exec(p.text)}))
    .filter(p=>p.match).map(({p,match})=>({value:Date.UTC(+match[2],MONTHS.indexOf(match[1]),1),pixel:p.x+p.w/2}));
  const values=labels.filter(p=>p.x+p.w<box.left && p.y+p.h/2>=box.top && p.y+p.h/2<=box.bottom && /^-?\d+(?:\.\d+)?$/.test(p.text))
    .map(p=>({value:+p.text,pixel:p.y+p.h/2}));
  const x=calibrate(dates,5,1), y=calibrate(values,6,-1);
  assert(labels.some(p=>p.text==='Days' && p.y>box.bottom), 'Graph date-axis title is missing');
  assert(labels.some(p=>/^wt$/i.test(p.text) && p.y>box.bottom+30), 'Graph weight legend is missing');
  const rotated=new PNG({width:png.height,height:32});
  for(let py=0;py<png.height;py++)for(let px=0;px<32;px++) {
    png.data.copy(rotated.data, (px*rotated.width+png.height-1-py)*4, (py*png.width+px)*4, (py*png.width+px)*4+4);
  }
  assert(/\bWeight\b/i.test(ocr(PNG.sync.write(rotated),['--psm','11'])), 'Graph value-axis title is not readable Weight');
  return {box,x,y,series:redSeries(png,box),labels};
}

function verifySeries(output, observations) {
  const expected=observations.slice().sort((a,b)=>a.date.localeCompare(b.date)).map(row=>({
    x:output.x.at(Date.parse(row.date+'T00:00:00Z')), y:output.y.at(+row.value),
  }));
  assert(expected.length>=2, 'Geometry verification requires at least two distinguishable points');
  assert(expected.every(p=>p.x>=output.box.left && p.x<=output.box.right && p.y>=output.box.top && p.y<=output.box.bottom), 'Expected observation lies outside printed axes');
  const actual=corners(output.series);
  assert(actual.length===expected.length, `Plotted vertex count differs: ${actual.length} versus ${expected.length}`);
  for(let i=0;i<expected.length;i++) {
    assert(Math.abs(actual[i].x-expected[i].x)<=TOLERANCE && Math.abs(actual[i].y-expected[i].y)<=TOLERANCE,
      `Plotted observation ${i+1} has wrong date/value geometry`);
  }
  assert(output.series.every((p,i)=>!i||p.x-output.series[i-1].x<=2), 'Plotted series has a missing segment');
  assert(output.series.every(p=>expected.slice(1).some((b,i)=>distance(p,expected[i],b)<=TOLERANCE)), 'Graph contains pixels outside the expected series');
  return {points:actual.length,dateTicks:output.x.ticks,valueTicks:output.y.ticks,tolerancePixels:TOLERANCE};
}

module.exports={decode,inspect,verifySeries,corners,calibrate};
