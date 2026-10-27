#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Failure proof must reject accepted work, decoded payload traces and missing retained state."""
import importlib.util
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

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
            self.assertEqual(archive.extractfile('fixture/source/original.xml').read(),b'exact synthetic original')
            self.assertEqual(archive.extractfile('fixture/snapshot.pending/member.bin').read(),b'partial')
        with self.assertRaises(RuntimeError):GATE.retain_tmpfs(owned,retained)
        (owned/'redirect').symlink_to(self.root)
        with self.assertRaises(RuntimeError):GATE.retain_tmpfs(owned,self.root/'refused.tar.gz')

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


if __name__=='__main__':unittest.main()
