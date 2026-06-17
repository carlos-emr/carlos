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
| CARLOS local/test | `https://<host>/<context>` |  |  |  |
| OSCAR | Deferred |  |  | OSCAR testing is blocked separately. |

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

## Result Log

Use sanitized identifiers only. Link to screenshots, HTTP transcripts, or client
collections only if they do not contain PHI or secrets.

| Date | Area | Endpoint or operation | Request type | Expected | Actual | Status | Evidence | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-06-17 | Source inspection | REST and SOAP configuration | Repository review | API surfaces identified from source | `/ws/rs`, `/ws/oauth`, `/ws/services`, SOAP `/ws/*`, and OLIS WSDL documented | Complete | `spring_ws.xml`, `applicationContextREST.xml`, `service.wsdl` | No live environment calls recorded in this entry. |
|  | REST |  |  |  |  |  |  |  |
|  | SOAP |  |  |  |  |  |  |  |

## Minimum Smoke Pass

A CARLOS API smoke pass is complete when these items are recorded in the result
log:

- One unauthenticated REST rejection.
- One authenticated `/ws/rs` read.
- One OAuth token flow or documented OAuth environment blocker.
- One OAuth-protected `/ws/services` rejection without credentials.
- One OAuth-protected `/ws/services` success, if OAuth credentials are available.
- One SOAP WSDL load.
- One SOAP auth rejection.
- One SOAP success against a safe read-only operation, if test credentials are
  available.
- One malformed REST request and one malformed SOAP request with controlled
  errors.
- Confirmation that no response, screenshot, or log excerpt stored for testing
  contains PHI or secrets.
