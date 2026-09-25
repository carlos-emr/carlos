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
  requires `_email`, loads consent and recipients, loads active sender accounts,
  prepares attachments, and renders the compose screen.
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

The current Configure Email admin page documents the `emailConfig` fields and
sample SMTP/API payloads, but sender records are still managed as deployment
configuration. Confirm the selected sender account is active before using real
patient communications.

## Credential Encryption Key

Sender accounts that authenticate (an SMTP password, or a provider API key such
as SendGrid's) store that secret in `emailConfig.configDetails`. CARLOS encrypts
it at rest with the application key `encryption.util.secret.key`, the same key
fax credentials use. A plaintext row inserted by hand is encrypted the first
time it is used to send. An unauthenticated `LOCAL` relay never uses a secret
and is never affected; a secret left on a `LOCAL` row is reported once so it can
be removed.

**Where the key comes from.** On its first start, CARLOS generates a key and
saves it to the override properties file if none is set, and it refuses to start
with an invalid one. A running server therefore always has a key. That makes the
key itself the thing to protect:

- **Back it up** with the rest of the server's configuration. Everything
  encrypted with it, email and fax credentials alike, can only be decrypted with
  that exact key.
- **Never replace it** on a server that has been running. A new key does not
  decrypt what the old one encrypted.
- **If it is lost**, CARLOS starts with a newly generated key and the old
  credentials stop working. Restore the original key and restart. If it cannot
  be recovered, re-enter each account's password or API key.

**What a send does** before it opens any connection:

| Sender account | Result |
|---|---|
| Encrypted credentials that decrypt with the current key | Sent. |
| Encrypted credentials that do **not** decrypt (the key was changed or regenerated) | Refused. The email log records `FAILED`: "Email sender account credentials cannot be read with the server's current encryption key. Contact your administrator." An ERROR names the account id and says to restore the original key. |
| Plaintext credentials, key available | Encrypted at rest, then sent. |
| Plaintext credentials, no key available | Only possible where CARLOS runs without its Startup listener. Sent with one WARN per account, unless `email.credentials.require_encryption_key` is `true`, `yes` or `on`: then refused with "Email sender account cannot be used until the server encryption key is configured." |

Every refusal is written to the audit log as
`EmailManager.sendEmail.refusedCredentialKey`. Logs name the account id and the
setting only. They never contain the credential, the configuration JSON or the
key. At startup CARLOS logs whether the enforcement setting is on, and warns if
its value is not one it recognises.

### Rollout

1. Confirm `encryption.util.secret.key` is present in the override properties
   on every server and is backed up. Do not generate or paste in a new one on a
   server that is already running.
2. Send a non-PHI test message from each credentialed account. Each plaintext
   row is encrypted on that first send.
3. Optionally set `email.credentials.require_encryption_key=true` and restart.
   With Startup in place this changes nothing day to day. It is a guard for
   deployments or tools that run CARLOS code without Startup, and against a
   future change to key creation.

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
- Require the encryption key for credentialed email:
  [issue #3673](https://github.com/carlos-emr/carlos/issues/3673).
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
