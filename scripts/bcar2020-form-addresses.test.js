/**
 * Source tests for BC-AR 2020's addresses and script order (issue #4919): js/formBCAR2020Record.js and
 * WEB-INF/jsp/form/formBCAR2020pg1.jsp.
 *
 * Every BC-AR 2020 page carries <base href="<context>/">, so the relative addresses the script used
 * ("../form/BCAR2020", "formBCAR2020pg<N>.jsp") resolved outside the application, and page 1 included the
 * measurement dialog, which loads jQuery, after its own jQuery and jQuery UI. Run with:
 *   node --test scripts/bcar2020-form-addresses.test.js
 */

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const WEBAPP = path.join(__dirname, '..', 'src', 'main', 'webapp');
const SCRIPT = fs.readFileSync(path.join(WEBAPP, 'js', 'formBCAR2020Record.js'), 'utf8');
const PAGE_1 = fs.readFileSync(path.join(WEBAPP, 'WEB-INF', 'jsp', 'form', 'formBCAR2020pg1.jsp'), 'utf8');
const ACTION = '/carlos/form/BCAR2020';

/** Loads the page script into a fresh context whose first form has the JSP's absolute action. */
function load() {
  const form = { attributes: { action: ACTION }, target: 'x' };
  Object.defineProperty(form, 'action', {
    get() { return this.attributes.action; },
    set(value) { this.attributes.action = value; },
  });
  form.getAttribute = (name) => form.attributes[name];
  const context = { document: { forms: [form] } };
  vm.createContext(context);
  vm.runInContext(SCRIPT, context);
  return { context, form };
}

test('shouldBuildEveryPageAddress_fromTheFormsOwnAction', () => {
  const { context } = load();
  for (const page of ['1', '2', '3', '4', '5']) {
    assert.equal(context.bcarPageUrl(page), `/carlos/form/formBCAR2020pg${page}`);
  }
  assert.equal(context.bcarPageUrl('6'), '/carlos/form/formBCAR2020Attachments');
});

test('shouldPointSaveBackAtTheFormsOwnAction_afterAPrintChangedIt', () => {
  const { context, form } = load();
  // The print dialog points the form at "?method=print"; Save's reset() must restore the plain action.
  form.action = `${context.bcarFormAction()}?method=print`;
  context.reset();
  assert.equal(form.action, ACTION);
  assert.equal(form.target, '');
});

/** Runs the page's onPageChange with stubbed page state; `changed` is whether the form counts as changed. */
function pageChange(pageNo, changed) {
  const { context, form } = load();
  const calls = [];
  Object.assign(form, {
    method: { value: 'exit' },
    forwardTo: { value: '1' },
    submit() { calls.push(`submit ${form.method.value} ${form.forwardTo.value} ${form.action}`); },
  });
  context.$ = (target) => ({
    hasClass: (name) => name === 'dirty' && changed,
    removeClass: (name) => { calls.push(`removeClass ${name}${target === form ? ' (the form)' : ''}`); },
  });
  context.confirm = () => true;
  context.checkAllDates = () => true;
  context.location = { href: '' };
  Object.assign(context, { demographicNo: '42', formId: '7', provNo: '999998' });
  context.onPageChange(pageNo);
  return { calls, href: context.location.href };
}

test('shouldOpenTheNextPage_atItsRoute_whenNothingChanged', () => {
  const { calls, href } = pageChange('2', false);
  assert.deepEqual(calls, []);
  assert.equal(href, '/carlos/form/formBCAR2020pg2?demographic_no=42&formId=7&provNo=999998');
});

test('shouldSaveFirstThroughTheFormsOwnAction_andClearTheChangedMark_whenThePageChanged', () => {
  const { calls, href } = pageChange('6', true);
  assert.equal(href, '');
  // The changed mark is cleared before the scripted submit, or the browser asks again whether to leave.
  assert.deepEqual(calls, ['removeClass dirty (the form)', `submit save 6 ${ACTION}`]);
});

test('shouldKeepThePrintDialogsChoices_outOfTheUnsavedChangesCheck', () => {
  const { context } = load();
  const calls = [];
  context.$ = (selector) => ({
    val: () => '',
    addClass: (name) => { calls.push(`${selector} addClass ${name}`); },
    dialog: () => { calls.push(`${selector} dialog`); },
  });
  context.dialogs(1);
  assert.ok(calls.indexOf('#print-dialog :input addClass ays-ignore') >= 0, 'the dialog\'s inputs are ignored by jquery.are-you-sure');
});

test('shouldNotUseAddressesRelativeToThePage_inTheScript', () => {
  assert.ok(!SCRIPT.includes('../form/'), 'no "../form/" address is left');
  assert.ok(!/formBCAR2020(pg|Attachments)[^'"\s]*\.jsp/.test(SCRIPT), 'no page address ends in .jsp');
});

test('shouldIncludeTheMeasurementDialog_beforeThePagesOwnJqueryAndJqueryUi', () => {
  const include = PAGE_1.indexOf('<%@ include file="demographicMeasurementModal.jsp" %>');
  const jquery = PAGE_1.indexOf('/library/jquery/jquery-3.7.1.min.js');
  const jqueryUi = PAGE_1.indexOf('/library/jquery/jquery-ui-1.14.2.min.js');
  assert.ok(include > 0 && jquery > 0 && jqueryUi > 0, 'page 1 has the include and both scripts');
  assert.equal(PAGE_1.split('demographicMeasurementModal.jsp').length - 1, 1, 'the dialog is included once');
  assert.ok(include < jquery && include < jqueryUi,
    'the dialog (which loads its own jQuery) comes before the jQuery that jQuery UI and the plugins attach to');
});
