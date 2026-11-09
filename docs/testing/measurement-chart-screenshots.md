# Measurement chart comparison screenshots

Baseline: `9004536dc8314d5f50542b48905ca3ca4099a916`. Candidate code: `4173aa17ef12be8c94eebe8d9013094aec10976a`.

**Scope:** these are Playwright screenshots of actual Java-generated chart configurations rendered using each revision's bundled JavaScript. They are renderer fixtures, not evidence of installed patient-chart, CDM, lab-ingestion, unlink, database, or PDF workflows. All data is synthetic.

Both sides use Chromium **154.0.8037.57**, a 1000 × 600 viewport, device scale 1, and JDK 25.0.3. The English and French edge cases are generated in separate `en_CA` and `fr_CA` JVMs. The baseline and candidate use their own compiled `MeasurementChart` and `MeasurementGraphAction22Action.actualLabChart` implementations.

The eight baseline renderer exceptions are displayed explicitly in the screenshots and recorded as failures. They are not substituted successful application responses. Every candidate edge case renders without an exception.

[Configuration, script and screenshot hashes](../images/pr6102/comparison-manifest.json) · [Java fixture exporter](../images/pr6102/capture/ComparisonModels.java) · [Playwright capture script](../images/pr6102/capture/capture-comparisons.cjs)

The capture utility accepts `CARLOS_ROOT`, `CARLOS_BASELINE_ROOT` and `CHROME_BIN`
to reproduce the saved configurations elsewhere. The configurations identify
their original Java revision; each run hashes the actual draw scripts and images.

## Defect-focused comparisons

| Case | Before | After |
| --- | --- | --- |
| en-constant-0.0 | ![Before en-constant-0.0](../images/pr6102/before/edge-en-constant-0_0.png) | ![After en-constant-0.0](../images/pr6102/after/edge-en-constant-0_0.png) |
| en-constant-0.05 | ![Before en-constant-0.05](../images/pr6102/before/edge-en-constant-0_05.png) | ![After en-constant-0.05](../images/pr6102/after/edge-en-constant-0_05.png) |
| en-constant-1.25E7 | ![Before en-constant-1.25E7](../images/pr6102/before/edge-en-constant-1_25E7.png) | ![After en-constant-1.25E7](../images/pr6102/after/edge-en-constant-1_25E7.png) |
| en-constant-4.5E7 | ![Before en-constant-4.5E7](../images/pr6102/before/edge-en-constant-4_5E7.png) | ![After en-constant-4.5E7](../images/pr6102/after/edge-en-constant-4_5E7.png) |
| en-constant-5000000.0 | ![Before en-constant-5000000.0](../images/pr6102/before/edge-en-constant-5000000_0.png) | ![After en-constant-5000000.0](../images/pr6102/after/edge-en-constant-5000000_0.png) |
| en-constant-99.0 | ![Before en-constant-99.0](../images/pr6102/before/edge-en-constant-99_0.png) | ![After en-constant-99.0](../images/pr6102/after/edge-en-constant-99_0.png) |
| en-constant-n0.05 | ![Before en-constant-n0.05](../images/pr6102/before/edge-en-constant-n0_05.png) | ![After en-constant-n0.05](../images/pr6102/after/edge-en-constant-n0_05.png) |
| en-constant-n6000000.0 | ![Before en-constant-n6000000.0](../images/pr6102/before/edge-en-constant-n6000000_0.png) | ![After en-constant-n6000000.0](../images/pr6102/after/edge-en-constant-n6000000_0.png) |
| en-qualified-results | ![Before en-qualified-results](../images/pr6102/before/edge-en-qualified-results.png) | ![After en-qualified-results](../images/pr6102/after/edge-en-qualified-results.png) |
| en-rounding-0.00 | ![Before en-rounding-0.00](../images/pr6102/before/edge-en-rounding-0_00.png) | ![After en-rounding-0.00](../images/pr6102/after/edge-en-rounding-0_00.png) |
| en-rounding-0.xxx | ![Before en-rounding-0.xxx](../images/pr6102/before/edge-en-rounding-0_xxx.png) | ![After en-rounding-0.xxx](../images/pr6102/after/edge-en-rounding-0_xxx.png) |
| fr-constant-0.0 | ![Before fr-constant-0.0](../images/pr6102/before/edge-fr-constant-0_0.png) | ![After fr-constant-0.0](../images/pr6102/after/edge-fr-constant-0_0.png) |
| fr-constant-0.05 | ![Before fr-constant-0.05](../images/pr6102/before/edge-fr-constant-0_05.png) | ![After fr-constant-0.05](../images/pr6102/after/edge-fr-constant-0_05.png) |
| fr-constant-1.25E7 | ![Before fr-constant-1.25E7](../images/pr6102/before/edge-fr-constant-1_25E7.png) | ![After fr-constant-1.25E7](../images/pr6102/after/edge-fr-constant-1_25E7.png) |
| fr-constant-4.5E7 | ![Before fr-constant-4.5E7](../images/pr6102/before/edge-fr-constant-4_5E7.png) | ![After fr-constant-4.5E7](../images/pr6102/after/edge-fr-constant-4_5E7.png) |
| fr-constant-5000000.0 | ![Before fr-constant-5000000.0](../images/pr6102/before/edge-fr-constant-5000000_0.png) | ![After fr-constant-5000000.0](../images/pr6102/after/edge-fr-constant-5000000_0.png) |
| fr-constant-99.0 | ![Before fr-constant-99.0](../images/pr6102/before/edge-fr-constant-99_0.png) | ![After fr-constant-99.0](../images/pr6102/after/edge-fr-constant-99_0.png) |
| fr-constant-n0.05 | ![Before fr-constant-n0.05](../images/pr6102/before/edge-fr-constant-n0_05.png) | ![After fr-constant-n0.05](../images/pr6102/after/edge-fr-constant-n0_05.png) |
| fr-constant-n6000000.0 | ![Before fr-constant-n6000000.0](../images/pr6102/before/edge-fr-constant-n6000000_0.png) | ![After fr-constant-n6000000.0](../images/pr6102/after/edge-fr-constant-n6000000_0.png) |
| fr-qualified-results | ![Before fr-qualified-results](../images/pr6102/before/edge-fr-qualified-results.png) | ![After fr-qualified-results](../images/pr6102/after/edge-fr-qualified-results.png) |
| fr-rounding-0.00 | ![Before fr-rounding-0.00](../images/pr6102/before/edge-fr-rounding-0_00.png) | ![After fr-rounding-0.00](../images/pr6102/after/edge-fr-rounding-0_00.png) |
| fr-rounding-0.xxx | ![Before fr-rounding-0.xxx](../images/pr6102/before/edge-fr-rounding-0_xxx.png) | ![After fr-rounding-0.xxx](../images/pr6102/after/edge-fr-rounding-0_xxx.png) |

## Compatibility controls

These screenshots accompany the existing chart reference suite. The single-point fixture intentionally adopts the repaired behavior; unaffected reference recordings remain unchanged.

| Case | Before | After |
| --- | --- | --- |
| actualLab-plusmeds-8pt | ![Before actualLab-plusmeds-8pt](../images/pr6102/before/actualLab-plusmeds-8pt.png) | ![After actualLab-plusmeds-8pt](../images/pr6102/after/actualLab-plusmeds-8pt.png) |
| actualLab-plusmeds | ![Before actualLab-plusmeds](../images/pr6102/before/actualLab-plusmeds.png) | ![After actualLab-plusmeds](../images/pr6102/after/actualLab-plusmeds.png) |
| actualLab-thousands | ![Before actualLab-thousands](../images/pr6102/before/actualLab-thousands.png) | ![After actualLab-thousands](../images/pr6102/after/actualLab-thousands.png) |
| actualLab | ![Before actualLab](../images/pr6102/before/actualLab.png) | ![After actualLab](../images/pr6102/after/actualLab.png) |
| bp-lines | ![Before bp-lines](../images/pr6102/before/bp-lines.png) | ![After bp-lines](../images/pr6102/after/bp-lines.png) |
| chartmeds-13 | ![Before chartmeds-13](../images/pr6102/before/chartmeds-13.png) | ![After chartmeds-13](../images/pr6102/after/chartmeds-13.png) |
| chartmeds-2 | ![Before chartmeds-2](../images/pr6102/before/chartmeds-2.png) | ![After chartmeds-2](../images/pr6102/after/chartmeds-2.png) |
| chartmeds-empty | ![Before chartmeds-empty](../images/pr6102/before/chartmeds-empty.png) | ![After chartmeds-empty](../images/pr6102/after/chartmeds-empty.png) |
| chartmeds-repeat | ![Before chartmeds-repeat](../images/pr6102/before/chartmeds-repeat.png) | ![After chartmeds-repeat](../images/pr6102/after/chartmeds-repeat.png) |
| default-12pt | ![Before default-12pt](../images/pr6102/before/default-12pt.png) | ![After default-12pt](../images/pr6102/after/default-12pt.png) |
| default-1pt | ![Before default-1pt](../images/pr6102/before/default-1pt.png) | ![After default-1pt](../images/pr6102/after/default-1pt.png) |
| default-2pt | ![Before default-2pt](../images/pr6102/before/default-2pt.png) | ![After default-2pt](../images/pr6102/after/default-2pt.png) |
| default-scientific | ![Before default-scientific](../images/pr6102/before/default-scientific.png) | ![After default-scientific](../images/pr6102/after/default-scientific.png) |
| default-thousands | ![Before default-thousands](../images/pr6102/before/default-thousands.png) | ![After default-thousands](../images/pr6102/after/default-thousands.png) |
| default-two-types | ![Before default-two-types](../images/pr6102/before/default-two-types.png) | ![After default-two-types](../images/pr6102/after/default-two-types.png) |
| inclRef-2pt | ![Before inclRef-2pt](../images/pr6102/before/inclRef-2pt.png) | ![After inclRef-2pt](../images/pr6102/after/inclRef-2pt.png) |
| inclRef-none | ![Before inclRef-none](../images/pr6102/before/inclRef-none.png) | ![After inclRef-none](../images/pr6102/after/inclRef-none.png) |
| lab-bp | ![Before lab-bp](../images/pr6102/before/lab-bp.png) | ![After lab-bp](../images/pr6102/after/lab-bp.png) |
| labRef | ![Before labRef](../images/pr6102/before/labRef.png) | ![After labRef](../images/pr6102/after/labRef.png) |
| native-bar | ![Before native-bar](../images/pr6102/before/native-bar.png) | ![After native-bar](../images/pr6102/after/native-bar.png) |
| native-line | ![Before native-line](../images/pr6102/before/native-line.png) | ![After native-line](../images/pr6102/after/native-line.png) |
| scatter-12pt | ![Before scatter-12pt](../images/pr6102/before/scatter-12pt.png) | ![After scatter-12pt](../images/pr6102/after/scatter-12pt.png) |
| scatter-2pt | ![Before scatter-2pt](../images/pr6102/before/scatter-2pt.png) | ![After scatter-2pt](../images/pr6102/after/scatter-2pt.png) |

## Remaining evidence

Native installed ON/BC workflow screenshots, CDM two-tab behavior, renderer outage/recovery in the application and actual print/PDF comparisons remain required by the acceptance register.

Visual inspection of the first candidate screenshots identified overlapping French date labels. The overlap was fixed and the final screenshots were recaptured; the earlier failed visual attempt remains in commit `ebab9861b41`.
