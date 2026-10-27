#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Workload producers and evidence guards must not manufacture large-import success."""
from collections import Counter
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import sys
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("cds_performance",Path(__file__).with_name("run-cds-performance.py"))
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class PerformanceEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)

    def test_valid_pdf_has_real_stream_length_object_offsets_and_terminal_xref(self):
        file=self.root/'source.pdf'
        length,sha256=GATE.pdf(file,100000,'explicit-seed')
        data=file.read_bytes()
        self.assertEqual(length,len(data));self.assertEqual(sha256,GATE.digest(file))
        self.assertIn(b'/Length 100000 >>\nstream\n',data)
        start=data.index(b'>>\nstream\n')+len(b'>>\nstream\n')
        self.assertEqual(data[start+100000:start+100000+10],b'endstream\n')
        xref=int(data.split(b'startxref\n')[-1].splitlines()[0])
        self.assertTrue(data[xref:].startswith(b'xref\n0 5\n'))
        rows=data[xref:].splitlines()
        for index,row in enumerate(rows[3:7],1):
            offset=int(row.split()[0]);self.assertTrue(data[offset:].startswith(str(index).encode()+b' 0 obj'))
        self.assertTrue(data.endswith(b'%%EOF\n'))

    def test_wait4_preserves_a_short_allocation_peak_and_actual_process_cpu_after_exit(self):
        receipt=self.root/'resources.json'
        child='import subprocess,sys;subprocess.run([sys.executable,"-c","x=bytearray(64*1024*1024);x[0]=1;del x"],check=True)'
        code=GATE.supervise(receipt,[sys.executable,'-c',child])
        self.assertEqual(code,0)
        value=json.loads(receipt.read_text())
        self.assertGreaterEqual(value['largestProcessPeakRssBytes'],64*1024*1024)
        self.assertGreater(value['userCpuSeconds']+value['systemCpuSeconds'],0)
        self.assertEqual(value['exitCode'],0)

    def test_capability_probe_cannot_be_mistaken_for_the_actual_owned_import_jvm(self):
        # Verbatim public entry/argument shape from the paired installed companion.
        main=b'io.github.carlos_emr.carlos.demographic.cdsimport.CdsImport'
        probe=[b'java',main,b'--capabilities']
        actual=[b'java',main,b'--operation',b'import',b'--provider',b'999998',b'--input',b'/owned/source']
        self.assertFalse(GATE.is_import_command(probe,'/owned/source','999998'))
        self.assertTrue(GATE.is_import_command(actual,'/owned/source','999998'))
        self.assertFalse(GATE.is_import_command(actual,'/different/source','999998'))
        self.assertFalse(GATE.is_import_command(actual,'/owned/source','other'))
        runuser=[b'/usr/sbin/runuser',b'-u',b'carlos',b'--',*actual]
        self.assertFalse(GATE.is_import_command(runuser,'/owned/source','999998'))

    def test_normal_process_disappearance_is_distinct_from_an_unreadable_live_process(self):
        with patch.object(Path,'read_text',side_effect=FileNotFoundError):
            self.assertIsNone(GATE.service_sample(123456))
        with patch.object(Path,'read_text',side_effect=PermissionError):
            with self.assertRaises(PermissionError):GATE.service_sample(123456)
        stat='123456 (java) Z '+' '.join('0' for _ in range(20))
        with patch.object(Path,'read_text',side_effect=[stat,'State:\tZ (zombie)\nUid:\t999\n']):
            self.assertIsNone(GATE.service_sample(123456))

    def test_large_pdf_shape_uses_bounded_stream_lengths_and_complete_dynamic_xref(self):
        file=self.root/'multiple.pdf'
        GATE.pdf(file,5000,'multi',max_stream_bytes=1024)
        data=file.read_bytes()
        lengths=[int(value) for value in __import__('re').findall(rb'/Length (\d+)',data)]
        self.assertEqual(sum(lengths),5000)
        self.assertTrue(all(value<=1024 for value in lengths))
        self.assertEqual(len(lengths),5)
        self.assertIn(b'/Contents [4 0 R 5 0 R 6 0 R 7 0 R 8 0 R]',data)
        self.assertIn(b'xref\n0 9\n',data)
        self.assertIn(b'/Size 9 /Root 1 0 R',data)

    def test_gc_proof_requires_multiple_numeric_monotonic_samples_and_real_time_deltas(self):
        log=self.root/'gc.log'
        header='Timestamp S0C YGC YGCT FGC FGCT GCT\n'
        log.write_text(header+'0.1 0 1 0.1 0 0 0.1\n1.1 0 2 0.2 0 0 0.2\n')
        proof=GATE.gc_evidence(log)
        self.assertEqual(proof['samples'],2)
        self.assertAlmostEqual(proof['delta']['GCT'],0.1)
        for body in ('0.1 0 1 0.1 0 0 0.1\n', 'jstat process disappeared\n',
                     '0.1 0 1 NaN 0 0 0.1\n1.1 0 2 0.2 0 0 0.2\n',
                     '0.1 0 2 0.2 0 0 0.2\n1.1 0 1 0.1 0 0 0.1\n'):
            log.write_text(header+body)
            with self.assertRaises((RuntimeError,ValueError)):GATE.gc_evidence(log)

    def test_scaling_cases_have_real_valid_wire_occurrences_and_both_payload_representations(self):
        case=GATE.generate_case(self.root,'small','ABCDEF0001',2,3,10000)
        self.assertEqual((2,6,6,4),(case['patients'],case['notes'],case['labs'],case['documents']))
        self.assertEqual(case['sourceBytes'],sum(row['bytes'] for row in case['members']))
        expected=Counter(row['sha256'] for row in case['members'] if row['document'])
        self.assertEqual(sum(expected.values()),2)
        self.assertEqual(len(expected),2)
        self.assertIn(b'<FilePath>',(Path(case['source'])/'patient-000000.xml').read_bytes())
        self.assertIn(b'<cdsd:Media>',(Path(case['source'])/'patient-000000.xml').read_bytes())

    def test_streamed_zip_retains_exact_original_members_and_declares_actual_zip64_sizes(self):
        case=GATE.generate_case(self.root,'zip','ABCDEF0002',2,0,10000,archive=True)
        self.assertFalse((self.root/'zip').exists())
        with zipfile.ZipFile(case['source']) as archive:
            self.assertEqual(archive.testzip(),None)
            self.assertEqual(set(archive.namelist()),{row['member'] for row in case['members']})
            for row in case['members']:
                data=archive.read(row['member']);self.assertEqual(len(data),row['bytes'])
                self.assertEqual(__import__('hashlib').sha256(data).hexdigest(),row['sha256'])
            first=archive.infolist()[0]
            with Path(case['source']).open('rb') as source:
                source.seek(first.header_offset)
                header=source.read(30)
                self.assertEqual(struct.unpack('<I',header[18:22])[0],0xffffffff)

    def test_missing_storage_is_an_explicit_failure_not_a_skip_or_logical_byte_counter(self):
        with patch.object(GATE.shutil,'disk_usage',return_value=type('Usage',(),{'free':1024})()):
            with self.assertRaisesRegex(RuntimeError,'STORAGE_PREREQUISITE'):
                GATE.storage_admission(self.root,20*GATE.GIB)

    def test_clean_receipt_requires_all_delivery_release_and_exact_run_proofs(self):
        run='12345678-1234-1234-1234-123456789abc'
        result={'event':'RESULT','runId':run,'cleanComplete':True,'clinicalComplete':True,
                'reportsDelivered':True,'cleanupComplete':True,'resumeRequested':False,
                'maintenanceReleasePending':False,'maintenanceReleaseState':'RELEASED'}
        rows=[{'event':'ACCEPTED','runId':run},result]
        self.assertEqual(GATE.complete_result(rows),result)
        for key in result.keys()-{'event','runId'}:
            with self.subTest(key=key):
                changed=dict(result);changed.pop(key)
                with self.assertRaises(RuntimeError):GATE.complete_result([rows[0],changed])
        with self.assertRaises(RuntimeError):GATE.complete_result([*rows,rows[0]])

    def test_incomplete_and_overlong_operational_rows_refuse(self):
        log=self.root/'operator.log'
        for data in ('{"event":"ACCEPTED"}', 'x'*65537+'\n'):
            log.write_text(data)
            with self.assertRaises(RuntimeError):GATE.rows(log)

    def test_atomic_evidence_replacement_preserves_protected_mode_and_refuses_symlinks(self):
        output=self.root/'proof.json'
        GATE.private_json(output,{'state':'RUNNING'},exclusive=True)
        GATE.private_json(output,{'state':'PASSED'})
        self.assertEqual(json.loads(output.read_text()),{'state':'PASSED'})
        self.assertEqual(output.stat().st_mode & 0o777,0o600)
        with self.assertRaises(RuntimeError):GATE.private_json(output,{},exclusive=True)
        link=self.root/'link';link.symlink_to(output)
        with self.assertRaises(RuntimeError):GATE.private_json(link,{})


if __name__=='__main__':unittest.main()
