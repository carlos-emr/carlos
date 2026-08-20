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
