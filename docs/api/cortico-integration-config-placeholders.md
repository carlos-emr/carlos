---
id: cortico-integration-config-placeholders
title: Cortico/Juno Integration Config Placeholders
---

# Cortico/Juno Integration Config Placeholders

This document describes the clinic-specific configuration placeholders reserved
for future Cortico/Juno-style integration adapter workflows. The placeholder
keys live in `src/main/resources/carlos.properties` under the
`integration.cortico.*` namespace.

These keys are **placeholders only**. No CARLOS code reads them yet, so
populating them or leaving them blank does not change any existing appointment,
demographic, provider, or document API behavior. Their purpose is to make future
adapter work explicit and configurable instead of relying on hardcoded
assumptions.

Each value is clinic-specific and must be supplied **per environment** — in the
per-environment override file, never in the committed default
`carlos.properties` — before a future adapter that consumes the value is wired
and separately tested. All keys default to empty.

The override file is the one named by the `carlos_override_properties` JVM system
property (`-Dcarlos_override_properties=/path/to/file`), which `CarlosProperties`
reads after the bundled defaults. The Debian package and the devcontainer both
point it at a file that is also called `carlos.properties`; that deployment file,
not the one in `src/main/resources`, is where real values go.

> **Do not commit** clinic-specific identifiers, status codes, provider/location
> numbers, OAuth/SOAP credentials, or any PHI into `carlos.properties`. Supply
> real values only in the per-environment override config.

## Placeholder keys

| Config key | Meaning | Required for future adapter? | Safe default |
| --- | --- | --- | --- |
| `integration.cortico.appointment.status.confirmed` | Status code written for a confirmed appointment via `ScheduleService.updateAppointment`. | Yes, for `confirm_appointment`. | Empty (no default assumed). |
| `integration.cortico.appointment.status.cancelled` | Status code written for a cancelled appointment. Commonly `C`. | Yes, for `cancel_appointment`. | Empty (no default assumed). |
| `integration.cortico.appointment.status.arrived` | Status code written for an arrived/here appointment. Commonly `H`. | Yes, for `here_appointment`. | Empty (no default assumed). |
| `integration.cortico.appointment.reminder.email_note_marker` | Marker text appended to `AppointmentTransfer.notes` when a reminder email is sent. Appended, not replaced. | Yes, for `reminder_email_sent_appointment`. | Empty (no marker appended). |
| `integration.cortico.appointment.reminder.sms_note_marker` | Marker text appended to `AppointmentTransfer.notes` when a reminder SMS is sent. Appended, not replaced. | Yes, for `reminder_sms_sent_appointment`. | Empty (no marker appended). |
| `integration.cortico.default_location` | Default location/clinic identifier when the inbound request omits one. | Optional. | Empty (no default assumed). |
| `integration.cortico.default_provider` | Default provider number when the inbound request omits one. | Optional. | Empty (no default assumed). |
| `integration.cortico.default_appointment_type` | Default appointment type when the inbound request omits one. | Optional. | Empty (no default assumed). |
| `integration.cortico.demographic.search.phn_field` | Demographic search field carrying the PHN/HIN for `searchDemographicsByAttributes` (the native field is `hin`). | Optional. | Empty (adapter decides explicitly). |
| `integration.cortico.document.default_type` | Default document type for adapter document-upload workflows. | Optional. | Empty (no default assumed). |

## Related documentation

The placeholder-to-contract mapping is inlined in the table above, so this
document is self-contained. For the full Cortico/Juno label-to-contract
compatibility matrix, see
[Cortico CARLOS API Compatibility](cortico-carlos-compatibility.md). Where the
matrix refers to clinic-specific status codes, reminder note markers, default
provider/location/appointment type, demographic search field, or default
document type, the configurable values are the `integration.cortico.*` keys
documented above.

Every key is declared with an empty value, so a future consumer must treat a
blank value as unset (for example `StringUtils.isBlank`) rather than rely on a
`getProperty(key, fallback)` default, which an empty declaration never reaches.
