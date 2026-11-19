# Release 2026.08 dependency remediation — October 2026

This assessment covers the 21 open Dependabot alerts on `main` retrieved on
October 14, 2026, compared with `release/2026.08` at `108bf284b2`.
Four Jackson alerts were already fixed on the release branch. This change
addresses the other 17 alerts without dismissing advisories or excluding them
from audit results. A release-branch fix does not close an alert against `main`
until the corrected dependency graph reaches that branch.

## Alert coverage

Alert numbers below refer to `carlos-emr/carlos` Dependabot alerts, not issues.
The committed Maven and npm lockfiles record the resolved artifacts and hashes.

| Alerts | Dependency | Release version before this change | Remediation |
| --- | --- | --- | --- |
| 221, 222 | Spring Framework | 7.0.8 | 7.0.9 through the existing Spring BOM; Micrometer commons/observation follow Spring to 1.16.7 |
| 206–209 | Jackson core/databind | 2.22.3 | Already outside the affected ranges ending at 2.22.2; retain and verify the packaged versions |
| 220 | shell-quote | 1.9.0 | 1.11.0 |
| 219 | sprintf-js | 1.0.3 | Remove via argparse 2.0.1 and the local compatibility adapter described below |
| 218 | source-map-js | 1.2.1 | 1.2.2 |
| 217 | proxy-addr | 2.0.7 | 2.0.8 |
| 202, 216 | joi | 17.13.6 | 17.13.8 |
| 215 | compression | 1.8.1 | 1.8.2 |
| 214 | postcss-selector-parser | 6.1.3 and 7.1.3 | 7.1.6 for all consumers |
| 212, 213 | tinypool | 1.1.1 | 2.1.2 |
| 211 | http-cache-semantics | 4.2.0 | Remove the old `got` / `cacheable-request` chain by overriding `latest-version` to 9.0.0 |
| 210 | braces | 3.0.3 | Pin the reviewed depth-guard derivative described below |
| 203–205 | brace-expansion | 5.0.9 | 5.0.12 |

The [Spring XSLT advisory](https://spring.io/security/cve-2026-47884/)
and [Spring SSE advisory](https://spring.io/security/cve-2026-47890/)
identify 7.0.9 as the fixed open-source release. `spring-webmvc` and
`spring-test` are test dependencies here; neither belongs in the application
WAR. The eleven production Spring modules are upgraded together.

The npm dependencies belong to documentation builds and development/test tools.
They are not packaged into the Java application WAR. The declared Node range
is `^20.19.0 || >=22.12.0`, covering Tinypool and the tested ES-module
interoperability. Docusaurus 3.9.2 already required Node 20 or newer. npm
10.9.9 or newer is required for the alias and local-file overrides. CI uses
Node 22 and npm 10.9.9; the development container pins Node 22.22.1 and the same
npm version, with official per-architecture Node checksums.

## Packages without a published upstream fix

### sprintf-js

The [precision advisory](https://github.com/advisories/GHSA-hp3w-g68c-fv3c)
has no fixed upstream release. Argparse 2 removes that dependency. Its legacy
constructor, however, loses the `version` option used by the js-yaml 3 CLI.
`scripts/npm/argparse-compat` translates only that option to the public argparse
2 API and otherwise delegates to the unmodified, pinned upstream package.
It also preserves non-enumerable exports used by js-yaml 3. Both js-yaml 3 and
4 CLI versions, help, YAML/JSON conversion, invalid input, and gray-matter
frontmatter parsing are exercised in the dependency regression tests.

Keep this adapter with the dependency overrides. Remove it when all consumers
use the modern argparse API and its version behavior has been verified.

### http-cache-semantics

The [cache advisory](https://github.com/advisories/GHSA-ch52-4w7c-c8xp)
also has no fixed release. `update-notifier` 6 is already an ES module and can
use `latest-version` 9. This removes the vulnerable caching implementation
entirely. A regression test exercises the actual notifier through a mocked
public fetch boundary and verifies that two responses are fetched independently.

### braces

The [recursive-pattern advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm)
has no patched upstream `braces` release. The override pins
`@dieub/braces-depth-guard@3.0.3-pn.3`, an MIT-licensed derivative of braces 3.0.3,
to reviewed [source commit 305a2e4bfe324bb53c336c1b03387ee1251c926f](https://github.com/dieub/braces-depth-guard/commit/305a2e4bfe324bb53c336c1b03387ee1251c926f).
This is a third-party backport, not an upstream release or endorsement.

`scripts/npm/braces-compat` forwards the original API to the scoped package.
This keeps its real name visible in the dependency graph: GitHub dependency
review treats an npm alias named `braces` as an upstream release and otherwise
assigns upstream advisories to the fork. The bridge declares the actual scoped
dependency, so scanners can track advisories for that package without an
advisory exception or a fabricated upstream version.

Review checked every published file against that source commit, verified the
registry signature and provenance attestation, and examined the depth/length
guards and AST-cycle handling. The lockfile pins the tarball integrity:

```text
sha512-QY+Uq4s42STyIMPoRkBuUZfYyvz0uZuwuUburLwMx5N+lWqnHHaBxcKPtgKVKjTyFnS1q4ivKu9Wxi4VG7FE9Q==
```

The new package name alone is not evidence of a fix. Regression tests resolve
both real consumers, micromatch and chokidar, and check malicious nested
patterns and ASTs, ordinary expansion/compilation, and file watching. CARLOS
maintainers own this pin and its regression checks. Do not update it solely
because a new fork version appears. Replace it with a reviewed upstream fix,
or remove it when the parent dependencies no longer require braces. Recheck
the advisory payloads and both consumers before removing the override.

## Reproducing validation

Use a supported Node version and `npm ci --ignore-scripts`, followed by
`npm audit`, `node --test scripts/dependency-security.test.js`,
`npm run test:scripts`, and `npm run build`. The full script suite requires
Poppler utilities on `PATH`. Audit should report zero vulnerabilities; the
dedicated tests also check actual remediation and API compatibility. They
exercise Tinypool directly because the normal documentation build does not
enable its worker-thread rendering path.

Build and test the Java application with the normal dependency lock check
enabled. Inspect the resulting WAR for Spring 7.0.9 and Jackson 2.22.3.
Live validation used a disposable synthetic database and the existing
`application-health`, `login`, `rest-birth-date`,
`export-content-eform-export-zip`, `oauth-rest-surfaces`, and
`form-rourke2020-growth` Playwright suites. The REST date check ran with the
server JVM in `America/Toronto`; Chromium PDF renderer smoke tests also passed.
