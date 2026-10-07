/* SPDX-License-Identifier: GPL-2.0-or-later */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '..');
const fragment = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/demographic/portalBookingPrompt.jsp'), 'utf8');
test('every booking label exists, with its own text, in all five catalogs', () => {
  const locales = ['en', 'es', 'fr', 'pl', 'pt_BR'];
  const bundles = locales.map(locale => {
    const entries = fs.readFileSync(path.join(root, `src/main/resources/oscarResources_${locale}.properties`), 'utf8')
      .split('\n').filter(line => line.startsWith('portal.booking.'));
    const map = Object.fromEntries(entries.map(line => {
      const index = line.indexOf('='); return [line.slice(0, index), line.slice(index + 1)];
    }));
    assert.equal(entries.length, Object.keys(map).length, 'duplicate booking catalog keys'); return map;
  });
  for (const key of [...fragment.matchAll(/<fmt:message key="(portal\.booking\.[^"]+)"/g)].map(match => match[1])) {
    assert.ok(bundles[0][key], `missing English label ${key}`);
    // Each catalog carries its own translated text; none may be missing, blank or still English.
    bundles.forEach((bundle, index) => {
      assert.ok(bundle[key] && bundle[key].trim(), key);
      if (index > 0) {
        assert.notEqual(bundle[key], bundles[0][key], `${locales[index]} ${key} is still the English text`);
      }
    });
  }
});
test('appointment binds the shared panel to the persisted patient and signals autocomplete changes', () => {
  const source = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/appointment/editappointment.jsp'), 'utf8');
  assert.match(source, /name="portalBookingPatient" value="<%= apptFromRequest\.getDemographicNo\(\) %>"/);
  assert.match(source, /name="portalBookingPatientInput" value="#demographic_no"/);
  assert.equal(source.match(/getElementById\('keyword'\)\.dispatchEvent\(new Event\('change'\)\)/g).length, 2);
});
test('master record includes the same internal panel for its action-resolved patient', () => {
  const source = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/demographic/edit.jsp'), 'utf8');
  assert.match(source, /jsp:include page="\/WEB-INF\/jsp\/demographic\/portalBookingPrompt\.jsp"/);
  assert.match(source, /name="portalBookingPatient" value="<%= demographic_no %>"/);
});
