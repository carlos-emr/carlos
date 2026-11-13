#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Export scaling evidence must not manufacture a passing export or hide per-read authorization."""

import copy
import importlib.util
import json
from pathlib import Path
import re
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location(
    "cds_export_scaling", Path(__file__).with_name("run-cds-export-scaling.py")
)
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)
REPOSITORY = Path(__file__).resolve().parents[2]


def verified_case(label="chart-128", origin="native", patients=1, records=128, **changes):
    notes = patients * records
    case = {
        "label": label,
        "origin": origin,
        "patients": patients,
        "notes": notes,
        "labs": notes,
        "exitCode": 0,
        "elapsedSeconds": 40.0,
        "budgetedStatements": 2500 + 6 * notes,
        "privilegeStatements": 120,
        "serviceJvmPeakRssBytes": 600 * GATE.MIB,
        "cliPeakRssBytes": 30 * GATE.MIB,
        "export": {
            "accepted": 1,
            "result": {
                "state": "READY",
                "totalPatients": patients,
                "processedPatients": patients,
                "blockedPatients": 0,
                "complete": True,
                "heldBack": False,
            },
        },
        "package": {
            "members": patients + 2,
            "xmlMembers": patients,
            "patientRecords": patients,
            "clinicalNotes": notes,
            "laboratoryResults": notes,
        },
    }
    case.update(changes)
    return case


# The measured native shape (docs/cds-export.md, "Installed export scaling lane"): a fixed command
# cost, then per extra chart and per extra note (one note and one laboratory result).
MEASURED = {
    "perPatient": 7249,
    "privilegePerPatient": 2962,
    "perNote": 23.2,
    "privilegePerNote": 0.50,
}
# The same lane on the revision before the per-patient authorization session
# (CdsExportPatientAuthorization): chart-128 was stopped after 1,899 s, still capturing, having
# issued 1,068,468 statements and 659,491 authorization-table reads (the complete export with the
# session: 136 s, 10,737 and 3,249). Lower bounds per note, against the one-note chart's cost.
PRE_SESSION = {
    "perNote": (1068468 - 8122) / 127,
    "privilegePerNote": (659491 - 3201) / 127,
}


def series(
    origin="native",
    per_note=MEASURED["perNote"],
    privilege_per_note=MEASURED["privilegePerNote"],
    per_patient=MEASURED["perPatient"],
    privilege_per_patient=MEASURED["privilegePerPatient"],
):
    """Both growth series of one origin with a fixed cost and linear per-note/per-patient growth."""
    cases = []
    for definition in GATE.case_definitions(origin + "-patients") + GATE.case_definitions(origin + "-charts"):
        notes = definition["patients"] * definition["records"]
        cases.append(
            verified_case(
                definition["label"],
                origin,
                definition["patients"],
                definition["records"],
                budgetedStatements=round(900 + per_patient * definition["patients"] + per_note * notes),
                privilegeStatements=round(
                    240 + privilege_per_patient * definition["patients"] + privilege_per_note * notes
                ),
            )
        )
    return cases


class ExportScalingEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def test_each_origin_grows_patients_and_one_chart_in_separate_profiles(self):
        self.assertEqual(
            GATE.PROFILES,
            ("native-patients", "native-charts", "imported-patients", "imported-charts"),
        )
        for origin in GATE.ORIGINS:
            with self.subTest(origin=origin):
                patients = GATE.case_definitions(origin + "-patients")
                charts = GATE.case_definitions(origin + "-charts")
                self.assertEqual([case["label"] for case in patients], ["patients-1", "patients-8", "patients-32"])
                self.assertEqual([case["label"] for case in charts], ["chart-128", "chart-512", "chart-2048"])
                self.assertTrue(all(case["origin"] == origin for case in patients + charts))
                self.assertEqual([case["records"] for case in patients], [1, 1, 1])
                self.assertEqual([case["patients"] for case in charts], [1, 1, 1])
        for unknown in ("scaling", "native", "native-other"):
            with self.assertRaises(ValueError):
                GATE.case_definitions(unknown)

    def test_case_budget_is_linear_in_patients_and_notes_and_refuses_impossible_sizes(self):
        budget = GATE.BUDGETS["native"]
        small = GATE.case_budget(budget, 1, 128)
        large = GATE.case_budget(budget, 1, 2048)
        self.assertEqual(
            large["statements"] - small["statements"], budget["statementsPerNote"] * 1920
        )
        self.assertEqual(
            large["privilegeStatements"] - small["privilegeStatements"],
            budget["privilegeStatementsPerNote"] * 1920,
        )
        self.assertEqual(large["elapsedSeconds"] - small["elapsedSeconds"], budget["secondsPerNote"] * 1920)
        self.assertEqual(small["serviceRssBytes"], budget["serviceRssMiB"] * GATE.MIB)
        for patients, notes in ((0, 1), (2, 1), (1.0, 1), (True, 1)):
            with self.subTest(patients=patients, notes=notes):
                with self.assertRaises(ValueError):
                    GATE.case_budget(budget, patients, notes)

    def test_ready_export_with_exact_package_has_no_functional_or_budget_failure(self):
        case = verified_case()
        self.assertEqual(GATE.functional_failures(case), [])
        self.assertEqual(GATE.budget_failures(case, GATE.BUDGETS["native"]), [])

    def test_held_unready_or_unreconciled_exports_are_functional_failures_not_budgets(self):
        mutations = {
            "EXPORT_PROCESS_FAILED": lambda case: case.update(exitCode=5),
            "EXPORT_NOT_READY": lambda case: case["export"]["result"].update(state="REVIEW"),
            "EXPORT_PATIENTS_HELD": lambda case: case["export"]["result"].update(
                blockedPatients=1, heldBack=True
            ),
            "EXPORT_PATIENT_COUNT": lambda case: case["export"]["result"].update(
                processedPatients=0
            ),
            "EXPORT_RECORD_COUNT": lambda case: case["package"].update(clinicalNotes=127),
        }
        for code, mutate in mutations.items():
            with self.subTest(code=code):
                case = verified_case()
                mutate(case)
                failures = GATE.functional_failures(case)
                self.assertIn(code, failures)
                self.assertTrue(set(failures) <= GATE.FUNCTIONAL_FAILURES)
        for missing in ({"export": {}}, {"export": {"accepted": 0, "result": {}}}, {"package": None}):
            with self.subTest(missing=missing):
                case = verified_case(**missing)
                self.assertIn("EXPORT_EVIDENCE_MISSING", GATE.functional_failures(case))

    def test_a_missing_negative_or_exceeded_measurement_fails_its_own_budget(self):
        budget = GATE.BUDGETS["native"]
        limits = GATE.case_budget(budget, 1, 128)
        for field, code, exceeded in (
            ("budgetedStatements", "STATEMENT_BUDGET", limits["statements"] + 1),
            ("privilegeStatements", "PRIVILEGE_STATEMENT_BUDGET", limits["privilegeStatements"] + 1),
            ("elapsedSeconds", "ELAPSED_BUDGET", limits["elapsedSeconds"] + 0.5),
            ("serviceJvmPeakRssBytes", "SERVICE_RSS_BUDGET", limits["serviceRssBytes"] + 1),
            ("cliPeakRssBytes", "CLI_RSS_BUDGET", limits["cliRssBytes"] + 1),
        ):
            for value in (None, -1, "1", exceeded):
                with self.subTest(field=field, value=value):
                    failures = GATE.budget_failures(verified_case(**{field: value}), budget)
                    self.assertEqual(failures, [code])
                    self.assertTrue(set(failures) <= GATE.BUDGET_FAILURES)

    def test_marginal_note_budget_fails_when_privilege_checks_return_to_every_read(self):
        """The census regression: privilege queries re-run per note instead of per patient.

        Before CdsExportPatientAuthorization the capture, reconciliation, mapping and emission
        steps re-ran the chart/category privilege and note program/role queries for every note
        (about fifteen access checks per note, each several authorization-table reads). The measured
        pre-session counters (PRE_SESSION) and a single extra read per note must both fail.
        """
        charts = frozenset({"charts"})
        for origin in GATE.ORIGINS:
            budget = GATE.BUDGETS[origin]
            with self.subTest(origin=origin, revision="per-patient session"):
                current = GATE.evaluate_marginal(series(origin), budget, required=charts)
                self.assertEqual(current["failures"], [])
                self.assertAlmostEqual(
                    current["values"]["privilegeStatementsPerNote"], MEASURED["privilegePerNote"], places=2
                )
            with self.subTest(origin=origin, revision="per-read checks"):
                regressed = GATE.evaluate_marginal(
                    series(
                        origin,
                        per_note=PRE_SESSION["perNote"],
                        privilege_per_note=PRE_SESSION["privilegePerNote"],
                    ),
                    budget,
                    required=charts,
                )
                # Thousands of times over; a single extra read per note already fails (below).
                self.assertGreater(
                    regressed["values"]["privilegeStatementsPerNote"],
                    1000 * budget["marginalPrivilegeStatementsPerNote"],
                )
                self.assertIn("MARGINAL_NOTE_PRIVILEGE_BUDGET", regressed["failures"])
                self.assertIn("MARGINAL_NOTE_STATEMENT_BUDGET", regressed["failures"])
                self.assertTrue(set(regressed["failures"]) <= GATE.MARGINAL_FAILURES)
            with self.subTest(origin=origin, revision="one extra privilege read per note"):
                # Even a single authorization-table read per note is a per-read regression.
                regressed = GATE.evaluate_marginal(
                    series(
                        origin,
                        per_note=MEASURED["perNote"] + 1,
                        privilege_per_note=MEASURED["privilegePerNote"] + 1,
                    ),
                    budget,
                    required=charts,
                )
                self.assertEqual(regressed["failures"], ["MARGINAL_NOTE_PRIVILEGE_BUDGET"])

    def test_complete_profile_requires_its_own_growth_series_but_a_subset_may_omit_it(self):
        budget = GATE.BUDGETS["native"]
        charts_only = [case for case in series() if case["label"].startswith("chart-")]
        self.assertEqual(
            GATE.evaluate_marginal(charts_only, budget, required=frozenset({"charts"}))["failures"],
            [],
        )
        self.assertEqual(
            GATE.evaluate_marginal(charts_only, budget, required=frozenset({"patients"}))["failures"],
            ["MARGINAL_PATIENT_STATEMENT_BUDGET", "MARGINAL_PATIENT_PRIVILEGE_BUDGET"],
        )
        self.assertEqual(
            GATE.evaluate_marginal(charts_only[:1], budget, required=frozenset({"charts"}))["failures"],
            ["MARGINAL_NOTE_STATEMENT_BUDGET", "MARGINAL_NOTE_PRIVILEGE_BUDGET"],
        )
        subset = GATE.evaluate_marginal(charts_only[:1], budget, required=frozenset())
        self.assertEqual(subset["failures"], [])
        self.assertIsNone(subset["values"]["statementsPerNote"])
        self.assertIsNone(GATE.marginal(charts_only[:1], "budgetedStatements", "notes"))

    def test_only_the_delegated_export_engine_and_root_adapter_are_measured(self):
        java = b"/usr/lib/jvm/java-25-openjdk-amd64/bin/java"
        engine = [java, b"-Xmx512m", b"-cp", b"x", GATE.EXPORT_MAIN, b"--operation", b"export",
                  b"--provider", b"999998", b"--patients-file", b"/opt/selection.json"]
        self.assertTrue(GATE.is_export_command(engine, "999998"))
        self.assertFalse(GATE.is_export_command(engine, "111111"))
        self.assertFalse(GATE.is_export_command([java, b"-Xmx128m", GATE.EXPORT_MAIN, b"--capabilities"], "999998"))
        download = [part if part != b"export" else b"download" for part in engine]
        self.assertFalse(GATE.is_export_command(download, "999998"))
        self.assertFalse(GATE.is_export_command(engine + [b"--operation", b"export"], "999998"))
        cli = [b"python3", b"-m", b"carlos_ctl.cli", b"cds-export", b"--provider", b"999998", b""]
        self.assertTrue(GATE.is_cli_command(cli))
        self.assertFalse(GATE.is_cli_command(cli[:3] + [b"cds-import"] + cli[4:]))
        self.assertFalse(GATE.is_cli_command(cli + [b"--download", b"run"]))
        self.assertFalse(GATE.is_cli_command([b"/bin/sh", b"/usr/sbin/carlos-ctl", b"cds-export", b""]))
        supervisor = [b"/usr/bin/python3", b"run-cds-export-scaling.py", b"--supervise", b"r",
                      b"--", b"/usr/sbin/carlos-ctl", b"cds-export", b""]
        self.assertFalse(GATE.is_cli_command(supervisor))

    def test_operator_log_projection_keeps_the_finite_result_but_never_the_handle(self):
        run = "0f1e2d3c-4b5a-4968-8776-655443322110"
        log = self.root / "export.log"
        lines = [
            {"event": "INITIALIZING", "operation": "export"},
            {"event": "SUBMISSION", "submissionKey": "scaling-key"},
            {"event": "ACCEPTED", "runId": run},
            {"event": "PROGRESS", "runId": run, "state": "RUNNING"},
            {"event": "RESULT", "runId": run, "state": "READY", "totalPatients": 1,
             "processedPatients": 1, "blockedPatients": 0, "complete": True, "heldBack": False,
             "safeCode": None, "operator": "unix:root", "unexpected": "FAKE-SCALING value"},
        ]
        text = "\n".join(json.dumps(line) for line in lines) + "\n"
        text += "carlos-ctl: CDS_EXPORT_PROGRESS: current authorized status is temporarily unavailable\n"
        text += "a stray line naming FAKE-SCALING and /opt/cds-export-scaling\n"
        log.write_text(text)
        projection, handle = GATE.export_events(log)
        self.assertEqual(handle, run)
        self.assertEqual(projection["accepted"], 1)
        self.assertEqual(projection["eventCounts"]["PROGRESS"], 1)
        self.assertEqual(projection["result"]["state"], "READY")
        self.assertNotIn("runId", projection["result"])
        self.assertNotIn("unexpected", projection["result"])
        self.assertEqual(projection["diagnosticPrefixes"], {"CDS_EXPORT_PROGRESS": 1})
        rendered = json.dumps(projection)
        for private in (run, "FAKE-SCALING", "/opt/", "unix:root"):
            self.assertNotIn(private, rendered)
        for invalid in (
            '{"event": "SURPRISE"}\n',
            '{"event": "ACCEPTED", "runId": "not-a-handle"}\n',
            json.dumps({"event": "ACCEPTED", "runId": run}) + "\n"
            + json.dumps({"event": "ACCEPTED", "runId": run.replace("0f", "1f")}) + "\n",
            "x" * 70000 + "\n",
        ):
            with self.subTest(invalid=invalid[:40]):
                log.write_text(invalid)
                with self.assertRaises(RuntimeError):
                    GATE.export_events(log)
        log.write_text(json.dumps(dict(lines[4], safeCode="free text")) + "\n")
        self.assertEqual(GATE.export_events(log)[0]["result"]["safeCode"], "UNRECOGNIZED")

    def test_public_evidence_whitelists_counts_and_refuses_paths_handles_and_values(self):
        case = verified_case(
            selection="/opt/cds-export-scaling/selection/native-chart-128.json",
            runId="0f1e2d3c-4b5a-4968-8776-655443322110",
            patientIds=[101, 102],
            kernelResources={"pid": 4242, "exitCode": 0, "largestProcessPeakRssBytes": 1},
        )
        public = GATE.public_case(case)
        for private in ("selection", "runId", "patientIds"):
            self.assertNotIn(private, public)
        self.assertEqual(public["kernelResources"], {"exitCode": 0, "largestProcessPeakRssBytes": 1})
        GATE.require_case_fits(case)
        for leaked in (
            {"label": "/opt/cds-export-scaling/delivery/package.bin"},
            {"failureClass": "FAKE-SCALING-1234"},
            {"phase": "0f1e2d3c-4b5a-4968-8776-655443322110"},
        ):
            with self.subTest(leaked=leaked):
                with self.assertRaises(RuntimeError):
                    GATE.require_case_fits(verified_case(**leaked))

    def test_package_counts_patient_records_notes_and_results_from_every_patient_member(self):
        package = self.root / "package.zip"
        record = (
            '<?xml version="1.0"?><OmdCds xmlns="cds" xmlns:cdsd="cds_dt"><PatientRecord>'
            "<Demographics/>{notes}{labs}</PatientRecord></OmdCds>"
        )
        with zipfile.ZipFile(package, "w") as archive:
            archive.writestr("ReadMe.txt", "synthetic")
            archive.writestr("ExportEventLog.csv", "synthetic")
            for index, size in enumerate((3, 5)):
                archive.writestr(
                    f"Patients/patient-{index}.xml",
                    record.format(
                        notes="<ClinicalNotes><MyClinicalNotesContent>n</MyClinicalNotesContent></ClinicalNotes>" * size,
                        labs="<LaboratoryResults><LabTestCode>c</LabTestCode></LaboratoryResults>" * size,
                    ),
                )
            archive.writestr("Patients/elsewhere.xml", '<Other xmlns="other"><ClinicalNotes/></Other>')
        counted = GATE.package_counts(package)
        self.assertEqual(counted["members"], 5)
        self.assertEqual(counted["xmlMembers"], 3)
        self.assertEqual(counted["patientRecords"], 2)
        self.assertEqual(counted["clinicalNotes"], 8)
        self.assertEqual(counted["laboratoryResults"], 8)
        with self.assertRaises(RuntimeError):
            GATE.package_counts(package, maximum_bytes=64)
        with self.assertRaises(RuntimeError):
            GATE.package_counts(package, maximum_members=4)
        unsafe = self.root / "unsafe.zip"
        with zipfile.ZipFile(unsafe, "w") as archive:
            archive.writestr("../escape.xml", "<x/>")
        with self.assertRaises(RuntimeError):
            GATE.package_counts(unsafe)
        hostile = self.root / "hostile.zip"
        with zipfile.ZipFile(hostile, "w") as archive:
            archive.writestr(
                "patient.xml",
                '<?xml version="1.0"?><!DOCTYPE x [<!ENTITY a "aaaa">]><OmdCds xmlns="cds">&a;</OmdCds>',
            )
        with self.assertRaises(Exception):
            GATE.package_counts(hostile)

    def test_unified_and_v1_cgroups_bind_the_same_limits_and_refuse_an_oom_kill(self):
        unified = self.root / "unified"
        unified.mkdir()
        (unified / "cgroup.controllers").write_text("cpu memory\n")
        (unified / "memory.max").write_text(str(1536 * GATE.MIB) + "\n")
        (unified / "cpu.max").write_text("200000 100000\n")
        (unified / "memory.events").write_text("low 0\nhigh 0\nmax 3\noom 0\noom_kill 0\noom_group_kill 0\n")
        (unified / "cpu.stat").write_text("usage_usec 10\n")
        legacy = self.root / "legacy"
        (legacy / "memory").mkdir(parents=True)
        (legacy / "cpu").mkdir()
        (legacy / "memory/memory.limit_in_bytes").write_text(str(1536 * GATE.MIB) + "\n")
        (legacy / "cpu/cpu.cfs_quota_us").write_text("200000\n")
        (legacy / "cpu/cpu.cfs_period_us").write_text("100000\n")
        (legacy / "memory/memory.oom_control").write_text("oom_kill_disable 0\nunder_oom 0\noom_kill 0\n")
        (legacy / "cpu/cpu.stat").write_text("nr_periods 1\nnr_throttled 0\nthrottled_time 0\n")
        for root, version, events, killed in (
            (unified, 2, "memory.events", "oom 0\noom_kill 1\noom_group_kill 0\n"),
            (legacy, 1, "memory/memory.oom_control", "oom_kill_disable 0\nunder_oom 0\noom_kill 1\n"),
        ):
            with self.subTest(version=version):
                limits = GATE.cgroup_limits(root)
                self.assertEqual(limits["version"], version)
                GATE.require_limits(limits, 1536, 2)
                for memory, cpus in ((1024, 2), (1536, 1)):
                    with self.assertRaises(RuntimeError):
                        GATE.require_limits(limits, memory, cpus)
                initial = GATE.cgroup_counters(root)
                GATE.require_no_oom(initial, GATE.cgroup_counters(root))
                (root / events).write_text(killed)
                with self.assertRaises(RuntimeError):
                    GATE.require_no_oom(initial, GATE.cgroup_counters(root))
        unlimited = self.root / "unlimited"
        (unlimited / "memory").mkdir(parents=True)
        (unlimited / "cpu").mkdir()
        (unlimited / "memory/memory.limit_in_bytes").write_text("9223372036854771712\n")
        (unlimited / "cpu/cpu.cfs_quota_us").write_text("-1\n")
        (unlimited / "cpu/cpu.cfs_period_us").write_text("100000\n")
        with self.assertRaises(RuntimeError):
            GATE.require_limits(GATE.cgroup_limits(unlimited), None, 2)

    def test_privilege_reads_have_their_own_class_only_in_the_export_classifier(self):
        row = ["a" * 32, "b" * 64, "PRIVILEGE_READ", "7", "9"]
        snapshot = GATE.GATE.statement_digest_snapshot([row], GATE.STATEMENT_CLASSES)
        self.assertEqual(snapshot["a" * 32]["statementClass"], "PRIVILEGE_READ")
        with self.assertRaises(RuntimeError):
            GATE.GATE.statement_digest_snapshot([row])
        self.assertTrue(GATE.GATE.STATEMENT_CLASSES < GATE.STATEMENT_CLASSES)

    def test_classifier_names_only_authorization_tables_before_ledger_and_native_reads(self):
        sql = GATE.STATEMENT_DIGEST_SQL
        order = [sql.index("'" + name + "'") for name in (
            "OBSERVATION", "SESSION_CONTROL", "PRIVILEGE_READ", "LEDGER_READ", "NATIVE_READ")]
        self.assertEqual(order, sorted(order))
        pattern = re.search(r"REGEXP '(`\([^']+\)`)' THEN 'PRIVILEGE_READ'", sql)[1]
        privilege = re.compile(pattern, re.IGNORECASE)
        for text, expected in (
            ("SELECT `p1_0` . `role_name` FROM `secRole` `p1_0` WHERE `p1_0` . `role_no` = ?", True),
            ("SELECT `objectName` FROM `secObjPrivilege` WHERE `roleUserGroup` IN (...)", True),
            ("SELECT DISTINCT `role_id` FROM `program_provider` WHERE `provider_no` = ?", True),
            ("SELECT ? FROM `admission` WHERE `client_id` = ? LIMIT ?", True),
            ("SELECT `program_no` , `reporter_caisi_role` FROM `casemgmt_note` WHERE `note_id` = ?", False),
            ("SELECT `admission_date` FROM `demographic` WHERE `demographic_no` = ?", False),
            ("SELECT * FROM `cds_export_run` WHERE `id` = ?", False),
        ):
            with self.subTest(text=text):
                self.assertEqual(bool(privilege.search(text)), expected)

    def test_complete_public_proof_of_six_cases_fits_the_bounded_artifact_copy(self):
        digest = {
            "scope": "exclusive server",
            "statementCount": 100000,
            "countsByClass": {name: 1000 for name in GATE.STATEMENT_CLASSES},
            "digestShapeCount": 400,
            "completeDigestSetSha256": "c" * 64,
            "topDigestLimit": 64,
            "omittedDigestShapeCount": 336,
            "omittedStatementCount": 1000,
            "omittedTimerPicoseconds": 10**15,
            "digests": [
                {"digest": "%032x" % index, "shapeSha256": "d" * 64, "statementClass": "NATIVE_READ",
                 "count": 10**6, "timerPicoseconds": 10**15}
                for index in range(64)
            ],
        }
        cases = []
        for case in series("imported"):
            case = copy.deepcopy(case)
            case.update(statementDigests=digest, budget=GATE.case_budget(
                GATE.BUDGETS["imported"], case["patients"], case["notes"]))
            cases.append(GATE.public_case(case))
        proof = {"profile": "imported", "cases": cases, "budgets": GATE.BUDGETS["imported"],
                 "marginal": GATE.evaluate_marginal(series("imported"), GATE.BUDGETS["imported"],
                                                    required=frozenset({"charts"}))}
        self.assertLess(len(json.dumps(proof, sort_keys=True, indent=2)), 1 << 20)

    def test_ci_runs_both_export_profiles_on_the_shared_owned_runtime_and_requires_every_case(self):
        source = (REPOSITORY / "scripts/ci/run-cds-performance-ci.py").read_text()
        for profile in GATE.PROFILES:
            profile = "export-" + profile
            self.assertIn('"' + profile + '"', source)
        self.assertIn('"scripts/ci/run-cds-export-scaling.py"', source)
        self.assertIn('"scripts/ci/CdsExportScalingFixture.java"', source)
        self.assertIn('export and proof.get("completeCaseSet") is not True', source)
        self.assertIn('"test_run_cds_export_scaling.py"', source)
        self.assertNotIn("--cases", source)

    def test_workflow_mirrors_the_import_lane_pins_and_publishes_only_sanitized_evidence(self):
        workflow = (REPOSITORY / ".github/workflows/cds-export-performance.yml").read_text()
        reference = (REPOSITORY / ".github/workflows/cds-import-performance.yml").read_text()
        pins = set(re.findall(r"uses: (\S+@[0-9a-f]{40})", workflow))
        self.assertTrue(pins)
        self.assertTrue(pins <= set(re.findall(r"uses: (\S+@[0-9a-f]{40})", reference)))
        self.assertNotRegex(workflow, r"uses: (?!\./)\S+@(?![0-9a-f]{40}\b)")
        self.assertIn("name: Installed CDS export ${{ matrix.profile }}", workflow)
        self.assertIn(
            "profile: [native-patients, native-charts, imported-patients, imported-charts]", workflow
        )
        self.assertIn("--profile 'export-${{ matrix.profile }}'", workflow)
        self.assertIn("if: always()", workflow)
        self.assertIn("path: target/cds-performance-*/public/*", workflow)
        self.assertIn("persist-credentials: false", workflow)
        for path in (
            "scripts/ci/run-cds-export-scaling.py",
            "scripts/ci/test_run_cds_export_scaling.py",
            "scripts/ci/CdsExportScalingFixture.java",
            "scripts/ci/run-cds-performance.py",
            "scripts/ci/run-cds-performance-ci.py",
            "scripts/ci/cds-performance-runtime.Dockerfile",
            "debian/carlos-ctl.pin",
            ".github/workflows/cds-export-performance.yml",
        ):
            with self.subTest(path=path):
                self.assertIn("'" + path + "'", workflow)


if __name__ == "__main__":
    unittest.main()
