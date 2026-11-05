/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #5174: retain versioned provincial layouts without hiding new forms or security findings.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { xml2js } = require('xml-js');

const root = path.resolve(__dirname, '..');
const formDir = 'src/main/webapp/WEB-INF/jsp/form/';
const properties = xml2js(fs.readFileSync(path.join(root, 'pom.xml'), 'utf8'), { compact: true })
  .project.properties;
const property = (name) => properties[name]?._text?.trim() || '';
const list = (name) => property(name).split(',').map(value => value.trim()).filter(Boolean);
const criteria = list('sonar.issue.ignore.multicriteria').map(id => ({
  id,
  rule: property(`sonar.issue.ignore.multicriteria.${id}.ruleKey`),
  resource: property(`sonar.issue.ignore.multicriteria.${id}.resourceKey`),
}));

/** Match Sonar's case-sensitive *, ** and ? against a project-relative path. */
function matches(pattern, file) {
  const tokens = pattern.match(/\*\*\/|\*\*|\*|\?|[^*?]+/g) || [];
  const regex = tokens.map(token => {
    if (token === '**/') return '(?:.*/)?';
    if (token === '**') return '.*';
    if (token === '*') return '[^/]*';
    if (token === '?') return '[^/]';
    return token.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }).join('');
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- Test-only matcher over committed POM globs and local fixture paths; literal regex characters are escaped above, and no application or request input reaches it.
  return new RegExp(`^${regex}$`).test(file);
}

const legacyPages = [
  'formBCNewBorn2008.jsp', 'formBCNewBorn2008pg1.jsp', 'formBCNewBorn2008pg2.jsp',
  'formBCNewBorn2008pg3.jsp', 'formbcar.jsp', 'formbcar2007.jsp', 'formbcar2007pg1.jsp',
  'formbcar2007pg2.jsp', 'formbcar2007pg3.jsp', 'formbcar2012.jsp', 'formbcar2012pg1.jsp',
  'formbcar2012pg2.jsp', 'formbcar2012pg3.jsp', 'formbcarpg1.jsp', 'formbcarpg1namepopup.jsp',
  'formbcarpg2.jsp', 'formbcarpg3.jsp', 'formbcbirthsummo.jsp', 'formbcbirthsummo2008.jsp',
  'formbcclientchartchecklist.jsp', 'formbchp.jsp', 'formbchppg1.jsp', 'formbchppg2.jsp',
  'formbcinr.jsp', 'formbcnewborn.jsp', 'formbcnewbornpg1.jsp', 'formbcnewbornpg2.jsp',
  'formbcnewbornpg3.jsp',
].map(name => formDir + name);
const markupRules = ['Web:InputWithoutLabelCheck', 'Web:UnsupportedTagsInHtml5Check'];
const cpd = list('sonar.cpd.exclusions');
const excluded = (file) => list('sonar.exclusions').some(pattern => matches(pattern, file));
const inSources = (file) => list('sonar.sources').some(source => file === source || file.startsWith(`${source}/`));
const ignoredRules = (file) => [...new Set(criteria.filter(entry => matches(entry.resource, file))
  .map(entry => entry.rule))].sort();

test('legacy BC duplication exclusions retain only the two approved form families and reporting data', () => {
  assert.deepEqual(cpd, [
    'src/main/resources/db/reporting/**', `${formDir}formbc*.jsp`,
    `${formDir}formBCNewBorn2008*.jsp`,
  ]);
  const actual = fs.readdirSync(path.join(root, formDir)).map(name => formDir + name)
    .filter(file => cpd.some(pattern => matches(pattern, file)));
  assert.deepEqual(actual.sort(), [...legacyPages].sort(),
    'A new matching page needs an explicit scope review before inheriting these exclusions');
});

test('legacy BC markup criteria suppress exactly the two approved rules on the two approved families', () => {
  assert.deepEqual(criteria.filter(entry => entry.id.startsWith('bcForm')), [
    { id: 'bcFormLabel', rule: markupRules[0], resource: '**/WEB-INF/jsp/form/formbc*.jsp' },
    { id: 'bcFormLabel2008', rule: markupRules[0], resource: '**/WEB-INF/jsp/form/formBCNewBorn2008*.jsp' },
    { id: 'bcFormFont', rule: markupRules[1], resource: '**/WEB-INF/jsp/form/formbc*.jsp' },
    { id: 'bcFormFont2008', rule: markupRules[1], resource: '**/WEB-INF/jsp/form/formBCNewBorn2008*.jsp' },
  ]);
  for (const file of legacyPages) {
    assert.ok(fs.existsSync(path.join(root, file)), file);
    assert.equal(inSources(file), true, `${file} must remain in sonar.sources`);
    const inclusions = list('sonar.inclusions');
    assert.ok(inclusions.length === 0 || inclusions.some(pattern => matches(pattern, file)),
      `${file} must remain inside any configured sonar.inclusions`);
    assert.equal(excluded(file), false, `${file} must still be analyzed`);
    assert.deepEqual(ignoredRules(file), [...markupRules].sort(),
      `${file}: security and every other rule must remain enabled`);
  }
});

test('BC-AR 2020 and unrelated JSPs stay outside both legacy BC exclusions', () => {
  const modernPages = fs.readdirSync(path.join(root, formDir))
    .filter(name => name.startsWith('formBCAR2020')).map(name => formDir + name);
  assert.ok(modernPages.length > 0, 'Exercise the real BC-AR 2020 pages');
  const controls = [
    ...modernPages, `${formDir}formRourke2024.jsp`, `${formDir}formBCNewBorn2026pg1.jsp`,
    `${formDir}formBcNewBorn2008pg1.jsp`, 'src/main/webapp/unrelated/formbcnewbornpg1.jsp',
    `${formDir}nested/formbcnewbornpg1.jsp`,
  ];
  for (const file of controls) {
    assert.equal(excluded(file), false, file);
    assert.equal(cpd.some(pattern => matches(pattern, file)), false, file);
    assert.deepEqual(ignoredRules(file), [], file);
  }
});
