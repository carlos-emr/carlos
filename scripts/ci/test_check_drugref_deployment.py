# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Decisions of check-drugref-deployment.sh, exercised without Docker or a DrugRef image.

The probe meets a real HTTP server on loopback. A fake docker on PATH reports the container's
state, its published port and its logs, and fails on any other call. The Compose translation
runs on JSON shaped like the output of `docker compose config --format json`.
"""
import http.server
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import textwrap
import threading
import unittest

SCRIPT = Path(__file__).resolve().with_name('check-drugref-deployment.sh')
SERVLET_PATH = '/drugref2/DrugrefService'
CONTAINER = 'drugref-check-fixture'
CONSOLE_SEVERE = 'SEVERE [main] Context [/drugref2] startup failed due to previous errors'
CONTEXT_SEVERE = 'SEVERE [main] Exception sending context initialized event to listener'

FAKE_DOCKER = textwrap.dedent(f'''\
    #!/bin/sh
    # Fake docker for test_check_drugref_deployment.py: answers from files in FAKE_DOCKER_DIR.
    case "$1" in
      inspect) cat "$FAKE_DOCKER_DIR/running" ;;
      port) test -s "$FAKE_DOCKER_DIR/port" && printf '127.0.0.1:%s\\n' "$(cat "$FAKE_DOCKER_DIR/port")" ;;
      logs) printf '%s\\n' 'INFO [main] Server startup' '{CONSOLE_SEVERE}' ;;
      exec) printf '%s\\n' '{CONTEXT_SEVERE}' ;;
      *) echo "unexpected docker call: $*" >&2; exit 99 ;;
    esac
    ''')


def free_port():
    """A loopback port nothing listens on (until a test starts a server there)."""
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0))
        return probe.getsockname()[1]


class _ServletStub(http.server.BaseHTTPRequestHandler):
    """Answers every GET with the status the test chose and records the requested path."""

    def do_GET(self):  # noqa: N802 - http.server dispatches on this name
        self.server.paths.append(self.path)
        self.send_response(self.server.status)
        self.send_header('Content-Length', '0')
        self.end_headers()

    def log_message(self, *_args):
        return


class ScriptTestCase(unittest.TestCase):
    """Runs functions of the real script in bash, with the fake docker first on PATH."""

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.fake_dir = Path(directory.name)
        docker = self.fake_dir / 'docker'
        docker.write_text(FAKE_DOCKER)
        docker.chmod(0o755)
        self.set_running(True)
        self.set_published_port('')
        self.env = dict(os.environ, FAKE_DOCKER_DIR=str(self.fake_dir),
                        PATH=f'{self.fake_dir}{os.pathsep}{os.environ["PATH"]}')

    def set_running(self, running):
        (self.fake_dir / 'running').write_text('true\n' if running else 'false\n')

    def set_published_port(self, port):
        (self.fake_dir / 'port').write_text(str(port))

    def serve(self, status, port=0):
        """Starts a servlet stub answering STATUS; returns the server (paths, server_port)."""
        server = http.server.ThreadingHTTPServer(('127.0.0.1', port), _ServletStub)
        server.status = status
        server.paths = []
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return server

    def call(self, statement, *args):
        """Sources the script (main does not run) and runs STATEMENT with ARGS as $1...

        Prints the function's status and PROBE_RESULT after its own output.
        """
        harness = ('source "$1"; shift; status=0; ' + statement + ' || status=$?; '
                   'printf "status=%s\\nresult=%s\\n" "$status" "$PROBE_RESULT"')
        result = subprocess.run(['bash', '-c', harness, 'check-harness', str(SCRIPT), *args],
                                env=self.env, capture_output=True, text=True, timeout=120)
        lines = result.stdout.splitlines()
        self.assertGreaterEqual(len(lines), 2, result.stderr)
        self.assertTrue(lines[-2].startswith('status='), result.stdout + result.stderr)
        return int(lines[-2].removeprefix('status=')), lines[-1].removeprefix('result='), result


class ProbeServletTest(ScriptTestCase):
    def probe(self, url, timeout=20):
        return self.call('probe_servlet "$@"', CONTAINER, url, str(timeout))

    def servlet_url(self, port):
        return f'http://127.0.0.1:{port}{SERVLET_PATH}'

    def test_405_from_the_post_only_servlet_means_deployed(self):
        server = self.serve(405)
        status, detail, _ = self.probe(self.servlet_url(server.server_port))
        self.assertEqual(status, 0)
        self.assertIn('HTTP 405', detail)
        self.assertIn('servlet initialised', detail)

    def test_200_from_a_build_that_also_serves_get_means_deployed(self):
        server = self.serve(200)
        status, detail, _ = self.probe(self.servlet_url(server.server_port))
        self.assertEqual(status, 0)
        self.assertIn('HTTP 200', detail)

    def test_404_means_the_context_did_not_deploy(self):
        server = self.serve(404)
        status, detail, _ = self.probe(self.servlet_url(server.server_port))
        self.assertEqual(status, 1)
        self.assertIn('HTTP 404', detail)
        self.assertIn('not deployed', detail)

    def test_403_names_the_address_filter(self):
        server = self.serve(403)
        status, detail, _ = self.probe(self.servlet_url(server.server_port))
        self.assertEqual(status, 1)
        self.assertIn('RemoteCIDRValve', detail)

    def test_500_and_503_mean_the_servlet_failed_to_initialise(self):
        for code in (500, 503):
            with self.subTest(code=code):
                server = self.serve(code)
                status, detail, _ = self.probe(self.servlet_url(server.server_port))
                self.assertEqual(status, 1)
                self.assertIn('failed to initialise', detail)

    def test_other_answers_are_not_taken_as_deployment(self):
        server = self.serve(302)
        status, detail, _ = self.probe(self.servlet_url(server.server_port))
        self.assertEqual(status, 1)
        self.assertIn('expected 405', detail)

    def test_waits_while_nothing_answers_then_judges_the_first_answer(self):
        port = free_port()
        starter = threading.Timer(1.5, self.serve, args=(405, port))
        starter.start()
        self.addCleanup(starter.cancel)
        status, detail, _ = self.probe(self.servlet_url(port))
        self.assertEqual(status, 0, detail)

    def test_reports_no_answer_when_tomcat_never_answers(self):
        status, detail, _ = self.probe(self.servlet_url(free_port()), timeout=1)
        self.assertEqual(status, 2)
        self.assertIn('did not answer', detail)

    def test_a_stopped_container_is_no_answer_without_waiting(self):
        self.set_running(False)
        server = self.serve(405)
        status, detail, _ = self.probe(self.servlet_url(server.server_port))
        self.assertEqual(status, 2)
        self.assertIn('stopped', detail)
        self.assertEqual(server.paths, [])


class ExpectationTest(ScriptTestCase):
    """The self-tests and the deployment verdict, over the port the container publishes."""

    def expect(self, function, status_code):
        server = self.serve(status_code)
        self.set_published_port(server.server_port)
        return (*self.call(function + ' "the fixture" "$@"', CONTAINER), server)

    def test_probe_asks_the_servlet_not_the_tomcat_root(self):
        _, _, _, server = self.expect('expect_deployed', 405)
        self.assertEqual(server.paths, [SERVLET_PATH])

    def test_self_test_passes_when_the_check_rejects_an_undeployed_context(self):
        status, _, result, _ = self.expect('expect_rejected', 404)
        self.assertEqual(status, 0, result.stderr)
        self.assertIn('Self-test passed: the check rejects the fixture', result.stdout)
        self.assertIn(CONSOLE_SEVERE, result.stdout)
        self.assertIn(CONTEXT_SEVERE, result.stdout)
        self.assertNotIn('::error::', result.stdout + result.stderr)

    def test_self_test_fails_when_the_check_accepts_the_configuration(self):
        status, _, result, _ = self.expect('expect_rejected', 405)
        self.assertEqual(status, 1)
        self.assertIn('::error::Self-test failed: the check accepted the fixture', result.stderr)

    def test_self_test_is_inconclusive_when_tomcat_does_not_answer(self):
        self.set_running(False)
        status, _, result, _ = self.expect('expect_rejected', 404)
        self.assertEqual(status, 1)
        self.assertIn('::error::Self-test inconclusive for the fixture', result.stderr)
        self.assertIn('::group::', result.stdout)

    def test_deployment_passes_on_the_servlet_answer(self):
        status, _, result, _ = self.expect('expect_deployed', 405)
        self.assertEqual(status, 0, result.stderr)
        self.assertIn('PASS: DrugRef deployed with the fixture', result.stdout)

    def test_failed_deployment_reports_and_shows_both_tomcat_logs(self):
        status, _, result, _ = self.expect('expect_deployed', 404)
        self.assertEqual(status, 1)
        self.assertIn('::error::DrugRef did not deploy with the fixture', result.stderr)
        self.assertIn(CONSOLE_SEVERE, result.stdout)
        self.assertIn(CONTEXT_SEVERE, result.stdout)
        self.assertIn('::endgroup::', result.stdout)

    def test_a_container_without_a_published_port_is_no_answer(self):
        self.set_published_port('')
        status, detail, result = self.call('expect_deployed "the fixture" "$@"', CONTAINER)
        self.assertEqual(status, 1)
        self.assertIn('published port 8080', detail)
        self.assertIn('::error::DrugRef did not deploy', result.stderr)


def compose_config(drugref=None, db=None):
    """JSON shaped like `docker compose config --format json drugref`."""
    service = {
        'build': {'context': '/repo/.devcontainer/drugref', 'dockerfile': 'Dockerfile'},
        'command': None,
        'container_name': 'carlos-drugref-dev',
        'depends_on': {'db': {'condition': 'service_healthy', 'required': True}},
        'entrypoint': None,
        'environment': {
            'CATALINA_OPTS': '-Dhibernate.dialect=org.hibernate.dialect.MariaDBDialect ',
            'TZ': 'America/Toronto',
            'PASSED_THROUGH': None,
        },
        'healthcheck': {'test': ['CMD', 'true']},
        'networks': {'carlos-network': None},
        'ports': [{'mode': 'ingress', 'target': 8080, 'published': '8180', 'protocol': 'tcp'}],
        'volumes': [
            {'type': 'bind', 'source': '/repo/volumes/drugref2.properties',
             'target': '/root/drugref2.properties', 'read_only': True, 'bind': {}},
            {'type': 'bind', 'source': '/repo/.devcontainer/drugref/drugref2.xml',
             'target': '/usr/local/tomcat/conf/Catalina/localhost/drugref2.xml', 'bind': {}},
        ],
    }
    service.update(drugref or {})
    return json.dumps({'services': {'drugref': service, 'db': {'environment': db or {}}}})


def compose_escaped(value):
    """VALUE as `docker compose config` renders it: every literal $ doubled."""
    return value.replace('$', '$$')


class ComposeTranslationTest(ScriptTestCase):
    def load(self, config):
        """Runs load_deployment on CONFIG; returns its status, the DEPLOYMENT options and the run."""
        status, _, result = self.call(
            'COMPOSE_JSON=$1; load_deployment && printf "%s\\0" "${DEPLOYMENT[@]}" && echo', config)
        records = result.stdout.rpartition('status=')[0].removesuffix('\n')
        return status, records.split('\0')[:-1], result

    def test_bind_mounts_and_environment_become_docker_run_options(self):
        status, options, result = self.load(compose_config())
        self.assertEqual(status, 0, result.stderr)
        self.assertEqual(options, [
            '--mount=type=bind,source=/repo/volumes/drugref2.properties,'
            'target=/root/drugref2.properties,readonly',
            '--mount=type=bind,source=/repo/.devcontainer/drugref/drugref2.xml,'
            'target=/usr/local/tomcat/conf/Catalina/localhost/drugref2.xml',
            '--env=CATALINA_OPTS=-Dhibernate.dialect=org.hibernate.dialect.MariaDBDialect ',
            '--env=TZ=America/Toronto',
            '--env=PASSED_THROUGH',
        ])

    def test_an_environment_value_keeps_its_newlines_in_one_option(self):
        config = compose_config({'environment': {'CATALINA_OPTS': '-Xms256m\n-Xmx512m'}})
        status, options, result = self.load(config)
        self.assertEqual(status, 0, result.stderr)
        self.assertEqual(options[2:], ['--env=CATALINA_OPTS=-Xms256m\n-Xmx512m'])

    def test_compose_dollar_escaping_is_decoded_in_values_and_paths(self):
        volume = {'type': 'bind', 'source': compose_escaped('/repo/dir$name/drugref2.xml'),
                  'target': compose_escaped('/conf/dir$name.xml'), 'read_only': True}
        environment = {'ONE': compose_escaped('pa$word'), 'TWO': compose_escaped('a$$b')}
        config = compose_config({'environment': environment, 'volumes': [volume]})
        status, options, result = self.load(config)
        self.assertEqual(status, 0, result.stderr)
        self.assertEqual(options, [
            '--mount=type=bind,source=/repo/dir$name/drugref2.xml,target=/conf/dir$name.xml,readonly',
            '--env=ONE=pa$word',
            '--env=TWO=a$$b',
        ])

    def test_a_setting_the_check_cannot_reproduce_fails(self):
        status, _, result = self.load(compose_config({'user': 'tomcat'}))
        self.assertEqual(status, 1)
        self.assertIn('sets user', result.stderr)
        self.assertIn('::error::The Compose drugref service cannot be reproduced', result.stderr)

    def test_a_non_bind_volume_fails(self):
        volume = {'type': 'volume', 'source': 'drugref-data', 'target': '/data'}
        status, _, result = self.load(compose_config({'volumes': [volume]}))
        self.assertEqual(status, 1)
        self.assertIn('bind mounts only', result.stderr)


class DatabaseSettingsTest(ScriptTestCase):
    def root_password(self, environment):
        return self.call('COMPOSE_JSON=$1; database_root_password', compose_config(db=environment))

    def test_root_password_is_the_one_the_compose_db_service_starts_with(self):
        mariadb_value, mysql_value = secrets.token_hex(8), secrets.token_hex(8)
        status, _, result = self.root_password({'MARIADB_ROOT_PASSWORD': mariadb_value,
                                                'MYSQL_ROOT_PASSWORD': mysql_value})
        self.assertEqual(status, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines()[0], mariadb_value)
        status, _, result = self.root_password({'MYSQL_ROOT_PASSWORD': mysql_value})
        self.assertEqual(status, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines()[0], mysql_value)

    def test_compose_dollar_escaping_in_the_root_password_is_decoded(self):
        value = secrets.token_hex(4) + '$' + secrets.token_hex(4)
        status, _, result = self.root_password({'MARIADB_ROOT_PASSWORD': compose_escaped(value)})
        self.assertEqual(status, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines()[0], value)

    def test_a_db_service_without_a_root_password_fails(self):
        status, _, result = self.root_password({'TZ': 'America/Toronto'})
        self.assertEqual(status, 1)
        self.assertIn('::error::The Compose db service sets no', result.stderr)

    def test_database_image_is_the_base_of_the_development_database_image(self):
        status, _, result = self.call('database_image')
        self.assertEqual(status, 0, result.stderr)
        self.assertRegex(result.stdout.splitlines()[0], r'^mariadb:\S+$')


class ContainerStartTest(ScriptTestCase):
    def test_a_container_that_cannot_start_is_reported(self):
        # The fake docker refuses `run`, as a failed image pull would.
        status, _, result = self.call('NETWORK=fixture-network; IMAGE=fixture:image; '
                                      'start_drugref "$@"', CONTAINER)
        self.assertEqual(status, 1)
        self.assertIn(f'::error::Could not start {CONTAINER} from fixture:image', result.stderr)

    def test_database_loads_the_schema_and_its_patches_with_the_password_off_argv(self):
        root_value = secrets.token_hex(8)
        status, _, result = self.call('COMPOSE_JSON=$1; NETWORK=fixture-network; '
                                      'start_database fixture-db',
                                      compose_config(db={'MARIADB_ROOT_PASSWORD': root_value}))
        self.assertEqual(status, 1)
        self.assertIn('::error::Could not start the drugref2 database from mariadb:', result.stderr)
        refused = result.stderr.splitlines()[0]
        self.assertTrue(refused.startswith('unexpected docker call: run '), refused)
        repo = SCRIPT.parents[2]
        self.assertIn(f'source={repo}/database/mysql/development-drugref.sql,'
                      'target=/docker-entrypoint-initdb.d/00-development-drugref.sql,readonly', refused)
        for patch in sorted((repo / 'database/mysql/drugref').glob('*.sql')):
            self.assertIn(f'source={patch},target=/docker-entrypoint-initdb.d/10-{patch.name},readonly',
                          refused)
        self.assertIn('--network-alias db', refused)
        self.assertIn('--env MARIADB_DATABASE=drugref2', refused)
        self.assertNotIn(root_value, refused)


class CommandLineTest(unittest.TestCase):
    def test_usage_without_an_image(self):
        result = subprocess.run(['bash', str(SCRIPT)], capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 64)
        self.assertIn('Usage:', result.stderr)


if __name__ == '__main__':
    unittest.main()
