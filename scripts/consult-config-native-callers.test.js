/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const root = path.resolve(__dirname, '..');
const read = (name) => fs.readFileSync(path.join(root, name), 'utf8');
const jspRoot = 'src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/config/';
const forms = [
  ['AddService', 'AddService'], ['DeleteServices', 'DelService'],
  ['EnableRequestResponse', 'EnableConRequestResponse'],
  ['DisplayService', 'UpdateServiceSpecialists'], ['DisplayInstitution', 'UpdateInstitutionDepartment'],
  ['AddSpecialist', 'AddSpecialist'], ['AddInstitution', 'AddInstitution'], ['AddDepartment', 'AddDepartment'],
  ['EditSpecialists', 'EditSpecialists'], ['EditInstitutions', 'EditInstitutions'], ['EditDepartments', 'EditDepartments'],
];

// These are the actual native forms, rather than synthetic request builders. No GET write caller needs migration.
for (const [jsp, action] of forms) {
  test(`${jsp}'s configuration write uses a native POST to ${action}`, () => {
    const source = read(`${jspRoot}${jsp}.jsp`);
    const matching = [...source.matchAll(/<form\b[^>]*>/gi)]
      .map(([form]) => form)
      .filter((form) => form.includes(`/encounter/${action}"`));
    assert.equal(matching.length, 1, `${jsp} must have exactly one native ${action} form`);
    assert.match(matching[0], /\bmethod\s*=\s*"post"/i);
  });
}

for (const [editor, parameter] of [['Specialists', 'specId'], ['Institutions', 'id'], ['Departments', 'id']]) {
  test(`the ${editor} editor keeps its native GET prefill link`, () => {
    const source = read(`${jspRoot}Edit${editor}.jsp`);
    assert.ok(source.includes(`"/encounter/Edit${editor}?${parameter}="`));
    assert.match(source, /<a\s+href="<%=\s*url\s*%>"/);
    assert.match(source, /objectName="_admin,_admin\.consult"\s+rights="w"/);
  });
}

test('native configuration POSTs remain behind CSRFGuard before Struts execution', () => {
  const webXml = read('src/main/webapp/WEB-INF/web.xml');
  const mappings = [...webXml.matchAll(/<filter-mapping>([\s\S]*?)<\/filter-mapping>/g)]
    .map(([, text]) => text);
  const csrf = mappings.findIndex((text) => /<filter-name>CSRFGuard<\/filter-name>/.test(text));
  const execution = mappings.findIndex((text) => /<filter-name>struts2-execute<\/filter-name>/.test(text));
  assert.ok(csrf >= 0 && execution > csrf);
  assert.match(mappings[csrf], /<url-pattern>\/\*<\/url-pattern>/);
  assert.doesNotMatch(mappings[csrf], /<dispatcher>/, 'default REQUEST validation must still apply');
  const injection = mappings.find((text) => /<filter-name>CsrfGuardScriptInjectionFilter<\/filter-name>/.test(text));
  assert.match(injection, /<dispatcher>FORWARD<\/dispatcher>/);
});

test('configuration privilege refusals use the existing HTTP 403 Struts result', () => {
  const routes = read('src/main/webapp/WEB-INF/classes/struts-encounter.xml');
  assert.match(routes, /<exception-mapping\s+exception="java\.lang\.SecurityException"\s+result="securityError"\s*\/>/);
  assert.match(routes, /<result\s+name="securityError">\/WEB-INF\/jsp\/error\/securityError\.jsp<\/result>/);
  assert.match(read('src/main/webapp/WEB-INF/jsp/error/securityError.jsp'), /response\.setStatus\(jakarta\.servlet\.http\.HttpServletResponse\.SC_FORBIDDEN\)/);
});

test('the ten repaired configuration findings are ordinary assertions with unchanged claim partitions', () => {
  const suite = require('./playwright-suite.json');
  const sweep = require('./get-reject-consult-config-playwright-checks');
  const checks = suite.checks.filter((entry) => entry.script === 'scripts/get-reject-consult-config-playwright-checks.js');
  assert.equal(checks.length, 11);
  assert.ok(checks.every((entry) => entry.expectedFailure === undefined));
  assert.deepEqual(sweep.PINNED, {});
  const claimed = checks.flatMap((entry) => sweep.validatePin(entry.envSet || {}));
  const universe = sweep.ACTIONS.flatMap(({ key }) => ['page', 'get', 'restricted', 'doctor'].map((concern) => `${key}.${concern}`));
  assert.deepEqual([...claimed].sort(), [...universe].sort());
});
