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
"""Pure controller protocol tests: no Docker, SQL, Java, or import executions."""
from __future__ import annotations

import copy
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import sys
from types import SimpleNamespace
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location("cds_container_recovery", Path(__file__).with_name("run-cds-container-recovery.py"))
assert SPEC and SPEC.loader
DRIVER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DRIVER)
RUN = "11111111-1111-4111-8111-111111111111"
NONCE = "22222222-2222-4222-8222-222222222222"


def arguments():
    return SimpleNamespace(container="carlos-cds-owned-target", metric_container="carlos-cds-owned-metrics",
                           owner_label="io.carlos.cds.task=recovery-test", network="carlos-cds-test",
                           prepared_root=Path("/tmp/cds-prepared"), launcher="/usr/local/bin/carlos-cds-import",
                           input=DRIVER.SOURCE + "/long.xml", provider="999998", source_namespace="test-namespace",
                           source_profile="pcon-2024-02", docker="docker", docker_socket="/var/run/docker.sock")


def container():
    args = arguments()
    mounts = []
    expected = {DRIVER.CONFIG: ("config", False), DRIVER.SOURCE: ("source", False), DRIVER.STATE: ("state", True),
                DRIVER.DOCUMENTS: ("documents", True), "/gate-evidence": ("evidence", True), "/gate-preparation": ("", True)}
    for path, (leaf, writable) in expected.items():
        mounts.append({"Type": "bind", "Source": str(args.prepared_root) + ("/" + leaf if leaf else ""),
                       "Destination": path, "RW": writable, "Propagation": "rprivate"})
    for index, path in enumerate((args.launcher, "/usr/lib/jvm/java-25-openjdk-amd64", "/usr/local/tomcat/webapps/carlos/WEB-INF/classes",
                                  "/usr/local/tomcat/webapps/carlos/WEB-INF/lib", "/usr/local/tomcat/lib")):
        mounts.append({"Type": "bind", "Source": "/tmp/frozen/" + str(index), "Destination": path,
                       "RW": False, "Propagation": "rprivate"})
    return {"Id": "a" * 64, "Name": "/" + args.container, "Image": "sha256:" + "b" * 64,
            "Path": "/bin/sleep", "Args": ["infinity"], "State": {"Running": True, "Paused": False},
            "Config": {"Image": "mutable:tag", "User": "", "Hostname": "fixed-hostname", "Tty": False,
                       "Env": ["HTTPS_PROXY=secret-value", "OTHER=value"], "Labels": {"io.carlos.cds.task": "recovery-test"}},
            "HostConfig": {"Memory": 768 * DRIVER.MIB, "MemorySwap": 1536 * DRIVER.MIB, "NanoCpus": 1000000000,
                           "ReadonlyRootfs": True, "Privileged": False, "AutoRemove": False, "CapAdd": sorted(DRIVER.CAPS),
                           "CapDrop": ["ALL"], "SecurityOpt": ["no-new-privileges"], "IpcMode": "private",
                           "RestartPolicy": {"Name": "no"}, "NetworkMode": args.network,
                           "Tmpfs": {"/carlos-tmp": "uid=10001,gid=10001,mode=700",
                                     "/run/carlos-cds-delegations": "uid=0,gid=10001,mode=750"}},
            "Mounts": mounts, "NetworkSettings": {"Networks": {args.network: {"NetworkID": "c" * 64,
                                  "Aliases": [args.container, "a" * 12], "IPAMConfig": None, "Links": None}}}}


class RecoveryProtocolTests(unittest.TestCase):
    def test_dependency_file_links_bind_actual_bytes_and_reject_directory_links(self):
        # Run the repository-owned standalone helper as a child, just as Docker
        # does. Fixture paths remain argv data; no input supplies Python source.
        source = DRIVER.PROBE.split('for path in request["immutable"]:')[0]
        source += '\nprint(json.dumps(digest(sys.argv[2])))\n'

        def measured(path):
            result = subprocess.run([sys.executable, "-c", source, "{}", str(path)],
                                    capture_output=True, text=True, timeout=5)
            self.assertLessEqual(len(result.stdout), 4096)
            return result

        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            libraries = base / "libs"
            libraries.mkdir()
            target = base / "dependency.jar"
            target.write_bytes(b"first dependency bytes")
            (libraries / "dependency.jar").symlink_to(target)
            first_result = measured(libraries)
            self.assertEqual(first_result.returncode, 0)
            first = json.loads(first_result.stdout)
            self.assertEqual(first["files"], 1)
            target.write_bytes(b"changed dependency bytes")
            changed = measured(libraries)
            self.assertEqual(changed.returncode, 0)
            self.assertNotEqual(json.loads(changed.stdout)["sha256"], first["sha256"])
            (libraries / "directory").symlink_to(base, target_is_directory=True)
            self.assertNotEqual(measured(libraries).returncode, 0)

    def test_unrelated_read_only_workspace_is_bound_without_scanning_it(self):
        info = container()
        info["Mounts"].append({"Type": "bind", "Source": "/workspace", "Destination": "/workspace",
                               "RW": False, "Propagation": "rprivate"})
        runtime, selected = DRIVER.contract(info, arguments())
        self.assertIn("/workspace", runtime["binds"])
        self.assertNotIn("/workspace", selected)

    def test_actual_docker_top_requires_pid_column_and_no_other_process(self):
        docker = mock.Mock()
        docker.request.return_value = {"Titles": ["PID", "COMMAND"], "Processes": [["123", "/bin/sleep infinity"]]}
        DRIVER.idle(docker, "a" * 64)
        self.assertIn("pid%2Cargs", docker.request.call_args.args[1])
        docker.request.return_value["Processes"].append(["124", "java"])
        with self.assertRaisesRegex(RuntimeError, "NON_IDLE"):
            DRIVER.idle(docker, "a" * 64)

    def test_recreation_preserves_environment_without_printing_it_and_pins_image(self):
        original = container()
        body = DRIVER.recreate_request(original, arguments())
        self.assertEqual(body["Image"], original["Image"])
        self.assertEqual(body["Env"], original["Config"]["Env"])
        self.assertEqual(body["HostConfig"], original["HostConfig"])
        runtime, _ = DRIVER.contract(original, arguments())
        self.assertNotIn("secret-value", json.dumps(runtime))
        self.assertNotIn("a" * 12, body["NetworkingConfig"]["EndpointsConfig"][arguments().network]["Aliases"])
        self.assertEqual(original["Config"]["Image"], "mutable:tag")

    def test_same_runtime_contract_after_exact_image_recreation(self):
        original = container()
        new = copy.deepcopy(original)
        new["Id"] = "d" * 64
        new["Config"]["Image"] = original["Image"]
        new["NetworkSettings"]["Networks"][arguments().network]["Aliases"] = [arguments().container, "d" * 12]
        self.assertEqual(DRIVER.contract(original, arguments())[0], DRIVER.contract(new, arguments())[0])

    def test_rejects_unowned_container_before_any_mutation(self):
        value = container()
        value["Config"]["Labels"] = {}
        with self.assertRaisesRegex(RuntimeError, "OWNERSHIP"):
            DRIVER.contract(value, arguments())

    def test_read_only_metric_container_uses_explicit_identity_without_relabeling(self):
        value = container()
        value["Name"] = "/" + arguments().metric_container
        value["Config"]["Labels"] = {}
        DRIVER.ownership(value, arguments(), metric=True)
        value["Name"] = "/unrelated-production-service"
        with self.assertRaisesRegex(RuntimeError, "NAME"):
            DRIVER.ownership(value, arguments(), metric=True)

    def test_metric_credentials_are_private_to_root_or_actual_controller(self):
        args = arguments()
        args.metric_java = "/usr/lib/jvm/java-25-openjdk-amd64/bin/java"
        args.metric_classpath = "/frozen/classes:/frozen/lib/*"
        args.jdbc_properties = "/run/cds-test/jdbc.properties"
        args.timeout = 60
        selected = {"/usr/lib/jvm/java-25-openjdk-amd64", "/frozen/classes", "/frozen/lib"}
        info = {"Id": "c" * 64, "Mounts": [{"Type": "bind", "Destination": path, "RW": False}
                                           for path in selected]}
        for uid, gid, mode, allowed in ((0, 0, 0o600, True), (1000, 1000, 0o600, True),
                                         (2000, 2000, 0o600, False), (1000, 1000, 0o640, False)):
            value = {"mounts": dict.fromkeys(selected | {args.jdbc_properties}, {}),
                     "private": {args.jdbc_properties: {"uid": uid, "gid": gid, "mode": mode}}}
            with self.subTest(uid=uid, mode=mode), mock.patch.object(DRIVER.os, "getuid", return_value=1000), \
                    mock.patch.object(DRIVER.os, "getgid", return_value=1000), \
                    mock.patch.object(DRIVER, "bounded_exec", return_value=value):
                if allowed:
                    DRIVER.metric_probe(args, info)
                else:
                    with self.assertRaisesRegex(RuntimeError, "CREDENTIALS"):
                        DRIVER.metric_probe(args, info)

    def test_rejects_budgets_capabilities_and_process_changes(self):
        changes = [("Memory", 1024 * DRIVER.MIB), ("NanoCpus", 2000000000), ("Privileged", True),
                   ("CapAdd", [*DRIVER.CAPS, "KILL"]), ("ReadonlyRootfs", False), ("AutoRemove", True)]
        for key, value in changes:
            with self.subTest(key=key):
                info = container()
                info["HostConfig"][key] = value
                with self.assertRaises(RuntimeError):
                    DRIVER.contract(info, arguments())
        info = container()
        info["Path"] = "/usr/bin/java"
        with self.assertRaisesRegex(RuntimeError, "PID1"):
            DRIVER.contract(info, arguments())

    def test_rejects_changed_source_bind_and_writable_artifact(self):
        for path, replacement in ((DRIVER.SOURCE, {"Source": "/tmp/other"}),
                                  ("/usr/local/tomcat/webapps/carlos/WEB-INF/classes", {"RW": True})):
            info = container()
            next(item for item in info["Mounts"] if item["Destination"] == path).update(replacement)
            with self.assertRaises(RuntimeError):
                DRIVER.contract(info, arguments())

    def test_only_safe_operational_console_fields_are_retained(self):
        source = {"event": "RESULT", "runId": RUN, "state": "COMPLETE", "clinicalComplete": True,
                  "pendingUnits": 0, "rawException": "secret source body", "patient": "Private Name",
                  "categories": {"Private Name": 1}, "operation": "resume"}
        safe = DRIVER.safe_row(source)
        self.assertEqual(safe, {"event": "RESULT", "runId": RUN, "state": "COMPLETE", "clinicalComplete": True,
                               "pendingUnits": 0, "operation": "resume"})
        self.assertIsNone(DRIVER.safe_row({"event": "STACKTRACE", "message": "private"}))

    def test_protected_reports_retain_only_digest_and_length(self):
        row = {"event": "PROTECTED_REPORTS", "runId": RUN, "reports": {key: {"protectedPath": "/private/name",
              "bytes": 8, "sha256": "e" * 64} for key in ("event", "error", "receipt", "units")}}
        safe = DRIVER.safe_row(row)
        self.assertNotIn("/private", json.dumps(safe))
        row["reports"].pop("units")
        with self.assertRaisesRegex(RuntimeError, "INCOMPLETE"):
            DRIVER.safe_row(row)

    def test_incomplete_or_sticky_release_cannot_pass(self):
        good = {"clinicalComplete": True, "cleanComplete": True, "reportsDelivered": True, "cleanupComplete": True,
                "resumeRequested": False, "maintenanceReleasePending": False, "maintenanceReleaseState": "RELEASED",
                "pendingUnits": 0, "expectedUnits": 514, "committedUnits": 514, "dryRun": False}
        DRIVER.complete(good)
        for key, value in (("resumeRequested", True), ("pendingUnits", 1), ("committedUnits", 513),
                           ("cleanupComplete", False), ("maintenanceReleaseState", "PENDING"), ("dryRun", True)):
            with self.subTest(key=key), self.assertRaises(RuntimeError):
                DRIVER.complete({**good, key: value})

    def test_accepted_job_must_keep_original_options_and_nonce(self):
        args = arguments()
        command = {"input": args.input, "submissionKey": NONCE, "providerNo": args.provider, "programId": None,
                   "facilityId": None, "sourceNamespace": args.source_namespace, "sourceProfile": args.source_profile,
                   "mergeExisting": False, "dryRun": False, "matchProviderNames": False, "timeshiftDays": 0,
                   "courseId": None, "synthetic": False}
        document = {"command": command, "actor": {"operator": "root", "providerNo": args.provider, "securityNo": None},
                    "snapshot": {"sha256": "e" * 64}, "acceptedRulesetDigest": "f" * 64}
        proof = DRIVER.accepted_binding(document, "a" * 64, args, NONCE)
        self.assertEqual(proof["commandSha256"], DRIVER.digest_json(command))
        for key, value in (("sourceNamespace", "other"), ("matchProviderNames", True), ("submissionKey", RUN)):
            changed = copy.deepcopy(document)
            changed["command"][key] = value
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, "OPTIONS"):
                DRIVER.accepted_binding(changed, "a" * 64, args, NONCE)

    def test_ticket_digest_is_unambiguous_and_uses_network_byte_order(self):
        self.assertNotEqual(DRIVER.argv_digest(["ab", "c"]), DRIVER.argv_digest(["a", "bc"]))
        import hashlib
        self.assertEqual(DRIVER.argv_digest(["x"]), hashlib.sha256(b"\0\0\0\1x").hexdigest())

    def test_killed_import_must_not_have_result_or_success_exit(self):
        for code, rows in ((0, []), (137, [{"event": "RESULT", "runId": RUN}])):
            operation = object.__new__(DRIVER.Operation)
            operation.child = mock.Mock()
            operation.child.wait.return_value = code
            operation.reader = mock.Mock()
            operation.reader.is_alive.return_value = False
            operation.values = lambda: rows
            with self.subTest(code=code), self.assertRaisesRegex(RuntimeError, "KILLED"):
                operation.finish(30, killed=True)

    def test_heap_oom_and_cgroup_oom_stay_hold(self):
        operation = object.__new__(DRIVER.Operation)
        operation.child = mock.Mock()
        operation.child.stdout = io.BytesIO(b"java.lang.OutOfMemoryError: private detail\n")
        operation.rows = []
        operation.failure = False
        operation.oom = False
        operation.lock = mock.MagicMock()
        operation.output = io.StringIO()
        operation.read()
        with self.assertRaisesRegex(RuntimeError, "OPERATION_OOM"):
            operation.values()
        with self.assertRaisesRegex(RuntimeError, "CGROUP_OOM"):
            DRIVER.cgroup({"memory.max": str(768 * DRIVER.MIB), "memory.swap.max": str(768 * DRIVER.MIB),
                           "cpu.max": "100000 100000", "memory.events": "oom 1\noom_kill 0"})

    def test_private_evidence_is_exclusive(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            DRIVER.save(path, "receipt.json", {"runId": RUN})
            self.assertEqual((path / "receipt.json").stat().st_mode & 0o777, 0o600)
            with self.assertRaises(FileExistsError):
                DRIVER.save(path, "receipt.json", {"runId": NONCE})

    def test_full_controller_sequence_replaces_container_and_resumes_once(self):
        """Exercise the complete controller against in-memory daemon/CLI fakes."""
        args = arguments()
        original = container()
        metric = copy.deepcopy(original)
        metric["Id"] = "c" * 64
        metric["Name"] = "/" + args.metric_container
        replacement = copy.deepcopy(original)
        replacement["Id"] = "d" * 64
        replacement["Config"]["Image"] = original["Image"]
        replacement["NetworkSettings"]["Networks"][args.network]["Aliases"] = [args.container, "d" * 12]
        command = {"input": args.input, "submissionKey": NONCE, "providerNo": args.provider, "programId": None,
                   "facilityId": None, "sourceNamespace": args.source_namespace, "sourceProfile": args.source_profile,
                   "mergeExisting": False, "dryRun": False, "matchProviderNames": False, "timeshiftDays": 0,
                   "courseId": None, "synthetic": False}
        document = {"command": command, "actor": {"operator": "root", "providerNo": args.provider, "securityNo": None},
                    "snapshot": {"sha256": "e" * 64}, "acceptedRulesetDigest": "f" * 64}
        memory = {"memory.max": str(768 * DRIVER.MIB), "memory.swap.max": str(768 * DRIVER.MIB),
                  "cpu.max": "100000 100000", "memory.peak": "400000000", "memory.events": "oom 0\noom_kill 0"}
        prepared = {**memory, "mounts": {"frozen": {"sha256": "e" * 64}}, "private": {}, "acceptedDocuments": 0}
        full = {"event": "RESULT", "runId": RUN, "clinicalComplete": True, "cleanComplete": True,
                "reportsDelivered": True, "cleanupComplete": True, "resumeRequested": False,
                "maintenanceReleasePending": False, "maintenanceReleaseState": "RELEASED", "pendingUnits": 0,
                "expectedUnits": 514, "committedUnits": 514, "dryRun": False}
        operations = []
        mutations = []
        state = {"killed": False, "resumed": False}

        class FakeDocker:
            def __init__(self, path):
                pass

            def inspect(self, selector):
                if selector in (args.metric_container, metric["Id"]):
                    return metric
                if selector == replacement["Id"]:
                    return replacement
                value = copy.deepcopy(original)
                if state["killed"]:
                    value["State"].update({"Running": False, "OOMKilled": False, "ExitCode": 137})
                return value

            def accepted(self, container_id, submission):
                self_submission = submission
                if self_submission != NONCE:
                    raise AssertionError("changed submission")
                return document, "a" * 64

            def request(self, method, route, body=None, statuses=(200,)):
                mutations.append((method, route, body))
                if "/kill?" in route:
                    state["killed"] = True
                if "/create?" in route:
                    return {"Id": replacement["Id"]}
                return {}

        class FakeOperation:
            def __init__(self, parsed, container_id, argv, label):
                self.nonce = NONCE if label == "import" else RUN
                self.argv = argv
                self.label = label
                self.child = mock.Mock()
                self.child.poll.return_value = None
                operations.append((container_id, argv, label, self.nonce))

            def values(self):
                return [{"event": "SUBMISSION", "submissionKey": NONCE}, {"event": "ACCEPTED", "runId": RUN}]

            def finish(self, timeout, killed=False):
                if self.label == "import":
                    if not killed:
                        raise AssertionError("import must be killed")
                    return self.values()
                if self.label == "resume":
                    state["resumed"] = True
                rows = [full]
                if self.label == "report":
                    rows.append({"event": "PROTECTED_REPORTS", "runId": RUN})
                return rows

        baseline = {"totalNativePatients": 10, "totalNativeNotes": 272, **{"other" + str(i): 0 for i in range(14)}}

        def fake_metrics(parsed, docker, selector):
            if selector == "fixture":
                value = dict(baseline)
                if state["resumed"]:
                    value["totalNativePatients"] += 1
                    value["totalNativeNotes"] += 256
                return value
            return {"nativeTargets": 1, "nativeNotes": 256 if state["resumed"] else 3, "holds": 0,
                    "checkpoints": 514 if state["resumed"] else 8, "expectedUnits": 514, "cancelRequested": False}

        def fake_bounded(command, timeout):
            if command[-1] == "--capabilities":
                return {"protocol": 1, "java": 25, "cliHeapMiB": 512, "submissionLookup": True,
                        "operations": ["import", "status", "resume", "cancel", "report"]}
            return memory

        with tempfile.TemporaryDirectory() as directory:
            base_argv = ["driver", "--disposable", "--container", args.container, "--owner-label", args.owner_label,
                         "--prepared-root", str(args.prepared_root), "--input", args.input, "--provider", args.provider,
                         "--source-namespace", args.source_namespace, "--source-profile", args.source_profile,
                         "--metric-container", args.metric_container, "--metric-classpath", "/helper:/classes",
                         "--jdbc-properties", "/private/jdbc.properties"]
            with mock.patch.object(DRIVER, "Docker", FakeDocker), mock.patch.object(DRIVER, "Operation", FakeOperation), \
                    mock.patch.object(DRIVER, "metrics", fake_metrics), mock.patch.object(DRIVER, "probe", return_value=prepared), \
                    mock.patch.object(DRIVER, "metric_probe", return_value={"mounts": {}}), \
                    mock.patch.object(DRIVER, "idle"), mock.patch.object(DRIVER, "bounded_exec", fake_bounded), \
                    mock.patch("sys.stdout", new_callable=io.StringIO):
                preflight = Path(directory, "preflight")
                with mock.patch("sys.argv", [*base_argv, "--evidence", str(preflight)]):
                    DRIVER.main()
                self.assertEqual(operations, [])
                self.assertEqual(mutations, [])
                fingerprint = json.loads((preflight / "preflight.json").read_text())["fingerprint"]
                evidence = Path(directory, "execute")
                with mock.patch("sys.argv", [*base_argv, "--evidence", str(evidence), "--execute", "--released-fingerprint", fingerprint]):
                    DRIVER.main()
                proof = json.loads((evidence / "proof.json").read_text())
                self.assertEqual(proof["containerRecovery"], "PASS")
                self.assertEqual(proof["runId"], RUN)
                self.assertEqual(proof["nativeNotes"], 256)
                self.assertTrue((evidence / "accepted-run.json").exists())
                self.assertTrue((evidence / "cgroup-before-kill.json").exists())
                self.assertTrue((evidence / "native-after-kill.json").exists())
        self.assertEqual([item[2] for item in operations], ["import", "submission-status", "resume", "status", "report"])
        self.assertIn(RUN, operations[2][1])
        self.assertIn(NONCE, operations[1][1])
        self.assertEqual([item[1] for item in mutations if "/kill?" in item[1]],
                         ["/containers/" + original["Id"] + "/kill?signal=SIGKILL"])
        deletion = next(item for item in mutations if item[0] == "DELETE")
        self.assertEqual(deletion[1], "/containers/" + original["Id"])
        creation = next(item for item in mutations if "/create?" in item[1])
        self.assertEqual(creation[2]["Image"], original["Image"])


if __name__ == "__main__":
    unittest.main()
