# Documentation dependency assessment — September 2026

The alpha12 promotion review included the existing npm dependency alerts, not
only dependencies changed by the release. Compatible patches are locked for
`colord`, `joi`, `baseline-browser-mapping`, `svgo`, `qs`, `fast-uri`,
`browserslist`, both supported `postcss-selector-parser` lines, and both
`js-yaml` lines. The last dependency was identified by a fresh npm audit after
the original GitHub alerts were assessed. Docusaurus remains on 3.9.2; the update
does not require a framework or parser major-version migration.

## Residual image-size advisories

The upstream npm release remains `image-size@2.0.2`, with no patched version
listed for [ICNS parser nontermination](https://github.com/advisories/GHSA-w3rx-r6r6-pgpr)
or [JXL/HEIF parser nontermination](https://github.com/advisories/GHSA-5p2g-fcmc-qvqq).
These are valid malformed-input availability issues. Do not dismiss them as
false positives or claim that npm audit is clean: npm also reports affected
transitive Docusaurus consumers.

In this repository, the package is consumed by Docusaurus's build-time
`@docusaurus/mdx-loader` image transform. That transform reads resolved local
documentation images through `image-size/fromFile`; remote image URLs are not
read through this parser. It is not a clinical image-upload dependency and is
not shipped in the Maven web application or the Debian application packages.
The generated documentation is static and does not run this Node parser for
site visitors.

The remaining exposure is a build denial of service from a malicious or corrupt
local documentation image. Building a contributor's checkout already executes
its configuration and plugins, so builds must use isolated, resource-limited
workers without production credentials. Do not feed untrusted patient images
or arbitrary uploaded files into this documentation toolchain. Do not rely on
`disableTypes()` on the main package as a fix: version 2.0.2's `fromFile` entry
bundles a separate parser state and does not export that control.

No unreviewed third-party fork, invented version, audit exclusion, or global
module monkey-patch is introduced. Reassess these two advisories when a trusted
patched release or an upstream Docusaurus replacement is available. The
build-only residual is recorded explicitly rather than represented as a fixed
runtime vulnerability.

## Verification

Use `npm ci --ignore-scripts`, `npm audit`, and `npm run build` to reproduce the
dependency and documentation checks. The audit is expected to remain nonzero
for the image-size advisories above; investigate any additional direct advisory.
The alpha12 review also runs the Node regression scripts and the installed
application's Playwright suite separately from the documentation build.
