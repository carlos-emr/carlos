# Provider-to-Patient Email Investigation

## Purpose

Investigate how provider-to-patient email functionality works in CARLOS, including available user flows, backend routing, configuration requirements, and expected behavior in local development.

## Scope

- Identify UI entry points for provider-to-patient email.
- Trace backend actions and services used to compose and send email.
- Confirm required permissions, patient data, consent, and sender configuration.
- Verify whether email is available as a direct chart workflow, eForm workflow, or both.
- Document local development behavior and expected setup requirements.
- Capture gaps, confusing user experience, and failure modes for follow-up work.

## Validation Plan

- Test provider workflow from a patient chart.
- Test eForm email workflow.
- Confirm behavior when the patient has no email address.
- Confirm behavior when consent is missing or not explicit opt-in.
- Confirm behavior when no sender account is configured.
- Confirm behavior when SMTP or API delivery is unavailable.
- Review email logs and status handling after attempted sends.

## Findings

Detailed findings will be added as pull request comments as the workflow is traced and verified.
