# JDOM2 maintenance decision

Assessed on 2026-10-31 against `release/2026.08`, base commit
`b4c0dfc94e677514e6f64dd271358f0295270739`.

## Decision

Retain `org.jdom:jdom2:2.0.6.1` on this release line, with mandatory parser
hardening, behavioral regression tests, and the existing dependency lock.
Treat upstream release inactivity as a maintenance risk. It does not establish
an unpatched vulnerability or justify rewriting clinical XML handling in a
maintenance release by itself.

A later migration should prefer JDK DOM or StAX where they fit the workflow.
Do not add another XML tree library merely to replace the dormant dependency.

## Evidence

- [Maven Central metadata](https://repo.maven.apache.org/maven2/org/jdom/jdom2/maven-metadata.xml)
  still lists 2.0.6.1 as both latest and release. There is no newer upstream
  stable version to upgrade to as of this assessment.
- [Upstream release notes](https://www.jdom.org/news/) date 2.0.6.1 to December
  2021 and include the fix for CVE-2021-33813. The
  [upstream issue](https://github.com/hunterhacker/jdom/issues/189) explains
  the interaction between SAX features and JDOM's entity expansion setting.
  This is a specific patched issue, not a claim that all future XML risks are absent.
- CARLOS uses the library directly. `rg -l 'org\.jdom2' src/main` finds **30
  production source files** (26 Java files and four JSPs), rather than the
  previously reported 24. This includes the CDS import runtime fingerprint,
  which references the class but does not parse XML. The count is a snapshot,
  not a fixed architectural limit.
- Parsing call sites in Java and JSP use `XmlUtils.createSecureSAXBuilder()`.
  The sole production `new SAXBuilder()` is inside that factory. Other uses
  expose JDOM elements, iterate trees, or serialize XML. Flowsheets, lab
  configuration, prevention, prescribing, reporting, OntarioMD and CDS import
  are among the affected workflows.
- JDOM types cross helper boundaries and JSP imports. JDK DOM has a different
  API and ownership model; Jackson XML is a binding API rather than a direct
  replacement for these mutable trees. Replacing the dependency requires
  workflow compatibility work, not an import or coordinate change.

## Implemented controls

`XmlUtils.createSecureSAXBuilder()` requires secure processing, rejects DOCTYPE
and disables external general entities, external parameter entities, external
DTD loading and entity expansion. JDOM records `setFeature()` settings without
applying them immediately. The factory therefore calls `buildEngine()` and
throws `IllegalStateException` if a protected engine cannot be created; later
engines are configured with the same restrictions. Builders remain local to a
caller and must not be shared between threads or have protections relaxed.

Previously, unsupported configured features already caused JDOM to reject engine
creation when parsing began. The change validates that configuration before the
factory returns; it does not claim to fix an observed XXE bypass. The extra engine
creation checks configuration only and does not parse input.

The Maven Enforcer `ban-obsolete-jdom` execution rejects JDOM 1 under either
historical coordinate and JDOM2 versions older than 2.0.6.1, including transitive
dependencies. The lock remains unchanged because this decision changes no
resolved artifact. Regenerating the lock cannot silently undo this version floor.

`Jdom2SecurityUnitTest` checks DOCTYPE rejection before entity resolution for
local files, HTTP entities, parameter entities, external DTDs and internal entity
expansion. It also checks reuse after successful and rejected parses, namespace,
CDATA, Unicode and escape round trips, and representative bundled clinical
configuration. Resolver probes use synthetic XML and fail if resolution is
attempted. No patient data or live external service is needed.
Additional regression tests cover factory failure when protections cannot be
applied, rejection of unsupported feature changes, and unexpanded XInclude
references.

Run the focused checks with the project's JDK 25 and Maven setup:

```sh
mvn --batch-mode --no-transfer-progress test \
  -Dtest=XmlUtilsUnitTest,Jdom2SecurityUnitTest -Dtest.forkCount=1
mvn --batch-mode --no-transfer-progress dependency:tree \
  -Dincludes=org.jdom:*,jdom:*
```

The normal CI build enforces the dependency rule; the normal Java test job
includes the regression tests. This change has no UI behavior change requiring
a separate Playwright scenario. Build, full Java tests and JSP compilation
remain the release gates.

## Reassessment and replacement criteria

Reassess before the next release line, and when a dependency alert, a JDK/parser
provider change or a JDOM-related defect appears. Existing dependency alerts
must remain enabled; do not suppress a future advisory based on this decision.

Prioritize replacement if an applicable vulnerability has no usable fix or
mitigation, the supported JDK/parser cannot meet these security requirements,
or a workflow change makes removal practical. Upstream release cadence alone
is a reason to plan, not a guarantee of security or a replacement deadline.

For removal, first inventory parse and serialization contracts per workflow.
Move one boundary at a time to secured JDK APIs; compare namespaces, mixed
content, whitespace, CDATA, element order, escaping, missing attributes and
saved XML round trips. Include existing flowsheet and report behavior, lab
mapping, prescription configuration, archive/export serialization, and CDS
import runtime identity in that verification. Remove JDOM2 and regenerate the
lock only after all source/JSP references and dependency paths have been
eliminated and the workflow tests pass.
