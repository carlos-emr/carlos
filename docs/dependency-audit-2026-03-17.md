# CARLOS EMR — Jakarta Dependency Audit (2026-03-17)

Comprehensive audit of all Maven dependencies in `pom.xml` for currency, Jakarta EE 11
compatibility, maintenance status, and security posture. Conducted against the Java 21 /
Spring 7.0.6 / Tomcat 11 / Jakarta EE 11 stack.

---

## Executive Summary

**Total dependencies audited**: 90+ (direct dependencies, managed versions, and build plugins)

| Status | Count | Details |
|--------|-------|---------|
| Current | ~75 | On latest available version |
| Upgrade needed | 4 | XMLBeans, HAPI v2, HAPI FHIR, DBUnit |
| Future upgrade target | 2 | Apache CXF 4.2.0, HttpClient 5.x |
| Dormant / EOL (monitor) | 5 | JDOM2, Xerces, Xalan, ZXing, ultrabuk-htmltopdf |
| Abandoned (replace) | 1 | ultrabuk-htmltopdf-java (wkhtmltopdf archived) |

### Critical Action Items

| Priority | Dependency | Current | Target | Reason |
|----------|-----------|---------|--------|--------|
| **CRITICAL** | HAPI FHIR | 6.10.5 | 8.6.2 | Uses `javax.*` — incompatible with Jakarta EE 11 |
| **HIGH** | HAPI v2 (HL7) | 1.0.1 | 2.5.1 | 15 years behind, security fixes, Jakarta servlet support |
| **HIGH** | XMLBeans | 3.1.0 | 5.3.0 | 5 major versions behind, years of bug fixes missing |
| **MEDIUM** | DBUnit | 2.7.3 | 3.0.0 | Adds JUnit 5 support; or evaluate removal |
| **MEDIUM** | CXF | 4.1.5 | 4.2.0 | Proper Jakarta EE 11 / Spring 7 alignment (blocked by Jackson 3 migration) |
| **MEDIUM** | HttpClient | 4.5.14 | 5.6 | 4.x is maintenance-only; 5.x is actively developed |
| **LOW** | ultrabuk-htmltopdf | 1.0.11 | Replace | wkhtmltopdf is archived, Qt WebKit engine unmaintained |
| **LOW** | CDS local_repo JARs | various | Audit | Check for `javax.*` imports; recompile if needed |

---

## Detailed Audit by Category

### 1. Core Frameworks

| Dependency | POM Version | Latest | Status | Notes |
|-----------|-------------|--------|--------|-------|
| Spring Framework (BOM) | 7.0.6 | 7.0.6 | Current | Released 2026-03-13 |
| Spring Security Crypto | 7.0.4 | 7.0.3/7.0.4 | Current | Verify 7.0.4 resolves from Maven Central |
| Hibernate ORM | 7.2.7.Final | 7.2.7.Final | Current | Released 2026-03-15; 7.3 CR in progress |
| Apache Struts | 7.1.1 | 7.1.1 | Current | Jakarta EE namespace migration complete |
| Apache CXF | 4.1.5 | 4.1.5 (4.2.0 avail) | **Upgrade target** | 4.2.0 = Jakarta EE 11 + Spring 7; blocked by Jackson 3 |

### 2. Apache Commons

| Dependency | POM Version | Latest | Status |
|-----------|-------------|--------|--------|
| commons-logging | 1.3.6 | 1.3.6 | Current — correct for Spring 7 (replaces spring-jcl) |
| commons-text | 1.15.0 | 1.15.0 | Current |
| commons-validator | 1.10.1 | 1.10.1 | Current |
| commons-io | 2.21.0 | 2.21.0 | Current |
| commons-collections4 | 4.5.0 | 4.5.0 | Current |
| commons-codec | 1.21.0 | 1.21.0 | Current |
| commons-lang3 | 3.20.0 | 3.20.0 | Current |
| commons-csv | 1.14.1 | 1.14.1 | Current |
| commons-dbcp2 | 2.14.0 | 2.14.0 | Current |
| **HttpClient** | **4.5.14** | **4.5.14 (5.6 avail)** | **Maintenance-only** — 4.x EOL, migrate to 5.x |
| **HttpMime** | **4.5.14** | **merged into 5.x** | **Maintenance-only** — folded into httpclient5 |

**HttpClient migration notes**: Apache provides an [official migration guide](https://hc.apache.org/httpcomponents-client-5.6.x/migration-guide/migration-to-classic.html) and [OpenRewrite has automated recipes](https://docs.openrewrite.org/recipes/apache/httpclient5/upgradeapachehttpclient_5). No CVEs on 4.5.14, so not urgent.

### 3. PDF / Document Processing

| Dependency | POM Version | Latest | Status |
|-----------|-------------|--------|--------|
| Apache PDFBox | 3.0.7 | 3.0.7 | Current — monitor CVE-2026-23907 (example-code only) |
| OpenPDF | 3.0.3 | 3.0.3 | Current |
| OpenRTF | 3.0.0 | 3.0.0 | Current |
| Flying Saucer PDF | 10.1.0 | 10.1.0 | Current — pom comment says "10.0.7", needs fix |
| JasperReports | 7.0.6 | 7.0.6 | Current — CVE-2025-10492 already patched |
| TwelveMonkeys ImageIO | 3.13.1 | 3.13.1 | Current |
| Apache POI | 5.5.1 | 5.5.1 | Current |
| commonmark-java | 0.27.1 | 0.27.1 | Current |
| **ultrabuk-htmltopdf** | **1.0.11** | **1.0.11** | **ABANDONED** — wkhtmltopdf archived, Qt WebKit EOL |

**ultrabuk-htmltopdf replacement options**: Flying Saucer (already in project), OpenHTMLtoPDF, or headless Chrome/Chromium.

### 4. Healthcare / HL7 / FHIR

| Dependency | POM Version | Latest | Status |
|-----------|-------------|--------|--------|
| **HAPI v2 (HL7)** | **1.0.1** | **2.5.1** | **SEVERELY OUTDATED** — 15 years behind, security + Jakarta fixes |
| **HAPI FHIR** | **6.10.5** | **8.6.2** | **CRITICAL** — 6.x uses javax.*, must upgrade for Jakarta EE 11 |
| org.hl7.fhir.core | 6.7.10 | 6.7.10 | Current — align with HAPI FHIR 8.x transitives |
| CDS local_repo JARs | various | N/A | **Audit** for javax.* imports |

**HAPI FHIR 7.0.0** was the javax → jakarta migration release. Upgrading from 6.10.5 to 8.6.2 is the most impactful change in this audit — it touches servlet, persistence, and XML binding namespaces. DSTU3 structures should still be available in 8.x.

**HAPI v2 2.5.1** (Jakarta variant) adds Jakarta servlet compatibility, new HL7 v2.7/v2.8 structure support, and XML parser security fixes.

### 5. Security Libraries

| Dependency | POM Version | Latest | Status |
|-----------|-------------|--------|--------|
| OWASP Encoder | 1.4.0 | 1.4.0 | Current — no CVEs |
| OWASP Encoder Jakarta JSP | 1.4.0 | 1.4.0 | Current — built for jakarta.servlet |
| OWASP CSRFGuard | 4.5.0-jakarta | 4.5.0-jakarta | Current — no CVEs on 4.x |
| Bouncy Castle bcpkix | 1.83 | 1.83 | Current — 1.84 in development |
| MySQL Connector/J | 9.6.0 | 9.6.0 | Current |

### 6. XML / Data Processing

| Dependency | POM Version | Latest | Status |
|-----------|-------------|--------|--------|
| **XMLBeans** | **3.1.0** | **5.3.0** | **SEVERELY OUTDATED** — 5 major versions behind |
| JDOM2 | 2.0.6.1 | 2.0.6.1 | Current but **DORMANT** — no release since 2021 |
| Jackson Databind | 2.21.1 | 2.21.1 | Current (LTS) — Jackson 3.x exists but separate migration |
| Jackson Jakarta XMLB | 2.21.1 | 2.21.1 | Current |
| Jackson XML | 2.21.1 | 2.21.1 | Current |
| Jackson Jakarta RS | 2.21.1 | 2.21.1 | Current |
| JAXB API | 4.0.5 | 4.0.5 | Current |
| JAXB Runtime | 4.0.6 | 4.0.6 | Current |
| Gson (managed) | 2.13.2 | 2.13.2 | Current — Google maintenance mode |
| SAAJ Impl | 3.0.4 | 3.0.4 | Current |
| JAX-WS RI | 4.0.3 | 4.0.3 | Current |

### 7. Web / UI

| Dependency | POM Version | Latest | Status |
|-----------|-------------|--------|--------|
| JSTL (Glassfish) | 3.0.1 | 3.0.1 | Current — Jakarta EE 10 JSTL, works on EE 11 |
| Displaytag (hazendaz) | 3.7.0 | 3.7.0 | Current — DataTables migration planned Q2 2026 |
| JSoup | 1.22.1 | 1.22.1 | Current — actively maintained |
| ZXing | 3.5.4 | 3.5.4 | Current but **maintenance mode** |
| Velocity Engine | 2.4.1 | 2.4.1 | Current |
| Velocity Tools | 3.1 | 3.1 | Current |
| Apache Ant | 1.10.15 | 1.10.15 | Current — consider removing (only 4 files use it) |

### 8. Logging

| Dependency | POM Version | Latest | Status |
|-----------|-------------|--------|--------|
| Log4j2 | 2.25.3 | 2.25.3 | Current |
| SLF4J | 2.0.17 | 2.0.17 | Current |
| commons-logging | 1.3.6 | 1.3.6 | Current — required by Spring 7 (replaces spring-jcl) |

Logging stack is correct: SLF4J → Log4j2, Spring → commons-logging 1.3.6 → Log4j2, Legacy → log4j-1.2-api → Log4j2.

### 9. Testing

| Dependency | POM Version | Latest | Status |
|-----------|-------------|--------|--------|
| Mockito | 5.23.0 | 5.23.0 | Current |
| AssertJ | 3.27.7 | 3.27.7 | Current |
| H2 Database | 2.4.240 | 2.4.240 | Current |
| **DBUnit** | **2.7.3** | **3.0.0** | **Outdated** — 3.0.0 adds JUnit 5, drops JUnit 4 |
| JaCoCo | 0.8.14 | 0.8.14 | Current |
| Checkstyle | 13.3.0 | 13.3.0 | Current |

### 10. Miscellaneous

| Dependency | POM Version | Latest | Status |
|-----------|-------------|--------|--------|
| Google Guava | 33.5.0-jre | 33.5.0-jre | Current |
| Caffeine | 3.2.3 | 3.2.3 | Current |
| Drools | 10.1.0 | 10.1.0 | Current |
| JFreeChart | 1.5.6 | 1.5.6 | Current |
| java-otp | 0.4.0 | 0.4.0 | Current |
| JetBrains Annotations | 26.1.0 | 26.1.0 | Current |
| JavaMelody | 2.6.0 | 2.6.0 | Current — 2.x is Jakarta EE edition |

### 11. Jakarta EE APIs

| Dependency | POM Version | Latest | EE 11 Aligned |
|-----------|-------------|--------|---------------|
| jakarta.servlet-api | 6.1.0 | 6.1.0 | Yes — Servlet 6.1 |
| jakarta.servlet.jsp-api | 4.0.0 | 4.0.0 | Yes — Pages 4.0 |
| jakarta.annotation-api | 3.0.0 | 3.0.0 | Yes — Annotations 3.0 |
| jakarta.inject-api | 2.0.1 | 2.0.1 | Yes — DI 2.0 |
| jakarta.persistence-api | 3.2.0 | 3.2.0 | Yes — Persistence 3.2 |
| jakarta.transaction-api | 2.0.1 | 2.0.1 | Yes — Transactions 2.0 |
| jakarta.xml.bind-api | 4.0.5 | 4.0.5 | Yes — JAXB 4.0 |
| Eclipse Angus Mail | 2.0.5 | 2.0.5 | Yes — Jakarta Mail 2.1 |

### 12. Transitive / Managed Dependencies

| Dependency | POM Version | Latest | Status |
|-----------|-------------|--------|--------|
| Netty BOM | 4.1.131.Final | 4.1.131.Final | Current on 4.1.x — all 2025 CVEs patched |
| Byte Buddy | 1.18.7 | 1.18.7 | Current |
| Xerces | 2.12.2 | 2.12.2 | Current but **DORMANT** — 4+ years no release |
| Xalan | 2.7.3 | 2.7.3 | Current but **EOL** — being retired |

### 13. Maven Build Plugins

All 13 plugins are at their latest stable versions. No updates needed.

| Plugin | Version |
|--------|---------|
| maven-compiler-plugin | 3.15.0 |
| maven-war-plugin | 3.5.1 |
| maven-resources-plugin | 3.5.0 |
| maven-clean-plugin | 3.5.0 |
| maven-dependency-plugin | 3.10.0 |
| maven-site-plugin | 3.21.0 |
| maven-antrun-plugin | 3.2.0 |
| maven-pmd-plugin | 3.28.0 |
| maven-checkstyle-plugin | 3.6.0 |
| maven-project-info-reports-plugin | 3.9.0 |
| maven-jxr-plugin | 3.6.0 |
| buildnumber-maven-plugin | 3.3.0 |
| doxia-module-markdown | 2.0.0 |

---

## Dormant / EOL Dependencies (Risk Registry)

These dependencies are on their latest versions but the projects themselves are dormant or
end-of-life. No immediate action needed, but they represent long-term maintenance risk
because any future CVEs will go unpatched.

| Dependency | Last Release | Risk | Mitigation |
|-----------|-------------|------|------------|
| JDOM2 2.0.6.1 | Dec 2021 | Low — no CVEs, zero deps, narrow API surface | Migrate to JDK DOM when capacity allows |
| Xerces 2.12.2 | Jan 2022 | Low — JDK includes built-in parser fork | Evaluate removing in favor of JDK parser |
| Xalan 2.7.3 | Apr 2023 | Medium — EOL, no future security patches | Evaluate JDK built-in XSLT processor |
| ZXing 3.5.4 | Nov 2025 | Low — maintenance mode, security patches only | No action unless CVE emerges |
| ultrabuk-htmltopdf 1.0.11 | 2020 | **High** — wkhtmltopdf archived, Qt WebKit EOL | Replace with Flying Saucer or OpenHTMLtoPDF |

---

## Recommended Upgrade Sequence

1. **XMLBeans 3.1.0 → 5.3.0** — Straightforward version bump, test AR2005/CKD XML processing
2. **HAPI v2 1.0.1 → 2.5.1** — Update all 5 structure JARs; test HL7 message parsing
3. **HAPI FHIR 6.10.5 → 8.6.2** — Largest change; javax→jakarta namespace migration throughout FHIR code
4. **DBUnit 2.7.3 → 3.0.0** — Or evaluate removal since modern tests use H2 + Spring @Transactional
5. **HttpClient 4.5.14 → 5.6** — Use OpenRewrite automated recipes; httpmime merges into httpclient5
6. **CXF 4.1.5 → 4.2.0** — After Jackson 3 migration dependency is resolved

---

*Generated with Claude Code — 2026-03-17*
