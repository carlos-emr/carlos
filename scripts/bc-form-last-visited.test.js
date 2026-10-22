/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// The BC multi-page forms reopen a saved record on the page it was last saved from: the record's c_lastVisited
// ("pg1", "pg2", ...) is appended to the form's name and that page is included. The value is request data (each page
// posts it back as a hidden field), so the wrapper accepts only the form's own page names. Before, an empty value (a
// record saved without it) made the wrapper include itself until the stack overflowed, and any other value was used
// as part of a WEB-INF include path. BC-AR 2007 and BC-AR 2012 also read c_lastVisited from the original BC-AR's table
// (factory "BCAR"), so a record reopened on whatever page an unrelated BC-AR row with the same id named.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const FORM_DIR = path.join(__dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'form');
const WRAPPERS = [
  { page: 'formbcar.jsp', prefix: 'formbcar', factory: 'BCAR', pages: 3 },
  { page: 'formbcar2007.jsp', prefix: 'formbcar2007', factory: 'BCAR2007', pages: 3 },
  { page: 'formbcar2012.jsp', prefix: 'formbcar2012', factory: 'BCAR2012', pages: 3 },
  { page: 'formbcnewborn.jsp', prefix: 'formbcnewborn', factory: 'BCNewBorn', pages: 3 },
  { page: 'formBCNewBorn2008.jsp', prefix: 'formBCNewBorn2008', factory: 'BCNewBorn2008', pages: 3 },
  { page: 'formbchp.jsp', prefix: 'formbchp', factory: 'BCHP', pages: 2 },
];

for (const wrapper of WRAPPERS) {
  test(`${wrapper.page} reads its own record and reopens only on one of its own pages`, () => {
    const src = fs.readFileSync(path.join(FORM_DIR, wrapper.page), 'utf8');
    assert.match(src, new RegExp(`factory\\("${wrapper.factory}"\\)`), 'the wrapper reads another form\'s table');
    assert.doesNotMatch(src, /getRequestDispatcher\([^;]*props\.getProperty\("c_lastVisited"/,
      'c_lastVisited goes into the include path unchecked');
    const guard = /String lastVisited = props\.getProperty\("c_lastVisited", ""\);\s*if \(!lastVisited\.matches\("([^"]+)"\)\) \{\s*lastVisited = "pg1";\s*\}/.exec(src);
    assert.ok(guard, 'the wrapper does not guard c_lastVisited');
    const accepted = new RegExp(`^${guard[1]}$`);
    for (let n = 1; n <= wrapper.pages; n++) {
      assert.ok(accepted.test(`pg${n}`), `pg${n} is refused`);
      assert.ok(fs.existsSync(path.join(FORM_DIR, `${wrapper.prefix}pg${n}.jsp`)), `${wrapper.prefix}pg${n}.jsp does not exist`);
    }
    for (const value of ['', `pg${wrapper.pages + 1}`, 'pg1/../../admin/admin', '/../../admin/admin', 'PG1', 'pg1 ']) {
      assert.ok(!accepted.test(value), `${JSON.stringify(value)} is accepted`);
    }
    assert.match(src, new RegExp(`getRequestDispatcher\\("/WEB-INF/jsp/form/${wrapper.prefix}" \\+ lastVisited`));
  });
}
