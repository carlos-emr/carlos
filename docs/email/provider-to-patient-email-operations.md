# Provider-to-Patient Email Operations

This runbook describes the provider-to-patient email path currently confirmed in
CARLOS. It summarizes the operational gates, sender setup, monitoring workflow,
and known gaps from the investigation tracked in
[PR #3096](https://github.com/carlos-emr/carlos/pull/3096).

Do not treat provider-to-patient email as generally production-ready until the
deployment has addressed the setup gates and open blockers listed below. This
document is operational guidance, not a security certification.

## Current Supported Workflow

The currently supported provider-to-patient workflow is the eForm email flow:

1. A provider opens a patient eForm.
2. The provider clicks **Email** in the eForm toolbar.
3. The eForm save flow stores email options and attachment state.
4. The email compose screen opens with patient recipients, sender accounts,
   consent status, message fields, and attachments.
5. Sending posts through `EmailSend2Action`, which delegates delivery to
   `EmailManager`.
6. `EmailManager` creates an `EmailLog` row, attempts delivery through the
   configured sender, and updates the log to `SUCCESS` or `FAILED`.

CARLOS Messenger is separate from this path. It handles internal messaging and
document transfer workflows; it is not the confirmed mechanism for sending
patient-facing email.

No general chart-level "email patient" composer has been confirmed as supported
yet. Treat the eForm workflow as the supported patient-facing path unless a
separate chart composer is implemented, reviewed, and documented.

## Relevant Code Paths

- `src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar.js`
  handles the eForm Email button, valid-recipient checks, consent prompt, and
  hidden `emailEForm=true` submit flag.
- `src/main/java/io/github/carlos_emr/carlos/eform/actions/AddEForm2Action.java`
  saves the eForm and moves email options, attachment selections, and patient
  context into session state for the compose redirect.
- `src/main/java/io/github/carlos_emr/carlos/email/action/EmailCompose2Action.java`
  requires `_email` and read access to the patient (`_demographic`, including
  per-patient restrictions), and works in two steps. The first request takes
  the staged session state once, prepares the attachments and the one-time send
  token, and redirects to `email/emailComposeAction?composeView=<id>`. That view
  URL loads consent, recipients and active sender accounts and renders the
  compose screen. Refreshing it shows the same compose screen, password and
  attachments without preparing anything again; an attachment preview link is
  renewed once less than a minute of its two minutes remains.
  The window shows "This email compose window has expired" instead when:
  - a send was submitted from it, even one that failed, or it was cancelled;
  - 30 minutes have passed since it was prepared;
  - it was the oldest of more than eight unsent compose states in the
    session, which also counts Manage Emails resends and send retries;
  - the URL is opened in another session, including after logging in again;
  - Tomcat restarted, or the request reached another server, because the
    prepared state is held in that server's memory.

  If preparing the attachments or storing the state fails, the provider is
  returned to the eForm with a generic error instead.
- `src/main/java/io/github/carlos_emr/carlos/email/action/EmailSend2Action.java`
  requires `_email`, collects compose fields, and calls `EmailManager`.
- `src/main/java/io/github/carlos_emr/carlos/managers/EmailManager.java`
  creates and updates `EmailLog` records, performs optional PDF password
  protection, dispatches through `EmailSender`, and optionally writes a chart
  note.
- `src/main/java/io/github/carlos_emr/carlos/email/admin/ManageEmails2Action.java`
  powers the Manage Emails status search, resolved marking, and resend flow.

## Required Setup Gates

Before a clinic sends real patient communications, confirm all of these gates:

- The sending user has the `_email` privilege. The compose and send actions both
  enforce this privilege.
- Email consent tracking is configured. `EmailComposeManager` expects the
  `EMAIL_COMMUNICATION` user property to resolve to an active consent type.
- The patient has an appropriate email consent status for the clinic's policy.
  The eForm toolbar warns when the patient is not explicitly opted in.
- At least one active sender account exists in `emailConfig`.
- The patient demographic record contains at least one valid email address.
- Production sender configuration uses real delivery infrastructure, such as an
  SMTP relay or an API sender such as SendGrid.
- Production sender domains have SPF, DKIM, and DMARC configured and monitored.
- The document store and the database live on encrypted volumes, and the outbound
  email archive keyring is backed up with the server configuration. See
  [Outbound Email Archive at Rest](#outbound-email-archive-at-rest).

The current Configure Email admin page documents the `emailConfig` fields and
sample SMTP/API payloads, but sender records are still managed as deployment
configuration. Confirm the selected sender account is active before using real
patient communications.

## Local Development

Local development must not send real patient email.

The local sender path can use localhost SMTP. If no local capture service or
relay is running, mail may fail with connection or delivery errors. Configure a
local capture service before testing email in development, and use only non-PHI
test patients and messages.

Development mail capture is tracked in
[PR #3097](https://github.com/carlos-emr/carlos/pull/3097). If that work is not
present in the branch or environment being used, configure an equivalent local
capture service before sending test messages.

Local capture is only for safe development testing. Production delivery still
requires real sender infrastructure, valid credentials, and authenticated sender
DNS.

## Production Sender Expectations

For production use, treat email as an external delivery dependency:

- Use a supported SMTP relay or API provider with clinic-owned credentials.
- Use a sender address from a domain controlled by the clinic or organization.
- Configure SPF, DKIM, and DMARC for the sending domain before enabling real
  patient sends.
- Verify attachment size limits and content policies with the relay or API
  provider.
- Send non-PHI test messages after every sender configuration change.
- Confirm successful test delivery and `EmailLog` status before sending patient
  communications.

## Optional PDF Signing

Outgoing PDF attachments can carry a cryptographic signature. It is disabled by
default (`pdf.signing.enabled=false`); with it off, nothing about a send changes.

What a signature does and does not give the recipient:

- It lets a PDF reader show that the file has not been altered since CARLOS
  signed it, and which certificate signed it.
- It does not encrypt anything and does not replace the password-protected PDF
  workflow. Signing runs after that encryption, so the signature covers the
  exact bytes that are archived and sent.
- A self-signed certificate proves control of the clinic's private key, but
  readers show an "unknown signer" warning until the recipient trusts that
  certificate. A certificate from a recognised authority avoids the warning.

To enable it, provision a PKCS#12 or JKS keystore holding the private key and
its certificate chain, readable only by the Tomcat user, and set in
`carlos.properties`:

```
pdf.signing.enabled=true
pdf.signing.keystore.path=/etc/carlos-emr/pdf-signing.p12
pdf.signing.keystore.type=PKCS12
pdf.signing.keystore.password=...
pdf.signing.key.alias=...
```

`pdf.signing.key.password` defaults to the keystore password. The signer name,
reason, location and contact shown in the reader are optional. Passwords are
used exactly as written, so a trailing space after the value makes the keystore
unreadable.

Property changes take effect after a Tomcat restart. The keystore file itself is
read on every send, so a keystore replaced in place at the same path takes
effect immediately.

Signing is fail-closed. Once enabled, a missing keystore, a wrong password, a
key that does not match its certificate, or a certificate whose key usage
forbids signing fails the send with "Failed to sign email PDF attachment"
rather than delivering an unsigned file. Every attachment on the email is
signed, so every attachment must be a PDF. Send a non-PHI test message after
enabling it or rotating the keystore, and open the received PDF to confirm the
signature panel.

Two situations stop sends once signing is on, and both show in **Admin > Manage
Emails** as "Failed to sign email PDF attachment", with the cause in the server
log:

- **The certificate expires, or is not yet valid.** Validity is checked on
  every send, so from the expiry date until the keystore is replaced every
  email with an attachment fails. That includes every encrypted-message email,
  because the message itself travels as a signed PDF. Track the expiry date and
  rotate ahead of it.
- **A source PDF needs a password to open**, most often an uploaded document
  that a third party protected with its own password. CARLOS cannot sign what
  it cannot open, so that document cannot be emailed while signing is enabled.
  A document that is only restricted (it opens without a password but limits
  printing or editing) is signed normally.

## Running More Than One Application Server

The supported install is one application server
([docs/install-deb.md](../install-deb.md)). If a deployment runs several
CARLOS servers behind a load balancer, it **must route each login session to
the same server for the whole session** ("sticky sessions", or session
affinity). The email compose flow requires it:

- Opening a compose window, or preparing a resend from **Manage Emails**,
  creates a one-time token. The generated PDF passphrase, its clue and the
  prepared attachment list are kept in that server's memory under the token.
  They are not stored in the HTTP session or the database. An entry lasts at
  most 30 minutes, with at most 8 per login session and 1,024 per server.
- The attachment PDFs prepared for that window are written to that server's
  own temporary directory.
- An attachment preview link is valid on that server only, for two minutes.

If submission of a prepared email or prepared resend reaches a different
server, that server cannot resolve its submission token. The send is refused
before transport with "This email compose window has expired or is no longer
valid. Please reopen the email compose window and try again."

A preview request that reaches a server without its preview capability returns
HTTP 403. A failed preview does not establish whether a separate send was
attempted or accepted. Opening a new resend from **Manage Emails** creates fresh
state on the receiving server, provided the authenticated session and source
documents are available. Submitting that prepared resend still requires the
same server.

To recover an unusable compose, return to the eForm and choose **Email** again,
or open a new resend from **Manage Emails**. Refreshing or reopening the old
compose URL does not recreate its prepared state. A restart or failover loses
the previous server's prepared state; HTTP-session replication does not
preserve it.

Carrying it between servers would need a shared, short-lived store that keeps
what the current design guarantees:
- a token works once;
- entries expire quickly;
- storage is bounded;
- no passphrase or attachment is ever in the HTTP session;
- nothing sensitive appears in logs or error messages.

CARLOS does not provide such a store, so sticky routing is the supported
configuration ([issue #3225](https://github.com/carlos-emr/carlos/issues/3225)).

## Monitoring and Operations

Monitor `EmailLog` rows for `FAILED` status. Use **Admin > Manage Emails** to
search by date range, patient, sender, and status. The same flow supports
marking failed rows as resolved and preparing a resend.

Recommended operating checks:

- Review failed email rows after sender configuration changes and during normal
  clinic operations.
- Investigate repeated failures before retrying patient communications.
- Test with non-PHI messages first whenever sender credentials, sender domains,
  relay/API settings, or DNS records change.
- Confirm the selected sender account is still active before using the compose
  flow for real patients.
- Review resend attempts carefully. Attachment PDFs are regenerated for resend,
  and failures can mean source documents are no longer renderable or accessible.

Repeated failures usually point to one of these causes:

- SMTP relay or API outage.
- Bad credentials, revoked API key, or disabled sender account.
- Missing or broken SPF, DKIM, or DMARC authentication.
- Rejected recipient address or patient demographic email typo.
- Attachment size limits or provider content rejection.
- Local development environment has no localhost SMTP capture service.
- PDF generation or attachment rendering failure before send.

## Outbound Email Archive at Rest

Every email sent to a patient is kept permanently as an exact copy in the outbound
email archive, attachments included. CARLOS encrypts each copy before writing it to
the document store (#3448), with keys from its own archive keyring file, separate from
`encryption.util.secret.key`. Full details, including the stored format, rotation and
failure codes, are in
[Encryption at rest](../outbound-email-archive.md#encryption-at-rest-3448).

**Deployment prerequisite: encrypted volumes.** CARLOS's encryption does not replace
disk encryption. Put the document store (`DOCUMENT_DIR`), the database data
directory, the archive keyring and the backup repository on encrypted volumes
(LUKS/dm-crypt or the cloud provider's volume encryption) before the archive holds
real patient email, and keep their owner-only permissions. Disk encryption covers a
stolen disk or snapshot; the archive keyring also covers a copy of the document
store restored somewhere else without the server configuration.

**The keyring.** CARLOS creates it on first start, owner-only, at the path in
`CARLOS_OUTBOUND_EMAIL_ARCHIVE_KEYRING_FILE`, else `email.archive.keyring.file`, else
`<context>-outbound-email-archive.keyring` (usually `carlos-outbound-email-archive.keyring`)
in the CARLOS user's home directory. A Debian install keeps it in
`/etc/carlos-emr/archive-keyring/`, which the nightly `carlos-emr-backup` already
includes. Anywhere else, add it to the configuration backup yourself.

- **Back it up with the server configuration**, keep a copy off the server, and back
  it up again after every key rotation. Without it the archive cannot be read.
- **Restore it before starting CARLOS.** If it is missing while encrypted archives
  exist, or CARLOS cannot rule that out, CARLOS refuses to start and logs one ERROR
  that names the fix: restore the keyring file from backup.
  `email.archive.keyring.acknowledge_loss=true` gets past that only when the keyring
  is lost for good, and leaves those archived emails unreadable. CARLOS also refuses to start
  when the keyring holds the right key numbers but cannot decrypt the newest archived
  emails: it is a different keyring, and the right one must be restored.
- **Several servers:** create the keyring on one server and copy it to the others
  before they first start; two servers starting without one would each make their own.
- **Rotate** with `email.archive.keyring.rotate_to=<next key number>` and a restart.
  Old keys are kept, so older archived emails stay readable. Rotation does not
  re-encrypt what is already archived.

**Capacity.** The archive only grows: each send keeps the full message and its
attachments (up to 50 MiB), and encrypted copies do not compress or de-duplicate
in backups. Estimate emails per day x average size x 365 per year plus backup copies,
and alert on the document-store volume and backup repository before they fill. The
[capacity note](../outbound-email-archive.md#capacity-and-growth-operational-note)
has the measuring query.

## Safety Notes

Do not put PHI in the email subject. The subject is normal email header content
and is not encrypted by this workflow.

Email body and subject are normal email content unless the message is routed
into the encrypted PDF workflow. The compose screen supports putting sensitive
message text into a PDF attachment and password-protecting attachments, but that
is not the same as true end-to-end encrypted email.

Password-protected PDFs reduce exposure for attachments or message PDFs, but the
password clue and surrounding email body remain normal email content. Choose
subjects, body text, and password clues accordingly.

## Known Gaps and Related Work

- Investigation: [PR #3096](https://github.com/carlos-emr/carlos/pull/3096).
- Compose layout fix: [PR #3100](https://github.com/carlos-emr/carlos/pull/3100).
- Rich Text Letter logout-script injection fix:
  [PR #3101](https://github.com/carlos-emr/carlos/pull/3101).
- Consent enforcement:
  [issue #3110](https://github.com/carlos-emr/carlos/issues/3110) /
  [PR #3128](https://github.com/carlos-emr/carlos/pull/3128).
- GET/HEAD send rejection:
  [issue #3111](https://github.com/carlos-emr/carlos/issues/3111) /
  [PR #3116](https://github.com/carlos-emr/carlos/pull/3116).
- Email transport secrets and PDF password exposure:
  [issue #3112](https://github.com/carlos-emr/carlos/issues/3112) /
  [PR #3130](https://github.com/carlos-emr/carlos/pull/3130).
- Temp PDF cleanup:
  [issue #3114](https://github.com/carlos-emr/carlos/issues/3114).
- Single message field:
  [issue #3118](https://github.com/carlos-emr/carlos/issues/3118) /
  [PR #3127](https://github.com/carlos-emr/carlos/pull/3127).
- Random PDF passphrases:
  [issue #3134](https://github.com/carlos-emr/carlos/issues/3134) /
  [PR #3135](https://github.com/carlos-emr/carlos/pull/3135).
- Dev mail capture:
  [PR #3097](https://github.com/carlos-emr/carlos/pull/3097).
- Outbound email archive foundation:
  [PR #3138](https://github.com/carlos-emr/carlos/pull/3138).
- Outbound email archive encryption at rest:
  [issue #3448](https://github.com/carlos-emr/carlos/issues/3448). Archives written
  before it stay plaintext until a re-encryption job exists.
