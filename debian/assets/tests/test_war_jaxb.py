# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
import importlib.util
import io
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
        valid = archive([(gate.SERVICE, gate.FACTORY + '\n')])
        cases = [
            ([('WEB-INF/lib/jaxb-runtime.jar', valid)], True),
            ([], False),
            ([('WEB-INF/lib/a.jar', valid), ('WEB-INF/lib/b.jar', valid)], False),
            ([('WEB-INF/lib/a.jar', archive([(gate.SERVICE, 'other.Factory')]))], False),
            ([('WEB-INF/classes/' + gate.SERVICE, gate.FACTORY)], False),
            ([('WEB-INF/lib/a.jar', archive([('META-INF/versions/25/' + gate.SERVICE, gate.FACTORY)]))], False),
            ([('WEB-INF/lib/a.jar', valid), ('WEB-INF/lib/shaded.jar', archive([('org/eclipse/persistence/Foo.class', b'x')]))], False),
            ([('WEB-INF/lib/a.jar', valid), ('WEB-INF/lib/bad.jar', b'corrupt')], False),
        ]
        with tempfile.TemporaryDirectory() as temp:
            war = Path(temp) / 'candidate.war'
            for entries, accepted in cases:
                with self.subTest(entries=[name for name, _ in entries]):
                    war.write_bytes(archive(entries))
                    self.assertEqual(gate.main([str(war)]), 0 if accepted else 1)
