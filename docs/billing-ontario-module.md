# Ontario Billing Module (`billings/ca/on`)

> **Audience**: developers extending or maintaining the Ontario MOH/OHIP billing
> workflow in CARLOS EMR. Frontend (JSP) details for the bill-entry form
> itself are out of scope here — see the JSP layer notes below.

> **Last refreshed**: 2026-04-28. Codebase changes after that date should be
> verified against the current source before relying on this document.

## 1 — At a glance

The Ontario billing module captures, persists, corrects, and submits OHIP
claims for Ontario-resident patients. It implements:

- **Bill entry** (`billingON.jsp`) — interactive claim creation tied to a
  demographic and (optionally) an appointment, including service-code lookup,
  diagnostic code, and referral-doctor selection.
- **Bill review** (`billingONReview.jsp`) — per-bill review of items before
  save, including dx-code persistence and total recomputation.
- **Bill correction** (`billingONCorrection.jsp`) — full lifecycle edits on
  an already-saved bill: status changes, item edits, third-party payment
  entry, audit-snapshot writes to `billing_on_repo`.
- **Bill status** (`billingONStatus.jsp`) — paginated, filtered list of all
  bills with sort/search.
- **Disk creation + MOH submission** (`billingDiskCreation.jsp` and the
  OHIP claim-file generators) — assemble batches, write the MOH HL7 / fixed-
  width file format, track disk IDs.
- **Remittance advice import** — pull MOH RA messages, settle headers,
  reconcile payments and rejects.
- **Service-code admin** — manage the `billing_service` table, including the
  underscore-prefixed "private" billing codes.

Provincial scope: **Ontario only**. The British Columbia analogue lives in
`billings/ca/bc` and is intentionally out of scope for this document. The
shared cross-province billing entry router lives at `billings/ca/pageUtil`
(see §4 below).

## 2 — Package map

```
src/main/java/io/github/carlos_emr/carlos/billings/
├── ca/
│   ├── pageUtil/                          ← cross-province router (Billing2Action)
│   ├── bc/                                ← BC implementation (out of scope)
│   └── on/                                ← Ontario implementation (this doc)
│       ├── BillingDates.java                top-level utility (date parsing / formatting)
│       ├── BillingMoney.java                top-level utility (BigDecimal money helpers)
│       ├── assembler/                       *ViewModelAssembler classes + composers/loaders
│       ├── command/                         typed write/validation commands
│       ├── dto/                             persistence/query transfer DTOs (incl. FeeSchedule* records)
│       ├── reports/                         reporting (RA / disk / Y/E statement)
│       ├── service/                         services / loaders / parsers / persisters / calculators
│       ├── support/                         dependency-free support utilities
│       ├── validator/                       input-validator classes + typed exception
│       ├── viewmodel/                       immutable presentation records
│       └── web/                             *2Action Struts gates

src/main/webapp/WEB-INF/jsp/billing/CA/ON/    ← JSP views (incl. shared jspf fragments)
src/main/webapp/WEB-INF/classes/struts-billing.xml  ← Struts mappings (one entry per public route)
```

### 2.1 Java identifier abbreviations

Class and member names use Java-style acronym casing:

| Official/domain term | Identifier form | Example |
|---|---|---|
| Ontario / ON | `On` | `ViewBillingOn2Action` |
| OHIP | `Ohip` | `OhipClaimFileService` |
| RA / remittance advice | `Ra` | `BillingOnRaService` |
| MOH | `Moh` | `MoveMohFiles2Action` |
| INR | `Inr` | `InrBillingUpdate2Action` |
| GST | `Gst` | `GstSettingsService` |
| MRI | `Mri` | `BillingOnMriViewModel` |
| EDT / OBEC | `Edt`, `Obec` | `BillingEdtObecOutputSpecificationParser` |
| diagnosis | `Diag` | `BillingOnReviewDiagPersister` |

This casing rule applies to Java identifiers only. Prose, JSP labels, external
protocol names, and Ministry/OHIP document references may keep official forms
such as `OHIP`, `RA`, and `MOH`. Avoid legacy compressed names (`3rd`, `Dig`,
`Db`, `Obj`, `Hlp`, `Bean`, `Handler`) and spell out normal domain words such
as `ThirdParty`, `Specialist`, `Payment`, `Address`, and `Report`.

## 3 — Architecture

The module is organised in three layers, top to bottom:

```
┌──────────────────────────────────────────────────────────────────────┐
│ HTTP boundary (Struts 7.1.1)                                         │
│   *2Action gates  ───────────►  authn/authz, request validation,     │
│   (web/)                       single-method per HTTP verb           │
└──────────────────────────────────────┬───────────────────────────────┘
                                       │
┌──────────────────────────────────────▼───────────────────────────────┐
│ Presentation layer                                                   │
│   *ViewModelAssembler  ─►  *ViewModel  ─►  JSP                       │
│   (assembler/)              (viewmodel/, immutable records)          │
│   Compose data for the view; no DAO writes; no HTTP semantics.       │
└──────────────────────────────────────┬───────────────────────────────┘
                                       │
┌──────────────────────────────────────▼───────────────────────────────┐
│ Domain / data layer                                                  │
│   *Loader / *Persister / *Calculator / *Service                      │
│   (service/)                                                         │
│        ↓                                                             │
│   *Dao    →    Entity  ←──── domain methods (rich-domain partial)    │
│   (commn/dao)  (commn/model: BillingONCHeader1, BillingONItem, …)    │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.1 Suffix grammar (the layer-naming policy applied)

Every class in the module obeys the project-wide policy in
[`docs/architecture/layer-names.md`](architecture/layer-names.md). The
shortest version: pick the most specific role-suffix; fall back to `*Service`
only when nothing more specific applies.

| Role | Suffix | Examples in this module |
|---|---|---|
| HTTP entry point | `*2Action` | `BillingOnSave2Action`, `ViewBillingOnReview2Action` |
| View-model builder | `*ViewModelAssembler` | `BillingOnFormViewModelAssembler`, `BillingOnCorrectionViewModelAssembler` |
| Immutable presentation DTO | `*ViewModel` | `BillingOnFormViewModel`, `BillingOnStatusViewModel` |
| Typed write/validation input | `*Command` | `BillingCorrectionSubmitCommand`, `BillingSpecialistClaimCommand` |
| Persistence/query transfer DTO | `*Dto` | `BillingClaimHeaderDto`, `BillingClaimItemDto` |
| Sub-assembler / partial composer | `*RenderComposer` | `BillingOnCorrectionRenderComposer` |
| Pure-read query service | `*Loader` | `BillingOnClaimLoader`, `BillingOnDiskLoader`, `ServiceCodeLoader` |
| Pure-write persistence service | `*Persister` | `BillingOnClaimPersister`, `BillingOnReviewDiagPersister`, `ServiceCodePersister` |
| Pure calculation | `*Calculator` | _(none currently in the ON module — `BillingOnInvoiceTotalsService` reads multiple DAOs and is therefore a `*Service`.)_ |
| Input validator | `*Validator` | `BillingOnReviewValidator` (with `BillingValidationException` for fail-fast paths) |
| Mixed read/write orchestration | `*Service` | `BillingCorrectionService`, `BillingOnHeaderCreationService`, `BillingOnLookupService` |
| Data access | `*Dao` | `BillingONCHeader1Dao`, `BillingONPaymentDao`, `BillingServiceDao` |

DAOs do **not** orchestrate other DAOs — cross-DAO operations live in a
service. Static-utility classes use a domain noun with no suffix and live
either at the top of `billings/ca/on/` (`BillingDates`, `BillingMoney`)
or, for module-internal helpers, under `support/` (`BillingDateOfBirths`,
`BillingDomIdTokens`).

Forbidden in new code (and currently absent from this module): `*Prep`,
`*Manager`, `*Helper`, `*Utils`, compound suffixes like `*ServiceManager` or
`*LoaderService`.

### 3.2 Spring wiring

- All non-DAO components are registered with `@org.springframework.stereotype.Service`.
- Constructor injection is used everywhere — there are no field-init
  `SpringUtils.getBean(...)` patterns left in the module. This was a
  deliberate cleanup; service-locator usage is treated as a regression.
- `@Lazy` is **not** used anywhere in `billings/ca/on`. It was carried
  forward from a long-since-resolved circular-dependency workaround and was
  stripped in 2026-04-27. If you reintroduce a real cycle, Spring will fail
  at boot — fix the cycle, don't paper over it with `@Lazy`.
- `@Transactional` (or `@Transactional(readOnly = true)`) is applied at the
  service layer, not on assemblers or actions. Some legacy services
  (e.g. `BillingCorrectionService`) currently lack it — this is documented
  as a known follow-up in §11.

### 3.3 Design rationale

Three constraints drove the layered shape:

1. **JSPs as views, not controllers.** Older billing JSPs hand-loaded data
   via inline scriptlets calling `SpringUtils.getBean(SomeDao.class)`. Every
   such pattern in `billing/CA/ON/` has been replaced by a ViewModelAssembler +
   ViewModel chain so the JSP renders from `${model.xxx}` only. The recipe is
   in [`docs/JSP-REFACTORING-GUIDE.md`](JSP-REFACTORING-GUIDE.md).
2. **Single-purpose classes over catch-alls.** The legacy `BillingONService`
   grew to 30+ unrelated methods and was deleted; its responsibilities now
   live across `BillingOnClaimLoader`, `BillingOnClaimPersister`,
   `BillingOnInvoiceTotalsService`, and several others. The same shape
   was applied to `*Step` / `*Prep` legacy classes.
3. **Pure queries on the entity, cross-DAO work in services.** Pure-state
   checks (`isOhipBill`, `isPaidInFull`, `isActive`, `recomputeTotalFromItems`)
   sit on `BillingONCHeader1` and `BillingONItem`. Cross-DAO operations
   (`calculateBalanceOwing` needs both header and payments) sit in a
   service. This is "rich-domain partial DDD" — see §5.

## 4 — Cross-province routing

The struts mapping `name="billing"` is the public entry point for both BC
and ON billing flows. It dispatches via a **router → chain → setup**
pattern:

```
GET/POST /carlos/billing?billRegion=ON&demographic_no=…
  │
  ├─► Billing2Action (ca.pageUtil) ◄── tiny router, no province imports
  │     ├── _billing r privilege check
  │     ├── reads request param billRegion (falls back to billregion property)
  │     └── returns "ON" or "BC"
  │
  ├─► result name="ON"  type="chain"  →  billing/CA/ON/billingView
  │     └── ViewBillingOn2Action  →  /WEB-INF/jsp/billing/CA/ON/billingON.jsp
  │
  └─► result name="BC"  type="chain"  →  billing/CA/BC/billingSetup
        └── BillingBCSetup2Action   →  /WEB-INF/jsp/billing/CA/BC/billingBC.jsp
```

Why the router lives at `ca.pageUtil` and not `ca.bc.pageUtil`: the cross-
province decision should not have BC-specific imports. The dedicated BC
setup action carries the BC-only coupling (`BillingSessionBean`,
`BillingGuidelines`, `BillingCreateBilling2Form`), and the ON path chains
through its own `ViewBillingOn2Action`. This was previously violated —
`Billing2Action` lived in `ca.bc.pageUtil` with BC imports — and was
hoisted to `ca.pageUtil` to break the cycle.

Region resolution rules (`Billing2Action.execute`):

1. If `billRegion` request parameter is present and non-empty, use it.
2. Otherwise, fall back to `CarlosProperties.getProperty("billregion")`.
3. Anything not equal to `"ON"` (including null) routes to BC. Historical
   behaviour: BC was the original deployment.

The router gracefully handles `CarlosProperties.getProperties()` returning
null (which can happen pre-config), defaulting to BC.

Privilege check: `_billing r`. Province-specific gates also enforce
`_billing r` or `_billing w` as appropriate; the router doesn't grant; it
funnels.

## 5 — Entity model

Three entities carry the bulk of Ontario billing state:

| Entity | Table | Role |
|---|---|---|
| `BillingONCHeader1` | `billing_on_cheader1` | One row per claim — provider, demographic, totals, status, dates |
| `BillingONItem` | `billing_on_item` | Line items per claim — service code, fee, dx code, status |
| `BillingONPayment` | `billing_on_payment` | Payment records (3rd party, OHIP RA settlement) |
| `BillingONExt` | `billing_on_ext` | Extension key/value rows attached to either claim or payment |

### 5.1 Domain methods (rich-domain partial)

Pure-state queries live on the entity, where they belong:

```java
// BillingONCHeader1
public boolean isOhipBill();              // payProgram == "HCP"
public boolean isSettled();                // status == SETTLED ("S")
public boolean isActive();                 // status != DELETED ("D")
public boolean isDeleted();                // status == DELETED
public void    markSettled();              // setStatus(SETTLED) — single home for settle-side effects
public boolean isPaidInFull(BigDecimal paidTotal);   // paidTotal.compareTo(total) >= 0
public Optional<BigDecimal> recomputeTotalFromItems();  // sum of active items' fees

// BillingONItem
public boolean isActive();
public boolean isDeleted();
```

These methods reason about the entity's own fields only — no DAO calls,
no cross-aggregate state. They are unit-tested at
`src/test/java/io/github/carlos_emr/carlos/commn/model/BillingONCHeader1UnitTest.java`.

### 5.2 Setter invariants

`BillingONCHeader1.setTotal` enforces that invoice totals are non-negative
(refunds belong on the payments table). A negative argument indicates a
calling-side bug or corrupt import data, so the setter fails fast with
`IllegalArgumentException` rather than silently changing the amount.

The invariant is bypassable via field-level access (Hibernate load,
direct constructors), so it is not a structural guarantee on every read.
Import flows that need tolerant normalization must do that before calling
the entity setter. See the JavaDoc on `BillingONCHeader1.setTotal` for the
full contract; the unit test that pins it is
`BillingONCHeader1UnitTest.shouldRejectNegativeValues`.

Other setters are unchanged from the legacy shape and accept any input
(including null) — invariant tightening is incremental and only added
when a real bug history justifies it.

### 5.3 Soft-delete convention

`status = "D"` is the soft-delete marker for both `BillingONCHeader1` and
`BillingONItem`. **Never check the literal `"D"` in callers** — use
`entity.isActive()` / `entity.isDeleted()`. Callers that need filtered
lists use `BillingONCHeader1Dao.findActiveItems(invoiceNo)` (which queries
`BillingONItem` directly, not via the parent collection — see §6).

`status` values you'll see in the data:
- `null` or empty — open / unsubmitted
- `"O"` — open / billable
- `"S"` — settled (per RA import)
- `"D"` — soft-deleted
- Other codes (`"N"`, `"R"`, …) appear in correction workflows; treat them
  as opaque unless the specific value is what you care about.

### 5.4 Cross-aggregate calculations

Calculations that need data beyond a single entity live in a service:

```java
// service/BillingOnInvoiceTotalsService
public BigDecimal calculateBalanceOwing(Integer invoiceNo);
//   total - paidTotal + refundTotal, where paid/refund come from
//   BillingONPaymentDao.find3rdPartyPayRecordsByBill(header).
```

This stays in a service because it joins two aggregates (claim + payments).
The single-aggregate sum-of-items recompute lives on the entity as
`recomputeTotalFromItems()`.

## 6 — Hibernate fetch policy

Two `@OneToMany` collections were `FetchType.EAGER` historically and have
been flipped to `LAZY`:

- `BillingONCHeader1.billingItems`
- `BillingONPayment.billingONExtItems`

EAGER loading meant every header lookup paid for the items join, even when
the caller only wanted header scalars (status updates, totals, payments).
With LAZY:

### 6.1 Use the right find method

| Need | DAO method | Reason |
|---|---|---|
| Header only | `BillingONCHeader1Dao.find(id)` | scalar-only paths, status updates, persistence |
| Header + items, single bill | `BillingONCHeader1Dao.findWithItems(id)` | renders, correction loads — JOIN FETCH for one query |
| Headers + items, demographic-scoped | `BillingONCHeader1Dao.findByDemoNoWithItems(demoNo, off, size)` | REST `BillingDetailConverter`, history pages — JOIN FETCH per row |
| Just the active items for a bill | `BillingONCHeader1Dao.findActiveItems(invoiceNo)` | direct JPQL on `BillingONItem` — bypasses the parent collection |

If you call `find(...)` and then access `.getBillingItems()` outside an
open Hibernate session (e.g., in a non-`@Transactional` assembler or a
REST converter), Hibernate throws `LazyInitializationException`. Use
`findWithItems` instead.

### 6.2 The DISTINCT pattern in `findByDemoNoWithItems`

```java
"SELECT DISTINCT h FROM BillingONCHeader1 h "
+ "LEFT JOIN FETCH h.billingItems "
+ "WHERE h.demographicNo = ?1 AND h.status != 'D' "
+ "ORDER BY h.billingDate DESC, h.billingTime DESC, h.id DESC"
```

`LEFT JOIN FETCH` produces one SQL row per child item, so without `DISTINCT`
the parent count would be wrong. `DISTINCT` keeps the parent count correct;
the items collection is still populated correctly in each returned entity.

### 6.3 Tested invariants

`src/test/java/io/github/carlos_emr/carlos/commn/dao/BillingONCHeader1DaoIntegrationTest.java`
contains nested classes `FindWithItems` and `FindByDemoNoWithItems` that
lock in the JOIN FETCH behaviour against H2. They use
`entityManager.clear()` after persisting test data to prove that the
returned entity's collection survives session detachment.

## 7 — Services / Loaders / Persisters reference

The service classes cover loaders, parsers/importers, persisters, calculators,
and business workflows. Use this table to find the right type when you need a
known operation.

| Class | Role | What it does |
|---|---|---|
| `BillingOnClaimLoader` | Loader | Bulk + filtered claim queries, history, code/fee lookups |
| `BillingOnDiskLoader` | Loader | Disk record queries, batch header reads, MRI list |
| `BillingOnLookupService` | Service | Provider/team/site lookups + a few status writes (mixed) |
| `BillingReviewQueryService` | Loader | Review-page service-code + percentage/dx queries |
| `BillingStatusQueryService` | Loader | Param-normalising facade over `BillingOnClaimLoader` for the status page |
| `ServiceCodeLoader` | Loader | `billing_service` table reads (code attrs, dropdown desc) |
| `ServiceCodePersister` | Persister | `billing_service` writes (admin add/update/delete of private codes) |
| `BillingClaimBatchAcknowledgementReportParser` | Parser | Fixed-width batch acknowledgement report parsing |
| `BillingClaimsErrorReportParser` | Parser | Fixed-width claims error report parsing |
| `BillingEdtObecOutputSpecificationParser` | Parser | EDT OBEC output report parsing |
| `BillingClaimsErrorReportImportService` | Import service | Claims error report upload/import workflow |
| `BillingOnClaimPersister` | Persister | Header/item/ext record inserts and updates |
| `BillingOnReviewDiagPersister` | Persister | Diagnostic-code persistence on review save |
| `BillingOnCorrectionPersister` | Persister | Correction lifecycle writes + colocated reads (split into `*Loader` is a candidate follow-up) |
| `BillingOnInvoiceTotalsService` | Service | `calculateBalanceOwing` — reads `BillingONCHeader1Dao` + `BillingONExtDao` + `BillingONPaymentDao` (cross-DAO ⇒ `*Service` per layer-names rule 4) |
| `BillingOnHeaderCreationService` | Service | Header creation orchestration (`@Transactional`) |
| `BillingCorrectionService` | Service | `updateInvoice` + `addThirdPartyPayment` workflows |
| `BillingCorrectionRecordService` | Service | Audit-snapshot writes to `billing_on_repo` |
| `BillingOnRaService` | Service | RA import + status updates |
| `BillingOnErrorReportService` | Service | RA error-report generation |
| `BillingRaReportService` | Service | RA summary/desc report data prep |
| `OnRaSettlementService` | Service | RA settlement workflow |
| `BillingDiskCreationService` | Service | OHIP disk creation lifecycle |
| `BillingOnDiskService` | Service | Disk operations facade |
| `OhipClaimFileService` | Service | OHIP fixed-width claim file generation |
| `OhipClaimExtractService` | Service | OHIP claim extract rendering/state carrier |
| `OhipReportGenerationService` | Service | OHIP report generation |
| `GstReportService` | Service | GST report query workflow |
| `GstSettingsService` | Service | GST percent settings read/write workflow |
| `BillingSpecialistClaimService` | Service | Specialist-billing workflow |
| `BillingClaimSubmissionService` | Service | Save-side orchestration (legacy; mixed) |
| `BillingOnAuditLogService` | Service | Audit log writes |
| `BillingThirdPartyService` | Service | Third-party billing workflow |
| `BillingThirdPartyRecordService` | Service | Third-party record lifecycle |
| `PatientEndYearStatementService` | Service | Year-end statement generation |

### 7.1 When to add which type

- **Adding a pure-read query?** Add a method to an existing `*Loader`, or
  create a new one if the surface doesn't fit. Annotate with
  `@Transactional(readOnly = true)`.
- **Adding a pure-write?** Add to an existing `*Persister`, or create a new
  one. Annotate with `@Transactional`.
- **Adding pure arithmetic?** First check if it fits on the entity (rich-
  domain). If it needs cross-aggregate state, add a method to a `*Calculator`.
- **Adding a multi-step lifecycle?** That's a `*Service` — put it on the
  most relevant existing one if it's small, else create a new service.

Avoid creating a new `*Service` whenever a more specific suffix fits. The
goal is for the suffix to advertise the role.

## 8 — Web layer (Struts 7.1.1)

Every billing entry point is a `*2Action` extending `org.apache.struts2
.ActionSupport`. The ON actions in `web/` follow a
consistent shape:

`ViewBillingOnReview2Action` is the canonical shape — the example below is the
real source, lightly trimmed (see `web/ViewBillingOnReview2Action.java`):

```java
public class ViewBillingOnReview2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final BillingOnReviewDiagPersister dxPersister;
    private final BillingOnReviewViewModelAssembler assembler;

    public ViewBillingOnReview2Action(SecurityInfoManager securityInfoManager,
                                       BillingOnReviewDiagPersister dxPersister,
                                       BillingOnReviewViewModelAssembler assembler) {
        this.securityInfoManager = securityInfoManager;
        this.dxPersister = dxPersister;
        this.assembler = assembler;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            // RFC 7231 §6.5.5: 405 responses MUST include the Allow header.
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        BillingOnReviewViewModel model = assembler.assemble(request, loggedInInfo);
        request.setAttribute("reviewModel", model);
        return SUCCESS;
    }

    public BillingOnReviewViewModel getReviewModel() { /* ... */ }
}
```

Notes on this template:

- Review2Action is **POST-only** because it triggers a clinical-write side
  effect (`BillingOnReviewDiagPersister`). Read-only actions
  (e.g., `ViewBillingOn2Action`) accept GET/HEAD/POST instead and emit
  `Allow: "GET, HEAD, POST"`.
- The null-session guard runs **before** `hasPrivilege` because
  `SecurityInfoManagerImpl.hasPrivilege` dereferences `loggedInInfo` and
  emits a noisy "Error checking privileges" stack-trace before returning
  false on null. Failing fast keeps the log signal clean.
- The 405 path uses `response.sendError(...)` (which commits the response)
  rather than `setStatus(...)` so framework dispatch stops cleanly.

### 8.1 Action conventions

- **One method per action.** Multi-method "router" actions (`?method=...`)
  have all been split into single-purpose actions over the course of this
  refactor. Don't add new method-dispatch actions.
- **Constructor injection.** No `field = SpringUtils.getBean(...)` shims.
- **Privilege check first.** `_billing r` for read, `_billing w` for write.
  Throw `SecurityException` (the global Struts exception mapping renders
  the access-denied page).
- **HTTP method gate.** Mutation actions reject non-POST with
  `405 Method Not Allowed` and an `Allow` response header. Read actions
  accept GET/HEAD/POST (the billing form self-posts on titlesearch).
  View actions that only forward to a render JSP (no side effect) accept
  any method; gating them with POST-only breaks the navigation link.
- **Throw or `return NONE`.** Never silently swallow validation failures.

### 8.2 Struts mapping

All Ontario billing action mappings live in
`src/main/webapp/WEB-INF/classes/struts-billing.xml`. Conventions:

- All routes are extensionless (`struts.action.extension=""`).
- All views live under `/WEB-INF/jsp/billing/CA/ON/` — never expose JSPs
  directly.
- Read actions return `SUCCESS` to a JSP forward. Mutation actions either
  forward to a confirmation JSP or chain to a sibling read action's result
  (e.g., `<result name="closeReload" type="chain">...</result>`).
- Cross-province dispatch uses `type="chain"` (preserves request, session,
  and original query params; no extra HTTP hop).

### 8.3 CSRF & encoding

- All POST forms get a CSRF-TOKEN auto-injected by CSRFGuard 4.5
  (configured in `Owasp.CsrfGuard.properties`). AJAX `fetch()` calls must
  read the token from `input[name="CSRF-TOKEN"]` and set it as the
  `CSRF-TOKEN` header. Pages without a real `<form>` use the bootstrap
  fragment at `/WEB-INF/jspf/csrf-token.jspf` — see
  [`docs/csrf-protection-architecture.md`](csrf-protection-architecture.md).
- All user data rendered to the JSP must use the CARLOS null-safe
  encoder: `<carlos:encode value="${...}"/>` for HTML body,
  `${carlos:forJavaScript(...)}` for JS strings, etc. CI fails the build
  if new code uses the unsafe `<e:forXxx>` / `${e:forXxx()}` /
  `Encode.forXxx(...)` forms — those return the literal string `"null"`
  for null inputs.

## 9 — Presentation layer (ViewModelAssembler + ViewModel)

Each JSP-backed page has exactly one ViewModelAssembler and one ViewModel:

```
ViewBillingOnReview2Action          ← gate
  └─► BillingOnReviewViewModelAssembler  ← composes data
        └─► BillingOnReviewViewModel ← immutable record consumed by the JSP
              └─► billingONReview.jsp
```

### 9.1 ViewModel conventions

- Java `record` (or a Lombok `@Builder` for nested-collection cases).
- All fields immutable. Lists are unmodifiable.
- Includes a `builder()` for ergonomic construction in the assembler.
- Fields named for their JSP-rendered shape (e.g., `formattedTotal`, not
  `total`). The view does no formatting — it just displays.

### 9.2 ViewModelAssembler conventions

- `@Service` Spring bean, constructor-injected dependencies.
- Single public method: `ViewModel assemble(HttpServletRequest, LoggedInInfo)`
  (or a more specific signature if the assembler needs domain inputs).
- No HTTP semantics — never returns an HTTP status code. If the request
  is malformed, the assembler fails fast with a typed exception or
  populates a per-field error flag on the view model. The action layer
  decides what HTTP code to send.
- No DAO writes. If the page submission triggers a write, the action
  delegates to a service (e.g., `BillingCorrectionService.updateInvoice`)
  before calling the assembler to render the post-write view.

### 9.3 The JSP itself

After the refactor, every billing JSP under `WEB-INF/jsp/billing/CA/ON/`
should:

- Declare the `<%@ taglib uri="carlos" prefix="carlos" %>` taglib once.
- Render exclusively from `${model.xxx}` — no `<%= ... %>` scriptlets,
  no `SpringUtils.getBean(...)` calls, no `<%@page import>` of model
  classes.
- Use `<carlos:encode>` (or the corresponding EL function) for every
  user-data interpolation.

The largest remaining JSP, `billingON.jsp`, is in flight; the
plan is in commit history under `chore/billing-refactor`. The other four
fat ON JSPs (`billingONCorrection.jsp`, `billingONReview.jsp`,
`billingONStatus.jsp`, `billingShortcutPg1.jsp`) follow the same recipe
but are individually further along.

The recipe itself is documented in
[`docs/JSP-REFACTORING-GUIDE.md`](JSP-REFACTORING-GUIDE.md).

## 10 — Adding a new feature (recipe)

Suppose you're adding a new "void claim" page. Follow this checklist:

1. **Database changes** (if any) go to `database/mysql/updates/update-YYYY-
   MM-DD-void-claim.sql`. Add the migration script; do not modify
   `oscarinit_2025.sql` directly.
2. **DAO method**, if a new query is needed. Add to the relevant
   `*Dao.java` interface and `*DaoImpl.java`. Use parameterised JPQL.
   Cover with a `*DaoIntegrationTest`.
3. **Service / Loader / Persister method.**
   - Pure read? → `*Loader`.
   - Pure write? → `*Persister`.
   - Multi-step → `*Service` (annotate with `@Transactional`).
   - Use the entity's own domain methods where applicable.
4. **ViewModel** under `viewmodel/` — immutable record, constructed via
   builder. Field names match the JSP's render needs.
5. **ViewModelAssembler** under `assembler/` — `@Service`, constructor-injected,
   single `assemble(...)` method.
6. **Action** under `web/` — `*2Action` extending `ActionSupport`. Privilege
   check, HTTP method gate, delegate to assembler/service, set
   `request.setAttribute("model", vm)`, return result string.
7. **Struts mapping** in `struts-billing.xml`. Choose the convention that
   matches similar actions; prefer extensionless routes.
8. **JSP** under `/WEB-INF/jsp/billing/CA/ON/`. Render from `${model.*}`
   only. Encode all output via `<carlos:encode>`.
9. **Tests:**
   - DAO method → `*DaoIntegrationTest` (extends `CarlosTestBase`,
     tagged `@Tag("integration") @Tag("dao") @Tag("billing")`).
   - Service method → unit test extending `CarlosUnitTestBase`, mock the
     DAO. Or integration test if real persistence behaviour matters.
   - ViewModelAssembler → unit test mocking the loaders/services it consumes.
   - Action → unit test asserting the privilege check, the method gate,
     and the success delegation pattern.
10. **Wire to navigation.** Add the link in the appropriate JSP nav
    fragment. Don't link to the JSP — link to the action route.

### 10.1 Naming the class

Pick the suffix that best advertises the role (see §3.1). If you find
yourself reaching for "FooSomething" and it has both reads and writes,
ask: does this orchestrate a lifecycle, or is it just a misnamed grab
bag? A grab bag should be split.

## 11 — Testing patterns

The module has 400+ tests across unit and integration tiers. Tagging:

```bash
mvn test -Dgroups="billing"            # all billing tests
mvn test -Dgroups="billing,unit"       # billing unit tests only
mvn test -Dgroups="billing,integration"  # billing integration tests
```

### 11.1 Unit test base classes

- `CarlosUnitTestBase` — for tests that mock `SpringUtils.getBean(...)`.
  Provides `registerMock(Class, Object)`.
- `CarlosTestBase` — for tests that need a Spring context + H2 database.
  Provides `entityManager`, `hibernateTemplate`, transactional rollback.

For an `*Action` test that needs `ServletActionContext`, the pattern is:

```java
private MockedStatic<ServletActionContext> servletActionContextMock;
private MockedStatic<LoggedInInfo> loggedInInfoMock;

@BeforeEach
void setUp() {
    mockitoCloseable = MockitoAnnotations.openMocks(this);
    mockRequest = new MockHttpServletRequest();
    mockResponse = new MockHttpServletResponse();
    mockRequest.setMethod("POST");

    servletActionContextMock = mockStatic(ServletActionContext.class);
    servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
    servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

    loggedInInfoMock = mockStatic(LoggedInInfo.class);
    loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
            .thenReturn(mockLoggedInInfo);

    when(mockSecurityInfoManager.hasPrivilege(any(), eq("_billing"), eq("r"), isNull()))
            .thenReturn(true);
}

@AfterEach
void tearDown() throws Exception {
    if (loggedInInfoMock != null) loggedInInfoMock.close();
    if (servletActionContextMock != null) servletActionContextMock.close();
    if (mockitoCloseable != null) mockitoCloseable.close();
}
```

`Billing2ActionUnitTest` is a good template — it exercises the privilege
gate, the region-resolution matrix, and the null-safe `CarlosProperties`
fallback.

### 11.2 BDD naming

The convention is `should<Action>_<preposition><Condition>()` with one
underscore separator. Examples in this module:

```java
void shouldReturnON_whenBillRegionParamIsON()
void shouldReturnBalanceOwing_givenSettledHeaderAndPayments()
void shouldThrowSecurityException_whenMissingBillingReadPrivilege()
void shouldReturnEmpty_whenAFeeCannotBeParsed()
```

### 11.3 Hibernate test traps

The `CarlosTestBase` integration tests share a JDBC connection across the
JPA `EntityManager` and the legacy Hibernate `Session`. Common pitfalls
(documented at length in CLAUDE.md):

- Use `hibernateTemplate.flush()` after a `HibernateDaoSupport`-based
  DAO write. `entityManager.flush()` only flushes the JPA context.
- HBM property names are case-sensitive — check the `.hbm.xml` before
  writing HQL.
- Always quote H2 reserved words in HBM column attributes
  (`column="`value`"`).
- `entityManager.clear()` before asserting on a freshly-loaded entity if
  you want to confirm true LAZY/JOIN-FETCH behaviour rather than first-
  level cache return.

## 12 — Open follow-ups

Things this module would benefit from but which are not yet done:

- **`@Transactional` on `BillingCorrectionService`.** The class TODO comment
  in source notes the gap; the `BillingValidationException` fast-fail
  closes the most acute exposure but a real merge mid-sequence is still
  a risk.
- **`billingON.jsp` JSP refactor (Phase 2A in `/root/.claude/plans/`).**
  The largest remaining ON JSP is being decomposed into Assembler +
  ViewModel + JSP includes — same recipe as the four other ON billing
  JSPs that are already done.
- **BC parity.** This module structure has not been mirrored into
  `billings/ca/bc`. The BC analogue still has the legacy patterns
  (multi-method actions, scriptlet JSPs, mixed-role services). Out of
  scope for the ON refactor branch; tracked separately.
- **Mixed-role services.** A handful of pre-existing services
  (`BillingOnLookupService`, `BillingDiskCreationService`,
  `BillingClaimSubmissionService`, `BillingOnCorrectionPersister`) carry
  both reads and writes. They legitimately orchestrate mixed lifecycles
  today, but if any one of them grows further, splitting into
  `*Loader` + `*Persister` is the next step.
- **JSP guardrail.** `scripts/lint/check-jsp-size.sh` fails CI on any JSP
  under `WEB-INF/jsp/billing/**` that exceeds the byte/scriptlet/getBean
  thresholds. Cheap insurance against the page-buffer workaround returning.

## 13 — Cross-references

- Layer-naming policy: [`docs/architecture/layer-names.md`](architecture/layer-names.md)
- JSP refactoring recipe: [`docs/JSP-REFACTORING-GUIDE.md`](JSP-REFACTORING-GUIDE.md)
- Struts action inventory: [`docs/struts-actions-detailed.md`](struts-actions-detailed.md)
- Struts URL conventions: [`docs/struts-web-endpoints.md`](struts-web-endpoints.md)
- CSRF protection: [`docs/csrf-protection-architecture.md`](csrf-protection-architecture.md)
- File path validation: [`docs/path-validation-utils.md`](path-validation-utils.md)
- Test framework: [`docs/test/modern-test-framework-complete.md`](test/modern-test-framework-complete.md)
- Test writing patterns: [`docs/test/test-writing-guide.md`](test/test-writing-guide.md)

Provincial siblings (out of scope here):

- BC billing: `src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/`
- BC Teleplan upload: `src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/Teleplan/`
- Cross-province MSP: `src/main/java/io/github/carlos_emr/carlos/billings/MSP/`

## 14 — Refactor history (selected)

The current shape of this module was reached over a series of focused
commits on the `chore/billing-refactor` branch. The most architecturally
significant ones, newest first:

| Commit | Slice |
|---|---|
| `a1f94adbdc` | Removed redundant `@Lazy` from 64 services + assemblers |
| `3e81a5c8d9` | Flipped `BillingONCHeader1.billingItems` and `BillingONPayment.billingONExtItems` to LAZY; added `findWithItems` / `findByDemoNoWithItems` |
| `eee331f027` | Renamed 5 misnamed `*Service` to `*Loader` / `*Persister`; split `BillingONServiceCodeService` into `ServiceCodeLoader` + `ServiceCodePersister` |
| `91d176501a` | Hoisted cross-province `Billing2Action` from `ca.bc.pageUtil` to `ca.pageUtil`; extracted `BillingBCSetup2Action`; stripped dead ON branch from BC `BillingView2Action` |
| `d6169b6813` | Partial DDD — added 9 domain methods to `BillingONCHeader1`/`BillingONItem`; added `setTotal` invariant |
| `13c0ec060c` | Closed test debt — 51 new tests for refactored actions/services |
| `faebd82653` | Split `BillingONService` catch-all into single-purpose pieces |
| `af86635bc2` | Codified the layer-naming policy + retired all `*Prep` classes |
| `e38d36813b` | Typed `GstSettingsService` API (dropped Properties bag); extracted dependency-free helpers |
| `533806d0b9` | Extracted `BillingOnHeaderCreationService` so DAO no longer orchestrates other DAOs |
| `8a11f59b52` | Split `PaymentType2Action` and `PatientEndYearStatement2Action` into single-method actions |
| `cbcc95ac29` | Migrated gstControl + gstreport JSPs to View2Action + ViewModel |
| `2f922b98a9` | Drained remaining service-locator and dual-bean bandaids |
| `8d138bf86c` | Converted 32 assemblers + validator to single-ctor injection |
| `3b7501c2dd` | Converted 53 field-init actions to ctor injection; added `@Primary` on `BillingDaoImpl` |
| `2618f98df7` | Converted 24 dual-ctor 2Actions to `@Autowired` ctor injection |

For the full history, see `git log --oneline chore/billing-refactor`.
