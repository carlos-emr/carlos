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
| `onComplete` | `function(transport)` | `null` | Called after `onSuccess` or `onFailure`, regardless of outcome. |

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
| Lock release on page unload | `synchronous: true` + `beforeunload` | `navigator.sendBeacon(url, data)` |
| Return value from server | `synchronous: true` + assign in callback | Refactor to `async`/`await` |
| Must-complete-before-close | `synchronous: true` + `beforeunload` | `navigator.sendBeacon()` |
| Sequential operations | `synchronous: true` in loop | `async`/`await` with `fetch()` |

**`navigator.sendBeacon()`** is specifically designed for fire-and-forget requests during page unload. It survives page navigation and does not block the UI thread:
```javascript
window.addEventListener('beforeunload', function() {
    navigator.sendBeacon('releaseNoteLock.do', new URLSearchParams({ noteId: 123 }));
});
```

---

## When NOT to Use CarlosAjax

### Use `fetch()` Directly When:

- **New code with no legacy patterns** — If you're writing new features from scratch and don't need Prototype.js callback ordering, use `fetch()` directly with async/await. Just remember to include the required headers:
  ```javascript
  const response = await fetch(url, {
      method: 'POST',
      headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'X-Requested-With': 'XMLHttpRequest'
      },
      body: new URLSearchParams(params)
  });
  const data = await response.text();
  ```

- **JSON API calls** — For REST endpoints that return JSON:
  ```javascript
  const response = await fetch(url, {
      headers: { 'X-Requested-With': 'XMLHttpRequest' }
  });
  const data = await response.json();
  ```

- **File uploads** — `CarlosAjax` uses `application/x-www-form-urlencoded` by default. For file uploads, use `fetch()` with `FormData`:
  ```javascript
  const formData = new FormData(document.getElementById('uploadForm'));
  const response = await fetch(url, {
      method: 'POST',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
      // Do NOT set Content-Type — browser sets it with boundary for multipart
      body: formData
  });
  ```

### Use `navigator.sendBeacon()` When:

- **Page unload / beforeunload handlers** — Fire-and-forget requests that must survive navigation:
  ```javascript
  navigator.sendBeacon(url, new URLSearchParams({ noteId: 123 }));
  ```
  Note: `sendBeacon()` always sends POST, cannot read the response, and has a ~64KB payload limit.

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
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Requested-With': 'XMLHttpRequest'
        },
        body: new URLSearchParams(data)
    });
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

## Required Headers — Quick Reference

Every AJAX call in CARLOS EMR must include these headers to work correctly with server-side filters:

| Header | Value | Required For |
|--------|-------|-------------|
| `X-Requested-With` | `XMLHttpRequest` | ALL AJAX requests (GET and POST) |
| `Content-Type` | `application/x-www-form-urlencoded` | POST requests with form data |

`CarlosAjax` adds these automatically. If using `fetch()` directly, add them manually.

---

## Existing Pattern Reference

The `documentManager/showDocument.js` file already uses a similar `fetch()` + script extraction pattern and serves as a proven reference implementation for the `evalScripts` behavior.
