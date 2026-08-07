# Layer Names — naming policy for new classes

**Goal:** when you reach for a new class name, the suffix tells the next reader its lifecycle and role at a glance. Mixed suffixes (`*ServiceManager`, `*LoaderService`) are forbidden — pick one.

This policy was written against the Ontario billing module (`io.github.carlos_emr.carlos.billings.ca.on.*`); it applies to all new code.

## The principle

**Suffix = role + lifecycle.** Pick the most specific verb that fits. Only fall back to `*Service` when nothing more specific applies. Never combine two role-suffixes, except for the sanctioned `*ImportService` suffix used by file-import workflows.

## Identifier abbreviations

Java identifiers use Java-style acronym casing, not all-caps acronym runs:

| Domain term | Java identifier form |
|---|---|
| Ontario / ON | `On` |
| OHIP | `Ohip` |
| RA / remittance advice | `Ra` |
| MOH | `Moh` |
| INR | `Inr` |
| GST | `Gst` |
| MRI | `Mri` |
| EDT / OBEC | `Edt`, `Obec` |
| diagnosis | `Diag` |

Use these forms only in identifiers. Prose, UI labels, external file names,
and protocol names may keep the official uppercase form (`OHIP`, `RA`, `MOH`).
Do not revive legacy compressed names such as `3rd`, `Dig`, `Db`, `Obj`,
`Hlp`, `Bean`, or `Handler`. Keep normal domain words such as `ThirdParty`,
`Specialist`, `Report`, `Payment`, and `Address` spelled out.

## The sanctioned suffixes

| Suffix | Lifecycle | What it does | Example |
|---|---|---|---|
| `*Action` | per-request (Struts2) | Privilege check + parse params + delegate + return result string. **No business logic.** | `ViewBillingOn2Action` |
| `*ViewModelAssembler` | `@Service` | Builds the `*ViewModel` for **exactly one** JSP. Read-only orchestration. | `BillingOnFormViewModelAssembler` |
| `*ViewModel` | DTO/record | Immutable view-state for one JSP. No behavior beyond accessors. | `BillingOnFormViewModel` |
| `*Command` | DTO/record | Typed input for a write or validation use case. No persistence behavior. | `BillingCorrectionSubmitCommand` |
| `*Dto` | DTO/record | Typed transfer shape for persistence/query boundaries. No presentation behavior. | `BillingClaimHeaderDto` |
| `*Loader` | `@Service` | Loads **one slice** of state onto a builder passed in. Used inside assemblers. | `BillingOnFormDemographicLoader` |
| `*Resolver` | `@Service` | Picks **one value** through a priority chain or rule. | `BillingOnFormBillFormResolver` |
| `*Composer` | `@Service` | Assembles a **complex sub-structure** onto a builder. Bigger than a Loader. | `BillingOnFormServiceGridComposer` |
| `*Validator` | `@Service` / `@Component` | Pure validation → typed `Result(messages, codeValid)`. No side effects. | `BillingOnReviewValidator` |
| `*Parser` | plain class or `@Service` | Parses fixed-format/file input into DTO records. No Struts request handling. | `BillingClaimsErrorReportParser` |
| `*ImportService` | `@Service` | Validates, parses, and persists externally supplied files. | `OnRaImportService` |
| `*Persister` | `@Service @Transactional` | **Side-effect-only writer** split out from a sibling reader. | `BillingOnReviewDiagPersister` |
| `*Calculator` | static or dependency-free `@Service` | Pure math/derivation. Typed in, typed out. No DAO ownership. | `BillingOnHistoryBalanceCalculator` |
| `*Service` | `@Service` (often `@Transactional`) | **Default fallback.** Multi-step business operation no single verb captures, including cross-DAO read orchestration. | `BillingOnHeaderCreationService`, `GstSettingsService` |
| `*Dao` | `@Repository` | Data access for one entity/table. **No cross-DAO calls.** | `BillingONCHeader1Dao` |

Plus utility classes — static-only, no Spring annotation. Use a domain noun (plural is fine):

| Pattern | Example |
|---|---|
| Static helpers, dependency-free | `BillingDateOfBirths`, `BillingDomIdTokens` |

**Don't** use `*Utils` or `*Helper` — those names attract clutter; a domain noun keeps focus.

## Decision rules — first match wins

1. **Does it expose a Struts2 URL?** → `*Action`
2. **Does it have zero deps + only static methods?** → utility class (no suffix; domain noun)
3. **Does it return a `*ViewModel` for one specific JSP?** → `*ViewModelAssembler`
4. **Does it write / mutate state / call across DAOs?**
   - …as a side-effect-only sibling of a reader → `*Persister`
   - …as the main verb of a single business op → `*Service`
5. **Does it produce read-only state without writing?**
   - …one slice onto someone else's builder → `*Loader`
   - …a complex sub-structure → `*Composer`
   - …one decision through a priority chain → `*Resolver`
   - …pure math → `*Calculator`
   - …pure validation result → `*Validator`
   A loader/composer/resolver may have one caller when it names a cohesive
   slice and keeps the top-level assembler readable; the suffix is about role,
   not reuse count.
6. **Does it do data access on one entity?** → `*Dao` (and only that — no cross-DAO calls)
7. **Otherwise** → `*Service`

## Forbidden / retired suffixes

- **`*Prep`** — not a role, not a lifecycle. Use `*Loader` for read-side prep, `*Service` for write-side prep, or absorb into the consumer.
- **`*DataAssembler`** — retired in Ontario billing. It was accurate during the JSP-scriptlet cleanup, but `*ViewModelAssembler` now says the real output type directly.
- **`*Manager`** as a new class. Existing `*Manager` classes in the codebase (e.g., `DemographicManager`) are legacy domain managers; don't add new ones. Use `*Service` for new code.
- **`*Bean` / `*Handler`** for module-owned classes. Use `*Dto` for transfer state and `*Parser`, `*ImportService`, or `*Service` for behavior.
- **`*Helper`, `*Utils`** — too generic. Use a domain noun for utility classes.
- **Compound suffixes** — `*LoaderService`, `*ServiceManager`, `*ResolverService`, etc. The annotation carries the infrastructure role; the suffix carries the conceptual role. Doubling up is noise. `*ImportService` is the explicit exception for workflows that validate, parse, and persist externally supplied files.

## Cross-DAO orchestration

A `*Dao` may not inject other DAOs. If a query needs another entity's data, the orchestration goes in a `*Service`. The DAO method is removed or split.

This rule eliminates a common drift pattern where DAOs grow business logic ("just one cross-table calc") and become god classes.

## When in doubt

Ask "what's the verb?":
- A reader that builds view state → `*ViewModelAssembler` (per JSP) or `*Loader` / `*Composer` / `*Resolver` (per slice).
- A writer that does one thing → `*Service`.
- A side-effect-only writer paired with a reader → `*Persister`.
- A pure function on typed inputs → `*Calculator` or `*Validator`.
- A static utility → noun class, no suffix.

If multiple options fit, pick the **smallest** scope name. `*ViewModelAssembler` is bigger than `*Composer` is bigger than `*Loader`.

## Migration policy

When you touch an existing class with a non-conforming name (e.g., a `*Prep`):
- If the change is a one-line bug fix, leave the name.
- If you're modifying ≥30% of the class or its public API, rename in the same PR. Note the rename in the commit message.
- Bulk renames (everything-`*Prep`-at-once) need their own PR with no other changes.

The point is to drift toward conformance, not to gate every edit on a rename.
