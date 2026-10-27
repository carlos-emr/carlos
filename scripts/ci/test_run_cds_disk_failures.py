#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Failure proof must reject accepted work, decoded payload traces and missing retained state."""
import importlib.util
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
import subprocess
import sys

SPEC=importlib.util.spec_from_file_location('disk_faults',Path(__file__).with_name('run-cds-disk-failures.py'))
GATE=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)
CI_SPEC=importlib.util.spec_from_file_location('disk_fault_ci',Path(__file__).with_name('run-cds-performance-ci.py'))
CI=importlib.util.module_from_spec(CI_SPEC)
CI_SPEC.loader.exec_module(CI)


class DiskFailureEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root=Path(self.temporary.name)

    def test_operator_failure_requires_unique_submission_and_cannot_hide_an_acceptance_or_result(self):
        log=self.root/'operator.log'
        nonce='12345678-1234-1234-1234-123456789abc'
        body=json.dumps({'event':'SUBMISSION','submissionKey':nonce})+'\nCDS_REASON: safeCode=DETAILS_PROTECTED outcomeUnknown=false\n'
        log.write_text(body)
        self.assertEqual(GATE.refusal(log)['submissionKey'],nonce)
        for row in ({'event':'ACCEPTED','runId':nonce},{'event':'RESULT','cleanComplete':True},
                    {'event':'SUBMISSION','submissionKey':nonce}):
            log.write_text(body+json.dumps(row)+'\n')
            with self.assertRaises(RuntimeError):GATE.refusal(log)
        log.write_text(json.dumps({'event':'SUBMISSION','submissionKey':nonce})+'\n')
        with self.assertRaises(RuntimeError):GATE.refusal(log)

    def test_enospc_trace_requires_actual_errno_and_raw_registers_instead_of_payload_bytes(self):
        trace=self.root/'trace'
        trace.write_text('write(0x3, 0x7f4000, 0x10000) = -1 ENOSPC (No space left on device)\n')
        self.assertEqual(GATE.raw_enospc(trace),1)
        trace.write_text('write(0x3, 0x7f4000, 0x10000) = -1 EPIPE (Broken pipe)\n')
        self.assertEqual(GATE.raw_enospc(trace),0)
        for value in ('write(3, "source or private credentials", 24) = -1 ENOSPC (No space left on device)\n',
                      'some unrelated ENOSPC message\n','write(0x3, 0x7f4000, 0x10000) = 65536\n'):
            trace.write_text(value)
            with self.assertRaises(RuntimeError):GATE.raw_enospc(trace)

    def test_retention_contains_the_actual_original_and_partial_copy_without_following_links(self):
        owned=self.root/'owned';owned.mkdir()
        (owned/'source').mkdir();(owned/'source'/'original.xml').write_bytes(b'exact synthetic original')
        (owned/'snapshot.pending').mkdir();(owned/'snapshot.pending'/'member.bin').write_bytes(b'partial')
        retained=self.root/'retained.tar.gz'
        proof=GATE.retain_tmpfs(owned,retained)
        self.assertEqual(proof['retainedRegularFileBytes'],31)
        self.assertEqual(proof['sha256'],GATE.RESOURCE.digest(retained))
        with tarfile.open(retained) as archive:
            self.assertEqual(archive.getmember('fixture').mode,owned.stat().st_mode&0o777)
            self.assertEqual(archive.extractfile('fixture/source/original.xml').read(),b'exact synthetic original')
            self.assertEqual(archive.extractfile('fixture/snapshot.pending/member.bin').read(),b'partial')
        with self.assertRaises(RuntimeError):GATE.retain_tmpfs(owned,retained)
        (owned/'redirect').symlink_to(self.root)
        with self.assertRaises(RuntimeError):GATE.retain_tmpfs(owned,self.root/'refused.tar.gz')

    def test_journal_source_uri_matches_only_the_same_local_file_or_directory(self):
        file=self.root/'original space-\u00e9.xml';file.write_bytes(b'original')
        directory=self.root/'source folder';directory.mkdir()
        self.assertTrue(GATE.matches_local_source_uri(file.as_uri(),file))
        self.assertTrue(GATE.matches_local_source_uri(directory.as_uri()+'/',directory))
        self.assertTrue(GATE.matches_local_source_uri(directory.as_uri(),directory))
        for value in (str(file),file.as_uri()+'/',file.as_uri()+'?request=other',file.as_uri()+'#other',
                      file.as_uri().replace('file:///','file://localhost/'),
                      file.as_uri().replace('file:///','file://foreign.example/'),
                      file.as_uri().replace('file:///','file://user:secret@foreign.example/'),
                      file.as_uri().replace('file:','https:'),file.as_uri()+'%GG',file.as_uri()+'%FF',
                      (self.root/'other.xml').as_uri(),None):
            with self.subTest(value=value):
                self.assertFalse(GATE.matches_local_source_uri(value,file))
        self.assertFalse(GATE.matches_local_source_uri('file:///relative.xml',Path('relative.xml')))

    def test_runtime_retirement_requires_verified_archived_state_and_known_zero_registry_outcome(self):
        good={'status':'PASSED','acceptedRuns':0,'afterLedger':dict.fromkeys(('cds_import_run','cds_import_patient',
            'cds_import_unit','cds_import_checkpoint','cds_import_event','cds_import_lineage','maintenanceActive','reservedUploadBytes'),0)}
        self.assertTrue(CI.disk_retirement_allowed(False,None,False))
        self.assertTrue(CI.disk_retirement_allowed(True,good,True))
        for receipt,verified in ((None,False),(None,True),(good,False),({'status':'FAILED',**{key:value for key,value in good.items() if key!='status'}},True),
                                 ({**good,'acceptedRuns':1},True),({**good,'afterLedger':{'cds_import_checkpoint':1}},True),
                                 ({**good,'afterLedger':None},True)):
            with self.subTest(receipt=receipt,verified=verified):
                self.assertFalse(CI.disk_retirement_allowed(True,receipt,verified))

    def test_service_readiness_proves_real_private_create_write_lock_and_force_before_submission(self):
        done=subprocess.run([sys.executable,'-c',GATE.FS_PROBE,str(self.root)],capture_output=True,text=True,check=True,timeout=15)
        value=json.loads(done.stdout)
        self.assertTrue(value['ok'])
        self.assertEqual(value['stage'],'verified-and-retired-own-unaccepted-probe')
        self.assertTrue(all(row['directory'] and not row['symlink'] for row in value['components']))
        self.assertEqual(len(list(self.root.iterdir())),0)
        self.assertEqual(len(value['uidIncludingFs']),4)

    def test_service_readiness_refuses_redirected_or_non_directory_storage_without_a_submission(self):
        plain=self.root/'file';plain.write_bytes(b'unchanged')
        link=self.root/'link';link.symlink_to(self.root,target_is_directory=True)
        for selected in (plain,link):
            done=subprocess.run([sys.executable,'-c',GATE.FS_PROBE,str(selected)],capture_output=True,text=True,check=True,timeout=15)
            value=json.loads(done.stdout)
            self.assertFalse(value['ok'])
        self.assertEqual(plain.read_bytes(),b'unchanged')


if __name__=='__main__':unittest.main()
