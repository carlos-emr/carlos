# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Execute the package transform against valid and drifting upstream inputs."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
HELPER = ROOT / 'debian/apply-drugref-jdbc-default.py'
NS = {'b': 'http://www.springframework.org/schema/beans'}
FIXTURE = b'''<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans">
    <!-- Preserve all unrelated bytes, not merely the parsed XML semantics. -->
    <bean id="unrelated" class="example.Unchanged" />
    <bean id="dataSource" class="com.zaxxer.hikari.HikariDataSource" destroy-method="close">
        <property name="driverClassName" value="${db_driver}" />
        <property name="jdbcUrl" value="${db_url}?serverTimezone=UTC" />
        <property name="autoCommit" value="false" />
        <property name="username" value="${db_user}" />
        <property name="password" value="${db_password}" />
        <property name="maximumPoolSize" value="32" />
        <property name="minimumIdle" value="2" />
        <property name="connectionTimeout" value="10000" />
    </bean>
    <bean id="later" class="example.AlsoUnchanged" />
</beans>
'''


class JdbcDefaultsPackagingTest(unittest.TestCase):
    def transform(self, content):
        return subprocess.run([sys.executable, str(HELPER), '--stdin'], input=content, capture_output=True)

    def test_exact_override_is_inserted_without_changing_existing_bytes(self):
        result = self.transform(FIXTURE)
        self.assertEqual(result.returncode, 0, result.stderr)
        root = ET.fromstring(result.stdout)
        props = root.findall("b:bean[@id='dataSource']/b:property[@name='dataSourceProperties']", NS)
        self.assertEqual(len(props), 1)
        self.assertEqual(props[0].attrib, {'name': 'dataSourceProperties', 'ref': 'carlosDebianMetadataProperties'})
        parser = root.find("b:bean[@id='carlosDebianJdbcUrl']", NS)
        self.assertEqual(parser.get('class'), 'com.mysql.cj.conf.ConnectionUrl')
        self.assertEqual(parser.get('factory-method'), 'getConnectionUrlInstance')
        self.assertEqual(parser.find("b:constructor-arg[@index='0']", NS).get('value'), '${db_url}?serverTimezone=UTC')
        self.assertIsNotNone(parser.find("b:constructor-arg[@index='1']/b:null", NS))
        selected = root.find("b:bean[@id='carlosDebianMetadataMode']", NS)
        self.assertEqual(selected.get('factory-method'), 'getProperty')
        self.assertEqual(selected.find("b:constructor-arg[@index='1']", NS).get('value'), '${db_use_information_schema:false}')
        entries = root.findall("b:bean[@id='carlosDebianMetadataProperties']/b:property[@name='sourceMap']/b:map/b:entry", NS)
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0].attrib, {'key': 'useInformationSchema', 'value-ref': 'carlosDebianMetadataMode'})
        # Remove only the two documented additions, then compare every original byte.
        addition = b'        <property name="dataSourceProperties" ref="carlosDebianMetadataProperties" />\n'
        cleaned = result.stdout.replace(addition, b'', 1)
        start = cleaned.index(b'\n    <!-- CARLOS Debian JDBC metadata defaults -->\n')
        ending = b'    <!-- End CARLOS Debian JDBC metadata defaults -->\n'
        end = cleaned.index(ending, start) + len(ending)
        self.assertEqual(cleaned[:start] + cleaned[end:], FIXTURE)

    def test_upstream_datasource_drift_and_ambiguous_inputs_fail_closed(self):
        altered = [
            FIXTURE.replace(b'id="dataSource"', b'id="otherSource"'),
            FIXTURE.replace(b'com.zaxxer.hikari.HikariDataSource', b'example.OtherPool'),
            FIXTURE.replace(b'${db_url}?serverTimezone=UTC', b'${db_url}?different=true'),
            FIXTURE.replace(b'name="maximumPoolSize" value="32"', b'name="maximumPoolSize" value="64"'),
            FIXTURE.replace(b'name="minimumIdle"', b'name="maximumPoolSize"'),
            FIXTURE.replace(b'    </bean>', b'        <property name="dataSourceProperties" value="existing" />\n    </bean>'),
            FIXTURE.replace(b'</beans>', b'<bean id="dataSource" class="example.OtherPool" /></beans>'),
            FIXTURE.replace(b'<beans ', b'<!DOCTYPE beans SYSTEM "file:///private">\n<beans '),
            b'<beans>',
        ]
        for content in altered:
            with self.subTest(content=content):
                result = self.transform(content)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, b'')

    def test_existing_names_or_aliases_of_generated_beans_are_rejected(self):
        for declaration in [b'<bean id="carlosDebianJdbcUrl" class="example.Decoy" />',
                            b'<bean name="other,carlosDebianMetadataMode" class="example.Decoy" />',
                            b'<alias name="unrelated" alias="carlosDebianMetadataProperties" />']:
            with self.subTest(declaration=declaration):
                result = self.transform(FIXTURE.replace(b'</beans>', declaration + b'</beans>'))
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, b'')

    def test_repeated_transform_is_refused(self):
        first = self.transform(FIXTURE)
        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertNotEqual(self.transform(first.stdout).returncode, 0)

    def test_literal_opening_in_comment_or_cdata_cannot_fake_a_transform(self):
        opening = b'<bean id="dataSource" class="com.zaxxer.hikari.HikariDataSource" destroy-method="close">'
        actual = FIXTURE.replace(opening, opening.replace(b'<bean ', b'<bean  '))
        for decoy in [b'<!-- ' + opening + b'\n    </bean> -->',
                      b'<![CDATA[' + opening + b'\n    </bean>]]>']:
            with self.subTest(decoy=decoy):
                content = actual.replace(b'    <!-- Preserve', b'    ' + decoy + b'\n    <!-- Preserve')
                result = self.transform(content)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, b'')

    def test_file_failure_preserves_input_and_success_preserves_mode(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'spring_config.xml'
            path.write_bytes(b'not xml')
            result = subprocess.run([sys.executable, str(HELPER), str(path)], capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(path.read_bytes(), b'not xml')
            path.write_bytes(FIXTURE)
            path.chmod(0o640)
            result = subprocess.run([sys.executable, str(HELPER), str(path)], capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(path.stat().st_mode & 0o777, 0o640)
            self.assertEqual(path.read_bytes(), self.transform(FIXTURE).stdout)

    def test_symlink_input_is_never_followed(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'original.xml'
            target.write_bytes(FIXTURE)
            link = Path(directory) / 'spring_config.xml'
            link.symlink_to(target)
            result = subprocess.run([sys.executable, str(HELPER), str(link)], capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(target.read_bytes(), FIXTURE)


if __name__ == '__main__':
    unittest.main()
