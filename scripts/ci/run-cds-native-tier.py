#!/usr/bin/env python3
#
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
#/
"""Run required CDS database contracts against a newly owned disposable MariaDB.

The supplied build container must already contain compiled application/test classes,
Java 25, Maven and this checkout. No existing database is contacted or reused.
"""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import re
import stat
import subprocess
import sys
import time
import uuid
from xml.etree.ElementTree import ElementTree
try:
    import defusedxml.ElementTree as ET
    from defusedxml.common import DefusedXmlException
except ImportError:
    sys.exit("Install hash-pinned dependencies from scripts/ci/requirements.txt before running this gate")

IMAGE = 'mariadb:11.8@sha256:f16d7a344413e519dff518f27bdb221388f84b0d274c97c4fb5f2cb0dc1b34fa'
DEVELOP_UPGRADE_BASE = '497896230576b492284b1c29efa77f4c4a2409ea'
CLASSES = (
    'CdsImportMaintenanceMariaDbIntegrationTest', 'CdsIdentityMariaDbIntegrationTest',
    'CdsImportCoordinatorMariaDbIntegrationTest',
    'CdsClinicalStorageMariaDbIntegrationTest', 'CdsAppointmentStorageMariaDbIntegrationTest',
    'CdsHistoryVisibilityMariaDbIntegrationTest', 'CdsDemographicStorageMariaDbIntegrationTest',
    'CdsReportStorageMariaDbIntegrationTest',
    'CdsHrmRepairAdmissionMariaDbIntegrationTest',
    'CdsReviewAcceptanceMariaDbIntegrationTest',
    'CdsImportLedgerMariaDbIntegrationTest', 'CdsTargetReceiptMariaDbIntegrationTest',
)

REQUIRED_CASES = {
    'CdsExportLedgerIntegrationTest': (
        'shouldPreserveTheObservedCancelRevision_acrossACommittedLeaseHeartbeat',
        'shouldRejectAStaleBrowserControl_withoutChangingTheNewerAttempt',
    ),
    'CdsImportCoordinatorMariaDbIntegrationTest': (
        'shouldPrefetchOneImmutableBinding_withoutTransactionOrRetainedConnection',
    ),
}


def coordinated_classes(repository, shard):
    """Resolve the reviewed inventory exactly once and partition every suite without overlap."""
    inventory = json.loads((repository / 'scripts/ci/cds-coordinated-test-classes.json').read_text())
    if not isinstance(inventory, list) or not inventory or any(not isinstance(item, str) for item in inventory):
        raise ValueError('Reviewed CDS inventory is invalid')
    sources = repository / 'src/test/java'
    selected, resolved = [], set()
    for item in inventory:
        if not re.fullmatch(r'[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*', item):
            raise ValueError('Reviewed CDS selector is not an exact class')
        matches = [sources / (item.replace('.', '/') + '.java')] if '.' in item else list(sources.rglob(item + '.java'))
        if len(matches) != 1 or not matches[0].is_file() or matches[0].is_symlink():
            raise ValueError('Reviewed CDS test is missing or ambiguous')
        qualified = '.'.join(matches[0].relative_to(sources).with_suffix('').parts)
        if qualified in resolved:
            raise ValueError('Reviewed CDS inventory contains duplicate classes')
        resolved.add(qualified)
        # Owning DAO tests live outside the feature package. Keep those with the
        # corresponding direction, rather than silently omitting them from CI.
        simple = matches[0].stem
        direction = ('export' if '.cdsexport.' in qualified or simple.startswith('CdsExport')
                     else 'import' if '.cdsimport.' in qualified else 'shared')
        if direction == shard:
            selected.append(qualified)
    if not selected or len({item.rsplit('.', 1)[-1] for item in selected}) != len(selected):
        raise ValueError('Reviewed CDS shard is empty or has ambiguous report identities')
    return tuple(selected)


def collect_reports(required, source, destination):
    """Reject missing, skipped or inconsistent physical evidence and strip captured configuration."""
    total, incomplete = 0, []
    for simple in required:
        files = list(source.glob('TEST-*.' + simple + '.xml'))
        if len(files) != 1:
            incomplete.append(simple + ': required report absent or ambiguous')
            continue
        metadata = files[0].lstat()
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > (32 << 20):
            raise RuntimeError('Required report is not a bounded regular file')
        suite = ET.parse(files[0], forbid_dtd=True).getroot()
        if suite.tag != 'testsuite' or not suite.get('name', '').endswith('.' + simple):
            raise RuntimeError('Required report suite binding is invalid')
        counts = {}
        for key in ('tests', 'errors', 'failures', 'skipped'):
            value = suite.get(key)
            if value is None or not value.isascii() or not value.isdecimal() or len(value) > 9:
                raise RuntimeError('Required report counters are invalid')
            counts[key] = int(value)
        cases = suite.findall('testcase')
        actual = {'tests': len(cases), 'errors': 0, 'failures': 0, 'skipped': 0}
        for case in cases:
            for key, tag in (('errors', 'error'), ('failures', 'failure'), ('skipped', 'skipped')):
                actual[key] += len(case.findall(tag))
        if counts != actual:
            raise RuntimeError('Required report counters do not match individual cases')
        print(simple + ': ' + ', '.join(f'{key}={value}' for key, value in counts.items()), flush=True)
        rerun = any(case.find(tag) is not None for case in cases
                    for tag in ('flakyFailure', 'flakyError', 'rerunFailure', 'rerunError'))
        if counts['tests'] < 1 or rerun or any(counts[key] for key in ('errors', 'failures', 'skipped')):
            incomplete.append(simple)
        for method in REQUIRED_CASES.get(simple, ()):
            if sum(case.get('name') == method for case in cases) != 1:
                incomplete.append(simple + ': required case absent or ambiguous')
        total += counts['tests']
        # Config and captured framework output never enter published reports.
        for parent in suite.iter():
            for child in list(parent):
                if child.tag.rsplit('}', 1)[-1] in {'properties', 'system-out', 'system-err'}:
                    parent.remove(child)
        ElementTree(suite).write(destination / files[0].name, encoding='utf-8', xml_declaration=True)
    return total, incomplete



def verify_admission_seeds(repository, client, run, log):
    """Replay exact guards on an owned scratch clone, then remove it before contract export."""
    statements = []
    pattern = re.compile(r"^[ \t]*INSERT\s+INTO\s+cds_import_admission\b[^;]*;", re.IGNORECASE | re.MULTILINE)
    for suffix in ('8.1__cds_import_ledger', '9__cds_upload_sessions'):
        text = (repository / 'database/mysql/migration/common' / ('V1.0.59.' + suffix + '.sql')).read_text()
        statements.extend(pattern.findall(text))
    if len(statements) != 3 or any('WHERE NOT EXISTS' not in sql.upper() for sql in statements):
        raise RuntimeError('Required admission seeds are not explicit existence-guarded statements')
    fixture = 'cds_seed_admission_fixture_' + uuid.uuid4().hex[:16]
    seeds = re.sub(r'\bcds_import_admission\b', fixture, '\n'.join(statements), flags=re.IGNORECASE)
    setup = 'CREATE TABLE ' + fixture + ' LIKE carlos_cds_test.cds_import_admission;\n'
    owner = '11111111-1111-4111-8111-111111111111'
    token = '22222222-2222-4222-8222-222222222222'
    sql = setup + seeds + '\nSELECT COUNT(*),SUM(owner_run_id IS NULL AND reserved_bytes=0) FROM ' + fixture + ';\n'
    sql += ("UPDATE " + fixture + " SET owner_run_id='" + owner + "',reserved_bytes=1234567,"
            "maintenance_active=1,maintenance_generation=7,maintenance_owner_token='" + token
            + "',maintenance_connection_id=99;\n")
    sql += seeds + '\n' + seeds
    sql += '\nSELECT JSON_ARRAY(id,owner_run_id,reserved_bytes,maintenance_active,maintenance_generation,maintenance_owner_token,maintenance_connection_id) FROM ' + fixture + ' ORDER BY id;'
    primary = None
    try:
        tested = run(client + ['--database=carlos_cds_test', '--batch', '--raw', '--skip-column-names', '-e', sql], log, capture=True)
        rows = tested.stdout.splitlines()
        if not rows or rows[0] != '3\t3' or len(rows) != 4:
            raise RuntimeError('Fresh admission seed defaults differ')
        actual = {entry[0]: entry[1:] for entry in (json.loads(row) for row in rows[1:])}
        expected = {key: [owner, 1234567, 1, 7, token, 99] for key in ('global', 'clinical-maintenance', 'UPLOAD_BUDGET')}
        if actual != expected:
            raise RuntimeError('Admission seed replay changed an existing owner or reservation')
        run(client + ['--database=carlos_cds_test', '-e', 'DROP TABLE ' + fixture], log)
        rejected = setup + 'ALTER TABLE ' + fixture + ' ADD CONSTRAINT fixture_positive_reservation CHECK(reserved_bytes>0);\n' + seeds
        if run(client + ['--database=carlos_cds_test', '-e', rejected], log, check=False, capture=True).returncode == 0:
            raise RuntimeError('Admission seed unexpectedly masked an unrelated storage constraint error')
    except BaseException as failure:
        primary = failure
        raise
    finally:
        cleanup = run(client + ['--database=carlos_cds_test', '--batch', '--raw', '--skip-column-names', '-e',
                                'DROP TABLE IF EXISTS ' + fixture + "; SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='carlos_cds_test' AND TABLE_NAME='" + fixture + "'"],
                      log, check=False, capture=True)
        if cleanup.returncode or cleanup.stdout.strip() != '0':
            if primary is not None:
                primary.add_note('Owned admission seed fixture cleanup also requires reconciliation')
            else:
                raise RuntimeError('Owned admission seed fixture cleanup requires reconciliation')
    print('Admission seed replay preserved all existing state and surfaced unrelated constraint failure.', flush=True)


def materialize_develop_migrations(repository, destination):
    """Use the actual pinned develop commit, including its original SQL bytes and file inventory."""
    prefix = 'database/mysql/migration/'
    listing = subprocess.run(['git', 'ls-tree', '-r', '--name-only', DEVELOP_UPGRADE_BASE, '--',
                              prefix + 'common', prefix + 'on', prefix + 'bc'],
                             cwd=repository, check=True, capture_output=True, text=True).stdout.splitlines()
    scripts = [item for item in listing if item.endswith('.sql')]
    if len(scripts) < 60:
        raise RuntimeError('Pinned develop migration inventory is incomplete')
    for item in scripts:
        if not re.fullmatch(re.escape(prefix) + r'(?:common|on|bc)/V[0-9.]+__[A-Za-z0-9_]+\.sql', item):
            raise RuntimeError('Pinned develop migration path is not an owned SQL resource')
        target = destination / item.removeprefix(prefix)
        target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        content = subprocess.run(['git', 'show', DEVELOP_UPGRADE_BASE + ':' + item],
                                 cwd=repository, check=True, capture_output=True).stdout
        if not content or len(content) > 16 * 1024 * 1024:
            raise RuntimeError('Pinned develop migration resource is missing or oversized')
        target.write_bytes(content)
        target.chmod(0o600)


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', default='carlos-tomcat-dev')
    parser.add_argument('--container-workspace', default='/workspace')
    parser.add_argument('--container-user', type=int,
                        help='Docker exec UID for required protected fixtures; defaults to the container identity')
    parser.add_argument('--maven', default='mvn')
    parser.add_argument('--maven-repository')
    parser.add_argument('--classpath-file', type=Path,
                        help='Reuse an existing dependency classpath for this same build container without invoking Maven')
    parser.add_argument('--schema-only', action='store_true',
                        help='Regenerate/check only fresh ON/BC schema contracts; never report native tests as run')
    parser.add_argument('--refresh-schema-contracts', action='store_true',
                        help='Regenerate ON/BC physical contracts from this newly owned full-chain reference server')
    parser.add_argument('--coordinated-shard', choices=('import', 'export', 'shared'),
                        help='Run an exact partition of the complete import/export inventory, with no skipped suites')
    parser.add_argument('--catalogue-case-folded', action='store_true',
                        help='Run physical source-catalogue and ledger contracts with MariaDB table-name case mode 1')
    parser.add_argument('--develop-upgrade', action='store_true',
                        help='Qualify retained-data ON/BC upgrades from the exact develop base through CLI and boot validation')
    args = parser.parse_args()
    if args.container_user is not None and args.container_user < 0:
        parser.error('--container-user must be a nonnegative UID')
    if args.schema_only and not args.refresh_schema_contracts:
        parser.error('--schema-only requires --refresh-schema-contracts')
    if args.coordinated_shard and args.schema_only:
        parser.error('--coordinated-shard cannot be combined with --schema-only')
    if args.catalogue_case_folded and (args.coordinated_shard or args.schema_only):
        parser.error('--catalogue-case-folded is an independent required physical database profile')
    if args.develop_upgrade and (args.coordinated_shard or args.schema_only or args.refresh_schema_contracts or args.catalogue_case_folded):
        parser.error('--develop-upgrade is an independent ON/BC upgrade profile')
    return args


def database_profiles(args):
    profiles = [('carlos_cds_test', 'jdbc.properties', 'on')]
    if not args.schema_only:
        profiles.append(('cds_ledger_test', 'ledger.properties', 'on'))
    if args.coordinated_shard or args.catalogue_case_folded:
        profiles.append(('cds_export_test', 'export.properties', 'on'))
    if args.refresh_schema_contracts:
        profiles.append(('cds_full_test', 'bc.properties', 'bc'))
    if args.develop_upgrade:
        profiles = [('cds_export_test', 'upgrade-on.properties', 'on'),
                    ('cds_full_test', 'upgrade-bc.properties', 'bc')]
    return profiles


def verify_case_mode(client, run, log, folded):
    case_mode = run(client + ['--batch', '--skip-column-names', '-e',
                              'SELECT @@lower_case_table_names'], log, capture=True).stdout.strip()
    if case_mode != ('1' if folded else '0'):
        raise RuntimeError('Disposable MariaDB table-name case mode differs from the required profile')


def main():
    args = parse_args()
    repository = Path(__file__).resolve().parents[2]
    selectors = coordinated_classes(repository, args.coordinated_shard) if args.coordinated_shard else CLASSES
    if args.catalogue_case_folded:
        selectors = ('CdsExportDocumentReadMariaDbIntegrationTest', 'CdsExportSchemaVisibilityMariaDbIntegrationTest',
                     'CdsExportLedgerIntegrationTest')
    required = tuple(item.rsplit('.', 1)[-1] for item in selectors)
    nonce = uuid.uuid4().hex
    name = 'cds-native-' + nonce
    network = name + '-net'
    output = repository / 'target' / name
    output.mkdir(mode=0o700, parents=True)
    private = output / 'private'
    private.mkdir(mode=0o700)
    container_output = args.container_workspace.rstrip('/') + '/target/' + name
    container_private = container_output + '/private'
    password = secrets.token_hex(32)
    (private / 'password').write_text(password)
    (private / 'client.cnf').write_text('[client]\nuser=root\npassword=' + password + '\n')
    for path in private.iterdir():
        path.chmod(0o600)
    created_network = created_database = connected_build = created_document_root = False
    document_root = '/var/lib/carlos-cds-transfer-documents-' + nonce
    docker = ['docker', '--host=unix:///var/run/docker.sock']
    log_path = output / 'native-tier.log'

    def run(command, log, check=True, capture=False):
        return subprocess.run(command, check=check, text=True,
                              stdout=subprocess.PIPE if capture else log, stderr=log)

    def build(command, log, check=True):
        identity = ['--user', str(args.container_user)] if args.container_user is not None else []
        return run(docker + ['exec'] + identity + ['--workdir', args.container_workspace, args.container] + command, log, check=check)

    maven = [args.maven, '-B', '-q']
    if args.maven_repository:
        maven.append('-Dmaven.repo.local=' + args.maven_repository)
    try:
        with log_path.open('w') as log:
            run(docker + ['network', 'create', '--internal', network], log)
            created_network = True
            run(docker + ['network', 'connect', network, args.container], log)
            connected_build = True
            run(docker + ['run', '-d', '--name', name, '--network', network, '--memory', '2g', '--cpus', '2',
                          '--tmpfs', '/var/lib/mysql:rw,nosuid,noexec,size=1536m',
                          '--mount', 'type=bind,src=' + str(private) + ',dst=/run/cds-test,readonly',
                          '-e', 'MARIADB_ROOT_PASSWORD_FILE=/run/cds-test/password', '-e', 'MARIADB_ROOT_HOST=%',
                          IMAGE, '--lower-case-table-names=' + ('1' if args.catalogue_case_folded else '0'),
                          '--innodb-page-size=32768', '--innodb-snapshot-isolation=ON',
                          '--character-set-server=utf8mb4', '--collation-server=utf8mb4_general_ci',
                          # Match the repository config for very wide legacy form DDL. SQL
                          # strict mode stays enabled, including all native storage tests.
                          '--innodb-strict-mode=OFF', '--innodb-default-row-format=dynamic',
                          '--max-allowed-packet=64M', '--innodb-buffer-pool-size=128M'], log)
            created_database = True
            mounts = run(docker + ['exec', name, 'cat', '/proc/mounts'], log, capture=True).stdout
            if not any(len(parts) > 2 and parts[1] == '/var/lib/mysql' and parts[2] == 'tmpfs'
                       for parts in (line.split() for line in mounts.splitlines())):
                raise RuntimeError('Disposable database storage is not the required tmpfs')
            client = docker + ['exec', name, 'mariadb', '--defaults-extra-file=/run/cds-test/client.cnf']
            deadline = time.monotonic() + 90
            while True:
                if run(client + ['-e', 'SELECT 1'], log, check=False).returncode == 0:
                    break
                if time.monotonic() > deadline:
                    raise RuntimeError('Disposable MariaDB did not become ready')
                time.sleep(1)
            verify_case_mode(client, run, log, args.catalogue_case_folded)
            profiles = database_profiles(args)
            run(client + ['-e', '; '.join('CREATE DATABASE ' + database + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci'
                                         for database, _, _ in profiles)], log)
            for database, filename, _ in profiles:
                properties = private / filename
                properties.write_text('jdbcUrl=jdbc:mysql://' + name + ':3306/' + database
                                      + '?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci'
                                      + '&allowPublicKeyRetrieval=true&useSSL=false\njdbcUser=root\njdbcPassword=' + password
                                      + '\nownedDisposableDatabase=true\n')
                properties.chmod(0o600)
            if args.classpath_file:
                if args.classpath_file.is_symlink() or not args.classpath_file.is_file() or args.classpath_file.stat().st_size > (1 << 20):
                    raise RuntimeError('Existing dependency classpath is not a bounded regular file')
                dependencies = args.classpath_file.read_text().strip()
                if not dependencies or '\n' in dependencies or '\r' in dependencies:
                    raise RuntimeError('Existing dependency classpath is invalid')
            else:
                build(maven + ['dependency:build-classpath', '-Dmdep.outputFile=' + container_output + '/classpath'], log)
                dependencies = (output / 'classpath').read_text().strip()
            classpath = args.container_workspace + '/target/classes' + os.pathsep + dependencies
            def run_develop_upgrade():
                materialize_develop_migrations(repository, private / 'develop-base')
                build(['python3', 'scripts/ci/verify-cds-consent-forward.py',
                       '--settings', container_private + '/upgrade-on.properties',
                       '--client-defaults', container_private + '/client.cnf',
                       '--proof', container_output + '/consent-forward-guard.json'], log)
                consent_guard = json.loads((output / 'consent-forward-guard.json').read_text())
                if consent_guard != {'status': 'PASSED', 'cases': 5, 'validShapes': 2,
                                     'nonStrictModeRefusals': 3, 'rowsPreserved': 5}:
                    raise RuntimeError('Consent forward migration evidence is incomplete')
                build(['javac', '-cp', classpath, '-d', container_output + '/classes',
                       'scripts/ci/CdsDevelopUpgradeProof.java',
                       'debian/assets/flyway-runner/src/main/java/io/github/carlos_emr/carlos/deb/FlywayRunner.java'], log)
                for database, filename, province in profiles:
                    build(['java', '-cp', container_output + '/classes' + os.pathsep + classpath,
                           'CdsDevelopUpgradeProof', container_private + '/' + filename,
                           container_private + '/develop-base', province,
                           container_output + '/upgrade-' + province + '.json'], log)
                    proof = json.loads((output / ('upgrade-' + province + '.json')).read_text())
                    expected = {'status': 'CLI_AND_RETENTION_PASSED', 'baseCommit': DEVELOP_UPGRADE_BASE, 'province': province,
                                'baseMigrations': 68 if province == 'on' else 65,
                                'headMigrations': 98 if province == 'on' else 96, 'retainedClinicalRows': 4, 'retainedAuditRows': 2, 'retainedPropertyRows': 1, 'interruptedColumnRecovery': True, 'concurrentRecoveryFenced': True, 'unreviewedSchemaRefused': True,
                                'appliedHistoryUnchanged': True, 'cliMigrate': True, 'cliValidate': True,
                                'bootValidate': True, 'pending': 0}
                    if proof != expected:
                        raise RuntimeError('Develop upgrade proof is incomplete or bound to another baseline')
                    physical = ['python3', 'scripts/schema-contract.py', '--database', database,
                                '--province', province, '--client-arg=--defaults-extra-file=' + container_private + '/client.cnf',
                                '--client-arg=-h', '--client-arg=' + name]
                    if build(physical + ['--check'], log, check=False).returncode:
                        build(physical + ['--output', container_output + '/schema-' + province + '.json'], log)
                        raise RuntimeError('Develop upgrade differs from the canonical fresh physical schema')
                    proof.update(status='PASSED', physicalSchemaAndSeedsMatched=True)
                    (output / ('upgrade-' + province + '.json')).write_text(json.dumps(proof, sort_keys=True) + '\n')
                print('CDS develop upgrade passed for both provinces; CLI, boot, retained data and original checksums verified.')
                return 0

            if args.develop_upgrade:
                return run_develop_upgrade()
            build(['javac', '-cp', classpath, '-d', container_output + '/classes',
                   'scripts/ci/CdsImportMariaDbBootstrap.java'], log)
            for _, filename, province in profiles:
                build(['java', '-cp', container_output + '/classes' + os.pathsep + classpath,
                       'CdsImportMariaDbBootstrap', container_private + '/' + filename,
                       'database/mysql/migration', province], log)
            verify_admission_seeds(repository, client, run, log)
            if args.refresh_schema_contracts:
                for database, province in [('carlos_cds_test', 'on'), ('cds_full_test', 'bc')]:
                    command = ['python3', 'scripts/schema-contract.py', '--database', database, '--province', province,
                               '--client-arg=--defaults-extra-file=' + container_private + '/client.cnf',
                               '--client-arg=-h', '--client-arg=' + name]
                    build(command, log)
                    build(command + ['--check'], log)
                build(['python3', 'scripts/schema-contract.py', '--verify-files'], log)
                print('Fresh full-chain ON/BC physical schema contracts regenerated and checked.', flush=True)
                if args.schema_only:
                    print('Schema-only operation complete; native contract tests were not run.', flush=True)
                    return 0
            # Explicit selectors and post-run XML checks prevent a missing property/tag from
            # quietly skipping the required physical-database contracts.
            original_reports = repository / 'target' / 'surefire-reports'
            reports = output / 'reports'
            reports.mkdir(mode=0o700)
            for simple in required:
                for previous in original_reports.glob('TEST-*.' + simple + '.xml'):
                    previous.unlink()
            test_command = maven + ['surefire:test', '-Dtest.forkCount=1',
                                    '-Dtest=' + ','.join(selectors),
                                    '-Dcds.test.properties=' + container_private + '/jdbc.properties',
                                    '-Dcds.ledger.test.properties=' + container_private + '/ledger.properties']
            if args.coordinated_shard or args.catalogue_case_folded:
                test_command.append('-Dcds.export.test.properties=' + container_private + '/export.properties')
                # The native-file tests exercise real trusted ancestry. A bind-mounted
                # checkout can be owned by the host runner; do not relax its ownership
                # checks or change host permissions to make those tests pass.
                build(['mkdir', '-m', '0700', document_root], log)
                created_document_root = True
                test_command.append('-Dcds.export.test.documentRoot=' + document_root)
            else:
                test_command.append('-Dgroups=integration')
            tested = build(test_command, log, check=False)
            total, incomplete = collect_reports(required, original_reports, reports)
            if tested.returncode or incomplete:
                raise RuntimeError('Required native contracts incomplete: ' + ', '.join(incomplete))
            print(f'CDS native tier passed: {total} tests across {len(required)} required suites; zero skips.')
            print('Sanitized reports: ' + str(output / 'reports'))
        return 0
    except (subprocess.CalledProcessError, RuntimeError, OSError, ET.ParseError, DefusedXmlException, ValueError) as failure:
        # Do not expose command arguments/configuration or captured database/Java output.
        print('CDS native tier failed (' + type(failure).__name__ + '); inspect protected ' + str(log_path), file=sys.stderr)
        return 1
    finally:
        cleanup_failures = []
        with (output / 'cleanup.log').open('w') as log:
            if created_document_root:
                if build(['rm', '-rf', '--', document_root], log, check=False).returncode:
                    cleanup_failures.append('document-root')
            if created_database:
                if run(docker + ['rm', '--force', '--volumes', name], log, check=False).returncode:
                    cleanup_failures.append('database')
            if connected_build:
                if run(docker + ['network', 'disconnect', network, args.container], log, check=False).returncode:
                    cleanup_failures.append('build-network')
            if created_network:
                if run(docker + ['network', 'rm', network], log, check=False).returncode:
                    cleanup_failures.append('network')
        for credential in private.iterdir():
            if credential.is_dir() and not credential.is_symlink():
                shutil.rmtree(credential)
            else:
                credential.unlink()
        private.rmdir()
        if cleanup_failures:
            raise RuntimeError('Owned CDS native-tier cleanup failed: ' + ', '.join(cleanup_failures))


if __name__ == '__main__':
    sys.exit(main())
