# SMS backend foundation

This module provides persistence, consent checks, queueing, provider adapters and audited body access. It has no public send endpoint or SMS user interface. Only explicitly enabled system tests can currently pass consent; this is not ready for clinical SMS traffic.

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

## Admission, dispatch and recovery

1. Validate the request and evaluate consent before persisting it. A consent exception or missing decision must not leave claimable work behind.
2. Persist the consent decision with the initial row in one transaction. A blocked row retains the body length/hash but discards the full body.
3. Before a queued send, recheck consent, including the current system-test switch, then acquire a rate-limit permit. Send and worker entry points suspend any caller transaction so the claim commits before the external send.
4. Only a definite provider rejection is eligible for a retry. An exception, null result or explicit uncertain result leaves the row `SENDING`, with an operator message explaining that its outcome is unknown.
5. Stale `SENDING` rows are reconciled through provider status lookup. A confirmed result updates the row; a definitive not-found result permits a bounded retry. An unavailable lookup ends in a failure requiring manual review. A timeout is not evidence that nothing was sent. Do not manually resend without reconciling with the provider.

A direct-send response reflects the persisted result, including a delivery webhook that arrived before the adapter response was saved. Callback identifiers must match the stored outbound message and its authenticated SMS backend. Callbacks cannot introduce internal queue/consent states. Opaque provider identifiers are case-sensitive and must not be silently truncated. A delivery callback that moves a stored outbound message into `FAILED` publishes `SmsSendFailedEvent`, which failure listeners receive only after the write commits. A callback for a message that is already `FAILED` (such as a replay), a callback the message ignores (out of order, or after `DELIVERED`), and a callback that matches no stored message publish nothing.

## Configuration and validation

- `sms.provider.default=STUB`: optional default for synthetic tests. An explicit unknown value blocks outbound SMS instead of silently simulating success. Known but unimplemented adapters are reported at startup.
- `sms.systemTest.enabled=true`: permits only `SYSTEM_TEST` messages. Normal patient messages remain consent-blocked.
- `sms.queue.scheduler.enabled=true`: required for automatic queue draining and stale recovery. It defaults off. Without it, invoke the worker explicitly; a queued response does not mean sent.
- `sms.queue.scheduler.intervalSeconds=60` and `sms.queue.scheduler.batchSize=60`: default polling controls.
- The initial database-coordinated limit is five sends per five-second fixed window per SMS backend. Confirm real carrier limits before enabling an adapter.

Run `mvn '-Dtest=**/sms/**/*Test' test` for the module's unit, persistence and competing-transaction tests. Tests use synthetic data. There is no browser flow to validate until a UI/API entry point is implemented.

Schema installation uses `V1.0.25__add_sms_system_of_record.sql` in the active common Flyway migrations, for new installations and upgrades. Do not run the obsolete prototype `database/mysql/updates` script. Databases created manually from an earlier draft of this unmerged PR require an explicit schema/data conversion before this migration: the draft `transaction_type`/`DIRECT` representation became `message_purpose`/`PATIENT_MESSAGE`. Do not drop existing SMS records to bypass a migration failure.

## Required before real SMS traffic

- Real provider clients, credentials, sender selection, status lookup and authenticated webhook endpoints.
- Patient consent/opt-out integration and an agreed policy for changes while messages are queued.
- Message-body encryption, retention and purge policy. Allowed system-test and inbound bodies still use clear database text; keep them synthetic. Hashes are correlation data, not anonymization.
- Authorized, redacted UI/API DTOs and operational views for queue backlog, uncertain sends and failures. Do not expose JPA entities or internal send commands directly.
- Carrier-level integration tests, encoding/segment billing limits and operational rollout validation. The current 160-character input limit does not guarantee one encoded SMS segment for every alphabet.

Record diagnostics are redacted. Full body retrieval goes through authorization and a committed audit record. These code boundaries do not replace database access controls or the production data policy above.
