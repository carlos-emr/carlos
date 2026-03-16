# CARLOS EMR Dependency Audit — March 16, 2026

Comprehensive audit of all 92+ Maven dependencies in `pom.xml` against latest stable
releases. Covers version currency, Jakarta EE compatibility, and strategic recommendations.

---

## Executive Summary

- **Total dependencies audited**: 92 (including dependencyManagement, plugins, and profiles)
- **Already at latest**: 82 dependencies (89%)
- **Applied in this audit**: 13 drop-in upgrades (see Section 1a/1b)
- **Patch/minor updates remaining**: 0 safe drop-in upgrades
- **Medium-effort updates available**: 4 dependencies (test thoroughly)
- **Major version upgrades available**: 4 dependencies (require migration effort)
- **Dormant/unmaintained**: 5 dependencies (risk assessment included)

---

## 1a. Applied Updates — Batch 1 (commit ce2f30a3)

These drop-in patch/minor bumps were applied and pushed:

| Dependency | Before | After | Risk |
|-----------|--------|-------|------|
| `commons-logging:commons-logging` | 1.3.5 | **1.3.6** | Trivial |
| `org.hibernate.orm:hibernate-core` | 7.2.6.Final | **7.2.7.Final** | Low |
| `net.sf.jasperreports:jasperreports` (×4) | 7.0.4 | **7.0.6** | Low |
| `org.apache.pdfbox:pdfbox` | 2.0.35 | **2.0.36** | Trivial |
| `org.mockito:mockito-core` | 5.21.0 | **5.23.0** | Low |
| `org.mockito:mockito-junit-jupiter` | 5.21.0 | **5.23.0** | Low |
| `org.jetbrains:annotations` | 26.0.2-1 | **26.1.0** | Trivial |

## 1b. Applied Updates — Batch 2 (comprehensive audit follow-up)

Additional drop-in upgrades discovered and applied:

| Dependency | Before | After | Risk | Notes |
|-----------|--------|-------|------|-------|
| `org.springframework.security:spring-security-crypto` | 7.0.3 | **7.0.4** | Trivial | Released 2026-03-16, patch |
| `net.bytebuddy:byte-buddy` | 1.18.6 | **1.18.7** | Trivial | Non-experimental Java 24 support |
| `net.bytebuddy:byte-buddy-agent` | 1.18.6 | **1.18.7** | Trivial | Matches byte-buddy |
| `org.codehaus.mojo:buildnumber-maven-plugin` | 3.2.0 | **3.3.0** | Trivial | Build plugin, released 2026-01-18 |

## 1c. Still Pending — FHIR Core Libraries (Separate PR Recommended)

| Dependency | Current | Latest | Risk | Notes |
|-----------|---------|--------|------|-------|
| `ca.uhn.hapi.fhir:org.hl7.fhir.utilities` | 6.4.0 | **6.7.10** | Low | Security fixes for CVE-2024-45294/51132; must remain compatible with hapi-fhir-base 6.10.5 |
| `ca.uhn.hapi.fhir:org.hl7.fhir.dstu3` | 6.4.0 | **6.7.10** | Low | Follows org.hl7.fhir.core versioning |

**Recommended action**: Apply in a separate PR with FHIR integration testing.

---

## 2. Medium-Effort Updates (Moderate Risk — Test Thoroughly)

| Dependency | Current | Latest | Risk | Migration Notes |
|-----------|---------|--------|------|-----------------|
| `org.apache.xmlbeans:xmlbeans` | 3.1.0 | **5.3.0** | Medium | 2 major versions behind. API is stable but some internal changes. Maintained by Apache POI team. Avoid 5.2.2 (log4j-api issue). |
| `org.testng:testng` | 7.5.1 | **7.12.0** | Medium | Legacy test dependency only. 7.6+ requires Java 11+ (OK — we use 21). Large version gap may affect test behavior. |
| `com.h2database:h2` | 2.2.224 | **2.4.240** | **High** | Test-only dependency but H2 version changes have historically broken HQL/SQL compatibility (see CLAUDE.md integration test pitfalls). Must test all integration tests thoroughly. |
| `org.dbunit:dbunit` | 2.7.3 | **3.0.0** | **High** | Major version with potential breaking API changes. Test-only dependency. Review migration guide before upgrading. |

**Recommended action**: Create separate PRs for each. XMLBeans and TestNG can likely be done together. H2 and DBUnit need dedicated testing effort.

---

## 3. Major Version Upgrades (High Risk — Dedicated Migration Effort)

### 3a. HAPI HL7v2: 1.0.1 → 2.6.0

| Aspect | Details |
|--------|---------|
| **Version gap** | 15 years of releases (1.0.1 is from ~2009) |
| **Jakarta support** | v2.5+ uses `jakarta.servlet`; v2.6.0 is Jakarta-native |
| **Java requirement** | JDK 11+ (we use 21, OK) |
| **Security** | Includes XML parser security fix |
| **Impact** | All HL7 message processing code needs review |
| **Artifacts** | `hapi-base`, `hapi-structures-v22`, `hapi-structures-v23`, `hapi-structures-v231`, `hapi-structures-v25`, `hapi-structures-v26` — all must upgrade together |

**Recommended action**: Dedicated issue and PR. This is the most impactful Jakarta alignment upgrade remaining.

### 3b. HAPI FHIR: 6.10.5 → 8.6.0

| Aspect | Details |
|--------|---------|
| **Version gap** | 2 major versions |
| **Jakarta support** | v7.0.0 migrated `javax.*` → `jakarta.*` (breaking for interceptors) |
| **Java requirement** | v8.0.0 requires Java 17+ (we use 21, OK) |
| **Migration guide** | https://hapifhir.io/hapi-fhir/docs/interceptors/jakarta_upgrade.html |
| **Impact** | Over 600 files changed in HAPI itself; check for custom interceptors using `javax.servlet` |

**Recommended action**: Dedicated issue and PR. Aligns FHIR stack with Jakarta EE — critical for long-term compatibility.

### 3c. Apache HttpClient: 4.5.14 → 5.6

| Aspect | Details |
|--------|---------|
| **Status** | HttpClient 4.x / HttpCore 4 is **officially End of Life** |
| **New coordinates** | `org.apache.httpcomponents.client5:httpclient5:5.6` |
| **httpmime** | Absorbed into httpclient5 (no separate artifact) |
| **Package change** | `org.apache.http.*` → `org.apache.hc.client5.http.*` |
| **Migration tool** | OpenRewrite recipe available: `org.openrewrite.apache.httpclient5` |
| **Coexistence** | 5.x can coexist with 4.x on classpath (different packages) |

**Recommended action**: Plan incremental migration. No urgent CVEs in 4.5.14, but no future fixes will be issued. OpenRewrite can automate much of the migration.

### 3d. PDFBox: 2.0.x → 3.0.7 (Future)

| Aspect | Details |
|--------|---------|
| **Breaking changes** | `PDDocument.load()` removed → use `Loader.loadPDF()`, font/color API changes, new IO module |
| **Benefits** | Incremental loading, better memory, actively developed |
| **JAXB** | 3.x removed javax.xml.bind dependency (good for Jakarta) |
| **Migration guide** | https://pdfbox.apache.org/3.0/migration.html |

**Recommended action**: Apply 2.0.36 patch now. Plan 3.x migration as a separate effort — requires touching every file that uses PDFBox APIs.

---

## 4. Dormant/Unmaintained Dependencies

These libraries have not had releases in years and may never receive Jakarta updates.

| Dependency | Current | Last Release | Status | Recommendation |
|-----------|---------|-------------|--------|----------------|
| `org.apache.commons:commons-digester3` | 3.2 | 2012 | Dormant | Works on Jakarta (no servlet dependency). Replace only if issues arise. Alternatives: JAXB, Jackson XML, SAX/StAX. |
| `xerces:xercesImpl` | 2.12.2 | 2022-01 | Dormant | No newer release expected. Used for XML parsing overrides. |
| `xalan:xalan` + `serializer` | 2.7.3 | 2023-05 | Dormant | Transitive from JSTL. Required by Struts. No replacement available. |
| `org.jdom:jdom2` | 2.0.6.1 | 2021-12 | Dormant | Unofficial fork exists (`com.github.hullbend:jdom2:2.0.6.2` with Java 17 fixes). |
| `com.github.scribejava:scribejava-core` | 8.3.3 | 2022-11 | Low activity | OAuth 1.0a library. No alternative with same API. Monitor for CVEs. |

---

## 5. Dependencies Already at Latest (No Action Needed)

All of these are confirmed current as of March 16, 2026:

### Core Libraries
| Dependency | Version | Notes |
|-----------|---------|-------|
| `net.bull.javamelody:javamelody-core` | 2.6.0 | Jakarta EE 2.x line |
| `org.apache.commons:commons-text` | 1.15.0 | |
| `org.apache.logging.log4j:log4j-core/api/1.2-api` | 2.25.3 | |
| `org.slf4j:slf4j-api` | 2.0.17 | |
| `commons-validator:commons-validator` | 1.10.1 | |
| `commons-io:commons-io` | 2.21.0 | |
| `org.apache.commons:commons-collections4` | 4.5.0 | |
| `commons-codec:commons-codec` | 1.21.0 | |
| `org.apache.commons:commons-lang3` | 3.20.0 | |
| `org.apache.commons:commons-csv` | 1.14.1 | |
| `com.google.code.gson:gson` | 2.13.2 | Maintenance mode |

### Jackson (all 2.21.1)
| Dependency | Notes |
|-----------|-------|
| `jackson-databind` | LTS line. Jackson 3.1.0 exists under new groupId but is a major migration. |
| `jackson-module-jakarta-xmlbind-annotations` | Jakarta-native |
| `jackson-dataformat-xml` | |
| `jackson-jakarta-rs-json-provider` | Jakarta RS namespace |

### Spring Framework (all 7.0.6)
| Dependency | Notes |
|-----------|-------|
| `spring-framework-bom` | Released 2026-03-13 |
| `spring-core`, `spring-tx`, `spring-orm`, `spring-web`, `spring-aop`, `spring-aspects`, `spring-test`, `spring-context-support`, `spring-webmvc` | All managed by BOM |
| `spring-security-crypto` | 7.0.4 — updated in this audit |

### Jakarta EE APIs
| Dependency | Version | Notes |
|-----------|---------|-------|
| `jakarta.servlet-api` | 6.1.0 | Jakarta EE 11 |
| `jakarta.servlet.jsp-api` | 4.0.0 | |
| `jakarta.annotation-api` | 3.0.0 | |
| `jakarta.inject-api` | 2.0.1 | |
| `jakarta.persistence-api` | 3.2.0 | JPA 3.2 |
| `jakarta.transaction-api` | 2.0.1 | |
| `jakarta.xml.bind-api` | 4.0.5 | |
| `org.glassfish.jaxb:jaxb-runtime/core` | 4.0.6 | |

### Web Frameworks
| Dependency | Version | Notes |
|-----------|---------|-------|
| `struts2-core` / `struts2-spring-plugin` | 7.1.1 | |
| `caffeine` | 3.2.3 | |
| `apache-cxf` / `cxf-*` | 4.1.5 | CXF 4.2.0 targets Jakarta EE 11 — not ready |
| `velocity-engine-core` | 2.4.1 | |
| `velocity-tools-generic` | 3.1 | |
| `jakarta.servlet.jsp.jstl` | 3.0.1 | |
| `saaj-impl` | 3.0.4 | |
| `jaxws-ri` | 4.0.3 | |
| `commonmark` | 0.27.1 | |
| `displaytag` (hazendaz) | 3.7.0 | Jakarta fork |
| `angus-mail` | 2.0.5 | Jakarta Mail 2.1 |
| `netty-bom` | 4.1.131.Final | 4.2.x line exists but is new major |
| `jettison` | 1.5.4 | |

### PDF/Charts/Reports
| Dependency | Version | Notes |
|-----------|---------|-------|
| `openpdf` / `openpdf-html` | 3.0.3 | `org.openpdf` namespace |
| `openrtf` | 3.0.0 | |
| `jfreechart` | 1.5.6 | |
| `poi` | 5.5.1 | |
| `flying-saucer-pdf` | 10.1.0 | |
| `twelvemonkeys common-lang` / `imageio-tiff` | 3.13.1 | |

### Security
| Dependency | Version | Notes |
|-----------|---------|-------|
| `owasp encoder` / `encoder-jsp` | 1.4.0 | |
| `csrfguard` / `csrfguard-*` | 4.5.0-jakarta | |
| `bcpkix-jdk18on` | 1.83 | |
| `zxing core` / `javase` | 3.5.4 | |

### Database & ORM
| Dependency | Version | Notes |
|-----------|---------|-------|
| `commons-dbcp2` | 2.14.0 | |
| `mysql-connector-j` | 9.6.0 | |
| `byte-buddy` / `byte-buddy-agent` | 1.18.7 | Updated in this audit |
| `xstream` | 1.4.21 | |

### Testing
| Dependency | Version | Notes |
|-----------|---------|-------|
| `junit-jupiter` / `junit-jupiter-params` | 6.0.3 | |
| `assertj-core` | 3.27.7 | |
| `mockito-junit-jupiter` | 5.23.0 | Updated in this audit |

### Misc
| Dependency | Version | Notes |
|-----------|---------|-------|
| `jsoup` | 1.22.1 | |
| `ant` | 1.10.15 | |
| `guava` | 33.5.0-jre | |
| `drools-engine` | 10.1.0 | |
| `totp` | 1.7.1 | |

---

## 6. Strategic Recommendations

### Immediate (This Sprint) — DONE
1. ~~Apply all Section 1 updates~~ — **Applied**: 13 drop-in bumps in Sections 1a + 1b
2. Apply FHIR core library updates (Section 1c) in a separate PR

### Short-Term (Next 2-4 Weeks)
2. Upgrade XMLBeans 3.1.0 → 5.3.0
3. Upgrade TestNG 7.5.1 → 7.12.0 (legacy tests only)
4. Evaluate H2 2.2.224 → 2.4.240 with full integration test run

### Medium-Term (Next Quarter)
5. **HAPI HL7v2 1.0.1 → 2.6.0** — critical Jakarta alignment for HL7 message processing
6. **HAPI FHIR 6.10.5 → 8.6.0** — Jakarta-native FHIR stack
7. **HttpClient 4.5.14 → 5.6** — EOL dependency removal (use OpenRewrite)

### Long-Term (Future Planning)
8. PDFBox 2.x → 3.x migration (significant API changes)
9. Evaluate replacing `ultrabuk-htmltopdf-java` (JitPack/wkhtmltopdf) with Flying Saucer or OpenPDF-html (both already in deps)
10. Monitor Jackson 3.x (new groupId `tools.jackson.core`) for eventual migration
11. Monitor CXF 4.2.x for Jakarta EE 11 readiness
12. Monitor Netty 4.2.x line stability

### Dependencies to Watch (Dormant Projects)
- `commons-digester3` — dormant since 2012, no Jakarta version planned
- `jdom2` — dormant since 2021
- `scribejava-core` — low activity since 2022
- `xerces` / `xalan` — effectively dormant, no alternatives

---

## 7. Jakarta EE Compatibility Status

| Category | Status |
|----------|--------|
| **Servlet/JSP/JSTL** | Fully Jakarta EE 11 |
| **JPA/JTA** | Fully Jakarta EE 11 (Hibernate 7.2, JPA 3.2) |
| **JAXB** | Fully Jakarta (4.0.x) |
| **JAX-WS** | Fully Jakarta (Metro 4.0.3) |
| **SOAP (SAAJ)** | Fully Jakarta (3.0.4) |
| **Mail** | Fully Jakarta (Angus Mail 2.0.5) |
| **Spring** | Fully Jakarta EE 11 (Spring 7.0.6) |
| **Struts** | Fully Jakarta (7.1.1) |
| **CXF** | Fully Jakarta EE 10 (4.1.5) |
| **Hibernate** | Fully Jakarta EE 11 (7.2.x) |
| **Drools** | Fully Jakarta (10.1.0) |
| **JasperReports** | Fully Jakarta (7.0.x) |
| **HAPI HL7v2** | **NOT Jakarta** — 1.0.1 predates Jakarta. Upgrade to 2.6.0 required. |
| **HAPI FHIR** | **NOT Jakarta** — 6.10.5 uses javax. Upgrade to 7.0.0+ required. |
| **HttpClient** | N/A — no servlet dependency, but 4.x is EOL |

---

*Generated by Claude Code — March 16, 2026*
*Updated: March 16, 2026 — Applied 13 drop-in upgrades (Sections 1a + 1b)*
*Source: Web searches against Maven Central, GitHub releases, and project websites*
