# Struts 6.8.0 Migration Plan

## Executive Summary

This document outlines the migration plan for upgrading CARLOS EMR from Apache Struts 2.5.33 to Struts 6.8.0.

**CRITICAL UPDATE (v2.0)**: Research into official Struts documentation and bug reports reveals that the `com.opensymphony.xwork2.*` packages are **deprecated but fully functional** in Struts 6.x. Furthermore, there is a **known bug (WW-5494)** where using `org.apache.struts2.ActionSupport` instead of `com.opensymphony.xwork2.ActionSupport` can cause interceptor stack corruption. The official recommendation is to **continue using the xwork2 packages** in Struts 6.x.

The full package migration from `com.opensymphony.xwork2.*` to `org.apache.struts2.*` is planned for **Struts 7.x**, not 6.x.

**This dramatically simplifies the migration.**

**Key Statistics:**
- **Current Version**: Struts 2.5.33
- **Target Version**: Struts 6.8.0
- **Files Requiring Code Changes**: ~3-5 files (configuration only)
- **Import Changes Required**: **NONE** (keep using xwork2 packages)
- **Action Mappings**: No changes needed

---

## Table of Contents

1. [Critical: Import Changes NOT Required](#critical-import-changes-not-required)
2. [Migration Overview](#1-migration-overview)
3. [Phase 1: Dependency Updates](#phase-1-dependency-updates)
4. [Phase 2: Struts Configuration Updates](#phase-2-struts-configuration-updates)
5. [Phase 3: Fix Pre-Existing Bugs](#phase-3-fix-pre-existing-bugs)
6. [Phase 4: Testing and Validation](#phase-4-testing-and-validation)
7. [Future: Struts 7.x Migration](#future-struts-7x-migration)
8. [Rollback Plan](#rollback-plan)
9. [References](#references)

---

## Critical: Import Changes NOT Required

### The WW-5494 Bug

**Bug**: [WW-5494](https://issues.apache.org/jira/browse/WW-5494) - "Using struts2.ActionSupport instead of xwork2.ActionSupport causes interceptors stack corrupted"

**Status**: Closed as **"Won't Fix"** - The bug was acknowledged but NOT fixed. The resolution means Struts team decided it wasn't worth fixing since there's a workaround.

**Impact**: If you change action classes to extend `org.apache.struts2.ActionSupport` instead of `com.opensymphony.xwork2.ActionSupport`, custom interceptor stacks may behave unexpectedly.

**Official Workaround**: From the Struts team (Kusal Kithul-Godage, Jan 2025): *"Given there is a straightforward workaround of simply continuing to use 'com.opensymphony.xwork2.ActionSupport', I think this bug (if it genuinely is one) is not urgent and is fine to be postponed."*

**Note**: This bug may or may not still exist in Struts 7.x where xwork2 packages are removed entirely. Testing would be required if migrating to 7.x.

### What This Means for CARLOS EMR

**DO NOT change these imports for Struts 6.8:**

```java
// KEEP THESE - They work fine in Struts 6.8
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.ActionContext;
import com.opensymphony.xwork2.ModelDriven;
import com.opensymphony.xwork2.validator.annotations.Validation;
```

The `com.opensymphony.xwork2.*` packages are:
- **Deprecated** in Struts 6.x (compiler warnings only)
- **Fully functional** in Struts 6.8.0
- **Safer** than the new packages due to WW-5494
- **Scheduled for removal** in Struts 7.x (requires Java 17+)

### Correction to Experimental Branch Approach

The experimental branch migrated all 458 2Action files to use `org.apache.struts2.ActionSupport`. This was **premature** and potentially introduces the WW-5494 bug. For Struts 6.8, this migration is unnecessary.

---

## 1. Migration Overview

### What Actually Changes Between Struts 2.5.33 and 6.8.0

| Component | Change Required | Notes |
|-----------|-----------------|-------|
| **pom.xml** | YES | Update Struts version, add Caffeine |
| **struts.xml DTD** | YES | Update to 6.0 DTD |
| **struts.xml exclude pattern** | YES | Fix pre-existing namespace bug |
| **ActionSupport imports** | NO | Keep using xwork2 (WW-5494) |
| **ActionContext imports** | NO | Keep using xwork2 |
| **Action code** | NO | No changes needed |
| **Test code** | NO | No changes needed |

### Requirements

From the [official migration guide](https://cwiki.apache.org/confluence/display/WW/Struts+2.5+to+6.0.0+migration):

- **Java**: 8+ (CARLOS uses Java 21 ✓)
- **Servlet API**: 3.1+ (CARLOS uses Servlet 4.0 via Tomcat 9 ✓)

### Risk Assessment (Revised)

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Dependency conflicts | Low | Medium | Test dependency tree |
| DTD validation errors | Low | Low | Copy exact DTD from docs |
| OGNL expression issues | Low | Medium | Test all forms |
| Date formatting changes | Medium | Low | Java 8 DateTimeFormatter used |

---

## Phase 1: Dependency Updates

### 1.1 Update pom.xml Struts Dependencies

**File**: `pom.xml` (around line 1416)

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

### 1.2 Add Caffeine Caching Dependency

Struts 6.x requires Caffeine for caching:

```xml
<!-- Add this new dependency -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.2.3</version>
</dependency>
```

### 1.3 Verify Dependencies

```bash
# Check for conflicts
mvn dependency:tree | grep -i struts
mvn dependency:tree | grep -i caffeine

# Verify OGNL version (should be 3.3.x transitive)
mvn dependency:tree | grep -i ognl
```

---

## Phase 2: Struts Configuration Updates

### 2.1 Update struts.xml DTD

**File**: `src/main/webapp/WEB-INF/classes/struts.xml`

```xml
<!-- BEFORE (line 2) -->
<!DOCTYPE struts PUBLIC
    "-//Apache Software Foundation//DTD Struts Configuration 2.3//EN"
    "http://struts.apache.org/dtds/struts-2.3.dtd">

<!-- AFTER -->
<!DOCTYPE struts PUBLIC
    "-//Apache Software Foundation//DTD Struts Configuration 6.0//EN"
    "https://struts.apache.org/dtds/struts-6.0.dtd">
```

**Note**: The URL changed from `http://` to `https://`.

### 2.2 Review Struts Constants

The following XWork constants were removed in Struts 6.0 (already using Struts equivalents in CARLOS):

| Removed Constant | Struts Equivalent |
|------------------|-------------------|
| `devMode` | `struts.devMode` |
| `logMissingProperties` | `struts.ognl.logMissingProperties` |

Verify `struts.xml` uses `struts.*` prefixed constants (it should already).

**Note**: Lines 12-13 have a duplicate `struts.custom.i18n.resources` definition. The second one (with MessageResources_mcedt) overrides the first. This is a pre-existing minor issue, not migration-related.

### 2.3 OGNL Expression Length Limit

Struts 6.0 limits OGNL expressions to 256 characters by default. If you have long expressions, you may need to increase this:

```xml
<!-- Only add if needed -->
<constant name="struts.ognl.expressionMaxLength" value="512" />
```

---

## Phase 3: Fix Pre-Existing Bugs

### 3.1 Update struts.action.excludePattern (CRITICAL)

The current exclude pattern references old namespaces that don't match current servlet URLs.

**File**: `src/main/webapp/WEB-INF/classes/struts.xml`

```xml
<!-- BEFORE (broken - doesn't match current namespaces) -->
<constant name="struts.action.excludePattern"
    value=".*\.(css|js|png|jpg|gif)$|^/ws/.*|^/servlet/(ca\.openosp\.DocumentUploadServlet|oscar\.DocumentTeleplanReportUploadServlet)$" />

<!-- AFTER (works with all servlet paths) -->
<constant name="struts.action.excludePattern"
    value=".*\.(css|js|png|jpg|gif)$|^/ws/.*|^/servlet/.*" />
```

This fix:
1. Resolves the pre-existing namespace mismatch
2. Prevents Struts 6.x file upload interceptor from consuming multipart requests meant for servlets
3. Is simpler and more maintainable

### 3.2 Optional: Update Deprecated FileUpload API

**File**: `src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/MSP/DocumentTeleplanReportUploadServlet.java`

This servlet uses deprecated Commons FileUpload API. While it will work, consider updating:

```java
// CURRENT (deprecated but functional)
import org.apache.commons.fileupload.DiskFileUpload;
DiskFileUpload upload = new DiskFileUpload();

// RECOMMENDED (modern API)
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
DiskFileItemFactory factory = new DiskFileItemFactory();
ServletFileUpload upload = new ServletFileUpload(factory);
```

---

## Phase 4: Testing and Validation

### 4.1 Build Verification

```bash
# Clean build
make clean

# Build without tests first
mvn compile -DskipTests

# If compilation succeeds, run tests
make install --run-tests
```

### 4.2 Expected Deprecation Warnings

You will see deprecation warnings for `com.opensymphony.xwork2.*` classes. **This is expected and acceptable for Struts 6.8.**

```
[WARNING] ... com.opensymphony.xwork2.ActionSupport is deprecated
```

These warnings indicate the classes will be removed in Struts 7.x but are fully functional in 6.x.

#### What's Deprecated

The following classes are marked `@Deprecated` in Struts 6.x (via [WW-3714](https://issues.apache.org/jira/browse/WW-3714)):
- `com.opensymphony.xwork2.ActionSupport` (458 files)
- `com.opensymphony.xwork2.ActionContext`
- `com.opensymphony.xwork2.ModelDriven` (2 files)
- `com.opensymphony.xwork2.validator.annotations.Validation` (1 file)

#### How to Suppress Deprecation Warnings

**Recommended: Maven compiler configuration** (suppresses globally)

Add to `pom.xml` in the `maven-compiler-plugin` configuration:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <!-- Existing configuration... -->
        <showDeprecation>false</showDeprecation>
    </configuration>
</plugin>
```

**Alternative: Per-class annotation** (if you prefer targeted suppression)

```java
@SuppressWarnings("deprecation")
public class MyAction extends ActionSupport {
    // ...
}
```

This would require adding the annotation to 458+ files, so the Maven approach is recommended.

### 4.3 Manual Testing Checklist

Test these critical paths:

- [ ] **Login/Logout** - Authentication flow
- [ ] **Dashboard** - Home page loads
- [ ] **Patient Search** - Form submission works
- [ ] **Appointments** - Date/time formatting correct
- [ ] **File Uploads** - Document upload, Teleplan file upload
- [ ] **Billing** - BC and ON billing forms
- [ ] **Tickler** - Create and edit ticklers

### 4.4 Known Struts 6.x Behavioral Changes

These changes are documented in Struts 6.x but **CARLOS is NOT affected** based on codebase verification:

| Change | CARLOS Impact | Reason |
|--------|---------------|--------|
| Date tag uses `DateTimeFormatter` | ✅ Not affected | No `<s:date>` tags used |
| FreeMarker auto-escaping | ✅ Not affected | No `.ftl` templates |
| Checkbox `submitUnchecked` default | ✅ Not affected | No `<s:checkbox>` tags used |
| OGNL expression 256 char limit | ✅ Not affected | No long OGNL expressions |
| Velocity moved to plugin | ✅ Not affected | No `.vm` templates |
| XWork constants removed | ✅ Already correct | Uses `struts.*` prefix |

### 4.5 Codebase Verification Summary

**Struts Tag Usage** (only 2 files):
- `src/main/webapp/mcedt/messages.jsp` - Basic tags: `<s:if>`, `<s:iterator>`, `<s:property>`
- `src/main/webapp/eform/partials/upload_image.jsp` - Basic tags: `<s:actionerror>`, `<s:actionmessage>`

**xwork2 Imports** (all deprecated but functional):
- 458 files: `com.opensymphony.xwork2.ActionSupport`
- 2 files: `com.opensymphony.xwork2.ModelDriven`
- 1 file: `com.opensymphony.xwork2.validator.annotations.Validation`

**Configuration**:
- 511 action mappings in struts.xml
- Standard interceptors only (`defaultStack`, `fileUpload`)
- No custom interceptors
- All constants use `struts.*` prefix (not old XWork constants)

---

## Future: Struts 7.x Migration (NOT POSSIBLE Without Jakarta)

**Struts 7.x is NOT compatible with Tomcat 9 / javax.servlet.**

From the [official migration docs](https://cwiki.apache.org/confluence/display/WW/Struts+6.x.x+to+7.x.x+migration):
> "Struts 7.x.x requires a servlet container which supports Jakarta Servlet API 6 at least, **it won't work with older versions**."

### Struts 7.x Hard Requirements

| Requirement | CARLOS Current | Struts 7.x Requires | Compatible? |
|-------------|----------------|---------------------|-------------|
| Java | 21 | 17+ | ✅ Yes |
| Servlet API | javax.servlet | **Jakarta Servlet API 6** | ❌ No |
| Tomcat | 9.0.97 | **Tomcat 10+** | ❌ No |

### Why Jakarta Migration is a Major Undertaking

The `javax.*` → `jakarta.*` namespace change affects:
- All servlet imports (`javax.servlet.*` → `jakarta.servlet.*`)
- JSP/JSTL libraries
- Many third-party libraries
- Potentially hundreds of files across the codebase

This is NOT just a Struts migration - it's a full Jakarta EE 9+ migration.

### Recommendation

**CARLOS should stay on Struts 6.8.x** - the latest version compatible with:
- Tomcat 9.x
- javax.servlet API
- Current infrastructure

The xwork2 deprecation warnings are acceptable - the packages are fully functional.

### If Struts 7.x Migration Is Ever Needed

The full package migration would include:

| Struts 6.x (current) | Struts 7.x (future) |
|----------------------|---------------------|
| `com.opensymphony.xwork2.ActionSupport` | `org.apache.struts2.ActionSupport` |
| `com.opensymphony.xwork2.ActionContext` | `org.apache.struts2.ActionContext` |
| `com.opensymphony.xwork2.ModelDriven` | `org.apache.struts2.ModelDriven` |
| `javax.servlet.*` | `jakarta.servlet.*` |

Plus the `@StrutsParameter` annotation requirement for all setter methods in Action classes.

The experimental branch commits can serve as partial reference, but would need Jakarta namespace updates too.

---

## Rollback Plan

### Immediate Rollback

```bash
# Revert pom.xml and struts.xml
git checkout pom.xml src/main/webapp/WEB-INF/classes/struts.xml

# Clean and rebuild
make clean && make install
```

### Partial Rollback

If only Struts version causes issues:
1. Revert pom.xml Struts version to 2.5.33
2. Revert struts.xml DTD to 2.3
3. Keep the exclude pattern fix (it's beneficial regardless)

---

## References

### Official Documentation

- [Struts 2.5 to 6.0.0 Migration Guide](https://cwiki.apache.org/confluence/display/WW/Struts+2.5+to+6.0.0+migration)
- [Version Notes 6.8.0](https://cwiki.apache.org/confluence/display/WW/Version+Notes+6.8.0)
- [Struts 6.x.x to 7.x.x Migration](https://cwiki.apache.org/confluence/display/WW/Struts+6.x.x+to+7.x.x+migration)

### Bug Reports

- [WW-5494: ActionSupport Interceptor Stack Issue](https://issues.apache.org/jira/browse/WW-5494) - Reason to keep using xwork2 packages

### Third-Party Guides

- [End Point Dev: Migration from Struts 2 to Struts 6](https://www.endpointdev.com/blog/2025/04/migration-from-struts2-to-struts6/)
- [OpenRewrite: Migrate to Struts 6.0](https://docs.openrewrite.org/recipes/java/struts/migrate6/migratestruts6)

---

## Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-03 | Initial plan based on experimental branch analysis |
| 1.1 | 2026-02-03 | Corrections after codebase verification |
| 2.0 | 2026-02-03 | **MAJOR REVISION** after official documentation research: |
| | | - Discovered WW-5494 bug with org.apache.struts2.ActionSupport |
| | | - Import changes NOT required for Struts 6.8 (keep xwork2) |
| | | - Dramatically simplified migration scope |
| | | - Added official documentation references |
| | | - Removed unnecessary Phase 2, 4, 5 from plan |

---

## Summary: Migration Checklist

For Struts 2.5.33 → 6.8.0, you only need to:

- [ ] Update `pom.xml`: Struts 2.5.33 → 6.8.0
- [ ] Update `pom.xml`: Add Caffeine 3.2.3 dependency
- [ ] Update `struts.xml`: DTD 2.3 → 6.0
- [ ] Update `struts.xml`: Fix exclude pattern to `^/servlet/.*`
- [ ] Run tests and verify functionality
- [ ] (Optional) Update deprecated FileUpload API in DocumentTeleplanReportUploadServlet

**Total files to modify: 2** (pom.xml, struts.xml)

---

**Document Version**: 2.0
**Created**: 2026-02-03
**Last Updated**: 2026-02-03
**Author**: Generated with Claude Code
