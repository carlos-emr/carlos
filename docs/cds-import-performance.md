# CDS import resource verification

Run the installed resource gates against the exact packaged candidate after the
ordinary installed host/container and retained-browser recovery gates. They
never edit a deployment configuration, change a failed source namespace, reset a
run, or delete an accepted snapshot to make another workload fit.

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
The required event file reconciles source patients and category occurrences;
clean completion also requires report delivery, cleanup and verified maintenance
release. Operator output remains in protected files. Failures retain the exact
accepted handle for investigation; the runner never automatically resubmits.

The separate `scripts/demographic-cds-large-ingress-playwright-checks.js` gate
authenticates through normal browser login and CSRF, then transfers a valid XML
larger than64MiB twice through real HTTP requests, once with Content-Length and
once with unknown-length chunked framing. Each request remains within the
server-advertised chunk bound. Independent52-byte journal hashing verifies the
entire completed transfer. Oversized individual requests must refuse without a
committed byte/chunk. This is authenticated transport verification through the
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
runs all three non-skipping profiles. Only whitelisted metric/count/hash proof
and a bounded log tail with the fresh database secret removed are published.
The owned synthetic runtime/database retire only when their CI job ends; this
does not authorize cleanup of any developer, retained-failure or clinical state.

These scripts are executable gates. Their existence is not an execution result.
Record the actual candidate hashes, runner resources and successful artifacts
before claiming the corresponding release acceptance has passed.
