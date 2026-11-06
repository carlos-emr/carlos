# JFreeChart reference fixtures

Recordings of the charts JFreeChart 1.5.6 drew for the measurement graph action and the scatter
servlet before `library/chartjs/carlos-chart-draw.js` replaced it (#5472). The renderer is checked
against them by `scripts/chart-draw-playwright-checks.js` (`npm run test:chart-draw-playwright`).

Each `*.json` file holds:

- `input`: the renderer input in the shape `MeasurementChart.toConfig()` produces for that chart;
- what JFreeChart chose for it: the axis ranges after its margins and zero rules, the tick unit,
  every tick position and label, the series paints and strokes, the renderer details (shapes,
  item labels, bar shadows, high-low ticks), the legend items and the theme fonts and colours;
- `metrics`: Java's line heights and sample string widths for the theme fonts, which JFreeChart's
  tick unit choice depends on.

`JfcReference.java` produced them. It builds every chart exactly as the former
`MeasurementGraphAction22Action` and `ScatterPlotChartServlet` did, with synthetic data, in the
`carlos-tomcat-dev` image (its fonts decide the metrics) with the time zone `America/Toronto`:

```bash
JAR=~/.m2/repository/org/jfree/jfreechart/1.5.6/jfreechart-1.5.6.jar   # no longer a project dependency
docker run --rm -v "$PWD/scripts/fixtures/chart/jfreechart:/jfc" -v "$JAR:/jfc/jfreechart.jar:ro" \
  -e TZ=America/Toronto carlos-tomcat-dev bash -c 'cd /jfc && mkdir -p /tmp/classes \
  && javac -cp jfreechart.jar -d /tmp/classes JfcReference.java \
  && java -Djava.awt.headless=true -Duser.timezone=America/Toronto -cp jfreechart.jar:/tmp/classes JfcReference \
  && cp out/*.json .'
```

The program writes each chart's JSON and a PNG of it under `out/` in its working directory; the
JSON files are copied here, the PNGs are for comparison by eye and are not committed.
