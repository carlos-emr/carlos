#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
"""Conservatively detect dead JSP imports/taglibs using the build's classpath.

Static includes share a translation unit; dynamic jsp:include pages do not.
Every includer's unit is considered when checking a shared fragment. Names in
HTML, strings and Java comments deliberately count as uses. JSP comments and
directives do not. This is a maintenance check, not a Java/EL type resolver:
Jasper must still compile every page after any cleanup (#5478).

Example (after Maven has compiled the application):
  mvn dependency:build-classpath -DincludeScope=compile -Dmdep.outputFile=target/jsp-classpath.txt
  python3 scripts/check-jsp-directives.py --classpath-file target/jsp-classpath.txt

Exit codes: 0 clean; 1 dead directives; 2 missing/unsupported analysis inputs.
"""

import argparse
from collections import defaultdict
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile


DIRECTIVE = re.compile(r'<%@\s*(page|taglib|include)\b.*?%>', re.S)
ATTRIBUTE = re.compile(r'([\w:]+)\s*=\s*([\"\'])(.*?)\2', re.S)
COMMENT = re.compile(r'<%--.*?--%>', re.S)
TOKEN = re.compile(r'\b[A-Za-z_$][\w$]*\b')


def attributes(text):
    return {name: value for name, quote, value in ATTRIBUTE.findall(text)}


def reachable(start, edges):
    seen, pending = set(), [start]
    while pending:
        node = pending.pop()
        if node not in seen:
            seen.add(node)
            pending.extend(edges[node])
    return seen


def translation_units(webapp):
    """Return each page/fragment and all source that can use its directives."""
    webapp = webapp.resolve()
    if not webapp.is_dir():
        raise ValueError(f'webapp does not exist: {webapp}')
    descriptor = webapp / 'WEB-INF/web.xml'
    if descriptor.exists() and re.search(r'<(?:\w+:)?include-(?:prelude|coda)\b', descriptor.read_text()):
        raise ValueError('implicit web.xml includes need analysis support before this check can run')
    texts = {p: p.read_bytes().decode('latin1') for p in webapp.rglob('*')
             if p.suffix in ('.jsp', '.jspf', '.jspx', '.tag', '.tagf', '.tagx')}
    if not texts:
        raise ValueError(f'no JSPs found in {webapp}')
    children, parents = defaultdict(set), defaultdict(set)
    pending = list(texts)
    while pending:
        path = pending.pop()
        active = COMMENT.sub('', texts[path])
        if re.search(r'<jsp:directive\.', active):
            raise ValueError(f'XML JSP directives need analysis support: {path}')
        for match in DIRECTIVE.finditer(active):
            attrs = attributes(match[0])
            if match[1] != 'include':
                continue
            name = attrs.get('file')
            if not name:
                raise ValueError(f'include without a literal file: {path}')
            target = (webapp / name.lstrip('/') if name.startswith('/') else path.parent / name).resolve()
            if not target.is_relative_to(webapp) or not target.is_file():
                raise ValueError(f'missing or outside-webapp static include: {path}: {name}')
            if target not in texts:
                texts[target] = target.read_bytes().decode('latin1')
                pending.append(target)
            children[path].add(target)
            parents[target].add(path)
    def directive_body(match):
        # Other page attributes may reference an imported type (extends="Base").
        if match[1] == 'page':
            return attributes(match[0]).get('extends', '')
        return ''

    bodies = {p: DIRECTIVE.sub(directive_body, COMMENT.sub('', data)) for p, data in texts.items()}
    closures = {p: reachable(p, children) for p in texts}
    for path, data in texts.items():
        unit = set().union(*(closures[p] for p in reachable(path, parents)))
        yield path, data, '\n'.join(bodies[p] for p in sorted(unit))


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
    for path, data, body in translation_units(webapp):
        tokens = set(TOKEN.findall(body))
        active = COMMENT.sub(lambda m: ' ' * len(m[0]), data)
        for match in DIRECTIVE.finditer(active):
            attrs = attributes(match[0])
            line = data.count('\n', 0, match.start()) + 1
            if match[1] == 'page':
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
        print(f'JSP directive audit: {len(findings)} dead directives')
        return int(bool(findings))
    except (OSError, ValueError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        print(f'JSP directive audit unavailable: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
