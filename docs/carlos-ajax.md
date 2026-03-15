# CarlosAjax — AJAX Utility Reference

## Overview

`CarlosAjax` is a lightweight AJAX wrapper that replaces Prototype.js's `Ajax.Request` and `Ajax.Updater` with modern `fetch()` and `XMLHttpRequest` APIs. It exists to provide a clean, consistent AJAX interface during and after the Prototype.js migration, preserving the exact behavioral contracts that CARLOS EMR server-side code depends on.

**File**: `src/main/webapp/share/javascript/carlos-ajax.js`
**Loaded via**: `src/main/webapp/includes/global-head.jspf` (available on all pages)

---

## Why CarlosAjax Exists

Replacing `Ajax.Request` / `Ajax.Updater` with raw `fetch()` is error-prone because:

1. **Server-side header dependencies** — Three Java servlet filters check for the `X-Requested-With: XMLHttpRequest` header. Omitting it corrupts AJAX responses with injected HTML.
2. **Content-Type contract** — Server actions expect `application/x-www-form-urlencoded` for POST data. `fetch()` does not set this by default.
3. **Callback ordering** — Prototype fires callbacks in a specific sequence (`onSuccess` → DOM update → `onComplete`). Breaking this order breaks dependent code.
4. **Script execution** — Modern `innerHTML` does NOT execute `<script>` tags. Some AJAX responses contain inline scripts that must run.
5. **Content accumulation** — `Insertion.Bottom`/`Insertion.Top` appends/prepends without destroying existing content.

`CarlosAjax` handles all of these automatically so individual call sites don't need to remember them.

---

## API Reference

### `CarlosAjax.request(url, options)`

Makes an AJAX request without DOM manipulation. Replacement for `Ajax.Request`.

**Parameters:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `method` | `string` | `'POST'` | HTTP method (`'GET'`, `'POST'`, `'PUT'`, `'DELETE'`) |
| `parameters` | `object\|string` | `null` | Request parameters. Objects are serialized to URL-encoded form. Strings are sent as-is. |
| `postBody` | `string` | `null` | Raw request body. If provided, takes precedence over `parameters`. |
| `contentType` | `string` | `'application/x-www-form-urlencoded'` | Content-Type header for POST requests. |
| `requestHeaders` | `object` | `{}` | Additional HTTP headers to include. |
| `synchronous` | `boolean` | `false` | If `true`, uses synchronous `XMLHttpRequest`. See [Synchronous Requests](#synchronous-requests). |
| `onSuccess` | `function(transport)` | `null` | Called on HTTP 2xx response. Receives a transport object. |
| `onFailure` | `function(transport)` | `null` | Called on HTTP 4xx/5xx response. Receives a transport object. |
| `onComplete` | `function(transport)` | `null` | Called after `onSuccess` or `onFailure`, regardless of outcome. Always fires — even on network errors. |

**Network error handling**: When `fetch()` rejects (DNS failure, connection refused, network offline), CarlosAjax catches the error and routes it to `onFailure` with a synthetic transport `{status: 0, responseText: 'Network error: ' + error.message}`, then fires `onComplete`. This matches the expectation of the 17+ `onFailure` callbacks in application code that expect to catch all errors.

**The `transport` object** passed to callbacks has:
- `transport.responseText` — raw response body string
- `transport.status` — HTTP status code (number)

**Example:**

```javascript
CarlosAjax.request('saveNote.do', {
    method: 'POST',
    parameters: { noteId: 123, content: noteText },
    onSuccess: function(transport) {
        console.log('Saved:', transport.responseText);
    },
    onFailure: function(transport) {
        alert('Save failed: ' + transport.status);
    },
    onComplete: function(transport) {
        hideSpinner();
    }
});
```

### `CarlosAjax.updater(elementOrId, url, options)`

Makes an AJAX request and inserts the response HTML into a DOM element. Replacement for `Ajax.Updater`.

**First argument** can be:
- **String** (`'divId'`): Always updates the target element, regardless of success/failure
- **Object** (`{success: 'divId'}`): Only updates the target on HTTP 2xx. On failure, no DOM update occurs. This two-target form is used in `newCaseManagementView.js.jsp` for note saving and issue updating.

**Additional parameters** (beyond those in `request()`):

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `insertion` | `string` | `null` | Where to insert content: `'bottom'`, `'top'`, `'before'`, `'after'`. If `null`, replaces element's innerHTML. |
| `evalScripts` | `boolean` | `false` | If `true`, extracts and executes `<script>` tags from the response after inserting the HTML. |

**Callback execution order** (matches Prototype.js contract):
1. `onSuccess(transport)` — fires first (if HTTP 2xx)
2. DOM update — response HTML inserted into target element
3. Script execution — if `evalScripts: true`, `<script>` tags run after DOM insertion
4. `onComplete(transport)` — fires last, after DOM is updated

**Example:**

```javascript
// Append drug row to existing list (preserves existing rows)
CarlosAjax.updater('rxText', 'addDrug.do', {
    method: 'POST',
    parameters: { drugId: 456 },
    insertion: 'bottom',
    evalScripts: true,
    onComplete: function(transport) {
        updateDrugCount();
    }
});

// Replace entire section content
CarlosAjax.updater('labResults', 'loadLabs.do', {
    method: 'GET',
    parameters: { demographicNo: demoNo },
    evalScripts: true
});
```

---

## Automatic Behaviors

### Headers Sent on Every Request

CarlosAjax automatically includes these headers on ALL requests:

```javascript
{
    'X-Requested-With': 'XMLHttpRequest',
    'X-Prototype-Version': 'CarlosAjax/1.0'  // optional, for logging
}
```

For POST requests, it also sets:
```javascript
{
    'Content-Type': 'application/x-www-form-urlencoded'
}
```

**Why**: Three server-side Java filters check `X-Requested-With`:
- `PrivacyStatementAppendingFilter` — skips privacy statement injection for AJAX
- `CsrfGuardScriptInjectionFilter` — skips CSRF `<script>` injection for AJAX
- `LogoutBroadcastFilter` — skips logout broadcast for AJAX

Without this header, AJAX responses will be corrupted with appended HTML.

### CSRF Token Injection (CRITICAL)

**Background**: CARLOS EMR uses OWASP CSRFGuard 4.5 to protect all POST/PUT/DELETE/PATCH requests. CSRFGuard's JavaScript (`csrfguard.js`) patches `XMLHttpRequest.prototype.open` and `.send` to automatically inject a `CSRF-TOKEN` parameter into every mutating XHR request. It also injects a hidden `CSRF-TOKEN` field into all `<form>` elements via MutationObserver.

**The problem**: CSRFGuard 4.5 does **NOT** intercept the `fetch()` API. Since `fetch()` is a separate browser API from `XMLHttpRequest`, CSRFGuard cannot patch it. If `CarlosAjax` uses `fetch()` for POST requests without including the CSRF token, **every mutating request will be rejected by the server** with a CSRF validation error.

**How CarlosAjax handles this**: For all POST/PUT/DELETE/PATCH requests, CarlosAjax automatically extracts the CSRF token from the DOM and includes it as a **request header**:

```javascript
// Internal implementation:
function getCsrfToken() {
    const tokenInput = document.querySelector('input[name="CSRF-TOKEN"]');
    return tokenInput ? tokenInput.value : '';
}

// On every mutating request, CarlosAjax sends the token as a REQUEST HEADER:
headers: {
    'X-Requested-With': 'XMLHttpRequest',
    'CSRF-TOKEN': getCsrfToken()   // HEADER, not body parameter
}
```

**Why a header, not a body parameter?** CSRFGuard's `Ajax=true` mode (configured in `Owasp.CsrfGuard.properties`) switches CSRF token validation based on the `X-Requested-With` header. When `X-Requested-With: XMLHttpRequest` is present, CSRFGuard validates the token from the **request header** named `CSRF-TOKEN`. Since CarlosAjax always sends `X-Requested-With`, it must also send the CSRF token as a header.

For `navigator.sendBeacon()` (which cannot set custom headers and does not send `X-Requested-With`), CSRFGuard falls back to validating the token from the **POST body parameter** — so sendBeacon correctly includes the token in the body via `URLSearchParams`.

The token value is read from the hidden `<input name="CSRF-TOKEN">` field that CSRFGuard's JavaScript injects into all forms on the page. This field is always present because CSRFGuard uses MutationObserver to inject it into dynamically-created forms as well.

**Token name**: `CSRF-TOKEN` (configured in `Owasp.CsrfGuard.properties`, line 270)
**Protected methods**: POST, PUT, DELETE, PATCH (line 97)
**Token rotation**: Disabled (session-scoped tokens — safe for multi-tab EMR usage)

**Important**: GET requests do NOT need CSRF tokens. CarlosAjax only injects the token for protected HTTP methods.

### CSRF Failure Redirect Detection (CRITICAL)

**The problem**: When CSRFGuard rejects a request (missing/invalid token), it does NOT return a 403 status code. Instead, it **redirects (302) to `errorpage.jsp`**. The `fetch()` API follows redirects transparently by default (`redirect: 'follow'`), so the caller receives the error page HTML with an **HTTP 200 status**. Without detection, `onSuccess` fires with error page content — and `CarlosAjax.updater()` would inject "Looks like something went wrong..." into the DOM.

**How CarlosAjax handles this**: After every `fetch()` response, CarlosAjax checks for CSRF redirect failures:

```javascript
// After fetch() resolves:
if (response.redirected && response.url.includes('errorpage.jsp')) {
    // Treat as failure — invoke onFailure with synthetic 403 status
    const transport = {
        responseText: 'CSRF validation failed — request was rejected by the server.',
        status: 403
    };
    if (options.onFailure) options.onFailure(transport);
    if (options.onComplete) options.onComplete(transport);
    return;
}
```

For synchronous `XMLHttpRequest`, the equivalent check uses `xhr.responseURL`:
```javascript
if (xhr.responseURL && xhr.responseURL.includes('errorpage.jsp')) {
    // Same failure handling
}
```

**Why this matters**: Without this detection, any CSRF token issue (expired session, token not in DOM, race condition) would silently corrupt the page by injecting error page HTML where data should be. This detection ensures CSRF failures are surfaced as errors, not silent data corruption.

### Session Cookie Inclusion

CarlosAjax explicitly sets `credentials: 'same-origin'` on all `fetch()` calls to ensure the JSESSIONID session cookie is always sent:

```javascript
fetch(url, {
    credentials: 'same-origin',
    // ... other options
});
```

While modern browsers default to `credentials: 'same-origin'`, this is set explicitly for defense-in-depth across all browser versions.

### Parameter Serialization

When `parameters` is an object, it is serialized to URL-encoded form data:
```javascript
// Input:  { name: 'John Doe', age: 30 }
// Output: 'name=John%20Doe&age=30'
```

This matches Prototype.js's `Form.serialize()` output format and is compatible with Java's `request.getParameter()`.

### Script Execution (evalScripts)

When `evalScripts: true` is set on `updater()`:
1. Response HTML is parsed for `<script>` tags
2. Non-script HTML is inserted into the target element
3. Each `<script>` tag is dynamically created as a DOM element and appended to the document
4. The browser handles script execution in insertion order

**Security context**: Scripts come from same-origin CARLOS EMR server responses (JSP pages with OWASP encoding). This is the same trust model as the original Prototype.js `evalScripts` behavior.

---

## Synchronous Requests

### When to Use

Synchronous requests block the browser thread until the response is received. They should ONLY be used when:

1. **Page unload handlers** — Code that must complete before the browser navigates away
2. **Return-value patterns** — Functions that must return a value obtained from the server

### How to Use

```javascript
// Synchronous request
CarlosAjax.request('checkLock.do', {
    method: 'POST',
    parameters: { noteId: 123 },
    synchronous: true,
    onSuccess: function(transport) {
        lockStatus = transport.responseText;
    }
});
// lockStatus is available here because the request was synchronous
```

### Modern Alternatives (Preferred)

Synchronous XHR is deprecated in browsers and should be replaced during migration:

| Pattern | Synchronous Workaround | Modern Replacement |
|---------|----------------------|-------------------|
| Lock release on page unload | `synchronous: true` + `beforeunload` | `navigator.sendBeacon()` via `visibilitychange` |
| Return value from server | `synchronous: true` + assign in callback | Refactor to `async`/`await` |
| Must-complete-before-close | `synchronous: true` + `beforeunload` | `navigator.sendBeacon()` via `visibilitychange` |
| Sequential operations | `synchronous: true` in loop | `async`/`await` with `fetch()` |

**`navigator.sendBeacon()`** is specifically designed for fire-and-forget requests during page unload. It survives page navigation and does not block the UI thread.

**Important: Use `visibilitychange`, not `beforeunload`/`unload`**: MDN and the Page Lifecycle API specification recommend `visibilitychange` because `beforeunload`/`unload` are unreliable on mobile browsers and prevent the back-forward cache (bfcache):
```javascript
document.addEventListener('visibilitychange', function() {
    if (document.visibilityState === 'hidden') {
        navigator.sendBeacon('releaseNoteLock.do', new URLSearchParams({
            noteId: 123,
            'CSRF-TOKEN': getCsrfToken()   // REQUIRED — sendBeacon sends POST
        }));
    }
});
```
The `visibilitychange` event fires in all the same scenarios as `beforeunload` (tab close, navigation, app switch) plus mobile scenarios where `beforeunload` does not fire.

---

## When NOT to Use CarlosAjax

### Use `fetch()` Directly When:

- **New code with no legacy patterns** — If you're writing new features from scratch and don't need Prototype.js callback ordering, use `fetch()` directly with async/await. **You MUST include both the required headers AND the CSRF token for POST requests:**
  ```javascript
  // Helper to get CSRF token (or use CarlosAjax.getCsrfToken() if available)
  function getCsrfToken() {
      const el = document.querySelector('input[name="CSRF-TOKEN"]');
      return el ? el.value : '';
  }

  const response = await fetch(url, {
      method: 'POST',
      credentials: 'same-origin',   // REQUIRED — ensures session cookie is sent
      headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'X-Requested-With': 'XMLHttpRequest',
          'CSRF-TOKEN': getCsrfToken()   // REQUIRED as HEADER for POST/PUT/DELETE/PATCH
      },
      body: new URLSearchParams(params)
  });
  // Check for CSRF redirect failure BEFORE using the response
  if (response.redirected && response.url.includes('errorpage.jsp')) {
      throw new Error('CSRF validation failed — request rejected by server');
  }
  const data = await response.text();
  ```

- **JSON API calls (GET only)** — GET requests do NOT need CSRF tokens:
  ```javascript
  const response = await fetch(url, {
      credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest' }
  });
  const data = await response.json();
  ```

- **File uploads** — `CarlosAjax` uses `application/x-www-form-urlencoded` by default. For file uploads, use `fetch()` with `FormData`. CSRFGuard's MutationObserver injects a hidden `CSRF-TOKEN` field into all `<form>` elements, so `FormData` from a form element will include it automatically:
  ```javascript
  // FormData from a form element includes CSRF-TOKEN automatically
  // (CSRFGuard injects the hidden field via MutationObserver)
  const formData = new FormData(document.getElementById('uploadForm'));
  const response = await fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
      // Do NOT set Content-Type — browser sets it with boundary for multipart
      body: formData
  });
  ```

  **Warning**: If you construct a `new FormData()` without a form element (empty constructor), you MUST add the CSRF token manually:
  ```javascript
  const formData = new FormData();  // empty — no auto-injected CSRF-TOKEN
  formData.append('file', fileBlob);
  formData.append('CSRF-TOKEN', getCsrfToken());  // MUST add manually
  ```

### Use `navigator.sendBeacon()` When:

- **Page unload handlers** — Fire-and-forget requests that must survive navigation. Use `visibilitychange` event (NOT `beforeunload`/`unload` — those are unreliable on mobile and block bfcache):
  ```javascript
  document.addEventListener('visibilitychange', function() {
      if (document.visibilityState === 'hidden') {
          navigator.sendBeacon(url, new URLSearchParams({
              noteId: 123,
              'CSRF-TOKEN': getCsrfToken()   // REQUIRED — sendBeacon sends POST
          }));
      }
  });
  ```
  Note: `sendBeacon()` always sends POST, cannot read the response, and has a ~64KB payload limit. Since it sends POST, the CSRF token MUST be included in the body.

- **Page unload with CSRF header validation** — When you need `X-Requested-With` + `CSRF-TOKEN` as headers (not body), use `fetch()` with `keepalive: true` instead of sendBeacon:
  ```javascript
  document.addEventListener('visibilitychange', function() {
      if (document.visibilityState === 'hidden') {
          fetch('releaseNoteLock.do', {
              method: 'POST',
              keepalive: true,     // Survives page unload like sendBeacon
              credentials: 'same-origin',
              headers: {
                  'X-Requested-With': 'XMLHttpRequest',
                  'CSRF-TOKEN': getCsrfToken(),
                  'Content-Type': 'application/x-www-form-urlencoded'
              },
              body: new URLSearchParams({ noteId: 123 })
          });
      }
  });
  ```
  `fetch()` with `keepalive: true` has the same 64KB limit and page-unload resilience as sendBeacon, but supports custom headers. Prefer this over sendBeacon when CSRF header-based validation is needed.

### Do NOT Use CarlosAjax When:

- **Streaming responses** — `CarlosAjax` waits for the full response. Use `fetch()` with `ReadableStream` for streaming.
- **WebSocket connections** — Use the native `WebSocket` API.
- **Cross-origin requests** — `CarlosAjax` is designed for same-origin CARLOS EMR server requests. For external APIs, use `fetch()` with appropriate CORS configuration.
- **Service Worker / Background Sync** — These APIs have their own request mechanisms.

---

## Migration Cheat Sheet

### From `Ajax.Request`

```javascript
// BEFORE (Prototype.js):
new Ajax.Request(url, {
    method: 'post',
    parameters: { key: value },
    onSuccess: function(transport) { handleResponse(transport.responseText); },
    onComplete: function() { cleanup(); }
});

// AFTER (CarlosAjax):
CarlosAjax.request(url, {
    method: 'POST',
    parameters: { key: value },
    onSuccess: function(transport) { handleResponse(transport.responseText); },
    onComplete: function() { cleanup(); }
});
```

### From `Ajax.Updater`

```javascript
// BEFORE (Prototype.js):
new Ajax.Updater('targetDiv', url, {
    method: 'post',
    parameters: data,
    insertion: Insertion.Bottom,
    evalScripts: true,
    onComplete: function() { afterInsert(); }
});

// AFTER (CarlosAjax):
CarlosAjax.updater('targetDiv', url, {
    method: 'POST',
    parameters: data,
    insertion: 'bottom',     // string instead of Prototype class
    evalScripts: true,
    onComplete: function() { afterInsert(); }
});
```

### From `Ajax.Request` with `asynchronous: false`

```javascript
// BEFORE (Prototype.js):
var result;
new Ajax.Request(url, {
    method: 'post',
    asynchronous: false,
    parameters: data,
    onSuccess: function(t) { result = t.responseText; }
});
doSomethingWith(result);

// AFTER (CarlosAjax — interim):
var result;
CarlosAjax.request(url, {
    method: 'POST',
    synchronous: true,
    parameters: data,
    onSuccess: function(t) { result = t.responseText; }
});
doSomethingWith(result);

// AFTER (modern — preferred):
async function doWork() {
    const response = await fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Requested-With': 'XMLHttpRequest',
            'CSRF-TOKEN': getCsrfToken()    // REQUIRED for POST
        },
        body: new URLSearchParams(data)
    });
    // Check for CSRF redirect failure
    if (response.redirected && response.url.includes('errorpage.jsp')) {
        throw new Error('CSRF validation failed');
    }
    const result = await response.text();
    doSomethingWith(result);
}
```

### From `Form.serialize()`

```javascript
// BEFORE (Prototype.js):
var params = Form.serialize(myForm);

// AFTER:
var params = new URLSearchParams(new FormData(myForm)).toString();
```

### From `.evalJSON()`

```javascript
// BEFORE (Prototype.js):
var json = transport.responseText.evalJSON();

// AFTER:
var json = JSON.parse(transport.responseText);
```

---

## Required Headers & Tokens — Quick Reference

Every AJAX call in CARLOS EMR must include these to work correctly with server-side filters and CSRF protection:

| Requirement | Value | Required For |
|-------------|-------|-------------|
| `X-Requested-With` header | `XMLHttpRequest` | ALL AJAX requests (GET and POST) |
| `Content-Type` header | `application/x-www-form-urlencoded` | POST requests with form data |
| `credentials` | `'same-origin'` | ALL fetch() calls (session cookie) |
| `CSRF-TOKEN` **header** | Token from hidden form field | POST, PUT, DELETE, PATCH requests (header because `Ajax=true` mode) |
| CSRF redirect detection | Check `response.redirected` + `response.url` | ALL fetch() calls |

`CarlosAjax` handles all of these automatically. If using `fetch()` directly, add them manually. Missing any one of these will cause silent failures.

---

## Existing Pattern Reference

The `documentManager/showDocument.js` file already uses a similar `fetch()` + script extraction pattern and serves as a proven reference implementation for the `evalScripts` behavior.
