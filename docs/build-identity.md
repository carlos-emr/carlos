# Build identity (build stamp)

CARLOS shows a build stamp on the authenticated About page, in REST
response headers (`buildTag`) and in the HL7 `SFT` segment. It is deliberately
not shown on the login page (see [Where it is shown](#where-it-is-shown)). This
document describes where that value comes from and why it is not a configuration
property.

## Where the value lives

| Artifact | Written by | Read by |
|----------|------------|---------|
| `WEB-INF/classes/carlos-build.properties` | Maven build (`src/main/resources/carlos-build.properties`) | `io.github.carlos_emr.carlos.utility.BuildInfo` |

Keys:

| Key | Source | Example |
|-----|--------|---------|
| `build.version` | Maven resource filtering of `${project.version}` | `2026.08.0-alpha11` |
| `build.date` | `maven-antrun-plugin` timestamp | `2026-09-01 10:15 AM` |
| `build.job` | `JOB_NAME` environment variable, or empty | `carlos-emr-deb`, `carlos-podman` |
| `build.number` | `BUILD_NUMBER` environment variable, or empty | `2026.08.0~alpha11`, `20260901-101500` |

`BuildInfo.getBuildTag()` renders `build.version`, followed by the job/number
pair in parentheses when the build carried one:

```text
2026.08.0-alpha11                                   # release WAR from CI
2026.08.0-alpha11 (carlos-emr-deb 2026.08.0~alpha11) # deb built from source
2026.09.0-SNAPSHOT (carlos-podman 20260901-101500)   # podman compile mode
```

Values that still contain an unsubstituted `${...}` placeholder are treated as
absent, so a build made outside the normal toolchain can never render raw
placeholder text to an unauthenticated visitor. The commit SHA is deliberately
not part of the tag for the same reason.

`CarlosProperties.getBuildDate()` and `CarlosProperties.getBuildTag()` delegate
to `BuildInfo`; they remain the public accessors used across the codebase.

## Why it is not in `carlos.properties`

`carlos.properties` is deployment configuration. Every packaged deployment
(the Debian package, carlos-podman, the devcontainer) loads an operator-owned
copy of it as an **override** on top of the in-WAR file. When the build stamp
was a `buildVersion` key in that file, the value written into the override at
first install shadowed the value in every later WAR, so upgrades kept showing
the first-installed build. Testers reported this on the 2026.08 alpha line as
"it does NOT update the buildVersion".

Build identity is a property of the artifact, so it now ships in its own
classpath resource that no override file is layered onto. Legacy `buildDate`
and `buildVersion` keys in an override file are ignored by the application;
`carlos-ctl init-config` comments them out on the next run so they do not
mislead operators.

## Where it is shown

The build identity is shown to **authenticated** users only, on the About page
(`encounter/ViewAbout`). It is deliberately **not** shown on the login page:
that page is served to unauthenticated visitors, and disclosing the exact build
lets an attacker fingerprint it against known CVEs before authenticating
(CWE-200 / information disclosure). The login page keeps an empty `#buildInfo`
container so its layout is unchanged. Neither the login JSP nor `LoginResourceBean`
references the build tag any more; the tag is computed only by `BuildInfo`
(exposed through `CarlosProperties`) and rendered on the authenticated surfaces
below. When adding a new place to surface the build, put it behind authentication.

## Setting the stamp in a build

- **CI / release WAR**: nothing to set. The tag is the project version.
- **Debian package** (`debian/rules`): sets `JOB_NAME=carlos-emr-deb` and
  `BUILD_NUMBER=<deb version>` for a from-source build. A package built around
  the published release WAR carries that WAR's stamp unchanged.
- **carlos-podman** (`Containerfile`): sets `JOB_NAME=carlos-podman` and
  `BUILD_NUMBER=<image stamp>` so the About page identifies the running image.
- **Local build**: leave both unset; the stamp is the version alone.
