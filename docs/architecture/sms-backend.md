# SMS backend foundation

This module provides persistence, consent checks, queueing, provider adapters and audited body access. It has no public send endpoint or SMS user interface, and only the `STUB` SMS backend has an adapter; this is not ready for clinical SMS traffic.

## Names and boundaries

| Name | Meaning |
| --- | --- |
| `SmsMessagePurpose` | Why a message exists: `PATIENT_MESSAGE`, `APPOINTMENT_REMINDER`, or `SYSTEM_TEST`. This is independent of direct versus queued dispatch. |
| `SmsProviderType` | An external SMS backend (`STUB`, `VOIPMS`, `CLOUDLI`), never a clinician. Only `STUB` has an adapter today. |
| `requestedByHealthcareProviderNo` | The CARLOS clinician/provider account requesting the message. |
| `SmsTransaction` | One logical SMS system-of-record entry, including retries; not one row per network attempt. |
| `applyIfVersionMatches` | Applies a worker update only if its claimed version is still current; preserves a newer webhook or worker update. |

There is one configured default SMS backend for new messages. Every row retains its selected backend, so changing the default does not reroute already queued work. Keep the resolver and per-backend limiter for this reason; per-message user routing and speculative adapter frameworks are unnecessary at this stage.

Business classes follow [the layer naming policy](layer-names.md): configuration and client selection use `Resolver`; multi-step queue, webhook and transaction operations use `Service`; the write-only body audit uses `Persister`; retry timing uses `Calculator`. `SmsQueueScheduler` and `LoggingSmsSendFailureListener` describe their executor and Spring event-listener lifecycle rather than introducing another business layer.

`SmsSendCommand.patientMessage(...)` constructs an ad-hoc patient-message command; the caller chooses direct or queued dispatch.

All adapters implement `send(command, clientReferenceId)`. There is no overload that discards the reference. The same logical message uses the same reference across recovery/retries. Adapters must use it for correlation and, where supported, idempotency. A client reference alone does not guarantee exactly-once delivery.

## Patient consent

`CarlosSmsConsentService` gates every outbound message on the patient's current consent record. It reads the existing `Consent` / `consentType` tables; SMS has no consent store of its own.

- The `sms_communication` row in the `property` table names the consent type to check, the same way `email_communication` does for email. `V1.0.32__add_sms_consent.sql` seeds it to a dedicated `sms_communication_consent` type. SMS deliberately does not reuse `electronic_communication_consent`: that wording never mentions text messages, and a text is visible on a locked screen. Existing patients therefore start blocked until SMS consent is recorded for them.
- The consent type is seeded **inactive** with a draft description. `V1.0.54__activate_sms_consent.sql` then replaces the draft with the approved wording (#3848) wherever the description is still the draft (ignoring letter case and spaces at the end, as the column compares text), and in the same statement switches the type on where the `sms_communication` property still points at it. A clinic's own wording is left alone, and a clinic that turned SMS consent off by deleting, clearing or repointing that property keeps it off, now with the approved wording. A clinic that switched only the type's active flag off, keeping the draft and the property, cannot be told apart from one that never switched it on, and is switched on. To switch it on later, point `sms_communication` at `sms_communication_consent` and run `UPDATE consentType SET active = 1 WHERE type = 'sms_communication_consent';`. To check after an upgrade: `SELECT description, active FROM consentType WHERE type = 'sms_communication_consent';`. An active consent type is shown on every patient record at every clinic; while it is inactive every patient SMS is blocked as `SMS_CONSENT_NOT_CONFIGURED` and staff never see the wording, since the add-patient form, the patient record and the consent REST API all list active consent types only. The approved wording does not name the patient's phone number, because consent is recorded for the patient, not for one number, until issue #2674. There is no administration screen for consent types, so later wording changes are made with an `UPDATE` in a new migration (never by editing a released one); consents already recorded are not tied to a wording version, which is part of the consent history work in issue #2674.
- Decisions, in order:

  | Situation | Row status | `consent_reason_code` | `consent_status` |
  | --- | --- | --- | --- |
  | `SYSTEM_TEST` message, `sms.systemTest.enabled=true` | proceeds | none | `SYSTEM_TEST` |
  | `SYSTEM_TEST` message, switch off | `CONSENT_BLOCKED` | `SMS_SYSTEM_TEST_DISABLED` | `SYSTEM_TEST` |
  | Missing command or patient | `CONSENT_BLOCKED` | `SMS_CONSENT_UNKNOWN` | `UNKNOWN` |
  | Property unset, or its consent type missing/inactive | `CONSENT_BLOCKED` | `SMS_CONSENT_NOT_CONFIGURED` | `NOT_CONFIGURED` |
  | No consent record, or the record is deleted | `CONSENT_BLOCKED` | `SMS_CONSENT_UNKNOWN` | `UNKNOWN` |
  | Patient opted out | `OPTOUT_BLOCKED` | `SMS_CONSENT_OPTED_OUT` | `OPT_OUT` |
  | Only an implied opt-in on record (`explicit=false`) | `CONSENT_BLOCKED` | `SMS_CONSENT_NOT_EXPLICIT` | `NOT_EXPLICIT` |
  | Patient explicitly consented | proceeds | none | `OPT_IN` |

- The `Consent` table has no unique key on patient and consent type, so a patient can hold more than one live record for the SMS type. The check reads all of them: any opt-out blocks the send, and otherwise the most recently edited explicit opt-in is the record relied on. A single-row lookup would pick one arbitrarily. An implied opt-in (`explicit=false`) never permits a send. The patient record stores explicit consent on every row it creates, so an implied row comes from an import or API caller. Staff clear the block once the patient confirms directly, by ticking "Patient confirmed consent directly" on the patient record and saving (#3858); a routine save never changes the flag.
- Each `sms_transaction` row keeps the consent it relied on: `consent_status`, `consent_id` and `consent_last_update_date` (the `Consent` row's edit date). The snapshot is written at admission. A dispatch-time block overwrites it. A dispatch-time permit rewrites it before the send only when it relies on a different record or edit date (for example the patient opted out and back in while the message was queued), so the row always names the consent the send actually relied on. If that rewrite is rejected because the row changed under the claim, the message is not sent; if it cannot be written at all, the message is not sent and the worker stops draining that SMS backend for the run. A permitting decision that names no consent state is never recorded, so it is never sent on.
- The consent record is read through `ConsentDao`, not `PatientConsentManager.getConsentByDemographicAndConsentType`. That manager method needs a `LoggedInInfo` for its privilege check and access log, and the queue worker rechecks consent on a scheduler thread with no session. Authorizing the sender is the job of the future send action, not of the consent check.
- Operator messages and reason codes never contain patient identifiers.

Not covered yet: consent scoped to a specific phone number or message type, and STOP-style inbound replies recording an opt-out. Both belong with the consent audit model in issue #2674.

Also not covered yet: an exception from the admission-time consent check propagates to the caller of `SmsSendService`/`SmsQueueService` with nothing persisted. That is fail-closed, but the future send action must catch it and must not render the exception message, which can carry query parameters.

## Admission, dispatch and recovery

1. Validate the request and evaluate consent before persisting it. A consent exception or missing decision must not leave claimable work behind. A body must fit one encoded SMS segment: 160 GSM-7 septets (extension characters such as `€` or `{` count twice), or 70 UTF-16 units when any character needs UCS-2 (supplementary characters such as emoji count twice). VoIP.ms rejects SMS over 160 characters and does not guarantee delivery of accented characters. `SYSTEM_TEST` messages are synthetic and need no patient.
2. Persist the consent decision with the initial row in one transaction. A blocked row retains the body length/hash but discards the full body.
3. Before a queued send, recheck consent, including the current system-test switch, then acquire a rate-limit permit. Both the direct send and the worker claim the row before taking a permit, and release the claim back to `QUEUED` if the permit is denied, so losing the claim never uses up a permit. If the direct-send limiter throws and the release is confirmed, the response also says queued so the caller does not retry an already accepted request. A concurrent change that prevents release is reflected in the response: a newer delivered/sent result is preserved, and an unresolved sending claim carries a warning not to resend manually. A release exception still reaches the caller. After the permit, both renew the claim with a version-checked write just before the send, which restarts the stale-send clock. A permit wait can outlast the stale-send timeout, and another run's stale recovery may then have taken the row over and found it unsent at the SMS provider; if the renewal finds the row changed, nothing is sent, and the direct send reports the row's current state. A conflict renewing the claim or writing the dispatch-time consent snapshot after the permit can still spend a permit without sending; that only adds throttling. Send and worker entry points suspend any caller transaction so the claim commits before the external send. If the recheck itself throws (for example the consent tables are unreachable, or one patient's consent rows cannot be loaded), nothing is sent on the unverified consent state. The attempt counts and the row is rescheduled with the normal retry backoff (`QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED`), so a row that keeps failing cannot head the queue on every run; at the retry limit it ends `FAILED` with `QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED` for manual review. The worker stops draining that SMS backend until the next run so an outage costs one row an attempt per run. Consent-check failures share the send retry budget, so an outage of the consent tables lasting past the backoff window terminally fails the rows it touches even though their consent may be valid; they are surfaced by the `QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED` code and the send-failed event, and must be re-sent by an operator. If the reschedule cannot be written either, the row stays `SENDING` and the other SMS backends still drain; stale recovery then fails it for manual review unless the SMS provider's status lookup can confirm it was never received (the stub provider cannot).
4. Of the SMS provider's answers, only a definite rejection is eligible for a retry. An exception, null result or explicit uncertain result leaves the row `SENDING`, with an operator message explaining that its outcome is unknown.
5. Stale `SENDING` rows are reconciled through provider status lookup. A confirmed result updates the row; a definitive not-found result permits a bounded retry. An unavailable lookup ends in a failure requiring manual review. A timeout is not evidence that nothing was sent. Do not manually resend without reconciling with the provider.

A direct-send response reflects the persisted result, including a delivery webhook that arrived before the adapter response was saved. Callback identifiers must match the stored outbound message and its authenticated SMS backend. Callbacks cannot introduce internal queue/consent states. Opaque provider identifiers are case-sensitive and must not be silently truncated. A delivery callback that moves a message CARLOS sent (`SENDING` or `SENT`) into `FAILED` publishes `SmsSendFailedEvent`, which failure listeners receive only after the write commits. Nothing is published for a message that is already `FAILED` (a replay, including `SENT` and `FAILED` callbacks replayed in turn, because a failed message ignores a later `SENT`), for a callback the message ignores (out of order, or after `DELIVERED`), or for a callback that matches no stored message, including later callbacks for the placeholder row it created. A message blocked by consent ignores every callback, so the record of the block stays. A message waiting in the queue, for its first attempt or a retry, ignores a failure report, so the send that is still due stands; a report that it was sent or delivered is applied. A message the carrier reported as failed accepts a `SENT` report only when that report is newer than the failure; a message CARLOS marked failed itself (for example an unknown outcome) accepts any `SENT` report. Adapters must echo the client reference in callbacks: a callback that carries only a provider message id and arrives before the send response is saved matches no row.

## Rate-limit locking and regression check

Each permit uses a separate transaction. An atomic `INSERT ... ON DUPLICATE KEY UPDATE` creates the
provider row or locks the existing row without changing its counter. The subsequent `FOR UPDATE`
read and counter update run in that same transaction. This avoids the gap-lock deadlock from reading
an absent key first, and the shared-lock upgrade deadlock from `INSERT IGNORE`. The window time is
sampled after locking, so waiting callers cannot reset a newer window using an old timestamp.

`JpaSmsSendRateLimitMariaDbIntegrationTest` is an opt-in check against real MariaDB with repeatable
read and snapshot isolation enabled. Set `SMS_TEST_DB_URL` to a `jdbc:mysql://` server URL ending in `/`,
`SMS_TEST_DB_USER`, and `SMS_TEST_DB_PASSWORD` through the test environment, then run:

```sh
mvn -Dtest=JpaSmsSendRateLimitMariaDbIntegrationTest test
```

The account needs permission to create and drop databases, and the `PROCESS` privilege: the test reads
`information_schema.INNODB_TRX` and `INNODB_LOCK_WAITS` to prove that a call is blocked by a row lock.
The test creates a unique temporary schema, writes only synthetic rows there, and drops it afterwards.
It checks concurrent seeded and missing rows, lock release on commit/rollback, failed-permit retry, the
`REQUIRES_NEW` boundary, direct-send claim release, and that a claim taken over by stale recovery during
a long permit wait is not sent a second time.
Without `SMS_TEST_DB_URL` the test is skipped. Supplying it makes connection or isolation failures
fail the test. H2 tests cover ordinary DAO behavior but cannot establish MariaDB lock behavior.

Queue and stale-recovery claims lock with `FOR UPDATE SKIP LOCKED`. A claim skips any row another transaction has locked (another claim, a direct send, a delivery callback), so it can come back empty while rows are still due; a later claim or run picks them up. Waiting instead could deadlock two concurrent workers on MariaDB (#3913). This needs MariaDB 10.6 or later; CARLOS requires 11.4.

## Configuration and validation

- `sms.provider.default=STUB`: optional default for synthetic tests. An explicit unknown value blocks outbound SMS instead of silently simulating success. Known but unimplemented adapters are reported at startup.
- `sms.systemTest.enabled=true`: permits `SYSTEM_TEST` messages without a patient consent record. It has no effect on patient messages or appointment reminders, which always need recorded SMS consent.
- `sms.queue.scheduler.enabled=true`: required for automatic queue draining and stale recovery. It defaults off. Without it, invoke the worker explicitly; a queued response does not mean sent.
- `sms.queue.scheduler.intervalSeconds=60` and `sms.queue.scheduler.batchSize=60`: default polling controls.
- The initial database-coordinated limit is five sends per five-second fixed window per SMS backend. Confirm real carrier limits before enabling an adapter.

Run `mvn '-Dtest=**/sms/**/*Test' test` for the module's unit, persistence and competing-transaction tests. Tests use synthetic data. There is no browser flow to validate until a UI/API entry point is implemented.

Schema installation uses `V1.0.25__add_sms_system_of_record.sql`, `V1.0.31__add_sms_security_objects.sql`, `V1.0.32__add_sms_consent.sql` and `V1.0.54__activate_sms_consent.sql` in the active common Flyway migrations, for new installations and upgrades. Do not run the obsolete prototype `database/mysql/updates` script. Databases created manually from an earlier draft of this unmerged PR require an explicit schema/data conversion before `V1.0.25`: the draft `transaction_type`/`DIRECT` representation became `message_purpose`/`PATIENT_MESSAGE`. Do not drop existing SMS records to bypass a migration failure.

## Security objects

SMS has three security objects. A role's grant is a ladder, `x` > `w` > `u` > `r`, so a role granted `w` also passes an `r` check; `d` sits outside the ladder and only `d` or `x` satisfies it. `(roleUserGroup, objectName)` is the primary key of `secObjPrivilege`, so a role has one grant per object.

| Object | Purpose | Ask for | Seeded default |
| --- | --- | --- | --- |
| `_sms` | Sending patient text messages and viewing SMS history | `w` to send, `r` to view history | `admin` and `doctor`: `x` |
| `_admin.sms` | SMS configuration and the operational views (queue backlog, failures) | `w` to change, `r` to view | `admin`: `x` |
| `_msgSMS` | Reading a stored message body through `SmsMessageBodyReadService` (audited) | `r`, plus `_demographic` `r` when the transaction has a patient | `admin` and `doctor`: `x` |

- Only `_msgSMS` is enforced today, by `CarlosSmsMessageBodyAuthorizationService`. `_sms` and `_admin.sms` are seeded ahead of the code (`V1.0.31__add_sms_security_objects.sql`) so the grants exist before the first gate ships; nothing checks them yet. `_msgSMS` is seeded by `V1.0.25`.
- Both migrations leave any existing clinic row for a role and object untouched, whatever its privilege.
- Grants take effect on the next request: privileges are read from `secObjPrivilege` on every check and nothing caches them, so no restart is needed after the migration.
- A clinic that wants a role to view history without sending grants it `r` on `_sms`.
- `_admin` = `x` confers nothing on `_admin.sms`; a dotted object needs its own row.

The actions that check `_sms` and `_admin.sms` arrive with #3836, #3838, #3839 and #3841. They follow the security-check rules in `CLAUDE.md` and `docs/soap-rbac-hardening.md`: the paren-form `SecurityException` message, and the patient's `demographicNo` rather than `null` whenever the patient is known. Two things for the first of those PRs to settle, because the current code does not:

- A Struts action's refusal is handled as a 403 only if its package maps `java.lang.SecurityException` to a `securityError` global result (see `struts-form.xml`); otherwise the exception reaches the container error page. The messenger and eform packages define neither the mapping nor the result today, and `carlos-default` supplies no result, so both must be added.
- `CarlosSmsMessageBodyAuthorizationService` throws `commn.exception.AccessDeniedException`, which no Struts package maps to a refusal.

## Required before real SMS traffic

- Real provider clients, credentials, sender selection, status lookup and authenticated webhook endpoints. An adapter's request timeout must stay well under the five-minute stale-send timeout: the claim renewal covers only the wait before the send, so a request still in flight when stale recovery's status lookup reports it not found would be sent again.
- The send action must set `SmsMessagePurpose` server-side, never from request data. `SYSTEM_TEST` skips patient consent whenever `sms.systemTest.enabled` is on. `SmsSendValidator` refuses a system test that names a patient or an appointment, so it cannot be filed on a patient's record, but it cannot tell whose phone number it is given: only offer system tests to administrators.
- Phone-number and message-type consent scoping, and STOP-reply opt-out (issue #2674). Recording SMS consent needs no new UI: once the consent type is active, the patient record's consent section lists it.
- Message-body encryption, retention and purge policy. Allowed system-test and inbound bodies still use clear database text; keep them synthetic. Hashes are correlation data, not anonymization.
- Authorized, redacted UI/API DTOs and operational views for queue backlog, uncertain sends and failures. Do not expose JPA entities or internal send commands directly.
- Carrier-level integration tests and operational rollout validation, including how the chosen provider handles UCS-2 text within its limits.

Record diagnostics are redacted. Full body retrieval goes through authorization and a committed audit record. These code boundaries do not replace database access controls or the production data policy above.
