#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
"""Detect dead JSP imports/taglibs and undeclared tag prefixes.

Static includes share a translation unit. Dynamic JSP dispatch uses a separate unit.
Every includer's unit is considered when checking a shared fragment. Names in
HTML, strings and Java comments deliberately keep directives live. Checks for
undeclared tag prefixes only consider template markup. Jasper still compiles
every page after cleanup and validates Java, EL and JSP syntax (#5478).

Example (after Maven has compiled the application):
  mvn dependency:build-classpath -DincludeScope=compile -Dmdep.outputFile=target/jsp-classpath.txt
  python3 scripts/check-jsp-directives.py --classpath-file target/jsp-classpath.txt

Exit with 0 for a clean audit, 1 for findings or 2 for unavailable analysis inputs.
"""

import argparse
from collections import Counter, defaultdict
from html import unescape
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from xml.parsers import expat
import zipfile


DIRECTIVE = re.compile(r'<%@\s*(page|tag|attribute|variable|taglib|include)\b.*?%>', re.S)
ATTRIBUTE = re.compile(r'([\w:-]+)\s*=\s*([\"\'])(.*?)\2', re.S)
COMMENT = re.compile(r'<%--.*?--%>', re.S)
TOKEN = re.compile(r'\b[A-Za-z_$][\w$]*\b')
XML_NAMESPACE = re.compile(r'\bxmlns(?::[\w.-]+)?\s*=\s*([\"\'])(.*?)\1', re.S)
JSP_BLOCK = re.compile(r'<%.*?%>', re.S)
QUALIFIED_TAG = re.compile(r'</?([\w.-]+):([\w.-]+)(?=[\s/>])')
DYNAMIC_DISPATCH = re.compile(r'''<jsp:(?:include|forward)\b((?:[^>"']|"[^"]*"|'[^']*')*)>''', re.S)

# Existing Struts 1 markup tracked by #6050. Limit exemptions by file and tag
# name and count closing tags as well. The gate accepts fewer occurrences.
LEGACY_HTML_TAGS = {
    'WEB-INF/jsp/PMmodule/Admin/ProgramEdit/access.jsp': (
        'select', 'options', 'select', 'hidden', 'hidden', 'checkbox', 'cancel',
    ),
    'WEB-INF/jsp/PMmodule/Admin/ProgramEdit/client_status.jsp': ('hidden', 'text', 'cancel'),
    'WEB-INF/jsp/PMmodule/Admin/ProgramEdit/queue.jsp': ('hidden',),
    'WEB-INF/jsp/PMmodule/Admin/ProgramEdit/service_restrictions.jsp': ('text', 'text', 'hidden'),
    'WEB-INF/jsp/PMmodule/Admin/ProgramEdit/staff.jsp': (
        'rewrite', 'hidden', 'hidden', 'select', 'options', 'select', 'cancel',
    ),
    'WEB-INF/jsp/PMmodule/Admin/ProgramEdit/teams.jsp': ('hidden', 'text', 'cancel'),
    'WEB-INF/jsp/PMmodule/Admin/ProgramView/queue.jsp': (
        'hidden', 'hidden', 'radio', 'radio', 'radio', 'radio', 'radio',
    ),
}


def attributes(text):
    return {name: value for name, quote, value in ATTRIBUTE.findall(text)}


def jsp_path(webapp, source, name):
    base = webapp if name.startswith('/') else source.parent
    return (base / name.lstrip('/')).resolve()


def dispatched_pages(template):
    for match in DYNAMIC_DISPATCH.finditer(template):
        name = attributes(match[1]).get('page', '').partition('?')[0]
        if name:
            yield name


def reachable(start, edges):
    seen, pending = set(), [start]
    while pending:
        node = pending.pop()
        if node not in seen:
            seen.add(node)
            pending.extend(edges[node])
    return seen


def check_web_xml(descriptor):
    """Read JSP settings without accepting DTDs or custom entity declarations."""
    parser = expat.ParserCreate(namespace_separator='}')
    xml_value = None

    def start_element(name, attrs):
        nonlocal xml_value
        name = name.rsplit('}', 1)[-1]
        if xml_value is not None:
            raise ValueError('web.xml is-xml configuration must contain only text')
        if name in ('include-prelude', 'include-coda'):
            raise ValueError('implicit web.xml includes need analysis support before this check can run')
        if name == 'is-xml':
            xml_value = []

    def text(data):
        if xml_value is not None:
            xml_value.append(data)

    def end_element(name):
        nonlocal xml_value
        if name.rsplit('}', 1)[-1] == 'is-xml':
            if ''.join(xml_value).strip() in ('true', '1'):
                raise ValueError('XML JSP configuration in web.xml needs analysis support before this check can run')
            xml_value = None

    def reject_dtd_or_entity(*_args):
        raise ValueError('web.xml DTDs and entity declarations are unsupported by the JSP directive audit')

    # Abort at the DOCTYPE opening, before internal declarations can expand or
    # an external subset can be read. Reject entity callbacks independently as
    # defense in depth; this parser never creates an external-entity parser.
    parser.StartDoctypeDeclHandler = reject_dtd_or_entity
    parser.EntityDeclHandler = reject_dtd_or_entity
    parser.ExternalEntityRefHandler = reject_dtd_or_entity
    parser.SkippedEntityHandler = reject_dtd_or_entity
    parser.SetParamEntityParsing(expat.XML_PARAM_ENTITY_PARSING_NEVER)
    parser.StartElementHandler = start_element
    parser.CharacterDataHandler = text
    parser.EndElementHandler = end_element
    try:
        with descriptor.open('rb') as stream:
            parser.ParseFile(stream)
    except expat.ExpatError as error:
        raise ValueError(f'cannot parse JSP configuration in {descriptor}: {error}') from error


def translation_units(webapp):
    """Return each source file, its directive uses and its shared tag prefixes."""
    if not webapp.is_dir():
        raise ValueError(f'webapp does not exist: {webapp}')
    descriptor = webapp / 'WEB-INF/web.xml'
    if descriptor.exists():
        check_web_xml(descriptor)
    texts = {p: p.read_bytes().decode('latin1') for p in webapp.rglob('*')
             if p.suffix in ('.jsp', '.jspf', '.jspx', '.tag', '.tagf', '.tagx')}
    if not texts:
        raise ValueError(f'no JSPs found in {webapp}')
    children, parents = defaultdict(set), defaultdict(set)
    pending = list(texts)
    directives, templates = {}, {}
    while pending:
        path = pending.pop()
        active = COMMENT.sub(lambda m: ' ' * len(m[0]), texts[path])
        # JSP documents can bind the JSP namespace to any prefix, including the
        # default namespace. Do not silently audit them as classic JSP syntax.
        if (path.suffix in ('.jspx', '.tagx')
                or any(unescape(value) == 'http://java.sun.com/JSP/Page'
                       for quote, value in XML_NAMESPACE.findall(active))
                or re.search(r'<jsp:directive\.', active)):
            raise ValueError(f'XML JSP directives need analysis support: {path}')
        directives[path] = [(match, attributes(match[0])) for match in DIRECTIVE.finditer(active)]
        templates[path] = JSP_BLOCK.sub(lambda m: ' ' * len(m[0]), active)
        for match, attrs in directives[path]:
            if match[1] != 'include':
                continue
            name = attrs.get('file')
            if not name:
                raise ValueError(f'include without a literal file: {path}')
            target = jsp_path(webapp, path, name)
            if not target.is_relative_to(webapp) or not target.is_file():
                raise ValueError(f'missing or outside-webapp static include: {path}: {name}')
            if target not in texts:
                texts[target] = target.read_bytes().decode('latin1')
                pending.append(target)
            children[path].add(target)
            parents[target].add(path)
    def directive_body(match):
        # Retain only type-bearing directive attributes, never import entries
        # themselves or non-type literals that could hide an unused import.
        attrs = attributes(match[0])
        if match[1] == 'page':
            return attrs.get('extends', '')
        if match[1] == 'attribute':
            return ' '.join(attrs.get(name, '') for name in
                            ('type', 'deferredValueType', 'deferredMethodSignature'))
        if match[1] == 'variable':
            return attrs.get('variable-class', '')
        return ''

    bodies = {p: DIRECTIVE.sub(directive_body, COMMENT.sub('', data)) for p, data in texts.items()}
    closures = {p: reachable(p, children) for p in texts}
    roots = {p for p in texts if p.suffix in ('.jsp', '.tag') or not parents[p]}
    dispatches = {path: tuple(dispatched_pages(template)) for path, template in templates.items()}
    pending_roots = list(roots)
    while pending_roots:
        root = pending_roots.pop()
        # Relative dispatch uses the executing JSP's path, including for
        # actions written in one of its statically included fragments.
        names = {name for member in closures[root] for name in dispatches[member]}
        for name in names:
            target = jsp_path(webapp, root, name)
            if target in texts and target not in roots:
                roots.add(target)
                pending_roots.append(target)

    declared = {
        p: {attrs['prefix'] for match, attrs in matches if match[1] == 'taglib' and 'prefix' in attrs}
        for p, matches in directives.items()
    }
    prefixes = {p: set().union(*(declared[member] for member in unit)) for p, unit in closures.items()}
    for path, data in texts.items():
        includers = reachable(path, parents)
        unit = set().union(*(closures[p] for p in includers))
        # One use keeps a shared declaration live. A shared use needs its
        # declaration in every unit that compiles the fragment.
        common_prefixes = set.intersection(*(prefixes[p] for p in (roots & includers or {path})))
        yield path, data, '\n'.join(bodies[p] for p in sorted(unit)), directives[path], templates[path], common_prefixes


class ClassIndex:
    def __init__(self):
        self.packages = defaultdict(set)

    def add(self, entry):
        entry = re.sub(r'^META-INF/versions/\d+/', '', entry)
        if not entry.endswith('.class') or entry.startswith('META-INF/') or '/' not in entry:
            return
        package, name = entry[:-6].rsplit('/', 1)
        package = package.replace('/', '.')
        # Member-class imports (Map.Entry and Map.*) are legal too.
        parts = name.split('$')
        for part in parts:
            self.packages[package].add(part)
            package += '.' + part

    def load(self, path):
        if not path.exists():
            raise ValueError(f'missing classpath input: {path}')
        # Maven includes dependency POMs (for example jaxws-ri) in this goal's
        # output. They describe dependencies and cannot contain loadable classes.
        if path.suffix == '.pom':
            return
        if path.is_dir():
            for entry in path.rglob('*.class'):
                self.add(entry.relative_to(path).as_posix())
        else:
            with zipfile.ZipFile(path) as archive:
                for entry in archive.namelist():
                    self.add(entry)

    def load_jdk(self, java_home):
        image = java_home / 'lib/modules'
        jimage = java_home / 'bin/jimage'
        if not image.is_file() or not jimage.is_file():
            raise ValueError(f'need a full JDK with jimage: {java_home}')
        result = subprocess.run([str(jimage), 'list', str(image)], check=True,
                                text=True, capture_output=True)
        for line in result.stdout.splitlines():
            self.add(line.strip())


def inspect(webapp, index):
    webapp = webapp.resolve()
    for path, data, body, directives, template, common_prefixes in translation_units(webapp):
        tokens = set(TOKEN.findall(body))
        for match, attrs in directives:
            line = data.count('\n', 0, match.start()) + 1
            if match[1] in ('page', 'tag'):
                for entry in attrs.get('import', '').split(','):
                    entry = entry.strip()
                    if not entry:
                        continue
                    package, name = entry.rsplit('.', 1)
                    if name == '*':
                        names = index.packages.get(package)
                        if not names:
                            yield path, line, 'unresolvable wildcard import', entry
                        elif not names.intersection(tokens):
                            yield path, line, 'unused wildcard import', entry
                    elif name not in tokens:
                        yield path, line, 'unused import', entry
            elif match[1] == 'taglib' and 'prefix' in attrs:
                prefix = re.escape(attrs['prefix'])
                if not re.search(r'(?:</?\s*' + prefix + r':|\b' + prefix + r'\s*:\s*\w+\s*\()', body):
                    yield path, line, 'unused taglib', attrs['prefix'] + ': ' + attrs.get('uri', '')

        legacy = Counter(LEGACY_HTML_TAGS.get(path.relative_to(webapp).as_posix(), ()))
        for match in QUALIFIED_TAG.finditer(template):
            prefix, tag = match.groups()
            if prefix == 'jsp' or prefix in common_prefixes:
                continue
            if prefix == 'html' and legacy[tag] > 0:
                legacy[tag] -= 1
                continue
            line = data.count('\n', 0, match.start()) + 1
            yield path, line, 'undeclared tag prefix', f'{prefix}:{tag}'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--webapp', type=Path, default=Path('src/main/webapp'))
    parser.add_argument('--classes', type=Path, default=Path('target/classes'))
    parser.add_argument('--classpath-file', required=True, type=Path)
    parser.add_argument('--java-home', type=Path)
    args = parser.parse_args()
    try:
        java_home = args.java_home
        if java_home is None:
            java = shutil.which('java')
            if not java:
                raise ValueError('set JAVA_HOME, --java-home, or put java on PATH')
            java_home = Path(os.environ.get('JAVA_HOME', Path(java).resolve().parent.parent))
        index = ClassIndex()
        index.load_jdk(java_home)
        index.load(args.classes)
        classpath = args.classpath_file.read_text().strip()
        if not classpath:
            raise ValueError('empty dependency classpath')
        for entry in classpath.split(os.pathsep):
            index.load(Path(entry))
        findings = list(inspect(args.webapp, index))
        for path, line, kind, value in findings:
            print(f'{path}:{line}: {kind}: {value}')
        print(f'JSP directive audit: {len(findings)} findings')
        return int(bool(findings))
    except (OSError, ValueError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        print(f'JSP directive audit unavailable: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
