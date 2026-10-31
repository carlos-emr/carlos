#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Failure proof must reject accepted work, decoded payload traces and missing retained state."""
import importlib.util
import ast
import errno
import inspect
import json
import os
from pathlib import Path
import tarfile
import tempfile
import unittest
import subprocess
import sys
import threading
from types import SimpleNamespace
from unittest.mock import Mock,patch

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
        trace.write_text('--- SIGSEGV {si_signo=SIGSEGV, si_code=SEGV_MAPERR, si_addr=NULL} ---\n'
                         'write(0x4, 0x7fd5cc083ce0, 0x10000)     = -1 ENOSPC (No space left on device)\n')
        self.assertEqual(GATE.raw_enospc(trace),1)
        trace.write_text('write(0x3, 0x7f4000, 0x10000) = -1 EPIPE (Broken pipe)\n')
        self.assertEqual(GATE.raw_enospc(trace),0)
        for value in ('write(3, "source or private credentials", 24) = -1 ENOSPC (No space left on device)\n',
                      'some unrelated ENOSPC message\n','write(0x3, 0x7f4000, 0x10000) = 65536\n'):
            trace.write_text(value)
            with self.assertRaises(RuntimeError):GATE.raw_enospc(trace)

    def test_live_native_tasks_are_bound_to_the_verified_jvm_thread_group_and_uid(self):
        ready=threading.Event();release=threading.Event();tid=[]
        def live_task():
            tid.append(threading.get_native_id());ready.set();release.wait(10)
        task=threading.Thread(target=live_task)
        task.start()
        try:
            self.assertTrue(ready.wait(5))
            observed={}
            GATE.observe_worker_threads(Path('/proc'),os.getpid(),os.getuid(),observed)
            self.assertIn(os.getpid(),observed)
            self.assertIn(tid[0],observed)
            self.assertNotEqual(os.getpid(),tid[0])
            self.assertTrue(all(value.isdecimal() for value in observed.values()))
        finally:
            release.set();task.join(5)
        self.assertFalse(task.is_alive())

    def test_task_observer_rejects_foreign_uid_group_and_reused_thread_identity(self):
        proc=self.root/'proc';task=proc/'100'/'task'/'101';task.mkdir(parents=True)
        status='Tgid:\t100\nUid:\t999\t999\t999\t999\n'
        (task/'status').write_text(status)
        def stat(start):return '101 (java) S '+'0 '*18+str(start)+'\n'
        (task/'stat').write_text(stat(123))
        observed={};GATE.observe_worker_threads(proc,100,999,observed)
        self.assertEqual(observed,{101:'123'})
        for invalid in (status.replace('100','200'),status.replace('999','998',1)):
            (task/'status').write_text(invalid)
            with self.assertRaises(RuntimeError):GATE.observe_worker_threads(proc,100,999,{})
        (task/'status').write_text(status);(task/'stat').write_text(stat(124))
        with self.assertRaises(RuntimeError):GATE.observe_worker_threads(proc,100,999,observed)

    def _thread_identity(self,proc,pid,tid,started):
        task=proc/str(pid)/'task'/str(tid);task.mkdir(parents=True)
        (task/'status').write_text(f'Tgid:\t{pid}\nUid:\t999\t999\t999\t999\n')
        (task/'stat').write_text(f'{tid} (java) S '+'0 '*18+str(started)+'\n')
        return task

    def test_task_disappearance_only_skips_enoent_esrch_and_never_invents_attribution(self):
        proc=self.root/'proc';missing=self._thread_identity(proc,100,101,111)
        live=self._thread_identity(proc,100,102,222)
        actual_iterdir=Path.iterdir;actual_read=Path.read_text
        for location in ('tasks','status','stat'):
            for code in (errno.ENOENT,errno.ESRCH,errno.EACCES,errno.EPERM,errno.EIO,errno.EINVAL):
                with self.subTest(location=location,errno=code):
                    failure=OSError(code,'synthetic procfs error');observed={100:'1000'}
                    def iterdir(path):
                        if path==proc/'100'/'task':
                            if location=='tasks':raise failure
                            return iter((missing,live))
                        return actual_iterdir(path)
                    def read(path,*args,**kwargs):
                        if path==missing/location:raise failure
                        return actual_read(path,*args,**kwargs)
                    with patch.object(Path,'iterdir',autospec=True,side_effect=iterdir), \
                            patch.object(Path,'read_text',autospec=True,side_effect=read):
                        if code in (errno.ENOENT,errno.ESRCH):
                            GATE.observe_worker_threads(proc,100,999,observed)
                            self.assertEqual(observed,{100:'1000'} if location=='tasks' else {100:'1000',102:'222'})
                        else:
                            with self.assertRaises(OSError) as caught:GATE.observe_worker_threads(proc,100,999,observed)
                            self.assertIs(caught.exception,failure)
                            self.assertEqual(observed,{100:'1000'})

    def _run_actual_process_scan(self,proc,sample,verify,observed,worker=None):
        # Execute the production loop unchanged, without the unrelated privileged
        # installation/database/tmpfs setup. A structural mismatch fails visibly.
        source,line=inspect.getsourcelines(GATE.main)
        tree=ast.increment_lineno(ast.parse(''.join(source)),line-1)
        target=ast.dump(ast.parse("Path('/proc').iterdir()",mode='eval').body)
        loops=[node for node in ast.walk(tree) if isinstance(node,ast.For) and ast.dump(node.iter)==target]
        self.assertEqual(len(loops),1)
        account=SimpleNamespace(pw_uid=999,pw_gid=999)
        scope={**vars(GATE),'Path':lambda value:proc if value=='/proc' else Path(value),
               'RESOURCE':SimpleNamespace(is_import_command=GATE.RESOURCE.is_import_command,
                    service_sample=sample,verify_java_identity=verify),
               'pwd':SimpleNamespace(getpwnam=lambda name:account),'source':Path('/synthetic/source.xml'),
               'metadata':{'provider':'999998'},'worker':worker,'worker_threads':observed,'result':{},'save':Mock()}
        exec(compile(ast.Module(body=loops,type_ignores=[]),GATE.__file__,'exec'),scope)
        return scope

    def _process_command(self,proc,pid):
        entry=proc/str(pid);entry.mkdir(parents=True,exist_ok=True)
        (entry/'cmdline').write_bytes(b'/usr/bin/java\0-Xmx512m\0io.github.carlos_emr.carlos.demographic.cdsimport.CdsImport\0'
                                    b'--operation\0import\0--input\0/synthetic/source.xml\0--provider\0' b'999998\0')
        return entry

    def test_process_scan_only_skips_disappearance_and_still_verifies_the_next_service_identity(self):
        proc=self.root/'proc';missing=self._process_command(proc,100);live=self._process_command(proc,200)
        self._thread_identity(proc,200,200,222)
        actual_iterdir=Path.iterdir;actual_read=Path.read_bytes
        for location in ('cmdline','sample'):
            for code in (errno.ENOENT,errno.ESRCH,errno.EACCES,errno.EPERM,errno.EIO,errno.EINVAL):
                with self.subTest(location=location,errno=code):
                    failure=OSError(code,'synthetic procfs error');observed={};verify=Mock()
                    def iterdir(path):return iter((missing,live)) if path==proc else actual_iterdir(path)
                    def read(path):
                        if location=='cmdline' and path==missing/'cmdline':raise failure
                        return actual_read(path)
                    def sample(pid):
                        if location=='sample' and pid==100:raise failure
                        return {'uid':999,'started':'222'}
                    with patch.object(Path,'iterdir',autospec=True,side_effect=iterdir), \
                            patch.object(Path,'read_bytes',autospec=True,side_effect=read):
                        if code in (errno.ENOENT,errno.ESRCH):
                            scope=self._run_actual_process_scan(proc,sample,verify,observed)
                            self.assertEqual(scope['worker'],(200,'222'))
                            self.assertEqual(scope['result']['serviceJvm'],{'pid':200,'startTimeTicks':'222','uid':999})
                            self.assertEqual(observed,{200:'222'})
                            self.assertEqual(verify.call_count,1);self.assertEqual(verify.call_args.args[:3],(200,999,999))
                            scope['save'].assert_called_once_with()
                        else:
                            with self.assertRaises(OSError) as caught:self._run_actual_process_scan(proc,sample,verify,observed)
                            self.assertIs(caught.exception,failure);self.assertEqual(observed,{})
                            verify.assert_not_called()

    def test_process_scan_never_attributes_a_foreign_uid_changed_birth_or_unverified_jvm(self):
        proc=self.root/'proc';self._process_command(proc,100);self._thread_identity(proc,100,100,111)
        observed={};verify=Mock()
        scope=self._run_actual_process_scan(proc,lambda pid:{'uid':998,'started':'111'},verify,observed)
        self.assertIsNone(scope['worker']);self.assertEqual(observed,{})
        verify.assert_not_called();scope['save'].assert_not_called()
        with self.assertRaisesRegex(RuntimeError,'DISK_FAULT_MULTIPLE_SERVICE_JVMS'):
            self._run_actual_process_scan(proc,lambda pid:{'uid':999,'started':'222'},verify,observed,(100,'111'))
        verify.assert_not_called();self.assertEqual(observed,{})
        verify=Mock(side_effect=RuntimeError('service executable or birth changed'))
        with self.assertRaisesRegex(RuntimeError,'service executable or birth changed'):
            self._run_actual_process_scan(proc,lambda pid:{'uid':999,'started':'111'},verify,observed)
        self.assertEqual(observed,{})

    def test_errno_proof_aggregates_only_attributed_jvm_threads_including_nonleader_main(self):
        prefix=self.root/'failed';(self.root/'failed.100').write_text('')
        actual='write(0x3, 0x7f4000, 0x10000) = -1 ENOSPC (No space left on device)\n'
        (self.root/'failed.101').write_text(actual)
        (self.root/'failed.999').write_text('write(3, "unrelated private body", 22) = -1 ENOSPC (No space left on device)\n')
        target=self.root/'safe.log'
        count,proof=GATE.collect_worker_traces(prefix,{100:'10',101:'11'},100,target)
        self.assertEqual(count,1)
        self.assertEqual(target.read_text(),actual)
        self.assertEqual(proof['sha256'],GATE.RESOURCE.digest(target))
        self.assertEqual([row['tid'] for row in proof['threads']],[100,101])
        self.assertEqual(proof['threads'][1]['offsetBytes'],0)
        self.assertEqual(proof['threads'][1]['kernelEnospcFailedWrites'],1)
        self.assertEqual(target.stat().st_mode&0o777,0o600)

    def test_owned_task_trace_is_never_silently_missing_or_decoded(self):
        prefix=self.root/'failed';(self.root/'failed.100').write_text('')
        for threads in ({101:'11'},{100:'10',101:'11'}):
            with self.assertRaises(RuntimeError):
                GATE.collect_worker_traces(prefix,threads,100,self.root/'safe.log')
        (self.root/'failed.101').write_text('write(3, "private body", 12) = -1 ENOSPC (No space left on device)\n')
        with self.assertRaises(RuntimeError):
            GATE.collect_worker_traces(prefix,{100:'10',101:'11'},100,self.root/'safe.log')
        self.assertFalse((self.root/'safe.log').exists())

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
