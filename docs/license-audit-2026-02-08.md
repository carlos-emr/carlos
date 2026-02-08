# CARLOS EMR - Dependency License Audit

**Date:** 2026-02-08
**Project License:** GPL-2.0-only
**Acceptable Licenses:** MIT, Apache 2.0, GPL-2.0, LGPL (2.1), BSD, Public Domain

## Executive Summary

Audit of all dependencies declared in `pom.xml`. Three dependencies have
licenses **incompatible** with GPL-2.0-only. Seven local-repo libraries have
**no license declared** and require verification.

| Category | Count |
|----------|-------|
| INCOMPATIBLE - Must Fix | 3 |
| UNKNOWN - Needs Verification | 7 |
| Compatible (runtime) | ~80 |
| Compatible (test-scope only) | ~12 |

---

## INCOMPATIBLE LICENSES - ACTION REQUIRED

### 1. com.itextpdf:itextpdf 5.5.13.5 - AGPL-3.0

**Problem:** iText 5.x is licensed under AGPL-3.0. The AGPL-3.0 is incompatible
with GPL-2.0-only because it imposes additional requirements (network-use
provision in Section 13) not present in GPL-2.0, and GPL-2.0 cannot satisfy
GPL-3.0/AGPL-3.0 terms.

**Recommended Fix:** Replace with [OpenPDF](https://github.com/LibrePDF/OpenPDF)
(`com.github.librepdf:openpdf`), which is dual-licensed LGPL-2.1+/MPL-2.0.
OpenPDF is the community fork of the pre-AGPL iText codebase and is API-compatible
for most use cases.

```xml
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>2.0.3</version>
</dependency>
```

### 2. com.itextpdf.tool:xmlworker 5.5.13.5 - AGPL-3.0

**Problem:** Same AGPL-3.0 issue as itextpdf above. xmlworker is the iText XML/HTML
to PDF conversion module.

**Recommended Fix:** Remove xmlworker and use OpenPDF's built-in HTML support, or
use [openhtmltopdf](https://github.com/openhtmltopdf/openhtmltopdf) (LGPL-2.1+)
which is based on Flying Saucer + PDFBox.

### 3. net.sf.jasperreports:jasperreports 6.21.5 - LGPL-3.0

**Problem:** LGPL-3.0 is defined as GPL-3.0 with additional permissions. Since
GPL-3.0 is incompatible with GPL-2.0-only, LGPL-3.0 inherits that incompatibility.
A GPL-2.0-only project cannot incorporate LGPL-3.0 libraries because the combined
work would need to satisfy GPL-3.0 terms that conflict with GPL-2.0.

**Recommended Fix:** This is harder to resolve. Options include:
- Check if an older JasperReports version was under LGPL-2.1 (some 5.x versions may have been)
- Accept the LGPL-3.0 library under a "system library" exception argument (legally uncertain)
- Consider switching the project license to GPL-2.0-or-later (which would make LGPL-3.0 compatible)
- Evaluate alternative reporting libraries (e.g., Apache POI for Excel, PDFBox for PDF)

**Note:** JasperReports 6.20.1+ removed the iText dependency internally, so at
least there is no transitive AGPL contamination through JasperReports itself.

---

## UNKNOWN LICENSE - VERIFICATION REQUIRED

These libraries are in `local_repo/` with no license declared in their POM files
or JAR manifests. They appear to be Canadian healthcare data standard stubs and
generated JAXB/XMLBeans classes.

| # | Dependency | Version | Description |
|---|-----------|---------|-------------|
| 4 | cds:cds | 5.2.3 | Canadian Data Standard XML schema classes |
| 5 | cds:cds_cihi | 1.0 | CIHI (Canadian Institute for Health Information) data |
| 6 | cds:cds_cihi_phcvrs | 1.0 | CIHI PHC VRS data |
| 7 | cds:cds_rourke | 1.0 | Rourke baby record data standard |
| 8 | cds:cds_hrm | 4.3.1 | Hospital Report Manager data standard |
| 9 | omd:hrm | 4.3 | Ontario MD HRM JAXB generated classes |
| 10 | ca.ssha.www:olis-service | 20111111 | Ontario Labs Information System service stubs |

**Action Required:** Determine the provenance of these JARs:
- If they were generated from XSD schemas published by Ontario/Canadian health
  authorities, the schemas themselves may be government-published (public domain
  or open license), and the generated code would inherit the project license
- If they were built as part of the OSCAR EMR project, they would be GPL-2.0+
  (the upstream OSCAR license), which is compatible with GPL-2.0
- If they came from a third-party with an unknown license, they need to be
  replaced or relicensed

---

## COMPATIBLE DEPENDENCIES - FULL LISTING

### MIT License

| Dependency | Version | Notes |
|-----------|---------|-------|
| org.slf4j:slf4j-api | 2.0.17 | |
| com.onelogin:java-saml | 2.9.0 | |
| org.jsoup:jsoup | 1.17.2 | |
| com.github.scribejava:scribejava-apis | 8.3.3 | |
| com.github.scribejava:scribejava-core | 8.3.3 | |
| com.github.openosp:ultrabuk-htmltopdf-java | 1.0.11 | |
| com.github.hazendaz:displaytag | 2.9.0 | hazendaz fork is MIT |
| org.bouncycastle:bcpkix-jdk18on | 1.79 | MIT-like (Bouncy Castle License) |

### Apache License 2.0

| Dependency | Version | Notes |
|-----------|---------|-------|
| net.bull.javamelody:javamelody-core | 1.99.4 | |
| commons-logging:commons-logging | 1.2 | |
| org.apache.commons:commons-text | 1.13.1 | |
| org.apache.logging.log4j:log4j-core | 2.25.3 | |
| org.apache.logging.log4j:log4j-api | 2.25.3 | |
| org.apache.logging.log4j:log4j-1.2-api | 2.25.3 | |
| commons-validator:commons-validator | 1.9.0 | |
| commons-io:commons-io | 2.18.0 | |
| commons-fileupload:commons-fileupload | 1.6.0 | |
| commons-collections:commons-collections | 3.2.2 | |
| org.apache.commons:commons-digester3 | 3.2 | |
| commons-codec:commons-codec | 1.18.0 | |
| org.apache.commons:commons-compress | 1.26.0 | |
| org.apache.commons:commons-lang3 | 3.18.0 | |
| commons-net:commons-net | 3.11.1 | |
| org.apache.httpcomponents:httpclient | 4.5.14 | |
| org.apache.httpcomponents:httpmime | 4.5.14 | |
| com.google.code.gson:gson | 2.10.1 | |
| org.springframework:spring-core | 5.3.39 | |
| org.springframework:spring-tx | 5.3.39 | |
| org.springframework:spring-orm | 5.3.39 | |
| org.springframework:spring-webmvc | 5.3.39 | |
| org.springframework:spring-aspects | 5.3.39 | |
| org.springframework:spring-test | 5.3.39 | |
| org.springframework:spring-aop | 5.3.39 | |
| org.springframework:spring-context-support | 5.3.39 | |
| org.springframework.integration:spring-integration-ftp | 5.5.20 | |
| org.springframework.integration:spring-integration-sftp | 5.5.20 | |
| org.springframework.security:spring-security-crypto | 6.3.9 | |
| net.sf.ehcache:ehcache | 2.10.9.2 | |
| org.apache.commons:commons-dbcp2 | 2.14.0 | |
| org.apache.pdfbox:pdfbox | 2.0.35 | |
| org.apache.xmlbeans:xmlbeans | 3.1.0 | |
| org.apache.poi:poi | 5.5.1 | |
| com.google.zxing:core | 3.5.3 | |
| com.google.zxing:javase | 3.5.3 | |
| org.apache.ant:ant | 1.10.15 | |
| org.apache.velocity:velocity-engine-core | 2.4.1 | |
| org.apache.velocity.tools:velocity-tools-generic | 3.1 | |
| org.apache.cxf:apache-cxf | 3.6.9 | |
| org.apache.cxf:cxf-rt-frontend-jaxws | 3.6.9 | |
| org.apache.cxf:cxf-rt-transports-http | 3.6.9 | |
| org.apache.cxf:cxf-core | 3.6.9 | |
| org.apache.cxf:cxf-rt-rs-client | 3.6.9 | |
| org.apache.axis2:axis2 | 1.8.2 | |
| org.apache.axis2:axis2-transport-http | 1.8.2 | |
| org.apache.axis2:axis2-adb | 1.8.2 | |
| com.fasterxml.jackson.core:jackson-databind | 2.19.2 | |
| com.fasterxml.jackson.datatype:jackson-datatype-jsr310 | 2.19.2 | |
| com.fasterxml.jackson.module:jackson-module-jaxb-annotations | 2.19.2 | |
| com.fasterxml.jackson.dataformat:jackson-dataformat-xml | 2.19.2 | |
| com.fasterxml.jackson.jaxrs:jackson-jaxrs-json-provider | 2.19.2 | |
| org.codehaus.jettison:jettison | 1.5.4 | |
| org.jasypt:jasypt | 1.9.3 | |
| org.apache.commons:commons-exec | 1.4.0 | |
| com.google.guava:guava | 33.4.8-jre | |
| org.apache.openjpa:openjpa | 3.2.2 | |
| ca.uhn.hapi.fhir:hapi-fhir-base | 6.10.5 | |
| ca.uhn.hapi.fhir:hapi-fhir-structures-dstu2 | 6.10.5 | |
| ca.uhn.hapi.fhir:hapi-fhir-structures-dstu3 | 6.10.5 | |
| org.apache.struts:struts2-core | 6.8.0 | |
| org.apache.struts:struts2-spring-plugin | 6.8.0 | |
| com.github.ben-manes.caffeine:caffeine | 3.1.8 | |
| org.jboss.aerogear:aerogear-otp-java | 1.0.0 | |
| org.jetbrains:annotations | 24.1.0 | |
| drools:drools-all | 2.0 | JBoss Drools rules engine |
| xmlrpc:xmlrpc | 1.2-b1 | Apache XML-RPC |
| javax.inject:javax.inject | 1 | |
| cglib:cglib-nodep | 3.3.0 | |

### BSD License (2-clause, 3-clause, or BSD-like)

| Dependency | Version | License Variant | Notes |
|-----------|---------|----------------|-------|
| org.owasp.encoder:encoder-jsp | 1.2.3 | BSD-3-Clause | |
| org.owasp.encoder:encoder | 1.2.1 | BSD-3-Clause | |
| org.owasp:csrfguard | 3.1.0 | BSD-3-Clause | |
| org.owasp.esapi:esapi | 2.6.2.0 | BSD-3-Clause | |
| org.commonmark:commonmark | 0.23.0 | BSD-2-Clause | |
| com.github.mwiede:jsch | 0.2.19 | BSD-3-Clause | Modified JSch fork |
| com.twelvemonkeys.common:common-lang | 3.12.0 | BSD-3-Clause | |
| janino:janino | 2.3.2 | BSD/Apache | v2.x was LGPL->Apache->BSD |
| jcharts:jcharts | 0.7.5 | BSD-like (Krysalis) | |

### LGPL-2.1 (Compatible with GPL-2.0)

| Dependency | Version | Notes |
|-----------|---------|-------|
| org.hibernate:hibernate-core | 5.6.15.Final | LGPL-2.1 |
| org.hibernate.common:hibernate-commons-annotations | 5.1.2.Final | LGPL-2.1 |
| org.jfree:jfreechart | 1.5.6 | LGPL-2.1-or-later |
| org.xhtmlrenderer:flying-saucer-pdf | 9.13.3 | LGPL-2.1+ (uses OpenPDF, NOT iText) |

### LGPL-2.1+/MPL (Compatible with GPL-2.0)

| Dependency | Version | Notes |
|-----------|---------|-------|
| com.github.librepdf:openrtf | 2.0.0 | Dual LGPL-2.1+/MPL |

### GPL-2.0 / GPL-2.0+ (Same license family)

| Dependency | Version | Notes |
|-----------|---------|-------|
| com.mysql:mysql-connector-j | 9.3.0 | GPL-2.0 + Universal FOSS Exception |
| com.ostermiller:ostermillerutils | 1.4.3 | GPL-2.0-or-later |

### Dual GPL-2.0 + HAPI License (MPL 1.1 / GPL)

| Dependency | Version | Notes |
|-----------|---------|-------|
| ca.uhn.hapi:hapi-base | 1.0.1 | Dual MPL-1.1/GPL - choose GPL |
| ca.uhn.hapi:hapi-structures-v21 | 1.0.1 | Same dual license |
| ca.uhn.hapi:hapi-structures-v22 | 1.0.1 | Same dual license |
| ca.uhn.hapi:hapi-structures-v23 | 1.0.1 | Same dual license |
| ca.uhn.hapi:hapi-structures-v231 | 1.0.1 | Same dual license |
| ca.uhn.hapi:hapi-structures-v24 | 1.0.1 | Same dual license |
| ca.uhn.hapi:hapi-structures-v25 | 1.0.1 | Same dual license |
| ca.uhn.hapi:hapi-structures-v26 | 1.0.1 | Same dual license |

### CDDL 1.1 + GPL-2.0 with Classpath Exception (Dual-licensed)

These are compatible via the GPL-2.0 option. The Classpath Exception allows
linking without the GPL's copyleft applying to the rest of the application.

| Dependency | Version | Scope | Notes |
|-----------|---------|-------|-------|
| javax.servlet:javax.servlet-api | 4.0.1 | provided | |
| org.glassfish.web:javax.servlet.jsp.jstl | 1.2.5 | compile | |
| com.sun.mail:jakarta.mail | 1.6.8 | compile | |
| com.sun.xml.messaging.saaj:saaj-impl | 1.5.3 | compile | |
| com.sun.xml.ws:jaxws-ri | 2.3.7 | compile (pom) | |
| javax.annotation:javax.annotation-api | 1.3.2 | compile | |
| javax.servlet.jsp:javax.servlet.jsp-api | 2.3.3 | provided | |
| org.glassfish.jaxb:jaxb-runtime | 2.3.9 | compile | |
| javax.xml.bind:jaxb-api | 2.3.1 | managed | |
| javax.persistence:javax.persistence-api | 2.2 | managed | EPL-1.0/CDDL dual |
| jakarta.persistence:jakarta.persistence-api | 2.2.3 | managed | EPL-2.0/CDDL dual |

### Permissive Custom License

| Dependency | Version | License | Notes |
|-----------|---------|---------|-------|
| org.jdom:jdom2 | 2.0.6.1 | JDOM License | BSD-like, Apache-compatible |

---

## TEST-SCOPE DEPENDENCIES (Not Distributed)

Test-scoped dependencies are **not packaged** into the WAR file and are **not
distributed** with the application. GPL copyleft requirements only apply to
distributed works, so test-scope license incompatibilities do not create legal
issues. Listed here for completeness.

| Dependency | Version | License | Notes |
|-----------|---------|---------|-------|
| junit:junit | 4.13.2 | EPL-1.0 | Test only |
| org.junit.jupiter:junit-jupiter | 5.10.1 | EPL-2.0 | Test only (profile) |
| org.junit.jupiter:junit-jupiter-params | 5.10.1 | EPL-2.0 | Test only (profile) |
| org.seleniumhq.selenium:selenium-java | 4.40.0 | Apache-2.0 | Test only |
| org.testng:testng | 7.5.1 | Apache-2.0 | Test only |
| org.mockito:mockito-core | 5.8.0 | MIT | Test only |
| org.mockito:mockito-junit-jupiter | 5.8.0 | MIT | Test only (profile) |
| org.assertj:assertj-core | 3.24.2 | Apache-2.0 | Test only |
| com.h2database:h2 | 2.2.224 | EPL-1.0/MPL-2.0 | Test only |
| org.dbunit:dbunit | 2.7.3 | LGPL-2.1 | Test only |
| com.microsoft.playwright:playwright | 1.40.0 | Apache-2.0 | Test only |

---

## MANAGED DEPENDENCIES (Transitive Overrides)

These are declared in `<dependencyManagement>` to pin versions of transitive
dependencies for security or compatibility. Their licenses are all compatible.

| Dependency | Version | License |
|-----------|---------|---------|
| xerces:xercesImpl | 2.12.2 | Apache-2.0 |
| org.apache.mina:mina-core | 2.1.10 | Apache-2.0 |
| io.netty:netty-bom | 4.1.129.Final | Apache-2.0 |
| org.apache.james:apache-mime4j-core | 0.8.10 | Apache-2.0 |

---

## FSF NOTE ON APACHE 2.0 + GPL-2.0

The FSF considers Apache License 2.0 to be **incompatible with GPL-2.0-only**
due to the patent termination clause in Apache 2.0 that is not present in
GPL-2.0. Apache 2.0 is only FSF-compatible with GPL-3.0+.

However, many projects (including the Linux kernel ecosystem) take a practical
position that Apache 2.0 libraries can be linked by GPL-2.0 programs. This audit
accepts Apache 2.0 as compatible per the project's stated criteria. If strict FSF
compliance is desired, all Apache 2.0 dependencies (~50) would need evaluation.

---

## RECOMMENDED ACTION PLAN

### Priority 1: Replace AGPL-3.0 Dependencies (iText)

1. Replace `com.itextpdf:itextpdf` with `com.github.librepdf:openpdf`
2. Replace `com.itextpdf.tool:xmlworker` with OpenPDF HTML support or openhtmltopdf
3. Audit all code that imports from `com.itextpdf.*` and migrate to `com.lowagie.text.*` (OpenPDF API)
4. Note: `flying-saucer-pdf` 9.13.3 already uses OpenPDF, so no issue there

### Priority 2: Resolve JasperReports LGPL-3.0

Options (choose one):
- **Option A:** Verify if JasperReports can be obtained under LGPL-2.1 (some older versions)
- **Option B:** Accept the risk with legal counsel's opinion
- **Option C:** Change project license to GPL-2.0-or-later (makes LGPL-3.0 compatible)
- **Option D:** Replace JasperReports with alternative reporting tools

### Priority 3: Verify Local Repo Libraries

1. Trace the origin of CDS, HRM, and OLIS JARs
2. Document their license (likely GPL-2.0+ from OSCAR upstream or public domain from government schemas)
3. Add LICENSE files or POM license declarations to each

---

## Methodology

This audit was performed by:
1. Reading every `<dependency>` entry in `pom.xml` (including `<dependencyManagement>` and profile dependencies)
2. Checking license declarations in POM files, Maven Central metadata, and official project websites
3. Examining `local_repo/` JARs and POMs for license information
4. Cross-referencing with the FSF license compatibility list
5. Verifying specific version licenses where licensing changed between versions (e.g., iText, Janino, displaytag)
