# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
import importlib.util
import io
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('war_jaxb', ROOT / 'debian/check-war-jaxb.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def archive(entries):
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w') as stream:
        for name, content in entries:
            stream.writestr(name, content)
    return output.getvalue()


class JaxbWarTest(unittest.TestCase):
    def test_actual_packaged_discovery(self):
        valid = archive([(gate.SERVICE, gate.FACTORY + '\n'), (gate.FACTORY.replace('.', '/') + '.class', b'factory')])
        cases = [
            ([('WEB-INF/lib/jaxb-runtime.jar', valid)], None),
            ([], 'WAR must register exactly one Glassfish JAXB factory'),
            ([('WEB-INF/lib/a.jar', valid), ('WEB-INF/lib/b.jar', valid)],
             'WAR must register exactly one Glassfish JAXB factory'),
            ([('WEB-INF/lib/a.jar', archive([(gate.SERVICE, 'other.Factory')]))],
             'JAXB factory class missing from registering JAR'),
            ([('WEB-INF/lib/a.jar', archive([(gate.SERVICE, 'other.Factory'), ('other/Factory.class', b'x')]))],
             'WAR must register exactly one Glassfish JAXB factory'),
            ([('WEB-INF/classes/' + gate.SERVICE, gate.FACTORY)], 'loose JAXB override in WEB-INF/classes'),
            ([('WEB-INF/lib/a.jar', archive([('META-INF/versions/25/' + gate.SERVICE, gate.FACTORY)]))],
             'versioned JAXB provider registration'),
            ([('WEB-INF/lib/a.jar', valid), ('WEB-INF/lib/shaded.jar', archive([('org/eclipse/persistence/Foo.class', b'x')]))],
             'EclipseLink/MOXy runtime in WAR'),
            ([('WEB-INF/lib/a.jar', valid), ('WEB-INF/lib/bad.jar', b'corrupt')], 'File is not a zip file'),
        ]
        with tempfile.TemporaryDirectory() as temp:
            war = Path(temp) / 'candidate.war'
            for entries, reason in cases:
                with self.subTest(entries=[name for name, _ in entries]):
                    war.write_bytes(archive(entries))
                    errors = io.StringIO()
                    with redirect_stderr(errors), redirect_stdout(io.StringIO()):
                        self.assertEqual(gate.main([str(war)]), 1 if reason else 0)
                    self.assertEqual(errors.getvalue(),
                                     f'JAXB WAR provider validation failed: {reason}\n' if reason else '')
