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

## Email Footer

The compose screen has a **Footer** card below the message. Use it for text the
clinic wants at the bottom of every patient email: a signature, a booking link,
or a line saying the mailbox is not checked for urgent issues. **Edit footer**
opens the footer window; what is applied there is used for this email only.

- **Formatting.** The window offers bold, italic, links and line breaks, and
  nothing else. Links may only point to `https:` web addresses or `mailto:`
  email addresses. Pasted text keeps no formatting. The window previews the
  formatted version (with the clinic logo, when one is set) and the plain-text
  version. The server cleans the footer against the same allow-list (jsoup in
  `EmailFooterHtml`) before it is stored or sent; the browser's cleaning
  (DOMPurify) only keeps the preview honest.
- **Two versions in every email with a footer.** The email goes out as
  `multipart/alternative`: a plain-text version (the message, one blank line,
  then the footer's text, with each link's address written out after its text)
  and a formatted (HTML) version. Mail apps show the formatted one; simple ones
  show the text. This is the same for SMTP and SendGrid senders. The message
  itself is never formatted: nothing in it is read as formatting, and its line
  breaks and spacing are kept in both versions (some mail apps still turn web
  addresses into links, as they do in plain text). An email without a footer is
  plain text only, as before.
- **Clinic logo.** An administrator can upload one logo on **Admin > Configure
  Email** (PNG or JPEG, at most 100 KB and 600 x 200 pixels). It shows above the
  footer in the formatted version, only in emails that have a footer. CARLOS
  re-saves the picture before storing it, which drops anything else the file
  carried (camera data, comments). The logo travels inside each email (an
  inline `cid:` part), never as a link: nothing is hosted, and opening the email
  tells no web server anything. A replaced or removed logo keeps its row in
  `emailFooterLogo`; uploads and removals are in the audit log
  (`emailFooterLogo`).
- **Unencrypted, even with encryption on.** With encryption on, the message goes
  inside the password-protected PDF and the email itself carries a fixed notice.
  The footer (and logo) come after that notice, in the email itself. They are
  never put inside the PDF. Anyone who can see the email can read the footer, so
  it must not contain patient information. The compose screen says this under
  the card.
- **Not charted.** With the chart option "Chart as new note in patient's
  chart", the chart note has the message but not the footer. The "[Sent on ... by ...]" line is unchanged.
- **Kept with the email.** `emailLog.footer` stores the cleaned, formatted
  footer as it was sent, apart from the message. The outbound archive copy is the
  exact message that was sent, so it includes both versions and the logo. In
  **Admin > Manage Emails**, "Copy and Open as New Email to Patient" fills in the
  footer that was sent. A failed send that is retried from the same window keeps
  the footer. Emails sent before footers existed have no footer.
- **Limits.** 2,000 characters, counted on the plain-text version, so formatting
  does not count. The formatted footer may take at most 10,000 characters of
  HTML. A longer footer sent another way is refused, never cut. A plain-text
  footer from an eForm longer than 2,000 characters is cut to 2,000 when the
  eForm is saved, before the compose screen shows it.

The compose screen fills in the footer from the first of these that applies:

1. The footer the eForm sends, in a field named `footerEmail` (the same way an
   eForm can send `bodyEmail` for the message). A blank one counts as none. An
   eForm's footer is plain text: its line breaks are kept and nothing in it is
   read as formatting.
2. The user's own footer. Every user who can send patient email (`_email`
   write), doctor or front desk, can save one on **Preferences > My Email
   Footer** (`email/myEmailFooter`), or by ticking **Also make this my usual
   footer** under the footer on the email screen (saved when the email is sent).
   Saving it empty is not "no footer": it removes the user's own footer, so the
   clinic footer applies.
3. The clinic footer, set on **Administration > Emails > Configure Email**
   (`_admin` write).
4. Nothing: the footer starts empty.

The clinic footer, each user's own footer and the footer of one email are all
edited in the same **Edit footer** window, with the same formatting and limits.

Changing the sending account never changes the footer. An eForm that sends
automatically opens the compose screen and submits it at once, so its email also
carries the user's or clinic footer (after a clinic change, the new clinic
footer: automatic emails never wait for the user to answer the notice), and a
clinic-change notice shows only briefly on that path.

New installations, and upgraded ones whose front-desk roles have no email
permission line at all, give the `receptionist`, `Medical Secretary` and
`secretary` roles the same email rights as doctors (the maintainer's decision of
8 October 2026). A clinic that already set, narrowed or switched off email for
those roles keeps its setting; one that deleted the line entirely cannot be told
apart from one that never set it, and gets the default.

### When the clinic footer changes

Every active user is told about a changed clinic footer (the maintainer's
decision of 8 October 2026), not only those who had their own footer: a notice
on the compose screen and on their footer page. The page shows the footer they
had until then, their own or the previous clinic footer.

Whether own footers are kept is set in code by
`EmailFooterService.REPLACE_OWN_FOOTERS_ON_CLINIC_CHANGE`. It is currently
`true` (the maintainer's decision of 6 October 2026), and the Configure Email
page words its explanation to match the setting.

- **`true` (current): own footers are replaced.** Saving a changed clinic footer
  replaces every user's own footer with it; saving it empty leaves everyone with
  no footer. The notice lets each user put their previous footer back, as their
  own, or keep the clinic footer. A user who had no footer before (no clinic
  footer was set) can only keep the new one.
- **`false`: own footers are kept.** Users who follow the clinic footer get the
  new one; users with their own footer keep it, and the notice lets them switch
  to the clinic footer.

"Every active user" means every active provider record other than CARLOS's
system providers; a provider without email rights never sees the notice.
Either way, the notice stays until the user answers it or saves their footer,
and the administrator is not asked to confirm. Saving the clinic footer without
editing it changes nothing, and the page says so. If someone else changed it
after the page was opened, nothing is saved: the page shows the current footer
and keeps the administrator's text in the box to check and save again.

Footers are stored in the `property` table: the clinic footer as
`email_footer_clinic_default` with no provider, each user's as `email_footer`,
and a pending notice as `email_footer_clinic_change`. Saves are POST only,
refuse more than 2,000 characters (counted on the plain text) or 10,000
characters of formatting, and are audited (who and when, not the
text; a clinic change also records how many users were told). A clinic save
that changes the footer locks the clinic footer (once one exists) and the users'
footers it reads, row by row by key, so another save at the same moment waits. A
user's own save, restore or dismiss takes the clinic footer's lock first, so it
waits for a clinic change in progress and then sees its notice.
A second clinic save then compares against the first one's result; a user's own
save then goes through, or, if the clinic change replaced their footer, is
rolled back and the page asks them to try again. The other collisions are
handled the same way: a deadlock, or a user's footer removed between the clinic
save reading it and locking it (the clinic save is the one rolled back then).
Footers are stored as formatted HTML, cleaned against the footer's allow-list
first, so a footer saved twice unchanged compares equal.

**Web application firewall.** The footer is posted as HTML in `footerEmail`.
The CARLOS ModSecurity setup keeps its cross-site scripting rules on for this
field (`ClinicalProseWafExclusionRegressionTest` pins that). The footer's
allowed tags (`b`, `strong`, `i`, `em`, `br`, `p`, `div`, `a href="https:..."`)
do not match those rules at the default paranoia level 1. A clinic that raises
the paranoia level may see footer posts refused; check the ModSecurity audit log
before adding any exclusion.

**Upgrade note.** The footer migration widens `property.value` from
`VARCHAR(2000)` to `TEXT`, so the clinic footer and each user's own footer can
be stored there as formatted HTML. It also adds an ordinary index on
`property (name, provider_no)`, which those footer lookups use; MariaDB builds it
without blocking reads or writes. No index or key covers `property.value`.
MariaDB rebuilds the `property` table for the widening; on a typical clinic it
takes seconds, during which writes to `property` wait. Run the upgrade outside
clinic hours on a very large installation.

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

The footer and the clinic logo are always normal email content, even when the
message is encrypted. Never put patient information in a footer.

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
