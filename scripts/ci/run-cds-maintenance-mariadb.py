#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
"""Run the real CDS maintenance tier from isolated Java output (no Maven or credential argv)."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
try:
    import defusedxml.ElementTree as ET
except ImportError:
    sys.exit("Install hash-pinned dependencies from scripts/ci/requirements.txt before running this gate")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file', type=Path, required=True,
                        help='Prepared application/test dependency classpath, including JUnit ConsoleLauncher')
    parser.add_argument('--jdbc-properties', type=Path, required=True,
                        help='Protected synthetic MariaDB jdbcUrl/jdbcUser/jdbcPassword file')
    parser.add_argument('--output', type=Path, required=True, help='Isolated classes, logs and XML reports directory')
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[2]
    output = args.output.resolve()
    if output in {repository / 'target/classes', repository / 'target/test-classes'}:
        parser.error('Output must be separate from the shared application/test class directories')
    if not args.jdbc_properties.is_file() or not args.classpath_file.is_file():
        parser.error('Protected JDBC properties and a prepared dependency classpath file are required')
    output.mkdir(parents=True, exist_ok=True)
    classes = output / 'classes'
    reports = output / 'reports'
    classes.mkdir(exist_ok=True)
    reports.mkdir(exist_ok=True)
    for previous in reports.glob('TEST-*.xml'):
        previous.unlink()
    classpath = os.pathsep.join([str(classes), str(repository / 'target/classes'),
                                 str(repository / 'target/test-classes'), args.classpath_file.read_text().strip()])
    package = 'src/main/java/io/github/carlos_emr/carlos/'
    sources = [package + path for path in [
        'demographic/cdsimport/support/CdsJdbcAdmissionState.java',
        'demographic/cdsimport/support/CdsJdbcAdmissionGuard.java',
        'demographic/cdsimport/support/CdsJdbcAdmissionInitializer.java',
        'demographic/cdsimport/service/CdsImportMaintenanceService.java',
        'demographic/cdsimport/service/CdsImportTargetSafetyService.java',
        'demographic/cdsimport/service/CdsTargetReceiptService.java',
        'utility/OscarTrackingBasicDataSource.java', 'report/data/QueryByExampleSqlValidator.java']]
    sources.append('src/test/java/io/github/carlos_emr/carlos/demographic/cdsimport/service/'
                   'CdsImportMaintenanceMariaDbIntegrationTest.java')
    with (output / 'compile.log').open('w') as log:
        compiled = subprocess.run(['javac', '-cp', classpath, '-d', str(classes)] + sources,
                                  cwd=repository, stdout=log, stderr=subprocess.STDOUT)
    if compiled.returncode:
        print('Maintenance tier compilation failed; see ' + str(output / 'compile.log'))
        return compiled.returncode
    command = ['java', '-Dcds.test.properties=' + str(args.jdbc_properties.resolve()), '-cp', classpath,
               'org.junit.platform.console.ConsoleLauncher', 'execute', '--disable-banner', '--details=summary',
               '--fail-if-no-tests', '--reports-dir', str(reports), '--select-class',
               'io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportMaintenanceMariaDbIntegrationTest']
    with (output / 'tests.log').open('w') as log:
        tested = subprocess.run(command, cwd=repository, stdout=log, stderr=subprocess.STDOUT)
    totals = dict(tests=0, skipped=0, errors=0, failures=0)
    for report in reports.glob('TEST-*.xml'):
        suite = ET.parse(report, forbid_dtd=True).getroot()
        for name in totals:
            totals[name] += int(suite.attrib.get(name, '0'))
    print('Maintenance MariaDB tier: ' + ', '.join(f'{name}={count}' for name, count in totals.items()))
    print('Logs and XML reports: ' + str(output))
    if tested.returncode:
        return tested.returncode
    if totals != dict(tests=24, skipped=0, errors=0, failures=0):
        print('The complete explicit 24-test database tier must execute without skips.')
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
