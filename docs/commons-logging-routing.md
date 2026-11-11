# Commons Logging routing

Tracking: [#5413](https://github.com/carlos-emr/carlos/issues/5413).

CARLOS logs through Log4j 2 (`log4j-core`, configured by `log4j2.xml` and the
`log4j.override.configuration` file). Libraries that log through another API reach it by one
fixed route each:

| API | Used by | Route to Log4j 2 |
|-----|---------|------------------|
| Log4j 2 API | CARLOS code (`MiscUtils.getLogger()`) | direct |
| Commons Logging (JCL) | Spring 7, PDFBox, JasperReports, the Commons libraries, the HttpClient 4 under CXF's HTTP transport, JSPs that call `LogFactory.getLog` | `commons-logging` 1.4 → `Log4jApiLogFactory` → Log4j 2 |
| SLF4J 2 | CSRFGuard, HAPI FHIR and others | `log4j-slf4j2-impl` → Log4j 2 |

## One Commons Logging implementation

`commons-logging:commons-logging` is the only artifact allowed to define the
`org.apache.commons.logging` classes. Spring 7 depends on it directly (the old `spring-jcl` is
gone), and since 1.3 its `LogFactory` finds the Log4j API and binds to `Log4jApiLogFactory`, so
no adapter jar is needed.

`org.slf4j:jcl-over-slf4j` used to arrive as well, through `apache-cxf` (and its
`cxf-rt-transports-http-hc`). It redefines `Log`, `LogFactory`, `LogConfigurationException`,
`impl.NoOpLog` and `impl.SimpleLog`, and registers a
`META-INF/services/org.apache.commons.logging.LogFactory` entry that reroutes JCL through SLF4J.
With both jars in `WEB-INF/lib`, which `LogFactory` class loaded, and so which route JCL took,
depended on class-loader order.

It is now excluded from `apache-cxf` and `hapi-fhir-base`, the two dependencies that declare it.
The exclusions on `poi`, `displaytag` and `javamelody-core` predate this and are kept as defence
in depth; their current releases do not bring it.

## Guards

| Guard | Where it runs | What it catches |
|-------|---------------|-----------------|
| `maven-enforcer-plugin`, execution `ban-duplicate-commons-logging` | every Maven build, `validate` phase, all scopes | `jcl-over-slf4j`, `spring-jcl`, `commons-logging-api` and `commons-logging-jboss-logging` (which redefine the classes), and `log4j-jcl` and `log4j-to-slf4j` (which change the route) |
| `CommonsLoggingRoutingUnitTest` | unit tests | a second definition of one of the five classes on the test class path, a service file or `commons-logging.properties` override, discovery not binding `Log4jApiLogFactory`, a JCL call not arriving in Log4j 2 exactly once at the configured level |
| `debian/check-war-commons-logging.py` | `debian/rules`, on the WAR being packaged (built or prebuilt `CARLOS_WAR`) | a second jar, or `WEB-INF/classes`, defining `org.apache.commons.logging` classes (including shaded and multi-release copies), a commons-logging older than 1.3 (no `Log4jApiLogFactory`), and discovery overrides |

The dependency lock alone does not hold this: regenerating it after a library bump admits
whatever the new tree resolves, which is how the bridge returned through CXF.

## When the enforcer fails

The message names the path that brought the banned artifact in. Add an `<exclusion>` for it to
that direct dependency in `pom.xml`, then regenerate the lock (`make lock`, or
`mvn se.vandmo:dependency-lock-maven-plugin:lock`). Do not add `log4j-to-slf4j`: Log4j 2 is the
backend, and that jar would point the Log4j API at SLF4J, which `log4j-slf4j2-impl` points back.

To see every JCL artifact and its path:

```bash
mvn dependency:tree -Dverbose \
  -Dincludes=commons-logging:commons-logging,org.slf4j:jcl-over-slf4j
```

To check a built WAR:

```bash
python3 debian/check-war-commons-logging.py target/carlos-*.war
```
