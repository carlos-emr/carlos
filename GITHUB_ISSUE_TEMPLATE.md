---
Title: 🚨 CRITICAL: Library Dependency Updates + JasperReports Security Vulnerability
Labels: type: maintenance, priority: critical, dependencies, security
---

## 🚨 CRITICAL SECURITY ALERT

**JasperReports Vulnerability Detected**: The dependency `net.sf.jasperreports:jasperreports` (v6.20.1) has a **CRITICAL Java deserialization vulnerability** that can lead to Remote Code Execution (RCE).

- **Severity**: 🔴 **CRITICAL**
- **Affected Versions**: <= 7.0.3 (including current AND available update)
- **Patched Version**: ❌ **NOT AVAILABLE**
- **Action**: **DO NOT UPDATE** - See `SECURITY_ALERT_JASPERREPORTS.md` for immediate mitigation steps

**IMMEDIATE ACTIONS REQUIRED**:
1. Assess if JasperReports is actively used
2. If not used: Remove from dependencies TODAY
3. If used: Implement security controls THIS WEEK
4. Plan migration to alternative solution

**Complete Details**: `SECURITY_ALERT_JASPERREPORTS.md`

---

## Overview

This issue documents available library updates for dependencies in `pom.xml` as of January 27, 2026. The analysis focuses on identifying patch version updates (non-breaking changes) that can be safely applied.

## Summary

A comprehensive review using Maven's `versions:display-dependency-updates` plugin identified **93 total library updates** available, categorized as follows:

- **Patch Updates (Non-Breaking)**: 5 libraries - *Recommended for immediate update*
- **Minor Updates (New Features)**: 31 libraries - *Should be evaluated and tested*
- **Major Updates (Breaking Changes)**: 56 libraries - *Require significant testing and code changes*
- **Special Versioning**: 1 library - *Requires individual assessment*

## ⭐ Patch Version Updates (Recommended Priority)

These updates only increment the patch version (x.y.**Z**), indicating bug fixes and non-breaking changes according to semantic versioning:

| Library | Current Version | Available Version | Notes |
|---------|----------------|-------------------|-------|
| `com.itextpdf:itextpdf` | 5.5.13.4 | 5.5.13.5 | PDF generation library |
| `com.itextpdf.tool:xmlworker` | 5.5.13.4 | 5.5.13.5 | PDF XML worker (companion to itextpdf) |
| `com.jcraft:jsch` | 0.1.54 | 0.1.55 | SSH/SFTP library |
| `org.apache.httpcomponents:httpmime` | 4.5.6 | **4.5.14** | HTTP multipart support (8 patch versions behind) ⚠️ |
| `org.jfree:jfreechart` | 1.5.4 | 1.5.6 | Chart generation library |

**Recommendation**: These 5 libraries can be updated with minimal risk. The `httpmime` library is particularly notable as it's 8 patch versions behind, likely containing important bug fixes and security improvements.

## 📋 Minor Version Updates (31 libraries - Require Testing)

Notable gaps:
- 🎭 **Playwright**: 17 minor versions behind (1.40.0 → 1.57.0)
- 🧪 **Mockito**: 13 minor versions behind (5.8.0 → 5.21.0)
- 📊 **PMD tools**: 10 minor versions behind (7.10.0 → 7.20.0)

<details>
<summary>Click to expand full list of 31 minor version updates</summary>

| Library | Current | Available | Gap |
|---------|---------|-----------|-----|
| `com.atlassian.commonmark:commonmark` | 0.10.0 | 0.17.0 | 7 minor versions |
| `com.fasterxml.jackson.core:jackson-databind` | 2.19.2 | 2.21.0 | 2 minor versions |
| `com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider` | 2.15.2 | 2.21.0 | 6 minor versions |
| `com.google.code.gson:gson` | 2.10.1 | 2.13.2 | 3 minor versions |
| `com.google.guava:guava` | 33.4.8-jre | 33.5.0-jre | 1 minor version |
| `com.h2database:h2` | 2.2.224 | 2.4.240 | 2 minor versions |
| `com.microsoft.playwright:playwright` | 1.40.0 | 1.57.0 | 17 minor versions |
| `com.mysql:mysql-connector-j` | 9.3.0 | 9.5.0 | 2 minor versions |
| `com.twelvemonkeys.common:common-lang` | 3.12.0 | 3.13.0 | 1 minor version |
| `commons-codec:commons-codec` | 1.18.0 | 1.20.0 | 2 minor versions |
| `commons-io:commons-io` | 2.18.0 | 2.21.0 | 3 minor versions |
| `commons-logging:commons-logging` | 1.2 | 1.3.5 | 1 minor version |
| `commons-net:commons-net` | 3.11.1 | 3.12.0 | 1 minor version |
| `commons-validator:commons-validator` | 1.9.0 | 1.10.1 | 1 minor version |
| `janino:janino` | 2.3.2 | 2.4.3 | 1 minor version |
| `net.sourceforge.pmd:pmd-core` | 7.10.0 | 7.20.0 | 10 minor versions |
| `net.sourceforge.pmd:pmd-java` | 7.10.0 | 7.20.0 | 10 minor versions |
| `org.apache.commons:commons-compress` | 1.26.0 | 1.28.0 | 2 minor versions |
| `org.apache.commons:commons-exec` | 1.3 | 1.6.0 | 3 minor versions |
| `org.apache.commons:commons-lang3` | 3.18.0 | 3.20.0 | 2 minor versions |
| `org.apache.commons:commons-text` | 1.13.1 | 1.15.0 | 2 minor versions |
| `org.bouncycastle:bcpkix-jdk18on` | 1.79 | 1.83 | 4 minor versions |
| `org.dom4j:dom4j` | 2.1.4 | 2.2.0 | 1 minor version |
| `org.jsoup:jsoup` | 1.17.2 | 1.22.1 | 5 minor versions |
| `org.mockito:mockito-core` | 5.8.0 | 5.21.0 | 13 minor versions |
| `org.mockito:mockito-junit-jupiter` | 5.8.0 | 5.21.0 | 13 minor versions |
| `org.owasp.encoder:encoder` | 1.2.1 | 1.4.0 | 2 minor versions |
| `org.owasp.encoder:encoder-jsp` | 1.2.3 | 1.4.0 | 2 minor versions |
| `org.owasp.esapi:esapi` | 2.6.2.0 | 2.7.0.1-RC1 | 1 minor version (RC) |
| `org.slf4j:slf4j-api` | 2.0.17 | 2.1.0-alpha1 | 1 minor version (alpha) |
| `org.testng:testng` | 7.5.1 | 7.12.0 | 7 minor versions |

</details>

## ⚠️ Major Version Updates (56 libraries - Breaking Changes)

These require careful planning and extensive testing:

**Key Framework Updates:**
- **Spring Framework**: 5.3.39 → 7.0.3 (requires Java 17+)
- **Hibernate**: 5.6.15.Final → 7.3.0.CR1 (ORM changes)
- **Struts**: 2.5.33 → 7.1.1 (web framework)
- **Jakarta EE**: Multiple libraries need `javax.*` → `jakarta.*` migration

**Healthcare Libraries:**
- **HAPI FHIR**: 6.10.5 → 8.6.1 (8 libraries)
- **HAPI HL7**: 1.0.1 → 2.6.0 (8 libraries)

<details>
<summary>Click to expand full list of 56 major version updates</summary>

See [DEPENDENCY_UPDATE_ANALYSIS.md](DEPENDENCY_UPDATE_ANALYSIS.md) for complete details.

</details>

## 🔒 Security Considerations

Libraries with security implications that should be prioritized:

- **OWASP libraries** (encoder, esapi, csrfguard) - Security-critical
- **Bouncy Castle** (bcpkix-jdk18on) - Cryptography library
- **Apache Commons** libraries - Frequently targeted in CVEs
- **Log4j** - Historical security issues (major version 3 is in beta)

## 📋 Recommendations

### ⭐ Immediate Action (Low Risk)
1. **Update patch versions** (5 libraries listed above) - Can be done immediately with minimal testing
2. **Test critical workflows** after update:
   - PDF generation
   - SFTP/SSH operations
   - HTTP file uploads
   - Chart generation

### 📅 Short Term (Medium Risk - Requires Testing)
1. **Security libraries**:
   - OWASP Encoder (1.2.x → 1.4.0)
   - Bouncy Castle (1.79 → 1.83)
2. **Testing infrastructure**:
   - Mockito (5.8.0 → 5.21.0)
   - Playwright (1.40.0 → 1.57.0)
3. **Code quality tools**:
   - PMD (7.10.0 → 7.20.0)

### 🎯 Long Term Planning (High Risk - Breaking Changes)
1. **Spring Framework 7.x migration** - Requires Java 17+, extensive testing (6-12 months)
2. **Jakarta EE namespace migration** - Systematic refactoring needed (2-4 months)
3. **Hibernate 7.x upgrade** - ORM layer changes (2-3 months)
4. **Struts 7.x upgrade** - Web framework changes (3-6 months)

## 📊 Testing Strategy

For any updates, the following testing should be performed:
1. ✅ Unit tests (existing test suite)
2. ✅ Integration tests
3. ✅ Security scanning (CodeQL)
4. ✅ Manual testing of critical healthcare workflows
5. ✅ Performance testing for ORM/database changes

## 📚 References

- **Analysis Tool**: `mvn versions:display-dependency-updates`
- **Analysis Date**: January 27, 2026
- **Branch**: develop
- **Complete Analysis**: [DEPENDENCY_UPDATE_ANALYSIS.md](DEPENDENCY_UPDATE_ANALYSIS.md)

---

**Generated by**: @claude (automated dependency analysis)

## Action Items

- [ ] Create separate issues for each category of updates
- [ ] Update patch version libraries (5 libraries)
- [ ] Research CVEs for current library versions
- [ ] Schedule testing window for minor version updates
- [ ] Create epic for major framework migration planning
- [ ] Prioritize security-critical library updates
