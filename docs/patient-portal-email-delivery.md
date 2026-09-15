# Encrypted email passwords in the patient Portal

When `patient_portal.email.enabled=true`, CARLOS requests a new password from the Portal for each encrypted outbound email. It encrypts the message/PDF attachments with that password, sends through the existing email sender, and publishes the password after the sender reports acceptance. The patient signs into the Portal with MFA and opens **Email passwords**, using the **Email <outbox ID>** reference from the email notice.

This is opt-in and defaults to `false`. Configure the existing pinned HTTPS connection and Ed25519 staff assertions first; see [client security](patient-portal-client-security.md). An enabled but unavailable or misconfigured Portal blocks the encrypted send. It never falls back to a demographic-derived password. Unencrypted mail and deployments with this option disabled retain their existing behavior.

The existing email-consent gate still runs before any password creation or email send. This change does not replace consent review or transport configuration. The email-consent and archive work remain separate dependencies for production rollout. In particular, preserve #3671's explicit-consent handling when combining the branches.

The sender needs patient-specific `_email` write, `_demographic` read, `_portal.account` read and `_portal.secret` read/write permissions. A usable, active Portal account is required; disabled, locked and password-reset-required accounts block sending. CARLOS requires exactly one recipient drawn from the selected patient's current demographic email addresses. Portal ownership uses the configured clinic ID and selected demographic number, not a submitted email address. Account creation/contact verification must already associate that Portal account with the correct person.

## Durable lifecycle and recovery

The `V1.0.26` migration adds lifecycle metadata to `emailLog`. It stores an opaque source reference, secret ID, original Portal origin/clinic and state, never the generated password. The generated password exists in the encryption step and is cleared before constructing the mail sender. It is not put in the email body, password clue, outbox password column or chart note.

| State | Meaning | Recovery |
| --- | --- | --- |
| PREPARING | Intent was saved before requesting a pending password | Cancel the unsent operation using its stored, idempotent reference |
| READY | Password exists; email sending has not started | Revoke the unsent password |
| SENDING | Sending started; acceptance is not durably confirmed | Check the provider's delivery record; explicitly confirm accepted or not accepted |
| SENT | Provider accepted; Portal publication may still be pending | Retry publication only |
| PUBLISHED | Provider accepted and password publication succeeded | Complete |
| REVOKE_PENDING | Unsent password still needs revocation | Retry revocation only |
| REVOKED | Unsent password was revoked | A new email can be composed if needed |

The composer shows recovery instead of a resend prompt when an operation remains unresolved. Opening an unresolved outbox item also goes to recovery. Staff can revisit `/email/portalDelivery?emailLogId=<id>` after restarting CARLOS. GET only displays the state; every recovery decision requires a CSRF-protected POST and fresh patient-scoped authorization. Recovery never sends an email. Conflicting state changes use compare-and-set database writes, and the original Portal origin/clinic must still match.

A timeout while sending is not proof of failure: SMTP/API acceptance can precede a lost response. Such a password stays pending instead of being revoked. Staff must check the mail provider's record before publishing or revoking it. “Accepted” is the sender's success result; it does not prove final inbox delivery, which may still bounce later. This integration does not introduce automatic email retries or claim exactly-once email delivery. A deliberately composed new email is a new operation with a new password.

If the send succeeds but publication fails, the outbox retains SUCCESS for the email and separately shows the pending password update. Retrying publication cannot send a duplicate email. A process failure between acceptance and its durable state update appears as SENDING and requires reconciliation.

## Integration with the email work in progress

`EmailManager` adds one portal-specific branch after consent checks and supplies encryption/send callbacks to `PortalEmailDelivery`. Transport credentials, SMTP/SendGrid implementation and archive formats remain owned by the email implementation. When integrating the archive PRs, keep the entire prepare/archive/send operation inside the send callback and return only after the provider accepts the prepared artifact; do not publish a password merely because an archive was written.

The callback is conservative: an exception once that callback starts is treated as an uncertain send. If future transports expose a reliable “definitely not accepted” result, that can use the pre-send cancellation path explicitly. Do not infer it from arbitrary exception messages.
