/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {PNG}=require('pngjs');
const {inkCount,signaturePng,assertPdfSelections,acceptanceLedger}=require('./eform-visual-widgets-playwright-checks');
function signature({ink=false,alpha=255,width=255,height=50}={}){const png=new PNG({width,height});png.data.fill(0);if(ink)for(let y=10;y<15;y++)for(let x=10;x<100&&x<width;x++){const i=(y*width+x)*4;png.data.set([0,0,0,alpha],i);}return {png,value:'data:image/png;base64,'+PNG.sync.write(png).toString('base64')};}
test('untouched empty signature is supported only when blank is explicitly expected',()=>{signaturePng('',{blank:true});assert.throws(()=>signaturePng(''));});
test('drawn and cleared PNG representations are distinct',()=>{signaturePng(signature({ink:true}).value);signaturePng(signature().value,{blank:true});assert.throws(()=>signaturePng(signature().value));assert.throws(()=>signaturePng(signature({ink:true}).value,{blank:true}));});
for(const value of ['data:text/html;base64,AAAA','https://clinic.invalid/sign.png','data:image/png;base64,!!!!','data:image/png;base64,AAAA'])test('signature output rejects invalid/unproven representation '+value,()=>assert.throws(()=>signaturePng(value)));
test('tiny signature data cannot substitute a complete canvas',()=>assert.throws(()=>signaturePng(signature({width:1,height:1}).value,{blank:true})));
test('transparent black pixels are not signature ink',()=>assert.equal(inkCount(signature({ink:true,alpha:0}).png),0));
test('signature region counting excludes unrelated image/control pixels',()=>{const png=signature({ink:true}).png;assert.equal(inkCount(png),450);assert.equal(inkCount(png,{x:150,y:0,width:100,height:50}),0);assert.equal(inkCount(png,{x:0,y:0,width:50,height:50}),200);});
test('PDF selection oracle retains multiplicity and excludes words containing X',()=>{assertPdfSelections('X\nX\nEXTRA',2);for(const text of ['','X','X X X','EXTRA'])assert.throws(()=>assertPdfSelections(text,2));});
test('clinical ledger preserves independent storage/reopen/print failures through later successful concerns',async()=>{const ledger=acceptanceLedger();await ledger.check('saved clears',()=>{throw Error('default restored');});await ledger.check('image pixels',()=>{});await ledger.check('reopen',()=>{throw Error('selection restored');});assert.throws(()=>ledger.finish(),error=>error.widgetAcceptance===true&&error.errors.length===2&&/saved clears/.test(error.message)&&/reopen/.test(error.message));});
const {assertPdfControlPositions}=require('./eform-visual-widgets-playwright-checks');
const controls=[{name:'checked',x:100,y:100,width:20,height:20,value:'X'},{name:'cleared',x:180,y:100,width:20,height:20,value:''}];
const mark=(x,y=80)=>({text:'X',xMin:x,yMin:y,xMax:x+7,yMax:y+8});
test('PDF control positions preserve selected and cleared field identities',()=>assertPdfControlPositions({width:612,height:792,words:[mark(78)]},controls));
test('PDF control positions reject swapped fields even when selection count is unchanged',()=>assert.throws(()=>assertPdfControlPositions({width:612,height:792,words:[mark(138)]},controls)));
test('PDF control positions reject displaced marks and unexpected page scaling',()=>{assert.throws(()=>assertPdfControlPositions({width:612,height:792,words:[mark(220)]},controls));assert.throws(()=>assertPdfControlPositions({width:450,height:600,words:[mark(78)]},controls));});
test('PDF positions reject an unmeasurable cleared control rather than calling it empty',()=>{for(const changed of [{width:0},{x:NaN},{x:700}])assert.throws(()=>assertPdfControlPositions({width:612,height:792,words:[mark(78)]},[controls[0],{...controls[1],...changed}]));});

test('explicitly cleared widgets require one exact blank row, distinct from missing, null or duplicate values',()=>{
  const {assertStoredWidgetValues}=require('./eform-visual-widgets-playwright-checks');
  const wanted={cleared:'',selected:'X'};
  assert.doesNotThrow(()=>assertStoredWidgetValues([['cleared',''],['selected','X']],wanted));
  for(const invalid of [[],[['cleared',null]],[['cleared','X']],[['cleared',''],['cleared','']]]) {
    assert.throws(()=>assertStoredWidgetValues([...invalid,['selected','X']],wanted),/Saved widget value\/identity/);
  }
});
