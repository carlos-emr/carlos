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

- The `sms_communication` row in the `property` table names the consent type to check, the same way `email_communication` does for email. `V1.0.29__add_sms_consent.sql` seeds it to a dedicated `sms_communication_consent` type. SMS deliberately does not reuse `electronic_communication_consent`: that wording never mentions text messages, and a text is visible on a locked screen. Existing patients therefore start blocked until SMS consent is recorded for them.
- Decisions, in order:

  | Situation | Row status | `consent_reason_code` | `consent_status` |
  | --- | --- | --- | --- |
  | `SYSTEM_TEST` message, `sms.systemTest.enabled=true` | proceeds | none | `SYSTEM_TEST` |
  | `SYSTEM_TEST` message, switch off | `CONSENT_BLOCKED` | `SMS_SYSTEM_TEST_DISABLED` | `SYSTEM_TEST` |
  | Missing command or patient | `CONSENT_BLOCKED` | `SMS_CONSENT_UNKNOWN` | `UNKNOWN` |
  | Property unset, or its consent type missing/inactive | `CONSENT_BLOCKED` | `SMS_CONSENT_NOT_CONFIGURED` | `NOT_CONFIGURED` |
  | No consent record, or the record is deleted | `CONSENT_BLOCKED` | `SMS_CONSENT_UNKNOWN` | `UNKNOWN` |
  | Patient opted out | `OPTOUT_BLOCKED` | `SMS_CONSENT_OPTED_OUT` | `OPT_OUT` |
  | Only implied consent on record (`explicit=false`) | `CONSENT_BLOCKED` | `SMS_CONSENT_NOT_EXPLICIT` | `NOT_EXPLICIT` |
  | Patient explicitly consented | proceeds | none | `OPT_IN` |

- The `Consent` table has no unique key on patient and consent type, so a patient can hold more than one live record for the SMS type. The check reads all of them: any opt-out blocks the send, and otherwise the most recently edited explicit opt-in is the record relied on. A single-row lookup would pick one arbitrarily. An implied opt-in (`explicit=false`) never permits a send; the patient record always stores explicit consent, so such a row can only come from an import or API caller.
- Each `sms_transaction` row keeps the consent it relied on: `consent_status`, `consent_id` and `consent_last_update_date` (the `Consent` row's edit date). The snapshot is written at admission. A dispatch-time block overwrites it. A dispatch-time permit rewrites it before the send only when it relies on a different record or edit date (for example the patient opted out and back in while the message was queued), so the row always names the consent the send actually relied on. If that rewrite cannot be written, or is rejected because the row changed under the claim, the message is not sent.
- The consent record is read through `ConsentDao`, not `PatientConsentManager.getConsentByDemographicAndConsentType`. That manager method needs a `LoggedInInfo` for its privilege check and access log, and the queue worker rechecks consent on a scheduler thread with no session. Authorizing the sender is the job of the future send action, not of the consent check.
- Operator messages and reason codes never contain patient identifiers.

Not covered yet: consent scoped to a specific phone number or message type, and STOP-style inbound replies recording an opt-out. Both belong with the consent audit model in issue #2674.

Also not covered yet: an exception from the admission-time consent check propagates to the caller of `SmsSendService`/`SmsQueueService` with nothing persisted. That is fail-closed, but the future send action must catch it and must not render the exception message, which can carry query parameters.

## Admission, dispatch and recovery

1. Validate the request and evaluate consent before persisting it. A consent exception or missing decision must not leave claimable work behind.
2. Persist the consent decision with the initial row in one transaction. A blocked row retains the body length/hash but discards the full body.
3. Before a queued send, recheck consent, including the current system-test switch, then acquire a rate-limit permit. Send and worker entry points suspend any caller transaction so the claim commits before the external send. If the recheck itself throws (for example the consent tables are unreachable, or one patient's consent rows cannot be loaded), nothing is sent on the unverified consent state. The attempt counts and the row is rescheduled with the normal retry backoff (`QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED`), so a row that keeps failing cannot head the queue on every run; at the retry limit it ends `FAILED` with `QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED` for manual review. The worker stops draining that SMS backend until the next run so an outage costs one row an attempt per run. Consent-check failures share the send retry budget, so an outage of the consent tables lasting past the backoff window terminally fails the rows it touches even though their consent may be valid; they are surfaced by the `QUEUE_CONSENT_CHECK_FAILED_RETRY_EXHAUSTED` code and the send-failed event, and must be re-sent by an operator. If the reschedule cannot be written either, the row stays `SENDING` and the other SMS backends still drain; stale recovery then fails it for manual review unless the SMS provider's status lookup can confirm it was never received (the stub provider cannot).
4. Only a definite provider rejection is eligible for a retry. An exception, null result or explicit uncertain result leaves the row `SENDING`, with an operator message explaining that its outcome is unknown.
5. Stale `SENDING` rows are reconciled through provider status lookup. A confirmed result updates the row; a definitive not-found result permits a bounded retry. An unavailable lookup ends in a failure requiring manual review. A timeout is not evidence that nothing was sent. Do not manually resend without reconciling with the provider.

A direct-send response reflects the persisted result, including a delivery webhook that arrived before the adapter response was saved. Callback identifiers must match the stored outbound message and its authenticated SMS backend. Callbacks cannot introduce internal queue/consent states. Opaque provider identifiers are case-sensitive and must not be silently truncated.

## Configuration and validation

- `sms.provider.default=STUB`: optional default for synthetic tests. An explicit unknown value blocks outbound SMS instead of silently simulating success. Known but unimplemented adapters are reported at startup.
- `sms.systemTest.enabled=true`: permits `SYSTEM_TEST` messages without a patient consent record. It has no effect on patient messages or appointment reminders, which always need recorded SMS consent.
- `sms.queue.scheduler.enabled=true`: required for automatic queue draining and stale recovery. It defaults off. Without it, invoke the worker explicitly; a queued response does not mean sent.
- `sms.queue.scheduler.intervalSeconds=60` and `sms.queue.scheduler.batchSize=60`: default polling controls.
- The initial database-coordinated limit is five sends per five-second fixed window per SMS backend. Confirm real carrier limits before enabling an adapter.

Run `mvn '-Dtest=**/sms/**/*Test' test` for the module's unit, persistence and competing-transaction tests. Tests use synthetic data. There is no browser flow to validate until a UI/API entry point is implemented.

Schema installation uses `V1.0.25__add_sms_system_of_record.sql` and `V1.0.29__add_sms_consent.sql` in the active common Flyway migrations, for new installations and upgrades. Do not run the obsolete prototype `database/mysql/updates` script. Databases created manually from an earlier draft of this unmerged PR require an explicit schema/data conversion before this migration: the draft `transaction_type`/`DIRECT` representation became `message_purpose`/`PATIENT_MESSAGE`. Do not drop existing SMS records to bypass a migration failure.

## Required before real SMS traffic

- Real provider clients, credentials, sender selection, status lookup and authenticated webhook endpoints.
- Compliance sign-off on the seeded SMS consent wording, phone-number and message-type consent scoping, and STOP-reply opt-out (issue #2674). Recording SMS consent needs no new UI: the patient record's consent section lists every active consent type.
- Message-body encryption, retention and purge policy. Allowed system-test and inbound bodies still use clear database text; keep them synthetic. Hashes are correlation data, not anonymization.
- Authorized, redacted UI/API DTOs and operational views for queue backlog, uncertain sends and failures. Do not expose JPA entities or internal send commands directly.
- Carrier-level integration tests, encoding/segment billing limits and operational rollout validation. The current 160-character input limit does not guarantee one encoded SMS segment for every alphabet.

Record diagnostics are redacted. Full body retrieval goes through authorization and a committed audit record. These code boundaries do not replace database access controls or the production data policy above.
