# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Exercise native verdicts with real subprocess exits, including python -O."""
import importlib.util
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import defusedxml.ElementTree as ET
from junit_evidence import display_literal, EvidenceError, read_surefire

SPEC = importlib.util.spec_from_file_location("mariadb_reports", Path(__file__).with_name("verify-mariadb-dao-reports.py"))
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

# Keep these fixture identities independent of the verifier's requirements: a
# deleted requirement must break the negative control, not delete the test too.
GROWTH_REQUIRED_CASES = {
    "shouldRetainCommittedFormAndLogSafely_whenArchiveReadOrWriteFails(boolean)[1]",
    "shouldRetainCommittedFormAndLogSafely_whenArchiveReadOrWriteFails(boolean)[2]",
    "shouldCommitDemographicAudit_whenLoadingWithoutAnMrp(boolean)[1]",
    "shouldCommitDemographicAudit_whenLoadingWithoutAnMrp(boolean)[2]",
}


TOKEN_REQUIRED_CASES = {
    "shouldUseEpochExpiryAcrossDatabaseZones_withoutDeletingRecentOrOtherPurposeTokens(String)[1]",
    "shouldUseEpochExpiryAcrossDatabaseZones_withoutDeletingRecentOrOtherPurposeTokens(String)[2]",
    "shouldUseEpochExpiryAcrossDatabaseZones_withoutDeletingRecentOrOtherPurposeTokens(String)[3]",
    "shouldBoundDeletionBatches_andReportActualIdempotentCounts",
    "shouldRefuseCorruptSoapMetadata_withoutPartiallyDeletingValidRows",
    "shouldRollbackEarlierDeletes_whenALaterDeleteFails",
    "shouldRefuseNonTransactionalStorage_beforeAnyDeletion",
    "shouldRefuseAnExistingTransaction_withoutCommittingOrRollingBackCallerWork",
}
SCHEDULE_REQUIRED_CASES = {
    "shouldApplyProgramProviderPatientFilters_whilePagingNativeTies",
    "shouldContinuePastConsentBlockedRows_withoutReturningAFalseEmptyNativePage",
    "shouldFaultLegacyTiePages_andKeepCompleteNativeTimestampGroups",
    "shouldKeepASingleRepeatableReadSnapshot_acrossNativeBatchQueries",
    "shouldPageArchiveTiesWithoutLoss_onTheNativeSchema",
    "shouldPageCurrentTiesWithoutLoss_onTheNativeSchema",
    "shouldPropagateASecondNativeQueryFailure_withoutReturningAPartialPage",
    "shouldRequireReconciliationForLateWritesBehindAnAlreadyReturnedCursor",
    "shouldRetainTheDaoHardPageLimit_atMaximumNativePageSize",
}

REPORTING_REQUIRED_CASES = {
    'shouldCommitPatientNameAudit_withAnUnassignedOrMissingProvider(String)[1]',
    'shouldCommitPatientNameAudit_withAnUnassignedOrMissingProvider(String)[2]',
    'shouldRollbackEarlierNameAudits_whenLaterCheckedFailureEscapes',
    'shouldRollbackEarlierNameAudits_whenLaterPatientEnrichmentFails',
}

ADMISSION_REQUIRED_CASES = {
    "shouldRollbackReadAudits_whenALaterCheckedFailureEscapes",

    "shouldCommitSynchronousAdmissionAudits_withThePatientFilteredResponse",
    "shouldRollbackEarlierReadAudits_whenLaterPatientPolicyAbortsTheResponse",
}

APPOINTMENT_REQUIRED_CASES = {
    "shouldRefuseStoredPatient_beforeArchivalOrMutation",
    "shouldRollbackArchiveAndEdit_whenReplacementPatientIsDenied",
    "shouldAllowAssignedPatientWrite_withoutReadPermission",
}

RX_REQUIRED_CASES = {
    "shouldCommitPrescriptionAndDrugs_whenNativeWritesSucceed",
    "shouldRequireReconciliation_whenCommitSucceedsButAcknowledgementIsLost",
    "shouldRollbackHeaderEarlierDrugsAndArchive_whenALaterNativeWriteFails",
    "shouldRollbackPhysicalState_whenCommitFailsBeforeSubmission",
}

TICKLER_REQUIRED_CASES = {
    "shouldRetainClinicalPriorityOrdering_whenUsingTypedCriteria",
    "shouldRollbackLegacyResolution_whenALaterStoredPatientIsDenied",
        "shouldRollbackLegacyResolutionAlias_whenALaterStoredPatientIsDenied",
    "shouldBindFilterValues_andKeepSortHintsOutOfNativeQueryText",

    "shouldAuthorizeTheStoredAndRequestedPatients_beforeFlushingAManagedEdit",
    "shouldDenyTheCurrentStoredPatient_beforeAnyNativeMutation",
    "shouldHoldThePhysicalPatientLock_untilTheMutationTransactionCommits",
    "shouldKeepDiscoveryRowsAndTotals_inOneNativeReadSnapshot",
    "shouldPageAndCountOnlyPermittedRows_afterNativeDeniedBatches",
    "shouldRetainUpdateOnlyAuthority_withoutAddingReadPermission",
    "shouldRollbackEarlierNativeWrites_whenALaterMutationFails",
    "shouldRollbackManagedFieldChanges_whenValidationReturnsFalse",
}

MERGE_REQUIRED_CASES = {"shouldRefuseDeniedParticipants_beforeAnyDatabaseOrAuditChange"}

IDENTIFIER_REQUIRED_CASES = {
    "shouldStartItsOwnJpaTransaction_andPreserveUndisclosedIdentifiers",
    "shouldRefreshPreviouslyCachedIdentifiers_afterTakingTheWriteLock",
    "shouldRollbackTheNativeWrite_whenALaterFailureOccurs",
    "shouldRetainExplicitEmptyStringClears_inTheNativeTransaction",
    "shouldReportConflict_withoutOverwritingAnOlderTransactionSnapshot",
}


class MariaDbReportEvidenceTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.reports = self.root / "reports"
        self.reports.mkdir()
        self.output = self.root / "summary.txt"

    def invoke(self, optimized=False):
        return subprocess.run([sys.executable, *(["-O"] if optimized else []),
                               str(Path(MODULE.__file__)), "--reports", str(self.reports),
                               "--output", str(self.output)], capture_output=True, text=True, timeout=10)

    def assert_refused(self, result, reason):
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("MariaDB evidence refused: " + reason, result.stderr)
        self.assertNotIn("Traceback", result.stderr)

    def seed(self, tag="", mode="generated", duplicate=False):
        for selected, required in MODULE.REQUIRED.items():
            required = dict(required)
            if selected == "generated":
                required["MeasurementDaoIntegrationTest"] += 3000 - sum(required.values())
            for family, count in required.items():
                suite = self.suite(family)
                suffix = "" if selected == "generated" else "-" + selected
                required_names = sorted({
                    "Growth0To36FormPersistenceIntegrationTest": GROWTH_REQUIRED_CASES,
                    "SoapTokenHousekeepingDaoIntegrationTest": TOKEN_REQUIRED_CASES,
                    "DemographicMergeMariaDbIntegrationTest": MERGE_REQUIRED_CASES,
                    "DemographicIdentifierPreservationMariaDbIntegrationTest": IDENTIFIER_REQUIRED_CASES,
                    "ScheduleSyncMariaDbIntegrationTest": SCHEDULE_REQUIRED_CASES,
                    "RxPrescribeMariaDbIntegrationTest": RX_REQUIRED_CASES,
                    "TicklerPatientAccessMariaDbIntegrationTest": TICKLER_REQUIRED_CASES,
                    "AppointmentUpdateDaoIntegrationTest": APPOINTMENT_REQUIRED_CASES,
                    "ProgramAdmissionAuditMariaDbIntegrationTest": ADMISSION_REQUIRED_CASES,
                    "ReportingPatientListAuditMariaDbIntegrationTest": REPORTING_REQUIRED_CASES,
                }.get(family, set()))
                def case(index):
                    fault = tag if selected == mode and family == next(iter(required)) and index == 0 else ""
                    name = required_names[index] if index < len(required_names) else f"shouldRead_forRow{index}"
                    if family == 'LegacyLabNativeMariaDbIntegrationTest' and index < len(MODULE.INBOX_CASES):
                        name = MODULE.INBOX_CASES[index]
                    physical = MODULE.BC_PHYSICAL_CASES.get(suite, ()) if selected == 'bc-flyway' else ()
                    if index < len(physical):
                        name = physical[index]
                    return (f'<testcase classname="{suite}{suffix}" name="{name}">'
                            + fault + '</testcase>')
                body = "".join(case(i) for i in range(count))
                if duplicate and selected == mode and family == next(iter(required)):
                    body += case(0)
                    count += 1
                (self.reports / ("TEST-" + suite + suffix + ".xml")).write_text(
                    f'<testsuite name="{suite}{suffix}" tests="{count}" failures="0" errors="0" skipped="0">'
                    + body + '</testsuite>')

    @staticmethod
    def suite(family: str) -> str:
        if family == 'LegacyLabNativeMariaDbIntegrationTest':
            return MODULE.INBOX_CLASS
        return next((name for name in MODULE.BC_PHYSICAL_CASES if name.rsplit('.', 1)[-1] == family), 'example.' + family)

    def test_each_physical_type_case_is_required_even_when_family_counts_are_unchanged(self):
        self.seed()
        for family, names in MODULE.BC_PHYSICAL_CASES.items():
            report = self.reports / ('TEST-' + family + '-bc-flyway.xml')
            original = report.read_text()
            for name in names:
                with self.subTest(case=name):
                    report.write_text(original.replace('name="' + name + '"', 'name="unrelatedPhysicalCase"'))
                    try:
                        self.assert_refused(self.invoke(), 'Required MariaDB bc-flyway physical-type case identities missing')
                    finally:
                        report.write_text(original)

    def test_foreign_suite_cannot_supply_physical_case_identities(self):
        self.seed()
        for family, names in MODULE.BC_PHYSICAL_CASES.items():
            with self.subTest(family=family):
                report = self.reports / ('TEST-' + family + '-bc-flyway.xml')
                original = report.read_text()
                substituted = original
                for index, name in enumerate(names):
                    substituted = substituted.replace('name="' + name + '"', 'name="unrelatedPhysical' + str(index) + '"')
                report.write_text(substituted)
                foreign = self.reports / 'TEST-example.ForeignPhysical-bc-flyway.xml'
                foreign.write_text('<testsuite name="example.ForeignPhysical-bc-flyway" tests="' + str(len(names)) + '" failures="0" errors="0" skipped="0">'
                                   + ''.join('<testcase classname="' + family + '-bc-flyway" name="' + name + '"/>' for name in names) + '</testsuite>')
                try:
                    for optimized in (False, True):
                        self.assert_refused(self.invoke(optimized), 'Test case belongs to another suite')
                finally:
                    report.write_text(original)
                    foreign.unlink()

    def test_named_native_regressions_are_required_without_changing_family_counts(self):
        self.seed()
        for mode in MODULE.REQUIRED:
            suffix = "" if mode == "generated" else "-" + mode
            families = [("AppointmentUpdateDaoIntegrationTest", APPOINTMENT_REQUIRED_CASES)] if mode == "generated" else []
            for family, names in families + [("SoapTokenHousekeepingDaoIntegrationTest", TOKEN_REQUIRED_CASES),
                                  ("DemographicMergeMariaDbIntegrationTest", MERGE_REQUIRED_CASES),
                                  ("DemographicIdentifierPreservationMariaDbIntegrationTest", IDENTIFIER_REQUIRED_CASES),
                                  ("ScheduleSyncMariaDbIntegrationTest", SCHEDULE_REQUIRED_CASES),
                                  ("RxPrescribeMariaDbIntegrationTest", RX_REQUIRED_CASES),
                                  ("TicklerPatientAccessMariaDbIntegrationTest", TICKLER_REQUIRED_CASES),
                                  ("ProgramAdmissionAuditMariaDbIntegrationTest", ADMISSION_REQUIRED_CASES),
                                  ("ReportingPatientListAuditMariaDbIntegrationTest", REPORTING_REQUIRED_CASES)]:
                report = self.reports / ("TEST-" + self.suite(family) + suffix + ".xml")
                original = report.read_text()
                for name in names:
                    with self.subTest(mode=mode, family=family, case=name):
                        report.write_text(original.replace('name="' + name + '"', 'name="unrelatedCase"'))
                        try:
                            self.assert_refused(self.invoke(), f"Required MariaDB {mode} cases missing: {family}")
                        finally:
                            report.write_text(original)

    def test_clean_controls_pass_in_both_python_modes(self):
        self.seed()
        for optimized in (False, True):
            with self.subTest(optimized=optimized):
                result = self.invoke(optimized)
                self.assertEqual(result.returncode, 0, result.stderr)

    def test_another_package_or_nested_class_cannot_supply_required_inbox_cases(self):
        self.seed()
        report = self.reports / ('TEST-' + MODULE.INBOX_CLASS + '-flyway.xml')
        original = report.read_text()
        for replacement in ('example.LegacyLabNativeMariaDbIntegrationTest', MODULE.INBOX_CLASS + '$Substitute',
                            'Substitute display container'):
            with self.subTest(classname=replacement):
                if replacement.startswith('example.'):
                    # Keep filename, suite and testcase mutually consistent so
                    # only the required fully qualified identity can refuse it.
                    other = self.reports / ('TEST-' + replacement + '-flyway.xml')
                    report.unlink()
                    other.write_text(original.replace(MODULE.INBOX_CLASS, replacement))
                else:
                    other = report
                    report.write_text(original.replace('classname="' + MODULE.INBOX_CLASS,
                                                       'classname="' + replacement))
                try:
                    self.assert_refused(self.invoke(), 'Required MariaDB flyway inbox case identities missing')
                finally:
                    other.unlink()
                    report.write_text(original)

    def test_empty_reports_fail_with_optimization_and_remove_old_success(self):
        for optimized in (False, True):
            self.output.write_text("old successful run")
            self.assert_refused(self.invoke(optimized), "No MariaDB DAO reports found")
            self.assertFalse(self.output.exists())

    def test_first_attempt_failures_cannot_be_reclassified_as_pass(self):
        for tag in ("failure", "error", "flakyFailure", "flakyError", "rerunFailure", "rerunError", "skipped"):
            with self.subTest(tag=tag):
                self.seed(tag=f'<{tag} message="private synthetic payload"/>')
                result = self.invoke()
                self.assert_refused(result, "Suite skip counter disagrees" if tag == "skipped" else "MariaDB generated contains failed, retried, or skipped cases")
                self.assertNotIn("private synthetic payload", result.stdout + result.stderr)

    def test_missing_required_family_in_each_lane_fails(self):
        self.seed()
        for mode, required in MODULE.REQUIRED.items():
            for family in required:
                with self.subTest(mode=mode, family=family):
                    suffix = "" if mode == "generated" else "-" + mode
                    suite = self.suite(family)
                    path = self.reports / ("TEST-" + suite + suffix + ".xml")
                    original = path.read_bytes()
                    path.unlink()
                    reason = ("Broad MariaDB DAO selection unexpectedly lost coverage"
                              if mode == "generated" and family == "MeasurementDaoIntegrationTest"
                              else f"Required MariaDB {mode} coverage missing: {family}")
                    try:
                        self.assert_refused(self.invoke(), reason)
                    finally:
                        path.write_bytes(original)

    def test_unmatched_upload_cases_alone_cannot_supply_required_coverage(self):
        self.seed()
        for mode in ("generated", "flyway"):
            with self.subTest(mode=mode):
                suffix = "" if mode == "generated" else "-" + mode
                path = self.reports / ("TEST-example.LegacyLabUploadDaoIntegrationTest" + suffix + ".xml")
                original = path.read_bytes()
                root = ET.fromstring(original)
                # The original four cases omit matched-HL7 commit and rollback.
                for case in list(root)[4:]:
                    root.remove(case)
                root.set("tests", "4")
                path.write_bytes(ET.tostring(root))
                try:
                    self.assert_refused(self.invoke(),
                                        f"Required MariaDB {mode} coverage missing: LegacyLabUploadDaoIntegrationTest")
                finally:
                    path.write_bytes(original)

    def test_growth_required_cases_cannot_be_replaced_by_unrelated_cases_in_any_lane(self):
        self.seed()
        for mode in MODULE.REQUIRED:
            for missing in sorted(GROWTH_REQUIRED_CASES):
                with self.subTest(mode=mode, missing=missing):
                    suffix = "" if mode == "generated" else "-" + mode
                    path = self.reports / ("TEST-example.Growth0To36FormPersistenceIntegrationTest" + suffix + ".xml")
                    original = path.read_bytes()
                    root = ET.fromstring(original)
                    for case in root:
                        if case.get("name") == missing:
                            case.set("name", "shouldPass_forUnrelatedScenario")
                    path.write_bytes(ET.tostring(root))
                    try:
                        for optimized in (False, True):
                            self.assert_refused(self.invoke(optimized),
                                                f"Required MariaDB {mode} cases missing: Growth0To36FormPersistenceIntegrationTest: {missing}")
                    finally:
                        path.write_bytes(original)

    def test_growth_archive_failure_cases_cannot_be_removed_in_any_lane(self):
        self.seed()
        for mode in MODULE.REQUIRED:
            with self.subTest(mode=mode):
                suffix = "" if mode == "generated" else "-" + mode
                path = self.reports / ("TEST-example.Growth0To36FormPersistenceIntegrationTest" + suffix + ".xml")
                original = path.read_bytes()
                root = ET.fromstring(original)
                for case in list(root):
                    if case.get("name", "").startswith("shouldRetainCommittedFormAndLogSafely_whenArchiveReadOrWriteFails"):
                        root.remove(case)
                root.set("tests", str(len(root)))
                path.write_bytes(ET.tostring(root))
                try:
                    self.assert_refused(self.invoke(),
                                        f"Required MariaDB {mode} coverage missing: Growth0To36FormPersistenceIntegrationTest")
                finally:
                    path.write_bytes(original)

    def test_duplicates_and_suite_binding_are_refused(self):
        self.seed(duplicate=True)
        self.assert_refused(self.invoke(), "Duplicate test case identity")
        self.seed()
        path = next(iter(sorted(self.reports.glob("TEST-example.*.xml"))))
        path.write_text(path.read_text().replace('name="example.', 'name="different.', 1))
        self.assert_refused(self.invoke(), "Report filename and suite identity disagree")

    def test_malformed_entities_and_symlinks_are_refused(self):
        self.seed()
        path = next(self.reports.glob("*.xml"))
        original = path.read_bytes()
        for body in (b"<testsuite", b'<!DOCTYPE testsuite [<!ENTITY secret SYSTEM "file:///does-not-exist">]><testsuite/>'):
            path.write_bytes(body)
            self.assert_refused(self.invoke(), "Cannot read test report" if body.startswith(b"<testsuite") else "DTDForbidden")
        outside = self.root / "outside.xml"
        outside.write_bytes(original)
        path.unlink()
        path.symlink_to(outside)
        self.assert_refused(self.invoke(), "Cannot read test report")
        path.unlink()
        os.mkfifo(path)
        self.assert_refused(self.invoke(), "Test report is not a bounded regular file")

    def test_counter_mismatch_cannot_supply_coverage(self):
        self.seed()
        path = next(self.reports.glob("*.xml"))
        path.write_text(re.sub(r'tests="\d+"', 'tests="0"', path.read_text(), count=1))
        self.assert_refused(self.invoke(), "Suite counter does not match executed test cases")

    def test_summary_omits_configuration_and_console_output(self):
        self.seed()
        path = next(self.reports.glob("*.xml"))
        path.write_text(path.read_text().replace('</testsuite>',
                        '<properties><property name="password" value="private-marker"/></properties>'
                        '<system-out>private-marker</system-out></testsuite>'))
        self.assertEqual(self.invoke().returncode, 0)
        self.assertNotIn("private-marker", self.output.read_text())

    def test_junit_display_class_names_bind_to_the_qualified_suite(self):
        self.seed()
        path = self.reports / 'TEST-example.MeasurementDaoIntegrationTest.xml'
        path.write_text(re.sub(r'classname="[^"]+"', 'classname="Clinical DAO display name"', path.read_text()))
        self.assertEqual(self.invoke().returncode, 0)
        self.assertIn("example.", self.output.read_text())

    def test_foreign_dotted_classnames_are_refused_regardless_of_package_case(self):
        self.seed()
        path = next(self.reports.glob("*.xml"))
        original = path.read_text()
        for classname in ("OtherPackage.ForeignTest", "_package.ForeignTest", "example.ForeignTest"):
            with self.subTest(classname=classname):
                path.write_text(re.sub(r'classname="[^"]+"', f'classname="{classname}"', original))
                self.assert_refused(self.invoke(), "Test case belongs to another suite")

    def test_dotted_display_names_must_be_declared_in_the_owning_source(self):
        suite = "io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcileUpdateStatUnitTest"
        path = self.root / ("TEST-" + suite + ".xml")
        def report(label):
            path.write_text(f'<testsuite name="{suite}" tests="1" failures="0" errors="0" skipped="0">'
                            f'<testcase classname="{label}" name="shouldUpdate_forStatus"/></testsuite>')
        report("MSPReconcile.updateStat")
        case = read_surefire(path, suite)[0]
        self.assertEqual(case.classname, suite)
        self.assertEqual(case.container, "MSPReconcile.updateStat")
        report("MSPReconcile.notDeclared")
        with self.assertRaisesRegex(ValueError, "belongs to another suite"):
            read_surefire(path, suite)

    def test_java_only_annotation_escapes_do_not_refuse_a_valid_suite(self):
        suite = "io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcileUpdateStatUnitTest"
        path = self.root / ("TEST-" + suite + ".xml")
        path.write_text(f'<testsuite name="{suite}" tests="1" failures="0" errors="0" skipped="0">'
                        '<testcase classname="MSPReconcile.updateStat" name="shouldUpdate_forStatus"/></testsuite>')
        source = r'''@DisplayName("MSPReconcile.updateStat")
                     @DisplayName("Patient\'s\sRecord\040\uu002e")'''
        with patch("junit_evidence.Path.read_text", return_value=source):
            self.assertEqual(read_surefire(path, suite)[0].container, "MSPReconcile.updateStat")
        self.assertEqual(display_literal(r'"Patient\'s\sRecord\040\uu002e"'), "Patient's Record .")
        self.assertEqual(display_literal(r'"escaped\\s"'), r"escaped\s")
        with self.assertRaisesRegex(EvidenceError, "Unsupported escape"):
            display_literal(r'"invalid\q"')

    def test_parenthesized_surefire_schema_suffixes_are_bound_to_the_filename(self):
        self.seed()
        for path in self.reports.glob('*.xml'):
            mode = 'bc-flyway' if path.stem.endswith('-bc-flyway') else 'flyway' if path.stem.endswith('-flyway') else None
            if mode:
                path.write_text(path.read_text().replace('-' + mode + '"', '(' + mode + ')"'))
        self.assertEqual(self.invoke().returncode, 0)

    def test_nested_name_collision_is_explicit_and_does_not_hide_a_failed_attempt(self):
        path = self.root / "TEST-example.RequiredTest.xml"
        path.write_text('<testsuite name="example.RequiredTest" tests="1" failures="0" errors="0" skipped="0">'
                        '<testcase classname="First container" name="shouldWrite_atomically"><flakyFailure/></testcase>'
                        '<testcase classname="Second container" name="shouldWrite_atomically"/></testsuite>')
        diagnostics = []
        cases = read_surefire(path, 'example.RequiredTest', diagnostics=diagnostics)
        self.assertEqual(len(cases), 2)
        self.assertNotEqual(cases[0].identity, cases[1].identity)
        self.assertEqual([case.outcome for case in cases], ['failure', 'pass'])
        self.assertEqual(diagnostics[0]['declaredTests'], 1)
        self.assertEqual(diagnostics[0]['executedCases'], 2)

    def test_unrelated_passing_cases_cannot_replace_any_inbox_count_case(self):
        self.seed()
        for mode in ('flyway', 'bc-flyway'):
            path = self.reports / ('TEST-' + MODULE.INBOX_CLASS + '-' + mode + '.xml')
            original = path.read_bytes()
            for name in (
                'shouldCountOnlyTheSelectedPatientLabs_andKeepZeroAndMissingPatientsUnmatched',
                'shouldCountDistinctPatientDocuments_andSeparateUnmatchedAndAcknowledgedRouting',
                'shouldCountDistinctHrmReports_byPatientProviderAndSignoffIndependentlyOfViewed',
            ):
                path.write_bytes(original.replace(name.encode(), b'unrelatedPassingCase'))
                for optimized in (False, True):
                    self.assert_refused(self.invoke(optimized), f'Required MariaDB {mode} inbox case identities missing')
                path.write_bytes(original)


if __name__ == "__main__":
    unittest.main()
