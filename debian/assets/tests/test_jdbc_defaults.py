# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Refuse older or incomplete DrugRef WARs before packaging a broken upgrade."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
HELPER = ROOT / 'debian/check-drugref-jdbc.py'
PACKAGE = 'io/github/carlos_emr/drugref2026/util/'
FIXTURE = b'''<beans xmlns="http://www.springframework.org/schema/beans">
    <bean id="dataSource" class="io.github.carlos_emr.drugref2026.util.DrugrefDataSource" destroy-method="close">
        <property name="driverClassName" value="${db_driver}" />
        <property name="jdbcUrl" value="${db_url}" />
        <property name="username" value="${db_user}" />
        <property name="password" value="${db_password}" />
    </bean>
</beans>'''


class JdbcDefaultsPackagingTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.webapp = Path(self.temporary.name)
        self.classes = self.webapp / 'WEB-INF/classes'
        self.classes.mkdir(parents=True)
        self.config = self.classes / 'spring_config.xml'
        self.config.write_bytes(FIXTURE)
        for name in ('DrugrefDataSource', 'MariaDbJdbcCompatibility', 'MariaDbCompatibilityDriver',
                     'MariaDbPreferredTlsPlugin'):
            path = self.classes / (PACKAGE + name + '.class')
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b'fixture')
        self.service = self.classes / 'META-INF/services/org.mariadb.jdbc.plugin.TlsSocketPlugin'
        self.service.parent.mkdir(parents=True)
        self.service.write_text(PACKAGE.replace('/', '.') + 'MariaDbPreferredTlsPlugin\n', encoding='utf-8')
        self.libraries = self.webapp / 'WEB-INF/lib'
        self.libraries.mkdir(parents=True)
        self.connector = self.libraries / 'mariadb-java-client-3.5.11.jar'
        self.write_connector(self.connector)

    def write_connector(self, path):
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('org/mariadb/jdbc/Driver.class', b'fixture')

    def check(self):
        return subprocess.run([sys.executable, str(HELPER), str(self.webapp)], capture_output=True)

    def test_migrated_application_is_verified_without_modifying_configuration(self):
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.config.read_bytes(), FIXTURE)

    def test_old_prebuilt_wars_and_changed_url_wiring_are_refused(self):
        invalid = [
            FIXTURE.replace(b'io.github.carlos_emr.drugref2026.util.DrugrefDataSource',
                            b'com.zaxxer.hikari.HikariDataSource'),
            FIXTURE.replace(b'${db_url}', b'${db_url}?serverTimezone=UTC'),
            FIXTURE.replace(b'${db_driver}', b'com.mysql.cj.jdbc.Driver'),
            FIXTURE.replace(b'${db_user}', b'root'),
            FIXTURE.replace(b'${db_password}', b'hard-coded-password'),
            FIXTURE.replace(b'<property name="username" value="${db_user}" />', b''),
            FIXTURE.replace(b'<property name="password" value="${db_password}" />', b''),
            FIXTURE.replace(b'id="dataSource"', b'id="otherSource"'),
            FIXTURE.replace(b'</beans>', b'<bean id="dataSource" /></beans>'),
            FIXTURE.replace(b'</bean>', b'<property name="jdbcUrl" value="other" /></bean>'),
            FIXTURE.replace(b'</beans>', b'<bean class="com.mysql.cj.conf.ConnectionUrl" /></beans>'),
            b'<!DOCTYPE beans SYSTEM "file:///private">' + FIXTURE,
            b'<beans>',
        ]
        for content in invalid:
            with self.subTest(content=content):
                self.config.write_bytes(content)
                result = self.check()
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(result.stdout, b'')
                self.assertEqual(self.config.read_bytes(), content)

    def test_utf16_and_utf32_xml_entities_are_refused_before_parsing(self):
        xml = ('<!DOCTYPE beans [<!ENTITY url "${db_url}">]>'
               + FIXTURE.decode('utf-8').replace('${db_url}', '&url;'))
        for encoding in ('utf-16', 'utf-16-be', 'utf-32', 'utf-32-be'):
            with self.subTest(encoding=encoding):
                self.config.write_bytes(xml.encode(encoding))
                result = self.check()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(b'NUL bytes or XML entities', result.stderr)

    def test_missing_compatibility_classes_are_refused(self):
        for name in ('DrugrefDataSource', 'MariaDbJdbcCompatibility', 'MariaDbCompatibilityDriver',
                     'MariaDbPreferredTlsPlugin'):
            with self.subTest(name=name):
                path = self.classes / (PACKAGE + name + '.class')
                path.unlink()
                self.assertNotEqual(self.check().returncode, 0)
                path.write_bytes(b'fixture')

    def test_missing_and_wrong_tls_service_registration_are_refused(self):
        self.service.unlink()
        self.assertNotEqual(self.check().returncode, 0)
        for content in ('', '# no provider\n', 'example.WrongProvider\n',
                        2 * (PACKAGE.replace('/', '.') + 'MariaDbPreferredTlsPlugin\n')):
            with self.subTest(content=content):
                self.service.write_text(content, encoding='utf-8')
                self.assertNotEqual(self.check().returncode, 0)

    def test_missing_duplicate_and_old_connectors_are_refused(self):
        self.connector.unlink()
        self.assertNotEqual(self.check().returncode, 0)
        self.write_connector(self.connector)
        duplicate = self.libraries / 'mariadb-java-client-3.4.0.jar'
        self.write_connector(duplicate)
        self.assertNotEqual(self.check().returncode, 0)
        self.connector.unlink()
        self.assertNotEqual(self.check().returncode, 0, 'the sole connector must also match the pin')
        duplicate.unlink()
        self.write_connector(self.connector)
        old = self.libraries / 'mysql-connector-j-9.1.0.jar'
        old.write_bytes(b'old connector')
        self.assertNotEqual(self.check().returncode, 0)

    def test_renamed_and_multi_release_mysql_connectors_are_refused(self):
        renamed = self.libraries / 'legacy-driver.jar'
        for entry in ('com/mysql/cj/jdbc/Driver.class',
                      'META-INF/versions/21/com/mysql/cj/jdbc/Driver.class'):
            with self.subTest(entry=entry):
                with zipfile.ZipFile(renamed, 'w') as archive:
                    archive.writestr(entry, b'fixture')
                result = self.check()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn(b'MySQL Connector/J classes', result.stderr)

    def test_truncated_or_misnamed_connector_is_refused(self):
        self.connector.write_bytes(b'not a jar')
        self.assertNotEqual(self.check().returncode, 0)
        with zipfile.ZipFile(self.connector, 'w') as archive:
            archive.writestr('unrelated.class', b'fixture')
        self.assertNotEqual(self.check().returncode, 0)


class FlywayConnectorPackagingTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('jar'), 'requires a JDK jar tool')
    def test_prebuilt_war_with_wrong_or_duplicate_connector_is_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            staging = Path(temporary)
            war = staging / 'carlos.war'
            output = staging / 'output'
            for connectors in (('mariadb-java-client-3.4.0.jar',),
                               ('mariadb-java-client-3.5.11.jar', 'mariadb-java-client-3.4.0.jar')):
                with self.subTest(connectors=connectors):
                    with zipfile.ZipFile(war, 'w') as archive:
                        for name in ('flyway-core-12.0.0.jar', 'flyway-mysql-12.0.0.jar', *connectors):
                            archive.writestr('WEB-INF/lib/' + name, b'fixture')
                    environment = os.environ.copy()
                    environment['JAVA_HOME'] = str(Path(shutil.which('jar')).resolve().parents[1])
                    result = subprocess.run(
                        ['sh', str(ROOT / 'debian/build-flyway-runner.sh'), str(war), str(output)],
                        cwd=ROOT, env=environment, capture_output=True)
                    self.assertEqual(result.returncode, 1, result.stderr)
                    self.assertIn(b'must ship exactly mariadb-java-client-3.5.11.jar', result.stderr)
                    self.assertFalse((output / 'carlos-flyway-runner.jar').exists())


if __name__ == '__main__':
    unittest.main()
