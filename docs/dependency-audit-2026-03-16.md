# CARLOS EMR — Full Dependency Audit

**Date**: March 16, 2026
**Stack**: Java 21 / Spring 7.0.6 / Hibernate 7.2.7 / Struts 7.1.1 / Tomcat 11.0 / Jakarta EE 11

---

## 1. Critical Issues

| Dependency | Version | Issue | Action |
|---|---|---|---|
| **HAPI FHIR** (`hapi-fhir-base`, `hapi-fhir-structures-dstu3`) | 6.10.5 | Uses `javax.*` namespace — incompatible with Jakarta EE 11 / Tomcat 11 / Spring 7. Runtime correctness risk. Latest is 8.8.0 (Feb 2026). | Upgrade to 7.x or 8.8.0 |
| **HAPI HL7v2** (`hapi-base`, 5 structure JARs) | 1.0.1 | 4+ major versions behind (latest 2.6.0, Feb 2025). From ~2009. pom.xml exclusions for xalan/jdom are workarounds for issues fixed upstream. | Upgrade to 2.6.0 |
| **ultrabuk-htmltopdf-java** (`com.github.openosp`) | 1.0.11 | Based on **deprecated wkhtmltopdf** (abandoned Jan 2023, obsolete WebKit). JitPack-only distribution, no public source repo, no security audit trail. pom.xml itself notes "Upgrades from this dependency are welcome." | Replace with `openhtmltopdf` (PDFBox-backed) or LibrePDF stack |
| **H2** (`com.h2database`, test scope) | 2.2.224 | CVE-2025-32966 (RCE via case-sensitivity bypass on INIT/RUNSCRIPT) + CVE-2025-49002 (CVSS 8.2, patch bypass). Fixed in 2.4.240. | Upgrade to 2.4.240 |

## 2. High Priority

| Dependency | Version | Latest | Issue |
|---|---|---|---|
| **HttpClient 4.x** (`org.apache.httpcomponents:httpclient`) | 4.5.14 | 5.6 (`client5`) | EOL trajectory. Spring 6+ dropped native HC4 support. Migration to `org.apache.httpcomponents.client5:httpclient5` recommended. |
| **HttpMime 4.x** (`org.apache.httpcomponents:httpmime`) | 4.5.14 | absorbed into `httpclient5` | Same EOL trajectory. Multipart functionality moves to HC5. |
| **ScribeJava** (`com.github.scribejava:scribejava-core`) | 8.3.3 | 8.3.3 (latest) | **Abandoned** — last release Nov 2022, maintainer unresponsive. OAuth library in PHI-adjacent context. Replace with Spring Security OAuth2 Client or Nimbus OAuth2/OIDC SDK. |
| **TOTP** (`dev.samstevens.totp:totp`) | 1.7.1 | 1.7.1 (latest) | **Abandoned** — last release Nov 2020, no maintainer responses since 2022. Security-critical MFA library. Replace with `com.eatthepath:java-otp` or implement via `javax.crypto` HMAC directly. |
| **XMLBeans** (`org.apache.xmlbeans:xmlbeans`) | 3.1.0 | 5.3.0 | 2 major versions behind (from 2019). Typically a transitive dependency of Apache POI. Upgrade may require schema regeneration. |
| **commons-digester3** (`org.apache.commons:commons-digester3`) | 3.2 | 3.2 (latest) | **EOL** — last release 2011 (14 years ago). Transitive `commons-beanutils` may expose CVE-2025-48734 (CVSS 8.8). CARLOS already excludes beanutils, but digester3 itself needs replacement (JAXB, Jackson XML). |

## 3. Medium Priority

| Dependency | Version | Latest | Issue |
|---|---|---|---|
| **Apache CXF** (all modules) | 4.1.5 | 4.2.0 | EE 10 library on EE 11 container. 4.2.0 requires Jackson 3.x + Spring 7 (which CARLOS now has). Upgrade blocked by Jackson 2→3 migration. CXF 4.1.5 works via forward compatibility. All CVEs patched at 4.1.5. |
| **jaxws-ri** (`com.sun.xml.ws`) | 4.0.3 | 4.0.3 (latest) | EE 10 implementation on EE 11 container. Same forward-compat situation as CXF. Low activity but not abandoned. |
| **saaj-impl** (`com.sun.xml.messaging.saaj`) | 3.0.4 | 3.0.4 (latest) | EE 10 implementation on EE 11 container. No CVEs. |
| **Jettison** (`org.codehaus.jettison`) | 1.5.4 | 1.5.4 (latest) | Low activity (last release Mar 2023). All known CVEs patched. Transitive CXF dependency. |
| **JDOM2** (`org.jdom:jdom2`) | 2.0.6.1 | 2.0.6.1 (latest) | **Unmaintained** — last release Dec 2021. CVE-2021-33813 (XXE) is patched in this version. Risk for healthcare XML parsing with no upstream to patch future vulnerabilities. Consider migration to Jackson XML, dom4j 2.x, or JDK built-in APIs. |
| **Velocity Tools** (`velocity-tools-generic`) | 3.1 | 3.1 (latest) | **Dormant** — last release Feb 2021. No Jakarta `jakarta.*` support. OK since CARLOS only uses `velocity-tools-generic` (no servlet dependency). |
| **PDFBox** (`org.apache.pdfbox`) | 3.0.7 | 3.0.7 (latest) | CVE-2026-23907 (path traversal in ExtractEmbeddedFiles, CVSS 5.4). Mitigated by CARLOS's mandatory `PathValidationUtils` usage. Watch for 3.0.8. |
| **flying-saucer-pdf** (`org.xhtmlrenderer`) | 10.1.0 | 10.0.6 (confirmed on Maven Central) | Version 10.1.0 may not exist on Maven Central — verify resolution source. Potential classpath overlap with `openpdf-html` (LibrePDF's fork of Flying Saucer). |
| **DBUnit** (`org.dbunit`) | 2.7.3 | 3.0.0 | Major version available (Jan 2025) with JUnit 5 native support. Since CARLOS uses JUnit 5, upgrading aligns with the modern test framework. Breaking API changes expected. |
| **commons-io** (`commons-io`) | 2.21.0 | 2.22.0 | Minor bump available. Low risk. |
| **org.hl7.fhir.utilities** / **org.hl7.fhir.dstu3** (overrides) | 6.7.10 | 6.8.2 | One minor version behind. Low-risk bump for validator improvements. |

## 4. Low Priority / Monitor

| Dependency | Version | Status |
|---|---|---|
| **Xalan** (`xalan:xalan`) | 2.7.3 | Current but stale project. No XSLT 2.0+ support. Consider JDK built-in or Saxon HE long-term. |
| **Xerces** (`xerces:xercesImpl`) | 2.12.2 | Current but stale. JDK's built-in XML parser (Xerces-derived) may replace the explicit dependency. |
| **XStream** (`com.thoughtworks.xstream`) | 1.4.21 | Current. Long CVE history — all patched at 1.4.21. Audit deserialization call sites for untrusted input; ensure allowed-type filtering is active. |
| **ZXing** (`com.google.zxing:core`, `javase`) | 3.5.4 | Maintenance mode only (no new features). Current version. No CVEs. |
| **Gson** (`com.google.code.gson`) | 2.13.2 | Maintenance mode (Google). Current and secure. |
| **CommonMark** (`org.commonmark`) | 0.27.1 | Current. **XSS risk**: no built-in HTML sanitization. If rendering user-supplied markdown, output must pass through a sanitizer. |
| **JFreeChart** (`org.jfree`) | 1.5.6 | Slow development pace. Current version. v2.0 on roadmap but unreleased. |
| **Drools** (`org.drools:drools-engine`) | 10.1.0 | Current. 10.2 delayed in Apache incubator process. Used standalone via KIE API — no Spring coupling. |
| **Jackson 2.x** (all modules) | 2.21.1 | Current 2.x LTS. Jackson 3.x migration will eventually be needed for CXF 4.2 and full Spring 7 ecosystem alignment. |

## 5. All Clear — Current, Maintained, No Issues

These dependencies are at their latest versions with no known CVEs or compatibility concerns:

**Logging**: Log4j2 2.25.3, SLF4J 2.0.17, commons-logging 1.3.6

**Core Framework**: Spring Framework 7.0.6 (BOM), Spring Security 7.0.4, Hibernate 7.2.7.Final, Struts 7.1.1, struts2-spring-plugin 7.1.1, Caffeine 3.2.3

**Database**: MySQL Connector/J 9.6.0, commons-dbcp2 2.14.0, ByteBuddy 1.18.7

**Jackson/JSON**: jackson-databind 2.21.1, jackson-module-jakarta-xmlbind-annotations 2.21.1, jackson-dataformat-xml 2.21.1, jackson-jakarta-rs-json-provider 2.21.1

**Security**: OWASP Encoder 1.4.0, encoder-jakarta-jsp 1.4.0, CSRFGuard 4.5.0-jakarta (all modules), Bouncy Castle bcpkix-jdk18on 1.83

**Jakarta EE APIs**: jakarta.servlet-api 6.1.0, jakarta.servlet.jsp-api 4.0.0, jakarta.servlet.jsp.jstl 3.0.1, jakarta.annotation-api 3.0.0, jakarta.inject-api 2.0.1, jakarta.xml.bind-api 4.0.5, jakarta.persistence-api 3.2.0, jakarta.transaction-api 2.0.1

**JAXB**: jaxb-runtime 4.0.6, jaxb-core 4.0.6

**Networking**: Netty BOM 4.1.131.Final, Angus Mail 2.0.5

**PDF/Documents**: OpenPDF 3.0.3, openpdf-html 3.0.3, OpenRTF 3.0.0

**Imaging**: TwelveMonkeys common-lang 3.13.1, imageio-tiff 3.13.1

**Reporting**: JasperReports 7.0.6 (core, pdf, jdt, excel-poi), Apache POI 5.5.1, JavaMelody 2.6.0, DisplayTag 3.7.0

**Apache Commons**: commons-text 1.15.0, commons-validator 1.10.1, commons-collections4 4.5.0, commons-codec 1.21.0, commons-lang3 3.20.0, commons-csv 1.14.1

**Utilities**: Guava 33.5.0-jre, Jsoup 1.22.1, JetBrains Annotations 26.1.0, Velocity Engine 2.4.1, Apache Ant 1.10.15

**Test**: Mockito 5.23.0, AssertJ 3.27.7

**local_repo**: CDS JARs (cds 5.2.3, cds_cihi 1.0, cds_cihi_phcvrs 1.0, cds_rourke 1.0, cds_hrm 4.3.1) — XMLBeans-generated healthcare schema bindings. No CVEs, no transitive dependencies. Supply-chain opacity risk (binary blobs, no public upstream), but accepted pattern for Canadian healthcare schema bindings.

## 6. Systemic Observations

### EE 10 vs EE 11 Split

The core stack (Spring 7, Hibernate 7, Tomcat 11, Struts 7) is fully Jakarta EE 11. However, several dependencies still target EE 10:

- **CXF 4.1.5** — EE 10 (4.2.0 is EE 11 but requires Jackson 3)
- **HAPI FHIR 6.10.5** — still `javax.*` (pre-EE 10)
- **jaxws-ri 4.0.3** — EE 10
- **saaj-impl 3.0.4** — EE 10
- **JSTL 3.0.1** — EE 10

These work via Tomcat 11's forward compatibility but represent a version tension that will need resolution as the ecosystem moves to EE 11-native releases.

### Jackson 2→3 Migration Dependency Chain

CXF 4.2.0 requires Jackson 3.x (`tools.jackson.*` packages). Since Jackson 2 and 3 use different package names, they can coexist on the classpath. However, a full migration would require:

1. Jackson 2.x → 3.x (package rename across codebase)
2. CXF 4.1.5 → 4.2.x
3. Verify all JAX-RS providers and Spring JSON handling

This is a future epic, not a single upgrade task.

### Abandoned Libraries in Security-Critical Roles

Two abandoned libraries serve security-critical functions:
- **ScribeJava** (OAuth) — last release Nov 2022
- **TOTP** (MFA) — last release Nov 2020

Both should be replaced with actively maintained alternatives before they accumulate unpatched vulnerabilities.

## 7. Summary Statistics

| Category | Count |
|---|---|
| Total dependencies audited | ~85 |
| Current and clean | ~60 |
| Critical (upgrade now) | 4 |
| High priority | 6 |
| Medium priority | 12 |
| Low / monitor | 9 |
| Abandoned / EOL | 4 (ScribeJava, TOTP, commons-digester3, ultrabuk-htmltopdf) |
| Unmaintained but current | 3 (JDOM2, Velocity Tools, ZXing) |

---

*Generated with [Claude Code](https://claude.ai/code) — 12 parallel agents auditing dependency groups via web search against Maven Central, NVD, GitHub, and project documentation.*
