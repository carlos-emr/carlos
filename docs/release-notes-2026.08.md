# CARLOS 2026.08 release notes

## REST appointment update compatibility

Existing schedule integrations can continue updating appointments without sending
`editVersion`; omitted, null or blank tokens retain last-writer-wins behavior.
No client changes are required. Clients can opt into conflict detection by echoing
`editVersion` from the `AppointmentTo1` response to `POST /schedule/getAppointment`;
the day and `fetchDays` summaries do not include it. A supplied stale token returns
HTTP 409 if another writer has changed the appointment. Both paths preserve
transactional archiving and server-managed audit fields. This applies to full appointment updates and the status, type and
urgency update routes. See the [REST appointment contract](api-collections-index.md#rest-appointment-edit-compatibility-and-conflict-detection)
and [#5663](https://github.com/carlos-emr/carlos/issues/5663).

## Inbox document descriptions

Document rows in the Inbox now always show the description the document was saved
with. Views that mix labs and documents, and the separate one-patient and name-search
views, previously showed the document's type (for example `others`) in that column.
This covers the Inbox Hub list, the legacy Inbox page and the REST inbox
(`/ws/rs/inbox/mine` and `/ws/services/inbox/mine`): a document item's `discipline`
field now carries the saved description rather than the type, and is empty for a
document saved without one. Integrations that need the type should read it from the
document. These lists also no longer stop at a document saved with no type, which
previously hid every document listed after it. See
[#5685](https://github.com/carlos-emr/carlos/issues/5685).

## Startup configuration compatibility

CARLOS refuses to start when the configuration is loaded only from the classpath
and `encryption.util.secret.key` is missing or blank. Earlier versions could
write a generated key to a `carlos.properties` file in the Tomcat account's home
directory. CARLOS now persists generated keys to the external configuration
that was actually loaded, so startup cannot silently depend on a different file.

Demo, CI and standalone WAR deployments must configure a stable key or select a
writable external `carlos.properties` using `-Dcarlos_override_properties`.
Packaged Debian installations and the devcontainer already select external
configuration files. A valid key supplied through classpath configuration
continues to work without writing to that resource.

For existing encrypted data, preserve and restore the original key; generating
a replacement prevents that data from being decrypted. See the
[startup encryption key guide](startup-encryption-key.md) for setup, deployment
paths and recovery instructions. This behavior is tracked in
[#5664](https://github.com/carlos-emr/carlos/issues/5664).
