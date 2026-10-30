# CDS import resource verification

Run the installed resource gates against the exact packaged candidate in an
exclusive disposable runtime. These resource profiles run independently of
browser and ordinary host/container acceptance. All gates remain mandatory
before any PR is marked ready. The resource gates never edit a deployment
configuration, change a failed source namespace, reset a run, or delete an
accepted snapshot to make another workload fit.

`scripts/ci/run-cds-performance.py` requires the actual installed `carlos-ctl`,
Java25/JDK instrumentation, MariaDB client, `xmllint`, root-owned synthetic
fixture metadata/JDBC properties and an already configured disposable runtime.
Its source directory must be a new child of that fixture's registered source
root. The service JVM still runs as the real non-root `carlos` account. Keep
other test writers stopped: query counters are explicitly **server-global**,
include observation overhead, and are not mislabeled as exact per-import SQL.

The `scaling` profile independently increases patients (1/8/32), laboratory and
note occurrences in one chart (128/512/2048 each), and inline plus external PDF
payloads (8/32/64MiB each). It does not accidentally turn these dimensions into
an unbounded cross product. A generated PDF is a real valid file with physical
bytes, a deterministic source comment stream, correct object/xref offsets and
SHA256. All generated patient XMLs pass the pinned OntarioMD XSD before import.

The `aggregate20` profile imports320 distinct patients with320 distinct64MiB
external PDFs in one ZIP. More than20GiB of real expanded source and native
document bytes are read, copied, hashed and reconciled. The producer streams one
PDF at a time into the archive, then retires only that unsubmitted temporary
producer file. Its original bytes remain in the retained input ZIP. Compression
is approximately7:1, within the unmodified production ratio ceiling200; this
profile does not claim incompressible-source throughput. Production retains
both the physical expanded snapshot and each unit's native publication. There
is no global document deduplication. Admission requires at least48GiB of actual
free storage inside the configured source filesystem; the CI owner requires50GiB
after packaging, before starting the profile.

The `zip64` profile imports an actual5GiB external PDF member, not a small member
with only a ZIP64 flag. It requires18GiB of free storage (20GiB at CI admission).
Both large profiles run on separate ephemeral CI runners so a later profile
does not require deleting an earlier accepted workload. A missing resource,
insufficient space, refusal, unknown outcome, missing instrumentation or failed
assertion fails the gate. None is a skipped test or a successful smaller substitute.

The default measured budgets are1024MiB peak service-JVM RSS,500 global queries
per expected durable unit, and3600seconds per workload. The trusted runtime uses
its unchanged512MiB CLI heap, with an actual1536MiB cgroup and two CPUs in CI.
The initial1024MiB regression budget accounts for the earlier host suite's
approximately800MiB largest-waited-descendant observation, which was not
attributable to a single phase. It does not establish fit in the separately
tested768MiB/one-CPU deployment. These are explicit regression budgets for this
synthetic matrix, not universal production sizing promises. The proof records
RSS/high-water samples and wait4 kernel peak/CPU/I/O receipts, actual service GC
columns and numeric timestamped sample deltas, import time separately from
verification/rehashing time,
global counters, available disk, exact native rows/checkpoints/holds, independent
native-document hash/size multisets and all four protected report hashes.
One-second read-only observations also record connected/running database
threads, the current run's pending work after its execution inventory is sealed,
and usable bytes/inodes on the source filesystem. Unsealed inventory counts
remain unknown. Sampled maxima are lower bounds on the true peaks and sampled
minima are upper bounds on the true minima. Database observations include their
own connections/query overhead; the proof counts observer queries explicitly.
The transaction metric is the sampled age of the oldest currently active
InnoDB transaction on this exclusive synthetic server. It does not measure a
committed transaction's exact duration or select transaction SQL/parameters.
Before the measured imports, the same installed runtime/configuration executes
a separate read-only lookup of a fresh unknown submission. Its exact finite
refusal, unchanged native/registry state, configuration hash and exclusive
per-command SQL counter window record the framework startup cost. This control
does not resolve a patient/source profile or change the 500-queries-per-unit
regression budget. Per-import counters sampled when SUBMISSION and ACCEPTED
messages are observed help separate bootstrap, source-copy and clinical work;
those samples are explicitly not atomic phase boundaries.
New owned CI servers enable bounded statement-digest instrumentation before any
submission. The proof records actual per-invocation and observed-milestone
digest count/time deltas, with a finite statement class and an in-server hash of
each normalized shape. SQL text, bound values and transaction SQL are never
selected into the proof. Missing instrumentation, digest overflow/eviction or
counter reset fails the gate. Complete class/count/time totals and a canonical
hash cover every changed digest; each observation projects the top 64 shapes
with explicit omitted shape/count/time totals to keep all nine cases within
the existing bounded artifact contract. These digest counts include observers.
The 500-per-unit budget is compared with `budgetedQueries`: the case's global
`Questions` delta minus `harnessQueriesInCounterWindow`, the harness's own
explicitly counted statements issued strictly between the opening and closing
counter samples (each metric client invocation runs exactly one statement). The
closing sample stays charged, the raw `globalQueries` delta remains in the proof
beside both values, and the budget itself is unchanged, so a slower runner that
samples for longer no longer charges its own monitoring to the import. Actual
CLI progress-event counts make time-based operator reporting overhead visible.
Each PROGRESS line reads the durable run summary, so the CLI spaces them out as
an import runs longer: the first after 5s, then 10s and 20s, then one every 30s.
Bounded file and field reads share one explicit run/epoch control scope. Each
read checks interruption and active-attempt/stage budgets locally. At every
forced boundary, and otherwise after a wait that starts at 250ms and doubles
after each unchanged poll up to 2s (`CdsControlPollBackoff`), one prepared SELECT
on the already-owned unpooled coordinator checks its physical lock, run epoch,
cancellation and global admission in a fresh autocommit snapshot. A long file or
a slow disk therefore costs at most one such statement every two seconds, and a
cancellation or lost owner is still seen within about two seconds; a failed or
refused poll is never remembered, so the next check polls again. It does not
borrow an ordinary pool connection or open a Hibernate transaction for each
polling read. The ledger-status polls of the review, historical-snapshot and
standalone verification paths use the same pacing. Clinical unit
transactions retain their independent locks, fencing and atomic checkpoints.
Inside a clinical unit, a separate local-only scope checks the same active
attempt budget for bounded source-field reads and before/after successful work
and verification callbacks. It performs no owner SQL or nested transaction.
Binary publication remains outside the short clinical reference transaction;
an expired preparation copy retires only its own unpublished partial bytes.
Only a query-budget comparison may be deferred to later independent workloads,
and only after that case's engine, native rows/files/hashes, all reports, zero
holds, cleanup and release have passed every check. Its exact failed budget and
`FAILED_QUERY_BUDGET` status remain in the proof and the profile exits nonzero
after collecting the remaining cases. Any missing/unknown/unsealed outcome or
other failed check immediately stops the profile. No failed source is retried.
The required event file reconciles source patients and category occurrences;
clean completion also requires report delivery, cleanup and verified maintenance
release. Operator output remains in protected files. Failures retain the exact
accepted handle for investigation; the runner never automatically resubmits.

Large inline binary workload XML is validated in full through the shipped
`CdsXmlParser`, pinned schema and default input limits, in a separate JVM with
a 64MiB heap and a private disk spool. This test-only validator performs no
database or clinical writes and is excluded from the WAR. Its source hash,
complete byte count and decoded binary count bind the result to the original
wire input. Tiny XML and external-reference workload XML additionally use the
independent pinned-XSD `xmllint --stream` check. The 64MiB browser transport
fixture contains 64 bounded text report values so the independent validator
does not need one aggregate-sized text value.

The separate `scripts/demographic-cds-large-ingress-playwright-checks.js` gate
authenticates through normal browser login and CSRF, then transfers a valid XML
larger than64MiB twice through real HTTP requests, once with Content-Length and
once with unknown-length chunked framing. Each request remains within the
server-advertised chunk bound. Independent52-byte journal hashing verifies the
entire completed transfer. Oversized individual requests must refuse without a
committed byte/chunk. Missing/invalid CSRF must refuse without a reservation.
The first real declared-length request disconnects after a64KiB prefix; the same
upload UUID and exact chunk then resume to the complete independently verified
source, without another acceptance or transfer identity. This is authenticated transport verification through the
actual filters/connector; the ordinary browser gate separately exercises file
selection and visible submission controls. It accepts no clinical job, proves
run/native/checkpoint/hold counts unchanged, and retires only its explicitly
unaccepted transfers through the supported abandon operation. The exact Tomcat
PID/start identity is sampled under a2560MiB RSS and256MiB growth budget, both
recorded and optionally tightened by the gate's environment. Restart or missing
metrics fails. Supply `CDS_LARGE_INGRESS_DISPOSABLE=true`, an absent private
`CDS_LARGE_INGRESS_EVIDENCE` directory, the frozen `CDS_CANDIDATE_SHA256`,
`CDS_LARGE_INGRESS_SERVER_PID` and `CDS_LARGE_INGRESS_TOMCAT_BASE`, alongside
the normal protected browser-harness credentials. The explicit
`CDS_LARGE_INGRESS_BASE_URL` must select the owned real HTTP reverse proxy;
`CDS_LARGE_INGRESS_PROXY_TOKEN` must match its independently recorded test-only
response header. A TCP relay directly to Tomcat is insufficient. TLS validation
remains enabled. The separate actual ModSecurity positive/negative gate proves
the packaged body/content-type contract. `CDS_LARGE_INGRESS_CTL_LIBRARY`
can select the paired companion's trusted Python library when it is not
installed under `/usr/lib/carlos-ctl`. The runner reads only the upload-root
property from the deployed context's private properties and verifies actual
retained upload bytes before abandoning them. Archive byte parity with the
deployed WAR is a separate required deployment proof.

The versioned resource workflow builds the actual companion Debian package and
the CARLOS WAR, starts a pinned Ubuntu26.04/Java25 runtime and a fresh pinned
MariaDB server, migrates the full Ontario chain, prepares the real fixture, and
runs every non-skipping profile in the workflow matrix. Only whitelisted
metric/count/hash proof and a bounded log tail with the fresh database secret
removed are published.
The owned synthetic runtime/database retire only when their CI job ends; this
does not authorize cleanup of any developer, retained-failure or clinical state.

Two additional installed profiles cover preaccept storage failure. Each owns a
fresh database and runtime with a dedicated64MiB tmpfs for its entire synthetic
fixture. `disk-reserve` keeps the default512MiB reserve and must produce the
finite operator reason `CDS_INPUT_DISK_RESERVE`; it does not claim ENOSPC.
The mount's initial root identity/mode is explicit: Docker can retain the
image home directory's0750 mode while making the tmpfs root-owned, which denies
service traversal. The fixture mounts0:0/0755 before preparation, while its
source/fixture and service-owned staging directories retain their private modes.
Before any submission, the
real service UID/GID records every ancestor's ownership/mode and its filesystem
UID/GID, then proves private creation, writing, locking and file/directory fsync
with a tiny owned probe that it retires. An inaccessible ancestor fails fixture
admission before it can be mistaken for the intended storage fault. Existing
accepted paths are never chmodded to make the test pass.
`disk-enospc` freezes the minimum valid one-byte reserve before the first submission solely to inject
the storage failure. A valid48MiB external PDF and XML fit as originals, while
their physical snapshot cannot also fit. Raw-register-only failed-write tracing
must prove actual kernel `ENOSPC` from the identified non-root service JVM,
and the operator must see `CDS_SOURCE_SNAPSHOT_FAILED`. Syscall payloads,
transaction SQL and credentials are never included in this trace. No ptrace
capability is added to the runtime.
This failure occurs while making the first durable source snapshot before
acceptance, with the original still present; it is not evidence of a later
clinical publication or second snapshot-copy failure.

Both profiles require a retained unique submission nonce, nonzero command exit,
unchanged original bytes/configuration, an intact matching preaccept journal,
no accepting/accepted seal and registry/native/checkpoint/hold absence verified
against the actual database. Supported submission lookup must remain visibly
incomplete. CLI snapshot copying precedes registry acceptance; these are
preaccept refusals, not claims of rollback after an uncertain clinical commit.
The complete synthetic tmpfs, including its partial snapshot, is archived and
independently hash-verified into the job artifact before the owned environment
retires. If submission may have started and archive verification or the
zero-acceptance outcome proof is missing, the harness fails and leaves its owned
runtime, database, network and protected prerequisites intact for reconciliation.
The gate never fills another filesystem, retries the failed source,
resets a namespace or changes policy after submission.

These scripts are executable gates. Their existence is not an execution result.
Record the actual candidate hashes, runner resources and successful artifacts
before claiming the corresponding release acceptance has passed.
