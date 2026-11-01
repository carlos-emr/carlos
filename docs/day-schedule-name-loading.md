# Day-schedule patient-name loading (issue #5306)

The day-schedule REST responses need only each patient's ID, last name and first
name. Previously, every nonblank appointment called `getDemographicFormattedName`,
which loaded a full demographic, its eager associations, MRP and next appointment.
The endpoint now collects patient IDs once and calls a manager method that checks
patient-specific `_demographic` read access for every distinct nonzero ID before
any name query. A constructor projection reads only those three columns, in batches
of at most 500 distinct IDs. It never hydrates a demographic or enriches MRP/next
appointment data.

The existing `ScheduleManager.getDayAppointments` call and its synchronous
`AppointmentManager.getDayAppointments` audit remain in place. The old formatted-name
helper has no separate patient-name audit; unrelated enrichment is no longer run.
The endpoint still iterates the original appointment list, retaining its order,
blank-slot names, null names for missing patients, name formatting, and all other
response fields. A denied patient or patient directive still aborts the response.

## Synthetic measurements

Measured inside `ubuntu:26.04` (amd64), OpenJDK 25.0.4.1 and MariaDB 11.8.6,
on the release/2026.08 code with the change. The
`DayScheduleNamesDaoIntegrationTest` harness uses real MariaDB reads for the old
single-patient helper and the new bulk path, including the real provider and
next-appointment DAO implementations. Repeated schedules cycle over at most five
patients. Patient access checks execute through the real manager with a stubbed
security decision. The appointment list is supplied by a stub schedule manager.
Thus the counts below cover patient-name and enrichment SQL, excluding the common
appointment-list query, schedule audit, authentication and authorization SQL.

| Appointments | Patients | JDBC statements, old → bulk | Median ms, old → bulk | P95 ms, old → bulk |
| --- | --- | --- | --- | --- |
| 1 | distinct | 4 → 1 | 14.553 → 5.440 | 44.936 → 29.564 |
| 25 | distinct | 76 → 1 | 52.001 → 1.345 | 92.746 → 1.659 |
| 100 | distinct | 301 → 1 | 188.747 → 3.315 | 267.203 → 6.237 |
| 1 | up to 5 repeated | 4 → 1 | 1.736 → 0.788 | 2.410 → 5.969 |
| 25 | up to 5 repeated | 56 → 1 | 49.555 → 0.709 | 77.896 → 0.851 |
| 100 | up to 5 repeated | 206 → 1 | 132.707 → 1.247 | 175.436 → 5.470 |

Each timing includes `ScheduleService.getAppointmentsForDay` response assembly,
with five warmups followed by 15 samples per path. The JPA persistence context is
cleared before each sample; database buffers and JVM code stay warm. SQL stdout
logging is disabled by the MariaDB harness. Hibernate statistics remain enabled.
These are local synthetic service timings, excluding HTTP, JSON serialization and
real security-service/database costs; they do not establish production latency or
an SLA. Small-sample P95 values are the maximum observed sample and are noisy.

The automated budget assertion is deterministic: one statement and zero entity or
collection loads for the new path at 1, 25 and 100 appointments, with distinct or
repeated patients. No wall-clock threshold is asserted. Empty input runs no SQL;
a missing patient ID produces no name row.
500 existing patients plus a duplicate use one statement; adding the 501st
existing patient uses two. Both cases assert every returned name projection. The
harness also compares all response fields recursively with the old helper's
results. REST tests cover Unicode names, repeated/missing patients, blank slots and
ordering; manager tests cover denial and directive propagation before any bulk read.

## Reproduction

Compile and run the focused H2/manager/REST suite:

```sh
mvn -Dtest=DayScheduleNamesDaoIntegrationTest,DemographicManagerUnitTest,ScheduleServiceEndpointTest \
  -Dtest.forkCount=1 test
```

For real-database measurements, configure the existing MariaDB DAO harness with
`CARLOS_TEST_MARIADB_DAO=true`, `CARLOS_TEST_MARIADB_URL` (a server JDBC URL ending
in `/`), `CARLOS_TEST_MARIADB_USER` and `CARLOS_TEST_MARIADB_PASSWORD`. Use a
disposable local server/account allowed to create and drop isolated test schemas.
After compiling the tests, run with those variables exported:

```sh
mvn -Dtest=DayScheduleNamesDaoIntegrationTest -Dtest.forkCount=1 \
  -Dschedule.names.benchmark=true surefire:test
```

The harness creates and removes a uniquely named schema. Only synthetic patients
are used. `SCHEDULE_NAMES` output records the query counts and timing summaries.
