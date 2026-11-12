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
 * The pure helpers of lab-to-flowsheet and the wiring of its five entries (the default one and one per pin). The browser flow is proved live; what can
 * drift without a browser is pinned here: how the flowsheet's own target is read, what counts as a link to a lab, and
 * which manifest entry pins which finding at which step.
 */

test('shouldReadTheTargetAsANumber_whateverTheOperatorAndEscaping', () => {
  assert.equal(check.targetOf('fade=[on] header=[A1C] body=[Target &lt;= 7.0%]'), 7);
  assert.equal(check.targetOf('Target <= 7.0%'), 7);
  assert.equal(check.targetOf('body=[Target &amp;lt;= 7.0%]'), 7, 'the tooltip body is encoded twice in the page HTML');
  assert.equal(check.targetOf('Target < 6.5 %'), 6.5);
  assert.equal(check.targetOf('Target ≤ 8%'), 8);
});

test('shouldReadTheTarget_whenTheTooltipIsEncodedTwiceAndOpensWithAWarning', () => {
  // The raw page since #4418: the payload is HTML-encoded, then attribute-encoded; a patient with no A1C gets a warning.
  assert.equal(check.targetOf("fade=[on] header=[A1C] body=[<span style='color:red'>A1C  has never been reviewed </span> </br>Target &amp;lt;= 7.0%]"), 7);
  // The same title read from the DOM attribute, one level unfolded by the browser.
  assert.equal(check.targetOf('fade=[on] header=[A1C] body=[&lt;span style=&#39;color:red&#39;&gt;A1C has never been reviewed&lt;/span&gt; Target &lt;= 6.5%]'), 6.5);
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
  for (const value of ['', 'value', 'fraction', 'time', 'seconds']) assert.doesNotThrow(() => check.validatePin(value));
  assert.throws(() => check.validatePin('link'), /LAB_FLOWSHEET_PIN must be unset or value, fraction, time, seconds, not link/);
});

test('shouldPinEachFindingAtItsOwnStep_inItsOwnManifestEntry', () => {
  const byName = new Map(entries.map((entry) => [entry.name, entry]));
  assert.deepEqual([...byName.keys()].sort(), ['lab-to-flowsheet', 'lab-to-flowsheet-fraction', 'lab-to-flowsheet-long-value',
    'lab-to-flowsheet-observed-time', 'lab-to-flowsheet-upload-time']);
  const expected = {
    'lab-to-flowsheet': { pin: undefined, finding: 258, step: check.STEP.link },
    // Finding 256 is fixed (#4613): its entry keeps the value steps but no longer expects them to fail.
    'lab-to-flowsheet-long-value': { pin: 'value', finding: 256, step: check.STEP.value, fixed: true },
    // Finding 257 is fixed: its pin step is now a plain assertion, so the entry carries no expectedFailure.
    'lab-to-flowsheet-fraction': { pin: 'fraction', fixed: true },
    // Finding 259 is fixed (#4631): its entry keeps the time step but no longer expects it to fail.
    'lab-to-flowsheet-observed-time': { pin: 'time', finding: 259, step: check.STEP.time, fixed: true },
    // Finding 262 is fixed (#4619): its entry keeps the pin step but no longer expects it to fail.
    'lab-to-flowsheet-upload-time': { pin: 'seconds', finding: 262, step: check.STEP.seconds, fixed: true },
  };
  for (const [name, { pin, finding, step, fixed }] of Object.entries(expected)) {
    const entry = byName.get(name);
    if (fixed) {
      assert.equal(entry.expectedFailure, undefined, `${name} still expects the fixed finding ${finding} to fail`);
    } else {
      assert.deepEqual(entry.expectedFailure, { finding, step }, `${name} pins another finding or step`);
    }
    assert.equal(entry.envSet && entry.envSet.LAB_FLOWSHEET_PIN, pin, `${name} selects another pin`);
    assert.ok(entry.env.includes('LAB_FLOWSHEET_PIN') && entry.env.includes('LAB_UPLOAD_DOCUMENT_STORE'), `${name} must list the variables the script reads`);
    assert.deepEqual(entry.provinces, ['ON'], `${name} is Ontario scope`);
    const alias = pkg.scripts[`test:${name}-playwright`];
    assert.ok(alias, `${name} has no npm alias`);
    assert.equal(/LAB_FLOWSHEET_PIN=(\w+)/.exec(alias)?.[1], pin, `${name}'s npm alias selects another pin`);
  }
});

/*
 * Finding 256 cut every result whose first five characters are digits or + , - . / : ; < = > ? to four. The lab the
 * value entry files must carry only such results, each a distinct code, so a measurement can be told apart by code.
 */
test('shouldFileOnlyResultsTheOldFilterCut_onTheValueEntryLab', () => {
  const tests = [check.LONG, ...check.LONG_MORE];
  assert.equal(new Set(tests.map((t) => t.code)).size, tests.length, 'each result has a code of its own');
  for (const t of tests) {
    assert.match(t.value, /^[-+,./0-9:;<=>?]{5,}$/, `${t.code} ${t.value} is a result the old filter cut to four characters`);
  }
});

/*
 * Finding 262 is reached only by a file: Create Lab writes every time with seconds (CMLLabHL7Generator formats 14
 * digits), so the fixture the upload entry sends must carry the 12-digit OBR-7 and nothing else unusual.
 */
test('shouldCarryATwelveDigitObservationTime_inTheUploadedCmlMessage', () => {
  const lab = { accession: 'ML0123ABCD', last: 'FAKE-PW0123456789ABCDEF', test: { code: '3767', name: 'HEMOGLOBIN A1C', value: '6.1', unit: '%', low: '4.0', high: '6.0', flag: 'A' },
    observed: '202608200945' };
  const segments = check.cmlUploadHl7(lab).split('\r');
  assert.equal(segments.at(-1), '', 'every segment ends with the HL7 segment terminator');
  const byName = Object.fromEntries(segments.filter(Boolean).map((segment) => [segment.slice(0, 3), segment.split('|')]));
  assert.deepEqual(Object.keys(byName), ['MSH', 'PID', 'ORC', 'OBR', 'OBX', 'NTE', 'FTS']);
  assert.equal(byName.MSH[1], '^~\\&', 'MSH-2 is the four encoding characters');
  assert.equal(byName.OBR[7], '202608200945', 'OBR-7 is the observation time, 12 digits: no seconds');
  assert.equal(byName.OBR[2], lab.accession);
  assert.equal(byName.ORC[2], lab.accession);
  assert.ok(byName.PID[5].startsWith(`${lab.last}^`), 'PID-5 names the owned patient');
  assert.deepEqual(byName.OBX.slice(1, 8), ['1', 'ST', '3767^HEMOGLOBIN A1C', '^^CHEMISTRY', '6.1', '%', '4.0-6.0']);
});

test('shouldTakeTheObservationTimeAsGiven_whateverItsWidth', () => {
  const lab = (observed) => check.cmlUploadHl7({ accession: 'ML0123ABCD', last: 'FAKE-PW0123456789ABCDEF',
    test: { code: '3767', name: 'HEMOGLOBIN A1C', value: '6.1', unit: '%', low: '4.0', high: '6.0', flag: 'A' }, observed });
  assert.equal(lab('20260820').split('\r')[3].split('|')[7], '20260820');
  assert.equal(lab('20260820094500').split('\r')[3].split('|')[7], '20260820094500');
});
