# Patient portal staging checklist

A checklist for standing up CARLOS and the patient portal together on staging infrastructure, and
proving they work before any real patient is invited. Work through it top to bottom; each section
assumes the one before it passed.

**Scope.** This covers the CARLOS side and the connection between the two systems. The portal's
own deployment (containers, PostgreSQL roles, nginx, secrets, migrations, preflight) is documented
in the portal repository and is only summarised here:

- [`deploy/README.md`](https://github.com/carlos-emr/carlos-portal/blob/main/deploy/README.md):
  how to deploy the portal.
- [`deploy/REAL_DATA_READINESS.md`](https://github.com/carlos-emr/carlos-portal/blob/main/deploy/REAL_DATA_READINESS.md):
  the evidence record to complete before any real patient data.

**Background.** How CARLOS authenticates the portal and itself, and how invitations are delivered,
is explained in [`patient-portal-client-security.md`](patient-portal-client-security.md). The
settings are documented in `src/main/resources/carlos.properties` under *Patient Portal
integration*.

**Test data only.** Staging uses test patients with test email addresses and phone numbers that
the team controls. Nothing here authorises real patient data; that is the readiness record's job.

## 0. Decide first

These decisions are tracked in [#3674](https://github.com/carlos-emr/carlos/issues/3674). Record
the answers before configuring anything, because the TLS pin and both public URLs depend on them.

- [ ] Portal hostname patients will open (`patient_portal.public_base_url`).
- [ ] Hostname CARLOS will call for the internal API (`patient_portal.base_url`). It may be the same
      host as the patient-facing one, but its `/internal/carlos/` route must be reachable only from
      CARLOS.
- [ ] Who hosts the portal, and who owns and renews its TLS certificate. Pin rotation (section 2)
      needs that person.
- [ ] Mail relay for the portal (STARTTLS, authenticated) and for CARLOS (the clinic's sending
      account). They may be the same provider; they are configured separately.
- [ ] SMS provider for the portal's MFA codes, reached through the portal's HTTPS SMS webhook.
- [ ] The clinic ID. It must be identical on both sides: 1 to 20 ASCII letters, digits, dots,
      underscores or hyphens.
- [ ] Which CARLOS build to deploy. Until #3478 and #3856 merge, that is the head of
      `feature/3854-portal-invite-workflow`. Record the commit.

## 1. Portal

Follow the portal's `deploy/README.md`. For CARLOS to work with it, check in particular:

- [ ] `PATIENT_PORTAL_ENVIRONMENT=production`, even on staging, so its production checks apply.
      `staging` and `development` relax them.
- [ ] PostgreSQL 16 with `sslmode=verify-full`; SQLite is refused in production.
- [ ] `PATIENT_PORTAL_CLINIC_ID` matches the value decided above.
- [ ] `PATIENT_PORTAL_PUBLIC_BASE_URL` is the patient-facing `https://` address.
- [ ] SMTP uses STARTTLS and authentication; `PATIENT_PORTAL_SMTP_FROM_ADDRESS` is set.
- [ ] `PATIENT_PORTAL_SMS_WEBHOOK_URL` is `https://` and `PATIENT_PORTAL_SMS_WEBHOOK_TOKEN` is set.
- [ ] `PATIENT_PORTAL_REQUIRE_MFA` is on.
- [ ] Both processes run: the web service and the separate outbox worker, which sends the portal's
      own email and SMS.
- [ ] nginx terminates TLS, forwards to `127.0.0.1:8090`, and allows `/internal/carlos/` only from
      the CARLOS server addresses. Patient-supplied `X-CARLOS-*` headers are stripped.
- [ ] `scripts/production-deploy preflight` passes with nothing waived.

The internal API token and staff-assertion keyring are set in section 2.

## 2. Keys and trust between CARLOS and the portal

Three independent credentials connect the systems. Generate each fresh for staging, keep it in the
deployment secret manager, and never reuse staging values in production.

| Credential | CARLOS setting | Portal setting |
|---|---|---|
| Service token (32+ visible ASCII characters) | `patient_portal.service_token` | `PATIENT_PORTAL_INTERNAL_API_TOKEN` |
| Ed25519 staff-assertion key pair | `patient_portal.staff_assertion.private_key` and `.key_id` | public key under that key ID in `PATIENT_PORTAL_INTERNAL_STAFF_ASSERTION_PUBLIC_KEYRING` |
| TLS public-key pin of the certificate CARLOS sees | `patient_portal.certificate.pins` | nothing: it is the key of the certificate nginx serves |

- [ ] Service token generated and set on both sides.
- [ ] Key pair generated with the `openssl` commands in `carlos.properties`. The private key goes
      only to CARLOS; the portal gets the raw public key in its keyring JSON,
      `{"<key-id>":"<public-key>"}`.
- [ ] Pin computed from the **verified certificate file** obtained from whoever runs nginx, using
      the command in `carlos.properties`. Do not copy a pin from a live connection or from a
      mismatch error: that trusts whatever answered.
- [ ] The portal's certificate also validates normally: the CARLOS JVM truststore trusts its issuer,
      and the hostname matches. A pin is checked in addition to normal validation, not instead of
      it.

## 3. CARLOS

### Settings

Set these in the deployment's override properties, not in the committed `carlos.properties`:

- [ ] `patient_portal.enabled=true`: the master switch, which is off by default (added in #3934). The
      portal stays off, whatever else is set, until this is exactly `true`; any other value except
      `false` or blank is a configuration error. Setting it back to `false` later switches the
      portal off without removing the credentials below.
- [ ] `patient_portal.base_url`: the internal API origin, `https://`, with no credentials, query or
      fragment.
- [ ] `patient_portal.clinic_id`: the same value as the portal's.
- [ ] `patient_portal.service_token`, `patient_portal.staff_assertion.private_key`,
      `patient_portal.staff_assertion.key_id`, `patient_portal.certificate.pins`: from section 2.
- [ ] `patient_portal.public_base_url`: the patient-facing `https://` address. Invitation emails link
      to `<public_base_url>/auth/activate`.
- [ ] `patient_portal.invite.sender_email`: the sender address of an **active** CARLOS email
      account.
- [ ] Timeouts left at their defaults unless there is a measured reason
      (`patient_portal.timeout.*`; `request.ms` must stay below 60000).
- [ ] CARLOS restarted: settings and the portal client are read once.

### Database

- [ ] Flyway applied `V1.0.30` (portal security objects) and `V1.0.31` (invitation delivery table
      and default grants).

### Email

- [ ] An active sender account for `patient_portal.invite.sender_email` exists in `emailConfig`,
      using a real relay with SPF, DKIM and DMARC for its domain. See
      [`email/provider-to-patient-email-operations.md`](email/provider-to-patient-email-operations.md).
- [ ] Email consent tracking is configured: the `email_communication` user property names an active
      consent type. Without it every invitation is refused ("consent is not configured").
- [ ] The outbound email archive directory is writable. Every sent email is archived as a patient
      document, and an archive failure stops the send.

### Who can do what

`V1.0.31` gives the `doctor` role `_portal.invite` (full) and `_portal.account` (read), and leaves
`_portal.account.unlock` with `admin`. Sending also needs `_email` write and `_edoc` write.

- [ ] A staging user whose only role is `doctor` exists, to prove the default grants are enough.
- [ ] Decide whether front-desk roles get `_portal.invite`. Without `_email` they can see and revoke
      invitations but not send one. Record the decision.
- [ ] A user with `_portal.account` write and `_portal.account.unlock` exists for the account tests
      (`admin` has both by default; `doctor` can only read account status).

## 4. Verify end to end

Run this with a test patient whose chart has an email address you can read, a complete date of
birth, a health card number, and email consent recorded as opt-in. For each step, record the time
and the result.

**Connection**

- [ ] Open the test patient's record: **Patient portal** appears in the left column. It is absent
      for a user without portal rights, and when the integration is not configured.
- [ ] Open it: it loads in the same window with the patient header, left navigation and panels, and
      shows no portal error.
- [ ] Temporarily set a wrong pin and restart CARLOS: the page reports a portal failure and the
      portal receives nothing. Restore the pin.
- [ ] Set `patient_portal.enabled=false` and restart CARLOS: the **Patient portal** entry disappears
      from the record, and the rest of CARLOS works as before. Set it back to `true` and restart.

**Invitation**

- [ ] As the doctor-only user, **Invite by email**. The result beside the button reads *Sent*; the
      Invitations table shows it *Pending*; Invitation deliveries shows *Sent*.
- [ ] The email arrives through the clinic's relay, links to `<public_base_url>/auth/activate`, and
      carries the code as text.
- [ ] **Resend** issues a new code. The first invitation becomes *Replaced*, and its code is refused
      on the portal's activation page.
- [ ] A patient with consent recorded as opt-out is refused with the consent message, and no
      invitation is created on the portal.

**Patient**

- [ ] With the new code, the test patient activates an account on the portal's own pages.
- [ ] Signing in sends an MFA code by SMS through the real provider; entering it reaches the
      dashboard.
- [ ] Back in CARLOS the account shows *Active* and the invitation *Accepted*; the invite form is
      hidden.

**Account controls**

- [ ] As the account user, disable with a reason: the patient is signed out and cannot sign in. Enable: they can again,
      after resetting their password.
- [ ] Lock the account with repeated wrong passwords, then **Unlock** as the unlock user: the
      patient must reset their password at next sign-in.

**The code stays out of CARLOS**

- [ ] The stored invitation email in the outbox no longer holds the code.
- [ ] The chart note names the address the invitation went to, never the code.
- [ ] The archived copy is typed `SMTP_RFC822_REDACTED` (or `API_PAYLOAD_REDACTED`) and shows
      `[redacted]` where the code was.
- [ ] Neither the CARLOS nor the portal logs contain the code.

## 5. Failure drills

- [ ] Stop the portal: the CARLOS patient record still opens; the Patient portal page reports the
      failure; nothing else in CARLOS is affected. Start it again.
- [ ] Stop the CARLOS mail relay and invite: the delivery shows the mail server refused the email,
      or that it may not have been sent, with what to do next. After the relay returns, a resend
      works and the patient receives only the new code.
- [ ] Leave an attempt unfinished (for example, stop the portal between prepare and send). After 15
      minutes, the Invitation deliveries panel offers the matching decision, and resolving it
      behaves as described in `patient-portal-client-security.md`.
- [ ] Rotate the staff-assertion key with old and new keys overlapping, as in `carlos.properties`,
      and confirm no call fails during the change.

## 6. Sign-off

- [ ] Sections 0 to 5 complete, with the CARLOS commit, the portal image digest, and the evidence
      for each step recorded.
- [ ] Every staging secret listed for destruction; production gets fresh ones.
- [ ] The portal's `REAL_DATA_READINESS.md` record started for the clinic. Real patients are
      invited only after it is signed.

## Known differences from the repository's own testing

The workflow has been tested in the repository against a real portal, but not on staging
infrastructure. The live run on 2026-09-24 (results on
[#3856](https://github.com/carlos-emr/carlos/pull/3856)) used SQLite, the portal's `staging` mode,
a local certificate authority, local mail and SMS servers, and no reverse proxy. Sections 1, 2 and
4 exist to close exactly those gaps.
