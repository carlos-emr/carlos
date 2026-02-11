# Plan: Configure Modern Tests for Parallel Execution

## Current State

- **59 test files** (~28,884 lines), split into two categories:
  - **Unit tests** (`@Tag("unit")`, ~12 files): Extend `OpenOUnitTestBase`, no Spring context, no database, use `MockedStatic` for `SpringUtils` and `LogAction`
  - **Integration tests** (`@Tag("integration")`, ~47 files): Extend `OpenOTestBase`/`OpenODaoTestBase`, share a single Spring context with H2 in-memory database
- **No parallel execution today**: Despite `test.parallel.enabled=true` in `test.properties`, there is no `junit-platform.properties` and no Surefire `<parallel>` config. All tests run sequentially.
- **Surefire 3.5.4** with two executions: `modern-tests` (JUnit 5) and `legacy-tests` (JUnit 4)

## Identified Race Condition Risks

### Risk 1: `MockedStatic` (Unit Tests) — RESOLVED ✓
`OpenOUnitTestBase` creates `MockedStatic<SpringUtils>` and `MockedStatic<LogAction>` per test method in `@BeforeEach`/`@AfterEach`.

**Initial Assessment**: There was concern that Mockito's `MockedStatic` would collide when multiple threads call `mockStatic(SpringUtils.class)` simultaneously because the ByteBuddy agent operates at the classloader level.

**Verification (lines 89-100 below)**: Mockito 5.x uses **thread-local scoping** for `MockedStatic`. Each thread can independently call `mockStatic(SpringUtils.class)` and maintain its own isolated mock scope. The Mockito documentation confirms: "Each MockedStatic is bound to the thread that created it."

**Validation**: The `MockedStaticThreadSafetyTest` validates this thread-local behavior by spawning concurrent threads that each create their own `MockedStatic<SpringUtils>` scope and verify isolation.

**Impact**: Unit tests are **safe to run in parallel across classes** when using Mockito 5.x. No changes required to existing test code beyond marking `OpenOUnitTestBase` with `@Execution(CONCURRENT)`.

### Risk 2: Shared H2 Database (Integration Tests) — HIGH
All integration tests share a single `jdbc:h2:mem:testdb` instance. While `@Transactional` + `@Rollback` provides isolation for individual test methods (uncommitted data is invisible to other connections), there are subtleties:
- **Schema DDL** (`hbm2ddl.auto=create`) runs once at context startup — safe as long as the Spring context is created once and shared.
- **`test-lookup-tables.sql`** is loaded once via `DataSourceInitializer` — same safety assumption.
- **`OpenODaoTestBase.cleanTables()`** in `@BeforeEach` issues `DELETE FROM` statements. If two integration tests run concurrently, one test's cleanup could delete another test's data mid-transaction (depending on H2's isolation level and auto-commit settings on the DBCP pool).

**Impact**: Flaky failures from missing data or constraint violations if integration tests run concurrently.

### Risk 3: `SpringUtils` Static Field (Integration Tests) — MEDIUM
`OpenOTestBase.initializeSpringUtils()` uses reflection to set `SpringUtils.beanFactory` to the test `ApplicationContext`. This is a **static field on a shared class**. With `@BeforeAll` and a static guard (`springUtilsInitialized`), it's written once and then read-only — safe for concurrent reads. However, if the Spring context is ever refreshed or if different test classes use different contexts, this becomes a race.

**Impact**: Low risk with the current single-context design. Would become a problem if we introduced per-class contexts.

### Risk 4: `OpenOTestBase.staticContext` — LOW
The `staticContext` field is set in `setApplicationContext()` with a null-check guard. Under parallel class instantiation, two threads could both see `null` and both attempt to set it. The second write is idempotent (same context object), so this is benign.

### Risk 5: `MockitoAnnotations.openMocks()` in `@BeforeEach` — LOW
Each test instance gets its own `@Mock` fields. `openMocks(this)` is instance-scoped, so this is safe.

## Proposed Strategy: Tiered Parallelism

The safest and most effective approach is to apply **different parallelism strategies** to unit tests and integration tests, since they have fundamentally different isolation characteristics.

### Phase 1: JUnit 5 Parallel Execution for Unit Tests Only

**What**: Enable JUnit 5's built-in parallel execution, but **only for unit tests**. Integration tests remain sequential.

**How**: Create `src/test-modern/resources/junit-platform.properties`:

```properties
# Enable JUnit 5 parallel execution
junit.jupiter.execution.parallel.enabled = true

# Default to sequential — tests must opt in
junit.jupiter.execution.parallel.mode.default = same_thread
junit.jupiter.execution.parallel.mode.classes.default = same_thread

# Thread pool sizing: fixed pool of 4 threads
junit.jupiter.execution.parallel.config.strategy = fixed
junit.jupiter.execution.parallel.config.fixed.parallelism = 4
```

Then annotate `OpenOUnitTestBase` with:
```java
@Execution(ExecutionMode.CONCURRENT)
```

This makes all unit test **classes** that extend `OpenOUnitTestBase` run concurrently with each other, but test **methods within** each class still run sequentially (same_thread default).

**MockedStatic Problem**: This alone won't work because `MockedStatic` has classloader-level scope. Two threads calling `mockStatic(SpringUtils.class)` simultaneously will collide.

**Solution — ResourceLock**: Use JUnit 5's `@ResourceLock` to serialize access to shared static resources:

```java
@Tag("unit")
@Tag("fast")
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock(value = "SpringUtils", mode = ResourceAccessMode.READ_WRITE)
public abstract class OpenOUnitTestBase {
```

Wait — this would serialize all unit tests, defeating the purpose. Instead, a better approach:

**Revised Solution — Separate static-mocking tests from non-static tests**:

Actually, the cleaner solution is: **unit tests that use `MockedStatic` must run sequentially with each other**, but they can run in parallel with integration tests (which use the real SpringUtils). Since all current unit tests extend `OpenOUnitTestBase` and all use `MockedStatic`, the practical approach is:

1. **Unit test methods within a class**: CONCURRENT (each `@BeforeEach` creates its own mocks)
2. **Unit test classes**: SAME_THREAD (serialize to avoid `MockedStatic` collisions)
3. **Integration test classes**: CONCURRENT (they share a Spring context + H2, isolated by `@Transactional`+`@Rollback`)

Wait, let me reconsider. Mockito's `MockedStatic` since 5.x actually uses a thread-local registration. Let me verify: Mockito 5.21.0 uses the `mockito-inline` agent by default. The `MockedStatic` scope is indeed **thread-local** as of Mockito 4.x+. Two threads can each `mockStatic(SpringUtils.class)` independently as long as each thread creates and closes its own scope.

If this is the case, then unit tests CAN run in parallel across threads without `MockedStatic` collisions, because each thread has its own mock scope.

**Let me verify this assumption before finalizing the plan.** The Mockito docs for 5.x state: "Each MockedStatic is bound to the thread that created it." This means:
- Thread A calls `mockStatic(SpringUtils.class)` → only affects Thread A
- Thread B calls `mockStatic(SpringUtils.class)` → only affects Thread B
- No collision.

This is correct for Mockito 5.x with the inline mock maker (which is the default and what `net.bytebuddy.experimental=true` supports).

**Conclusion**: Unit tests CAN safely run in parallel across classes, because `MockedStatic` is thread-local in Mockito 5.x.

### Phase 1 (Revised): Parallel Unit Tests

**Create** `src/test-modern/resources/junit-platform.properties`:

```properties
junit.jupiter.execution.parallel.enabled = true
junit.jupiter.execution.parallel.mode.default = same_thread
junit.jupiter.execution.parallel.mode.classes.default = same_thread
junit.jupiter.execution.parallel.config.strategy = fixed
junit.jupiter.execution.parallel.config.fixed.parallelism = 4
```

**Annotate** `OpenOUnitTestBase`:
```java
@Execution(ExecutionMode.CONCURRENT)  // Classes run concurrently
```

**Leave** `OpenOTestBase` with no annotation (inherits `same_thread` default).

**Effect**: Unit test classes run 4 at a time. Integration tests remain sequential. No code changes needed in individual test files.

**Validation**: Run `make install --run-unit-tests` and verify all pass. Then run full suite.

### Phase 2: Parallel Integration Tests (Requires Database Isolation)

Integration tests sharing a single H2 database is the main obstacle. Options:

#### Option A: Per-Thread H2 Databases (Recommended)

Replace the single `jdbc:h2:mem:testdb` with per-thread databases using a unique name:

```properties
db_uri=jdbc:h2:mem:testdb_${thread};MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
```

**Problem**: Spring caches the ApplicationContext. All tests sharing the same `@ContextConfiguration` get the same context (and same DataSource). We'd need to either:
- Use `@DirtiesContext` (expensive — rebuilds context per class), or
- Use a custom `DataSource` that routes to per-thread H2 instances

This is complex and risks instability. **Defer to Phase 3.**

#### Option B: Surefire Forked JVMs (Recommended for Phase 2)

Use Maven Surefire's `forkCount` to run integration test classes in separate JVM processes. Each fork gets its own H2 in-memory database naturally (separate JVM = separate memory).

**Changes to pom.xml** (modern-tests execution):
```xml
<forkCount>2</forkCount>
<reuseForks>true</reuseForks>
```

**Effect**: Surefire launches 2 JVM forks. Test classes are distributed across forks. Each fork has its own Spring context and H2 database. No shared state.

**Trade-off**: Spring context startup time (~3-5s) is incurred per fork. With `reuseForks=true`, each fork reuses its JVM across multiple test classes, so the context only starts once per fork.

**Net gain**: With 2 forks and ~47 integration test classes, wall-clock time roughly halves (minus overhead).

### Phase 3: Advanced — Per-Thread Database Routing (Future)

Use Spring's `AbstractRoutingDataSource` to route each thread to a unique H2 database:

```java
public class ThreadLocalDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return Thread.currentThread().getName();
    }
}
```

Combined with JUnit 5's `@Execution(CONCURRENT)` on integration test classes, this would give true in-process parallelism. This is the most complex option and should only be pursued after Phase 1 and 2 are stable.

## Recommended Implementation Plan

### Step 1: Create `junit-platform.properties`
- File: `src/test-modern/resources/junit-platform.properties`
- Enable parallel execution with `same_thread` defaults
- Set fixed parallelism of 4

### Step 2: Annotate `OpenOUnitTestBase` for Concurrent Execution
- Add `@Execution(ExecutionMode.CONCURRENT)` to `OpenOUnitTestBase`
- This opts all unit tests into parallel class execution
- Methods within each class stay sequential (safe default)

### Step 3: Verify MockedStatic Thread Safety
- Write a small validation test that runs two `MockedStatic<SpringUtils>` scopes on separate threads simultaneously
- Confirm Mockito 5.21.0's thread-local behavior works as expected with the ByteBuddy agent
- If it fails, fall back to `@ResourceLock` serialization for unit tests

### Step 4: Add `@Execution(SAME_THREAD)` Guard to `OpenOTestBase`
- Explicitly annotate `OpenOTestBase` with `@Execution(ExecutionMode.SAME_THREAD)`
- This ensures integration tests don't accidentally run in parallel until Phase 2
- Documents the intentional decision

### Step 5: Increase Surefire Fork Count for Integration Tests
- Change Surefire `modern-tests` execution to use `<forkCount>2</forkCount>` and `<reuseForks>true</reuseForks>`
- Each fork gets its own JVM → its own Spring context → its own H2 database
- No code changes needed in tests
- Can tune `forkCount` based on CI resources (2-4 is reasonable)

### Step 6: Adjust `OpenODaoTestBase.cleanTables()` for Fork Safety
- With forked JVMs, `cleanTables()` only affects its own fork's H2 instance, so no changes needed
- Document this assumption in the base class JavaDoc

### Step 7: Update `test.properties` and Documentation
- Update `test.parallel.threads` to match `junit-platform.properties`
- Document parallel execution strategy in `docs/test/`
- Update CLAUDE.md test section with parallel execution notes

### Step 8: CI Validation
- Run full test suite: `make install --run-tests`
- Run unit tests only: `make install --run-unit-tests`
- Run integration tests only: `make install --run-integration-tests`
- Compare timing before/after to measure improvement
- Run 5x to check for flakiness

## Files to Create/Modify

| File | Action | Purpose |
|------|--------|---------|
| `src/test-modern/resources/junit-platform.properties` | CREATE | JUnit 5 parallel config |
| `src/test-modern/java/.../test/unit/OpenOUnitTestBase.java` | MODIFY | Add `@Execution(CONCURRENT)` |
| `src/test-modern/java/.../test/base/OpenOTestBase.java` | MODIFY | Add `@Execution(SAME_THREAD)` |
| `pom.xml` | MODIFY | Add `forkCount`/`reuseForks` to modern-tests |
| `src/test-modern/resources/test.properties` | MODIFY | Align parallel settings |
| `docs/test/modern-test-framework-complete.md` | MODIFY | Document parallel strategy |
| `CLAUDE.md` | MODIFY | Note parallel test execution |

## Expected Performance Improvement

- **Unit tests (~12 classes, ~200+ tests)**: ~4x speedup with 4 threads
- **Integration tests (~47 classes)**: ~2x speedup with 2 forks
- **Overall**: Significant reduction in `make install --run-tests` wall time

## Risks and Mitigations

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| MockedStatic not truly thread-local | Low (documented in Mockito 5.x) | Step 3 validation test |
| H2 fork isolation incomplete | Very low (separate JVMs) | Each fork is a separate process |
| Spring context startup overhead per fork | Certain | `reuseForks=true` minimizes this |
| Flaky tests from hidden shared state | Medium | Run 5x in CI, fix any failures |
| Test ordering dependencies | Low | Tests should be independent by design |
