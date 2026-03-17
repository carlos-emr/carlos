# CARLOS EMR Jakarta Dependency Audit — March 2026 (Corrected)

**Corrected audit** of all Maven dependencies not at their latest stable version,
plus abandoned/dormant libraries still in the dependency tree.

**Audit date**: March 17, 2026 (corrected from March 16 original)
**Platform**: Java 21, Jakarta EE 10/11, Tomcat 11.0, Spring 7.0.6, Hibernate 7.2.7

---

## Corrections from Original Audit

The March 16 audit contained several material errors:

1. **Jackson 3.x omitted** — Jackson 3.0.0 GA released Oct 3, 2025 (5 months ago).
   The audit listed Jackson 2.21.1 as the target without mentioning the 3.x line exists.
2. **CXF 4.2.0 dismissed on false grounds** — CXF 4.2.0 released Feb 17, 2026 with
   Jakarta EE 11 + Spring 7 + Jackson 3. The audit said "skip, blocked by Jackson 3"
   while Jackson 3 was already GA.
3. **flying-saucer-pdf 10.1.0 is a phantom version** — does not exist on Maven Central.
   Latest is 10.0.6. The pom.xml was updated to a non-existent version.
4. **PDFBox listed as 2.0.35** — was already upgraded to 3.0.7 on this branch.
5. **DBUnit listed for upgrade** — was unused and has since been removed entirely.
6. **Several "up to date" entries were wrong** — Hibernate was listed as 7.2.6
   when 7.2.7 was out; other versions were stale.

---

## Dependencies NOT at Latest Stable

These are the only dependencies in pom.xml that are behind their latest stable release
or have available major version upgrades. Everything else is current.

### Critical — Security or Major Version Gap

| Dependency | Current | Latest Stable | Gap | Notes |
|---|---|---|---|---|
| `ca.uhn.hapi:hapi-base` (+ 5 structure modules) | **1.0.1** | **2.6.0** | 15 years behind | Jakarta servlet support, XML parser security fix. All 6 modules must upgrade together. |
| `org.apache.xmlbeans:xmlbeans` | **3.1.0** | **5.3.0** | 4 major versions | Used by POI. Check POI 5.5.1 transitive compatibility before upgrading. |
| `ca.uhn.hapi.fhir:hapi-fhir-base` (+ dstu3) | **6.10.5** | **~8.3.10** | 2 major versions | Jakarta namespace change in 7.0.0, interceptor API changes. Large migration. |
| `org.apache.httpcomponents:httpclient` (+ httpmime) | **4.5.14** | **4.5.14** (EOL) | EOL, successor: **httpclient5 5.6** | HttpClient 4.x is maintenance-only/final. Successor is `org.apache.hc.client5:httpclient5`. httpmime absorbed into httpclient5. |

### Major Version Available (GA, Not Just RC)

| Dependency | Current | Latest 2.x/Current Line | Latest Major | Notes |
|---|---|---|---|---|
| `com.fasterxml.jackson.core:jackson-databind` (+ 3 modules) | **2.21.1** | 2.21.1 (current line latest) | **3.0.4** (`tools.jackson` groupId, GA Oct 2025) | New groupId `tools.jackson.*`, can coexist with 2.x. Jackson 3.1.0 branch also open. |
| `org.apache.cxf:*` (all modules) | **4.1.5** | 4.1.5 (Jakarta EE 10) | **4.2.0** (Jakarta EE 11, Feb 2026) | Requires Jackson 3.0, Spring 7, Hibernate 7.2, Tomcat 11. We already have Spring 7 + Hibernate 7.2 + Tomcat 11. |
| `com.github.mwiede:jsch` | **not in pom** | — | **2.27.8** | Audit listed 0.2.19 but jsch is not currently a direct dependency. Verify if it's transitive. |
| `commons-collections:commons-collections` | **3.2.2** | 3.2.2 (EOL) | **commons-collections4 4.5.0** | Package rename `org.apache.commons.collections` → `org.apache.commons.collections4`. OpenRewrite recipe available. |

### Behind Latest Stable (Same Major Line)

| Dependency | Current | Latest Stable | Behind By | Drop-in? |
|---|---|---|---|---|
| `org.xhtmlrenderer:flying-saucer-pdf` | **10.1.0** (INVALID) | **10.0.6** | Current version does not exist on Maven Central | Must downgrade to 10.0.6 |

### Phantom/Invalid Versions in pom.xml

| Dependency | Version in pom.xml | Exists on Maven Central? | Correct Latest |
|---|---|---|---|
| `flying-saucer-pdf` | 10.1.0 | **NO** — latest is 10.0.6, dev snapshot is 10.0.7-SNAPSHOT | 10.0.6 |

---

## Abandoned / Dormant / EOL Dependencies Still in Tree

| Dependency | Current | Last Release | Status | Recommendation |
|---|---|---|---|---|
| `com.github.openosp:ultrabuk-htmltopdf-java` | 1.0.11 | Unknown | **Dead-end** — wraps archived wkhtmltopdf project | Replace with openpdf-html or flying-saucer-pdf (both already in stack) |
| `xerces:xercesImpl` | 2.12.2 | 2022 | **Dormant** — 3 years no release, known CVEs | JDK built-in XML parser may suffice for most uses |
| `xalan:xalan` + `serializer` | 2.7.3 | 2023 | **Dormant** — JDK includes XSLT 1.0 processing | Evaluate if JDK built-in XSLT is sufficient |
| `org.jdom:jdom2` | 2.0.6.1 | 2020 | **Dormant** — 6 years no release | Low risk (stable API) but no security patches |
| `commons-collections:commons-collections` | 3.2.2 | 2015 | **EOL** — superseded by commons-collections4 | Migrate to `commons-collections4` 4.5.0 |
| `org.apache.httpcomponents:httpclient` | 4.5.14 | 2023 (final) | **EOL** — 4.x is maintenance-only, final release | Migrate to `httpclient5` 5.6 |
| `org.apache.httpcomponents:httpmime` | 4.5.14 | 2023 (final) | **EOL** — absorbed into httpclient5 | Drops away with httpclient5 migration |

---

## Dependencies Confirmed at Latest Stable

All other dependencies are at their latest stable version as of March 17, 2026:

| Category | Dependencies (all current) |
|---|---|
| **Spring** | spring-framework-bom 7.0.6, spring-security-crypto 7.0.4 |
| **Hibernate** | hibernate-core 7.2.7.Final |
| **Jakarta EE APIs** | servlet-api 6.1.0, persistence-api 3.2.0, transaction-api 2.0.1, annotation-api 3.0.0, inject-api 2.0.1, jsp-api 4.0.0, jstl 3.0.1 |
| **JAXB** | jakarta.xml.bind-api 4.0.5, jaxb-runtime 4.0.6, jaxb-core 4.0.6 |
| **Struts** | struts2-core 7.1.1, struts2-spring-plugin 7.1.1 |
| **Jackson 2.x** | jackson-databind 2.21.1, jackson-module-jakarta-xmlbind 2.21.1, jackson-dataformat-xml 2.21.1, jackson-jakarta-rs-json-provider 2.21.1 |
| **Logging** | log4j-core/api/1.2-api 2.25.3, slf4j-api 2.0.17, commons-logging 1.3.6 |
| **Apache Commons** | commons-text 1.15.0, commons-io 2.21.0, commons-codec 1.21.0, commons-lang3 3.20.0, commons-csv 1.14.1, commons-dbcp2 2.14.0, commons-validator 1.10.1 |
| **PDF/Reporting** | pdfbox 3.0.7, openpdf 3.0.3, openrtf 3.0.0, jasperreports 7.0.6, jfreechart 1.5.6, poi 5.5.1 |
| **Security** | encoder 1.4.0, encoder-jakarta-jsp 1.4.0, csrfguard 4.5.0-jakarta, bcpkix-jdk18on 1.83 |
| **Web/Mail** | commonmark 0.27.1, angus-mail 2.0.5, jsoup 1.22.1, displaytag 3.7.0 |
| **Infrastructure** | byte-buddy 1.18.7, caffeine 3.2.3, netty-bom 4.1.131.Final, guava 33.5.0-jre, velocity-engine-core 2.4.1, velocity-tools-generic 3.1 |
| **Healthcare** | org.hl7.fhir.utilities 6.7.10, org.hl7.fhir.dstu3 6.7.10 |
| **Other** | drools-engine 10.1.0, zxing 3.5.4, ant 1.10.15, saaj-impl 3.0.4, jaxws-ri 4.0.3, twelvemonkeys 3.13.1, java-otp 0.4.0 |
| **Build Plugins** | All 13 plugins current (compiler 3.15.0, surefire 3.5.5, war 3.5.1, checkstyle 13.3.0, pmd 7.22.0, etc.) |
| **Test** | junit-jupiter 6.0.3, mockito 5.23.0, assertj 3.27.7, h2 2.4.240, mysql-connector-j 9.6.0 |

---

## Prioritized Action Plan (Revised)

### Phase 1 — Fix Invalid Version (Immediate)
1. `flying-saucer-pdf` 10.1.0 → **10.0.6** (current version is phantom, does not exist on Maven Central)

### Phase 2 — Security (High Priority)
2. `xmlbeans` 3.1.0 → **5.3.0** (4 major versions behind, verify POI compatibility)
3. `hapi-base` + 5 structure modules 1.0.1 → **2.6.0** (15 years behind, Jakarta servlet + security)

### Phase 3 — Jackson 3 + CXF 4.2 Migration (Planned)
4. Jackson 2.21.1 → **3.0.4** (new `tools.jackson` groupId, coexists with 2.x during migration)
5. CXF 4.1.5 → **4.2.0** (requires Jackson 3, gives Jakarta EE 11 alignment)

### Phase 4 — EOL Library Migrations
6. `commons-collections` 3.2.2 → `commons-collections4` **4.5.0** (package rename, OpenRewrite recipe)
7. `httpclient` 4.5.14 → `httpclient5` **5.6** (major API change, httpmime absorbed)

### Phase 5 — HAPI FHIR Major Migration
8. `hapi-fhir-base` + dstu3 6.10.5 → **~8.3.x** (Jakarta namespace in 7.0, interceptor API changes)

### Phase 6 — Removals
9. Remove `ultrabuk-htmltopdf-java` 1.0.11 (wraps archived wkhtmltopdf)
10. Evaluate removal of `xerces` 2.12.2 (dormant, JDK built-in may suffice)
11. Evaluate removal of `xalan` 2.7.3 (dormant, JDK built-in XSLT)

---

## Removed Since Original Audit

These dependencies were listed in the original audit but have since been removed:

| Dependency | Was | Action Taken |
|---|---|---|
| `org.dbunit:dbunit` | 2.7.3 | **Removed** — unused, no tests loaded DBUnit XML datasets |
| `com.google.code.gson:gson` | 2.10.1 | **Removed** — sole usage migrated to Jackson |
| `org.codehaus.jettison:jettison` | 1.5.4 | **Removed** — unmaintained, CVE history |
| `com.github.scribejava:scribejava-core` | 8.3.3 | **Removed** — OAuth 1.0a is fully in-house |
| `org.testng:testng` | 7.5.1 | **Removed** — all tests migrated to JUnit 5 |
| `commons-digester3` | 3.2 | **Removed** — replaced with JAXB for XML parsing |
| `com.thoughtworks.xstream:xstream` | 1.4.21 | **Removed** — dead dependencyManagement entry cleaned up |
| `dev.samstevens.totp:totp` | 1.7.1 | **Replaced** with `com.eatthepath:java-otp` 0.4.0 |
| `org.owasp.encoder:encoder-jsp` | 1.4.0 | **Replaced** with `encoder-jakarta-jsp` 1.4.0 (Jakarta namespace) |
| `org.apache.pdfbox:pdfbox` | 2.0.35 | **Upgraded** to 3.0.7 (major version migration completed) |

---

*Corrected with Claude Code — March 17, 2026*
