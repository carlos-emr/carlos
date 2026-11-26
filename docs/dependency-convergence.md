# Dependency convergence

Tracking: [#5711](https://github.com/carlos-emr/carlos/issues/5711).

Maven picks one version of each artifact on its own: the nearest declaration wins, then the
first. A module that only one library asks for therefore keeps that library's version, however
far it is from the rest of its family. `dependencies-lock.json` records whatever was picked, so
regenerating it after a library bump records a split as readily as a fix. Two such splits
shipped in the 2026.08 WAR:

| Artifact | Was | Beside | Brought in by |
|----------|-----|--------|---------------|
| `org.apache.httpcomponents.client5:httpclient5-cache` | 5.3.1 | `httpclient5` 5.6.5 | `cxf-rt-ws-security` → `wss4j-ws-security-common` → `opensaml-saml-impl` → `shib-networking` 9.2.3 |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.17.1 | `jackson-databind` 2.22.4 | a direct pin; HAPI FHIR and OpenSAML asked for 2.21.x |

Both now resolve with their families: `httpclient5-cache` is managed at `${httpclient5.version}`,
and `com.fasterxml.jackson:jackson-bom` is imported at `${jackson.version}`, so Jackson
dependencies are declared without a version and move together. `jackson-annotations` follows
the BOM's major.minor version (`2.22`), the scheme Jackson uses since 2.20.

## Guards

| Guard | Where it runs | What it catches |
|-------|---------------|-----------------|
| `maven-enforcer-plugin`, execution `require-converged-dependencies`, `requireSameVersions` | every Maven build, `validate` phase, all scopes | a resolved artifact of a guarded family on a version different from the rest of that family |
| the same execution, `requireUpperBoundDeps` | every Maven build, `validate` phase, all scopes | a resolved version below one that some dependency asks for: a stale pin or a nearer, older request |
| the same execution, `banDuplicateClasses` (`extra-enforcer-rules`) | every Maven build, `validate` phase, compile and runtime scope | two jars in `WEB-INF/lib` defining one class with different bytes |
| `DependencyConvergenceUnitTest` | unit tests | a split Jackson or HttpComponents family in `dependencies-lock.json`, and a jsr310 jar on the test class path from another Jackson release |

The guarded families are those `pom.xml` keeps on one version property or BOM: Jackson
(except `jackson-annotations`), HttpClient 5, HttpCore 5, CXF, Spring Framework, Spring
Security, Log4j 2, Bouncy Castle, HAPI HL7 v2, HAPI FHIR, FHIR core, Drools with KIE, Struts,
JasperReports, POI and Tomcat.

`dependencyConvergence` is deliberately not used: it also fails every harmless upward mediation
(SLF4J, the Commons libraries, Guava and others) in the CXF, HAPI and Drools trees, which would
turn each library bump into a list of unrelated pins.

### Known downgrades

`requireUpperBoundDeps` lists each reviewed downgrade it ignores, with the reason, in
`pom.xml`:

| Artifact | Resolved | Asked for | Why it stays |
|----------|----------|-----------|--------------|
| `net.sf.saxon:Saxon-HE` | 11.6 | 13.0 (FHIR core) | deliberate pin: XML serialization must not change; `XmlUtilsUnitTest` |
| `com.fasterxml.woodstox:woodstox-core` | 7.2.0 | 7.2.2 (CXF) | deliberate pin at its `dependencyManagement` entry |
| `com.github.javaparser:javaparser-core` | 3.25.10 | 3.27.0 (Drools) | deliberate pin at its `dependencyManagement` entry |
| `org.apache.logging.log4j:log4j-api` | `${log4j2.version}` | 2.26.1 (Struts) | Log4j moves as one family |
| `org.eclipse.jdt:ecj` | 3.21.0 (`jasperreports-jdt`) | 3.44.0 (Drools) | not yet reviewed for a move |
| `org.ehcache:ehcache` | 3.10.8 (CXF) | 3.12.0 (WSS4J) | not yet reviewed for a move |
| `org.jboss.logging:jboss-logging` | 3.6.1.Final (Hibernate) | 3.6.3.Final (`hibernate-models`) | not yet reviewed for a move |

Remove an entry when its downgrade goes away; add one only with its reason.

## When the enforcer fails

`requireSameVersions` lists each version it found and the artifacts on it;
`requireUpperBoundDeps` prints every path to the artifact with the version each one asks for.
Manage the artifact at its family's version (its BOM or version property) in
`dependencyManagement`, rather than excluding it from the library that needs it, then regenerate
the lock (`make lock`, or `mvn se.vandmo:dependency-lock-maven-plugin:lock`) and confirm the lock
diff holds only the versions you meant to move.

```bash
mvn dependency:tree -Dverbose -Dincludes=org.apache.httpcomponents.client5
```

A deliberate downgrade (a pin kept for compatibility) goes in the `requireUpperBoundDeps`
excludes with its reason, and in the table above.

## Compatibility of the 2026.08 moves

- **`httpclient5-cache` 5.3.1 → 5.6.5.** Only `shib-networking`'s `FileCachingHttpClientBuilder`
  and `InMemoryCachingHttpClientBuilder` use it. Every class, method and field they reference
  resolves in 5.6.5 (168 references, none missing), and 5.6.5 declares the same dependencies.
- **`jackson-datatype-jsr310` 2.17.1 → 2.22.4.** CDS import and export use `JavaTimeModule`. With
  `WRITE_DATES_AS_TIMESTAMPS` disabled, as every CDS mapper that writes instants has it, the text
  written for every `java.time` type is unchanged, and ISO-8601 input reads the same. Two 2.17
  defects are fixed: a negative fractional `Duration` read back wrong (`-1.5` as `PT-0.5S`), and
  a pre-1970 fractional `Instant` written as a numeric timestamp was one second off. No CDS
  record stores either: the one mapper that writes numeric timestamps
  (`CdsExportClinicalPackageService`) carries only a `LocalDate`, whose array form is unchanged.
  `CdsNativePreparationServiceUnitTest` pins the sealed text and the read-back.
- `CdsRuntimeIdentity` hashes the Jackson jars, so the jsr310 move changes the CDS ruleset
  fingerprint, as any change to a hashed dependency or CDS class does. Finish or cancel CDS
  imports before upgrading, as [the operator guide](cds-import-operator-guide.md) requires.
