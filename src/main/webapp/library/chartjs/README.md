# Chart.js 4.5.1

`chart.umd.min.js` is the unmodified MIT-licensed distribution from
<https://registry.npmjs.org/chart.js/-/chart.js-4.5.1.tgz>, at
`package/dist/chart.umd.min.js`. Its bytes also match
<https://cdn.jsdelivr.net/npm/chart.js@4.5.1/dist/chart.umd.min.js>.

SHA-256: `48444a82d4edcb5bec0f1965faacdde18d9c17db3063d042abada2f705c9f54a`.

Keep this third-party artifact out of bulk source rewrites. The prior copy had
eight property names with their trailing `a` removed, including
`drawOnChartArea` and `inChartArea`. This disabled default chart grids and altered
the public helper/event contract. `scripts/chartjs-vendor.test.js` guards the
published bytes; `scripts/chart-draw-playwright-checks.js` checks visible grids
on native line/bar charts and the custom image renderer in Chromium.

`carlos-chart-draw.js` is the application adapter, licensed separately under
GPL-2.0-or-later as stated in its header. Make application rendering changes there.
