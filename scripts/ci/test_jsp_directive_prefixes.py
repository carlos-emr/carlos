# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Exercise tag prefixes in classic JSPs with synthetic translation units."""

import importlib.util
import re
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest


CHECKER = Path(__file__).resolve().parents[1] / 'check-jsp-directives.py'
SPEC = importlib.util.spec_from_file_location('check_jsp_directives', CHECKER)
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)

WEBAPP = Path(__file__).resolve().parents[2] / 'src/main/webapp'
RETIRED_ENCOUNTER_FRAGMENTS = (
    'encounter-head.jspf', 'encounter-header-bar.jspf', 'encounter-row-three.jspf')


class JspDirectivePrefixTest(unittest.TestCase):
    def audit(self, pages):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            for name, markup in pages.items():
                source = root / name
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_text(markup, encoding='utf-8')
            return [(path.relative_to(root).as_posix(), line, kind, value)
                    for path, line, kind, value in checker.inspect(root, checker.ClassIndex())]

    def test_undeclared_prefixes_report_the_template_line_numbers(self):
        findings = self.audit({
            'page.jsp': '<%-- A JSP comment may contain\n'
                        '<ignored:tag/> --%>\n'
                        '<% String tag = "<ignored:scriptlet/>"; %>\n'
                        '<jsp:include page="header.jsp"/>\n'
                        '<div><chart-widget:label>Sample\n'
                        '</chart-widget:label></div>',
            'header.jsp': '<span>Header</span>',
        })

        self.assertEqual(findings, [
            ('page.jsp', 5, 'undeclared tag prefix', 'chart-widget:label'),
            ('page.jsp', 6, 'undeclared tag prefix', 'chart-widget:label'),
        ])

    def test_static_include_scope_requires_a_declaration_in_every_includer(self):
        findings = self.audit({
            'a.jsp': '<%@ include file="library.jspf" %><%@ include file="widget.jspf" %>',
            'b.jsp': '<%@ include file="widget.jspf" %>',
            'library.jspf': '<%@ taglib prefix="widget" tagdir="/WEB-INF/tags" %>',
            'widget.jspf': '<widget:label/>',
        })

        self.assertEqual(findings, [('widget.jspf', 1, 'undeclared tag prefix', 'widget:label')])

    def test_static_sibling_and_tagdir_supply_the_prefix(self):
        findings = self.audit({
            'page.jsp': '<%@ include file="library.jspf" %><%@ include file="widget.jspf" %>',
            'library.jspf': '<%@ taglib prefix="widget" tagdir="/WEB-INF/tags" %>',
            'widget.jspf': '<widget:label/>',
        })

        self.assertEqual(findings, [])

    def test_dispatched_fragment_has_an_independent_unit(self):
        for action in ('include', 'forward'):
            with self.subTest(action=action):
                findings = self.audit({
                    'page.jsp': '<%@ taglib uri="widgets" prefix="widget" %>'
                                '<%@ include file="widget.jspf" %>'
                                f'<jsp:{action} page="widget.jspf"/>',
                    'widget.jspf': '<widget:label/>',
                })

                self.assertEqual(findings, [('widget.jspf', 1, 'undeclared tag prefix', 'widget:label')])

    def test_relative_dispatch_in_a_static_fragment_uses_the_root_page_directory(self):
        findings = self.audit({
            'views/page.jsp': '<%@ taglib uri="widgets" prefix="widget" %>'
                              '<%@ include file="label.jspf" %>'
                              '<%@ include file="/shared/dispatch.jspf" %>',
            'shared/dispatch.jspf': '<jsp:include page="label.jspf"/>',
            'views/label.jspf': '<widget:label/>',
        })

        self.assertEqual(findings, [('views/label.jspf', 1, 'undeclared tag prefix', 'widget:label')])

    def test_legacy_exceptions_do_not_hide_new_uses(self):
        legacy_page = 'WEB-INF/jsp/PMmodule/Admin/ProgramEdit/queue.jsp'
        findings = self.audit({
            legacy_page: '<html:hidden/>\n<html:hidden/>\n<html:text/>',
            'new.jsp': '<html:hidden/>',
        })

        self.assertCountEqual(findings, [
            (legacy_page, 2, 'undeclared tag prefix', 'html:hidden'),
            (legacy_page, 3, 'undeclared tag prefix', 'html:text'),
            ('new.jsp', 1, 'undeclared tag prefix', 'html:hidden'),
        ])


class EncounterIncludeFragmentDirectiveTest(unittest.TestCase):
    """#6300 / #6336: the encounter include fragments declare the prefixes they use.

    The fragments are not included by any page today (the classic encounter layout is
    retired), so the production directive audit over the real tree is the only thing
    that translates them as units.
    """

    def test_encounter_include_fragments_declare_every_tag_prefix(self):
        # The wildcard-import legs need the Maven classpath; only prefixes are asserted.
        findings = [(path.relative_to(WEBAPP).as_posix(), line, value)
                    for path, line, kind, value in checker.inspect(WEBAPP, checker.ClassIndex())
                    if kind == 'undeclared tag prefix'
                    and path.relative_to(WEBAPP).as_posix().startswith('WEB-INF/jsp/encounter/includes/')]
        self.assertEqual(findings, [])

    def test_fragments_exist_and_declare_oscar_and_security_taglibs(self):
        directory = WEBAPP / 'WEB-INF/jsp/encounter/includes'
        for name in RETIRED_ENCOUNTER_FRAGMENTS:
            with self.subTest(fragment=name):
                source = (directory / name).read_text(encoding='utf-8')
                self.assertRegex(source, r'<%@\s*taglib\b[^%]*prefix="oscar"')
                if name == 'encounter-row-three.jspf':
                    self.assertRegex(source, r'<%@\s*taglib\b[^%]*prefix="security"')

    def test_fragment_reference_scan_ignores_jsp_comments_and_reports_directive_lines(self):
        markup = """<%-- <%@ include file="encounter-head.jspf" %> --%>
<%--
<jsp:include page="encounter-row-three.jspf"/>
--%>
<%@ include
    file="/WEB-INF/jsp/encounter/includes/encounter-head.jspf" %>
<jsp:include
    page="encounter-header-bar.jspf?mode=fake"/>
"""
        self.assertEqual(list(self.jsp_reference_lines(markup)), [5, 7])

    @staticmethod
    def jsp_reference_lines(text):
        active = checker.COMMENT.sub(lambda match: re.sub(r'[^\n]', ' ', match[0]), text)
        references = []
        for match in checker.DIRECTIVE.finditer(active):
            if match[1] == 'include':
                references.append((match.start(), checker.attributes(match[0]).get('file', '')))
        for match in checker.DYNAMIC_DISPATCH.finditer(active):
            references.append((match.start(), checker.attributes(match[1]).get('page', '')))
        for offset, target in sorted(references):
            path = re.split(r'[?#]', target, maxsplit=1)[0]
            if path.rsplit('/', 1)[-1] in RETIRED_ENCOUNTER_FRAGMENTS:
                yield text.count('\n', 0, offset) + 1

    def test_fragments_are_not_included_by_any_page(self):
        pattern = re.compile(r'(?:include|page|file|forward|result)[^\n]*(?:'
                             + '|'.join(map(re.escape, RETIRED_ENCOUNTER_FRAGMENTS)) + ')')
        references = []
        for root in (WEBAPP, WEBAPP.parent / 'java', WEBAPP.parent / 'resources'):
            for source in root.rglob('*'):
                if source.suffix not in ('.jsp', '.jspf', '.xml', '.tag', '.java') or not source.is_file():
                    continue
                text = source.read_text(encoding='utf-8', errors='replace')
                if source.suffix in ('.jsp', '.jspf', '.tag'):
                    lines = self.jsp_reference_lines(text)
                else:
                    lines = (number for number, line in enumerate(text.splitlines(), 1)
                             if pattern.search(line) and not line.lstrip().startswith(('//', '*', '/*')))
                references.extend(f'{source.relative_to(WEBAPP.parents[1])}:{number}' for number in lines)
        self.assertEqual(references, [], 'a fragment is now referenced; revisit render coverage')


if __name__ == '__main__':
    unittest.main()
