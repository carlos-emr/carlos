# CDS browser lifecycle and effective-command gate

`scripts/demographic-cds-lifecycle-playwright-checks.js` is a manual gate for an
isolated, migrated reference database. It does not run imports by default: without
`CDS_LIFECYCLE_EXECUTE=true` it prints `NOT_EXECUTED` and exits 2. A Node unit-test
pass is preparation evidence; it is not evidence that these deployed scenarios ran.

Run only after the release coordinator identifies the frozen deployed WAR and
authorizes new acceptances, and after the retained browser recovery is complete.
Do not run concurrently with another login using the synthetic account, database
restore, service replacement, import gate, or admission/configuration change.
The runner retains accepted charts, jobs, immutable source, reports and its private
state file on success and failure. It never deletes or replaces accepted work.

## Ordinary accepted-run faults

The runner creates one valid source patient and 256 clinical notes: 514 expected
source/native units. Each note has a deterministic 48KiB synthetic payload (the
whole fixture remains below16MiB) to exercise real bounded transfer and leave a
measurable native-progress window. It drives the real import form with the reviewed provider,
program and facility, merge disabled, and provider-name matching disabled. The
last setting deliberately differs from the form's checked default.

1. The genuine isolated Nginx listener18085 forwards through an owned streaming
   relay18086 to native Tomcat. The relay forwards the exact armed contract-2
   submit unchanged once and withholds only its verified native202 response.
   SQL must prove a native patient/note, nonzero checkpoints, pending units and
   a hold before the real Nginx15-second read timeout produces504, and again
   immediately after504. The original listener18082/read300 remains unchanged.
   A Playwright route only records/arms and continues the original request; it
   does not manufacture the timeout. The raw exact response remains recorded.
2. The browser reads the existing owner-scoped upload receipt and opens that run.
   It does not reupload, submit again, mint another namespace, or treat an unknown
   acceptance as a refusal. Exactly one accepted submission must remain.
3. With another actual written/pending/held proof, it closes the import tab,
   reopens the authenticated form, and verifies the same run/source/options/rules.
4. With another actual written/pending/held proof, it expires only the current
   synthetic server session through the finite external test attach helper and
   Tomcat's native retained Session reference. The original
   cookie must then receive an actual protected HTTP refusal. A fresh native login
   opens the same accepted run. This is administrative native `HttpSession`
   expiration; it does not claim that an inactivity timer elapsed, nor substitute
   cookie deletion or logout for server-session expiration.
5. Completion requires the same immutable acceptance and sealed inventory,
   514 checkpoints, 256 native notes, one native patient, zero holds/pending units,
   clinical/report/cleanup/clean completion, no pending resume, and verified
   maintenance release. Native admission/note program context and truthful absent
   source authorship are checked. A missed active-progress window fails visibly.

The protected accepted job document, retained upload chunk journal, actual worker
snapshot seal/inventory/member, selected report-set digest and all four physical
report files are verified independently. Browser downloads of event, error and
completion reports must match those physical hashes. Web acceptance intentionally
stores `snapshot=null`; the worker's later `jobRoot/snapshot` publication is a
separate fact. The web source digest binds the ordered 52-byte chunk receipt
journal, rather than the later file-inventory digest.

The narrow shared metadata EOF observer can classify only its independently
proven Chromium bookkeeping case. The intentional real proxy504 has a separate
single-request expectation following actual upstream acceptance and proxy nonce
proof. The UI may cancel the non-JSON504 body; that exact expected cancellation
does not claim successful metadata EOF. Other script,
HTTP, console, network, UTF-8, JSON, ownership or completeness failures remain fatal.

## Separate synthetic-course command parity

Use `CDS_LIFECYCLE_MODE=synthetic` with a different private state file. This mode
creates one source patient and one note, then uses the actual web form's synthetic,
course and nonzero day-shift controls. It checks the reviewed active student count,
requires four units and one native note per copy, verifies shifted native birth and
note dates and per-copy program admission, and checks the protected immutable
command against every selected effective option. Two approved students therefore
require two native patients, two notes and eight checkpoints. It does not change
the ordinary fault gate's 514-unit expectation.

The source namespace must already be explicitly registered for synthetic use;
educator/course/student/personal-program rows and delegation must already be
reviewed. The runner does not create grants, change configuration, infer a student
program, or fall back to ordinary chart import.

## Invocation and retained retry

Use the repository's installed Playwright version and its matched browser, the
normal protected browser credential wrapper, and the existing disposable MariaDB
configuration. The runner never needs credentials in command arguments.

Common explicit environment:

| Variable | Meaning |
| --- | --- |
| `CDS_LIFECYCLE_EXECUTE` | `true` only after coordinated execution release |
| `CDS_LIFECYCLE_MODE` | `faults` or `synthetic` |
| `CDS_LIFECYCLE_STATE_FILE` | Unique private absolute file under `/tmp` or `/workspace`; retain and reuse on retry |
| `CDS_LIFECYCLE_ARTIFACT_FILE`, `CDS_LIFECYCLE_ARTIFACT_SHA256` | Exact reviewed WAR bytes and pinned digest; deployment identity remains independently attested by the release coordinator |
| `CDS_LIFECYCLE_PROVIDER_NO`, `CDS_LIFECYCLE_PROGRAM_ID`, `CDS_LIFECYCLE_FACILITY_ID` | Reviewed, authorized nondefault context |
| `CDS_LIFECYCLE_SOURCE_NAMESPACE`, `CDS_LIFECYCLE_SOURCE_PROFILE` | Existing explicit source registration |
| `CDS_LIFECYCLE_SERVER_ROOT`, `CDS_LIFECYCLE_LOCAL_ROOT` | Exact protected staging/upload root and its local mounted equivalent for independent immutable-file verification |
| `CDS_LIFECYCLE_PROXY_CONTRACT_FILE` | Ordinary mode: protected descriptor for the genuine isolated Nginx18085 listener,15-second read timeout and response proof nonce |
| `CDS_LIFECYCLE_PROXY_RELAY_ARGV` | Ordinary mode: explicit executable argv prefix for the owned relay; target/control directory/attempt/context/port are appended by the runner |

Ordinary mode also requires `CDS_LIFECYCLE_SESSION_EXPIRE_ARGV`, a JSON argv array
for the local private Tomcat helper, and `CDS_LIFECYCLE_SESSION_PREFLIGHT_ARGV`
for its non-mutating attach/context check. Compile both helper and agent Java
sources into a private helper class directory using the same JDK, outside the
production artifact. For example, replace runtime/classes/PID/context with the
reviewed disposable instance:

```text
["docker","exec","-i","--user","1000:1000","PRIVATE_RUNTIME","java","--add-modules","jdk.attach","-cp","PRIVATE_HELPER_CLASSES","CdsSyntheticSessionExpiry","TOMCAT_PID","/carlos"]
```

The Java helper needs a JDK with local attach permission for that Tomcat process.
Its finite test agent uses native `Manager.findSession` and `Session.expire` on
the retained reference; it does not rewrite bytecode, install an application
endpoint, or use missing-session MBean getters. PID/start/artifact/context and
private request ownership must be attested. The selected session ID travels only
through stdin and an owned bounded0600 temporary request file, deleted in finally;
it is never an agent argument or general log. Unsupported attach fails before
acceptance rather than falling back to a different session mechanism.
The preflight stdout contract is the bounded object
`{"preflight":true,"supported":true,"atomicReference":true}`; normal expiration
additionally returns `expired:true`, `scope:"selected-HttpSession"`, and
`naturalTimeout:false`. Neither operation may print the session secret.

An example relay argv prefix for the disposable runtime is:

```text
["docker","exec","-i","--user","1000:1000","PRIVATE_RUNTIME","node","/workspace/carlos/scripts/lib/cds-lifecycle-proxy-relay.js"]
```

The relay binds loopback18086, forwards only to loopback native8080, streams
ordinary bodies, and buffers at most16KiB of the armed submit and2MiB of its
acceptance metadata. A private0600 exact upload/body hash arms the single
submission. It records no cookie, CSRF token or original body. Its own finite
fallback only closes the connection and cannot manufacture504. The real proxy
must supply the expected504. The runner stops its owned relay on every exit and
retains private control evidence. The isolated proxy listener must already be
reviewed/configured; the runner never edits Nginx or application configuration.

Synthetic mode additionally requires `CDS_LIFECYCLE_COURSE_ID`, nonzero
`CDS_LIFECYCLE_SHIFT_DAYS`, and `CDS_LIFECYCLE_EXPECTED_COPIES` (reviewed 1–8).

After a failed or interrupted invocation, reuse the exact state file, source and
artifact. A retained submission intent with no known outcome must resolve through
its already recorded upload; if that receipt is unavailable the runner fails
without resubmitting. A crash before a fault's evidence is durably recorded cannot
be retrospectively counted as a successful fault test. Do not delete the state
file merely to obtain a green run. Inspect and retain the accepted run first.

Local preparation checks:

```bash
node scripts/cds-lifecycle.test.js
node scripts/cds-lifecycle-proxy.test.js
node --check scripts/demographic-cds-lifecycle-playwright-checks.js
```

The focused tests run in the existing `test:scripts` glob without a browser or
database. Actual acceptance, tab closure, native session expiration and proxy
continuation evidence must be reported separately with the frozen artifact digest.
