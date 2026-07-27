# eForm corpus soak — method

How to measure CARLOS's compatibility with real, third-party eForms, and how to avoid the traps that
produced wrong answers the first several times.

The harness is `scripts/eform-corpus-soak-playwright-checks.js`
(`npm run test:eform-corpus-soak`). It is a local developer tool, deliberately not part of CI: it
needs form packages that are not in the repository, and third-party form quality is outside CARLOS's
control.

## Why this exists

The repo's other eForm Playwright checks use synthetic fixtures authored to suit the renderer. Real
clinic forms — the ~1,350 published on oscargalaxy.org — are two decades of hand-authored HTML and
exercise paths fixtures never reach. Measured against them, CARLOS started at **1 of 10 forms
rendering**. Every subsequent fix came from this loop, not from reading code.

## The loop

1. **Source packages.** The Galaxy library is a WordPress Download Manager site. Enumerate by date
   through its REST API rather than scraping:

   ```
   https://oscargalaxy.org/wp-json/wp/v2/wpdmpro?per_page=100&page=N&orderby=date&order=desc&_fields=id,slug,date
   ```

   Download each as `https://oscargalaxy.org/download/<slug>/?wpdmdl=<id>`.

   **Expect ~20% of "eForms" not to be eForm packages.** In one batch of 100, 21 were bare HTML, SQL
   snippets or plain text. Validate that each file is a ZIP *containing an `.html`/`.htm` entry*
   before counting it, or your denominator is wrong.

2. **Seed a provider signature first.** A large share of the corpus carries the legacy stamp script,
   which builds its URL at load time:

   ```js
   document.getElementById('StampSignature').src =
           "../eform/displayImage.do?imagefile=consult_sig_" + ProviderNumber + ".png";
   ```

   With no signature on disk the request fails and the render is refused — correctly, but for a
   reason that says nothing about CARLOS. Measured on one 49-package batch: **31/49 without a
   signature, 41/49 with one.** Drop any PNG at `<eform image dir>/consult_sig_<providerNo>.png`
   (`999998` for the devcontainer's `carlosdoc`).

3. **Import through the real route.** The script POSTs each ZIP to
   `/eform/manageEForm?method=importEForm` (`EFormExportZip.importForm`), the same path an
   administrator uses. **Do not "simplify" this** to uploading the HTML and images separately: that
   bypasses the importer, and filenames are normalised differently on the two paths. An early run
   did exactly that and produced failures that looked like renderer defects but were harness
   artifacts.

4. **Render, then triage by histogram — not form by form.** The gate names its blocking components,
   so one command turns N opaque failures into a ranked list:

   ```bash
   grep "blocked incomplete output" catalina.out | grep -oE '\[[^]]*\]' \
     | sed 's/=[0-9]*/=N/g' | sort | uniq -c | sort -rn
   ```

   Fix the largest cluster, re-soak, repeat. Every fix in this work came from that histogram.

5. **Open the PDFs.** A clean completeness gate does not mean the page is correct. A blank background
   and a letter printed as raw markup both passed every automated check in this project before
   anyone looked. The soak rasterizes a random sample (default 6, `EFORM_CORPUS_VISUAL_SAMPLE`) and
   always reports which files it picked, even where no rasterizer is installed.

## Reading the results

Three outcomes look like failure and only one is a defect.

| Result | Meaning |
|---|---|
| `PDF OK` | Rendered. Still open it — see step 5. |
| `NO PDF …timeout` **with** an fdid | The gate refused. Real signal; triage by histogram. |
| `NO PDF …timeout` **without** an fdid | The form never *saved*. Usually a `required` field the harness does not fill — not a defect. |

That last row matters: in one 78-package batch, 10 of the 13 non-renders were forms requiring
clinician input. Verified by filling every `[required]` control and re-running — all rendered. Report
those separately or you will overstate the defect count by a factor of three.

## Traps this method exists to avoid

Each of these produced a confidently wrong answer at least once.

- **Measure the render surface, not a `curl` probe.** An unauthenticated `curl` to a missing asset
  returns `302 → logoutPage`, which led to a reported "gate blind spot". The render browser carries
  the `CARLOS_EFORM_RENDER` capability cookie and gets `404`/`403` instead. Across 394 render windows
  the render surface produced **zero 3xx**. Always confirm which surface a status came from before
  drawing a conclusion.
- **Distinguish `304` from `302`.** 66 3xx in one day were `304 Not Modified` on the *viewer*, where
  the browser context persists and caches. Those are successful loads. A rule that treats all 3xx
  alike would refuse essentially every document once any cache exists.
- **A bare `src` is usually not a defect.** Corpus forms deliberately reference each asset twice —
  once bare so the form opens off a local disk, once through `${oscar_image_path}` so it resolves
  when served. The bare reference is *expected* to 404 over HTTP, and the marker copies often sit in
  comments purely so the ZIP exporter's scan finds the filenames. Rewriting them would be rewriting
  a placeholder the form intentionally replaces.
- **Re-run before believing a failure.** Several "incompatibilities" were races. Two forms from the
  same vendor landed on opposite sides of the gate in one run and swapped in the next. If a verdict
  is not reproducible over 3–5 runs, it is a race, and the race is the bug.
- **Watch for silent success failures.** Page stabilization settled after a 500 ms quiet window while
  49 of 50 corpus timers fire at ≥1000 ms, so a field-populating timer often never ran — and the PDF
  shipped with that field **blank and every check green**. Compare rendered output against the form,
  not just the pass count.
- **Check the frequency before fixing.** `/js/jquery-1.7.1.min.js` is referenced by 160 of 199
  packages and is not shipped, which looked urgent. The render surface never loads it: 1 request in
  400 renders, and all 160 packages also load an aliased jQuery. Frequency in the *corpus* is not
  frequency in the *render*.
- **`unzip` is not installed in this devcontainer.** A scan built on `unzip -p … 2>/dev/null`
  reports **zero hits for everything** and reads as a clean negative result. It produced a wrong
  conclusion during this work — a finding was nearly retracted on the strength of it. Use Python's
  `zipfile`, and **assert a known-positive control in the same pass**: `oscarDB=` matches 198 of 199
  packages, so any scan reporting 0 for it is broken, not informative. The same rule generalises —
  every corpus scan should carry one expression whose answer you already know.

## AP coverage audit

What the 199 packages actually ask the `oscarDB=` / `APCache` channel for, measured against
`src/main/resources/oscar/eform/apconfig.xml`. Re-run it before assuming a form's blank field is a
missing AP — most apparent gaps are not.

**Two false-positive classes will dominate a naive scan:**

- **Dynamic module references.** `m$WT#value`, `e$formname#field` and friends are not apconfig
  entries at all; `EForm.getAPExtra` (`EForm.java:788`) resolves a leading `<letter>$` as a module —
  `m$` reads the patient's *last* measurement of that type via
  `EctMeasurementsDataBeanHandler.getLast`, `e$` reads stored eform values. 46 distinct such
  references appear in the corpus. Truncate a key at `#` and every one of them looks undefined.
- **Case.** `EFormLoader.getAP` compares with `equalsIgnoreCase` (`EFormLoader.java:154`), so `DOB`,
  `HIN`, `patient_namef` and 11 other keys that differ from apconfig only in case resolve correctly.

**Findings, after excluding both:**

| Finding | Count | Notes |
|---|---|---|
| Keys genuinely undefined | **3** | `formatted_clinic_address`, `him_her`, `PatientNameL` — one package each |
| Duplicated `<ap-name>` entries | **13** | 378 entries, 365 distinct names |
| Dynamic `m$`/`e$` references | 46 | Already work; no apconfig entry needed |

The duplicates matter because `getAP` returns the **first** match, so every later definition of a
repeated name is dead — `current_user*` accounts for most of them. Worth a cleanup pass, but nothing
is currently mis-resolved by it.

**No new APs were added for the three undefined keys, deliberately.** Two of them are one form's
naming variance on an AP that already exists — `PatientNameL` against the defined `patient_nameL`,
`formatted_clinic_address` against `clinic_address` — and the underscore difference puts them out of
reach of the case-insensitive lookup. Only `him_her` is a capability CARLOS lacks (there are no
pronoun APs at all), and it is used by a single package. Adding aliases so that one third-party
form's typo resolves would make apconfig a dumping ground for other people's spelling; the better
outcome is the one now in place — the viewer names the unresolved key to the clinician and the
server logs it, so a blank field is visible rather than silent.

**On measurements specifically:** 15 packages read `m$WT#value` and 13 read `m$HT#value`, so
measurement access is common and already works — it is *last-value* access, resolved server-side at
load. Only **one** package needs a full dated *series*, and that is the growth chart the legacy fetch
adapter serves from the `who_measurements` AP. Measurement support is therefore not a platform gap;
treat a new obsolete-fetch form as another row in the adapter's route table, not as evidence that the
channel is missing.

## Environment

```bash
EFORM_CORPUS_DIR=/path/to/zips \
EFORM_CORPUS_OUT=/tmp/eform-smoke/batchN \
CHROME_PATH=/path/to/chrome \
  npm run test:eform-corpus-soak
```

`BASE_URL` defaults to the loopback devcontainer instance and is validated — a non-loopback host
requires `ALLOW_NON_LOCAL_BASE_URL=true`, because this script logs in and performs destructive
imports and saves. `EFORM_CORPUS_DEMOGRAPHIC` (default `1`) and
`EFORM_CORPUS_RENDER_TIMEOUT_MS` (default `120000`) are the other knobs. Results land in
`<out>/corpus-soak.json` beside each PDF.

Budget roughly **2 minutes per refused form** — the timeout dominates — and seconds per successful
one. A 78-package batch takes about an hour.

## Results so far

Cumulative, across 199 real Galaxy packages:

| Batch | Packages | Before | After |
|---|---|---|---|
| 1–2 | 10 | 1 | 6 |
| 3 | 16 | 5 | 8 |
| 4 | 49 | 24 | 41 |
| 5 | 46 | 37 | 45 effective |
| 6 | 78 | 65 | 77 effective |

Batches 5 and 6 opened at 80–83% *before* any batch-specific work, because the earlier fixes were
general rather than per-form. That is the signal to watch: if a new batch opens low, the last round
of fixes was too narrow.
