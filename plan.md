# Plan: Migrate CARLOS EMR to Tomcat 11 + Spring 7

## Current State → Target State

| Component | Current | Target | Jakarta EE |
|---|---|---|---|
| Tomcat | 10.1 (EE 10) | 11.0 (EE 11) | 11 |
| Spring Framework | 6.2.17 | 7.0.6 | 11 |
| Spring Security | 6.3.9 | 7.x | 11 |
| Jakarta Servlet API | 6.0.0 | 6.1.0 | 11 |
| Jakarta JSP API | 3.1.0 | 4.0.0 | 11 |
| JSTL (Glassfish) | 3.0.1 | 4.0.0 | 11 |
| Hibernate | 6.6.40 | 6.6.40 (keep) | — |
| Struts | 7.1.1 | 7.1.1 (keep) | — |
| Java | 21 | 21 (keep) | — |
| JUnit 5 | 5.10.1 | 5.11.x+ | — |

## Pre-requisites (all met)

- Java 21 (Spring 7 requires 17+, Tomcat 11 requires 17+)
- Jakarta EE namespace migration complete (all `javax.*` → `jakarta.*`)
- Struts 7.1.1 already uses `org.apache.struts2.*` namespace
- JUnit 4 → 5 migration complete (PR #591, commit e78a564b):
  - 334 legacy tests migrated to JUnit 5 in `src/test-modern/` (now 450 files)
  - `src/test/java/` cleared — zero Java files remain
  - 24 deferred tests (Selenium UI, MCEDT, REST webserv) removed, tracked for future recreation
  - `junit:junit:4.13.2` dependency to be removed from `pom.xml` as part of this migration

## Migration Steps

### Phase 1: Jakarta EE 11 API Version Bumps

Update `pom.xml` dependency versions:

```xml
<!-- Servlet API 6.0.0 → 6.1.0 -->
<artifactId>jakarta.servlet-api</artifactId>
<version>6.1.0</version>

<!-- JSP API 3.1.0 → 4.0.0 -->
<artifactId>jakarta.servlet.jsp-api</artifactId>
<version>4.0.0</version>

<!-- JSTL 3.0.1 → 4.0.0 (if available, or verify 3.0.1 compat) -->
<artifactId>jakarta.servlet.jsp.jstl</artifactId>
<version>4.0.0</version>

<!-- Annotation API 2.1.1 → 3.0.0 -->
<artifactId>jakarta.annotation-api</artifactId>
<version>3.0.0</version>

<!-- Inject API 2.0.1 → keep (no change for EE 11) -->
```

**Risk**: JSP 4.0 may have breaking changes in EL expression handling. All JSPs need smoke testing.

### Phase 2: Spring Framework 7.0.6

1. Update BOM in `pom.xml`:
   ```xml
   <artifactId>spring-framework-bom</artifactId>
   <version>7.0.6</version>
   ```
2. Update Spring Security:
   ```xml
   <artifactId>spring-security-crypto</artifactId>
   <version>7.x.x</version>  <!-- match Spring 7 compatible release -->
   ```
3. Check for removed/changed Spring APIs:
   - `PersistenceAnnotationBeanPostProcessor` (already removed in our codebase ✓)
   - Any deprecated Spring 6.x APIs that are removed in 7.0
4. Update JUnit 5 to 5.11.x+ for Spring 7 compatibility
5. Remove `junit:junit:4.13.2` dependency (no longer needed post-migration)
6. Build and fix compilation errors

### Phase 3: Tomcat 11 Container

1. Update Dockerfile base image:
   ```dockerfile
   FROM tomcat:11.0-jdk21-temurin
   ```
2. Review Tomcat `server.xml` for:
   - `maxParameterCount` default changed from 10,000 → 1,000 (may need explicit override for healthcare forms with many fields)
   - SecurityManager removal (no action needed)
   - Cookie parsing changes (RFC 6265 stricter)
3. Update `pom.xml` Tomcat embed dependencies if any exist
4. Test deployment in devcontainer

### Phase 4: Verification & Testing

1. `make clean && make install --run-tests` — all tests must pass
2. Run UI test suite: `/test-fullsuite` (all 9 Playwright tests)
3. Verify all 458 Struts 2Action mappings work
4. Check OWASP CSRFGuard 4.5 compatibility with new Servlet API
5. Verify CXF 4.1.5 works with Spring 7 (may need CXF upgrade)
6. Test Drools 10.0.0 with Spring 7
7. Test HAPI FHIR 6.10.5 with Spring 7

## Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| JSP 4.0 EL breaking changes | **Medium** | Full JSP smoke test suite |
| Spring Security 7.x API changes | **Medium** | Only using `spring-security-crypto`, low surface |
| CXF 4.1.5 incompatibility | **Medium** | May need CXF 4.2.x+ |
| OWASP CSRFGuard Servlet 6.1 | **Low** | Already Jakarta-aware (4.5-jakarta) |
| Hibernate 6.6 with JPA 3.2 | **Low** | Works, just misses new JPA 3.2 features |
| Struts 7.1.1 with Servlet 6.1 | **Low** | Already Jakarta EE 11 aligned |

## Out of Scope (Future Work)

- **Hibernate 7.x upgrade**: Not required for Spring 7 but recommended for full JPA 3.2. Separate effort due to potential schema/HQL changes.
- **DrugRef container** (still on Tomcat 9 / Java 11): Separate migration.
- **Struts 1.x removal**: Legacy Struts 1 dependencies still exist for Velocity Tools. Separate cleanup.
- **Deferred test recreation**: 24 tests (Selenium UI, MCEDT, REST) removed during JUnit 5 migration, to be recreated in modern framework.

## Estimated Effort

- **Phase 1 (API bumps)**: Small — `pom.xml` changes + JSP smoke testing
- **Phase 2 (Spring 7)**: Medium — BOM update + fix any removed API usage + remove JUnit 4 dep
- **Phase 3 (Tomcat 11)**: Small — Dockerfile change + config review
- **Phase 4 (Verification)**: Medium — comprehensive testing across all modules
