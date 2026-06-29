# CARLOS API Testing Checklist

This document records the CARLOS SOAP and REST API testing scope and provides a
place to document manual test results. Do not paste real PHI, secrets, OAuth
tokens, passwords, signed payloads, or production patient identifiers into this
file.

## Source References

The current API surface was identified from these files:

- `src/main/webapp/WEB-INF/web.xml`: CXF servlet is mounted at `/ws/*`.
- `src/main/resources/spring_ws.xml`: SOAP services and session-authenticated
  REST server at `/ws/rs`.
- `src/main/resources/applicationContextREST.xml`: OAuth 1.0a endpoints at
  `/ws/oauth` and OAuth-protected REST services at `/ws/services`.
- `src/main/axis2/service.wsdl`: OLIS SOAP `OLISRequest` contract.

## Environments

| Environment | Base URL | Tester | Date | Notes |
| --- | --- | --- | --- | --- |
| CARLOS local/test | `https://<host>/<context>` |  |  | Primary CARLOS target. |
| OSCAR 19 Galaxy VM | `https://<host>/oscar` |  |  | Comparison target when available; use demo/test data only. |

## REST Surfaces

| Surface | Auth model | Purpose | Examples to verify |
| --- | --- | --- | --- |
| `/ws/rs` | CARLOS web session / REST auth interceptor | Legacy UI-facing REST resources | `persona`, `jobs`, `forms`, `reporting`, `tickler`, `eform`, `eforms` |
| `/ws/oauth` | OAuth 1.0a token flow | Request token, user authorization, access token exchange | request token, `/authorize`, `/token` |
| `/ws/services` | OAuth 1.0a interceptor | OAuth-protected data APIs | `oauth/info`, demographics, schedule, notes, labs, documents, prescriptions |

## REST Checklist

| ID | Check | Expected result | Actual result | Status | Notes |
| --- | --- | --- | --- | --- | --- |
| REST-01 | Load an unauthenticated `/ws/rs` read endpoint. | Request is rejected or redirected according to session auth rules. |  |  |  |
| REST-02 | Load an authenticated `/ws/rs` read endpoint using a valid CARLOS session. | HTTP 200 with valid JSON or XML. |  |  |  |
| REST-03 | Submit malformed JSON to a JSON endpoint. | Controlled 4xx error; no stack trace or PHI in response. |  |  |  |
| REST-04 | Call `/ws/services` without OAuth credentials. | Request is rejected with an auth failure. |  |  |  |
| REST-05 | Complete OAuth 1.0a request-token, authorize, and access-token flow. | Access token is issued for a valid configured consumer. |  |  |  |
| REST-06 | Call `/ws/services/oauth/info` or another low-risk read endpoint with a valid OAuth signature. | HTTP 200 with expected JSON. |  |  |  |
| REST-07 | Call a protected endpoint with an invalid signature. | Request is rejected; no sensitive detail is exposed. |  |  |  |
| REST-08 | Call a protected endpoint with a valid token but insufficient patient/provider access. | Authorization failure or empty result according to business rules. |  |  |  |
| REST-09 | Verify `Accept: application/json` responses. | `Content-Type` is JSON and body parses successfully. |  |  |  |
| REST-10 | Verify XML extension or `Accept: application/xml` where supported. | XML response is valid or unsupported media is handled cleanly. |  |  |  |
| REST-11 | Exercise one safe write operation in a test-only record. | Data persists and can be read back; failed writes do not partially save. |  |  |  |
| REST-12 | Repeat the write request if the endpoint could be retried by a client. | Duplicate behavior is understood and documented. |  |  |  |

## SOAP Surfaces

The CXF SOAP servlet is mounted under `/ws`. `spring_ws.xml` exposes these
services:

- `/ws/SystemInfoService`
- `/ws/LoginService`
- `/ws/ScheduleService`
- `/ws/BookingService`
- `/ws/ProviderService`
- `/ws/DemographicService`
- `/ws/FacilityService`
- `/ws/ProgramService`
- `/ws/AllergyService`
- `/ws/PreventionService`
- `/ws/MeasurementService`
- `/ws/DocumentService`
- `/ws/PrescriptionService`
- `/ws/LabUploadService`

Most SOAP services after login are configured with the WS-Security username
token interceptor. The OLIS WSDL in `src/main/axis2/service.wsdl` defines
`OLISRequest` with SOAPAction:

`http://www.ssha.ca/2005/HIAL/OLIS/OLISRequest`

## Appointment Integration Mapping

The external appointment integration method names map to SOAP operations on
`/ws/ScheduleService`. Names such as `addAppointment.ws` and
`updateAppointment.ws` are treated as legacy OSCAR operation labels, not
standalone CARLOS paths. In CARLOS, test them as SOAP operations published by
`/ws/ScheduleService?wsdl`.

| Integration method | Required | CARLOS SOAP operation | Test focus | Notes |
| --- | --- | --- | --- | --- |
| `post_appointment_data` | Yes | `ScheduleService.addAppointment` | Create a test-only appointment and verify it can be read back. | Mutating test; requires disposable appointment data. |
| `update_appointment` | Yes | `ScheduleService.updateAppointment` | Update editable fields on the test appointment. | Verify no unrelated fields are lost. |
| `confirm_appointment` | Yes | `ScheduleService.updateAppointment` | Update appointment status to the confirmed state. | Confirm exact status code/id in CARLOS test data before running. |
| `cancel_appointment` | Yes | `ScheduleService.updateAppointment` | Update appointment status to the cancelled state. | Confirm exact status code/id in CARLOS test data before running. |
| `here_appointment` | Yes, for kiosk | `ScheduleService.updateAppointment` | Update appointment status to the kiosk/here state. | Confirm exact status code/id in CARLOS test data before running. |
| `reminder_email_sent_appointment` | No | `ScheduleService.updateAppointment` | Update appointment notes or reminder marker used by the integration. | Optional; verify expected note format before running. |
| `reminder_sms_sent_appointment` | No | `ScheduleService.updateAppointment` | Update appointment notes or reminder marker used by the integration. | Optional; verify expected note format before running. |
| `get_day_work_schedule` | Yes | `ScheduleService.getDayWorkSchedule` | Read a provider day schedule. | Use a provider/date with known schedule data. |
| `async_get_day_work_schedule` | Yes | `ScheduleService.getDayWorkSchedule` | Same server operation as `get_day_work_schedule`. | Async behavior is client-side unless a separate endpoint is identified. |
| `get_day_work_schedule_date_only` | Yes | Derived from `getDayWorkSchedule` | Verify client-side date-only filtering against the full schedule result. | No dedicated CARLOS endpoint identified. |
| `get_providers_appointments` | Yes | `ScheduleService.getAppointmentsForProvider` | Read provider appointments for a date. | Use a provider/date with known appointments. |
| `async_get_providers_appointments` | Yes | `ScheduleService.getAppointmentsForProvider` | Same server operation as `get_providers_appointments`. | Async behavior is client-side unless a separate endpoint is identified. |
| `get_providers_appointments_date_only` | Yes | Derived from `getAppointmentsForProvider` | Verify client-side date-only filtering against the full provider appointment result. | No dedicated CARLOS endpoint identified. |
| `get_appointments_for_patient` | Yes | `ScheduleService.getAppointmentsForPatient` | Read appointments for a test patient. | Use non-production test patient data only. |
| `get_appointment_by_id` | Yes | `ScheduleService.getAppointment` | Read the test appointment by id. | Use the id returned by `addAppointment` or known fixture data. |
| `get_appointment_status_ids` | No | Unmapped | Identify whether status ids come from REST `/ws/rs/schedule/statuses`, configuration, or client constants. | No SOAP operation identified in `ScheduleWs`. |
| `get_appointment_types` | No | `ScheduleService.getAppointmentTypes` | Read appointment types. | Also available in REST as `/ws/rs/schedule/types`. |
| `get_providers` | No | Unmapped in this appointment matrix | Identify whether the integration should use SOAP `ProviderService.getProviders` or REST provider endpoints. | Not listed as a Schedule SOAP operation. |
| `get_suggestions` | No | Unmapped | Identify expected suggestion source and API contract. | No endpoint identified yet. |

Required appointment testing is complete only when the required mapped SOAP
operations are exercised against test-only appointment data, and the derived
date-only methods are verified against the full SOAP responses.

## SOAP Checklist

| ID | Check | Expected result | Actual result | Status | Notes |
| --- | --- | --- | --- | --- | --- |
| SOAP-01 | Load `?wsdl` for a representative CARLOS SOAP service. | WSDL is reachable and imports resolve. |  |  |  |
| SOAP-02 | Import the WSDL into a SOAP client. | Client generates/imports operations without schema errors. |  |  |  |
| SOAP-03 | Call `LoginService` with valid test credentials. | Successful login response or documented token/session response. |  |  |  |
| SOAP-04 | Call a secured SOAP service without WS-Security headers. | SOAP fault or auth rejection. |  |  |  |
| SOAP-05 | Call a secured SOAP service with valid WS-Security UsernameToken headers. | Successful response for permitted test data. |  |  |  |
| SOAP-06 | Call a secured SOAP service with invalid credentials. | SOAP fault or auth rejection; no stack trace or sensitive detail. |  |  |  |
| SOAP-07 | Send malformed SOAP XML. | SOAP fault or controlled 4xx/5xx response. |  |  |  |
| SOAP-08 | Verify namespace handling for request payloads. | Correct namespace accepted; incorrect namespace rejected cleanly. |  |  |  |
| SOAP-09 | Validate the OLIS `OLISRequest` WSDL contract with a SOAP client. | Operation and SOAPAction are recognized. |  |  |  |
| SOAP-10 | Send an OLIS-style request with missing `SignedData`. | Controlled validation failure. |  |  |  |
| SOAP-11 | Load `/ws/ScheduleService?wsdl` and confirm appointment operations are present. | `addAppointment`, `updateAppointment`, `getDayWorkSchedule`, `getAppointmentsForProvider`, `getAppointmentsForPatient`, `getAppointment`, and `getAppointmentTypes` are present. |  |  |  |
| SOAP-12 | Create a test-only appointment through `ScheduleService.addAppointment`. | Appointment id is returned and the appointment can be read back. |  |  | Use disposable test patient/provider/date data. |
| SOAP-13 | Update that appointment through `ScheduleService.updateAppointment`. | Editable fields persist and unrelated fields remain intact. |  |  | Covers `update_appointment`. |
| SOAP-14 | Confirm, cancel, and kiosk-here status changes through `ScheduleService.updateAppointment`. | Each required status transition persists with the expected CARLOS status id/code. |  |  | Confirm status ids/codes before running. |
| SOAP-15 | Mark optional email/SMS reminder sent behavior through `ScheduleService.updateAppointment`, if required by the integration. | Expected reminder note/marker persists without overwriting existing notes. |  |  | Optional integration behavior. |
| SOAP-16 | Read schedule data through `ScheduleService.getDayWorkSchedule`. | Provider day schedule is returned for a known provider/date. |  |  | Also validates `async_get_day_work_schedule` server operation. |
| SOAP-17 | Validate `get_day_work_schedule_date_only` derived behavior. | Date-only filtered result matches the full `getDayWorkSchedule` response for the selected date. |  |  | No dedicated endpoint identified. |
| SOAP-18 | Read provider appointments through `ScheduleService.getAppointmentsForProvider`. | Provider appointments are returned for a known provider/date. |  |  | Also validates `async_get_providers_appointments` server operation. |
| SOAP-19 | Validate `get_providers_appointments_date_only` derived behavior. | Date-only filtered result matches the full `getAppointmentsForProvider` response for the selected date. |  |  | No dedicated endpoint identified. |
| SOAP-20 | Read patient appointments through `ScheduleService.getAppointmentsForPatient`. | Test patient's appointment list is returned. |  |  | Use test patient data only. |
| SOAP-21 | Read the created appointment through `ScheduleService.getAppointment`. | Appointment details match the created/updated test appointment. |  |  | Covers `get_appointment_by_id`. |
| SOAP-22 | Read appointment types through `ScheduleService.getAppointmentTypes`. | Appointment type list is returned or an empty list is handled cleanly. |  |  | Optional integration method. |
| SOAP-23 | Resolve unmapped appointment-support methods. | `get_appointment_status_ids`, `get_providers`, and `get_suggestions` are mapped to an API or documented as unsupported. |  |  | Open mapping items. |

## Result Log

Use sanitized identifiers only. Link to screenshots, HTTP transcripts, or client
collections only if they do not contain PHI or secrets.

| Date | Area | Endpoint or operation | Request type | Expected | Actual | Status | Evidence | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-06-17 | Source inspection | REST and SOAP configuration | Repository review | API surfaces identified from source | `/ws/rs`, `/ws/oauth`, `/ws/services`, SOAP `/ws/*`, and OLIS WSDL documented | Complete | `spring_ws.xml`, `applicationContextREST.xml`, `service.wsdl` | No live environment calls recorded in this entry. |
| 2026-06-18 | CARLOS/OSCAR comparison | SOAP, REST, OAuth, and appointment integration paths | Live test/demo environments | Determine whether CARLOS APIs work and whether CARLOS/OSCAR behave the same | CARLOS SOAP worked for tested appointment flows; CARLOS REST worked for core flows with defects; CARLOS and OSCAR were compatible but not identical | Complete | PR investigation summary/comments | Keep detailed evidence sanitized and outside this checklist if it contains sensitive values. |
|  | REST |  |  |  |  |  |  |  |
|  | SOAP |  |  |  |  |  |  |  |

## Minimum Smoke Pass

A CARLOS API smoke pass is complete when these items are recorded in the result
log:

- Record an unauthenticated REST rejection.
- Record an authenticated `/ws/rs` read.
- Complete an OAuth token flow, or document the OAuth environment blocker.
- Verify an OAuth-protected `/ws/services` rejection without credentials.
- Verify an OAuth-protected `/ws/services` success, if OAuth credentials are
  available.
- Load a SOAP WSDL.
- Confirm SOAP auth rejection.
- Call a safe read-only SOAP operation successfully, if test credentials are
  available.
- Schedule SOAP WSDL confirms the required appointment operations are present.
- Required appointment integration reads are exercised:
  `getDayWorkSchedule`, `getAppointmentsForProvider`,
  `getAppointmentsForPatient`, and `getAppointment`.
- Required appointment integration writes are exercised against disposable test
  data: `addAppointment` and `updateAppointment` status/update variants.
- One malformed REST request and one malformed SOAP request with controlled
  errors.
- Confirmation that no response, screenshot, or log excerpt stored for testing
  contains PHI or secrets.
