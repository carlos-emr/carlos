/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const check = require('./lab-to-flowsheet-playwright-checks');

const ROOT = path.join(__dirname, '..');
const manifest = JSON.parse(fs.readFileSync(path.join(ROOT, 'scripts', 'playwright-suite.json'), 'utf8'));
const pkg = JSON.parse(fs.readFileSync(path.join(ROOT, 'package.json'), 'utf8'));
const entries = manifest.checks.filter((entry) => entry.script === 'scripts/lab-to-flowsheet-playwright-checks.js');

/*
 * The pure helpers of lab-to-flowsheet and the wiring of its three entries. The browser flow is proved live; what can
 * drift without a browser is pinned here: how the flowsheet's own target is read, what counts as a link to a lab, and
 * which manifest entry pins which finding at which step.
 */

test('shouldReadTheTargetAsANumber_whateverTheOperatorAndEscaping', () => {
  assert.equal(check.targetOf('fade=[on] header=[A1C] body=[Target &lt;= 7.0%]'), 7);
  assert.equal(check.targetOf('Target <= 7.0%'), 7);
  assert.equal(check.targetOf('Target < 6.5 %'), 6.5);
  assert.equal(check.targetOf('Target ≤ 8%'), 8);
});

test('shouldReadNoTarget_whenTheGuidelineStatesNone', () => {
  assert.equal(check.targetOf(''), null);
  assert.equal(check.targetOf(null), null);
  assert.equal(check.targetOf('fade=[on] header=[A1C] body=[Review twice a year]'), null);
  assert.equal(check.targetOf('Target > 7'), null);
});

test('shouldFindTheA1cTitle_whetherOrNotTheHeadingCarriesAColour', () => {
  const page = (attributes) => `<div class="preventionSection" id="BP"  style="overflow: auto;"><p class="noborder"  title="fade=[on] header=[BP] body=[Target &lt; 130/80]"></p></div>
<div class="preventionSection" id="A1C"  style="overflow: auto;">
  <div class="headPrevention"><p class="noborder" ${attributes} title="fade=[on] header=[A1C] body=[Target &lt;= 7.0%]"  >`;
  assert.equal(check.a1cItemTitle(page('')), 'fade=[on] header=[A1C] body=[Target &lt;= 7.0%]');
  assert.equal(check.a1cItemTitle(page('style="background-color: yellow"')), 'fade=[on] header=[A1C] body=[Target &lt;= 7.0%]');
  assert.equal(check.a1cItemTitle('<div id="BP"></div>'), null);
  assert.equal(check.a1cItemTitle(undefined), null);
});

test('shouldRecogniseALinkToTheLab_inAnOnclickAnHrefAndATitle', () => {
  const display = '/carlos/lab/CA/ALL/ViewLabDisplay?segmentID=216&amp;providerNo=999998';
  assert.ok(check.linksToLab(`<div onclick="popupPage(700,960,'lab','${display}')">`, '216'));
  assert.ok(check.linksToLab(`<a href="${display}">`, 216));
  assert.ok(check.linksToLab('<a href="/carlos/lab/CA/ALL/ViewLabDisplayAjax?segmentID=216">', '216'));
  assert.ok(check.linksToLab('<p title="body=[<a href=\'/carlos/lab/CA/ALL/labDisplay.jsp?providerNo=1&segmentID=216\'>]">', '216'));
});

test('shouldNotTakeAMeasurementIdForALink_orOneLabNumberForAnother', () => {
  const row = '<div class="preventionProcedure" onclick="javascript:fsPopup(760,670,\'/carlos/encounter/oscarMeasurements/ViewAddMeasurementData?measurement=A1C&amp;id=216&amp;demographic_no=3712&amp;template=diab2\',\'addMeasurementData\')">';
  assert.equal(check.linksToLab(row, '216'), false, 'the measurement id 216 is not lab 216');
  assert.equal(check.linksToLab('<a href="/carlos/lab/CA/ALL/ViewLabDisplay?segmentID=2161">', '216'), false, 'lab 2161 is not lab 216');
  assert.equal(check.linksToLab('<a href="/carlos/lab/CA/ALL/ViewLabDisplay?segmentID=21">', '216'), false, 'lab 21 is not lab 216');
  assert.equal(check.linksToLab('<a href="/carlos/lab/CA/ALL/ViewLabDisplay?segmentID=216">', ''), false);
  assert.equal(check.linksToLab(null, '216'), false);
});

test('shouldAcceptEveryDocumentedPin_andRefuseAnyOther', () => {
  for (const value of ['', 'value', 'fraction', 'time']) assert.doesNotThrow(() => check.validatePin(value));
  assert.throws(() => check.validatePin('link'), /LAB_FLOWSHEET_PIN must be unset or value, fraction, time, not link/);
});

test('shouldPinEachFindingAtItsOwnStep_inItsOwnManifestEntry', () => {
  const byName = new Map(entries.map((entry) => [entry.name, entry]));
  assert.deepEqual([...byName.keys()].sort(), ['lab-to-flowsheet', 'lab-to-flowsheet-fraction', 'lab-to-flowsheet-long-value', 'lab-to-flowsheet-observed-time']);
  const expected = {
    'lab-to-flowsheet': { pin: undefined, finding: 258, step: check.STEP.link },
    'lab-to-flowsheet-long-value': { pin: 'value', finding: 256, step: check.STEP.value },
    'lab-to-flowsheet-fraction': { pin: 'fraction', finding: 257, step: check.STEP.fraction },
    'lab-to-flowsheet-observed-time': { pin: 'time', finding: 259, step: check.STEP.time },
  };
  for (const [name, { pin, finding, step }] of Object.entries(expected)) {
    const entry = byName.get(name);
    assert.deepEqual(entry.expectedFailure, { finding, step }, `${name} pins another finding or step`);
    assert.equal(entry.envSet && entry.envSet.LAB_FLOWSHEET_PIN, pin, `${name} selects another pin`);
    assert.ok(entry.env.includes('LAB_FLOWSHEET_PIN') && entry.env.includes('LAB_UPLOAD_DOCUMENT_STORE'), `${name} must list the variables the script reads`);
    assert.deepEqual(entry.provinces, ['ON'], `${name} is Ontario scope`);
    const alias = pkg.scripts[`test:${name}-playwright`];
    assert.ok(alias, `${name} has no npm alias`);
    assert.equal(/LAB_FLOWSHEET_PIN=(\w+)/.exec(alias)?.[1], pin, `${name}'s npm alias selects another pin`);
  }
});
