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

    def test_page_superclass_is_a_use_of_an_import(self):
        self.assertEqual(self.audit({'page.jsp': '<%@ page import="app.Base" extends="Base" %>'}), [])

    def test_non_type_page_attributes_do_not_hide_dead_imports(self):
        self.assertEqual(self.audit({'page.jsp': '<%@ page import="app.UTF" contentType="text/html; charset=UTF-8" %>'}),
                         [('page.jsp', 'unused import', 'app.UTF')])

    def test_tag_imports_are_audited_without_counting_their_own_names(self):
        self.assertEqual(self.audit({'widget.tag': '''<%@ tag import="app.Unused, app.Used, absent.*" %>
            <%@ attribute name="Unused" type="java.lang.String" %><%= Used.class %>'''}),
                         [('widget.tag', 'unused import', 'app.Unused'),
                          ('widget.tag', 'unresolvable wildcard import', 'absent.*')])

    def test_tag_type_attributes_and_static_fragments_keep_imports(self):
        self.assertEqual(self.audit({
            'widget.tag': '''<%@ tag import="app.Value, app.Result, app.Argument, app.Shared, app.Variable" %>
                <%@ include file="attributes.tagf" %><%= FragmentType.class %>''',
            'attributes.tagf': '''<%@ tag import="app.FragmentType" %>
                <%@ attribute name="value" type="Value" %>
                <%@ attribute name="deferred" deferredValue="true" deferredValueType="Shared" %>
                <%@ attribute name="method" deferredMethod="true" deferredMethodSignature="Result call(Argument)" %>
                <%@ variable name-given="variable" variable-class="Variable" %>''',
        }), [])

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

    def test_xml_jsp_syntax_fails_closed_for_any_prefix_and_extension(self):
        for filename in ('page.jspx', 'widget.tagx', 'page.jsp', 'widget.tag'):
            for prefix, namespace in (('j:', 'xmlns:j="http://java.sun.com/JSP/Page"'),
                                      ('', "xmlns='http://java.sun.com/JSP/Page'")):
                with self.subTest(filename=filename, namespace=namespace), self.assertRaises(ValueError):
                    self.audit({filename: '<' + prefix + 'root ' + namespace + ' version="2.0">'
                                '<' + prefix + 'directive.page import="app.Unused"/></' + prefix + 'root>'})
        # An XML fragment can have an arbitrary extension when statically included.
        with self.assertRaises(ValueError):
            self.audit({'page.jsp': '<%@ include file="fragment.inc" %>',
                        'fragment.inc': '<j:root xmlns:j="http://java.sun.com/JSP/Page"/>'})

    def test_xml_jsp_extensions_fail_closed_even_without_namespace_declarations(self):
        for filename in ('page.jspx', 'widget.tagx'):
            with self.subTest(filename=filename), self.assertRaises(ValueError):
                self.audit({filename: '<body/>'})

    def test_web_xml_rejects_xml_jsp_configuration_with_namespaces_and_character_references(self):
        for prefix, namespace in (('', 'xmlns="https://jakarta.ee/xml/ns/jakartaee"'),
                                  ('w:', 'xmlns:w="https://jakarta.ee/xml/ns/jakartaee"')):
            for value in ('true', '1', '  &#116;rue  '):
                with self.subTest(prefix=prefix, value=value), self.assertRaises(ValueError):
                    self.audit({'page.jsp': '', 'WEB-INF/web.xml':
                                '<' + prefix + 'web-app ' + namespace + '><' + prefix + 'jsp-config>'
                                '<' + prefix + 'jsp-property-group><' + prefix + 'url-pattern>*.jsp</'
                                + prefix + 'url-pattern><' + prefix + 'is-xml>' + value + '</' + prefix + 'is-xml>'
                                '</' + prefix + 'jsp-property-group></' + prefix + 'jsp-config></'
                                + prefix + 'web-app>'})

    def test_web_xml_comments_and_disabled_xml_configuration_remain_supported(self):
        for value in ('false', '0'):
            with self.subTest(value=value):
                self.assertEqual(self.audit({'page.jsp': '', 'WEB-INF/web.xml':
                    '<web-app><!-- <is-xml>true</is-xml><include-prelude>unused.jspf</include-prelude> -->'
                    '<jsp-config><jsp-property-group><url-pattern>*.jsp</url-pattern>'
                    '<is-xml>' + value + '</is-xml></jsp-property-group></jsp-config></web-app>'}), [])

    def test_malformed_web_xml_fails_closed(self):
        with self.assertRaisesRegex(ValueError, 'cannot parse JSP configuration'):
            self.audit({'page.jsp': '', 'WEB-INF/web.xml': '<web-app><jsp-config></web-app>'})

    def test_xml_namespace_character_references_cannot_bypass_rejection(self):
        for namespace in ('http://java.sun.com/JSP/&#80;age', 'http://java.sun.com/JSP/&#x50;age'):
            with self.subTest(namespace=namespace), self.assertRaises(ValueError):
                self.audit({'page.jsp': '<j:root xmlns:j="' + namespace + '" version="2.0">'
                            '<j:directive.page import="java.util.Date"/></j:root>'})

    def test_html_namespaces_and_jsp_comments_do_not_trigger_xml_rejection(self):
        self.assertEqual(self.audit({'page.jsp': '''<html xmlns="http://www.w3.org/1999/xhtml"
            xmlns:svg="http://www.w3.org/2000/svg"><body/></html>
            <%-- <j:root xmlns:j="http://java.sun.com/JSP/Page"/> --%>'''}), [])

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
