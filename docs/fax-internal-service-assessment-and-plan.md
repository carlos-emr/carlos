# Fax Internal Service Assessment and Implementation Plan (Framework-Aware Revision)

## Objective
Deliver a robust fax subsystem that supports:
1. **direct provider APIs** (starting with SRFax),
2. **existing middleware relay mode** (backward compatible), and
3. reliable **send + status + inbound polling/import** execution with strong tests.

This revision is aligned to this repository’s actual platform constraints (Struts 6.8, Spring 5.3.x, Hibernate 5.6.x, CXF JAX-RS/JAX-WS, javax namespace).

---

## Project-context constraints that must shape implementation

### 1) Service exposure pattern in this codebase
- The project already exposes REST APIs primarily via **CXF JAX-RS service beans** declared in `spring_ws.xml` under `/rs`.
- While Struts 6.8 REST is available in principle, introducing a second REST stack for fax admin/control APIs would add avoidable operational complexity.

**Recommendation:** Prefer CXF JAX-RS for new fax REST endpoints unless there is a specific UI/action-only requirement. Keep Struts actions only where legacy UI flow requires them.

### 2) Spring/Hibernate composition pattern
- DAOs are Spring repositories extending `AbstractDaoImpl` and using JPA/Hibernate style query patterns.
- Managers/services are Spring-managed (`@Service`) and are already the right seam for business orchestration.

**Recommendation:** Put fax orchestration into Spring services with constructor injection; DAOs remain persistence adapters.

### 3) Existing anti-patterns to remove incrementally
- `SpringUtils.getBean(...)` lookups in core flow classes and Struts actions.
- `new` instantiation for core workers from scheduler.
- Static mutable scheduler state (`isRunning`, timer fields).
- Mixed/legacy HTTP client usage (`DefaultHttpClient` + CXF + old Apache patterns).

**Recommendation:** remove from core path first, then clean up action-layer access progressively.

---

## Current fax implementation: key findings

### A. Good foundations already present
- Domain and persistence entities exist: `FaxConfig`, `FaxJob`, `FaxClientLog`.
- Functional pipeline already exists conceptually: send (`FaxSender`), status (`FaxStatusUpdater`), inbound import (`FaxImporter`).
- Security improvement exists for encrypted credential fields in `FaxConfig`.

### B. High-impact issues blocking “internal provider service” goal
1. **Transport is hard-coded to middleware URL conventions** (`/fax/...`) in multiple classes.
2. **No provider capability model** in config (no explicit provider type/capabilities).
3. **Core path is not DI-friendly** (manual bean lookups and object creation).
4. **Scheduler reliability semantics are weak** (lifecycle/running state management).
5. **Client stack inconsistency** and legacy HTTP APIs.
6. **Known defects in file handling logic** (string replacement and extension checks).
7. **Coverage is insufficient** for provider-level and orchestration-level confidence.

---


## Decision: is the current plan a concern?

Short answer: **the direction is correct, but it should be implemented as a Spring-service-first refactor, not a controller-stack rewrite.**

- **Concern level:** moderate if implemented as a broad framework change; low if implemented as incremental Spring service layering.
- **Best fit update:** keep Struts + CXF entry points, add Spring fax orchestration/services underneath.
- **Why this is better here:** it removes core anti-patterns (`SpringUtils.getBean`, scheduler static state, transport coupling) without forcing risky UI/API rewrites.

## Target architecture (compatible with this repo)

### 1) Core abstraction layer
Introduce provider abstraction:
- `FaxProviderClient` (interface)
  - `send(OutboundFaxPayload)`
  - `getStatus(providerJobId)`
  - `pollInbound(...)`
  - `downloadInbound(...)`
  - `deleteInbound(...)`
  - optional `cancel(providerJobId)` for provider-capable cancellation

Implementations:
- `MiddlewareFaxProviderClient` (preserve current relay behavior)
- `SRFaxProviderClient` (new direct provider adapter)

### 2) Provider routing and config model
Extend `FaxConfig` safely:
- `providerType` enum (e.g., `MIDDLEWARE`, `SRFAX`)
- optional provider settings (JSON text column or explicit columns for first provider)
- capability flags where needed (supports polling, supports cancel, etc.)

Backward compatibility rule:
- existing rows default to `MIDDLEWARE` in migration path.

### 3) Orchestration service
Add Spring service boundary, e.g. `FaxTransportService`:
- `processOutboundQueue()`
- `reconcileStatuses()`
- `pollAndImportInbound()`

This service should contain orchestration only, delegating transport details to provider clients and persistence to DAOs.

### 4) Scheduler redesign (without breaking operations)
Replace direct worker construction with DI:
- Scheduler bean injects `FaxTransportService`.
- Remove static mutable state and use bean lifecycle state + health metrics/status DTO.
- Keep `restart` behavior for admin but with deterministic stop/start semantics.

### 5) Endpoint strategy for control/diagnostics
For API control/status endpoints:
- Prefer **CXF JAX-RS service** under existing `/rs` server wiring in `spring_ws.xml`.
- Keep Struts actions for existing JSP/admin pages; migrate only if/when useful.

---

## Anti-pattern-aware implementation sequence

### Phase 0 — correctness fixes first (small and safe)
1. Fix current fax file/path logic defects.
2. Add null/invalid config guardrails.
3. Add regression tests for those exact defects.

### Phase 1 — DI refactor with zero behavior change
1. Convert `FaxSender`, `FaxImporter`, `FaxStatusUpdater` into Spring services with constructor injection.
2. Replace `SpringUtils.getBean` usage in these classes.
3. Update scheduler to injected orchestration service calls.

### Phase 2 — provider adapter seam
1. Add `FaxProviderClient` contract + mapping DTOs.
2. Implement middleware adapter that preserves legacy behavior.
3. Route existing send/import/status calls through adapter dispatch.

### Phase 3 — SRFax direct adapter
1. Implement SRFax client/auth/request mapping.
2. Map SRFax statuses to internal `FaxJob.STATUS` transitions.
3. Add adapter contract tests with mock HTTP responses.

### Phase 4 — inbound reliability and idempotency
1. Add stable inbound dedupe key strategy (provider message ID + hash fallback).
2. Make download/delete retries bounded and explicit.
3. Ensure failed imports are diagnosable and retryable without duplication.

### Phase 5 — operational hardening
1. Structured logs with correlation IDs (fax job ID + provider reference).
2. Metrics on queue depth, send latency, failures by provider, poll/import outcomes.
3. Admin diagnostics endpoint for scheduler and provider health.

---

## Test strategy (what “working successfully without errors” means here)

### Unit tests
- Provider routing by `providerType`.
- Status transition mapping per provider.
- File/path normalization and validation behavior.
- Scheduler/orchestration decision logic and error-handling paths.

### Spring component tests
- Outbound queue processing with in-memory DB and mocked provider clients.
- Inbound poll/download/import queue-linking flow.
- Status reconciliation flow including partial failures.

### Adapter contract tests
- Middleware adapter request/response compatibility against current relay protocol.
- SRFax adapter request/auth/poll/status mapping.

### Regression tests
- Existing middleware-only deployments continue functioning.
- Existing `FaxConfig` data remains valid post-migration.
- Restart/cancel/resend behavior remains intact (or improved) in admin paths.

---

## Practical coding standards for this initiative

1. **No hidden bean lookups in core flow** (`SpringUtils.getBean` prohibited there).
2. **No static mutable scheduler state** for runtime truth.
3. **One HTTP client approach per adapter** with explicit timeout configuration.
4. **Provider adapters contain protocol details only**; orchestration service owns workflow.
5. **Keep schema changes backward-compatible** and migration-safe.

---

## Definition of done
- Middleware mode remains supported and tested.
- SRFax direct mode supports send + status + inbound poll/import.
- Scheduler lifecycle is deterministic and observable.
- Core fax flow is DI-managed and testable.
- Tests cover unit + component + adapter contracts with passing CI.
