/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {chromium}=require('playwright');
const path=require('node:path');
const root=path.join(__dirname,'..');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_EXECUTABLE_PATH || '/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const page=await browser.newPage();
 await page.setContent('<form id="ticklerAddForm" data-date-error="Bad date" data-time-error="Bad time"><div><div><input id="date" name="serviceDate" class="date-picker required"></div></div><div><div><input id="time" name="serviceTime" class="time-picker required"></div></div><button type="button" id="outside">Outside</button></form>');
 await page.addScriptTag({path:root+'/src/main/webapp/library/flatpickr/flatpickr.min.js'});
 await page.evaluate(()=>{
 window.$=selector=>({ready(){},each(fn){document.querySelectorAll(selector).forEach(e=>fn.call(e));},val(){return document.querySelector(selector).value;},css(){}});
 $.fn={dataTableExt:{afnFiltering:[]},dataTable:{moment(){},ext:{order:{}}}};
 });
 await page.addScriptTag({path:root+'/src/main/webapp/web/dashboard/display/drilldownDisplayController.js'});
 await page.evaluate(()=>initTicklerPickers());
 let assertions=0;
 for(const [value,expected] of [['14:30','2:30 PM'],['10:30 am','10:30 AM'],['2:30 Pm','2:30 PM'],['00:00','12:00 AM'],['23:59','11:59 PM']]){
  await page.locator('#time').fill(value);await page.locator('#outside').click();
  const actual=await page.locator('#time').inputValue();if(actual!==expected)throw Error(value+' normalized to '+actual);assertions++;
 }
 for(const value of ['today','13:75 PM','25:00','10:30 AMjunk']){
  for(const action of ['blur','enter','outside']){
   await page.locator('#time').fill(value);
   if(action==='blur')await page.locator('#time').blur();else if(action==='enter')await page.locator('#time').press('Enter');else await page.locator('#outside').click();
   if(await page.locator('#time').inputValue()!==value)throw Error(action+' erased '+value);
   if(!await page.locator('#time').evaluate(e=>e.validity.customError))throw Error('No visible invalid state');assertions+=2;
  }
 }
 console.log('PASS '+assertions+' real Chromium/Flatpickr assertions');await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
