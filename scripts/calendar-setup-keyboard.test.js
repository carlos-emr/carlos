/**
 * Unit tests for the keyboard wiring of src/main/webapp/share/calendar/calendar-setup.js (#5058).
 *
 * The Calendar.setup shim binds Flatpickr to a date box. A keyboard user could not reach most
 * calendar buttons (an <img>, or an <a> with no href) and, with allowInput, the arrow keys stayed in
 * the box. These tests run the real shim in a vm against a small fake DOM and a Flatpickr stub that,
 * like Flatpickr, copies its hook lists when it is created, and check what each key and click does.
 * The real Flatpickr's own day keys (arrows, Enter, Escape) are not re-tested here. Run with:
 *   node --test scripts/calendar-setup-keyboard.test.js
 */

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const CALENDAR = path.join(__dirname, '..', 'src', 'main', 'webapp', 'share', 'calendar');
const SHIM = fs.readFileSync(path.join(CALENDAR, 'calendar-setup.js'), 'utf8');

/** A DOM element with the parts the shim touches. */
function element(document, tagName, attributes = {}, textContent = '') {
  const attrs = new Map(Object.entries(attributes));
  const listeners = {};
  const el = {
    tagName, textContent, value: '',
    hasAttribute: (name) => attrs.has(name),
    getAttribute: (name) => (attrs.has(name) ? attrs.get(name) : null),
    setAttribute: (name, value) => { attrs.set(name, String(value)); },
    addEventListener: (type, fn) => { (listeners[type] ||= []).push(fn); },
    focus: () => { document.activeElement = el; },
    /** Dispatch an event; returns it, with defaultPrevented. */
    fire: (type, props = {}) => {
      const event = { type, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; }, stopPropagation() {}, ...props };
      (listeners[type] || []).forEach((fn) => fn(event));
      return event;
    },
  };
  return el;
}

/**
 * Runs Calendar.setup on box `d` with trigger `dcal`; returns the page.
 * @param {Object} opts trigger: { tagName, attributes, text }; params: extra Calendar.setup params; tt: Calendar._TT
 */
function setUp({ trigger = { tagName: 'IMG' }, params = {}, tt = {}, guardedClose = true, field = true } = {}) {
  const document = { activeElement: null };
  document.body = element(document, 'BODY');
  document.activeElement = document.body;
  const box = element(document, 'INPUT', { type: 'text', id: 'd' });
  const button = element(document, trigger.tagName, { id: 'dcal', ...(trigger.attributes || {}) }, trigger.text || '');
  const other = element(document, 'INPUT', { type: 'text', id: 'other' });
  const days = { selected: element(document, 'SPAN', {}, '5'), today: element(document, 'SPAN', {}, '19') };
  document.getElementById = (id) => ({ d: field ? box : null, dcal: button, other }[id] || null);
  // A trigger with no field gets a stand-in field inserted beside it (#5055).
  const inserted = [];
  document.createElement = (tagName) => {
    const el = element(document, tagName.toUpperCase());
    el.style = {};
    return el;
  };
  button.parentNode = { insertBefore: (el, before) => { inserted.push({ el, before }); } };
  button.nextSibling = other;
  const calendarContainer = {
    querySelector: (selector) => (/\.selected/.test(selector) ? days.selected : /\.today/.test(selector) ? days.today : null),
    contains: (el) => el === days.selected || el === days.today,
  };
  let instance = null;
  const flatpickr = (target, options) => {
    // Like Flatpickr: the hook lists are copied when the instance is created.
    const onClose = [].concat(options.onClose || []).slice();
    instance = {
      input: target, isOpen: false, calendarContainer, options, closes: 0,
      open() { this.isOpen = true; },
      // Real Flatpickr's close() has no re-entry guard: it clears isOpen and fires onClose
      // every time it is called. guardedClose: false models that.
      close() {
        if (guardedClose && !this.isOpen) return;
        this.isOpen = false; this.closes += 1;
        if (this.closes > 50) throw new RangeError('close() re-entered itself');
        onClose.forEach((hook) => hook([], '', instance));
      },
      toggle() { if (this.isOpen) this.close(); else this.open(); },
    };
    return instance;
  };
  flatpickr.parseDate = () => undefined;
  const context = { console, document, flatpickr };
  context.window = context;
  context.Calendar = { _flatpickrReady: true, _pendingSetups: [], _pendingLocaleUrl: null, _FD: null, _flatpickrLocale: null, _TT: tt };
  vm.createContext(context);
  vm.runInContext(SHIM, context);
  context.Calendar.setup({ inputField: 'd', ifFormat: '%Y-%m-%d', button: 'dcal', ...params });
  assert.ok(instance, 'Calendar.setup did not initialise flatpickr');
  return { document, box, button, other, days, inserted, fp: instance, Calendar: context.Calendar };
}

test('shouldMakeAnImgTriggerAFocusableNamedButton_whenItHasNoName', () => {
  const page = setUp({ tt: { CHOOSE_DATE: 'Choisir une date' } });
  assert.equal(page.button.getAttribute('tabindex'), '0');
  assert.equal(page.button.getAttribute('role'), 'button');
  assert.equal(page.button.getAttribute('aria-label'), 'Choisir une date', 'the name comes from the calendar lang file');
});

test('shouldNameTheTriggerInEnglish_whenTheLangFileHasNoString', () => {
  assert.equal(setUp().button.getAttribute('aria-label'), 'Choose a date');
});

test('shouldKeepATriggersOwnName_whenItHasAltTitleOrText', () => {
  for (const trigger of [{ tagName: 'IMG', attributes: { alt: 'Calendar' } }, { tagName: 'IMG', attributes: { title: 'Pick' } },
    { tagName: 'A', text: 'pick' }]) {
    const page = setUp({ trigger });
    assert.equal(page.button.getAttribute('tabindex'), '0', `${trigger.tagName} is focusable`);
    assert.equal(page.button.getAttribute('aria-label'), null, `${trigger.tagName} keeps its own name`);
  }
});

test('shouldLeaveRealControlsFocusAndKeys_asTheyAre', () => {
  for (const trigger of [{ tagName: 'BUTTON', text: 'cal' }, { tagName: 'A', attributes: { href: '#' }, text: 'pick' },
    { tagName: 'INPUT', attributes: { type: 'button', value: '...' } }, { tagName: 'IMG', attributes: { tabindex: '-1' } }]) {
    const page = setUp({ trigger });
    const what = `${trigger.tagName} ${JSON.stringify(trigger.attributes || {})}`;
    assert.equal(page.button.getAttribute('role'), null, `${what} gets no role`);
    assert.equal(page.button.getAttribute('tabindex'), (trigger.attributes || {}).tabindex || null, `${what} keeps its tabindex`);
    assert.equal(page.button.getAttribute('aria-label'), null, `${what} keeps its own name, or stays out of the tab order unnamed`);
    const enter = page.button.fire('keydown', { key: 'Enter' });
    assert.equal(enter.defaultPrevented, false, 'the shim adds no key handler to a control the keyboard already activates');
    assert.equal(page.fp.isOpen, false);
  }
});

test('shouldNameAnIconOnlyButton_withoutChangingItsFocusOrKeys', () => {
  const page = setUp({ trigger: { tagName: 'BUTTON', attributes: { type: 'button' } }, tt: { CHOOSE_DATE: 'Wybierz datę' } });
  assert.equal(page.button.getAttribute('aria-label'), 'Wybierz datę');
  assert.equal(page.button.getAttribute('role'), null);
  assert.equal(page.button.getAttribute('tabindex'), null);
});

test('shouldLeaveAFormFieldTrigger_asItIs', () => {
  const page = setUp({ trigger: { tagName: 'INPUT', attributes: { type: 'text' } } });
  assert.equal(page.button.getAttribute('aria-label'), null);
  assert.equal(page.button.getAttribute('role'), null);
});

test('shouldOpenAndFocusTheChosenDay_whenEnterOrSpaceIsPressedOnAnImgTrigger', () => {
  for (const key of ['Enter', ' ']) {
    const page = setUp();
    page.button.focus();
    const event = page.button.fire('keydown', { key });
    assert.ok(event.defaultPrevented, `${JSON.stringify(key)} is taken by the trigger`);
    assert.equal(page.fp.isOpen, true);
    assert.equal(page.document.activeElement, page.days.selected, 'focus moves to the selected day');
  }
});

test('shouldIgnoreOtherKeys_onTheTrigger', () => {
  const page = setUp();
  const tab = page.button.fire('keydown', { key: 'Tab' });
  assert.equal(tab.defaultPrevented, false);
  assert.equal(page.fp.isOpen, false);
});

test('shouldFocusTheChosenDay_whenARealButtonIsActivatedFromTheKeyboard', () => {
  const page = setUp({ trigger: { tagName: 'BUTTON' } });
  page.button.focus();
  page.button.fire('click', { detail: 0 });
  assert.equal(page.fp.isOpen, true);
  assert.equal(page.document.activeElement, page.days.selected);
});

test('shouldOnlyToggle_whenTheTriggerIsClickedWithTheMouse', () => {
  const page = setUp();
  page.box.focus();
  page.button.fire('click', { detail: 1 });
  assert.equal(page.fp.isOpen, true);
  assert.equal(page.document.activeElement, page.box, 'a mouse click moves no focus into the calendar');
  page.fp.close();
  assert.equal(page.document.activeElement, page.box, 'and closing moves none either');
  page.button.fire('click', { detail: 1 });
  page.button.fire('click', { detail: 1 });
  assert.equal(page.fp.isOpen, false, 'a second click closes it, as before');
});

test('shouldReturnFocusToTheTrigger_whenACalendarOpenedFromItCloses', () => {
  const page = setUp();
  page.button.focus();
  page.button.fire('keydown', { key: 'Enter' });
  // Flatpickr focuses the box, then closes, when a day is picked or Escape is pressed.
  page.box.focus();
  page.fp.close();
  assert.equal(page.document.activeElement, page.button);
});

test('shouldLeaveFocusWhereTheUserMovedIt_whenTheCalendarClosesThen', () => {
  const page = setUp();
  page.button.fire('keydown', { key: 'Enter' });
  page.other.focus();
  page.fp.close();
  assert.equal(page.document.activeElement, page.other);
});

test('shouldStillCallThePagesOnClose_beforeTheFocusReturns', () => {
  const calls = [];
  const page = setUp({ params: { onClose: (cal) => calls.push(typeof cal.hide) } });
  page.button.fire('keydown', { key: 'Enter' });
  page.fp.close();
  assert.deepEqual(calls, ['function'], 'the page\'s onClose still gets the legacy calendar object');
  assert.equal(page.document.activeElement, page.button);
});

test('shouldBindAHiddenStandInAtTheTrigger_whenThereIsNoFieldAndThePageTakesTheDates', () => {
  // billingShortcutPg1.jsp: the trigger's field is a textarea named, not identified, billDate.
  const page = setUp({ field: false, trigger: { tagName: 'A', attributes: { href: '#' }, text: '[Service Date]' },
    params: { multiple: [], onClose: () => {} } });
  assert.equal(page.inserted.length, 1, 'one stand-in field is inserted');
  const { el, before } = page.inserted[0];
  assert.equal(page.fp.input, el, 'Flatpickr is bound to the stand-in, not to the trigger');
  assert.equal(before, page.other, 'the stand-in goes right after the trigger');
  assert.equal(el.tagName, 'INPUT');
  assert.equal(el.style.display, 'none');
  assert.equal(el.getAttribute('aria-hidden'), 'true');
  assert.equal(page.fp.options.positionElement, page.button, 'the calendar opens at the trigger');
  assert.deepEqual([...page.fp.options.ignoredFocusElements], [page.button], 'a press on the trigger does not count as leaving');
  page.button.fire('click', { detail: 1 });
  assert.equal(page.fp.isOpen, true, 'one click on the trigger opens it');
  page.button.fire('click', { detail: 1 });
  assert.equal(page.fp.isOpen, false, 'and the next closes it');
});

test('shouldKeepBindingToTheTrigger_whenThereIsNoFieldAndNoHookForTheDates', () => {
  const page = setUp({ field: false });
  assert.equal(page.inserted.length, 0, 'no stand-in: there would be nowhere to put the pick');
  assert.equal(page.fp.input, page.button);
});

test('shouldCloseOnce_whenThePagesOnCloseEndsWithCalHide', () => {
  // billingShortcutPg1.jsp's onClose ends with the jscalendar idiom cal.hide() (#5055).
  const page = setUp({ guardedClose: false, params: { onClose: (cal) => cal.hide() } });
  page.fp.open();
  page.fp.close();
  assert.equal(page.fp.closes, 1, 'hide() made while closing must not close again');
  assert.equal(page.fp.isOpen, false);
});

test('shouldStillCloseAnOpenCalendar_whenHideIsCalledFromOutsideOnClose', () => {
  let legacy;
  const page = setUp({ guardedClose: false, params: { onClose: (cal) => { legacy = cal; } } });
  page.fp.open();
  page.fp.close();
  page.fp.open();
  legacy.hide();
  assert.equal(page.fp.isOpen, false);
  assert.equal(page.fp.closes, 2);
});

test('shouldMoveIntoTheOpenCalendar_whenArrowDownIsPressedInAnEmptyBox', () => {
  const page = setUp();
  page.box.focus();
  page.fp.open();
  const event = page.box.fire('keydown', { key: 'ArrowDown' });
  assert.ok(event.defaultPrevented);
  assert.equal(page.document.activeElement, page.days.selected);
  page.box.focus();
  page.fp.close();
  assert.equal(page.document.activeElement, page.box, 'a calendar entered from the box gives focus back to the box');
});

test('shouldKeepArrowDownInTheBox_whenItHoldsText', () => {
  const page = setUp();
  page.box.focus();
  page.box.value = '2026-10';
  page.fp.open();
  const event = page.box.fire('keydown', { key: 'ArrowDown' });
  assert.equal(event.defaultPrevented, false);
  assert.equal(page.document.activeElement, page.box);
});

test('shouldOpenAndMoveIntoTheCalendar_whenAltArrowDownIsPressedInTheBox', () => {
  const page = setUp();
  page.box.focus();
  page.box.value = '2026-10-05';
  const event = page.box.fire('keydown', { key: 'ArrowDown', altKey: true });
  assert.ok(event.defaultPrevented);
  assert.equal(page.fp.isOpen, true);
  assert.equal(page.document.activeElement, page.days.selected);
});

test('shouldNotOpenFromTheBox_whenThePageReservesOpeningForItsButton', () => {
  const page = setUp({ params: { clickOpens: false } });
  page.box.focus();
  const event = page.box.fire('keydown', { key: 'ArrowDown', altKey: true });
  assert.equal(event.defaultPrevented, false);
  assert.equal(page.fp.isOpen, false);
});

test('shouldCloseTheCalendarAndKeepFocus_whenEscapeIsPressedInTheBox', () => {
  const page = setUp();
  page.box.focus();
  page.fp.open();
  const event = page.box.fire('keydown', { key: 'Escape' });
  assert.ok(event.defaultPrevented);
  assert.equal(page.fp.isOpen, false);
  assert.equal(page.document.activeElement, page.box);
  const closed = page.box.fire('keydown', { key: 'Escape' });
  assert.equal(closed.defaultPrevented, false, 'Escape in the box with the calendar closed is left to the page');
});

test('shouldNameTheTrigger_inEveryCalendarLangFile', () => {
  const english = /CHOOSE_DATE"\] = "([^"]+)"/.exec(fs.readFileSync(path.join(CALENDAR, 'lang', 'calendar-en.js'), 'utf8'));
  assert.ok(english, 'calendar-en.js has no CHOOSE_DATE string');
  for (const lang of ['es', 'fr', 'pl', 'pt']) {
    const text = /CHOOSE_DATE"\] = "([^"]+)"/.exec(fs.readFileSync(path.join(CALENDAR, 'lang', `calendar-${lang}.js`), 'utf8'));
    assert.ok(text, `calendar-${lang}.js has no CHOOSE_DATE string`);
    assert.notEqual(text[1], english[1], `calendar-${lang}.js has the English text, not a translation`);
  }
});
