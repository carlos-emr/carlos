#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const path = require('node:path');
const {devices} = require('playwright');
const {launchBrowser} = require('./lib/playwright-harness');
const web = path.join(__dirname, '../src/main/webapp');

(async () => {
  const browser = await launchBrowser({headless: true,
    chromePath: process.env.CHROME_PATH || process.env.CHROMIUM_PATH || ''});
  try {
    for (const options of [{}, devices['iPhone 13']]) {
      const context = await browser.newContext(options);
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.stack));
      await page.setContent('<div id="assignTickler"><div class="modal-body"><form id="ticklerAddForm"><div><div>'
        + '<input id="day" class="date-picker required" name="serviceDate"></div></div><div><div>'
        + '<input id="time" class="time-picker required" name="serviceTime"></div></div></form></div></div>'
        + '<button id="outside" style="margin-top:400px">Outside</button><button id="saveTicklerBtn">Save</button>');
      await page.addStyleTag({path: path.join(web, 'library/flatpickr/flatpickr.min.css')});
      await page.addScriptTag({path: path.join(web, 'library/jquery/jquery-3.7.1.min.js')});
      await page.addScriptTag({path: path.join(web, 'library/flatpickr/flatpickr.min.js')});
      // Table plumbing is unrelated to this fixture. Retain the real jQuery event and save path.
      await page.evaluate(() => {
        $.fn.dataTableExt = {afnFiltering: []};
        $.fn.dataTable = {moment() {}, ext: {order: {}}};
        $.fn.DataTable = () => ({on() {return this;}, draw() {return this;}});
        window.ctx = '/carlos';
        window.requests = [];
        $.ajax = request => window.requests.push(request.data);
      });
      await page.addScriptTag({path: path.join(web, 'web/dashboard/display/drilldownDisplayController.js')});
      await page.evaluate(() => new Promise(resolve => $(document).ready(() => {initTicklerPickers(); resolve();})));
      await page.evaluate(() => initTicklerPickers());
      assert.equal(await page.locator('#dayError, #timeError').count(), 2);
      const day = page.locator('#day');
      const time = page.locator('#time');
      assert.equal(await page.locator('.flatpickr-mobile').count(), 0);
      for (const [field, value] of [[day, '31/12/2030'], [day, '12/11/2030'], [day, '2030-02-30'],
        [day, '2030-12-31junk'], [day, 'today'], [time, 'today'], [time, '25:90'], [time, '10:30garbage'], [time, '13:30 PM']]) {
        for (const commit of ['Tab', 'Enter', 'click']) {
          await day.fill('2030-12-31');
          await time.fill('10:30 AM');
          await field.fill('');
          await field.pressSequentially(value);
          if (commit !== 'click') await field.press(commit);
          await page.locator('#outside').click();
          assert.equal(await field.inputValue(), '', `${value} after ${commit}`);
          assert.match(await field.evaluate(input => input.validationMessage), field === day ? /Invalid service date/ : /Invalid service time/, `${value} after ${commit} mobile=${!!options.isMobile}`);
          assert.equal(await field.getAttribute('aria-invalid'), 'true');
          await page.locator('#saveTicklerBtn').click();
          assert.deepEqual(await page.evaluate(() => requests), []);
        }
      }
      // Save while the valid time still has focus, so Save also exercises the blur path.
      await day.fill('');
      await day.pressSequentially('2030-12-31');
      await day.press('Tab');
      await time.fill('');
      await time.pressSequentially('01:30 PM');
      await page.locator('#saveTicklerBtn').click();
      assert.deepEqual(await page.evaluate(() => requests[0]), [
        {name: 'serviceDate', value: '2030-12-31'}, {name: 'serviceTime', value: '1:30 PM'},
        {name: 'method', value: 'saveTickler'},
      ]);
      assert.equal(await day.getAttribute('aria-invalid'), null);
      // A real calendar pick must correct an invalid typed date, including its validation state.
      await day.fill('31/12/2030');
      await page.locator('#outside').click();
      await day.click();
      await page.locator('.flatpickr-calendar.open .flatpickr-day:not(.prevMonthDay):not(.nextMonthDay)').first().click();
      assert.match(await day.inputValue(), /^\d{4}-\d{2}-01$/);
      assert.equal(await day.evaluate(input => input.validationMessage), '');
      // The time control must still support its spinner and emit a parseable one-digit hour.
      await time.click();
      await page.locator('.flatpickr-calendar.open .flatpickr-hour').click();
      await page.locator('.flatpickr-calendar.open .flatpickr-hour').press('ArrowUp');
      await page.locator('#outside').click();
      assert.match(await time.inputValue(), /^2:30 PM$/);
      assert.equal(await time.evaluate(input => input.validationMessage), '');
      const destroyed = await page.evaluate(() => {
        const inputs = [document.getElementById('day'), document.getElementById('time')];
        window.bootstrap = {Modal: {getOrCreateInstance: () => ({show() {}})}};
        $.ajax = request => request.error({status: 500}, 'error', 'fixture refusal');
        sendData('/web/dashboard/display/AssignTickler', '', 'modal');
        return inputs.every(input => input._flatpickr === undefined);
      });
      assert.equal(destroyed, true, 'Failed modal replacement must destroy both pickers');
      assert.match(await page.locator('#assignTickler .modal-body').innerText(), /Request failed/);
      await page.locator('#outside').click();
      assert.equal(await page.locator('#dayError, #timeError').count(), 0);
      assert.deepEqual(errors, []);
      await context.close();
    }
    console.log('Dashboard tickler picker: desktop and mobile typing, blur, Enter, Save and selection passed.');
  } finally {
    await browser.close();
  }
})().catch(error => {console.error(error); process.exitCode = 1;});
