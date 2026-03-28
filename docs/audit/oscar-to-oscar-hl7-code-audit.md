# Oscar-to-Oscar HL7 Code Audit

> **Status**: DEAD CODE — should be removed
>
> **Date of analysis**: March 2026
>
> **Decision**: The oscar\_to\_oscar peer-to-peer HL7 messaging subsystem was missed
> during the CAISI Integrator removal. It is a separate but related inter-EMR
> communication feature that no longer serves a purpose in CARLOS. The code should be
> removed to reduce attack surface and maintenance burden.

---

## Background

The CAISI Integrator (SOAP-based inter-EMR hub) was removed before CARLOS was
published. However, a second inter-EMR system — the **oscar\_to\_oscar** peer-to-peer
HL7 v2 messaging package — was not included in that removal scope and remains fully
wired into the codebase.

The integrator removal plan (`docs/archive/caisi-integrator-removal-plan.md`) explicitly
scoped itself to the integrator only. The oscar\_to\_oscar package was overlooked because
it is technically a different mechanism: direct encrypted HTTP POST between two
CARLOS/OSCAR instances rather than SOAP calls through a central hub. Both systems serve
the same purpose (inter-EMR data exchange) and both are dead in practice.

---

## What oscar\_to\_oscar Does

The package enables direct peer-to-peer HL7 v2 message exchange between CARLOS/OSCAR
installations. It supports three message types:

| Message Type | Class | Purpose |
|---|---|---|
| ORU\_R01 | `OruR01.java` | Unsolicited lab observation results (eData) |
| REF\_I12 | `RefI12.java` | Consultation referral requests (eReferral) |
| OMP\_O09 | `OmpO09.java` | Prescription data (used in QR codes) |

**Transport**: `SendingUtils.java` encrypts the HL7 payload (AES data encryption +
RSA key wrapping + MD5WithRSA signature) and POSTs it as a multipart form to the
remote instance's `/lab/newLabUpload.do` endpoint.

**Configuration**: Each `ProfessionalSpecialist` record has optional eData fields
(`eDataUrl`, `eDataOscarKey`, `eDataServiceKey`, `eDataServiceName`) that configure
the remote endpoint. If no specialists have `eDataUrl` set, the feature is dormant.

---

## Core Package Files (DELETE)

All files in `src/main/java/.../commn/hl7/v2/oscar_to_oscar/`:

| File | Purpose |
|---|---|
| `OruR01.java` | Constructs ORU\_R01 messages with observation data |
| `RefI12.java` | Constructs REF\_I12 referral messages |
| `OmpO09.java` | Constructs OMP\_O09 prescription messages |
| `SendingUtils.java` | Encrypted HTTP POST transport layer |
| `OscarToOscarUtils.java` | HL7 pipe parser utilities, constants |
| `DataTypeUtils.java` | HL7 data type helpers, role constants |
| `ByteArrayBody.java` | Custom HttpClient 5 multipart body (unused — `SendingUtils` uses `MultipartEntityBuilder.addBinaryBody()` directly) |

---

## Consumer Files

### Outbound — Sending (MODIFY to remove oscar\_to\_oscar usage)

| File | Usage | Impact |
|---|---|---|
| `lab/ca/all/pageUtil/OruR01Upload2Action.java` | Sends ORU\_R01 messages | Delete entire action |
| `lab/ca/all/pageUtil/SendOruR01UIBean.java` | UI bean for send form | Delete entire class |
| `encounter/oscarConsultationRequest/pageUtil/EctConsultationFormRequest2Action.java` | `doHl7Send()` sends REF\_I12 + ORU\_R01 | Remove `doHl7Send()` method and `esend` submission handling |
| `managers/ConsultationManagerImpl.java` | `doHl7Send()` business logic | Remove method and oscar\_to\_oscar imports |
| `web/PrescriptionQrCodeUIBean.java` | Uses `OmpO09` for QR code HL7 encoding | Remove oscar\_to\_oscar imports; evaluate if QR feature should remain with a simpler encoding |

### Inbound — Receiving (MODIFY or DELETE)

| File | Usage | Impact |
|---|---|---|
| `lab/ca/all/parsers/OscarToOscarHl7V2Handler.java` | Routes incoming ORU\_R01 and REF\_I12 messages | Delete entire class |
| `lab/ca/all/parsers/OscarToOscarHl7V2/OruR01Handler.java` | Parses incoming ORU\_R01 | Delete entire class |
| `lab/ca/all/parsers/OscarToOscarHl7V2/RefI12Handler.java` | Parses incoming REF\_I12 | Delete entire class |
| `lab/ca/all/parsers/OscarToOscarHl7V2/ChainnedMessageAdapter.java` | Base adapter for message handlers | Delete entire class |
| `lab/ca/all/upload/handlers/OscarToOscarHl7V2Handler.java` | Routes incoming uploads | Delete entire class |
| `lab/ca/all/upload/handlers/OscarToOscarHl7V2/AdtA09Handler.java` | Handles ADT\_A09 messages | Delete entire class |

### View Layer (MODIFY or DELETE)

| File | Usage | Impact |
|---|---|---|
| `lab/ca/all/pageUtil/ViewOruR01UIBean.java` | Displays received ORU\_R01 data | Delete entire class |
| `ui/servlet/ContentRenderingServlet.java` | Serves binary content from ORU\_R01 (`source=oruR01`) | Remove oruR01 source handling branch |
| `encounter/oscarConsultationRequest/pageUtil/EctViewRequest2Action.java` | Displays received REF\_I12 | Remove oscar\_to\_oscar import usage for parsing received referral data |

---

## JSP Files

### Delete

| JSP | Purpose |
|---|---|
| `lab/CA/ALL/sendOruR01.jsp` | UI form for sending eData (ORU\_R01) |
| `lab/CA/ALL/viewOruR01.jsp` | UI for viewing received eData |

### Modify

| JSP | Change Required |
|---|---|
| `admin/admin.jsp` (line 816) | Remove link to `sendOruR01.jsp` |
| `casemgmt/ChartNotesAjax.jsp` (lines 278, 489-495) | Remove `remoteCapableProfessionalSpecialists` check and "eSend" button |
| `oscarMDS/Page.jsp` (line 336) | Remove `ORU_R01:` category link to `viewOruR01.jsp` |
| `admin/keygen/createKey.jsp` (line 124) | Remove `OscarToOscarUtils` import |
| `encounter/oscarConsultationRequest/ConsultationFormRequest.jsp` (lines 1300, 2157, 2177) | Remove `eDataUrl` check and "Send Electronically" buttons |

---

## Struts Configuration

Remove from `src/main/webapp/WEB-INF/classes/struts.xml` (line 1584):

```xml
<action name="lab/CA/ALL/oruR01Upload"
        class="io.github.carlos_emr.carlos.lab.ca.all.pageUtil.OruR01Upload2Action">
    <result name="result">/common/genericResultPage.jsp</result>
</action>
```

---

## DAO / Model Impact

### ProfessionalSpecialist eData Fields

`ProfessionalSpecialist.java` has four eData fields used exclusively by oscar\_to\_oscar:

- `eDataUrl` — URL of remote CARLOS instance
- `eDataOscarKey` — RSA public key of remote instance
- `eDataServiceKey` — RSA signing key for this service
- `eDataServiceName` — Service identifier

These fields and their getters/setters can be removed from the model. The HBM/JPA
mapping should also be updated. Database columns can remain (Hibernate ignores
unmapped columns) and be dropped in a future migration.

### ProfessionalSpecialistDao Methods to Remove

- `findByEDataUrlNotNull()` — used by `SendOruR01UIBean` and consultation form
- `hasRemoteCapableProfessionalSpecialists()` — used by `ChartNotesAjax.jsp`

### Orphaned Model

`RemoteDataLog.java` (`commn/model/RemoteDataLog.java`) — the `RemoteDataLogDao` was
already deleted but the model class remains. Delete it.

---

## Localization Keys to Remove

Found in `oscarResources_{en,fr,es,pt_BR,pl}.properties`:

- `admin.admin.sendOruR01` — "Send data electronically to another CARLOS"
- `encounter.eSend` — "eSend" button label
- `encounter.eSendTitle` — "eSend" button title
- Any other keys containing `eData`, `eSend`, `eReferral` related to this feature

---

## Archived Documentation

Already in `docs/archive/hl7_communications/`:

- `oscar_to_oscar_communications.txt`
- `linking_to_sendOruR01.txt`
- `readme.txt`
- Example HL7 files and screenshots

These archived docs can remain as historical reference.

---

## Security Notes

The current code has several security concerns that further justify removal:

1. **MD5WithRSA signatures** (`SendingUtils.java:183`) — MD5 is cryptographically
   broken; signatures can be forged
2. **AES/ECB mode** (`SendingUtils.java:172`) — `Cipher.getInstance("AES")` defaults
   to ECB mode, which is insecure for structured data
3. **Hardcoded test keys** (`SendingUtils.java:210-211`) — `main()` method contains
   hardcoded RSA keys
4. **No input validation** on received HL7 messages — inbound handlers parse
   untrusted data without sanitization
5. **PHI in audit log** (`SendingUtils.java:102`) — raw HL7 data (containing patient
   info) is written to the log via `LogAction.addLog()`

---

## Estimated Scope

| Category | Files Deleted | Files Modified |
|---|---|---|
| Core package | 7 | 0 |
| Outbound consumers | 2 | 3 |
| Inbound handlers | 6 | 0 |
| View beans | 1 | 2 |
| JSPs | 2 | 5 |
| Struts config | 0 | 1 |
| DAO/Model | 1 | 2 |
| Properties | 0 | 5 |
| **Total** | **19** | **18** |

---

## Relationship to Integrator Removal

| System | Transport | Topology | Status |
|---|---|---|---|
| CAISI Integrator | SOAP/WS-Security | Hub-and-spoke (central server) | **Removed** |
| oscar\_to\_oscar | HTTP POST + RSA/AES | Peer-to-peer (direct) | **Still present — remove** |

Both systems were inter-EMR communication mechanisms. The integrator was removed
because the central server no longer exists. The oscar\_to\_oscar system should be
removed for the same reason — no CARLOS deployments are configured to exchange
peer-to-peer HL7 messages, and the feature requires manual key exchange and endpoint
configuration that no one performs.
