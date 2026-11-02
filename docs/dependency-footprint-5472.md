# Dependency replacements for #5472

The WAR no longer carries POI, JFreeChart, Selenium or displaytag. The dependency lock
contains 279 compile/runtime dependencies, down from 303. POI is a test-only independent
reader for the spreadsheet round trips. The JSTL API is declared directly, because Tomcat
does not supply it; the existing WAR packaging check also requires the API and implementation.

## Output and deployment differences requiring review

* Report-by-template and clinical exports now download `oscarReport.xlsx`, with the OOXML
  content type. The existing `getXLS` request parameter remains supported. The small JDK ZIP/XML
  writer keeps string cells (including identifiers, Unicode and formula-looking text) separate
  from numeric cells. Report-by-template retains its conservative numeric conversion; clinical
  export retains its existing numeric conversion. CSV is unchanged.
* Measurement endpoints still return PNG/JPEG at their existing dimensions, so image, print and
  document callers keep working. Chart.js supplies the visual styling. Series values, daily
  replacement semantics, separate blood-pressure series, reference bounds, secondary axes and
  medication intervals are represented explicitly. The obsolete `rxincl` example overlay with
  hardcoded 2007–2009 drugs is replaced by the selected patient's medication intervals.
* Chart rendering now needs the already-running local browser service, like eForm PDF rendering.
  Charts and eForm PDFs share the two-browser limit. Chart requests have bounded waiting queues;
  session startup, late-session cleanup and targeted teardown use the existing renderer controls.
* Former displaytag tables use encoded JSTL rows. Sorting and paging run locally, with typed
  numeric/date keys and case/accent-insensitive text collation, numbered pages and keyboard
  controls. Without JavaScript all rows remain readable; sort controls are added only when the enhancement script runs. Sorting no longer navigates or repeats
  search parameters, CSRF tokens or rejected form values into URLs. Existing row actions remain
  in the JSPs, including billing edit/delete and referral labels.

These changes are proposed for review; #5472 requires sign-off on output differences before
merging. A byte-identical XLS or chart image is not claimed.

## Browser transport

`RenderBrowser` sends the renderer's existing W3C navigation, script, timeout and log commands
with the JDK HTTP client. CDP commands still cross chromedriver's private pipe to Chromium.
The service is loopback-only, redirects are refused, inherited outbound proxies are not used
for that local control channel, and capability prefixes remain part of every request path.
The existing launch flags, network/completeness gates, native print parameters, concurrency
limits and cleanup backstop remain active. Browser error responses are not included in errors.

## Reproduction

Run in Ubuntu 26.04 with Java 25, Maven, the packaging Build-Depends and a running matching
chromedriver/Chromium pair:

```sh
mvn test
mvn -Pjspc -DskipTests package
python3 scripts/check-war-container-apis.py target/carlos-*.war
CARLOS_SMOKE_DRIVER_URL=http://127.0.0.1:9515 \
CARLOS_SMOKE_CHROMIUM=/path/to/chrome \
mvn test -Dtest=EFormBrowserPdfServiceSeleniumSmokeIntegrationTest,MeasurementChartSmokeIntegrationTest
CARLOS_SMOKE_CHROMIUM=/path/to/chrome npm run test:dependency-tables-playwright
```

The PDF/chart browser smoke suites are opt-in and require the local service URL and browser binary above; the legacy Selenium smoke class name remains for operator command compatibility.

The standalone table Playwright check requires only Chromium; it loads a static DOM fixture without network navigation or a driver service.

The PDF tests assert authored pagination and a real text layer. The chart smoke checks real
PNG/JPEG rendering with clinical series, reference bounds and medication intervals. Playwright
checks default sorting, typed keys, paging, keyboard operation and row actions without form
submission. The JSP profile compiles the actual application pages.

The local managed environment blocks `commondatastorage.googleapis.com`, where the release's
pinned Chromium snapshot is downloaded. Local browser/package validation therefore uses a
matching Debian Chromium/driver pair through the supported `CHROMIUM_DIST` build input.
The release pin and download script are unchanged. This fixture is not a release artifact.
