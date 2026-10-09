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

There is one active SMS backend per clinic, chosen in Administration > SMS (or `sms.provider.default` while nothing is saved), and new messages use it. Every row retains its selected backend. A provider change records a durable retirement boundary with the settings: old queued rows, including future retries, remain retired even if that provider is selected again before cleanup. The worker materializes their failures in bounded batches for staff to review and resend; new rows admitted after reactivation remain eligible. One failed cleanup write is skipped for that run, and two stop that provider's cleanup batch. An inactive queued STUB system test has its own reason asking the administrator to use Send test again.

The worker checks the active provider before claiming each row. After claim renewal, both the worker and direct send coherently check the sending switch, active provider, settings and retirement boundary immediately before dispatch. Changed or unreadable settings return an unsent eligible claim to the queue; a retired unsent claim is failed, and the worker stops draining for that run. No lock spans the provider call, so a configuration save after this final check cannot cancel a dispatch already starting. Recovery reloads settings per message; a retired uncertain `SENDING` row requires manual reconciliation without querying a new account or automatically retrying. If the active backend cannot be determined, a run sends and fails nothing. Keep the resolver and per-backend limiter; per-message user routing and speculative adapter frameworks are unnecessary at this stage. See [durable provider retirement](#durable-provider-retirement) for locking, deployment and rollback requirements.

Business classes follow [the layer naming policy](layer-names.md): configuration and client selection use `Resolver`; multi-step queue, webhook and transaction operations use `Service`; the write-only body audit uses `Persister`; retry timing uses `Calculator`. `SmsQueueScheduler` and `LoggingSmsSendFailureListener` describe their executor and Spring event-listener lifecycle rather than introducing another business layer.

`SmsSendCommand.patientMessage(...)` constructs an ad-hoc patient-message command; the caller chooses direct or queued dispatch.

All adapters implement `send(command, clientReferenceId, settings)`. There is no overload that discards the reference. The same logical message uses the same reference across recovery/retries. Adapters must use it for correlation and, where supported, idempotency. A client reference alone does not guarantee exactly-once delivery.

## Adding an SMS provider

Everything that differs between providers lives behind `SmsProviderClient`; the send, queue, settings and callback code never names one. A new provider is a Spring bean implementing that interface plus its name in `SmsProviderType` (stored on every row, so it is the one shared line; `VOIPMS` is already listed). `SmsProviderSwapUnitTest` proves this with a fake second provider registered as `CLOUDLI`. The provider supplies:

- `send(command, clientReferenceId, settings)` and, where the provider can, `lookupMessageStatus(clientReferenceId, providerMessageId, settings)`. The recipient always arrives in E.164 form (`+14165550123`); the provider converts it to its own format. `settings` (`SmsProviderSettings`) carries the clinic's sender number and this provider's credentials, and only the active provider gets them. A provider missing a value it needs answers a definite failure without contacting the SMS provider.
- `credentialFields()`: each `SmsCredentialField` has a name (stored under it, posted as `credential.<PROVIDER>.<name>`), a label message key the provider adds to all five bundles, and whether it is required before sending can be switched on. `requiresSenderNumber()` does the same for the sender number.
- `sendRateLimit()`: how many texts per window (`SmsSendRateLimit`); the default is five per five seconds.
- `validateCallback`, `parseInboundWebhook` and `parseDeliveryWebhook` for its callbacks.

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
3. Before a queued send, recheck consent, including the current system-test switch, then acquire a rate-limit permit. Both the direct send and the worker claim the row before taking a permit, and release the claim back to `QUEUED` if the permit is denied, so losing the claim never uses up a permit. If the direct-send limiter throws and the release is confirmed, the response also says queued so the caller does not retry an already accepted request. A concurrent change that prevents release is reflected in the response: a newer delivered/sent result is preserved, and an unresolved sending claim carries a warning not to resend manually. A release exception still reaches the caller. After the permit, both renew the claim with a version-checked write just before the send, which restarts the stale-send clock. A permit wait can outlast the stale-send timeout, and another run's stale recovery may then have taken the row over and found it unsent at the SMS provider; if the renewal finds the row changed, nothing is sent, and the direct send reports the row's current state. A conflict renewing the claim or writing the dispatch-time consent snapshot after the permit can still spend a permit without sending; that only adds throttling. Send and worker entry points suspend any caller transaction so the claim commits before the external send. If the recheck itself throws (for example the consent tables are unreachable, or one patient's consent rows cannot be loaded), nothing is sent on the unverified consent state. The attempt counts and the row is rescheduled with the normal retry backoff (`QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED`), so a row that keeps failing cannot head the queue on every run; at the retry limit it ends `FAILED` with `QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED` for manual review. The worker stops draining that SMS backend until the next run so an outage costs one row an attempt per run. Consent-check failures share the send retry budget, so an outage of the consent tables lasting past the backoff window terminally fails the rows it touches even though their consent may be valid; they are surfaced by the `QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED` code and the send-failed event, and must be re-sent by an operator. If the reschedule cannot be written either, the row stays `SENDING` and the run goes on; stale recovery then fails it for manual review unless the SMS provider's status lookup can confirm it was never received (the stub provider cannot).
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

Administration > SMS (`admin/ConfigureSms`, `_admin.sms` read to view, write to save) stores the settings in `sms_config` (`V1.0.59`). Until someone saves that page, the properties below still apply; once saved, the stored values win:

- **Provider:** only providers with an installed client can be chosen, because sends through any other would fail. Today that is `STUB` only.
- **Sending on/off:** while off, `SmsSendService.send` and `SmsQueueService.enqueue` refuse new messages without recording them, and the scheduler leaves already-queued messages (and stale-send recovery) alone until sending is turned back on. The system test still works, so the setup can be checked before sending is turned on.
- **Queue scheduler on/off:** applied at once on the server where it is saved (the scheduler starts or stops after the save commits); other servers pick it up at their next start.
- **Sender number, webhook secret and provider credentials:** the secret and every credential value are encrypted at rest with `EncryptionUtils` (`encryption.util.secret.key`, outside the database). The page never shows them back; a blank field keeps the stored value. Only the credential fields the chosen provider declares (`SmsProviderClient.credentialFields()`) are kept, each labelled with the provider's translated label. The form renders a credential group for each installed provider and enables only the selected group. Input names include the provider; nonblank values for another provider, unknown fields or old unscoped names refuse the whole save with a form error. Without JavaScript all groups remain available and the same server check applies. Choosing another provider clears every stored credential, so one provider's login is never handed to another that uses the same field name; the webhook secret is CARLOS's own and is kept. Sending can be switched on only once the provider's required credentials and, if it needs one, the sender number are saved. A credential that no longer decrypts (for example after `encryption.util.secret.key` changed) counts as not stored: the page warns and asks for it again. Leaving an optional one blank drops it; a required one is kept until it is entered again, in case the key is put right. While the active provider is not ready (a required credential or sender number is missing, or a saved credential cannot be read), direct sends and queueing are refused with nothing recorded, and the queue worker leaves that provider's rows waiting, with an error in the log naming the reason and a readiness warning on the settings page. Credentials only reach a provider from saved settings, so a provider that needs them is never ready while only `sms.provider.default` names it.
- **Send system test:** sends the fixed text "CARLOS SMS system test. No reply needed." to a number the administrator types, through `STUB` only, as a `SYSTEM_TEST` with no patient. It still needs `sms.systemTest.enabled=true`; never type a patient's number. If a rate-limited system test stays queued when STUB is inactive, the worker fails it as `QUEUE_SYSTEM_TEST_NOT_ACTIVE`, asking the administrator to use Send test again.
- Each save from Administration > SMS writes an audit record (`SmsConfigAuditRecorder`, content `sms_config`): who saved, the provider and switches now in force, and the names of the settings that changed. It never holds a secret, a credential or the sender number. The record joins the save's transaction, so a save that cannot be audited is not stored.
- **Saves from an out-of-date page are refused.** The form sends back the settings version it showed, and the save is refused with "another administrator saved…" when the stored version has moved on, so a page left open in another tab cannot silently put back settings someone has since changed (such as turning sending on again). Two saves racing each other are refused the same way (MariaDB error 1020 or a duplicate key). A second click on Save, whose settings the first click already stored, is reported as saved. If a secret cannot be encrypted (no working `encryption.util.secret.key`), the form shows an error and nothing is stored.

- `sms.provider.default=STUB`: optional default for synthetic tests. An explicit unknown value blocks outbound SMS instead of silently simulating success. Known but unimplemented adapters are reported at startup.
- `sms.systemTest.enabled=true`: permits `SYSTEM_TEST` messages without a patient consent record. It has no effect on patient messages or appointment reminders, which always need recorded SMS consent.
- `sms.queue.scheduler.enabled=true`: required for automatic queue draining and stale recovery. It defaults off. Without it, invoke the worker explicitly; a queued response does not mean sent.
- `sms.queue.scheduler.intervalSeconds=60` and `sms.queue.scheduler.batchSize=60`: default polling controls.
- The database-coordinated limit is a fixed window per SMS backend, which each provider states (`SmsProviderClient.sendRateLimit()`); the default, and the stub's, is five sends per five seconds. Confirm real carrier limits before enabling an adapter.

Run `mvn '-Dtest=**/sms/**/*Test' test` for the module's unit, persistence and competing-transaction tests. Tests use synthetic data. The browser entry point is Administration > SMS (`admin/ConfigureSms`): check it by saving the settings and sending a system test, which always goes through `STUB`.

Schema installation uses `V1.0.25__add_sms_system_of_record.sql`, `V1.0.31__add_sms_security_objects.sql`, `V1.0.32__add_sms_consent.sql`, `V1.0.54__activate_sms_consent.sql` and `V1.0.59__add_sms_config.sql` in the active common Flyway migrations, for new installations and upgrades. Do not run the obsolete prototype `database/mysql/updates` script. Databases created manually from an earlier draft of this unmerged PR require an explicit schema/data conversion before `V1.0.25`: the draft `transaction_type`/`DIRECT` representation became `message_purpose`/`PATIENT_MESSAGE`. Do not drop existing SMS records to bypass a migration failure.

## Security objects

SMS has three security objects. A role's grant is a ladder, `x` > `w` > `u` > `r`, so a role granted `w` also passes an `r` check; `d` sits outside the ladder and only `d` or `x` satisfies it. `(roleUserGroup, objectName)` is the primary key of `secObjPrivilege`, so a role has one grant per object.

| Object | Purpose | Ask for | Seeded default |
| --- | --- | --- | --- |
| `_sms` | Sending patient text messages and viewing SMS history | `w` to send, `r` to view history | `admin` and `doctor`: `x` |
| `_admin.sms` | SMS configuration and the operational views (queue backlog, failures) | `w` to change, `r` to view | `admin`: `x` |
| `_msgSMS` | Reading a stored message body through `SmsMessageBodyReadService` (audited) | `r`, plus `_demographic` `r` when the transaction has a patient | `admin` and `doctor`: `x` |

- `_msgSMS` is enforced by `CarlosSmsMessageBodyAuthorizationService`; `_sms` `r` by `ViewSmsHistory2Action` (the patient's SMS history, #3839, together with `_demographic` `r` for that patient); and `_admin.sms` by `ConfigureSms2Action` (Administration > SMS, #3836: `r` to view the page, `w` to save or send a system test). `_msgSMS` is seeded by `V1.0.25`, and `_sms` and `_admin.sms` by `V1.0.31__add_sms_security_objects.sql`.
- Both migrations leave any existing clinic row for a role and object untouched, whatever its privilege.
- Grants take effect on the next request: privileges are read from `secObjPrivilege` on every check and nothing caches them, so no restart is needed after the migration.
- A clinic that wants a role to view history without sending grants it `r` on `_sms`.
- `_admin` = `x` confers nothing on `_admin.sms`; a dotted object needs its own row.

The history view (#3839) and the settings page (#3836) are the first actions to check `_sms` and `_admin.sms`; the rest arrive with #3838 and #3841. They follow the security-check rules in `CLAUDE.md` and `docs/soap-rbac-hardening.md`: the paren-form `SecurityException` message, and the patient's `demographicNo` rather than `null` whenever the patient is known. Two things each new action has to handle:

- A Struts action's refusal is handled as a 403 only if its package maps `java.lang.SecurityException` to a `securityError` global result (see `struts-form.xml`); otherwise the exception reaches the container error page. The demographic package (`sms/ViewSmsHistory`) and the admin package (`admin/ConfigureSms`) have both. The messenger and eform packages define neither the mapping nor the result today, and `carlos-default` supplies no result, so an SMS action placed there must add both.
- `CarlosSmsMessageBodyAuthorizationService` throws `commn.exception.AccessDeniedException`, which no Struts package maps to a refusal. The caller must catch it: `ViewSmsHistory2Action.showMessage` renders a denial page instead of the text.

## Required before real SMS traffic

- Real provider clients, status lookup and authenticated webhook endpoints (credentials and the sender number now reach the active provider through `SmsProviderSettings`). An adapter's request timeout must stay well under the five-minute stale-send timeout: the claim renewal covers only the wait before the send, so a request still in flight when stale recovery's status lookup reports it not found would be sent again.
- Changing the credentials to a different account of the same provider leaves that account's stale `SENDING` rows to be looked up with the new account's credentials. A provider client must not answer "not found" for a message it cannot see in the current account, or stale recovery would send it again.
- The send action must set `SmsMessagePurpose` server-side, never from request data. `SYSTEM_TEST` skips patient consent whenever `sms.systemTest.enabled` is on. `SmsSendValidator` refuses a system test that names a patient or an appointment, so it cannot be filed on a patient's record, but it cannot tell whose phone number it is given: only offer system tests to administrators.
- Phone-number and message-type consent scoping, and STOP-reply opt-out (issue #2674). Recording SMS consent needs no new UI: once the consent type is active, the patient record's consent section lists it.
- Message-body encryption, retention and purge policy. Allowed system-test and inbound bodies still use clear database text; keep them synthetic. Hashes are correlation data, not anonymization.
- Authorized, redacted UI/API DTOs and operational views for queue backlog, uncertain sends and failures. Do not expose JPA entities or internal send commands directly.
- Carrier-level integration tests and operational rollout validation, including how the chosen provider handles UCS-2 text within its limits.

Record diagnostics are redacted. Full body retrieval goes through authorization and a committed audit record. These code boundaries do not replace database access controls or the production data policy above.

## Durable provider retirement

After a provider change in Administration > SMS, texts admitted under the retired selection remain
retired even if the clinic selects that provider again before any worker runs. CARLOS stores a
monotonic transaction-ID boundary for each provider in the existing InnoDB `SystemPreferences`
table, under `sms.provider.retiredThrough.<PROVIDER>`, together with `sms.provider.retirement.v1`.
The complete registry is initialized on the first save with this version of CARLOS. Missing legacy
state means no tracked earlier retirement; partial, duplicate, negative, or malformed registry state
blocks admission and dispatch. Retirements before the upgrade cannot be reconstructed from the old
configuration row.

Admission, settings changes, claims, renewal, and returning an unsent claim acquire the existing STUB
rate-limit row as a database mutex before configuration, retirement preferences, or message rows.
Its atomic upsert and lock do not consume a permit or reset its window. Every retirement reads the
highest committed outbound ID with a current locking read and commits its boundary with the settings
and audit. Provider calls and the separate rate-limit permit transaction occur after this lock is
released. A final coherent check includes the sending switch, current provider and settings, and
retirement boundary. This check cannot cancel a dispatch already starting when a later save commits.

Retired queued rows, including future retries, are failed in bounded worker batches for staff to
review and resend. New rows for a reselected provider are above its retirement boundary. With sending
or the scheduler disabled, failure display waits until a worker runs; retirement already prevents
sending on resume. An unsent claim returned after retirement is failed rather than requeued. Cleanup
writes each row in its own transaction; one failed write is skipped for that run so a healthy row can
proceed, and two failed writes stop that provider's cleanup batch.

Swapping does not proactively change rows already SENDING: an external send may already have started.
Its normal outcome/version guards and callback reconciliation still apply. Status recovery reloads
settings for each lookup. Retired uncertain sends require manual reconciliation without an external
lookup through the newly selected account and without an automatic retry. Existing stale-recovery
version races can discard a late synchronous result; adapter timeouts must stay below the stale-send
threshold. Same-provider account changes still need the conservative account-identity policy described
above before a real adapter ships.

Deploy this protocol with all older workers and admissions quiesced; mixed binaries cannot enforce
its boundary. Quiesce admissions and in-flight workers again across a downgrade. Keep sending and
workers disabled unless all retired queued/definitely-unsent rows have been materialized as terminal
failures and every retired uncertain `SENDING` row has been reconciled or materialized for manual
review so older recovery cannot query a different account and retry it. A manual-review failure does
not establish that the external send never occurred. Preserved keys alone cannot protect an older
binary. Keep the retirement keys and monotonic SMS IDs; deleting the registry or resetting IDs is
incompatible with this protocol's guarantees.
