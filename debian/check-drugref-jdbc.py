#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Reject staged DrugRef WARs without the MariaDB migration and legacy compatibility.

The upstream application now owns its JDBC configuration. In particular, do not
inject the former MySQL-only metadata parser or rewrite private operator files.
This check applies to both source builds and explicitly supplied DRUGREF_WARs.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

BEANS = '{http://www.springframework.org/schema/beans}'
PACKAGE = 'io.github.carlos_emr.drugref2026.util.'
CONNECTOR = 'mariadb-java-client-3.5.11.jar'
MULTI_RELEASE = re.compile(r'^META-INF/versions/[0-9]+/')


def verify(webapp):
    classes = webapp / 'WEB-INF/classes'
    configuration = classes / 'spring_config.xml'
    content = configuration.read_bytes()
    if b'\x00' in content or b'<!DOCTYPE' in content or b'<!ENTITY' in content:
        raise ValueError('DrugRef Spring configuration must not contain NUL bytes or XML entities')
    root = ET.fromstring(content)
    sources = [element for element in root.iter() if element.get('id') == 'dataSource']
    if len(sources) != 1:
        raise ValueError('Expected exactly one DrugRef datasource')
    source = sources[0]
    if (source.tag != BEANS + 'bean'
            or source.get('class') != PACKAGE + 'DrugrefDataSource'
            or source.get('destroy-method') != 'close'):
        raise ValueError('DrugRef requires the MariaDB-compatible DrugrefDataSource')
    properties = {}
    for prop in source:
        if prop.tag != BEANS + 'property' or prop.get('name') in properties:
            raise ValueError('Ambiguous DrugRef datasource property')
        properties[prop.get('name')] = prop
    for name, value in {'driverClassName': '${db_driver}', 'jdbcUrl': '${db_url}',
                        'username': '${db_user}', 'password': '${db_password}'}.items():
        prop = properties.get(name)
        if (prop is None or prop.attrib != {'name': name, 'value': value}
                or len(prop) or (prop.text or '').strip()):
            raise ValueError('DrugRef datasource does not preserve operator ' + name)
    if any(element.get('class', '').startswith('com.mysql.') for element in root.iter()):
        raise ValueError('DrugRef still refers to MySQL Connector/J')
    for name in ('DrugrefDataSource', 'MariaDbJdbcCompatibility', 'MariaDbCompatibilityDriver',
                 'MariaDbPreferredTlsPlugin'):
        if not (classes / (PACKAGE.replace('.', '/') + name + '.class')).is_file():
            raise ValueError('DrugRef JDBC compatibility class is missing: ' + name)
    service = classes / 'META-INF/services/org.mariadb.jdbc.plugin.TlsSocketPlugin'
    providers = [line.split('#', 1)[0].strip() for line in service.read_text(encoding='utf-8').splitlines()]
    if [provider for provider in providers if provider] != [PACKAGE + 'MariaDbPreferredTlsPlugin']:
        raise ValueError('DrugRef MariaDB TLS compatibility service registration is missing or ambiguous')

    libraries = webapp / 'WEB-INF/lib'
    connectors = list(libraries.glob('mariadb-java-client-*.jar'))
    if len(connectors) != 1 or connectors[0].name != CONNECTOR:
        raise ValueError('DrugRef must ship exactly ' + CONNECTOR)
    if list(libraries.glob('mysql-connector-*.jar')):
        raise ValueError('DrugRef still ships MySQL Connector/J')
    for library in libraries.iterdir():
        if library.suffix.lower() != '.jar':
            continue
        with zipfile.ZipFile(library) as archive:
            if any(MULTI_RELEASE.sub('', name).startswith('com/mysql/')
                   and name.endswith('.class') for name in archive.namelist()):
                raise ValueError('DrugRef still ships MySQL Connector/J classes in ' + library.name)
    with zipfile.ZipFile(connectors[0]) as connector:
        if 'org/mariadb/jdbc/Driver.class' not in connector.namelist():
            raise ValueError('DrugRef MariaDB connector jar does not contain its driver')


def main():
    if len(sys.argv) != 2:
        raise ValueError('Expected one staged DrugRef webapp directory')
    verify(Path(sys.argv[1]))
    print('DrugRef MariaDB JDBC configuration and legacy compatibility verified')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, ET.ParseError, zipfile.BadZipFile) as error:
        print('DrugRef JDBC packaging check refused: ' + str(error), file=sys.stderr)
        sys.exit(1)
