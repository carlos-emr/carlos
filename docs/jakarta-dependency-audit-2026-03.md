# CARLOS EMR Jakarta Dependency Audit — March 2026

Comprehensive audit of all Maven dependencies in `pom.xml` for version currency, Jakarta EE compatibility, and migration opportunities.

**Audit date**: March 16, 2026
**Target platform**: Java 21, Jakarta EE 10/11, Tomcat 10.1, Spring 7.x, Hibernate 7.x

---

## Executive Summary

- **Total dependencies audited**: 123 (direct + managed + plugins)
- **Already up to date**: ~65
- **Minor/patch updates available**: ~30
- **Significantly outdated or EOL**: ~12
- **Recommended removals**: 3 (jcharts, Jettison, ultrabuk-htmltopdf)
- **Major migration candidates**: 4 (HttpClient 5.x, HAPI HL7v2 2.x, HAPI FHIR 8.x, commons-collections4)

### Critical Actions (Security)

| Dependency | Current | Latest | Reason |
|-----------|---------|--------|--------|
| JasperReports (all 4 modules) | 7.0.3 | **7.0.4** | CVE-2025-10492 security fix |
| HAPI HL7v2 (all 6 modules) | 1.0.1 | **2.6.0** | XML parser security vulnerability fix + Jakarta servlet support |
| XMLBeans | 3.1.0 | **5.3.0** | 4 major versions behind, security improvements |
| Checkstyle (plugin dep) | 10.20.1 | **13.3.0** | 3 major versions behind |
| encoder-jsp | 1.4.0 | 1.4.0 | Consider switch to `encoder-jakarta-jsp` for proper Jakarta namespace |

---

## 1. Jakarta EE / Spring / Hibernate

| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.2 | **4.0.5** | Upgrade available |
| `org.glassfish.jaxb:jaxb-runtime` | 4.0.5 | **4.0.6** | Upgrade available |
| `org.glassfish.jaxb:jaxb-core` | 4.0.5 | **4.0.6** | Upgrade available |
| `org.springframework:spring-framework-bom` | 7.0.6 | 7.0.6 | Up to date |
| `org.springframework.security:spring-security-crypto` | 7.0.3 | 7.0.3 | Up to date |
| `org.hibernate.orm:hibernate-core` | 7.2.6.Final | 7.2.6.Final | Up to date |
| `jakarta.persistence:jakarta.persistence-api` | 3.2.0 | 3.2.0 | Up to date |
| `jakarta.transaction:jakarta.transaction-api` | 2.0.1 | 2.0.1 | Up to date |
| `jakarta.servlet:jakarta.servlet-api` | 6.1.0 | 6.1.0 | Up to date |
| `jakarta.annotation:jakarta.annotation-api` | 3.0.0 | 3.0.0 | Up to date |
| `jakarta.inject:jakarta.inject-api` | 2.0.1 | 2.0.1 | Up to date |
| `jakarta.servlet.jsp:jakarta.servlet.jsp-api` | 4.0.0 | 4.0.0 | Up to date |
| `org.glassfish.web:jakarta.servlet.jsp.jstl` | 3.0.1 | 3.0.1 | Up to date |
| `com.sun.xml.ws:jaxws-ri` | 4.0.3 | 4.0.3 | Up to date |
| `com.sun.xml.messaging.saaj:saaj-impl` | 3.0.4 | 3.0.4 | Up to date |
| `net.bytebuddy:byte-buddy` | 1.17.7 | **1.18.6** | Upgrade available (Java 26 compat) |
| `org.apache.struts:struts2-core` | 7.1.1 | 7.1.1 | Up to date |
| `org.apache.struts:struts2-spring-plugin` | 7.1.1 | 7.1.1 | Up to date |

**Actions**:
- Upgrade `jakarta.xml.bind-api` 4.0.2 → 4.0.5 (drop-in)
- Upgrade `jaxb-runtime` + `jaxb-core` 4.0.5 → 4.0.6 (upgrade together)
- Upgrade `byte-buddy` + `byte-buddy-agent` 1.17.7 → 1.18.6 (module-info support, Java 26 compat)

---

## 2. Apache Commons / Logging

| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| `commons-logging:commons-logging` | 1.3.5 | 1.3.5 | Up to date |
| `org.apache.commons:commons-text` | 1.15.0 | 1.15.0 | Up to date |
| `org.apache.logging.log4j:log4j-core` | 2.25.3 | 2.25.3 | Up to date |
| `org.apache.logging.log4j:log4j-api` | 2.25.3 | 2.25.3 | Up to date |
| `org.apache.logging.log4j:log4j-1.2-api` | 2.25.3 | 2.25.3 | Up to date |
| `org.slf4j:slf4j-api` | 2.0.17 | 2.0.17 | Up to date |
| `org.apache.logging.log4j:log4j-slf4j2-impl` | 2.25.3 | 2.25.3 | Up to date |
| `commons-validator:commons-validator` | 1.10.1 | 1.10.1 | Up to date |
| `commons-io:commons-io` | 2.21.0 | 2.21.0 | Up to date |
| `commons-codec:commons-codec` | 1.18.0 | **1.21.0** | 3 minor versions behind |
| `org.apache.commons:commons-lang3` | 3.18.0 | **3.20.0** | 2 minor versions behind |
| `org.apache.commons:commons-csv` | 1.12.0 | **1.14.1** | Upgrade available |
| `org.apache.commons:commons-dbcp2` | 2.14.0 | 2.14.0 | Up to date |
| `commons-collections:commons-collections` | 3.2.2 | 3.2.2 (EOL) | **MIGRATE to commons-collections4 4.5.0** |
| `org.apache.commons:commons-digester3` | 3.2 | 3.2 (2011) | **UNMAINTAINED — evaluate removal** |

**Actions**:
- Upgrade `commons-codec` 1.18.0 → 1.21.0 (drop-in)
- Upgrade `commons-lang3` 3.18.0 → 3.20.0 (drop-in)
- Upgrade `commons-csv` 1.12.0 → 1.14.1 (drop-in)
- **Plan migration**: `commons-collections` 3.2.2 → `commons-collections4` 4.5.0 (API changes, package rename `org.apache.commons.collections` → `org.apache.commons.collections4`, OpenRewrite recipe available)
- **Evaluate**: `commons-digester3` 3.2 — unmaintained since 2011, has CVEs in transitive deps. Consider replacing with JAXB, Jackson XML, or direct SAX/StAX

---

## 3. HTTP Client

| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| `org.apache.httpcomponents:httpclient` | 4.5.14 | 4.5.14 (final) | **MIGRATE to httpclient5 5.6** |
| `org.apache.httpcomponents:httpmime` | 4.5.14 | 4.5.14 (final) | Absorbed into httpclient5 |

**Actions**:
- **Plan migration**: HttpClient 4.x is maintenance-only. HttpClient 5.x (`org.apache.httpcomponents.client5:httpclient5` 5.6) adds HTTP/2, async APIs, modern TLS. New package: `org.apache.hc.client5`. Official migration guide and OpenRewrite recipe available. `httpmime` gets absorbed into httpclient5 (no separate artifact needed).

---

## 4. CXF / Jackson / Web Services

| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| `org.apache.cxf:*` (all modules) | 4.1.5 | 4.1.5 | Up to date (CXF 4.2.0 targets Jakarta EE 11 — skip for now) |
| `com.fasterxml.jackson.core:jackson-databind` | 2.19.2 | **2.21.1** | Upgrade available |
| `jackson-module-jakarta-xmlbind-annotations` | 2.19.2 | **2.21.1** | Upgrade with Jackson BOM |
| `jackson-dataformat-xml` | 2.19.2 | **2.21.1** | Upgrade with Jackson BOM |
| `jackson-jakarta-rs-json-provider` | 2.19.2 | **2.21.1** | Upgrade with Jackson BOM |
| `com.google.code.gson:gson` | 2.10.1 | **2.13.2** | 3 minor versions behind (maintenance mode) |
| `org.codehaus.jettison:jettison` | 1.5.4 | 1.5.4 | **REMOVE — unmaintained since 2023, multiple CVEs** |
| `com.github.scribejava:scribejava-core` | 8.3.3 | 8.3.3 | Up to date (stable, no releases since 2022) |
| `org.commonmark:commonmark` | 0.23.0 | **0.27.1** | 4 minor versions behind |
| `org.eclipse.angus:angus-mail` | 2.0.3 | **2.0.5** | Upgrade available |
| `io.netty:netty-bom` | 4.1.129.Final | **4.1.131.Final** | 2 patches behind (4.2.x is separate migration) |
| `org.apache.velocity:velocity-engine-core` | 2.4.1 | 2.4.1 | Up to date |
| `org.apache.velocity.tools:velocity-tools-generic` | 3.1 | 3.1 | Up to date |

**Actions**:
- Upgrade all Jackson modules 2.19.2 → 2.21.1 (coordinated upgrade, all must match)
- Upgrade `gson` 2.10.1 → 2.13.2 (drop-in)
- **Remove** `jettison` 1.5.4 — unmaintained, CVE history, replaced by Jackson
- Upgrade `commonmark` 0.23.0 → 0.27.1 (adds MarkdownRenderer, JPMS support, footnotes)
- Upgrade `angus-mail` 2.0.3 → 2.0.5
- Upgrade `netty-bom` 4.1.129.Final → 4.1.131.Final

---

## 5. PDF / Reporting / Charting

| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| `org.apache.pdfbox:pdfbox` | 2.0.35 | 2.0.35 | Up to date (3.x exists but major migration) |
| `com.github.librepdf:openpdf` | 3.0.2 | **3.0.3** | Upgrade available |
| `com.github.librepdf.openpdf:openpdf-html` | 3.0.2 | **3.0.3** | Upgrade available |
| `com.github.librepdf:openrtf` | 3.0.0 | 3.0.0 | Up to date |
| `net.sf.jasperreports:jasperreports` | 7.0.3 | **7.0.4** | **SECURITY: CVE-2025-10492** |
| `net.sf.jasperreports:jasperreports-pdf` | 7.0.3 | **7.0.4** | Same as above |
| `net.sf.jasperreports:jasperreports-jdt` | 7.0.3 | **7.0.4** | Same as above |
| `net.sf.jasperreports:jasperreports-excel-poi` | 7.0.3 | **7.0.4** | Same as above |
| `org.jfree:jfreechart` | 1.5.6 | 1.5.6 | Up to date |
| `jcharts:jcharts` | 0.7.5 | 0.7.5 (2004) | **ABANDONED — remove, migrate to JFreeChart** |
| `org.apache.poi:poi` | 5.5.1 | 5.5.1 | Up to date |
| `org.xhtmlrenderer:flying-saucer-pdf` | 10.0.7 | **10.1.0** | Upgrade available |
| `com.twelvemonkeys.common:common-lang` | 3.13.0 | **3.13.1** | Upgrade available |
| `com.twelvemonkeys.imageio:imageio-tiff` | 3.13.0 | **3.13.1** | Upgrade available |
| `org.apache.xmlbeans:xmlbeans` | 3.1.0 | **5.3.0** | **4 major versions behind** |
| `com.github.openosp:ultrabuk-htmltopdf-java` | 1.0.11 | 1.0.11 | **Dead-end — wraps archived wkhtmltopdf** |

**Actions**:
- **URGENT**: Upgrade all JasperReports modules 7.0.3 → 7.0.4 (security fix)
- **HIGH**: Upgrade `xmlbeans` 3.1.0 → 5.3.0 (check POI transitive dep compatibility)
- Upgrade `openpdf` + `openpdf-html` 3.0.2 → 3.0.3
- Upgrade `flying-saucer-pdf` 10.0.7 → 10.1.0
- Upgrade `twelvemonkeys` (both) 3.13.0 → 3.13.1
- **Plan removal**: `jcharts` 0.7.5 — abandoned since 2004, JFreeChart already available
- **Plan replacement**: `ultrabuk-htmltopdf-java` — wraps archived wkhtmltopdf, replace with openpdf-html or flying-saucer-pdf

---

## 6. Security / OWASP / Crypto

| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| `org.owasp.encoder:encoder` | 1.4.0 | 1.4.0 | Up to date |
| `org.owasp.encoder:encoder-jsp` | 1.4.0 | 1.4.0 | Up to date (consider `encoder-jakarta-jsp` for Jakarta namespace) |
| `org.owasp:csrfguard` | 4.5.0-jakarta | 4.5.0-jakarta | Up to date |
| `org.owasp:csrfguard-extension-session` | 4.5.0-jakarta | 4.5.0-jakarta | Up to date |
| `org.owasp:csrfguard-jsp-tags` | 4.5.0-jakarta | 4.5.0-jakarta | Up to date |
| `org.bouncycastle:bcpkix-jdk18on` | 1.83 | 1.83 | Up to date |
| `com.github.mwiede:jsch` | 0.2.19 | **2.27.8** | **Major version jump available** |
| `xerces:xercesImpl` | 2.12.2 | 2.12.2 | Up to date (project dormant) |
| `xalan:xalan` | 2.7.3 | 2.7.3 | Up to date (project dormant) |
| `xalan:serializer` | 2.7.3 | 2.7.3 | Up to date |
| `com.thoughtworks.xstream:xstream` | 1.4.21 | 1.4.21 | Up to date |
| `org.apache.mina:mina-core` | 2.1.10 | **2.2.5** | Minor series upgrade available |
| `dev.samstevens.totp:totp` | 1.7.1 | 1.7.1 | Up to date (dormant since 2020) |
| `com.github.ben-manes.caffeine:caffeine` | 3.1.8 | **3.2.3** | Upgrade available |

**Actions**:
- Upgrade `caffeine` 3.1.8 → 3.2.3 (minor version, Java 21 compatible)
- Upgrade `mina-core` 2.1.10 → 2.2.5 (review changelog for API changes)
- **Evaluate**: `jsch` 0.2.19 → 2.27.8 — major version jump (versioning scheme changed), thorough testing required
- **Evaluate**: Switch `encoder-jsp` to `encoder-jakarta-jsp` for proper Jakarta servlet namespace
- **Watch**: `totp` 1.7.1 — unmaintained since 2020, consider `com.eatthepath:java-otp` long-term

---

## 7. Healthcare / HAPI

| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| `ca.uhn.hapi.fhir:hapi-fhir-base` | 6.10.5 | **8.8.0** | Major upgrade (Jakarta in 7.0.0) |
| `ca.uhn.hapi.fhir:hapi-fhir-structures-dstu3` | 6.10.5 | **8.8.0** | Same as above |
| `ca.uhn.hapi.fhir:org.hl7.fhir.utilities` | 6.4.0 | **6.8.2** | Minor upgrade |
| `ca.uhn.hapi.fhir:org.hl7.fhir.dstu3` | 6.4.0 | **6.5.26** | Minor upgrade |
| `ca.uhn.hapi:hapi-base` | 1.0.1 | **2.6.0** | **Major — Jakarta servlet + security fix** |
| `ca.uhn.hapi:hapi-structures-v25` | 1.0.1 | **2.6.0** | Same as above |
| `ca.uhn.hapi:hapi-structures-v231` | 1.0.1 | **2.6.0** | Same as above |
| `ca.uhn.hapi:hapi-structures-v22` | 1.0.1 | **2.6.0** | Same as above |
| `ca.uhn.hapi:hapi-structures-v23` | 1.0.1 | **2.6.0** | Same as above |
| `ca.uhn.hapi:hapi-structures-v26` | 1.0.1 | **2.6.0** | Same as above |

**Actions**:
- **HIGH**: Upgrade HAPI HL7v2 (all 6 modules) 1.0.1 → 2.6.0 — Jakarta servlet support (2.5+), XML parser security fix
- **Plan migration**: HAPI FHIR 6.10.5 → 8.8.0 — Jakarta namespace change in 7.0.0, interceptor API changes, requires min Java 17 (we have 21). Large migration, do separately.
- Upgrade `org.hl7.fhir.utilities` 6.4.0 → 6.8.2
- Upgrade `org.hl7.fhir.dstu3` 6.4.0 → 6.5.26

---

## 8. Miscellaneous

| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| `org.drools:drools-engine` | 10.0.0 | **10.1.0** | Minor upgrade (Apache KIE incubator) |
| `com.google.zxing:core` + `javase` | 3.5.4 | 3.5.4 | Up to date |
| `org.jsoup:jsoup` | 1.22.1 | 1.22.1 | Up to date |
| `org.jdom:jdom2` | 2.0.6.1 | 2.0.6.1 | Up to date (low activity) |
| `com.google.guava:guava` | 33.5.0-jre | 33.5.0-jre | Up to date |
| `org.jetbrains:annotations` | 26.0.2-1 | 26.0.2 | Current (drop `-1` suffix for standard artifact) |
| `org.apache.ant:ant` | 1.10.15 | 1.10.15 | Up to date |
| `com.github.hazendaz:displaytag` | 3.7.0 | 3.7.0 | Up to date (Jakarta compatible) |
| `com.mysql:mysql-connector-j` | 9.3.0 | **9.6.0** | 3 minor versions behind |

**Actions**:
- Upgrade `mysql-connector-j` 9.3.0 → 9.6.0
- Upgrade `drools-engine` 10.0.0 → 10.1.0
- Consider normalizing `jetbrains:annotations` to 26.0.2 (drop `-1` suffix)

---

## 9. Test Dependencies

| Dependency | Current | Latest | Status |
|-----------|---------|--------|--------|
| `org.junit.jupiter:junit-jupiter` | 6.0.3 | 6.0.3 | Up to date |
| `org.junit.jupiter:junit-jupiter-params` | 6.0.3 | 6.0.3 | Up to date |
| `org.mockito:mockito-core` | 5.21.0 | **5.23.0** | 2 minor behind |
| `org.mockito:mockito-junit-jupiter` | 5.21.0 | **5.23.0** | 2 minor behind |
| `org.assertj:assertj-core` | 3.27.7 | 3.27.7 | Up to date |
| `com.h2database:h2` | 2.2.224 | **2.4.240** | Significantly behind |
| `org.dbunit:dbunit` | 2.7.3 | **3.0.0** | Major version behind |
| `org.seleniumhq.selenium:selenium-java` | 4.40.0 | **4.41.0** | 1 minor behind |
| `org.testng:testng` | 7.5.1 | **7.12.0** | **Very outdated (7 minor behind)** |

**Actions**:
- Upgrade `mockito-core` + `mockito-junit-jupiter` 5.21.0 → 5.23.0
- Upgrade `selenium-java` 4.40.0 → 4.41.0
- **Evaluate**: `h2` 2.2.224 → 2.4.240 — test carefully, H2 can have behavioral changes
- **Evaluate**: `dbunit` 2.7.3 → 3.0.0 — major version, check for breaking changes
- **Evaluate**: `testng` 7.5.1 → 7.12.0 — very outdated, requires Java 11+ since 7.6.0

---

## 10. Build Plugins

| Plugin | Current | Latest | Status |
|--------|---------|--------|--------|
| `jacoco-maven-plugin` | 0.8.14 | 0.8.14 | Up to date |
| `maven-resources-plugin` | 3.3.1 | **3.5.0** | Behind |
| `buildnumber-maven-plugin` | 3.2.0 | **3.3.0** | 1 minor behind |
| `maven-site-plugin` | 3.21.0 | 3.21.0 | Up to date |
| `maven-compiler-plugin` | 3.13.0 | **3.15.0** | 2 minor behind |
| `maven-clean-plugin` | 3.5.0 | 3.5.0 | Up to date |
| `maven-dependency-plugin` | 3.8.1 | **3.10.0** | 2 minor behind |
| `maven-antrun-plugin` | 3.1.0 | **3.2.0** | 1 minor behind |
| `maven-war-plugin` | 3.4.0 | **3.5.1** | Behind |
| `maven-checkstyle-plugin` | 3.6.0 | 3.6.0 | Up to date |
| `com.puppycrawl.tools:checkstyle` | 10.20.1 | **13.3.0** | **3 major versions behind** |
| `maven-pmd-plugin` | 3.28.0 | 3.28.0 | Up to date |
| `net.sourceforge.pmd:pmd-core` | 7.20.0 | **7.22.0** | 2 minor behind |
| `net.sourceforge.pmd:pmd-java` | 7.20.0 | **7.22.0** | 2 minor behind |
| `maven-surefire-plugin` | 3.5.4 | **3.5.5** | 1 patch behind |
| `build-helper-maven-plugin` | 3.6.0 | **3.6.1** | 1 patch behind |
| `dependency-lock-maven-plugin` | 1.1.0 | **1.1.1** | 1 patch behind |
| `maven-javadoc-plugin` | 3.11.2 | **3.12.0** | 1 minor behind |
| `maven-project-info-reports-plugin` | 3.9.0 | 3.9.0 | Up to date |
| `maven-jxr-plugin` | 3.6.0 | 3.6.0 | Up to date |
| `doxia-module-markdown` | 2.0.0 | 2.0.0 | Up to date |

**Actions**:
- **HIGH**: Upgrade `checkstyle` 10.20.1 → 13.3.0 (3 major versions behind)
- Upgrade `maven-compiler-plugin` 3.13.0 → 3.15.0
- Upgrade `maven-resources-plugin` 3.3.1 → 3.5.0
- Upgrade `maven-dependency-plugin` 3.8.1 → 3.10.0
- Upgrade `maven-war-plugin` 3.4.0 → 3.5.1
- Upgrade `maven-surefire-plugin` 3.5.4 → 3.5.5
- Upgrade `pmd-core` + `pmd-java` 7.20.0 → 7.22.0
- Upgrade remaining plugins (buildnumber, antrun, build-helper, dependency-lock, javadoc)

---

## 11. Dormant/EOL Dependencies to Watch

These dependencies are still current but their projects show very low activity:

| Dependency | Last Release | Notes |
|-----------|-------------|-------|
| `xerces:xercesImpl` | 2021 | JDK built-in XML parser may suffice |
| `xalan:xalan` + `serializer` | 2023 | JDK includes XSLT processing |
| `dev.samstevens.totp:totp` | 2020 | Consider `com.eatthepath:java-otp` |
| `org.jdom:jdom2` | 2020 | Very low activity |
| `com.github.scribejava:scribejava-core` | 2022 | Stable/feature-complete, low risk |

---

## Prioritized Action Plan

### Phase 1 — Security Fixes (Immediate)
1. JasperReports 7.0.3 → 7.0.4 (CVE-2025-10492)
2. XMLBeans 3.1.0 → 5.3.0 (4 major versions behind)
3. HAPI HL7v2 1.0.1 → 2.6.0 (XML parser security + Jakarta servlet)

### Phase 2 — Drop-in Upgrades (Low Risk)
4. Jackson all modules 2.19.2 → 2.21.1
5. commons-codec 1.18.0 → 1.21.0
6. commons-lang3 3.18.0 → 3.20.0
7. commons-csv 1.12.0 → 1.14.1
8. Gson 2.10.1 → 2.13.2
9. jakarta.xml.bind-api 4.0.2 → 4.0.5
10. jaxb-runtime + jaxb-core 4.0.5 → 4.0.6
11. byte-buddy 1.17.7 → 1.18.6
12. mysql-connector-j 9.3.0 → 9.6.0
13. commonmark 0.23.0 → 0.27.1
14. angus-mail 2.0.3 → 2.0.5
15. netty-bom 4.1.129.Final → 4.1.131.Final
16. openpdf 3.0.2 → 3.0.3
17. flying-saucer-pdf 10.0.7 → 10.1.0
18. twelvemonkeys 3.13.0 → 3.13.1
19. caffeine 3.1.8 → 3.2.3
20. drools-engine 10.0.0 → 10.1.0

### Phase 3 — Plugin Updates
21. Checkstyle 10.20.1 → 13.3.0
22. All Maven plugins (compiler, resources, dependency, war, surefire, etc.)
23. PMD 7.20.0 → 7.22.0
24. Test deps (mockito, selenium, H2, TestNG)

### Phase 4 — Migrations (Planned)
25. `commons-collections` 3.2.2 → `commons-collections4` 4.5.0 (package rename)
26. `httpclient` 4.5.14 → `httpclient5` 5.6 (major API change)
27. `encoder-jsp` → `encoder-jakarta-jsp` (Jakarta namespace)
28. `jsch` 0.2.19 → 2.27.8 (major version, thorough testing)
29. `mina-core` 2.1.10 → 2.2.5 (review API changes)

### Phase 5 — Removals/Replacements
30. Remove `jettison` 1.5.4 (unmaintained, CVE history, replaced by Jackson)
31. Remove `jcharts` 0.7.5 (abandoned since 2004, migrate to JFreeChart)
32. Replace `ultrabuk-htmltopdf-java` (wraps archived wkhtmltopdf)
33. Evaluate `commons-digester3` removal (unmaintained since 2011)

### Phase 6 — Major Migrations (Future)
34. HAPI FHIR 6.10.5 → 8.8.0 (Jakarta namespace change, interceptor API)
35. PDFBox 2.0.35 → 3.x (major API rewrite)
36. H2 2.2.224 → 2.4.240 (test database, behavioral changes)
37. dbunit 2.7.3 → 3.0.0 (test infrastructure, breaking changes)

---

*Generated with Claude Code — March 16, 2026*
