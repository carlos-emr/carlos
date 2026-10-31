# JSTL's embedded Xalan: CVE-2022-34169 scanner triage

Assessed on October 31, 2026 against `release/2026.08` at `b4c0dfc94e67`.

## Finding

`org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1` embeds Xalan Java 2.7.2,
including its XSLTC compiler, under `org.eclipse.tags.shaded.org.apache.*`.
The [published POM](https://repo.maven.apache.org/maven2/org/glassfish/web/jakarta.servlet.jsp.jstl/3.0.1/jakarta.servlet.jsp.jstl-3.0.1.pom)
relocates Xalan packages using the Maven Shade plugin. The original POM inside
the JAR records `xalan:xalan:2.7.2`, and the embedded version class reports
`Xalan Java 2.7.2`.

The application's `dependencyManagement` pin for `xalan:xalan:2.7.3` controls
only separately resolved Maven artifacts. It cannot replace classes already
shaded into another artifact. The current dependency lock contains JSTL 3.0.1
and the standalone `xalan:serializer:2.7.3`, with no standalone `xalan:xalan`
entry. The serializer supplies classes used by Struts; it does not update
JSTL's embedded compiler. A clean Maven dependency tree alone therefore does
not establish that the embedded copy is fixed.

The [Maven Central metadata](https://repo.maven.apache.org/maven2/org/glassfish/web/jakarta.servlet.jsp.jstl/maven-metadata.xml)
still lists 3.0.1 as the latest release on the assessment date. An ordinary
version upgrade is not currently available at this coordinate.

## Exposure and disposition

[CVE-2022-34169](https://github.com/advisories/GHSA-9339-86wc-4qgf) concerns
integer truncation while compiling malicious XSLT stylesheets with XSLTC,
potentially allowing arbitrary Java bytecode execution. Xalan 2.7.3 fixes it;
the shaded 2.7.2 copy remains present and affected.

Observed exposure through CARLOS's checked-in JSPs is low: a scan of `src/`
found no JSTL XML tag-library URIs (`jakarta.tags.xml`,
`http://java.sun.com/jsp/jstl/xml`, or `http://java.sun.com/jstl/xml`), no direct use of the JSTL XML tag
handler packages, and no references to `org.eclipse.tags.shaded`. The JAR also
has no `META-INF/services/` entries registering its relocated transformer
factories as default JAXP providers. These observations support a low-risk
triage assessment for the current source; they do not prove that every
deployment or extension makes the compiler unreachable.

Retain this as a known embedded vulnerable component with low observed
application exposure, not as remediated or a version-detection false positive.
This documentation change neither removes the component nor suppresses scanner
findings. Reassess when JSTL changes, an XML tag library or explicit shaded
factory is introduced, or deployment-specific code accepts XSLT. A complete
remediation requires an upstream build with patched Xalan or a separately
reviewed replacement/rebuild; another `dependencyManagement` pin or exclusion
cannot remove the embedded classes.

## Reproducing the evidence

With a JDK installed, download the published artifact and inspect it:

```sh
curl -fSLO https://repo.maven.apache.org/maven2/org/glassfish/web/jakarta.servlet.jsp.jstl/3.0.1/jakarta.servlet.jsp.jstl-3.0.1.jar
java -cp jakarta.servlet.jsp.jstl-3.0.1.jar org.eclipse.tags.shaded.org.apache.xalan.Version
jar tf jakarta.servlet.jsp.jstl-3.0.1.jar | rg 'shaded/org/apache/xalan/(Version|xsltc/compiler/XSLTC)\.class'
unzip -p jakarta.servlet.jsp.jstl-3.0.1.jar META-INF/maven/org.glassfish.web/jakarta.servlet.jsp.jstl/pom.xml
rg -n 'jakarta\.tags\.xml|java\.sun\.com/(jsp/)?jstl/xml|org\.apache\.taglibs\.standard\.tag\.(common|rt|el)\.xml|org\.eclipse\.tags\.shaded' src
mvn --batch-mode --no-transfer-progress dependency:tree -Dincludes=xalan:*,org.glassfish.web:jakarta.servlet.jsp.jstl
```

The version command should print `Xalan Java 2.7.2`. The source search should
return no matches (exit status 1); search URIs rather than assuming the tag
prefix is `x`. Inspect the packaged `WEB-INF/lib/jakarta.servlet.jsp.jstl-3.0.1.jar`
as well when triaging a deployed WAR. The Maven Central JAR inspected here has
SHA-256 `5cc6e60b9e74d38c25fe4f2d22dfd40577f5b8396bc885f7061cd2c525a43b86`.
