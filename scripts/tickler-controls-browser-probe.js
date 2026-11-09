/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {chromium}=require('playwright');
const path=require('node:path');
const root=path.join(__dirname,'..');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.PLAYWRIGHT_EXECUTABLE_PATH || '/usr/bin/chromium',headless:true,args:['--no-sandbox']});
 const page=await browser.newPage();page.setDefaultTimeout(8000);
 await page.setContent(`<div id="assignTickler" class="modal fade" tabindex="-1"><div class="modal-dialog"><div class="modal-content"><div class="modal-header">Assign Tickler</div><div class="modal-body">
 <form id="ticklerAddForm" data-date-error="Bad date" data-time-error="Bad time">
 <div class="row"><div class="col-6"><label for="date">Date</label><div><div class="input-group"><input type="text" id="date" name="serviceDate" class="date-picker form-control required"><label for="date" class="input-group-text btn">Calendar</label></div></div></div>
 <div class="col-6"><label for="time">Time</label><div><div class="input-group"><input type="text" id="time" name="serviceTime" class="time-picker form-control required"><label for="time" class="input-group-text btn">Clock</label></div></div></div></div>
 <div class="row"><div class="col-12"><select class="form-select"><option>Suggested text</option></select><textarea id="outside" rows="6" class="form-control"></textarea></div></div>
 </form></div><div class="modal-footer"><button type="button">Save</button></div></div></div></div>`);
 await page.addStyleTag({path:root+'/src/main/webapp/library/bootstrap/5.3.8/css/bootstrap.min.css'});
 await page.addStyleTag({path:root+'/src/main/webapp/library/flatpickr/flatpickr.min.css'});
 await page.addScriptTag({path:root+'/src/main/webapp/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js'});
 await page.addScriptTag({path:root+'/src/main/webapp/library/flatpickr/flatpickr.min.js'});
 await page.evaluate(()=>{
 window.$=selector=>({ready(){},each(fn){document.querySelectorAll(selector).forEach(e=>fn.call(e));},val(){return document.querySelector(selector).value;},css(){}});
 $.fn={dataTableExt:{afnFiltering:[]},dataTable:{moment(){},ext:{order:{}}}};
 });
 await page.addScriptTag({path:root+'/src/main/webapp/web/dashboard/display/drilldownDisplayController.js'});
 await page.evaluate(()=>{initTicklerPickers();bootstrap.Modal.getOrCreateInstance(document.getElementById('assignTickler')).show();});
 await page.locator('#assignTickler').waitFor({state:'visible'});
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
 for(const value of ['31/12/2030','12/11/2030','2030-02-30','2030-12-31junk','today']) {
  for(const action of ['Tab','Enter','outside']) {
   await page.locator('#date').fill('2030-12-31');await page.locator('#time').fill('10:30 AM');
   await page.locator('#date').fill('');await page.locator('#date').pressSequentially(value);
   if(action==='outside')await page.locator('.modal-header').click();else await page.locator('#date').press(action);
   await page.locator('#outside').click();
   const actual=await page.locator('#date').inputValue();
   if(actual!==value)throw Error(action+' changed invalid date '+value+' to '+JSON.stringify(actual));
   if(!await page.locator('#date').evaluate(e=>e.validity.customError))throw Error('No visible invalid date state');assertions+=2;
  }
 }
 console.log('PASS '+assertions+' real Chromium/Flatpickr assertions');await browser.close();
})().catch(e=>{console.error(e);process.exit(1);});
