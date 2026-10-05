#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Apply the one reviewed package-only DrugRef datasource XML transformation.

Reject upstream drift rather than silently editing another bean or overriding
operator properties. All bytes outside the inserted reference and factory definitions remain unchanged.
"""
import os
import re
from pathlib import Path
import stat
import sys
import tempfile
import xml.etree.ElementTree as ET

BEANS = '{http://www.springframework.org/schema/beans}'
OPENING = b'<bean id="dataSource" class="com.zaxxer.hikari.HikariDataSource" destroy-method="close">'
EXPECTED = {
    'driverClassName': '${db_driver}',
    'jdbcUrl': '${db_url}?serverTimezone=UTC',
    'autoCommit': 'false',
    'username': '${db_user}',
    'password': '${db_password}',
    'maximumPoolSize': '32',
    'minimumIdle': '2',
    'connectionTimeout': '10000',
}
INSERTION = b'        <property name="dataSourceProperties" ref="carlosDebianMetadataProperties" />\n'
DEFAULTS = Path(__file__).resolve().parent / 'assets/drugref-jdbc-defaults.xml'
BEGIN_DEFAULTS = b'\n    <!-- CARLOS Debian JDBC metadata defaults -->\n'
END_DEFAULTS = b'    <!-- End CARLOS Debian JDBC metadata defaults -->\n'


def shape(element):
    return (element.tag, element.attrib, (element.text or '').strip(),
            [shape(child) for child in element])



def transform(content):
    if b'<!DOCTYPE' in content.upper() or b'<!ENTITY' in content.upper():
        raise ValueError('DrugRef XML must not declare a DTD or entity')
    root = ET.fromstring(content)
    defaults = ET.fromstring(DEFAULTS.read_bytes())
    expected_beans = {element.get('id'): element for element in defaults}
    if len(expected_beans) != 4 or None in expected_beans:
        raise ValueError('Invalid package-owned JDBC default definitions')
    for element in root.iter():
        names = set(re.split(r'[\s,;]+', element.get('name', '')))
        names.update([element.get('id'), element.get('alias')])
        if names.intersection(expected_beans):
            raise ValueError('Package JDBC bean name already exists')
    beans = [element for element in root.iter() if element.get('id') == 'dataSource']
    if len(beans) != 1 or beans[0].tag != BEANS + 'bean':
        raise ValueError('Expected exactly one Spring dataSource bean')
    bean = beans[0]
    if bean.attrib != {'id': 'dataSource', 'class': 'com.zaxxer.hikari.HikariDataSource', 'destroy-method': 'close'}:
        raise ValueError('DrugRef datasource declaration changed')
    properties = list(bean)
    if len(properties) != len(EXPECTED):
        raise ValueError('DrugRef datasource properties changed or already transformed')
    found = {}
    for prop in properties:
        name = prop.get('name')
        if (prop.tag != BEANS + 'property' or len(prop) or name in found
                or set(prop.attrib) != {'name', 'value'}
                or (prop.text or '').strip()):
            raise ValueError('Unexpected DrugRef datasource property structure')
        found[name] = prop.get('value')
    if found != EXPECTED or content.count(OPENING) != 1:
        raise ValueError('DrugRef datasource values or source formatting changed')
    start = content.index(OPENING)
    end = content.index(b'</bean>', start)
    line = content.rfind(b'\n', start, end) + 1
    if not content[end - 4:end] == b'    ' or content[line:end].strip():
        raise ValueError('Unexpected DrugRef datasource closing tag')
    ET.register_namespace('', BEANS[1:-1])
    block = BEGIN_DEFAULTS + b''.join(ET.tostring(element, encoding='utf-8') for element in defaults) + END_DEFAULTS
    close = end + len(b'</bean>')
    result = content[:line] + INSERTION + content[line:close] + block + content[close:]
    # The literal opening could have come from a comment/CDATA decoy rather
    # than the parsed bean. Prove the actual datasource received the property.
    transformed = ET.fromstring(result)
    actual = [element for element in transformed.iter() if element.get('id') == 'dataSource']
    if len(actual) != 1 or len(actual[0]) != len(EXPECTED) + 1:
        raise ValueError('Transform did not add exactly one datasource property')
    additions = [element for element in actual[0] if element.get('name') == 'dataSourceProperties']
    if len(additions) != 1:
        raise ValueError('Transform did not update the actual datasource')
    added = additions[0]
    if (added.tag != BEANS + 'property'
            or added.attrib != {'name': 'dataSourceProperties', 'ref': 'carlosDebianMetadataProperties'}
            or len(added) or (added.text or '').strip()):
        raise ValueError('Transformed datasource property has unexpected semantics')
    untouched = [element for element in actual[0] if element is not added]
    if [shape(element) for element in untouched] != [shape(element) for element in properties]:
        raise ValueError('Transform changed existing datasource properties')
    for name, definition in expected_beans.items():
        matches = [element for element in transformed.iter() if element.get('id') == name]
        if len(matches) != 1 or shape(matches[0]) != shape(definition):
            raise ValueError('Transform did not add the exact JDBC factory definitions')
    return result


def main():
    if len(sys.argv) != 2:
        raise ValueError('Expected one staged spring_config.xml path or --stdin')
    if sys.argv[1] == '--stdin':
        sys.stdout.buffer.write(transform(sys.stdin.buffer.read()))
        return
    path = Path(sys.argv[1])
    if path.is_symlink() or not path.is_file():
        raise ValueError('Expected a regular staged XML file')
    mode = stat.S_IMODE(path.stat().st_mode)
    changed = transform(path.read_bytes())
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix=path.name + '.', delete=False) as output:
            temporary = Path(output.name)
            output.write(changed)
            output.flush()
            os.fsync(output.fileno())
        temporary.chmod(mode)
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, ET.ParseError) as error:
        print('DrugRef JDBC packaging transform refused: ' + str(error), file=sys.stderr)
        sys.exit(1)
