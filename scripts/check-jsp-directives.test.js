/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { spawnSync } = require('node:child_process');

// Exercise the analyzer with synthetic translation units, independent of Maven.
const cases = String.raw`
import importlib.util, pathlib, tempfile, unittest, zipfile
spec = importlib.util.spec_from_file_location('checker', 'scripts/check-jsp-directives.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

class Directives(unittest.TestCase):
    def audit(self, pages, classes=()):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for name, text in pages.items():
                target = root/name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text)
            index = checker.ClassIndex()
            for name in classes:
                index.add(name)
            return [(p.relative_to(root).as_posix(), kind, value)
                    for p, line, kind, value in checker.inspect(root, index)]

    def test_static_include_shares_imports_in_both_directions(self):
        self.assertEqual(self.audit({
            'page.jsp': '<%@ page import="app.Parent" %><%@ include file="bits/child.jspf" %><%= Child.class %>',
            'bits/child.jspf': '<%@ page import="app.Child" %><%= Parent.class %>',
        }), [])

    def test_fragment_keeps_uses_from_all_parents_and_their_siblings(self):
        self.assertEqual(self.audit({
            'a.jsp': '<%@ include file="shared.jspf" %>',
            'b.jsp': '<%@ include file="shared.jspf" %><%@ include file="sibling.jspf" %>',
            'shared.jspf': '<%@ page import="app.Shared" %><%@ taglib uri="functions" prefix="fn" %>',
            'sibling.jspf': '<%= Shared.class %> $' '{fn:length(value)}',
        }), [])

    def test_dynamic_include_is_a_separate_unit(self):
        findings = self.audit({
            'page.jsp': '<%@ page import="app.Child" %><jsp:include page="child.jsp"/>',
            'child.jsp': '<%= Child.class %>',
        })
        self.assertEqual(findings, [('page.jsp', 'unused import', 'app.Child')])

    def test_directives_and_jsp_comments_are_not_uses(self):
        findings = self.audit({'page.jsp': '''<%@ page import="app.Dead, app.Kept" %>
            <%-- Dead.class <dead:tag/> <%@ include file="absent.jsp" %> --%>
            <%@ taglib uri="dead" prefix="dead" %> Kept'''
        })
        self.assertEqual(findings, [('page.jsp', 'unused import', 'app.Dead'),
                                    ('page.jsp', 'unused taglib', 'dead: dead')])

    def test_wildcards_only_see_the_exact_package(self):
        findings = self.audit({'page.jsp': '<%@ page import="absent.*, app.*, sub.*" %>Child'},
                              ['app/Unused.class', 'sub/Child.class'])
        self.assertEqual(findings, [('page.jsp', 'unresolvable wildcard import', 'absent.*'),
                                    ('page.jsp', 'unused wildcard import', 'app.*')])

    def test_nested_and_multi_release_classes_are_indexed(self):
        self.assertEqual(self.audit({'page.jsp': '<%@ page import="app.Outer.*" %>Inner'},
                                   ['META-INF/versions/25/app/Outer$Inner.class']), [])

    def test_tag_usage_and_el_functions_are_kept(self):
        self.assertEqual(self.audit({'page.jsp': '''<%@ taglib uri="t" prefix="t" %>
            <%@ taglib uri="f" prefix="f" %><t:thing/> $''' + '{f:work(value)}'}), [])

    def test_cycles_terminate_and_absolute_includes_resolve(self):
        self.assertEqual(self.audit({
            'a.jsp': '<%@ include file="/bits/b.jspf" %><%@ page import="app.Used" %>',
            'bits/b.jspf': '<%@ include file="/a.jsp" %>Used',
        }), [])

    def test_unsafe_or_unsupported_inputs_fail_closed(self):
        for pages in ({'a.jsp': '<%@ include file="missing.jspf" %>'},
                      {'a.jsp': '<%@ include file="../../outside.jspf" %>'},
                      {'a.jspx': '<jsp:directive.page import="app.Type"/>'},
                      {'a.jsp': '', 'WEB-INF/web.xml': '<include-prelude>p.jspf</include-prelude>'}):
            with self.subTest(pages=pages), self.assertRaises(ValueError):
                self.audit(pages)

    def test_archive_classes_and_bad_archives(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = pathlib.Path(directory)/'lib.jar'
            with zipfile.ZipFile(archive, 'w') as jar:
                jar.writestr('app/Type.class', b'fake bytecode')
            index = checker.ClassIndex()
            index.load(archive)
            self.assertEqual(index.packages['app'], {'Type'})
            pom = pathlib.Path(directory)/'dependency.pom'
            pom.write_text('<project/>')
            index.load(pom)
            archive.write_bytes(b'broken')
            with self.assertRaises(zipfile.BadZipFile):
                index.load(archive)

unittest.main()
`;

test('JSP audit protects include scope, classpath resolution and taglib usage', () => {
  const result = spawnSync('python3', ['-I', '-c', cases], {
    cwd: path.join(__dirname, '..'), encoding: 'utf8',
  });
  assert.equal(result.status, 0, result.stdout + result.stderr);
});

test('the actual JSP CI shell preserves compilation and classpath failures', () => {
  const workflow = fs.readFileSync(path.join(__dirname, '../.github/workflows/maven-project.yml'), 'utf8');
  const step = workflow.slice(workflow.indexOf('      - name: Compile JSPs'));
  const match = step.match(/docker exec carlos-tomcat-dev bash (-[a-z]+) "([\s\S]*?)\n\s*"/);
  assert.ok(match, 'locate the actual container shell and commands');
  for (const [compilation, classpath, audit, expected, reached] of [
    [17, 0, 0, 17, false], [0, 23, 0, 23, false],
    [0, 0, 29, 29, true], [0, 0, 0, 0, true],
  ]) {
    const stubs = `make() { return ${compilation}; }; mvn() { return ${classpath}; }; `
      + `python3() { echo AUDIT_REACHED; return ${audit}; };\n`;
    const result = spawnSync('bash', [match[1], stubs + match[2]], {encoding: 'utf8'});
    assert.equal(result.status, expected, result.stderr);
    assert.equal(result.stdout.includes('AUDIT_REACHED'), reached);
  }
});
