# Installed CDS CLI verification

The opt-in gate `scripts/ci/run-cds-cli-engine.py` exercises the installed
`carlos-ctl` package and actual CARLOS Java engine. It does not replace the
schema, physical transaction, authorization, browser or streaming-memory tiers.
It requires a disposable Ubuntu 26.04 container running as root, Java 25, the
non-root `carlos` service account, the matching installed CARLOS webapp/runtime
libraries and host companion package, and a freshly migrated synthetic test DB.
The CLI never applies a migration implicitly.

The Java fixture helper is test code:
`io.github.carlos_emr.carlos.demographic.cdsimport.cli.CdsCliEngineFixture`.
Compile it with the project's test sources and include its test classes plus
the candidate JDBC dependencies in `--classpath`. The actual import still uses
the separately installed application's production classpath through
`carlos-ctl`; the fixture helper only prepares synthetic rows/files and reads
bounded database verification counts.

Prepare a mode-0600 JDBC properties file with `jdbcUrl`, `jdbcUser` and
`jdbcPassword`. Pass only that file's path. The helper verifies that the actual
selected database is a test database before modifying it, requires the real
active bootstrap provider/doctor role, and creates explicit synthetic course,
student and alphanumeric provider rows. The fixed production properties must
be absent: the gate refuses to replace any existing deployment configuration.
The requested fixture root must also be absent. Use a new disposable runtime
for each gate invocation; do not rename a source namespace to bypass a failed
canonical lineage.

```sh
sudo python3 scripts/ci/run-cds-cli-engine.py \
  --disposable \
  --jdbc-properties /run/cds-test/jdbc.properties \
  --fixture-root /var/lib/carlos-emr/cds-cli-engine-check \
  --classpath 'target/test-classes:target/classes:/opt/carlos-candidate/WEB-INF/lib/*'
```

The gate requires all of these outcomes:

- A preview seals discovery, creates zero clinical checkpoints and reports
  `DRY_RUN_COMPLETE`, separately from clinical completion.
- Ordinary XML import publishes one complete patient with all expected units,
  matching native/checkpoint counts and no outstanding chart hold.
- Unchanged reimport reconciles as a no-op; completed-run resume adds no rows.
- Status and protected reports preserve the recorded clinical outcome. Every
  delivered report's size and SHA-256 must match the actual protected file.
- A valid alphanumeric provider key without delegation is denied by Java.
- The full pinned contract corpus accounts for all schema categories, including
  an independently authorized alphanumeric provider and its native prevention
  creator value. Directory and ZIP inputs each account for two patients.
- SIGTERM after a durable accepted handle is observed, explicit cancellation,
  and resume reconcile the original run through clinical completion.
- Synthetic course mode produces exactly two independent student copies.
  Missing/inactive personal programs, inactive students and inactive courses
  are refused without an ordinary-chart fallback or new committed units.

The script streams command output into mode-0600 files under its private
`operator-evidence` directory. It prints only a bounded operational pass record
with run IDs, the evidence directory and observed child-process peak RSS.
Credentials, patient XML and clinical report bodies are not printed. A timeout
stops the process group; forced termination aborts the gate and requires durable
reconciliation instead of automatic resubmission. Synthetic accepted sources,
run state and native rows remain intact for inspection until the disposable
environment is retired.

The JVM calendar zone must match the configured clinical zone. The one-shot
CLI selects the trusted configured zone before its application context starts;
web import refuses a mismatched runtime zone without changing the shared web
JVM. Root-owned delegation/source properties are immutable for each running
context: reload/restart is required for a running context to observe edits.
Provider/account/role changes in the database are checked at unit boundaries.
