# OAuth access-mode validation

Issue [#5246](https://github.com/carlos-emr/carlos/issues/5246) was a mismatch between
`authz-rest-sweep` and the installed access mode. Its OAuth positive control called
`app/getApps/`, which the restricted legacy allowlist deliberately refuses. Changing
the package defaults to satisfy that control would undo the compatibility boundary
introduced in [#4461](https://github.com/carlos-emr/carlos/pull/4461).

The defaults remain:

```properties
oauth.scope.enforcement.enabled=false
oauth.scope.legacy.access=restricted
```

`EXPECT_OAUTH_MODE` declares the expected server behavior to both
`authz-rest-sweep` and `oauth-rest-surfaces`. It does not configure the server or
automatically accept whatever mode it finds. A mismatch fails the check.

| Expected mode | OAuth checks in `authz-rest-sweep` |
| --- | --- |
| `legacy-restricted` (default) | Both providers approve scopeless tokens. Each token must read the owned patient. Apps, chart summary, pharmacies and forms must return the application's exact `403 restricted_endpoint`. These refusals validate the allowlist; they do not count as service-privilege coverage. |
| `scoped` | Both tokens receive `app.read recordux.read pharmacy.read form.read`. Each service must answer 200 for the full provider before its exact service-level 403 counts for the receptionist. A patient read, outside the requested scopes, must return `403 insufficient_scope`. |
| `legacy-full` | Scopeless tokens reach the services; the same full-provider/receptionist controls prove service privilege checks. |

Every mode checks its consent-page warning and the permanent `jobs` block. The
session API checks on `/ws/rs` still exercise anonymous refusals, provider/role
permissions, patient locks and refused writes in every run. The companion
`oauth-rest-surfaces` check covers legacy patient reads/updates and document
attachment, read/write scope boundaries and the broader OAuth handshake/security
checks.

## Default installation

Use a disposable installation with synthetic data and the usual test credentials
and database environment from the browser-test harness. Keep the default server
properties and run:

```sh
EXPECT_OAUTH_MODE=legacy-restricted node scripts/run-playwright-suite.js \
  --only authz-rest-sweep --only oauth-rest-surfaces --province ON
```

The first check reports the legacy allowlist assertions explicitly. A failed
patient-read control, a wrong patient, a WAF refusal or a different OAuth refusal
reason cannot pass as restricted legacy coverage.

## Separate scoped installation

Use a separate disposable installation, configured with
`oauth.scope.enforcement.enabled=true`, and restart it before the run. Both
properties are server-wide, so switching an installation that serves existing
clients can interrupt legacy integrations. Run against the separate installation
using its `BASE_URL` and matching database/test credentials:

```sh
EXPECT_OAUTH_MODE=scoped node scripts/run-playwright-suite.js \
  --only authz-rest-sweep --only oauth-rest-surfaces --province ON
```

Record both runs as release-validation evidence; a default-mode pass does not
claim the OAuth service-privilege coverage of the scoped run. Neither check edits
properties, restarts services, or changes stored token scopes. Discard the scoped
installation afterwards. If an exclusive disposable installation is reused,
restore its original configuration and restart it even when a check fails.

For optional full-legacy validation, use another disposable installation with
enforcement disabled and `oauth.scope.legacy.access=full`, and declare
`EXPECT_OAUTH_MODE=legacy-full`. This is an explicit compatibility option, not a
prerequisite for the normal suite.
