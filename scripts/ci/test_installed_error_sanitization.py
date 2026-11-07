# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('installed_fault', Path(__file__).with_name('run-installed-error-sanitization.py'))
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class InstalledErrorFixtureTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name).resolve()
        self.webapp = self.root / 'app'
        (self.webapp / 'WEB-INF/classes').mkdir(parents=True)
        self.descriptor = self.webapp / 'WEB-INF/web.xml'
        self.original = (b'<web-app>\r\n<!-- preserve bytes -->\r\n'
            b'<filter-mapping><filter-name>ResponseSanitizationFilter</filter-name>'
            b'<url-pattern>/*</url-pattern><dispatcher>REQUEST</dispatcher>'
            b'<dispatcher>ERROR</dispatcher></filter-mapping></web-app>')
        self.descriptor.write_bytes(self.original)
        self.compiled = self.root / 'ErrorSanitizationFaultServlet.class'
        self.compiled.write_bytes(b'fixture-class')

    def test_failure_and_interruption_restore_the_exact_descriptor_and_remove_owned_classes(self):
        for failure in (RuntimeError, KeyboardInterrupt, InterruptedError):
            with self.assertRaises(failure):
                with GATE.fault_routes(self.webapp, 'a' * 32, self.compiled):
                    installed = self.descriptor.read_bytes()
                    self.assertIn(('/ws/rs/__test-error-' + 'a' * 32 + '</url-pattern>').encode(), installed)
                    self.assertIn(('/__test-error-' + 'a' * 32 + '.jsp</url-pattern>').encode(), installed)
                    self.assertIn(('<error-code>500</error-code><location>/__test-error-' + 'a' * 32 + '.jsp</location>').encode(), installed)
                    self.assertIn(b'preserve bytes', installed)
                    raise failure()
            self.assertEqual(self.descriptor.read_bytes(), self.original)
            self.assertFalse((self.webapp / 'WEB-INF/classes/carlostest5794').exists())

    def test_existing_class_directory_and_linked_descriptor_are_refused_without_mutation(self):
        owned = self.webapp / 'WEB-INF/classes/carlostest5794'
        owned.mkdir()
        sentinel = owned / 'unrelated'
        sentinel.write_text('preserve')
        with self.assertRaises(ValueError), GATE.fault_routes(self.webapp, 'a' * 32, self.compiled):
            self.fail('must refuse existing fixtures')
        self.assertEqual(sentinel.read_text(), 'preserve')
        self.assertEqual(self.descriptor.read_bytes(), self.original)
        sentinel.unlink()
        owned.rmdir()
        self.descriptor.unlink()
        self.descriptor.symlink_to(self.compiled)
        with self.assertRaises(ValueError), GATE.fault_routes(self.webapp, 'a' * 32, self.compiled):
            self.fail('must refuse a linked descriptor')
        self.assertEqual(self.compiled.read_bytes(), b'fixture-class')

    def test_concurrent_descriptor_edits_fail_and_are_preserved_for_recovery(self):
        changed = b'<web-app><!-- concurrent operator change --></web-app>'
        with self.assertRaisesRegex(RuntimeError, 'Concurrent descriptor drift'):
            with GATE.fault_routes(self.webapp, 'a' * 32, self.compiled):
                self.descriptor.write_bytes(changed)
        self.assertEqual(self.descriptor.read_bytes(), changed)

    def test_missing_error_dispatch_mapping_is_not_accepted_as_qualification(self):
        missing = self.original.replace(b'<dispatcher>ERROR</dispatcher>', b'')
        self.descriptor.write_bytes(missing)
        with self.assertRaisesRegex(ValueError, 'REQUEST/ERROR'), GATE.fault_routes(self.webapp, 'a' * 32, self.compiled):
            self.fail('missing ERROR mapping must refuse qualification')
        self.assertEqual(self.descriptor.read_bytes(), missing)

    def test_existing_numeric_500_mapping_is_refused_without_mutation(self):
        existing = self.original.replace(b'</web-app>',
            b'<error-page><error-code>500</error-code><location>/e.jsp</location></error-page></web-app>')
        self.descriptor.write_bytes(existing)
        with self.assertRaisesRegex(ValueError, 'numeric 500'), GATE.fault_routes(self.webapp, 'a' * 32, self.compiled):
            self.fail('existing 500 mapping must be reconciled')
        self.assertEqual(self.descriptor.read_bytes(), existing)
        self.assertFalse((self.webapp / 'WEB-INF/classes/carlostest5794').exists())

    def test_invalid_tokens_are_refused_without_mutation(self):
        for token in ('A' * 32, 'a' * 31, 'a' * 33, 'g' * 32, ''):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, 'Invalid fault fixture token'), GATE.fault_routes(self.webapp, token, self.compiled):
                    self.fail('invalid token must be refused')
                self.assertEqual(self.descriptor.read_bytes(), self.original)
                self.assertFalse((self.webapp / 'WEB-INF/classes/carlostest5794').exists())
