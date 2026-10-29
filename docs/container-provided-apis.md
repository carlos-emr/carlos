# Container-provided Jakarta APIs

Tracking: [#5416](https://github.com/carlos-emr/carlos/issues/5416).

CARLOS runs in Apache Tomcat 11: the Debian packages use Ubuntu's `libtomcat11-java`, and the
devcontainer and container images use the `tomcat:11.0.x` image. Tomcat supplies several Jakarta
APIs itself. The application compiles and tests against them, but must not ship them in the WAR.
This page records which APIs those are, why, and the checks that keep it so.

## Rules

| API | Coordinates | Scope | Supplied at run time by |
|-----|-------------|-------|-------------------------|
| Servlet 6.1 | `jakarta.servlet:jakarta.servlet-api` | `provided` | Tomcat `lib/servlet-api.jar` |
| Pages (JSP) 4.0 | `jakarta.servlet.jsp:jakarta.servlet.jsp-api` | `provided` | Tomcat `lib/jsp-api.jar` |
| Expression Language 6.0 | `jakarta.el:jakarta.el-api` **6.0.1** | `provided` | Tomcat `lib/el-api.jar`, implemented by `lib/jasper-el.jar` |
| Annotations 3.0 | `jakarta.annotation:jakarta.annotation-api` 3.0.0 | `provided` | Tomcat `lib/annotations-api.jar` |
| Tomcat internals | `org.apache.tomcat:*` (for example `tomcat-coyote`) | `provided` | Tomcat `lib/` |
| Standard Tag Library (JSTL) 3.0 | `jakarta.servlet.jsp.jstl:jakarta.servlet.jsp.jstl-api` 3.0.2 and `org.glassfish.web:jakarta.servlet.jsp.jstl` 3.0.1 | `compile` | **the WAR**: Tomcat ships no JSTL |

1. A dependency that brings a container API must leave it `provided`. A direct `provided`
   declaration keeps that scope even when a library asks for the API at `compile` scope; this
   is how `jakarta.servlet-api` and `jakarta.el-api` are declared in `pom.xml`. The EL pin in
   `<dependencyManagement>` also carries `<scope>provided</scope>`, so every transitive path
   resolves to it.
2. Never make JSTL `provided`. Every page that uses `<c:...>` or `<fmt:...>` would fail.
3. Do not add an Expression Language implementation (Expressly, GlassFish `jakarta.el`) to the
   WAR. `ExpressionFactory.newInstance()` looks the factory up through the web application's
   class loader, so a bundled implementation could replace Tomcat's.
4. After a dependency change, regenerate `dependencies-lock.json`
   (`mvn se.vandmo:dependency-lock-maven-plugin:lock`) and run the checks below.

## Why a WAR copy is wrong

Tomcat's web application class loader always asks its parent first for `jakarta.el`,
`jakarta.servlet` (except `jakarta.servlet.jsp.jstl`), `jakarta.annotation`, `jakarta.websocket`,
`jakarta.security.auth.message`, `javax.websocket` and its own `org.apache.catalina`, `coyote`,
`el`, `jasper`, `juli`, `naming` and `tomcat` packages (except `org.apache.tomcat.jdbc`)
(`WebappClassLoaderBase.filter`). A copy in `WEB-INF/lib` is used only for a class the container
lacks. It never changes what Tomcat runs. It does change what runs outside Tomcat: unit tests,
JSP precompilation and any tool that puts `WEB-INF/lib/*` on its class path.

Until #5416 the WAR shipped two such copies:

- `jakarta.el-api-5.0.1.jar`, an Expression Language 5 API beside Tomcat 11's EL 6. It arrived
  at `compile` scope through `displaytag` → `jakarta.servlet.jsp.jstl-api` 3.0.2, which declares
  EL 5.0.0. The Glassfish JSTL implementation declares the same API `provided`.
- `jakarta.annotation-api-3.0.0.jar`, a direct `compile` dependency that duplicated Tomcat's
  `annotations-api.jar`.

Both are now `provided`; nothing else in `WEB-INF/lib` changed. Tomcat 11.0.27 and Ubuntu 26.04's
`libtomcat11-java` 11.0.22 contain every public class of `jakarta.el-api` 6.0.1 and
`jakarta.annotation-api` 3.0.0. Only package-private helper classes (for example
`jakarta.el.ELUtil`) and `package-info` differ, so the class loader never needed the WAR copy as
a fallback.

## Expression Language 5 to 6 review

- **Removed in EL 6:** `ELResolver.getFeatureDescriptors(ELContext, Object)` on every resolver,
  and the `ELResolver.TYPE` and `RESOLVABLE_AT_DESIGN_TIME` constants. No class in the WAR
  declares or calls `getFeatureDescriptors`. The constants are compile-time strings, inlined
  into any caller.
- **Added:** `OptionalELResolver` and `RecordELResolver`; `BeanELResolver` no longer needs the
  `java.desktop` module.
- **Who uses EL in the WAR:** the JSTL API and implementation, `spring-web`, FreeMarker and
  JavaMelody. Every EL constructor, method and field they reference (32 members) resolves
  against the `el-api.jar` of Tomcat 11.0.27 and of Ubuntu 26.04's 11.0.22. CARLOS code has no
  EL resolver of its own. The EL functions
  in `carlos-tag.tld` and `integration-tags.tld` are static `String` methods (`SafeEncode`) and do
  not touch the EL API. displaytag does not use EL classes at all.
- **Run-time behaviour is unchanged:** because of the class-loader filter, JSPs were already
  evaluated by Tomcat's EL 6 before this change.

## Classpaths without Tomcat

- **Unit and integration tests:** `provided` dependencies are on the test class path, so tests
  compile and run against EL 6.0.1, the version the JSP 4.0 API itself is built against. No test
  needs an EL implementation; `CarlosEncodeTagUnitTest` only implements `PageContext`, whose
  `getELContext()` returns an EL type.
- **JSP precompilation (`mvn package -Pjspc`, `make jspc`, the CI `jspc` job):**
  `jspc-maven-plugin` 5.0.0 bundles Tomcat 10.1's Jasper (Pages 3.1, EL 5.0), and its class
  loader puts that Jasper and its EL ahead of the project's dependencies. The `jspc` profile
  therefore pins the plugin's `tomcat-jasper` to `${tomcat.version}`, which brings Tomcat 11's
  Jasper, JSP API, EL API and EL implementation together. All 992 JSPs compiled with it when
  this changed, as they did with the bundled 10.1 Jasper.
- **`scripts/compile-edit-appointment-jsp.sh`:** puts `$CATALINA_HOME/lib` first, so it already
  compiled against the deployment's EL.
- **The packaged Flyway runner (`carlos-ctl db-*`):** runs with `WEB-INF/lib/*` on its class path
  but uses neither EL nor the annotations API.

## Checks

- `ContainerApiPackagingUnitTest` runs with every `mvn test`. It reads `dependencies-lock.json`,
  which the build's lock check holds equal to the resolved dependency graph, and requires the EL
  API to be 6.x and `provided`. It opens every `compile` and `runtime` jar, which is exactly what
  the WAR plugin packages, and fails on any class in a container package, on an
  `ExpressionFactory` service file, and on missing JSTL.
- `scripts/check-war-container-apis.py` applies the same rules to the built WAR (or an exploded
  web application). `debian/rules` runs it on every package build, so a jar copied into the WAR
  by any route is refused. Its cases are in `scripts/check-war-container-apis.test.js`.
- Both read `scripts/fixtures/tomcat-container-packages.json`. Change that file only alongside
  a change to Tomcat's class-loader filter.
- `scripts/el-jstl-pages-playwright-checks.js` opens representative deployed pages (JSTL,
  displaytag, the `carlos:` EL functions) and fails on an unevaluated `${...}`, an error page or
  an EL failure.

To check by hand:

```bash
mvn dependency:tree -Dverbose -Dincludes=jakarta.el,jakarta.annotation,jakarta.servlet.jsp.jstl
mvn package -DskipTests && python3 scripts/check-war-container-apis.py target/carlos-*.war
mvn test -Dtest=ContainerApiPackagingUnitTest
```
