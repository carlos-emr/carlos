<!--
Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

This software is published under the GPL GNU General Public License.
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

CARLOS EMR Project
https://github.com/carlos-emr/carlos
-->

# Disposable CDS container recovery proof

`scripts/ci/run-cds-container-recovery.py` is an opt-in controller for one ordinary
import of an already prepared synthetic source containing one Demographics and
256 ClinicalNotes occurrences. It waits for native notes and checkpoints with
pending work, SIGKILLs only the exact owned disposable container, replaces that
container, and resumes the same accepted run. The expected outcome is 514
checkpoints, 256 new native notes, one new native patient, no unrelated native
changes or holds, delivered reports, completed cleanup, and maintenance release
`RELEASED`.

The script was delivered without executing this clinical drill. A capability
probe or unit test is not clinical acceptance. Candidate artifact release remains
an external decision; the current artifact must be released before `--execute`.

## Preparation and immutable contract

An authorized operator must prepare and freeze the new candidate, source,
configuration, and two disposable containers **before any acceptance**. The
controller runs as the host user with access to the Unix Docker socket and Docker
CLI; only the image-owned ticket helper runs as container root. Java runs through
the real launcher as UID/GID 10001 with all capabilities dropped. Python 3 must be
installed in both containers. The target must have idle sleep as PID 1 and no
other processes. Both names must start `carlos-cds-`; the destructive target must
carry the exact explicit `io.carlos.cds.*` ownership label. The separate read-only
metric container is bound by its explicit name, immutable container ID, runtime
digests, and frozen dependency/JDBC hashes; it does not need to be replaced to add
a label. The controller never kills, replaces, or otherwise changes that metric
container.

The target contract is read-only root, `no-new-privileges`, 768MiB memory,
1536MiB memory plus swap, one CPU, CapDrop ALL, and only CHOWN, SETUID, SETGID and
SETPCAP for the root ticket helper. The actual launcher's capability reply must
report CLI protocol 1, Java 25, heap 512MiB, submission lookup, and exactly import,
status, resume, cancel and report. Browser API versions do not change this CLI
protocol.

`--prepared-root` names a Docker-daemon host directory with these persistent
binds. The controller never changes their contents or permissions.

| Leaf | Container destination | Access / namespace owner / mode |
| --- | --- | --- |
| config | `/run/carlos-config` | RO / root:10001 / 0750 |
| source | `/var/lib/carlos-emr/cds-import/source` | RO / root:10001 / 0750 |
| state | `/var/lib/carlos-emr/cds-import/state` | RW / 10001:10001 / 0700 |
| documents | `/var/lib/CarlosDocument` | RW / 10001:10001 / 0700 |
| evidence | `/gate-evidence` | RW / root:root / 0700 |
| whole prepared root | `/gate-preparation` | RO / root:root / 0711 |

The whole-root alias is read-only so its `config` and `source` children cannot
provide an alternate writable route around their dedicated read-only mounts.
Only the separate state, document and evidence leaves are writable. The controller
rejects a writable parent alias even when the source and configuration binds are
individually read-only.

The parent alias remains bound in the runtime contract and its permissions are
checked. Content hashing uses only the dedicated configuration, source and
installed code/dependency roots; it never recursively hashes the redundant parent
and its mutable state, document or evidence children.

The base's execute-only traversal allows the Docker daemon to rebind the same
private leaves. The production properties file is root:10001 0640. The metric
container's explicit JDBC file is 0600, owned by root:root or the controller's
actual Unix UID/GID; it is hash-bound and is never copied or printed.
Tmpfs `/carlos-tmp` is 10001:10001 0700 and `/run/carlos-cds-delegations` is
root:10001 0750. The launcher, exact JDK25, production classes and dependency
directories must be read-only binds at their installed paths. The metric helper,
its classpath dependencies, and its JDK must also be read-only binds. Unsupported
features, directory symlinks in immutable trees, extra writable binds, automatic
restart, prior accepted job documents, and ambiguous setup fail closed.

## Run preflight, then release the exact fingerprint

Use a new absent private controller evidence directory for each command. A
preflight performs read-only inspection and hashing and prints `HOLD` plus its
fingerprint; it does not submit an import. Source structural validation rejects
the original one-note fixture before acceptance.

```sh
python3 scripts/ci/run-cds-container-recovery.py \
  --disposable \
  --container carlos-cds-container-final-engine \
  --owner-label io.carlos.cds.task=APPROVED_TASK_VALUE \
  --prepared-root /tmp/cds-container-final-prep \
  --input /var/lib/carlos-emr/cds-import/source/PREPARED_LONG_SOURCE.xml \
  --source-namespace cli-e2e-d39ef336 --source-profile pcon-2024-02 \
  --provider 999998 \
  --metric-container carlos-cds-cli-final-engine \
  --metric-java /usr/lib/jvm/java-25-openjdk-amd64/bin/java \
  --metric-classpath '/opt/cds-candidate/helper:/usr/share/carlos-emr/webapp/carlos/WEB-INF/classes:/usr/share/carlos-emr/webapp/carlos/WEB-INF/lib/*:/usr/share/tomcat11/lib/*' \
  --jdbc-properties /run/cds-test/jdbc.properties \
  --evidence /tmp/cds-container-recovery-preflight-UNIQUE
```

After explicit external release, use exactly the same selectors, a new evidence
directory, and add `--execute --released-fingerprint APPROVED_SHA256`. The script
recomputes and checks that fingerprint before the sole submission. It binds the
source/configuration/artifact/JDK/launcher hashes, secret-free runtime digests,
clinical options, metric helper hashes, and exact metric container ID. Environment
values stay in memory and structured Docker API requests; evidence contains only
their digest. Replacing or restaging a candidate requires a new preflight and
release. Completed or partial runs accepted under older fingerprints must never
be resubmitted through this path.

The import's submission nonce and accepted run are durably saved before any kill.
The protected accepted job document is independently read through Docker archive
without running a target-side JDBC observer. Its hash, exact command, source
snapshot digest, and accepted ruleset digest must remain unchanged. Native
inspection uses only the separate frozen fixture's read-only `inspect` operation,
which independently confines itself to synthetic databases. A nonzero existing
database baseline is supported; global native counts must change by exactly one
patient and 256 notes.

The controller preserves the original container's actual cgroup peak and OOM
counters before SIGKILL, and checks the replacement's separate counters after
resume. The tiny kernel-counter probe runs inside the target and its memory
counts against 768MiB; native SQL metrics remain outside that budget. The old
container must show exit 137 without OOM, the killed import must have no RESULT,
and independent post-kill metrics must still show pending units. Container
replacement uses the exact image ID, environment, UID, caps, cgroups, network,
read-only flags and persistent bind paths. No volumes or clinical files are
deleted. A fresh operation-bound ticket resolves the original submission, and
another fresh ticket resumes its original handle. No second import is submitted.

Only `proof.json` with `containerRecovery: PASS` represents a completed drill.
Private JSONL logs retain bounded safe operational metadata and report digests,
not source/report bodies, credentials, property values, or raw exception text.
Timeout, OOM, a missed partial-work window, and every failure remain HOLD with
`failure.json` and all accepted persistent state retained. The controller does
not cancel, retry, raise caps, reset the database, or reconcile an uncertain run.
An interrupted controller needs explicit review of its recorded nonce, handle,
container identity and partial state; rerunning with a fresh evidence directory
is not a recovery mechanism.

Pure protocol checks, without Docker or clinical workflows:

```sh
python3 -m unittest discover -s scripts/ci -p test_cds_container_recovery.py
python3 -m py_compile scripts/ci/run-cds-container-recovery.py
```

After this run has completed and released maintenance, the same constrained
runtime can independently import a fresh copy of the versioned all-categories
corpus through `scripts/ci/run-cds-container-engine.py --expect-all-categories`.
Prepare that distinct synthetic source before freezing the source mount. This
second gate verifies the exact category occurrence set, native projections for
all seven notes, medication, allergy, partial prevention, appointments,
measurements, both documents, physician/referrer/contact/pharmacy associations
and the private-note-safe laboratory mirror with its original timestamp offset.
It compares all sixteen native table counts against the pre-import baseline and
checks actual cgroup peak and zero OOM events. Its new acceptance is independent
of the replacement drill; it never resubmits the interrupted source.
