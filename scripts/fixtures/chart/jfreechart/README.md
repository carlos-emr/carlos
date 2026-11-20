# JFreeChart reference fixtures

Recordings of the charts JFreeChart 1.5.6 drew for the measurement graph action and the scatter
servlet before `library/chartjs/carlos-chart-draw.js` replaced it (#5472). The renderer is checked
against them by `scripts/chart-draw-playwright-checks.js` (`npm run test:chart-draw-playwright`).

Each `*.json` file holds:

- `input`: the renderer input in the shape `MeasurementChart.toConfig()` produces for that chart;
- what JFreeChart chose for it: the axis ranges after its margins and zero rules, the tick unit,
  every tick position and label, the series paints and strokes, the renderer details (shape type and bounds,
  item labels, bar shadows, high-low ticks), the legend items and the theme fonts and colours;
- `metrics`: Java's line heights and sample string widths for the theme fonts, which JFreeChart's
  tick unit choice depends on.

`JfcReference.java` produced them. It builds every chart exactly as the former
`MeasurementGraphAction22Action` and `ScatterPlotChartServlet` did, with synthetic data, in an
Ubuntu 26.04 container with OpenJDK 25 and only `fonts-dejavu-core`, the fonts the packaged
renderer has (they decide the metrics), with the time zone `America/Toronto`. Recordings made in
the `carlos-tomcat-dev` image are byte-identical:

```bash
JAR=~/.m2/repository/org/jfree/jfreechart/1.5.6/jfreechart-1.5.6.jar   # no longer a project dependency
docker run --rm -v "$PWD/scripts/fixtures/chart/jfreechart:/jfc" -v "$JAR:/jfc/jfreechart.jar:ro" \
  -e TZ=America/Toronto ubuntu:26.04 bash -c 'apt-get update -qq && apt-get install -y -qq --no-install-recommends \
  openjdk-25-jdk-headless libharfbuzz0b fontconfig >/dev/null && cd /jfc && mkdir -p /tmp/classes \
  && javac -cp jfreechart.jar -d /tmp/classes JfcReference.java \
  && java -Djava.awt.headless=true -Duser.timezone=America/Toronto -cp jfreechart.jar:/tmp/classes JfcReference \
  && cp out/*.json .'
```

`carlos-chart-draw.js` also carries Java's integer glyph advances for DejaVu Sans, plain at 8, 10
and 12px and bold at 12, 14, 18 and 20px (`JAVA_ADVANCES`), recorded the same way by
`JavaAdvances.java` with `FontMetrics.charWidth` under JFreeChart's rendering hints for U+0020 to
U+00FF. The draw script sizes labels with them and draws Latin-1 text a glyph at a time at those
advances, as Java2D placed it; regenerate them alongside the fixtures if the bundled font changes.

The program writes each chart's JSON and a PNG of it under `out/` in its working directory; the
JSON files are copied here, the PNGs are for comparison by eye and are not committed.
