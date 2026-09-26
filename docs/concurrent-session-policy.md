# Concurrent Session Policy

Issue: [#3980](https://github.com/carlos-emr/carlos/issues/3980). Related: #2565 and PR #2673
(one user may hold several sessions), #163 (policy review), #2245 (logout redirect loop).

Since PR #2673, one CARLOS user can be signed in on any number of browsers or devices at once.
The concurrent-session policy lets a site keep that behaviour, ask the user what to do with their
other sessions, or allow only one session per user. It also lets a site cap the number of sessions.

The "keep or sign out other sessions" chooser comes from open-osp/Open-O PR #136 (Chitrank Davé,
merged 2025-12-03 into `release/Open-O-RC.25`). CARLOS reimplements the idea on its Struts 2 login
flow and changes the design where the fork's version had gaps. The table at the end lists those
changes.

## Configuration

Both keys are in `carlos.properties`, or `/etc/carlos-emr/carlos.properties` on a packaged install.
CARLOS reads them at startup, so restart it after a change (`carlos-ctl restart` on a packaged
install).

| Key | Values | Default | Effect |
|---|---|---|---|
| `login.concurrent_sessions.policy` | `allow`, `prompt`, `single` | `allow` | `allow`: keep other sessions and ask nothing. `prompt`: ask the user. `single`: sign the older sessions out automatically. |
| `login.concurrent_sessions.max` | whole number ≥ 0 | `0` (no limit) | Most sessions per user. When a sign-in would exceed the limit under `allow` or `prompt`, the user must sign out their other sessions to continue. |

With the defaults (`allow`, `0`), login is the same as before the policy existed. The login action
checks the defaults and returns before it reads the session registry.

An unrecognised value is logged as a warning and the default is used. A typo in the property file
must not lock every user out.

## What the user sees

Under `prompt`, or when the limit is reached, the chooser appears after every credential check has
passed: password, PIN, MFA and any forced password reset. It appears before the schedule loads. It
lists the user's other sessions with the sign-in time, the time of last activity and the address
each session signed in from. It also warns that signing a session out loses unsaved work in it,
including notes being edited, and releases its note locks. The user has three buttons:

- **Keep other sessions signed in.** This button is not shown when the limit is reached.
- **Sign out other sessions.**
- **Cancel sign-in.** This ends the pending login, signs nobody out and returns to the login page.

A browser whose session was signed out lands on the login page on its next request. The page shows
"You were signed out because your account signed in from another browser or device". Pages that
are only open in the background find out within a minute through the session heartbeat that
`LogoutBroadcastFilter` injects. The notice is shown once.

AJAX login clients (`ajaxResponse=true`) cannot show the chooser. Under `prompt` their other sessions
are kept, and the decision is audited. When the limit is reached, the login is refused with a JSON
error rather than signing out sessions the user was never asked about.

## Design and security invariants

- **No authenticated session until the choice is made.** The chooser is shown from a new
  pre-login session. The old pre-login session is invalidated first, so a session id fixed before
  login cannot be used to finish the login. The new session holds only an opaque, single-use token
  (`PendingSessionChoices.TOKEN_ATTR`) and no `user` attribute, so `LoginFilter` and the session
  heartbeat still treat it as signed out.
- **Credentials and authentication results stay out of the HTTP session.** The pending login
  (security number, provider number, the `LoginCheckLogin.auth` result, the mobile/full-site flag
  and a validated `oauth_token`) is held in `PendingSessionChoiceCache`. That cache is process-local,
  a Caffeine cache with a five-minute TTL keyed by a 256-bit random token, and follows the
  `PendingMfaChallengeCache` pattern. The submit consumes the entry atomically, so a replayed token
  completes nothing.
- **POST-only and CSRF-protected.** `/login/sessionChoice` answers GET and HEAD with 405 before it
  reads anything. It is not in the CSRFGuard unprotected list; only `/login` itself is. The chooser
  form is a real `<form method="post">`, so CSRFGuard injects its token. `LoginFilter` lets the
  route through without a session only because it sits under the exempt `/login` prefix.
- **Only the signing-in user's own sessions.** The pending login is found only through this
  browser's token. The submit re-reads the security row and the provider, and refuses to finish a
  login for an account that was deactivated while the chooser was open.
- **Other sessions are signed out only after the new login fully succeeds.** The new session is
  registered first. The older sessions are settled only once it has passed every failure-prone
  setup step: provider load, facility, logged-in info and OAuth binding. A login that fails
  part-way leaves the user's existing sessions, and any unsaved work in them, untouched.
- **One admission at a time per user.** Counting, deciding, registering and settling run under a
  per-user lock (`ConcurrentSessionAdmission`; 64 lock stripes, per JVM). Two simultaneous logins
  for the same account therefore cannot both count the same sessions. Without the lock, both could
  pass a limit, or both could stay signed in under `single`.
- **Re-checked on submit.** If other sessions signed in while the chooser was open and the limit is
  now reached, "keep" is refused. The chooser is shown again with only "sign out", and the token
  stays valid.
- **Revoked sessions release their locks.** `UserSessionManager.invalidateOtherSessions` invalidates
  each session. That runs `OscarSessionListener`, which releases case-note locks, clears pending
  MFA and chooser state, and unregisters the session. The registry takes a snapshot first because
  the listener modifies the same registry entry.
- **The signed-out notice cannot be forged.** `RevokedUserSessions` keeps a SHA-256 digest of each
  revoked session id, never the id itself, for three hours. `UnauthenticatedRejectionResolver`
  sends a browser whose cookie names a revoked session to `/index` rather than `/logoutPage`,
  because the logout POST would delete the cookie that links the browser to the marker.
  `RootEntryRedirectFilter` consumes the marker and sets a request attribute for the login page.
  The page never reads a URL parameter for the notice. `/index` is exempt from `LoginFilter`, so
  the path cannot loop (#2245). AJAX and download routes still receive a 401 and do not consume
  the marker.

## Audit

The login action writes `LogAction` rows with action `log in` (`LogConst.LOGIN`). The provider
number and client address are recorded as for any login row. The count goes in the `contentId`
column.

| `content` | When | `contentId` |
|---|---|---|
| `concurrent_sessions_prompted` | the chooser is shown | other sessions at that moment |
| `concurrent_sessions_kept` | the user keeps other sessions (or an AJAX client under `prompt`) | other sessions kept |
| `concurrent_sessions_revoked` | the user signs other sessions out | sessions signed out |
| `concurrent_sessions_revoked_auto` | the `single` policy signs older sessions out | sessions signed out |
| `concurrent_sessions_limit_refused` | an AJAX login is refused at the limit | other sessions |
| `concurrent_sessions_invalid_choice` | the chooser was submitted with no or an unknown answer | (empty) |

No PHI is involved. Session ids are never written to the audit log. The application log refers to
a session by its shortened reference only.

## Deployment notes

- The session registry and the revoked-session markers are per JVM. On several Tomcat nodes each
  node enforces the policy for the sessions it holds. A sticky-session load balancer keeps a user's
  sessions on the node that owns them, but sessions on other nodes are not counted or signed out.
- A restart empties the registry. Sessions restored from disk after a restart are not counted until
  they sign in again.

## Code map

| Piece | File |
|---|---|
| Policy value and decision table | `login/ConcurrentSessionPolicy.java` |
| Per-user admission lock | `login/ConcurrentSessionAdmission.java` |
| Login integration and chooser submit | `login/Login2Action.java` (`applyConcurrentSessionPolicy`, `beginSessionChoice`, `submitSessionChoice`, `settleOtherSessions`) |
| Pending login store and session contract | `login/PendingSessionChoiceCache.java`, `login/PendingSessionChoices.java` |
| Chooser view model and page | `login/ConcurrentSessionChoiceViewModel.java`, `WEB-INF/jsp/login/sessionChoice.jsp` |
| Session registry | `managers/UserSessionManager.java`, `managers/UserSessionManagerImpl.java` |
| Signed-out notice | `managers/RevokedUserSessions.java`, `sec/UnauthenticatedRejectionResolver.java`, `login/RootEntryRedirectFilter.java`, `WEB-INF/jsp/login/index.jsp` |
| Route | `WEB-INF/classes/struts-login.xml` (`login/sessionChoice`) |

## Tests

- Unit: `ConcurrentSessionPolicyUnitTest`, `ConcurrentSessionAdmissionUnitTest`, `PendingSessionChoiceCacheUnitTest`,
  `Login2ActionConcurrentSessionUnitTest`, `UserSessionManagerImplUnitTest`,
  `RevokedUserSessionsUnitTest`, `RootEntryRedirectFilterUnitTest` and
  `UnauthenticatedRejectionResolverUnitTest`. `MutatorActionGetRejectionContractUnitTest` registers
  `Login2Action` as a conditional mutator.
- Browser: `scripts/concurrent-session-policy-playwright-checks.js`
  (`npm run test:concurrent-session-policy-playwright`). Run it once per policy, with
  `CONCURRENT_SESSION_POLICY` and `CONCURRENT_SESSION_MAX` set to match the server's configuration.
  Under the default policy, `scripts/login-playwright-checks.js` also checks that a second browser
  signs in with no chooser, and that the chooser route rejects GET, HEAD and CSRF-less POSTs. The
  shared harness `login()` keeps other sessions when the chooser appears, so the rest of the suite
  also runs under `prompt`.

## Differences from the parallel fork

| open-osp/Open-O PR #136 | CARLOS |
|---|---|
| Always prompts; no configuration | `allow` / `prompt` / `single`, plus a session limit; the default keeps the behaviour from before the policy existed |
| Registers the new authenticated session and then asks | Asks before any authenticated session exists |
| Stores the `LoginCheckLogin` object in the HTTP session while the choice is pending | Stores only an opaque single-use token; the payload is kept in a TTL cache |
| NPE when `sessionChoice` is missing | A missing or unknown answer ends the pending login |
| No audit of signed-out sessions | Every decision is audited with a count |
| The signed-out browser gets a bare login page | A one-time "signed in elsewhere" notice, with no redirect loop |
| Struts 1 form bean and CSRF setup | Struts 2 route, POST-only, CSRFGuard-protected, registered in the mutator contract |
