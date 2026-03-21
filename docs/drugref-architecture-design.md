# DrugRef Architecture Design

## Executive Summary

DrugRef is a drug reference database providing drug lookups, ATC code resolution, drug-drug interaction checking, and allergy warnings for the CARLOS EMR prescription module. It currently runs as a **separate Docker container** (Java 11, Tomcat 9) communicating with CARLOS (Java 21, Tomcat 11) via **unauthenticated XML-RPC over HTTP**.

This document analyzes DrugRef's history, current architecture, security concerns, and recommends a modernization path: **absorbing DrugRef as a CARLOS Spring service**, eliminating the separate container and all associated security gaps.

---

## 1. Historical Context

### How DrugRef Evolved

DrugRef was **never a compiled JAR embedded in the main application**. Since its creation in 2003, it has always been a **separate web application** communicating via XML-RPC:

- **2003 (Original OSCAR)**: DrugRef deployed as a separate WAR on the same Tomcat instance. `RxDrugRef.java` (`@since 2003-09-19`) was always a network client calling a remote XML-RPC service at a URL configured via the `drugref_url` property. The original deployment config shows: `drugref_url=http://localhost:8080/drugref/DrugrefService`

- **Open-Drugref era**: The DrugRef server was extracted into a standalone project ([open-osp/Open-Drugref](https://github.com/open-osp/Open-Drugref)) with its own database (`drugref2`), populated from Health Canada's Drug Product Database (DPD) extracts. It ran as an independent web service.

- **CARLOS migration to Java 21**: When CARLOS upgraded to Java 21 + Jakarta EE (Tomcat 11), Open-Drugref could not follow. Open-Drugref remains on Java 11 + `javax.*` namespace (Tomcat 9). The incompatibility is at the Jakarta EE namespace level, not just Java version. This forced DrugRef into a **separate Docker container**.

- **February 2026**: The old Apache `xmlrpc:xmlrpc:1.2-b1` Maven dependency was also incompatible with Java 21 and was removed. A custom `SimpleXmlRpcClient.java` was written using Java 21's built-in `HttpClient` to replace it.

### What Was Removed vs. What Remains

- **MyDrugRef** (a separate clinical decision support service providing guidelines, bulletins, and treatment recommendations) has been **removed** from CARLOS.
- **DrugRef** (the core drug reference database for lookups, interactions, and allergy warnings) **remains active** and is critical to the prescription module.

---

## 2. Current Architecture

### Container Topology

```
┌─────────────────────────────┐     ┌──────────────────────────┐
│  CARLOS EMR                 │     │  DrugRef                 │
│  carlos-tomcat-dev          │     │  carlos-drugref-dev      │
│  Java 21 / Tomcat 11       │     │  Java 11 / Tomcat 9      │
│  Port: 8080                 │     │  Port: 8180 (→8080)      │
│                             │     │                          │
│  RxDrugRef.java ───XML-RPC──┼────►│  DrugrefService          │
│  SimpleXmlRpcClient.java    │     │  (SOAP/XML-RPC endpoint) │
└──────────┬──────────────────┘     └────────┬─────────────────┘
           │                                  │
           │   carlos-network (bridge)        │
           │                                  │
           └──────────┬───────────────────────┘
                      │
                      ▼
           ┌─────────────────────┐
           │  MariaDB            │
           │  carlos-mariadb-dev │
           │  Port: 3306         │
           │                     │
           │  oscar DB (CARLOS)  │
           │  drugref2 DB        │
           └─────────────────────┘
```

### Communication Protocol

- **Protocol**: XML-RPC 1.0 over HTTP (no authentication, no TLS)
- **Client**: `RxDrugRef.java` (813 lines, ~30 public methods) → `SimpleXmlRpcClient.java` (Java 21 `HttpClient`)
- **Config**: `drugref_url` property in `carlos.properties`
  - Dev: `http://drugref:8080/drugref2/DrugrefService`
  - Production: `http://67.69.12.116:8001`

### DrugRef Database Schema (drugref2)

17 tables, ~560,000 records total. Key tables:

| Table | Records | Purpose |
|-------|---------|---------|
| `cd_drug_product` | ~38,851 | Drug product master records (DIN, brand names) |
| `cd_active_ingredients` | ~97,850 | Active pharmaceutical ingredients |
| `cd_drug_search` | ~79,356 | Search index with drug categories |
| `cd_therapeutic_class` | ~43,901 | ATC/AHFS codes and classifications |
| `interactions` | ~5,264 | Drug-drug interaction matrix (ATC-based) |
| `cd_form` | ~47,799 | Pharmaceutical dosage forms |
| `cd_route` | ~48,271 | Routes of administration |
| `cd_companies` | ~43,731 | Pharmaceutical manufacturers |
| `link_generic_brand` | ~38,851 | Generic-brand relationships |
| `history` | 22 | Update audit trail |

Schema definition: `release/drugref.sql`
Data dump: `database/mysql/development-drugref.sql` (12.8 MB)

### Integration Surface

**23 files** in CARLOS depend on DrugRef, primarily through `RxDrugRef`:

| Component | File | Role |
|-----------|------|------|
| XML-RPC Client | `prescript/util/RxDrugRef.java` | Core DrugRef API client (~30 methods) |
| XML-RPC Transport | `prescript/util/SimpleXmlRpcClient.java` | HTTP + XML serialization |
| Data Models | `prescript/data/RxDrugData.java` | Parses untyped responses into domain objects |
| Interaction Cache | `prescript/data/RxInteractionData.java` | Static singleton cache (unbounded) |
| Async Interactions | `prescript/data/RxInteractionWorker.java` | Background thread for interaction checks |
| Async Allergies | `prescript/data/RxAllergyWarningWorker.java` | Background thread for allergy warnings |
| Spring Service | `managers/DrugLookUpManager.java` | REST API wrapper (`@Service`) |
| Drug Search UI | `prescript/pageUtil/RxSearchDrug2Action.java` | Struts2 action for drug search |
| Allergy Search | `prescript/pageUtil/RxSearchAllergy2Action.java` | Struts2 action for allergy search |
| Admin Update | `prescript/pageUtil/RxUpdateDrugref2Action.java` | Admin DB update trigger |
| Utilities | `prescription/util/DrugrefUtil.java` | Decision support message processing |
| REST API | `webserv/rest/RxLookupService.java` | JAX-RS endpoint `/rxlookup` |

### Key API Methods (grouped by function)

**Drug Search**: `list_drug_element()`, `list_drug_element2()`, `list_drug_element3()`, `list_drug_element_route()`, `list_search_element_select_categories()`

**Drug Lookup**: `getDrug()`, `getDrug2()`, `getDrugByDIN()`, `getGenericName()`, `getDrugForm()`

**ATC Resolution**: `atc()`, `atcFromDIN()`, `atcFromBrand()`, `atc2text()`, `drug2atclist()`, `druglist2atclist()`

**Interactions**: `interaction()` (2 overloads), `interaction_by_drugnames()`, `interactionByRegionalIdentifier()`, `getInteractions()`

**Allergy Warnings**: `getAlergyWarnings()`, `getAllergyClasses()`

**Health/Admin**: `verify()`, `getLastUpdateTime()`, `updateDB()`, `version()`, `identify()`

---

## 3. Security Risk Assessment

### HIGH: Unauthenticated XML-RPC Endpoint

The DrugRef XML-RPC service has **zero authentication**. Any process that can reach `drugref:8080` on the Docker network can:
- Call any of the ~40 methods
- Trigger `updateDB()` (full database refresh, takes ~1 hour, potential DoS)
- Call `suggestAlias()` to inject data

In production, the default URL (`http://67.69.12.116:8001`) suggests DrugRef may be accessible over the general network without any authentication.

### HIGH: Missing Privilege Check in Admin Action

`RxUpdateDrugref2Action.java` has **no `SecurityInfoManager.hasPrivilege()` check**. Any logged-in CARLOS user can trigger a full DrugRef database update, regardless of their role. This should require `_admin` privileges at minimum.

### MEDIUM: No TLS Between Containers

All XML-RPC calls go over plain HTTP. While drug reference data is not PHI (no patient data is sent to DrugRef):
- Drug lookups could reveal what conditions are being treated (indirect privacy concern)
- A network attacker could inject false interaction warnings or suppress real ones (patient safety risk)

### LOW: Unbounded Static Cache

`RxInteractionData` uses a static `Hashtable` singleton keyed by `Vector.hashCode()`:
- Hash collisions possible (incorrect interaction results)
- Never expires, grows unbounded (memory leak over long-running instances)
- Not thread-safe for concurrent modifications

---

## 4. Architecture Options Evaluated

### Option A: Fork & Migrate Open-Drugref Codebase

Upgrade the Open-Drugref code to Java 21 + Jakarta EE and embed it in CARLOS.

| Pros | Cons |
|------|------|
| Preserves all existing logic | Requires understanding a separate legacy codebase |
| One-to-one migration of behavior | Has its own Hibernate config, servlet architecture, build system |
| | Open-Drugref is just XML-RPC wrapping SQL queries -- no complex business logic worth preserving |

**Verdict: Rejected.** It is simpler to write fresh DAOs against the known schema than to migrate the entire server application.

### Option B: Keep Separate Container, Add Security

Maintain the container architecture but add authentication (API keys, mTLS, or JWT) and network policies.

| Pros | Cons |
|------|------|
| Minimal code changes | Perpetuates maintaining a Java 11 app that will only become harder to support |
| Isolated failure domain | XML-RPC is obsolete; adding mTLS adds complexity to what should be simplified |
| | Still need to maintain separate build pipeline, container image, healthchecks |

**Verdict: Rejected.** This approach invests effort in securing an architecture that should be simplified.

### Option C: Absorb as a CARLOS Spring Service (RECOMMENDED)

Create a new Spring service within CARLOS that directly accesses the `drugref2` database, replacing all XML-RPC calls with in-process method calls.

| Pros | Cons |
|------|------|
| Security solved by default (Spring Security, SecurityInfoManager) | Significant development effort (~270-350 hours) |
| No more unauthenticated network endpoint | Need to understand and map 17-table schema |
| No more XML-RPC attack surface | Data update mechanism needs reimplementation |
| Eliminates container dependency entirely | |
| Direct SQL is faster than XML-RPC round-trips | |
| Properly typed models instead of Hashtable/Vector | |
| CARLOS already has the database in the same MariaDB instance | |

**Verdict: Recommended.** The drugref2 schema is simple and stable. The integration surface is well-bounded (~30 methods). The database is already co-located. Security, caching, and testing all become simpler.

### Option D: Modernize as a REST Microservice

Rewrite DrugRef as a modern Java 21 REST service with OAuth2/API keys.

| Pros | Cons |
|------|------|
| Clean REST API | Over-engineering for a read-only lookup service |
| Independent scaling | No reason for separate deployment, build pipeline, monitoring |
| | Still a separate service to maintain |

**Verdict: Rejected.** DrugRef is a read-only reference database with 17 tables. The microservice overhead is not justified.

---

## 5. Recommended Migration Path

### Phase 1: Database Access Layer (Foundation)

**Goal**: Create a second datasource and DAOs for the `drugref2` database within CARLOS.

1. Add `drugrefDataSource` bean in Spring config pointing to `jdbc:mysql://db:3306/drugref2` (read-only connection pool)
2. Create JPA entities in `io.github.carlos_emr.carlos.drugref.model`:
   - `DrugProduct` (cd_drug_product)
   - `ActiveIngredient` (cd_active_ingredients)
   - `DrugSearch` (cd_drug_search)
   - `TherapeuticClass` (cd_therapeutic_class)
   - `Interaction` (interactions)
   - `DrugForm` (cd_form), `DrugRoute` (cd_route)
   - `LinkGenericBrand` (link_generic_brand)
   - `DrugHistory` (history)
3. Create DAOs in `io.github.carlos_emr.carlos.drugref.dao`

### Phase 2: Service Interface (API Replacement)

**Goal**: Create `DrugRefService` that replaces all `RxDrugRef` XML-RPC methods.

New Spring `@Service`: `io.github.carlos_emr.carlos.drugref.DrugRefService`

| Old XML-RPC Method | New Service Method |
|--------------------|--------------------|
| `list_drug_element*()` | `searchDrugs(String query, SearchOptions)` |
| `getDrug()`, `getDrug2()`, `getDrugByDIN()` | `getDrug(int code)`, `getDrugByDin(String din)` |
| `interaction()`, `interaction_by_drugnames()` | `getInteractions(List<String> atcCodes, int minSignificance)` |
| `get_allergy_warnings()` | `getAllergyWarnings(String atcCode, List<Allergy>)` |
| `atc()`, `atcFromDIN()`, `atcFromBrand()` | `resolveAtcCodes(String identifier)` |
| `verify()`, `getLastUpdateTime()` | `verify()`, `getLastUpdateTime()` |
| `updateDB()` | `updateDatabase()` (local DPD import with admin auth) |

### Phase 3: Adapter Layer (Backward Compatibility)

**Goal**: Enable incremental migration without breaking existing callers.

- Modify `RxDrugRef` to delegate to `DrugRefService` (local) or old XML-RPC based on feature flag (`drugref.provider=local` vs `drugref.provider=xmlrpc`)
- All 23 existing callers continue working unchanged during transition
- Side-by-side testing: run both paths and compare results

### Phase 4: Caller Migration & Modernization

**Goal**: Update callers to use `DrugRefService` directly with typed models.

- Inject `DrugRefService` via `SpringUtils.getBean()` in 2Action classes
- Replace `RxInteractionData` static singleton with Spring-managed Caffeine cache
- Replace bare `Thread` subclasses with Spring `@Async` or `CompletableFuture`
- **Add `SecurityInfoManager.hasPrivilege("_admin", "w")` to database update action** (currently missing)

### Phase 5: Cleanup

- Remove `SimpleXmlRpcClient.java`, `XmlRpcFaultException.java`
- Remove `drugref` service from `docker-compose.yml`
- Remove `.devcontainer/drugref/` directory
- Remove `drugref_url` property from `carlos.properties`
- Update CI workflow to stop building drugref container

---

## 6. Effort Estimate

| Phase | Effort | Notes |
|-------|--------|-------|
| Phase 1: DAOs | ~80-100 hours | 17 tables, straightforward schema |
| Phase 2: Service | ~100-120 hours | ~30 methods, query translation |
| Phase 3: Adapter | ~20-30 hours | Feature flag + delegation |
| Phase 4: Migration | ~60-80 hours | 23 caller files |
| Phase 5: Cleanup | ~10-20 hours | Container removal, config cleanup |
| **Total** | **~270-350 hours** | **~2-3 developer-months** |

Phases 1-3 can be done as a focused effort. Phases 4-5 can be spread across sprints.

---

## 7. Security Improvements Gained

After completing this migration:

- All drug reference operations go through Spring Security and `SecurityInfoManager`
- No more unauthenticated network endpoint
- No more XML-RPC attack surface (XML parsing, potential XXE)
- `updateDB()` gets proper admin privilege checks
- Input validation via typed service methods instead of untyped `Vector`/`Hashtable`
- Unbounded static cache replaced with TTL-managed Caffeine cache
- One fewer container to maintain, monitor, and secure

---

## 8. Key Considerations

### Data Integrity
- The `drugref2` database is populated from Health Canada's Drug Product Database (DPD) extracts
- The `updateDB()` functionality needs reimplementation as a local scheduled job that downloads and imports DPD data
- The `interactions` table contains Holbrook drug interaction data (separate from DPD)
- The `history` table tracks update timestamps

### Backward Compatibility
- The adapter pattern (Phase 3) ensures zero breakage during transition
- The `drugref2` schema stays identical -- no data migration needed
- The same SQL that populates drugref2 today continues to work

### Testing
- Phase 1-2: Integration tests using `CarlosTestBase` with H2 + drugref schema from `release/drugref.sql`
- Phase 3: Side-by-side comparison tests (XML-RPC vs local for same queries)
- Phase 4-5: Existing prescription UI tests to verify all drug features remain functional

### Performance
- Direct database queries will be faster than XML-RPC round-trips (eliminate serialization overhead)
- Spring-managed connection pooling replaces per-request HTTP connections
- Caffeine cache with TTL replaces the unsafe unbounded static singleton

---

*Document created: 2026-03-21*
