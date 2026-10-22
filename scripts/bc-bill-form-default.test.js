/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// The BC bill form chooses its billing form (the code table) from default_view, the clinic's Billing Settings and the
// provider's preference. The packaged default_view is the Ontario form code MFP, which no BC install has, so on a new
// BC install none of them named a form, the bill carried none, and the form's reload after choosing the billing
// physician answered HTTP 500 (bean.getBillForm() was null). The page now falls back to the form its list shows
// first, and compares the chosen form null-safely. The live half is every BC billing check that opens the bill form
// (billing-bc-wcb-form, billing-bc-wcb-code-pickers, billing-bc-bill-create-save, ...) on a fresh BC deb install.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const PAGE = path.join(__dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'billing', 'CA', 'BC', 'billingBC.jsp');

test('the BC bill form falls back to an installed billing form when no setting names one', () => {
  const src = fs.readFileSync(PAGE, 'utf8');
  const fallback = /if \(bean\.getBillForm\(\) == null \|\| !billform\.serviceExists\(bean\.getBillForm\(\)\)\) \{\s*BillingFormData\.BillingForm\[\] installedForms = billform\.getFormList\(\);\s*if \(installedForms\.length > 0\) \{\s*bean\.setBillForm\(installedForms\[0\]\.getFormCode\(\)\);/;
  assert.match(src, fallback);
  // It runs after every setting has had its say, and before anything reads the chosen form.
  const at = src.search(fallback);
  assert.ok(at > src.indexOf('bean.setBillForm(defaultBillingForm);'), 'the fallback runs before the configured defaults');
  assert.ok(at < src.indexOf('bean.getBillRegion(), new Date()), demo);'), 'the code table is read before the fallback');
});

test('the Billing form list compares the chosen form null-safely', () => {
  const src = fs.readFileSync(PAGE, 'utf8');
  assert.doesNotMatch(src, /bean\.getBillForm\(\)\.equalsIgnoreCase\(/);
  assert.match(src, /billformlist\[i\]\.getFormCode\(\)\.equalsIgnoreCase\(bean\.getBillForm\(\)\)/);
});
