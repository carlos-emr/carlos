# Session Management and Logout Broadcast

## Overview

CARLOS EMR uses a two-layer approach to ensure all open browser windows and tabs are cleaned up when a user's session ends, whether from manual logout, inactivity timeout, or server restart.

| Layer | Detects | Mechanism |
|-------|---------|-----------|
| **Logout broadcast** | Manual logout, server-detected timeout | `logout.jsp` sends BroadcastChannel + localStorage message before redirecting to `logout.do` |
| **Session heartbeat** | Server restart, session destroyed, inactivity | Client polls `/status/sessionHeartbeat.jsp` every 60s; triggers logout on `{"valid":false}` or if no successful response within the inactivity limit |

Both layers trigger the same response: broadcast "logout" to all windows, then popups close and tabs redirect to login.

## Configuration

### INACTIVITY_LIMIT_MINS

Controls how long (in minutes) a user can be idle before their session expires.

```properties
# In carlos.properties or over_ride_config.properties
INACTIVITY_LIMIT_MINS=60
```

- **Default**: 60 minutes (applied by LoginFilter when not configured)
- **Minimum recommended**: 5 minutes (lower values may cause false logouts during slow page loads)
- **Disable**: Not recommended for healthcare environments; if needed, set to a very large value

### Three Timeout Layers

CARLOS has three independent session timeout mechanisms:

1. **LoginFilter (application-level)**: Tracks `last_request_time` session attribute, compares against `INACTIVITY_LIMIT_MINS`. This is the primary inactivity timer.

2. **Container session timeout**: Configured in `web.xml` via `<session-timeout>`. Currently set to 120 minutes. Acts as a hard upper bound on session lifetime regardless of activity.

3. **Client-side safety net**: The injected heartbeat script tracks `lastSuccessfulHeartbeat` timestamp. If no successful heartbeat response is received within `INACTIVITY_LIMIT_MINS`, the client triggers logout. This catches edge cases like prolonged network outages.

## Architecture

### Components

#### `sessionHeartbeat.jsp`
- Location: `src/main/webapp/status/sessionHeartbeat.jsp` (in `status/` directory for general health/status endpoints)
- Lightweight JSON endpoint: returns `{"valid": true}` or `{"valid": false}`
- Uses `session="false"` JSP directive to prevent orphan session creation
- Called with `?autoRefresh=true` to bypass `UserActivityFilter` activity tracking
- Added to LoginFilter's `EXEMPT_URLS` (accessible without auth) and `EXEMPT_URLS_FOR_REQUEST_TIMEOUT` (doesn't reset inactivity timer)

#### `LogoutBroadcastFilter`
- Location: `src/main/java/io/github/carlos_emr/carlos/app/LogoutBroadcastFilter.java`
- Servlet filter registered in `web.xml` after `PrivacyStatementAppendingFilter`
- Injects ~1KB inline JavaScript into all authenticated HTML responses
- Exclusions configured via init-param (excludes `/logout.jsp` to prevent self-listening)

#### `logout.jsp`
- Modified to broadcast logout signal before redirecting to `logout.do`
- Uses `session="false"` to prevent orphan session creation
- Includes `<meta http-equiv="refresh">` as no-JavaScript fallback

### Injected JavaScript Behavior

The script injected by `LogoutBroadcastFilter` does two things:

**1. Logout Listener**
- Listens on `BroadcastChannel('carlos_logout')` (primary)
- Listens on `window.storage` event for `carlos_logout_signal` key (fallback for older browsers)
- On receiving signal: closes window (works for popups) or redirects to login page

**2. Session Heartbeat**
- Polls `sessionHeartbeat.jsp?autoRefresh=true` every 60 seconds
- On `{"valid": true}`: updates `lastSuccessfulHeartbeat` timestamp
- On `{"valid": false}`: broadcasts logout to all windows
- On network error: silently retries next cycle (no false logout)
- If no successful response for longer than `INACTIVITY_LIMIT_MINS`: triggers logout (safety net)

### Idempotency

Each window uses a `done` flag (called `logoutInProgress` in concept) to ensure:
- The broadcast is sent at most once per window
- `window.close()` and redirect execute at most once
- Receiving a broadcast while already logging out is a no-op

### Guard Against Duplicate Injection

The script sets `window.__carlosLogoutActive = true` at the start. If the script runs a second time (e.g., nested frames), the guard prevents duplicate listeners and heartbeat timers.

## Scenarios

### Manual Logout
1. User clicks logout -> browser loads `/logout.jsp`
2. `logout.jsp` broadcasts `'logout'` via BroadcastChannel + localStorage
3. All other windows receive signal -> popups close, tabs redirect to login
4. `logout.jsp` redirects to `logout.do` -> session invalidated -> login page shown

### Inactivity Timeout (Server-Detected)
1. User idle for configured period. Next real request from any window hits LoginFilter.
2. LoginFilter detects `last_request_time` exceeded -> redirects that window to `/logout.jsp`
3. Same as manual logout from step 2 onward

### Inactivity Timeout (Heartbeat-Detected)
1. User idle. Heartbeat polls every 60s. After server-side timeout fires, session is invalidated.
2. Next heartbeat poll returns `{"valid": false}`
3. That window calls `broadcastLogout()` -> all windows notified -> popups close, tabs redirect

### Server Restart / Session Destroyed
1. Server restarts. All sessions lost.
2. During restart: heartbeat polls get network errors -> silently ignored (no false logout)
3. Once server is back: next heartbeat returns `{"valid": false}` (old session gone)
4. `broadcastLogout()` fires -> all windows close/redirect

### Prolonged Network Outage
1. Network goes down. Heartbeat polls fail silently.
2. After configured inactivity limit with no successful `{"valid": true}`, client-side safety net fires.
3. `broadcastLogout()` -> all windows close/redirect. Patient data protected.

### Only Popup Windows Open (Main Window Closed)
1. Each popup has the injected listener with its own heartbeat
2. The first popup to detect session loss broadcasts to all others
3. All popups close (`window.close()` works since they were opened via `window.open()`)

## Browser Constraints

- **`window.close()`**: Only works for windows opened via `window.open()` (popups). For tabs opened by the user, the browser ignores `window.close()` and the script falls back to redirecting to the login page after 200ms.
- **BroadcastChannel**: Supported in all modern browsers. Not available in IE11.
- **localStorage `storage` event**: Provides fallback for browsers without BroadcastChannel. The event only fires in *other* windows (not the one that set the value), which is exactly the desired behavior.

## Performance Impact

- Heartbeat interval: 60 seconds per window
- With 10 open windows: ~10 additional requests/minute to the server
- `sessionHeartbeat.jsp` is extremely lightweight (no Spring beans, no business logic)
- Comparable to existing `tabAlertsRefresh.jsp` polling already in the application

## Testing

### Quick Verification
1. `make install` - build succeeds with no errors
2. Log in, open multiple windows/tabs
3. Click logout in one window -> verify all others close or redirect

### Inactivity Timeout Test
1. Set `INACTIVITY_LIMIT_MINS=2` in `over_ride_config.properties`
2. Restart server
3. Log in, open multiple windows
4. Wait 2+ minutes without interaction
5. Verify all windows detect expiry and close/redirect

### Server Restart Test
1. Log in, open multiple windows
2. Run `server restart`
3. Once server is back, within 60s all windows should detect loss and redirect

### Exclusion Verification
- Login page (`index.jsp`): injected script should NOT appear (no valid session)
- AJAX responses: check browser devtools Network tab -> script not in AJAX responses
- `logout.jsp`: script NOT injected (in exclusion list)
- Heartbeat polling: verify `last_request_time` is NOT reset (check debug logs with `debug-on`)
