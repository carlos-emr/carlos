# Dependency replacements for #5472

The WAR no longer carries POI, JFreeChart, Selenium or displaytag. The WAR packaging check
(`scripts/check-war-container-apis.py`) verifies container API and JSTL boundaries. POI is a test-only independent
reader for the spreadsheet round trips. The JSTL API is declared directly, because Tomcat
does not supply it; the existing WAR packaging check also requires the API and implementation.

## Output and deployment differences requiring review

* Report-by-template and clinical exports now download `oscarReport.xlsx`, with the OOXML
  content type. The existing `getXLS` request parameter remains supported. The small JDK ZIP/XML
  writer keeps string cells (including identifiers, Unicode and formula-looking text) separate
  from numeric cells. Report-by-template retains its conservative numeric conversion; clinical
  export retains its existing numeric conversion. CSV is unchanged.
* The release branch's direct Dx Research XLSX export also uses the JDK writer. It retains
  its sheet name, eleven string columns, frozen header and 22-character column widths.
  Rows are serialized one at a time, without adding a runtime POI dependency.
* Measurement endpoints still return PNG/JPEG at their existing dimensions, so image, print and
  document callers keep working. The draw script reproduces the JFreeChart charts: the "JFree"
  theme and the plain `new JFreeChart` look of the combined medication charts, the series paint
  sequence, JFreeChart's auto ranges (margins, the include-zero and sticky-zero rules, the symbol
  axis), its 1-2-5 number units and standard date units with their formats (`d-MMM`, `MMM-yyyy`,
  `yyyy`, `HH:mm`), chosen by its two-pass label-size estimate in Java's integer font metrics and
  anchored as `DateAxis.previousStandardDate` anchors them, 1px lines, 6x6 squares, item labels,
  high-low range bars, flat bars with their 4px gray shadow, and the bottom legend with each
  renderer's graphic. `MeasurementChart` describes each chart's look as the former action
  configured it (margins, zero, plot background, gridlines, shapes, item-label rules, green or
  sequence-coloured reference ranges). `scripts/fixtures/chart/jfreechart` holds recordings made
  with JFreeChart 1.5.6 from the former code paths, and `scripts/chart-draw-playwright-checks.js`
  requires the same ranges, ticks, labels, colours, fonts, legend and bar geometry. Pixel checks
  also require visible dashed gridlines on both axes where the recorded grid colour differs
  from the background. The adapter explicitly enables chart-area grids and uses Chart.js 4's
  `border.dash` setting; medication grids remain gray and align with the row centres. Kept as they
  were, because this change replaces the library and not the charts: the reference range chart's
  "HighLowChartDemo2" title and "DREFERENCE RANGE" legend entry, the `rxincl` chart's
  "MED + LAB CHART" title, and the green value axis of `labRef` when ranges exist. Two
  departures: `labRef` drew that green axis twice (one axis object at two indices) and it is
  drawn once here, and a date axis with a single instant gets half a day of margin instead of
  JFreeChart's two milliseconds. Series values, daily replacement semantics, separate
  blood-pressure series, reference bounds, secondary axes and medication intervals are
  represented explicitly. The obsolete `rxincl` example overlay with hardcoded 2007–2009 drugs
  is replaced by the selected patient's medication intervals.
* Chart rendering now needs the already-running local browser service, like eForm PDF rendering.
  Charts and eForm PDFs share the two-browser limit; a chart request queues for a slot for the
  renderer's slot wait (up to sixteen waiting at once) before reporting the renderer busy, and the
  bundled Chart.js and draw script (`library/chartjs/carlos-chart-draw.js`) are read from the WAR
  once. The measurement graph action answers HTTP 503 with no image when the renderer is
  unavailable, like the scatter servlet. A medication-only chart (ChartMeds) is the single
  medication panel of the former combined plot, with long product names shortened on its axis
  as before and a prescription that starts and ends the same day drawn as a one pixel line with
  its shadow, as JFreeChart drew it. The devcontainer installs and starts the matching
  chromedriver (`server start`), so graphs and eForm PDFs render there without setup.
* Former displaytag tables use encoded JSTL rows (`${carlos:forHtml(...)}`), with the shared
  assets included once per rendered page (`CarlosTableAssetsJspRegressionTest`). Pages whose
  backing attribute was a lone placeholder bean under displaytag (the Disease Registry results)
  now seed an empty list, since `<c:forEach>` iterates collections only. An empty list renders
  only its message, not a header row (`JstlTableEmptyStateJspRegressionTest`), as displaytag did. Sorting and paging run locally, with typed
  numeric/date keys and case/accent-insensitive text collation, numbered pages and keyboard
  controls. Without JavaScript all rows remain readable; sort controls are added only when the enhancement script runs. Sorting no longer navigates or repeats
  search parameters, CSRF tokens or rejected form values into URLs. Existing row actions remain
  in the JSPs, including billing edit/delete and referral labels.
  Disease registry results retain their existing DataTables search and sorting, with
  DataTables as their sole enhancer and all matching rows on one page.

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
CARLOS_SMOKE_CHROMIUM=/path/to/chrome npm run test:chart-draw-playwright
```

The PDF/chart browser smoke suites are opt-in and require the local service URL and browser binary above; the legacy Selenium smoke class name remains for operator command compatibility.

The standalone table and chart-draw Playwright checks require only Chromium; they load static DOM fixtures and the bundled scripts without network navigation or a driver service. The deployed checks `report-by-template`, `export-content-rbt-columns` and `el-jstl-pages` read the `.xlsx` export and the JSTL tables.

The PDF tests assert authored pagination and a real text layer. The chart smoke checks real
PNG/JPEG rendering with clinical series, reference bounds and medication intervals. Playwright
checks default sorting, typed keys, paging, keyboard operation and row actions without form
submission. The JSP profile compiles the actual application pages.

Regression coverage also checks overlapping literal OOXML escape sequences, duplicate scatter
observations, medication names with identical shortened labels, empty medication charts, and
large constant measurement values. Reference ranges load both bounds by measurement ID, and
lab-plus-medication charts accept the LOINC provider's collection-date field. Invalid range data
or a failed chart draw produces an explicit error before image bytes are sent. Empty migrated
tables retain their localized messages, including staff tables with nested team markup.

Corrected grid screenshots rendered directly in Chromium from the synthetic chart fixtures:

* [Measurement graph](images/pr5518/grid-fixed-default.png)
* [Scatter plot](images/pr5518/grid-fixed-scatter.png)
* [Medication intervals](images/pr5518/grid-fixed-chartmeds.png)

These captures supersede the chart grid appearance in the earlier PR screenshot gallery.
The medication capture uses the two-medication fixture (including a one-day interval) with a
synthetic patient title; it is a chart fixture, not a capture of a deployed patient page.

The local managed environment blocks `commondatastorage.googleapis.com`, where the release's
pinned Chromium snapshot is downloaded. Local browser/package validation therefore uses a
matching Debian Chromium/driver pair through the supported `CHROMIUM_DIST` build input.
The release pin and download script are unchanged. This fixture is not a release artifact.
