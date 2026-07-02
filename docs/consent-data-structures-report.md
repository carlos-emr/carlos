# CARLOS Consent Data Structures: Practical Short Version

Date: 2026-06-02

This is the minimum you need to understand before adding more consent types or building the consent UI further.

## 1. There Are Two Main Consent Tables

CARLOS uses:

```text
consentType = what kinds of consent exist
Consent     = what one patient answered for one consent type
```

Example:

```text
consentType says:
"Email consent exists."

Consent says:
"Patient 20 opted in to Email consent."
```

## 2. `consentType` Means "The Consent Menu"

This table defines the options that can show in the UI.

Important fields:

| Field | What it means |
|---|---|
| `id` | The database id. |
| `type` | The code name. Example: `electronic_communication_consent`. |
| `name` | The display name. Example: `Email consent`. |
| `description` | The explanation shown to users. |
| `active` | `1` means show/use it. `0` means inactive. |

Current useful row:

```text
id: 2
type: electronic_communication_consent
name: Email consent
active: 1
```

To add a basic consent type today, add a new active row to `consentType`.

## 3. `Consent` Means "The Patient's Answer"

This table stores a patient's answer for a consent type.

Important fields:

| Field | What it means |
|---|---|
| `demographic_no` | The patient id. |
| `consent_type_id` | Links to `consentType.id`. |
| `optout` | The actual yes/no answer. |
| `deleted` | Whether the answer was reset/cleared. |
| `last_entered_by` | Who last changed it. |
| `consent_date` | When the patient opted in. |
| `optout_date` | When the patient opted out. |
| `edit_date` | Last change date/time. |

## 4. How CARLOS Decides Consent Status

There is no real `status` field today.

CARLOS mainly uses:

```text
optout
deleted
```

Meaning:

| Data state | Meaning |
|---|---|
| No `Consent` row | No answer / unknown. |
| `deleted = 1` | Reset/cleared. Ignore this answer. |
| `deleted = 0`, `optout = 0` | Patient opted in. |
| `deleted = 0`, `optout = 1` | Patient opted out. |

So:

```text
optout = 0 means Opt In
optout = 1 means Opt Out
deleted = 1 means Reset/Cleared
```

## 5. What The Current Demographic UI Sends

The UI uses `consentType.type` as the form field name.

Example:

```text
electronic_communication_consent
```

Radio values:

| UI choice | Submitted value | Saved result |
|---|---:|---|
| Opt In | `0` | `optout = false` |
| Opt Out | `1` | `optout = true` |

Reset/clear sends:

```text
deleteConsent_<type> = 1
```

That sets:

```text
deleted = true
```

So `Reset answer` is the right label. It does not hard-delete the row. It marks it ignored.

## 6. Two Config Flags Must Be On

For the consent section to show:

```text
privateConsentEnabled=true
USE_NEW_PATIENT_CONSENT_MODULE=true
```

`USE_NEW_PATIENT_CONSENT_MODULE` enables the dynamic consent system.

`privateConsentEnabled` controls whether the demographic consent area appears.

## 7. Older Consent Fields Are Separate

These older fields are not part of the new consent table:

```text
privacyConsent
informedConsent
usSigned
given_consent
```

They live in `demographicExt`, which is a general key/value table.

Do not build new consent-type UI on those old fields.

## 8. Consent Documents Do Not Really Exist Yet

CARLOS can store general patient documents.

It can classify a document as:

```text
Other Letter / Consent from Patient
```

But there is no direct link between:

```text
a document
and
a specific consent type
```

There is no current `ConsentDocument` table.

So the mock's "Consent documents" section needs new backend structure.

## 9. What The Current Data Model Cannot Store

The current consent tables cannot cleanly store:

| UI need | Current support |
|---|---|
| Note beside a consent | Missing |
| Needs review | Missing |
| Reviewed by / reviewed date | Missing |
| Consent expiry | Missing |
| Required document flag | Missing |
| Document linked to consent type | Missing |

## 10. What To Build Next

To make the mock real, add three things.

### A. Add fields to `Consent`

Recommended:

```text
status
note
reviewed_by
reviewed_date
expiry_date
```

This supports:

```text
Not set
Consented
Opted out
Needs review
Expired
```

### B. Add fields to `consentType`

Recommended:

```text
requiresDocument
sortOrder
defaultStatus
```

This supports:

```text
document required
display order
new rows start as Not set or Needs review
```

### C. Add a new `ConsentDocument` table

Recommended fields:

```text
id
demographic_no
consent_type_id
consent_id
document_no
status
source
note
created_by
created_date
deleted
```

This lets CARLOS say:

```text
This PDF belongs to this patient's SMS consent.
```

## 11. Main Risk

The database does not currently guarantee one active consent answer per patient/type.

The code assumes this:

```text
one patient + one consent type = one current answer
```

But the database does not enforce it.

If expanding this module, add a constraint or application guard so duplicate current consent rows cannot happen.

## 12. Files That Matter

Most important files:

```text
src/main/java/io/github/carlos_emr/carlos/commn/model/Consent.java
src/main/java/io/github/carlos_emr/carlos/commn/model/ConsentType.java
src/main/java/io/github/carlos_emr/carlos/managers/PatientConsentManagerImpl.java
src/main/java/io/github/carlos_emr/carlos/demographic/pageUtil/DemographicUpdate2Action.java
src/main/webapp/WEB-INF/jsp/demographic/edit-form-clinical.jsp
database/mysql/oscarinit.sql
```

## Bottom Line

For simple new consent types:

```text
Add active rows to consentType.
CARLOS saves patient answers in Consent.
```

For the improved UI mock:

```text
Add status/note fields to Consent.
Add document metadata to consentType.
Add a new ConsentDocument table.
```

