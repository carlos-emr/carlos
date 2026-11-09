#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-2.0-or-later
"""Require complete native evidence, including failed first attempts."""
import argparse
import json
from pathlib import Path
import sys

from junit_evidence import EvidenceError, read_surefire

CRITICAL = {
    "MariaDbDaoHarnessIntegrationTest": 1,
    "MeasurementDaoIntegrationTest": 1,
    "MeasurementsExtDaoIntegrationTest": 1,
    "ProviderLabRoutingDaoIntegrationTest": 1,
    "BillingONCHeader1DaoIntegrationTest": 1,
}
# Surefire's default JUnit 5 names distinguish both boolean invocations. Counts
# alone cannot prove archive-failure or demographic-audit regressions executed.
REQUIRED_CASES = {
    "Growth0To36FormPersistenceIntegrationTest": {
        f"{method}(boolean)[{index}]"
        for method in (
            "shouldRetainCommittedFormAndLogSafely_whenArchiveReadOrWriteFails",
            "shouldCommitDemographicAudit_whenLoadingWithoutAnMrp",
        )
        for index in (1, 2)
    },
}
# AbstractDaoFindAllOrderingIntegrationTest pins issue #5308 where it matters: MariaDB returns an
# unordered page in secondary-index order, so its paging cases must execute on the physical server.
# CaseManagementIssueDAO adds one duplicate-link case on the legacy Flyway schema only.
REQUIRED = {"generated": {**CRITICAL, "FixedJasperReportDaoIntegrationTest": 2, "PatientEndYearStatementIntegrationTest": 5, "PatientEndYearStatementTransactionIntegrationTest": 2, "LegacyLabUploadDaoIntegrationTest": 6, "LabRoutingLookupDaoIntegrationTest": 4, "MeasurementPatientMatchMariaDbIntegrationTest": 10,
                          "JdbcConnectorMariaDbIntegrationTest": 32,
                          "BillingHistoryDaoIntegrationTest": 1, "LookupDaoIntegrationTest": 1,
                          "AbstractDaoFindAllOrderingIntegrationTest": 6,
                          "CaseManagementIssueDAOIntegrationTest": 21,
                          "ConsultationListProjectionIntegrationTest": 17,
                          "BCAR2020FormSaveTransactionIntegrationTest": 15,
                          "RxCorrectionSaveRollbackIntegrationTest": 2,
                          "DemographicMergeMariaDbIntegrationTest": 8,
                          "Growth0To36FormPersistenceIntegrationTest": 19},
            "flyway": {**CRITICAL, "FixedJasperReportDaoIntegrationTest": 2, "PatientEndYearStatementIntegrationTest": 5, "PatientEndYearStatementTransactionIntegrationTest": 2, "LegacyLabUploadDaoIntegrationTest": 6, "LabRoutingLookupDaoIntegrationTest": 4,
                       "LegacyLabNativeMariaDbIntegrationTest": 7, "ConsultationArchiveDaoIntegrationTest": 1,
                       "ArchiveAuditTimestampIntegrationTest": 2,
                       "CaseManagementNoteLockMariaDbIntegrationTest": 15,
                       "CaseManagementIssueDAOIntegrationTest": 22,
                       "ConsultationListProjectionIntegrationTest": 17,
                       "RxCorrectionSaveRollbackIntegrationTest": 2,
                       "DemographicMergeMariaDbIntegrationTest": 8,
                       "Growth0To36FormPersistenceIntegrationTest": 19},
            "bc-flyway": {"FixedJasperReportDaoIntegrationTest": 2, "PatientEndYearStatementTransactionIntegrationTest": 2, "LabRoutingLookupDaoIntegrationTest": 4,
                           "LegacyLabNativeMariaDbIntegrationTest": 7,
                           "ConsultationArchiveDaoIntegrationTest": 1,
                           "ArchiveAuditTimestampIntegrationTest": 2,
                           "CaseManagementNoteLockMariaDbIntegrationTest": 15,
                           "BcBillingNativeMariaDbIntegrationTest": 22,
                           "FormXmlImportServiceIntegrationTest": 7,
                           "EFormApColumnLookupIntegrationTest": 2,
                           "AppointmentDaySheetServiceIntegrationTest": 4,
                           "AntenatalReportDaoIntegrationTest": 5,
                           "EFormGroupDaoIntegrationTest": 9,
                           "BcBillingDaoTransactionIntegrationTest": 4,
                           "LegacyBcTeleplanExportMariaDbIntegrationTest": 8,
                           "BCAR2020FormSaveTransactionIntegrationTest": 15,
                           "DemographicMergeMariaDbIntegrationTest": 8,
                           "Growth0To36FormPersistenceIntegrationTest": 19}}


INBOX_CASES = (
    'shouldCountOnlyTheSelectedPatientLabs_andKeepZeroAndMissingPatientsUnmatched',
    'shouldCountDistinctPatientDocuments_andSeparateUnmatchedAndAcknowledgedRouting',
    'shouldCountDistinctHrmReports_byPatientProviderAndSignoffIndependentlyOfViewed',
)
INBOX_CLASS = 'io.github.carlos_emr.carlos.commn.dao.LegacyLabNativeMariaDbIntegrationTest'
BC_PHYSICAL_CASES = {
    'io.github.carlos_emr.carlos.appointment.service.AppointmentDaySheetServiceIntegrationTest': (
        'shouldReadPhysicalIdsAndEditVersions_beforeAcknowledgingNativeDaySheetRows',),
    'io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingNativeMariaDbIntegrationTest': (
        'shouldRetainNumericTimeRules_fromPhysicalTinyintFlags',
        'shouldPreserveCharacterStatusAndCallerWrites_whenReadingCurrentBillState'),
    'io.github.carlos_emr.carlos.commn.dao.AntenatalReportDaoIntegrationTest': (
        'shouldRetainPhysicalPlannerTextAndTinyintFlags_whenPatientOwnsTheForm',),
}


def verify(reports: Path, output: Path) -> dict:
    if reports.is_symlink() or not reports.is_dir():
        raise EvidenceError("MariaDB report directory is missing or is a symlink")
    paths = sorted(reports.glob("TEST-*.xml"))
    if not paths:
        raise EvidenceError("No MariaDB DAO reports found")
    counts = {mode: dict(tests=0, failures=0, skipped=0) for mode in REQUIRED}
    families = {mode: dict.fromkeys(required, 0) for mode, required in REQUIRED.items()}
    passed_cases = {mode: {family: set() for family in REQUIRED_CASES} for mode in REQUIRED}
    identities = set()
    inbox_cases = {mode: set() for mode in REQUIRED}
    bc_physical_cases = set()
    collisions = []
    lines = []
    for report in paths:
        mode = ("bc-flyway" if report.stem.endswith("-bc-flyway") else
                "flyway" if report.stem.endswith("-flyway") else "generated")
        suffix = "" if mode == "generated" else mode
        suite = report.stem.removeprefix("TEST-")
        if suffix:
            suite = suite.removesuffix("-" + suffix)
        family = suite.rsplit(".", 1)[-1].split("$", 1)[0]
        diagnostics = []
        cases = read_surefire(report, suite, suffix, diagnostics)
        collisions.extend({"mode": mode, **row} for row in diagnostics)
        for case in cases:
            identity = (mode, *case.identity)
            if identity in identities:
                raise EvidenceError("Duplicate test execution across MariaDB reports")
            identities.add(identity)
            counts[mode]["tests"] += 1
            if case.outcome != "pass":
                counts[mode]["failures" if case.outcome == "failure" else "skipped"] += 1
            if case.classname == INBOX_CLASS and not case.container and case.outcome == 'pass':
                inbox_cases[mode].add(case.name)
            if mode == 'bc-flyway' and case.classname == suite and not case.container and case.outcome == 'pass':
                bc_physical_cases.add((case.classname, case.name))
            if family in families[mode] and case.outcome == "pass":
                families[mode][family] += 1
                if family in REQUIRED_CASES and case.classname == suite:
                    passed_cases[mode][family].add(case.name)
            label = f" [{case.container}]" if case.container else ""
            lines.append(f"{mode} {case.outcome}: {case.classname}{label}::{case.name}")
    summary = {"counts": counts, "families": families, "counterCollisions": collisions}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("MariaDB DAO integration results\n" + json.dumps(summary, indent=2)
                      + "\n" + "\n".join(lines) + "\n", encoding="utf-8")
    if counts["generated"]["tests"] < 2500:
        raise EvidenceError("Broad MariaDB DAO selection unexpectedly lost coverage")
    for mode, required in REQUIRED.items():
        if counts[mode]["failures"] or counts[mode]["skipped"]:
            raise EvidenceError(f"MariaDB {mode} contains failed, retried, or skipped cases")
        for family, minimum in required.items():
            if families[mode][family] < minimum:
                raise EvidenceError(f"Required MariaDB {mode} coverage missing: {family}")
        for family, names in REQUIRED_CASES.items():
            missing = names - passed_cases[mode][family]
            if missing:
                raise EvidenceError(f"Required MariaDB {mode} cases missing: {family}: " + ", ".join(sorted(missing)))
    for mode in ('flyway', 'bc-flyway'):
        if not set(INBOX_CASES) <= inbox_cases[mode]:
            raise EvidenceError(f'Required MariaDB {mode} inbox case identities missing')
    if not {(family, name) for family, names in BC_PHYSICAL_CASES.items() for name in names} <= bc_physical_cases:
        raise EvidenceError('Required MariaDB bc-flyway physical-type case identities missing')
    return summary


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reports", type=Path, default=Path("target/surefire-reports"))
    parser.add_argument("--output", type=Path, default=Path("target/mariadb-dao-test-summary.txt"))
    args = parser.parse_args(argv)
    args.output.unlink(missing_ok=True)
    try:
        print(json.dumps(verify(args.reports, args.output), indent=2))
    except Exception as failure:
        reason = str(failure) if isinstance(failure, EvidenceError) else type(failure).__name__
        print(f"MariaDB evidence refused: {reason}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
