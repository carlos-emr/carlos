/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';

/*
 * A legacy eForm whose layout depends on browser defaults, and the measurements that show whether
 * something CARLOS added to its page restyled it (issue #3944). Shared by
 * eform-bootstrap-isolation-playwright-checks.js (standalone) and
 * eform-bootstrap-print-fidelity-playwright-checks.js (installed application and PDF).
 *
 * The form is in the shape of the OSCAR Galaxy corpus: one authored page div (#page1, which the PDF
 * renderer prints as a page), flow content relying on the body margin, Times, normal line height and
 * content-box sizing, fields absolutely positioned over the page, and class names that coincide with
 * Bootstrap's (.table, .row, .container, .small, .lead). Its own styles come both before and after a
 * head script, because the renderers insert their assets in front of the first head element that is
 * not a title, meta or style, so a form's later styles follow CARLOS's.
 *
 * Every element whose geometry is compared carries data-geom. The MARK* words are located in the
 * downloaded PDF and compared with where the bare form lays them out.
 */

const FORM_TITLE = 'Legacy layout fixture';

/** The marker words whose PDF positions are compared, and what each one is sensitive to. */
const MARKERS = Object.freeze({
  MARKFLOW: 'flow text after a heading: heading size and margins, paragraph margins, line height',
  MARKAFTER: 'text after a padded, bordered input: box-sizing',
  MARKCELL: 'the second cell of a table classed "table": Bootstrap .table width and cell padding',
  MARKABS: 'an absolutely positioned label: a control that nothing should move',
});

/**
 * The template HTML.
 *
 * @param {{ hiddenInputs?: string, bodyEnd?: string, headAssets?: string }} [parts] extra markup the
 *   standalone check adds where a renderer would: hidden inputs inside the form, assets in the head
 *   (after the first styles) and scripts at the end of the body
 * @returns {string} a complete HTML document
 */
function legacyEformHtml({ hiddenInputs = '', bodyEnd = '', headAssets = '' } = {}) {
  return `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>${FORM_TITLE}</title>
<style>
  .fieldLabel { position: absolute; font-size: 9pt; }
  .entry { position: absolute; width: 180px; height: 18px; padding: 1px; border: 1px solid #000; }
  #page1 { position: relative; width: 750px; height: 900px; border: 1px solid #999; page-break-after: always; }
  #BottomButtons { position: absolute; left: 0; top: 920px; }
</style>
<style media="print">.DoNotPrint { display: none; }</style>
${headAssets}<script type="text/javascript">var needToConfirm = false;</script>
<style>
  td.fieldLabel2 { font-size: 10pt; }
  h1.formTitle { font-family: Arial, sans-serif; }
</style>
</head><body>
<form id="legacyForm" name="FormName" method="post" action="">
<div id="page1" data-geom>
<h1 class="formTitle" data-geom id="formTitle">Laboratory Requisition</h1>
<p data-geom id="intro">MARKFLOW Complete every field. Paragraph text keeps the browser's default margins.</p>
<p class="lead" data-geom id="leadText">A paragraph that happens to use the class name lead.</p>
<div class="container" data-geom id="containerBlock">A block that happens to use the class name container.</div>
<div class="row" data-geom id="rowBlock"><span data-geom id="rowChildA">Left</span><span data-geom id="rowChildB">Right</span></div>
<table class="table" border="1" data-geom id="legacyTable"><tr><td class="fieldLabel2" data-geom id="tableCell">Test</td><td data-geom id="tableCell2"><span class="small" data-geom id="smallText">MARKCELL</span></td></tr></table>
<label for="patientName" data-geom id="nameLabel">Patient</label>
<input type="text" id="patientName" name="patientName" value="FAKE-Test" style="width:200px; padding:1px 4px; border:2px solid #333" data-geom><span data-geom id="afterInput">MARKAFTER</span>
<br>
<textarea id="notes" name="notes" rows="2" cols="30" data-geom></textarea>
<select id="priority" name="priority" data-geom><option>Routine</option><option>Urgent</option></select>
<button type="button" id="legacyButton" data-geom>Check all</button>
<span class="fieldLabel" style="left:20px; top:430px" data-geom id="labelHin">Health card</span>
<input class="entry" style="left:120px; top:426px" id="hin" name="hin" value="" data-geom>
<span class="fieldLabel" style="left:300px; top:600px" data-geom id="labelAbs">MARKABS</span>
<span class="fieldLabel" style="left:20px; top:860px" data-geom id="labelSignature">Signature</span>
<input class="entry" style="left:120px; top:856px" id="signatureLine" name="signatureLine" data-geom>
</div>
<div class="DoNotPrint" id="BottomButtons"><input name="SubmitButton" id="SubmitButton" type="submit" value="Submit"></div>
${hiddenInputs}</form>
${bodyEnd}</body></html>`;
}

/** Computed properties compared for every measured element. */
const GEOMETRY_PROPERTIES = Object.freeze(['display', 'position', 'boxSizing', 'width', 'height',
  'marginTop', 'marginRight', 'marginBottom', 'marginLeft',
  'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
  'borderTopWidth', 'borderLeftWidth', 'borderCollapse', 'borderSpacing',
  'fontFamily', 'fontSize', 'fontWeight', 'lineHeight', 'color', 'backgroundColor',
  'textAlign', 'verticalAlign', 'flexWrap']);

/**
 * Box and computed style of the body and every data-geom element, keyed by id. The body's height
 * is left out: the toolbar is appended inside the body, so the body is meant to grow by it.
 */
async function measureEform(page) {
  // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the argument is the fixed GEOMETRY_PROPERTIES list
  return page.evaluate((properties) => {
    const round = (value) => Math.round(value * 100) / 100;
    const describe = (element, withHeight = true) => {
      const rect = element.getBoundingClientRect();
      const style = getComputedStyle(element);
      const box = [rect.left + window.scrollX, rect.top + window.scrollY, rect.width, rect.height].map(round);
      const names = withHeight ? properties : properties.filter((property) => property !== 'height');
      return {
        box: withHeight ? box : box.slice(0, 3),
        style: Object.fromEntries(names.map((property) => [property, style[property]])),
      };
    };
    const result = { body: describe(document.body, false) };
    for (const element of document.querySelectorAll('[data-geom]')) result[element.id] = describe(element);
    return result;
  }, [...GEOMETRY_PROPERTIES]);
}

/** Every difference between two measurements, as readable lines. */
function differences(expected, actual) {
  const lines = [];
  for (const id of Object.keys(expected)) {
    if (!actual[id]) { lines.push(`${id}: missing`); continue; }
    if (expected[id].box.join() !== actual[id].box.join()) {
      lines.push(`${id}: box ${expected[id].box.join(',')} -> ${actual[id].box.join(',')}`);
    }
    for (const [property, value] of Object.entries(expected[id].style)) {
      if (actual[id].style[property] !== value) {
        lines.push(`${id}: ${property} ${value} -> ${actual[id].style[property]}`);
      }
    }
  }
  return lines;
}

/**
 * Where each MARK* word starts, in CSS pixels from the top-left corner of #page1's border box (the
 * origin of the PDF page the renderer prints it as).
 */
async function markerOffsets(page) {
  // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the argument is the fixed MARKERS word list
  return page.evaluate((words) => {
    const origin = document.getElementById('page1').getBoundingClientRect();
    const found = {};
    const walker = document.createTreeWalker(document.getElementById('page1'), NodeFilter.SHOW_TEXT);
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
      for (const word of words) {
        const at = node.data.indexOf(word);
        if (at < 0 || found[word]) continue;
        const range = document.createRange();
        range.setStart(node, at);
        range.setEnd(node, at + word.length);
        const rect = range.getBoundingClientRect();
        found[word] = { x: rect.left - origin.left, y: rect.top - origin.top, height: rect.height };
      }
    }
    return found;
  }, Object.keys(MARKERS));
}

module.exports = { FORM_TITLE, GEOMETRY_PROPERTIES, MARKERS, differences, legacyEformHtml, markerOffsets, measureEform };
