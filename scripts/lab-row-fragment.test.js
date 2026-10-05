/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const web = path.join(__dirname, '../src/main/webapp');

test('the injected lab row initializes result and test-name tooltips with the shipped library', () => {
  const element = title => ({ title, style: {}, appendChild() {}, addEventListener() {} });
  const nodes = {
    preventionProcedure00_5: element('fade=[on] header=[137] body=[g/L 120 - 160]'),
    ahead0_5: element('fade=[on] header=[HEMOGLOBIN] body=[]'),
  };
  const rounded = [];
  const context = { window: { addEventListener() {} }, document: {
    addEventListener() {}, createElement: () => element(''), getElementById: id => nodes[id],
  }, Rounded: selector => rounded.push(selector) };
  vm.createContext(context);
  vm.runInContext(fs.readFileSync(path.join(web, 'share/javascript/boxover.js'), 'utf8'), context);
  const jsp = fs.readFileSync(path.join(web, 'WEB-INF/jsp/lab/DisplayLabValue.jsp'), 'utf8');
  // Render the fragment's actual inline script for one result and a deterministic row ID.
  const script = jsp.match(/<script\b[^>]*>([\s\S]*?)<\/script\b[^>]*>/i)[1]
    .replace(/<%=\s*""\+k\+""\+ran\s*%>/g, '00_5')
    .replace(/<%=ran%>/g, '0_5').replace(/<%(?![=!])[\s\S]*?%>/g, '');
  vm.runInContext(script, context);
  assert.equal(nodes.preventionProcedure00_5.boHDR, '137');
  assert.equal(nodes.preventionProcedure00_5.boBDY, 'g/L 120 - 160');
  assert.equal(nodes.ahead0_5.boHDR, 'HEMOGLOBIN');
  assert.equal(nodes.preventionProcedure00_5.hasbox, 1);
  assert.equal(nodes.ahead0_5.hasbox, 1);
  assert.deepEqual(rounded, ['div#preventionProcedure00_5', 'div#headPrevention0_5']);
});

test('the row loading indicator names an image included in the web application', () => {
  const jsp = fs.readFileSync(path.join(web, 'WEB-INF/jsp/lab/CumulativeLabValues.jsp'), 'utf8');
  const asset = jsp.match(/img\.setAttribute\('src', '\$\{carlos:forJavaScript\(pageContext\.request\.contextPath\)\}([^']+)'\)/)[1];
  assert.ok(fs.statSync(path.join(web, asset)).isFile(), `Missing row loading image: ${asset}`);
});
