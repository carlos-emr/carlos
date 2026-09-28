---
id: cortico-carlos-compatibility
title: Cortico CARLOS API Compatibility
---

# Cortico CARLOS API Compatibility Matrix

This matrix maps Cortico/Juno-style integration labels to native CARLOS and
OSCAR 19 API contracts. It is intended as a planning aid for Cortico-style
integrations and must not contain PHI, credentials, OAuth tokens, cookies, or
production patient identifiers.

## Route Rules

- Native CARLOS and OSCAR SOAP services are exposed under
  `/<context>/ws/<ServiceName>`, for example `/carlos/ws/ScheduleService` or
  `/oscar/ws/ScheduleService`.
- Literal operation paths such as `addAppointment.ws`, `updateAppointment.ws`,
  and `getDemographic.ws` are not native CARLOS/OSCAR routes. If a client calls
  those paths directly, use a shim or external proxy.
- OAuth-protected REST data APIs are exposed under
  `/<context>/ws/services/...`.
- Clinic-specific values such as provider IDs, demographic IDs, appointment
  types, status codes, reminder note markers, and OAuth/SOAP credentials must be
  configured per environment.

## Appointment SOAP

| Cortico/Juno label | Compatible? | Native CARLOS/OSCAR contract | Adapter needed |
| --- | --- | --- | --- |
| `post_appointment_data` / `addAppointment.ws` | Yes | `ScheduleService.addAppointment(AppointmentTransfer)` | Only if literal `addAppointment.ws` is called. |
| `update_appointment` / `updateAppointment.ws` | Yes | `ScheduleService.updateAppointment(AppointmentTransfer)` | Only if literal `updateAppointment.ws` is called. |
| `confirm_appointment` / `updateAppointment.ws` | Yes | `ScheduleService.updateAppointment`, with configured confirmed status | Status value is clinic-specific. |
| `cancel_appointment` / `updateAppointment.ws` | Yes | `ScheduleService.updateAppointment`, commonly `status = "C"` | Confirm clinic status configuration. |
| `here_appointment` / `updateAppointment.ws` | Yes | `ScheduleService.updateAppointment`, commonly `status = "H"` | Confirm clinic status configuration. |
| `reminder_email_sent_appointment` / `updateAppointment.ws` | Partial | `ScheduleService.updateAppointment`, updating `AppointmentTransfer.notes` | Append configured email marker to existing notes. |
| `reminder_sms_sent_appointment` / `updateAppointment.ws` | Partial | `ScheduleService.updateAppointment`, updating `AppointmentTransfer.notes` | Append configured SMS marker to existing notes. |
| `get_day_work_schedule` / `getDayWorkSchedule.ws` | Yes | `ScheduleService.getDayWorkSchedule(providerNo, date)` | Only if literal `.ws` path is called. |
| `async_get_day_work_schedule` / `getDayWorkSchedule.ws` | Yes | Same server call as `get_day_work_schedule` | Async behavior is client-side or proxy-side. |
| `get_day_work_schedule_date_only` | Partial | Derived from `getDayWorkSchedule` | Filter or reshape result on our side. |
| `get_providers_appointments` / `getAppointmentsForProvider.ws` | Yes | `ScheduleService.getAppointmentsForProvider(providerNo, date)` | Only if literal `.ws` path is called. |
| `async_get_providers_appointments` / `getAppointmentsForProvider.ws` | Yes | Same server call as `get_providers_appointments` | Async behavior is client-side or proxy-side. |
| `get_providers_appointments_date_only` | Partial | Derived from `getAppointmentsForProvider` | Filter or reshape result on our side. |
| `get_appointments_for_patient` / `getAppointmentsForPatient.ws` | Yes | `ScheduleService.getAppointmentsForPatient(demographicId, startIndex, itemsToReturn)` | Only if literal `.ws` path is called. |
| `get_appointment_by_id` / `getAppointment.ws` | Yes | `ScheduleService.getAppointment(appointmentId)` | Only if literal `.ws` path is called. |
| `get_appointment_status_ids` | Partial | REST `/ws/services/schedule/statuses`, `/ws/rs/schedule/statuses`, config, or constants | No native Schedule SOAP operation. |
| `get_appointment_types` / `getAppointmentTypes.ws` | Yes | `ScheduleService.getAppointmentTypes()` | Only if literal `.ws` path is called. |
| `get_providers` | Partial | `ProviderService.getProviders/getProviders2` or REST provider routes | Not a `ScheduleService` operation. |
| `get_suggestions` | No | No native appointment suggestion endpoint identified | Requires a Cortico transcript or new adapter contract. |

## Patient And Demographic APIs

| Cortico/Juno label | Compatible? | Native CARLOS/OSCAR contract | Adapter needed |
| --- | --- | --- | --- |
| `submit_patient_data` | Yes | REST `POST /<context>/ws/services/demographics` with OAuth | Juno SOAP `addDemographic.ws` requires shim/proxy. |
| `update_patient_data` | Yes | REST `PUT /<context>/ws/services/demographics` with OAuth | Juno SOAP `updateDemographic.ws` requires shim/proxy. |
| `get_patient_objs_by_name` | Partial | REST `GET /<context>/ws/services/demographics/{demographic_no}` for ID lookup; SOAP `DemographicService.getDemographic/getDemographic2` for ID lookup | Listed REST path is by demographic number, not name. Use `searchDemographicByName` or search APIs for real name search. |
| `get_patient_by_attributes_phn` | Yes | SOAP `DemographicService.searchDemographicsByAttributes`, with PHN/HIN in `hin` | Literal `searchDemographicsByAttributes.ws` requires shim/proxy. |
| `get_patient_by_attributes_email` | Yes | SOAP `DemographicService.searchDemographicsByAttributes`, with email in `email` | Literal `searchDemographicsByAttributes.ws` requires shim/proxy. |

## Document APIs

| Cortico/Juno label | Compatible? | Native CARLOS/OSCAR contract | Adapter needed |
| --- | --- | --- | --- |
| `upload_document` | Yes | REST `POST /<context>/ws/services/document/saveDocumentToDemographic` with OAuth | Juno REST v1 document create/link routes require shim/proxy. |

## Implementation Guidance

- Prefer native CARLOS contracts where Cortico can be configured to call them
  directly.
- Keep compatibility shims outside CARLOS or disabled by default until a
  sanitized Cortico connector transcript proves the exact literal paths and
  payload shapes.
- When adapting writes, preserve existing appointment or demographic data by
  reading the current object first where the client only supplies a partial
  update.
- Keep evidence and logs sanitized: no PHI, credentials, tokens, cookies, signed
  OAuth payloads, or production identifiers.
