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

    def test_distinct_kernel_resident_peak_interfaces_enforce_same_budget_without_false_ordering(self):
        budget=1024*GATE.MIB
        actual={'exitCode':0,'largestProcessPeakRssBytes':502616064}
        self.assertEqual(GATE.enforce_rss_budget(502784000,actual,budget),502784000)
        for observed,kernel in ((budget+1,actual),(502784000,{'exitCode':0,'largestProcessPeakRssBytes':budget+1}),
                                (502784000,{'exitCode':4,'largestProcessPeakRssBytes':502616064}),
                                (502784000,{'exitCode':0}), (0,actual)):
            with self.subTest(observed=observed,kernel=kernel):
                with self.assertRaises(RuntimeError):GATE.enforce_rss_budget(observed,kernel,budget)

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
        xml=(Path(case['source'])/'patient-000000.xml').read_bytes()
        self.assertEqual(xml.count(b'<Reports><Format>Binary</Format>'),2)
        self.assertEqual(xml.count(b'<FileExtensionAndVersion>PDF</FileExtensionAndVersion>'),2)

    def test_streamed_zip_retains_exact_original_members_and_declares_actual_zip64_sizes(self):
        case=GATE.generate_case(self.root,'zip','ABCDEF0002',2,0,10000,archive=True)
        self.assertFalse((self.root/'zip').exists())
        with zipfile.ZipFile(case['source']) as archive:
            self.assertEqual(archive.testzip(),None)
            self.assertEqual(set(archive.namelist()),{row['member'] for row in case['members']})
            for row in case['members']:
                data=archive.read(row['member']);self.assertEqual(len(data),row['bytes'])
                self.assertEqual(__import__('hashlib').sha256(data).hexdigest(),row['sha256'])
                if row['member'].endswith('.xml'):
                    self.assertIn(b'<Format>Binary</Format><FileExtensionAndVersion>PDF</FileExtensionAndVersion><FilePath>',data)
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

    def test_failure_evidence_retains_code_locations_without_exception_messages_or_secrets(self):
        sentinel='synthetic-private-password-and-SQL'
        try:
            raise RuntimeError(sentinel)
        except RuntimeError as failure:
            evidence=GATE.safe_failure(failure)
        self.assertEqual(evidence['exceptionClass'],'RuntimeError')
        self.assertTrue(evidence['codeLocations'])
        self.assertNotIn(sentinel,json.dumps(evidence))

    def test_operator_failure_projection_retains_finite_reason_and_receipt_but_withholds_raw_startup_lines(self):
        log=self.root/'operator.log'
        hidden='synthetic-private-password-and-SQL'
        log.write_text('CDS_BOOTSTRAP: '+hidden+'\n'+hidden+'\n'
                       +'CDS_REASON: safeCode=CDS_INPUT_DISK_RESERVE outcomeUnknown=false\n'
                       +json.dumps({'event':'RESULT','cleanComplete':False,'state':'REVIEW_REQUIRED','unexpectedPrivateField':hidden})+'\n')
        proof=GATE.operator_projection(log)
        self.assertEqual(proof['diagnosticPrefixes'],{'CDS_BOOTSTRAP':1,'CDS_REASON':1})
        self.assertEqual(proof['reasonCodes'],[{'code':'CDS_INPUT_DISK_RESERVE','outcomeUnknown':False}])
        self.assertEqual(proof['events'][0]['state'],'REVIEW_REQUIRED')
        self.assertNotIn(hidden,json.dumps(proof))

    def test_unavailable_auxiliary_measurement_does_not_hide_actual_terminal_failure_codes(self):
        evidence={}
        def missing():
            raise RuntimeError('private GC failure message')
        GATE.capture_evidence(evidence,'gc',missing)
        GATE.capture_evidence(evidence,'diagnosticCodes',lambda:['CDS_SOURCE_SNAPSHOT_FAILED'])
        self.assertEqual(evidence['diagnosticCodes'],['CDS_SOURCE_SNAPSHOT_FAILED'])
        self.assertEqual(evidence['failureEvidenceErrors']['gc']['exceptionClass'],'RuntimeError')
        self.assertNotIn('private GC failure message',json.dumps(evidence))

    def test_technical_operation_failure_keeps_reconciliation_state_and_code_locations_without_messages(self):
        hidden='private-source-SQL-and-password'
        value={'code':'CDS_OPERATION_FAILED','clinicalAcceptance':'RECONCILE_DURABLE_STATE',
            'causes':[{'type':'java.nio.file.FileSystemException','frames':['package.Service#accept:42'],
                       'message':hidden,'sqlState':'HY000','vendorCode':28}],'privateDetail':hidden}
        projected=GATE.technical_failure_projection(value)
        self.assertEqual(projected['clinicalAcceptance'],'RECONCILE_DURABLE_STATE')
        self.assertNotIn(hidden,json.dumps(projected))
        log=self.root/'stderr.log';log.write_text('CDS_TECHNICAL: '+json.dumps(value)+'\n')
        actual=GATE.operator_projection(log)
        self.assertEqual(actual['technicalFailureEvidence'][0]['causes'][0]['type'],'java.nio.file.FileSystemException')
        self.assertNotIn(hidden,json.dumps(actual))
        empty=GATE.technical_failure_projection({**value,'causes':[]})
        self.assertTrue(empty['technicalDetailsUnavailable'])
        self.assertEqual(empty['clinicalAcceptance'],'RECONCILE_DURABLE_STATE')
        for changed in ({**value,'clinicalAcceptance':'NOT_STARTED'},
                        {**value,'causes':[{'type':'java.io.IOException','frames':['message '+hidden]}]},
                        {**value,'causes':[{'type':'java.io.IOException','frames':['a.B#method:1']*33}]}):
            with self.assertRaises(RuntimeError):GATE.technical_failure_projection(changed)

    def test_startup_control_cannot_treat_an_acceptance_or_generic_error_as_readonly_baseline(self):
        good={'events':[{'event':'INITIALIZING','operation':'status'}],
              'reasonCodes':[{'code':'SUBMISSION_OUTCOME_NOT_YET_KNOWN','outcomeUnknown':True}]}
        GATE.require_startup_control(4,good)
        for code,evidence in ((0,good),(2,good),(4,{**good,'events':good['events']+[{'event':'ACCEPTED'}]}),
                              (4,{**good,'reasonCodes':[{'code':'CDS_RUNTIME','outcomeUnknown':True}]}),
                              (4,{**good,'reasonCodes':[{'code':'SUBMISSION_OUTCOME_NOT_YET_KNOWN','outcomeUnknown':False}]})):
            with self.subTest(code=code,evidence=evidence):
                with self.assertRaises(RuntimeError):GATE.require_startup_control(code,evidence)

    def test_resource_samples_preserve_unknown_inventory_and_observed_peaks_after_completion(self):
        run='12345678-1234-1234-1234-123456789abc'
        calls=[]
        responses=iter([[['12','2','3']],[['0','-1','0']],[['14','3','5']],[['1','514','256']],
                        [['11','1','0']],[['1','514','0']]])
        def query(statement):
            calls.append(statement)
            return next(responses)
        summary={'maxObservedPendingUnits':None}
        initial=GATE.resource_observation(query,self.root,run)
        self.assertIsNone(initial['pendingUnits']);self.assertIsNone(initial['expectedUnits'])
        GATE.retain_resource_observation(summary,initial)
        self.assertIsNone(summary['maxObservedPendingUnits'])
        GATE.retain_resource_observation(summary,GATE.resource_observation(query,self.root,run))
        GATE.retain_resource_observation(summary,GATE.resource_observation(query,self.root,run))
        self.assertEqual(summary['maxObservedPendingUnits'],256)
        self.assertEqual(summary['last']['pendingUnits'],0)
        self.assertEqual(summary['maxObservedThreadsConnected'],14)
        self.assertEqual(summary['maxObservedActiveTransactionAgeSeconds'],5)
        self.assertEqual(summary['samples'],3)
        self.assertEqual(summary['unsealedOrNotAcceptedSamples'],1)
        self.assertEqual(summary['sealedInventorySamples'],2)
        self.assertGreaterEqual(summary['minimumObservedFreeInodes'],0)
        self.assertEqual(len(calls),6)
        self.assertNotIn('trx_query',' '.join(calls).lower())

    def test_resource_samples_refuse_missing_live_metrics_and_impossible_sealed_backlog(self):
        run='12345678-1234-1234-1234-123456789abc'
        for response in ([],[['1','2']],[['1','NaN','2']]):
            with self.subTest(response=response):
                with self.assertRaises(RuntimeError):GATE.resource_observation(lambda _:response,self.root,None)
        responses=iter([[['1','1','0']],[['1','4','5']]])
        with self.assertRaisesRegex(RuntimeError,'impossible backlog'):
            GATE.resource_observation(lambda _:next(responses),self.root,run)


if __name__=='__main__':unittest.main()
