/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),zlib=require('node:zlib');
const {execFileSync}=require('node:child_process');
const {PNG}=require('pngjs');
const {assert}=require('./playwright-harness');
const graph=require('./pdf-graph');
const PAGES=Object.freeze([
  {id:1,media:[0,0,360,480],crop:[12,18,348,462],rotate:0},
  {id:2,media:[0,0,540,360],crop:[20,10,520,350],rotate:90},
  {id:3,media:[0,0,420,540],crop:[10,20,410,520],rotate:180},
]);

/** Independent scans with asymmetric colour blocks, crops and existing rotation. */
function fixturePdf(marker) {
  assert(/^FAKE-PW[a-f0-9]{16}$/.test(marker),'Invalid mixed-PDF fixture marker');
  const objects=[null,null],add=value=>{objects.push(Buffer.isBuffer(value)?value:Buffer.from(value));return objects.length;};
  const stream=(dictionary,bytes)=>Buffer.concat([Buffer.from('<< '+dictionary+' /Length '+bytes.length+' >>\nstream\n'),bytes,Buffer.from('\nendstream')]);
  const font=add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>'),kids=[];
  for(const model of PAGES) {
    const pixels=Buffer.alloc(60*80*3,255),colours=[[220,30,40],[20,160,50],[30,70,220]];
    for(let y=0;y<80;y++)for(let x=0;x<60;x++) {
      let colour;
      if(x>5&&x<25&&y>6&&y<25)colour=colours[model.id-1];
      else if(x>35&&x<54&&y>45&&y<70)colour=[40,40,40];
      else if(x>8&&x<15&&y>40&&y<45+model.id*7)colour=[240,150,20];
      if(colour)for(let c=0;c<3;c++)pixels[(y*60+x)*3+c]=colour[c];
    }
    const image=add(stream('/Type /XObject /Subtype /Image /Width 60 /Height 80 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode',zlib.deflateSync(pixels)));
    const annotation=model.id===2?add('<< /Type /Annot /Subtype /Link /Rect [55 100 115 140] /Border [0 0 3] /C [0 0 1] /F 4 /A << /S /URI /URI (https://example.invalid/'+marker+'/page2) >> >>'):null;
    const content='q '+model.media[2]+' 0 0 '+model.media[3]+' 0 0 cm /Scan Do Q\n'
      +(model.id===3?'BT /F1 12 Tf 60 90 Td ('+marker+' MIXED THREE) Tj ET\n':'');
    const body=add(stream('',Buffer.from(content)));
    kids.push(add('<< /Type /Page /Parent 2 0 R /MediaBox ['+model.media.join(' ')+'] /CropBox ['+model.crop.join(' ')+'] /Rotate '+model.rotate
      +' /Resources << /XObject << /Scan '+image+' 0 R >> /Font << /F1 '+font+' 0 R >> >> /Contents '+body+' 0 R'+(annotation?' /Annots ['+annotation+' 0 R]':'')+' >>'));
  }
  objects[0]=Buffer.from('<< /Type /Catalog /Pages 2 0 R >>');objects[1]=Buffer.from('<< /Type /Pages /Kids ['+kids.map(id=>id+' 0 R').join(' ')+'] /Count 3 >>');
  const chunks=[Buffer.from('%PDF-1.4\n')],offsets=[0];let length=chunks[0].length;
  objects.forEach((object,index)=>{offsets.push(length);const chunk=Buffer.concat([Buffer.from((index+1)+' 0 obj\n'),object,Buffer.from('\nendobj\n')]);chunks.push(chunk);length+=chunk.length;});
  chunks.push(Buffer.from('xref\n0 '+(objects.length+1)+'\n0000000000 65535 f \n'+offsets.slice(1).map(offset=>String(offset).padStart(10,'0')+' 00000 n \n').join('')
    +'trailer\n<< /Size '+(objects.length+1)+' /Root 1 0 R >>\nstartxref\n'+length+'\n%%EOF\n'));
  return Buffer.concat(chunks);
}

/** Rotate a decoded reference clockwise without resampling or reading product code. */
function rotateRaster(image,turns) {
  turns=((turns%4)+4)%4;
  const result=new PNG({width:turns%2?image.height:image.width,height:turns%2?image.width:image.height});
  for(let y=0;y<image.height;y++)for(let x=0;x<image.width;x++) {
    const [dx,dy]=turns===0?[x,y]:turns===1?[image.height-1-y,x]:turns===2?[image.width-1-x,image.height-1-y]:[y,image.width-1-x];
    image.data.copy(result.data,(dy*result.width+dx)*4,(y*image.width+x)*4,(y*image.width+x+1)*4);
  }
  return result;
}

/** Allow one raster pixel of edge quantization and eight RGBA levels.
 * At most 0.1% of pixels may remain unmatched in either direction.
 */
function compareRaster(actual,expected,label) {
  assert(actual.width===expected.width&&actual.height===expected.height,label+': effective page dimensions changed');
  function within(from,to,x,y) {
    const start=(y*from.width+x)*4;
    for(let c=0;c<4;c++) {
      let min=255,max=0;
      for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++) {
        const xx=x+dx,yy=y+dy;
        if(xx>=0&&xx<to.width&&yy>=0&&yy<to.height){const value=to.data[(yy*to.width+xx)*4+c];min=Math.min(min,value);max=Math.max(max,value);}
      }
      if(from.data[start+c]<min-8||from.data[start+c]>max+8)return false;
    }
    return true;
  }
  let changed=0;
  for(let y=0;y<actual.height;y++)for(let x=0;x<actual.width;x++) {
    const i=(y*actual.width+x)*4;
    if([0,1,2,3].some(c=>Math.abs(actual.data[i+c]-expected.data[i+c])>8)&&(!within(actual,expected,x,y)||!within(expected,actual,x,y)))changed++;
  }
  assert(changed/(actual.width*actual.height)<=0.001,label+': page identity/content/orientation differs ('+changed+' unmatched pixels)');
}

/** Poppler reads geometry, annotations and visible cropped pixels independently. */
function inspect(bytes) {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'mixed-pdf-inspect-')),file=path.join(root,'input.pdf');
  try {
    fs.writeFileSync(file,bytes,{mode:0o600});const count=graph.pageCount(bytes);
    const info=execFileSync('pdfinfo',['-box','-f','1','-l',String(count),file],{encoding:'utf8',timeout:30000});
    const urls=execFileSync('pdfinfo',['-url',file],{encoding:'utf8',timeout:30000}).split('\n').slice(1).filter(line=>line.trim()).map(line=>{
      const match=/^\s*(\d+)\s+\S+\s+(\S+)\s*$/.exec(line);assert(match,'Unreadable PDF annotation action');return {page:Number(match[1]),url:match[2]};
    });
    const pages=[];
    for(let page=1;page<=count;page++) {
      const selected=info.split('\n').filter(line=>Number(/^Page\s+(\d+)\s/.exec(line)?.[1])===page);
      const lineFor=label=>selected.find(line=>line.includes(label));
      const box=label=>{const line=lineFor(label),numbers=line?.slice(line.indexOf(':')+1).trim().split(/\s+/).map(Number);assert(numbers?.length===4&&numbers.every(Number.isFinite),'Unreadable '+label);return numbers;};
      const rotation=Number(lineFor('rot:')?.split(':')[1]);assert(Number.isInteger(rotation),'Unreadable page rotation');
      const base=path.join(root,'page-'+page);
      execFileSync('pdftoppm',['-cropbox','-png','-r','72','-f',String(page),'-l',String(page),'-singlefile',file,base],{timeout:30000,stdio:'pipe'});
      pages.push({media:box('MediaBox:'),crop:box('CropBox:'),rotate:rotation,raster:PNG.sync.read(fs.readFileSync(base+'.png')),
        text:graph.pageText(bytes,page).trim(),urls:urls.filter(row=>row.page===page).map(row=>row.url)});
    }
    return pages;
  } finally {fs.rmSync(root,{recursive:true,force:true});}
}

function verifyPages(actual,expected,baseline) {
  assert(actual.length===expected.length,'Mixed PDF has missing/extra pages');
  for(let i=0;i<expected.length;i++) {
    const {id,turns=0}=expected[i],reference=baseline[id-1],page=actual[i],model=PAGES[id-1];
    assert(reference&&model,'Unknown independent page identity');
    assert(page.rotate===(model.rotate+turns*90)%360,'Mixed PDF has wrong effective rotation');
    for(const key of ['media','crop'])assert(page[key].every((value,index)=>Math.abs(value-model[key][index])<0.02),'Mixed PDF '+key+' changed: expected '+JSON.stringify(model[key])+', actual '+JSON.stringify(page[key]));
    assert(page.text===reference.text,'Mixed PDF text content/association changed');
    assert(JSON.stringify(page.urls)===JSON.stringify(reference.urls),'Mixed PDF annotation action was lost or changed');
    compareRaster(page.raster,rotateRaster(reference.raster,turns),'page '+(i+1)+' identity '+id);
  }
}
module.exports={PAGES,fixturePdf,rotateRaster,compareRaster,inspect,verifyPages};
