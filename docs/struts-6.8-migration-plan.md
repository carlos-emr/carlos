# Struts 6.8.0 Migration Plan

## Executive Summary

This document outlines the migration plan for upgrading CARLOS EMR from Apache Struts 2.5.33 to Struts 6.8.0. The experimental branch contains a prior migration attempt (pre-namespace migration) that provides valuable guidance but cannot be directly merged due to the OpenO → CARLOS namespace migration that occurred afterward.

**Key Statistics:**
- **Current Version**: Struts 2.5.33
- **Target Version**: Struts 6.8.0
- **2Action Classes**: 458 files requiring import changes
- **Action Mappings**: 500 mappings in struts.xml
- **Test Files**: OpenOWebTestBase requires ActionContext API migration
- **Estimated Scope**: ~500 files total

---

## Table of Contents

1. [Migration Overview](#1-migration-overview)
2. [Pre-Migration Checklist](#2-pre-migration-checklist)
3. [Phase 1: Dependency Updates](#phase-1-dependency-updates)
4. [Phase 2: ActionSupport Import Migration](#phase-2-actionsupport-import-migration)
5. [Phase 3: Struts Configuration Migration](#phase-3-struts-configuration-migration)
6. [Phase 4: ActionContext API Migration](#phase-4-actioncontext-api-migration)
7. [Phase 5: Deprecated Features Removal](#phase-5-deprecated-features-removal)
8. [Phase 6: File Upload Compatibility](#phase-6-file-upload-compatibility)
9. [Phase 7: DAO and Model Compatibility Fixes](#phase-7-dao-and-model-compatibility-fixes)
10. [Phase 8: Testing and Validation](#phase-8-testing-and-validation)
11. [Rollback Plan](#rollback-plan)
12. [Reference: Experimental Branch Changes](#reference-experimental-branch-changes)

---

## 1. Migration Overview

### What Changed Between Struts 2.5.x and 6.x

| Component | Struts 2.5.x | Struts 6.x | Impact |
|-----------|--------------|------------|--------|
| ActionSupport Package | `com.opensymphony.xwork2.ActionSupport` | `org.apache.struts2.ActionSupport` | All 458 2Action classes |
| ActionContext API | `ActionContext.getContext()` | `ActionContext.of()` builder pattern | Test framework |
| DTD Version | struts-2.3.dtd | struts-6.0.dtd | struts.xml |
| @Validation Annotation | Supported | Deprecated/Removed | Action classes |
| OGNL Version | 3.1.29 | 3.3.5 | Expression language |
| Caching | Built-in | Requires Caffeine 3.x | New dependency |

### Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Import change breaks compilation | Low | High | Automated bulk replacement |
| ActionContext test failures | Medium | Medium | Reference experimental branch patterns |
| File upload compatibility | Medium | High | Exclude servlet patterns from Struts |
| Runtime action routing errors | Low | High | Comprehensive testing |

---

## 2. Pre-Migration Checklist

Before starting the migration:

- [ ] **Create feature branch** from `develop`
- [ ] **Verify build passes** on current develop: `make install --run-tests`
- [ ] **Document current test pass rate** for comparison
- [ ] **Review experimental branch** commits for guidance:
  - `e267c1286` - Main migration commit
  - `f0b881925` - ActionSupport import replacement
  - `b1a11c681` - File upload fixes
  - `31473feb4` - Switch statement fixes
  - `3e174d6dc` - Security and DAO fixes
- [ ] **Backup database** (if testing against real data)
- [ ] **Notify team** of migration in progress

---

## Phase 1: Dependency Updates

### 1.1 Update pom.xml Struts Dependencies

**File**: `pom.xml`

```xml
<!-- BEFORE -->
<dependency>
    <groupId>org.apache.struts</groupId>
    <artifactId>struts2-core</artifactId>
    <version>2.5.33</version>
</dependency>
<dependency>
    <groupId>org.apache.struts</groupId>
    <artifactId>struts2-spring-plugin</artifactId>
    <version>2.5.33</version>
</dependency>

<!-- AFTER -->
<dependency>
    <groupId>org.apache.struts</groupId>
    <artifactId>struts2-core</artifactId>
    <version>6.8.0</version>
</dependency>
<dependency>
    <groupId>org.apache.struts</groupId>
    <artifactId>struts2-spring-plugin</artifactId>
    <version>6.8.0</version>
</dependency>
```

### 1.2 Add Required New Dependencies

```xml
<!-- Caffeine caching - Required for Struts 6.x -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.1.8</version>
</dependency>
```

### 1.3 Update Related Dependencies

```xml
<!-- OGNL - Expression Language -->
<dependency>
    <groupId>ognl</groupId>
    <artifactId>ognl</artifactId>
    <version>3.3.5</version>  <!-- Was 3.1.29 -->
</dependency>

<!-- FreeMarker - Template Engine -->
<dependency>
    <groupId>org.freemarker</groupId>
    <artifactId>freemarker</artifactId>
    <version>2.3.33</version>  <!-- Was 2.3.31 -->
</dependency>

<!-- Javassist - Bytecode manipulation -->
<dependency>
    <groupId>org.javassist</groupId>
    <artifactId>javassist</artifactId>
    <version>3.29.0-GA</version>  <!-- Was 3.20.0-GA -->
</dependency>

<!-- Checker Framework annotations -->
<dependency>
    <groupId>org.checkerframework</groupId>
    <artifactId>checker-qual</artifactId>
    <version>3.37.0</version>
</dependency>
```

### 1.4 Verification Step

```bash
# Verify dependencies resolve correctly (won't compile yet)
mvn dependency:resolve
mvn dependency:tree | grep -i struts
```

---

## Phase 2: ActionSupport Import Migration

### 2.1 Overview

All 458 2Action classes need import statement updates:

```java
// BEFORE
import com.opensymphony.xwork2.ActionSupport;

// AFTER
import org.apache.struts2.ActionSupport;
```

### 2.2 Automated Migration Script

Create and run this script:

```bash
#!/bin/bash
# File: scripts/migrate-struts-imports.sh

echo "Migrating ActionSupport imports from XWork2 to Struts2..."

# Find all Java files with the old import
find src/main/java -name "*.java" -type f | while read file; do
    if grep -q "com.opensymphony.xwork2.ActionSupport" "$file"; then
        echo "Updating: $file"
        sed -i 's/import com\.opensymphony\.xwork2\.ActionSupport;/import org.apache.struts2.ActionSupport;/g' "$file"
    fi
done

# Also check test-modern directory
find src/test-modern/java -name "*.java" -type f | while read file; do
    if grep -q "com.opensymphony.xwork2.ActionSupport" "$file"; then
        echo "Updating test: $file"
        sed -i 's/import com\.opensymphony\.xwork2\.ActionSupport;/import org.apache.struts2.ActionSupport;/g' "$file"
    fi
done

echo "Migration complete. Files updated:"
grep -r "org.apache.struts2.ActionSupport" src/main/java --include="*.java" | wc -l
```

### 2.3 Manual Verification

```bash
# Verify no old imports remain
grep -r "com.opensymphony.xwork2.ActionSupport" src/main/java --include="*.java"
# Should return 0 results

# Verify new imports are present
grep -r "org.apache.struts2.ActionSupport" src/main/java --include="*.java" | wc -l
# Should return ~458
```

### 2.4 Files Affected

Key modules with 2Action classes:
- `io.github.carlos_emr.carlos.PMmodule.web.*` - Program Management
- `io.github.carlos_emr.carlos.billing.CA.BC.*` - BC Billing
- `io.github.carlos_emr.carlos.billing.CA.ON.*` - ON Billing
- `io.github.carlos_emr.carlos.encounter.pageUtil.*` - Encounters
- `io.github.carlos_emr.carlos.casemgmt.web.*` - Case Management
- `io.github.carlos_emr.carlos.login.*` - Authentication
- `io.github.carlos_emr.carlos.messenger.*` - Messaging
- `io.github.carlos_emr.carlos.tickler.*` - Tickler
- Plus ~30 more packages

---

## Phase 3: Struts Configuration Migration

### 3.1 Update struts.xml DTD

**File**: `src/main/webapp/WEB-INF/classes/struts.xml`

```xml
<!-- BEFORE -->
<!DOCTYPE struts PUBLIC
    "-//Apache Software Foundation//DTD Struts Configuration 2.3//EN"
    "http://struts.apache.org/dtds/struts-2.3.dtd">

<!-- AFTER -->
<!DOCTYPE struts PUBLIC
    "-//Apache Software Foundation//DTD Struts Configuration 6.0//EN"
    "https://struts.apache.org/dtds/struts-6.0.dtd">
```

### 3.2 Update Exclude Pattern

Add servlet exclusions to prevent Struts from consuming multipart requests meant for legacy servlets:

```xml
<!-- BEFORE -->
<constant name="struts.action.excludePattern"
    value=".*\.(css|js|png|jpg|gif)$|^/ws/.*|^/servlet/(ca\.openosp\.DocumentUploadServlet|oscar\.DocumentTeleplanReportUploadServlet)$" />

<!-- AFTER - Add ^/servlet/.* pattern -->
<constant name="struts.action.excludePattern"
    value=".*\.(css|js|png|jpg|gif)$|^/ws/.*|^/servlet/.*" />
```

### 3.3 Verify Action Mappings

No changes required to individual action mappings - they use class FQN which already uses the new namespace.

---

## Phase 4: ActionContext API Migration

### 4.1 Overview

Struts 6.x introduces a builder pattern for ActionContext. This primarily affects test code.

### 4.2 OpenOWebTestBase Migration

**File**: `src/test-modern/java/io/github/carlos_emr/carlos/test/base/OpenOWebTestBase.java`

```java
// BEFORE (Struts 2.5.x)
ActionContext context = ActionContext.getContext();
if (context == null) {
    context = new ActionContext(new HashMap<>());
    ActionContext.setContext(context);
}
Map<String, Object> contextMap = context.getContextMap();
contextMap.put(ServletActionContext.HTTP_REQUEST, mockRequest);
context.setParameters(httpParameters);
// ... later ...
ActionContext.setContext(null);  // cleanup

// AFTER (Struts 6.x)
HttpParameters httpParameters = HttpParameters.create(requestParameters).build();
Map<String, Object> sessionMap = new HashMap<>();
// ... populate sessionMap with session attributes ...

ActionContext context = ActionContext.of()
    .withServletRequest(mockRequest)
    .withServletResponse(mockResponse)
    .withSession(sessionMap)
    .withParameters(httpParameters);

ActionContext.bind(context);  // bind to current thread
// ... test execution ...
ActionContext.clear();  // cleanup (in @AfterEach)
```

### 4.3 Key API Changes

| Struts 2.5.x | Struts 6.x |
|--------------|------------|
| `ActionContext.getContext()` | `ActionContext.of()` |
| `new ActionContext(Map)` | Builder pattern with `of()` |
| `context.setParameters(params)` | `.withParameters(params)` |
| `ActionContext.setContext(context)` | `ActionContext.bind(context)` |
| `ActionContext.setContext(null)` | `ActionContext.clear()` |
| Direct map manipulation | `.withServletRequest()`, `.withSession()`, etc. |

### 4.4 Search for Affected Files

```bash
# Find files using ActionContext
grep -r "ActionContext" src/test-modern/java --include="*.java" -l
grep -r "ActionContext" src/main/java --include="*.java" -l
```

---

## Phase 5: Deprecated Features Removal

### 5.1 Remove @Validation Annotations

The `@Validation` annotation is deprecated in Struts 6.x. Search and remove:

```bash
# Find files with @Validation
grep -r "@Validation" src/main/java --include="*.java" -l
```

**Example fix:**
```java
// BEFORE
@Validation
public class EctImmCreateImmunizationSetInit2Action extends ActionSupport {

// AFTER
public class EctImmCreateImmunizationSetInit2Action extends ActionSupport {
```

### 5.2 Verify No Deprecated XWork2 Imports

```bash
# Check for any remaining XWork2 imports
grep -r "com.opensymphony.xwork2" src/main/java --include="*.java"
```

Common XWork2 classes that may need migration:
- `com.opensymphony.xwork2.ActionSupport` → `org.apache.struts2.ActionSupport`
- `com.opensymphony.xwork2.ActionContext` → `org.apache.struts2.ActionContext`

---

## Phase 6: File Upload Compatibility

### 6.1 Problem Description

Struts 6.x file upload interceptor may consume multipart requests before legacy servlets can process them.

### 6.2 DocumentUploadServlet Update

**File**: `src/main/java/io/github/carlos_emr/carlos/servlet/DocumentUploadServlet.java` (or similar)

```java
// BEFORE (deprecated Commons FileUpload API)
DiskFileUpload upload = new DiskFileUpload();

// AFTER (modern Commons FileUpload API)
DiskFileItemFactory factory = new DiskFileItemFactory();
ServletFileUpload upload = new ServletFileUpload(factory);
```

### 6.3 Affected Servlets

Review these servlets for file upload compatibility:
- `DocumentUploadServlet`
- `DocumentTeleplanReportUploadServlet`
- Any other servlet handling `multipart/form-data`

---

## Phase 7: DAO and Model Compatibility Fixes

Based on experimental branch findings, these DAO/model changes may be required:

### 7.1 TeleplanS25Dao - JPA Parameter Indexing

```java
// BEFORE (0-based, incorrect in Struts 6.x context)
query.setParameter(0, value);

// AFTER (1-based, JPA standard)
query.setParameter(1, value);
// Or use named parameters
query.setParameter("paramName", value);
```

### 7.2 DrugDaoImpl - Boolean Literal Fixes

```java
// BEFORE
query = "... d.archived = 0 ..."

// AFTER
query = "... d.archived = false ..."
```

### 7.3 Drug Model - Type Changes

```java
// If gcnSeqNo field exists and needs updating
// BEFORE
private int gcnSeqNo;

// AFTER
private String gcnSeqNo = "0";
```

### 7.4 AllergyDaoImpl - Remove Invalid ORDER BY

Review ORDER BY clauses for non-existent fields.

---

## Phase 8: Testing and Validation

### 8.1 Compilation Verification

```bash
# First verify compilation
mvn compile -DskipTests

# Check for compilation errors
# Fix any remaining import or API issues
```

### 8.2 Unit Test Execution

```bash
# Run unit tests (no database)
make install --run-unit-tests
```

### 8.3 Integration Test Execution

```bash
# Run integration tests
make install --run-integration-tests
```

### 8.4 Full Test Suite

```bash
# Run all tests
make install --run-tests
```

### 8.5 Manual Testing Checklist

Critical paths to manually verify:

- [ ] **Login/Logout** - Login2Action, Logout2Action
- [ ] **Dashboard** - Home2Action, AdminHome2Action
- [ ] **Patient Search** - Demographic search functionality
- [ ] **Appointments** - Schedule viewing and booking
- [ ] **Encounter** - EctDisplay*2Action components in left navbar
- [ ] **Billing** - BC and ON billing flows
- [ ] **File Uploads** - Document upload, Teleplan file upload
- [ ] **Tickler** - AddTickler2Action, EditTickler2Action
- [ ] **Case Management** - CaseloadContent2Action

### 8.6 Regression Testing

Compare test pass rates:
- Document pre-migration pass rate
- Document post-migration pass rate
- Investigate any new failures

---

## Rollback Plan

If critical issues are discovered:

### Immediate Rollback

```bash
# Revert all changes (before commit)
git checkout .
git clean -fd

# Or if committed, revert to develop
git checkout develop
```

### Partial Rollback

If only specific components fail:
1. Identify failing component
2. Check experimental branch for additional fixes
3. Apply targeted fixes
4. Re-test affected functionality

### Version Pinning

If Struts 6.8.0 has issues, try:
- Struts 6.7.0
- Struts 6.6.0
- etc.

---

## Reference: Experimental Branch Changes

### Key Commits to Reference

| Commit | Date | Description |
|--------|------|-------------|
| `e267c1286` | Dec 22, 2025 | Main migration: 2.5.33 → 6.8.0, pom.xml, struts.xml, test base |
| `f0b881925` | Dec 22, 2025 | Bulk ActionSupport import replacement (440+ files) |
| `b1a11c681` | Jan 2, 2026 | File upload servlet conflict resolution |
| `4acb38d61` | Dec 29, 2025 | Missing method mappings in actions |
| `31473feb4` | Jan 15, 2026 | Switch statement logic fixes (RxWriteScript2Action) |
| `3e174d6dc` | Jan 24, 2026 | Security fixes and DAO compatibility |

### Viewing Experimental Changes

```bash
# Fetch experimental branch
git fetch origin experimental

# View specific commit
git show e267c1286

# Compare files between branches
git diff develop..origin/experimental -- pom.xml
git diff develop..origin/experimental -- src/main/webapp/WEB-INF/classes/struts.xml
```

### Namespace Mapping

The experimental branch used old namespaces. Map as follows:

| Experimental Branch | Current Develop |
|---------------------|-----------------|
| `org.oscarehr.*` | `io.github.carlos_emr.carlos.*` |
| `ca.openosp.openo.*` | `io.github.carlos_emr.carlos.*` |
| `oscar.*` | `io.github.carlos_emr.carlos.*` |

---

## Migration Execution Order

### Recommended Sequence

1. **Create feature branch**: `git checkout -b struts-6.8-migration`
2. **Phase 1**: Update pom.xml dependencies
3. **Phase 2**: Run import migration script
4. **Phase 3**: Update struts.xml
5. **Phase 5**: Remove @Validation annotations
6. **Compile**: `mvn compile -DskipTests`
7. **Phase 4**: Migrate ActionContext in tests
8. **Phase 6**: Update file upload servlets
9. **Phase 7**: Apply DAO/model fixes as needed
10. **Phase 8**: Full testing cycle
11. **PR**: Create pull request for review

### Estimated Task Breakdown

| Phase | Task Count | Complexity |
|-------|------------|------------|
| Phase 1 | 1 file (pom.xml) | Low |
| Phase 2 | ~458 files (automated) | Low |
| Phase 3 | 1 file (struts.xml) | Low |
| Phase 4 | 1-3 files | Medium |
| Phase 5 | ~5 files | Low |
| Phase 6 | 2-3 files | Medium |
| Phase 7 | 3-5 files | Medium |
| Phase 8 | N/A | Testing effort |

---

## Appendix A: Helpful Commands

```bash
# Count 2Action files
find src/main/java -name "*2Action.java" | wc -l

# Find all ActionSupport imports
grep -r "ActionSupport" src/main/java --include="*.java" | grep "import" | sort -u

# Find ActionContext usage
grep -r "ActionContext" src --include="*.java" -l

# Find @Validation usage
grep -r "@Validation" src/main/java --include="*.java" -l

# Verify struts.xml DTD
head -5 src/main/webapp/WEB-INF/classes/struts.xml

# Check Struts version in pom.xml
grep -A2 "struts2-core" pom.xml
```

---

## Appendix B: Struts 6.x Documentation

- [Struts 6.x Migration Guide](https://struts.apache.org/announce-2023.html)
- [Struts 6.x Release Notes](https://struts.apache.org/releases.html)
- [ActionContext API Changes](https://struts.apache.org/core-developers/action-context.html)

---

**Document Version**: 1.0
**Created**: 2026-02-03
**Author**: Generated with Claude Code
