# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The deployment seam the OSCAR 19 import runs on.

Two contracts, and they matter in opposite directions:

* the deb's own answers must not have changed when they moved behind an
  object -- the rehearsal proves the whole import, these pin the pieces;
* every substrate question must actually be ASKED of that object, or a
  port that answers differently is ignored and writes into the deb's
  paths on a host that has none of them.

Run (from debian/assets):
    python3 -m unittest carlos_ctl.tests.test_host -v
"""

import os
import shutil
import tempfile
import unittest
from unittest import mock

from carlos_ctl import o19host, o19import


class TestTheDebsOwnAnswers(unittest.TestCase):

    """`Host` IS the deb package's deployment."""

    def setUp(self):
        self.host = o19host.Host()

    def test_the_workspace_and_documents_are_the_packaged_paths(self):
        self.assertEqual(self.host.state_dir,
                         "/var/lib/carlos-emr/o19-import")
        self.assertEqual(self.host.documents_root,
                         "/var/lib/carlos-emr/OscarDocument")

    def test_the_client_runs_over_the_unix_socket_as_root(self):
        self.assertEqual(self.host.client_base_argv(None),
                         ["mariadb", "--protocol=socket", "--user=root"])

    def test_the_development_seam_replaces_the_connection_tail(self):
        self.assertEqual(
            self.host.client_base_argv(["--socket=/tmp/s", "-uroot"]),
            ["mariadb", "--socket=/tmp/s", "-uroot"])

    def test_the_client_needs_no_credential_in_its_environment(self):
        # root over the socket: there is nothing to pass, and passing an
        # empty environment would run every client without PATH
        self.assertEqual(self.host.client_env(), {})
        self.assertIsNone(o19import._client_env())

    def test_a_hosts_client_credential_is_merged_not_substituted(self):
        host = o19host.Host()
        host.client_env = lambda: {"MYSQL_PWD": "s3cret"}
        with mock.patch.object(o19import, "HOST", host):
            env = o19import._client_env()
        self.assertEqual(env["MYSQL_PWD"], "s3cret")
        # ...and this process's own environment survives, or the client
        # runs without PATH
        self.assertEqual(env.get("PATH"), os.environ.get("PATH"))

    def test_the_staging_credential_is_a_private_defaults_file(self):
        work = tempfile.mkdtemp(prefix="o19host-")
        self.addCleanup(shutil.rmtree, work, ignore_errors=True)
        cnf = os.path.join(work, "client.cnf")
        self.assertEqual(self.host.stage_credential("pa\\ss", cnf), {})
        self.assertEqual(os.stat(cnf).st_mode & 0o777, 0o600)
        with open(cnf) as fh:
            text = fh.read()
        self.assertIn("user=" + o19host.STAGING_USER, text)
        # a backslash in a password is escaped for the option-file
        # parser, or the client reads a different secret than the DDL set
        self.assertIn("password=pa\\\\ss", text)
        self.host.clear_stage_credential(cnf)
        self.assertFalse(os.path.exists(cnf))

    def test_clearing_a_credential_that_is_not_there_is_not_an_error(self):
        # it is called from a `finally`, after a failure that may have
        # happened before the file existed
        self.host.clear_stage_credential("/nonexistent/client.cnf")


class TestEverySubstrateQuestionIsAsked(unittest.TestCase):

    """A port answers differently only if the import ASKS.

    Each of these fails if `o19import` reaches for the deb's constant
    instead of the host -- which on carlos-podman means writing the
    workspace, the documents and the client argv into paths that host
    does not have."""

    class FakeHost(o19host.Host):
        label = "a test deployment"

        @property
        def state_dir(self):
            return "/somewhere/else/o19-import"

        @property
        def documents_root(self):
            return "/somewhere/else/Documents"

        def client_base_argv(self, mariadb_args):
            return ["podman", "exec", "-i", "db", "mariadb"]

        def client_env(self):
            return {"MYSQL_PWD": "root-pw"}

    def setUp(self):
        self.host = self.FakeHost()
        patch = mock.patch.object(o19import, "HOST", self.host)
        patch.start()
        self.addCleanup(patch.stop)

    def test_the_client_argv_is_the_hosts(self):
        recorded = {}

        def fake_run(argv, **kw):
            recorded["argv"] = argv
            recorded["env"] = kw.get("env")
            return mock.Mock(returncode=0, stdout="", stderr="")

        with mock.patch.object(o19import, "run", fake_run):
            o19import.make_query(None)("SELECT 1")
        self.assertEqual(recorded["argv"][:5],
                         ["podman", "exec", "-i", "db", "mariadb"])
        # ...with the credential in the environment, never on the argv
        self.assertEqual(recorded["env"]["MYSQL_PWD"], "root-pw")
        self.assertNotIn("root-pw", " ".join(recorded["argv"]))

    def test_the_streaming_client_argv_is_the_hosts_too(self):
        # the archive export uses a second client; a port that changed
        # only the first would stream from the wrong database
        recorded = {}

        class FakeProc:
            def __init__(self, argv, **kw):
                recorded["argv"] = argv
                recorded["env"] = kw.get("env")
                self.stdin = mock.Mock()
                self.stdout = mock.Mock()
                self.stdout.read.return_value = b""
                self.stderr = mock.Mock()
                self.stderr.read.return_value = b""
                self.returncode = 0

            def wait(self):
                return 0

        with mock.patch.object(o19import.subprocess, "Popen", FakeProc):
            list(o19import.make_row_stream(None)("SELECT 1"))
        self.assertEqual(recorded["argv"][:5],
                         ["podman", "exec", "-i", "db", "mariadb"])
        self.assertEqual(recorded["env"]["MYSQL_PWD"], "root-pw")

    def test_the_workspace_is_the_hosts(self):
        self.assertEqual(o19import.HOST.state_dir,
                         "/somewhere/else/o19-import")
        # and destroy-data looks in the same place, or it leaves a
        # clinic's archive CSV export behind on a wiped host
        from carlos_ctl import dbops
        self.assertIn("HOST.state_dir",
                      __import__("inspect").getsource(dbops.o19_estate))

    def test_the_documents_tree_is_the_hosts(self):
        # o19docs reads ctx["documents_root"]; the context must carry the
        # host's answer rather than letting the module default stand
        import inspect
        source = inspect.getsource(o19import._make_ctx)
        self.assertIn('"documents_root": HOST.documents_root', source)


class TestThePortSurface(unittest.TestCase):

    """What a second deployment has to answer, in one place.

    A method the import calls but the base class does not define would
    be a seam nobody can find: a port author reads `Host` to learn what
    to override."""

    REQUIRED = (
        "state_dir", "documents_root", "is_packaged_host",
        "configured_province", "configured_db_name", "identity_source",
        "client_base_argv", "client_env", "stage_credential",
        "clear_stage_credential", "staging_client_argv", "sql_escape",
        "flyway_validate", "backup_configured",
        "backup_configuration_hint", "pre_import_backup",
        "app_running_refusal",
    )

    def test_every_seam_is_on_the_base_class(self):
        for name in self.REQUIRED:
            self.assertTrue(hasattr(o19host.Host, name), name)

    def test_every_seam_the_import_calls_is_declared(self):
        """The other direction: `o19import` may not reach for a host
        attribute the base class does not define, or a port would have
        to discover it from a traceback."""
        import inspect
        import re
        called = set(re.findall(r"HOST\.([A-Za-z_]+)",
                                inspect.getsource(o19import)))
        undeclared = sorted(c for c in called
                            if not hasattr(o19host.Host, c))
        self.assertEqual(undeclared, [])

    def test_each_seam_is_documented(self):
        # a port author reads these docstrings to learn what the answer
        # has to mean, not just its type
        for name in self.REQUIRED:
            attr = getattr(o19host.Host, name)
            doc = getattr(attr, "__doc__", None)
            if isinstance(attr, property):
                doc = attr.fget.__doc__
            self.assertTrue(doc and doc.strip(), name)


if __name__ == "__main__":
    unittest.main()
