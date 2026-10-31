#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Installed preaccept reserve/ENOSPC gates on a dedicated, bounded CI tmpfs.

Never fill an existing filesystem or change configuration after submission.
The owning CI runtime/database are disposable; failure evidence is retained.
"""
from __future__ import annotations
import argparse
import importlib.util
import json
import os
from pathlib import Path
import pwd
import re
import signal
import subprocess
import sys
import tarfile
import time
import uuid
from urllib.parse import unquote_to_bytes,urlsplit

SPEC=importlib.util.spec_from_file_location('cds_resources',Path(__file__).with_name('run-cds-performance.py'))
RESOURCE=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RESOURCE)
MIB=1<<20
CONFIG=Path('/etc/carlos-emr/carlos.properties')
FS_PROBE=r'''
import fcntl,json,os,pathlib,stat,sys,uuid
root=pathlib.Path(sys.argv[1]);chain=list(reversed(root.parents))+[root]
result={'uid':list(os.getresuid()),'gid':list(os.getresgid()),'groups':os.getgroups(),'components':[],'ok':False}
for key in ('Uid','Gid'):
    line=next(line for line in pathlib.Path('/proc/self/status').read_text().splitlines() if line.startswith(key+':'))
    result[key.lower()+'IncludingFs']=list(map(int,line.split()[1:]))
for index,path in enumerate(chain):
    try:
        info=path.lstat()
        result['components'].append({'index':index,'uid':info.st_uid,'gid':info.st_gid,'mode':oct(stat.S_IMODE(info.st_mode)),
            'directory':stat.S_ISDIR(info.st_mode),'symlink':stat.S_ISLNK(info.st_mode),'traversable':os.access(path,os.X_OK,effective_ids=True)})
    except OSError as failure:
        result.update(stage='ancestor-stat',failedComponent=index,errno=failure.errno);break
else:
    if any(row['symlink'] for row in result['components']):
        result['stage']='symlink-refused'
    else:
        probe=root/('.f15-readiness-'+uuid.uuid4().hex)
        created=False
        try:
            result['stage']='create-private-probe';probe.mkdir(mode=0o700);created=True
            result['stage']='write-force-lock-private-file'
            fd=os.open(probe/'owned',os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
            try:
                os.write(fd,b'owned-synthetic-readiness');os.fsync(fd);fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
            finally:os.close(fd)
            result['stage']='force-private-directory'
            fd=os.open(probe,os.O_RDONLY|os.O_DIRECTORY)
            try:os.fsync(fd)
            finally:os.close(fd)
            (probe/'owned').unlink();probe.rmdir();created=False
            result.update(ok=True,stage='verified-and-retired-own-unaccepted-probe')
        except OSError as failure:result['errno']=failure.errno
        finally:
            if created:
                try:
                    (probe/'owned').unlink(missing_ok=True);probe.rmdir()
                except OSError as failure:result['probeRetirementErrno']=failure.errno
print(json.dumps(result,sort_keys=True))
'''


def require(condition: bool, code: str) -> None:
    if not condition:
        raise RuntimeError(code)


def operator_reason(log: Path) -> dict:
    reasons=[]
    with log.open() as stream:
        for line in stream:
            require(len(line)<=65536,'DISK_FAULT_LOG_CAPACITY')
            matched=re.fullmatch(r'CDS_REASON: safeCode=([A-Z][A-Z0-9_]{0,95}) outcomeUnknown=(true|false)\n',line)
            if matched:
                reasons.append({'code':matched[1],'outcomeUnknown':matched[2]=='true'})
    require(len(reasons)==1,'DISK_FAULT_OPERATOR_REASON_MISSING')
    return reasons[0]


def refusal(log: Path) -> dict:
    values=RESOURCE.rows(log)
    submitted=[row for row in values if row.get('event')=='SUBMISSION']
    require(len(submitted)==1 and RESOURCE.UUID.fullmatch(str(submitted[0].get('submissionKey',''))),
            'DISK_FAULT_SUBMISSION_HANDLE_MISSING')
    require(not any(row.get('event') in ('ACCEPTED','RESULT') for row in values),'DISK_FAULT_UNEXPECTED_ACCEPTANCE_OR_RESULT')
    return {'submissionKey':submitted[0]['submissionKey'],'reason':operator_reason(log)}


def raw_enospc(path: Path) -> int:
    """Only register addresses/counts and errno may occur; never accept decoded buffers."""
    require(path.is_file() and not path.is_symlink() and path.stat().st_size<=MIB,'DISK_FAULT_TRACE_MISSING')
    count=0
    with path.open() as stream:
        for line in stream:
            require(len(line)<=1024,'DISK_FAULT_TRACE_CAPACITY')
            if re.search(r'\b(?:write|writev|pwrite64)\(',line):
                require(re.fullmatch(r'(?:write|writev|pwrite64)\((?:0|0x[0-9a-f]+), (?:0|0x[0-9a-f]+), (?:0|0x[0-9a-f]+)(?:, (?:0|0x[0-9a-f]+))?\) += -1 [A-Z0-9_]+ \([^\r\n]{1,128}\)\n',line)
                        is not None,'DISK_FAULT_TRACE_NOT_RAW_REGISTERS')
                if ' = -1 ENOSPC (No space left on device)' in line:
                    count+=1
            else:
                require(line.startswith(('+++ ','--- ')),'DISK_FAULT_UNEXPECTED_TRACE_RECORD')
    return count


def observe_worker_threads(proc: Path, pid: int, uid: int, observed: dict[int,str]) -> None:
    """Attribute traced native tasks to the already verified service JVM, not its launcher."""
    tasks=proc/str(pid)/'task'
    try:
        selected=list(tasks.iterdir())
    except (FileNotFoundError,ProcessLookupError):
        return
    require(len(selected)<=512,'DISK_FAULT_JVM_THREAD_CAPACITY')
    for task in selected:
        if not task.name.isdecimal():continue
        try:
            status=(task/'status').read_text()
            stat=(task/'stat').read_text()
        except (FileNotFoundError,ProcessLookupError):
            continue  # A completed task has no usable live attribution; never guess its trace ownership.
        require(len(status)<=65536 and len(stat)<=4096,'DISK_FAULT_JVM_THREAD_IDENTITY_CAPACITY')
        group=re.search(r'^Tgid:\s+([0-9]+)$',status,re.M)
        identities=re.search(r'^Uid:\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)$',status,re.M)
        require(group is not None and int(group[1])==pid and identities is not None
                and all(int(value)==uid for value in identities.groups()),'DISK_FAULT_JVM_THREAD_IDENTITY_CHANGED')
        fields=stat[stat.rfind(')')+2:].split()
        require(stat.split('(',1)[0].strip()==task.name and len(fields)>19 and fields[19].isdecimal(),
                'DISK_FAULT_JVM_THREAD_START_UNAVAILABLE')
        tid=int(task.name)
        require(tid not in observed or observed[tid]==fields[19],'DISK_FAULT_JVM_THREAD_REUSED')
        observed[tid]=fields[19]
        require(len(observed)<=512,'DISK_FAULT_JVM_THREAD_CAPACITY')


def collect_worker_traces(prefix: Path, threads: dict[int,str], leader: int, destination: Path) -> tuple[int,dict]:
    """Validate each observed JVM task's raw trace before publishing bounded combined evidence."""
    require(leader in threads and 0<len(threads)<=512 and not destination.exists(),'DISK_FAULT_TRACE_OWNERSHIP_MISSING')
    rows=[];parts=[];total=failures=0
    for tid,started in sorted(threads.items()):
        trace=Path(str(prefix)+'.'+str(tid))
        count=raw_enospc(trace)
        raw=trace.read_bytes()
        require(total+len(raw)<=MIB,'DISK_FAULT_COMBINED_TRACE_CAPACITY')
        rows.append({'tid':tid,'startTimeTicks':started,'offsetBytes':total,'bytes':len(raw),
                     'sha256':RESOURCE.digest(trace),'kernelEnospcFailedWrites':count})
        parts.append(raw);total+=len(raw);failures+=count
    with os.fdopen(os.open(destination,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600),'wb') as output:
        for part in parts:output.write(part)
    return failures,{'bytes':total,'sha256':RESOURCE.digest(destination),'threads':rows}


def private_json(path: Path, maximum: int=65536, *, owner: int=0) -> dict:
    RESOURCE.protected_file(path,owner=owner)
    require(path.stat().st_size<=maximum,'DISK_FAULT_PRIVATE_METADATA_CAPACITY')
    return json.loads(path.read_text())


def matches_local_source_uri(value: object, source: Path) -> bool:
    """Jackson's Path wire value is a local file URI, not a platform path string."""
    if not isinstance(value,str) or not source.is_absolute():return False
    try:
        parsed=urlsplit(value)
        if parsed.scheme!='file' or parsed.netloc or parsed.query or parsed.fragment \
                or re.search(r'%(?![0-9a-fA-F]{2})',parsed.path):return False
        decoded=unquote_to_bytes(parsed.path).decode('utf-8',errors='strict')
        expected=source.as_posix()
        return decoded==expected or source.is_dir() and decoded==expected+'/'
    except (ValueError,UnicodeError):return False


def retain_tmpfs(root: Path, destination: Path) -> dict:
    """Retain this entire synthetic failed fixture before its owned CI runtime retires."""
    require(not destination.exists(),'DISK_FAULT_ARCHIVE_ALREADY_EXISTS')
    files=total=0
    with tarfile.open(destination,'x:gz',dereference=False) as archive:
        require(root.is_dir() and not root.is_symlink(),'DISK_FAULT_ARCHIVE_ROOT_INVALID')
        archive.add(root,arcname='fixture',recursive=False)
        files+=1
        for directory,children,names in os.walk(root,followlinks=False):
            require(len(children)+len(names)<=1024,'DISK_FAULT_ARCHIVE_ENTRY_CAPACITY')
            for name in sorted([*children,*names]):
                selected=Path(directory)/name
                require(not selected.is_symlink() and selected.name!='carlos.properties' and selected.suffix!='.properties',
                        'DISK_FAULT_ARCHIVE_FOREIGN_OR_SECRET_INPUT')
                files+=1
                require(files<=2048,'DISK_FAULT_ARCHIVE_ENTRY_CAPACITY')
                if selected.is_file():
                    total+=selected.stat().st_size
                    require(total<=64*MIB,'DISK_FAULT_ARCHIVE_BYTE_CAPACITY')
                archive.add(selected,arcname=str(Path('fixture')/selected.relative_to(root)),recursive=False)
    destination.chmod(0o600)
    return {'bytes':destination.stat().st_size,'sha256':RESOURCE.digest(destination),
            'retainedEntries':files,'retainedRegularFileBytes':total}


def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--disposable',action='store_true')
    parser.add_argument('--scenario',choices=('reserve','enospc'),required=True)
    parser.add_argument('--fixture-metadata',type=Path,required=True)
    parser.add_argument('--jdbc-properties',type=Path,required=True)
    parser.add_argument('--evidence',type=Path,required=True)
    parser.add_argument('--classpath',required=True)
    parser.add_argument('--candidate-sha256',required=True)
    args=parser.parse_args()
    require(args.disposable and os.geteuid()==0 and Path('/.dockerenv').is_file(),'DISK_FAULT_DISPOSABLE_ROOT_REQUIRED')
    require(re.fullmatch(r'[0-9a-f]{64}',args.candidate_sha256) is not None,'DISK_FAULT_CANDIDATE_REQUIRED')
    require(args.evidence.is_absolute() and not args.evidence.exists() and args.evidence.parent==Path('/run'),
            'DISK_FAULT_PRIVATE_EVIDENCE_REQUIRED')
    args.evidence.mkdir(mode=0o700)
    proof=args.evidence/'proof.json'
    result={'protocol':1,'scenario':args.scenario,'candidateSha256':args.candidate_sha256,'status':'RUNNING',
            'scope':'preaccept disk failure; no accepted clinical run is expected'}
    save=lambda:RESOURCE.private_json(proof,result)
    save()
    process=None
    owned_root=None
    client=args.evidence/'mysql-client.cnf'
    env={key:value for key,value in os.environ.items()
         if key not in {'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH'}}
    try:
        metadata=private_json(args.fixture_metadata)
        root=args.fixture_metadata.parent
        require(str(root)==metadata['fixtureRoot'] and root.parent==Path('/var/lib/carlos-emr'),'DISK_FAULT_ROOT_BINDING')
        matches=[line.split() for line in Path('/proc/self/mountinfo').read_text().splitlines()
                 if len(line.split())>6 and line.split()[4]=='/var/lib/carlos-emr']
        require(len(matches)==1 and matches[0][matches[0].index('-')+1]=='tmpfs','DISK_FAULT_OWNED_TMPFS_REQUIRED')
        usage=os.statvfs(root)
        require(usage.f_blocks*usage.f_frsize==64*MIB and usage.f_bavail*usage.f_frsize>50*MIB,
                'DISK_FAULT_FIXED_TMPFS_CAPACITY')
        sys.path.insert(0,'/usr/lib/carlos-ctl')
        from carlos_ctl.o19_preflight import parse_properties_text
        require(CONFIG.is_file() and not CONFIG.is_symlink() and CONFIG.stat().st_uid==0
                and CONFIG.stat().st_mode&0o022==0 and CONFIG.stat().st_size<=65536,'DISK_FAULT_INSTALLED_CONFIG_TRUST')
        properties=parse_properties_text(CONFIG.read_text(encoding='ISO-8859-1'))
        jdbc=parse_properties_text(RESOURCE.protected_file(args.jdbc_properties).read_text(encoding='ISO-8859-1'))
        endpoint=urlsplit(jdbc['jdbcUrl'].removeprefix('jdbc:'))
        database=endpoint.path.removeprefix('/')
        require(endpoint.scheme=='mysql' and re.fullmatch(r'[A-Za-z0-9_]+_test',database)
                and not any((endpoint.username,endpoint.password,endpoint.query,endpoint.fragment)),
                'DISK_FAULT_EXPLICIT_TEST_DATABASE_REQUIRED')
        escape=lambda value:'"'+str(value).replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('\r','\\r')+'"'
        with os.fdopen(os.open(client,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600),'w') as output:
            output.write('[client]\n'+'\n'.join(key+'='+escape(value) for key,value in {
                'host':endpoint.hostname,'port':endpoint.port or 3306,'user':jdbc['jdbcUser'],'password':jdbc['jdbcPassword']}.items())+'\n')
        def ledger_counts():
            tables=('cds_import_run','cds_import_patient','cds_import_unit','cds_import_checkpoint','cds_import_event','cds_import_lineage')
            selected=[*('(SELECT COUNT(*) FROM '+table+')' for table in tables),
                      '(SELECT COALESCE(SUM(maintenance_active),0) FROM cds_import_admission)',
                      '(SELECT COALESCE(SUM(reserved_bytes),0) FROM cds_import_admission)']
            names=(*tables,'maintenanceActive','reservedUploadBytes')
            statement='SELECT '+','.join(selected)
            measured=subprocess.run(['mariadb','--defaults-file='+str(client),'--batch','--raw','--skip-column-names',database,'-e',statement],
                                    env=env,capture_output=True,text=True,timeout=30)
            fields=measured.stdout.strip().split('\t')
            require(measured.returncode==0 and len(fields)==len(names) and all(value.isdecimal() for value in fields),
                    'DISK_FAULT_LEDGER_ABSENCE_PROOF_UNAVAILABLE')
            return dict(zip(names,map(int,fields)))
        floor=int(properties.get('cds.import.minimumFreeDiskBytes',str(512*MIB)))
        require(floor==(1 if args.scenario=='enospc' else 512*MIB),'DISK_FAULT_FROZEN_RESERVE_POLICY')
        frozen_config=RESOURCE.digest(CONFIG)
        result.update(configurationSha256=frozen_config,tmpfsCapacityBytes=64*MIB,minimumFreeDiskBytes=floor,
                      faultPolicy='minimum valid one-byte reserve frozen BEFORE submission only to force kernel ENOSPC' if floor==1 else 'unchanged production reserve')
        helper=['java','-Xmx128m','-cp',args.classpath,
                'io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsCliEngineFixture']
        def fixture(operation,value):
            done=subprocess.run([*helper,operation,str(args.jdbc_properties),str(value)],env=env,capture_output=True,text=True,timeout=90)
            require(done.returncode==0 and len(done.stdout)<=65536,'DISK_FAULT_NATIVE_FIXTURE_VERIFICATION')
            return json.loads(done.stdout)
        require(fixture('verify-prepared',root)==metadata,'DISK_FAULT_PREPARED_BINDING')
        owned_root=root
        account=pwd.getpwnam('carlos')
        staged=Path(properties['cds.import.stagingRoot'])
        ready=subprocess.run([sys.executable,'-c',FS_PROBE,str(staged)],env=env,user=account.pw_uid,group=account.pw_gid,
                             extra_groups=[],capture_output=True,text=True,timeout=15)
        require(ready.returncode==0 and len(ready.stdout)<=16384,'DISK_FAULT_SERVICE_FS_PROBE_UNAVAILABLE')
        result['initialServiceFilesystemReadiness']=json.loads(ready.stdout)
        save()
        require(result['initialServiceFilesystemReadiness'].get('ok') is True,'DISK_FAULT_INITIAL_SERVICE_FILESYSTEM_NOT_READY')
        before=fixture('inspect','fixture')
        initial_ledger=ledger_counts()
        require(all(value==0 for value in [*before.values(),*initial_ledger.values()]),'DISK_FAULT_NONEMPTY_CLINICAL_DATABASE')
        result['beforeLedger']=initial_ledger
        source=Path(metadata['sources']['ordinary'])
        if args.scenario=='enospc':
            folder=source.parent/'kernel-enospc'
            folder.mkdir(mode=0o755)
            pdf=folder/'source.pdf'
            RESOURCE.pdf(pdf,48*MIB,'F15-'+uuid.uuid4().hex)
            source=folder
            xml=folder/'patient.xml'
            xml.write_bytes(RESOURCE.xml_start('F15'+uuid.uuid4().hex[:16].upper())+RESOURCE.report(pdf.name)+b'</PatientRecord></OmdCds>')
            xml.chmod(0o644)
            with xml.open('rb') as incoming:
                checked=subprocess.run(['xmllint','--nonet','--stream','--noout','--schema',
                    str(Path(__file__).resolve().parents[2]/'src/main/resources/cds-import/schema/EMR_Data_Migration_Schema.xsd'),'-'],
                    stdin=incoming,env=env,capture_output=True,timeout=30)
            require(checked.returncode==0,'DISK_FAULT_SOURCE_SCHEMA_INVALID')
        originals={file.name:{'bytes':file.stat().st_size,'sha256':RESOURCE.digest(file)}
                   for file in sorted(source.iterdir())} if source.is_dir() else {
                       source.name:{'bytes':source.stat().st_size,'sha256':RESOURCE.digest(source)}}
        result.update(originals=originals,beforeNative=before,freeBeforeSubmissionBytes=os.statvfs(root).f_bavail*usage.f_frsize)
        save()
        log=args.evidence/'operator.log'
        trace=args.evidence/'failed-syscalls'
        command=['strace','-qq','-ff','-e','trace=write,writev,pwrite64','-e','raw=write,writev,pwrite64',
                 '-e','status=failed','-o',str(trace),'/usr/sbin/carlos-ctl','cds-import','--input',str(source),
                 '--provider',str(metadata['provider']),'--source-namespace',metadata['namespace'],
                 '--source-profile',metadata['profile'],'--no-match-providers']
        started=time.monotonic()
        worker=None
        worker_threads={}
        with os.fdopen(os.open(log,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600),'wb') as output:
            process=subprocess.Popen(command,env=env,stdout=output,stderr=subprocess.STDOUT,start_new_session=True)
            while process.poll() is None:
                require(time.monotonic()-started<300,'DISK_FAULT_DEADLINE_RETAIN_STATE')
                # The runtime is dedicated/exclusive. Restrict to exact public import argv,
                # service UID/executable/start identity, not the handshake or runuser parent.
                for entry in Path('/proc').iterdir():
                    if not entry.name.isdecimal():
                        continue
                    try:
                        argv=(entry/'cmdline').read_bytes().split(b'\0')
                        if not RESOURCE.is_import_command(argv,str(source),str(metadata['provider'])):
                            continue
                        sample=RESOURCE.service_sample(int(entry.name))
                        if sample is None or sample['uid']!=pwd.getpwnam('carlos').pw_uid:
                            continue
                        identity=(int(entry.name),sample['started'])
                        require(worker is None or worker==identity,'DISK_FAULT_MULTIPLE_SERVICE_JVMS')
                        if worker is None:
                            require(argv.count(b'-Xmx512m')==1,'DISK_FAULT_SERVICE_HEAP_CHANGED')
                            account=pwd.getpwnam('carlos')
                            RESOURCE.verify_java_identity(identity[0],account.pw_uid,account.pw_gid,Path('/usr/bin/java').resolve(),identity[1])
                            worker=identity
                            result['serviceJvm']={'pid':worker[0],'startTimeTicks':worker[1],'uid':account.pw_uid}
                            save()
                        observe_worker_threads(Path('/proc'),worker[0],sample['uid'],worker_threads)
                    except (FileNotFoundError,ProcessLookupError):
                        continue
                time.sleep(0.1)
        result['importElapsedSeconds']=time.monotonic()-started
        result['operatorExitCode']=process.returncode
        RESOURCE.capture_evidence(result,'operatorEvidence',lambda:RESOURCE.operator_projection(log,root/'state/runs/cli-diagnostics'))
        save()
        require(process.returncode==4 and worker is not None,'DISK_FAULT_EXPECTED_OPERATOR_REFUSAL')
        operator=refusal(log)
        require(operator['reason']=={'code':'CDS_SOURCE_SNAPSHOT_FAILED' if args.scenario=='enospc' else 'CDS_INPUT_DISK_RESERVE',
                                      'outcomeUnknown':False},'DISK_FAULT_WRONG_OPERATOR_REASON')
        result.update(operator=operator,operatorExitCode=process.returncode)
        save()
        safe_trace=args.evidence/'service-failed-syscalls.log'
        failures,trace_proof=collect_worker_traces(trace,worker_threads,worker[0],safe_trace)
        result['kernelEnospcFailedWrites']=failures
        result['rawRegisterOnlySyscallTrace']=trace_proof
        save()  # A failed errno assertion must still retain its validated physical trace evidence.
        require((failures>0)==(args.scenario=='enospc'),'DISK_FAULT_KERNEL_ERRNO_PROOF_MISMATCH')
        jobs=list(staged.glob('job-*'))
        require(len(jobs)==1 and jobs[0].is_dir() and not jobs[0].is_symlink(),'DISK_FAULT_PREACCEPT_STATE_MISSING')
        service_uid=pwd.getpwnam('carlos').pw_uid
        seal=private_json(jobs[0]/'acceptance.seal',owner=service_uid)
        document=jobs[0]/seal['document']
        require(document.parent==jobs[0] and document.name=='acceptance-'+seal['intentSha256']+'.json'
                and RESOURCE.digest(document)==seal['intentSha256'],'DISK_FAULT_PREACCEPT_SEAL_CHANGED')
        journal=private_json(document,owner=service_uid)
        require(journal['operator']=='root' and journal['submissionKey']==operator['submissionKey']
                and journal['originalSourceModifiedByImporter'] is False
                and matches_local_source_uri(journal['request']['command']['input'],source)
                and not (jobs[0]/'accepting.seal').exists() and not (jobs[0]/'accepted.seal').exists(),
                'DISK_FAULT_PREACCEPT_OWNERSHIP_CHANGED')
        require((jobs[0]/'snapshot.pending').is_dir(),'DISK_FAULT_PARTIAL_SNAPSHOT_NOT_RETAINED')
        result['failureStage']='preaccept durable source snapshot (retained snapshot.pending), before registry acceptance'
        require(RESOURCE.digest(CONFIG)==frozen_config,'DISK_FAULT_CONFIGURATION_CHANGED_AFTER_SUBMISSION')
        for name,expected in originals.items():
            file=source/name if source.is_dir() else source
            require(file.stat().st_size==expected['bytes'] and RESOURCE.digest(file)==expected['sha256'],
                    'DISK_FAULT_ORIGINAL_CHANGED')
        after=fixture('inspect','fixture')
        require(after==before and fixture('verify-prepared',root)==metadata,'DISK_FAULT_CLINICAL_OR_ACCEPTANCE_MUTATION')
        lookup=args.evidence/'submission-lookup.log'
        with os.fdopen(os.open(lookup,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600),'wb') as output:
            found=subprocess.run(['/usr/sbin/carlos-ctl','cds-import','--status-submission',operator['submissionKey'],
                                  '--provider',str(metadata['provider'])],env=env,stdout=output,stderr=subprocess.STDOUT,timeout=180)
        require(found.returncode==4 and not any(row.get('event') in ('ACCEPTED','RESULT') for row in RESOURCE.rows(lookup)),
                'DISK_FAULT_SUBMISSION_RECONCILIATION_FALSE_SUCCESS')
        lookup_reason=operator_reason(lookup)
        require(lookup_reason['code']=='SUBMISSION_OUTCOME_NOT_YET_KNOWN','DISK_FAULT_SUBMISSION_LOOKUP_REASON')
        final_ledger=ledger_counts()
        require(final_ledger==initial_ledger,'DISK_FAULT_LEDGER_OR_CHECKPOINT_MUTATION')
        result.update(status='PASSED',afterNative=after,acceptedRuns=0,
                      originalSourceUnchanged=True,configurationUnchanged=True,partialPreacceptRetained=True,
                      freeAfterBytes=os.statvfs(root).f_bavail*usage.f_frsize,
                      traceSha256=trace_proof['sha256'],preacceptJournalSha256=seal['intentSha256'],
                      submissionLookupExitCode=found.returncode,submissionLookupReason=lookup_reason,afterLedger=final_ledger,
                      acceptanceProof='source snapshot precedes registry acceptance; no accepting seal; registry absence verified; not a clinical rollback claim')
        save()
    except Exception as failure:
        result.update(status='FAILED',failure=RESOURCE.safe_failure(failure))
        save()
        raise RuntimeError('CDS disk fault gate failed; retain exact source, submission and private evidence') from None
    finally:
        client.unlink(missing_ok=True)
        if process is not None and process.poll() is None:
            result['terminationRequested']=True
            os.killpg(process.pid,signal.SIGTERM)
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                result['terminationConfirmed']=False
            else:
                result['terminationConfirmed']=True
        if owned_root is not None:
            try:
                result['retainedStateArchive']=retain_tmpfs(owned_root,args.evidence/'retained-preaccept.tar.gz')
            except Exception as failure:
                result.update(status='FAILED',retentionFailure=RESOURCE.safe_failure(failure))
                save()
                raise RuntimeError('CDS disk fault retention failed; do not retire its owned runtime') from None
        save()
    print('PASS installed disk fault:',args.scenario,'acceptedRuns=0 kernelEnospcWrites='+str(failures),flush=True)


if __name__=='__main__':
    try:
        main()
    except RuntimeError as failure:
        print(str(failure),flush=True)
        raise SystemExit(1) from None
