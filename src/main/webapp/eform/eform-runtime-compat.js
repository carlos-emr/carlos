/*
 * Runtime compatibility for stored eForms written against pre-migration CARLOS.
 *
 * Two adaptations live here:
 *
 *  - String timer callbacks. Modern CSP blocks native setTimeout("code", delay) and
 *    setInterval("code", delay). The browser remains the JavaScript parser: stored source is never
 *    scanned or rewritten on the server.
 *  - Obsolete clinical-data fetches. Some forms XHR a route that has since been renamed and that the
 *    render surface could not use even under its new name; those are answered from data the server
 *    embedded in the page. See installCarlosEformLegacyFetchCompatibility below.
 */
(function installCarlosEformTimerCompatibility(window, document) {
    "use strict";

    if (window.__carlosEformTimerCompat && window.__carlosEformTimerCompat.installed) {
        return;
    }

    var nativeSetTimeout = window.setTimeout;
    var nativeSetInterval = window.setInterval;
    var status = {
        installed: false,
        failed: false,
        // Set by the sentinel appended to each injected string-timer script; see
        // executeStringCallback. Declared here so the shape is visible in one place.
        completed: false,
        // Scheduled-but-not-yet-run string timeouts. The renderer awaits this reaching zero (see
        // whenIdle) so a capture cannot outrun the form's own deferred work.
        pending: 0,
        errorMessage: null
    };
    window.__carlosEformTimerCompat = status;

    /**
     * Programmatic equivalent of the capture-phase submit guard below, for callers that submit via
     * HTMLFormElement.submit(). That API deliberately fires no submit event, so the listener never
     * runs for the floating toolbar's save paths (remoteSave -> RichTextLetter.submit()) -- only the
     * legacy SubmitButton.click() path is covered by the listener alone.
     *
     * @return {boolean} true when the caller must abort (a legacy timer failed); the banner is shown.
     */
    status.shouldBlockSubmission = function shouldBlockSubmission() {
        if (!status.failed) {
            return false;
        }
        showFailureNotice();
        return true;
    };

    function showFailureNotice() {
        if (!document.body || document.getElementById("carlos-eform-timer-compat-error")) {
            return;
        }
        var notice = document.createElement("div");
        notice.id = "carlos-eform-timer-compat-error";
        notice.setAttribute("role", "alert");
        notice.style.cssText = "position:fixed;z-index:2147483647;top:0;left:0;right:0;"
                + "padding:12px;background:#8b0000;color:#fff;font:16px sans-serif;text-align:center";
        notice.textContent = "This eForm could not run a legacy timer. Review the form before saving or printing.";
        document.body.insertBefore(notice, document.body.firstChild);
    }

    function markFailure(error) {
        status.failed = true;
        status.errorMessage = error && error.message ? String(error.message) : "Timer callback failed";
        showFailureNotice();
        window.dispatchEvent(new CustomEvent("carlos:eform-timer-compat-error"));
    }

    function executeStringCallback(source) {
        var script = document.createElement("script");
        var executionError = null;
        function captureError(event) {
            // Only errors thrown BY the injected script count. A failed resource load (a 404'd
            // <img> or <script> elsewhere on the page) also dispatches "error", and a capture-phase
            // window listener sees it — script errors target the window, resource errors target the
            // element, so that is the discriminator.
            //
            // Without this the shim blamed the timer for any subresource that happened to fail
            // during the few microseconds this listener is attached, and reported a compatibility
            // failure that blocked the whole render. It presented as flakiness: the same saved form
            // rendered or was refused run to run depending on when the unrelated 404 landed. Legacy
            // eForms deliberately carry such 404s — they reference each asset twice, once bare so
            // the form opens off a local disk — so this fired across the shared-form corpus.
            if (event.target && event.target !== window) {
                return;
            }
            executionError = event.error || new Error(event.message || "Timer callback failed");
        }
        // Completion sentinel. The appended assignment is reached only if the stored source parsed
        // and ran to its end, so it decides success directly instead of inferring it from whatever
        // errors happened to fire nearby. The window "error" listener now only supplies a message
        // for diagnostics; it no longer decides the outcome, because it cannot tell an error thrown
        // by this script from one thrown by any other code in the same tick — and legacy eForms
        // throw constantly. That ambiguity made healthy renders fail intermittently.
        // The leading newline terminates a trailing // comment in the stored source.
        status.completed = false;
        script.textContent = String(source) + "\n;window.__carlosEformTimerCompat.completed = true;";
        window.addEventListener("error", captureError, true);
        try {
            (document.head || document.documentElement).appendChild(script);
        } catch (error) {
            markFailure(error);
            throw error;
        } finally {
            window.removeEventListener("error", captureError, true);
            script.remove();
        }
        if (!status.completed) {
            var failure = executionError
                    || new Error("Timer callback did not run to completion");
            markFailure(failure);
            throw failure;
        }
    }

    function schedule(nativeTimer, receiver, handler, delay, callbackArguments) {
        if (typeof handler !== "string") {
            return nativeTimer.apply(receiver, [handler, delay].concat(callbackArguments));
        }
        // Only one-shot timers are counted. A repeating setInterval would never drain, so waiting on
        // it would stall every render that uses one.
        var counted = nativeTimer === nativeSetTimeout;
        if (counted) {
            status.pending += 1;
        }
        return nativeTimer.call(receiver, function runStoredTimerSource() {
            try {
                executeStringCallback(handler);
            } finally {
                // finally, not after the call: executeStringCallback rethrows a failed timer, and
                // leaking the count would leave the renderer waiting for a timer that already ran.
                if (counted) {
                    status.pending -= 1;
                }
            }
        }, delay);
    }

    /**
     * Resolves once every scheduled string timer has run, or once maxWaitMillis has elapsed.
     *
     * <p>The PDF renderer awaits this before capturing. Without it the capture raced the form: page
     * stabilization settles after a short quiet window, while stored timers are typically scheduled
     * a second or more out, so the timer usually never ran at all — and a timer that populates a
     * field left that field BLANK in the delivered PDF, with every gate satisfied. When the render
     * did outlast the delay the timer fired and could report a failure, so the same saved form
     * produced different results run to run.</p>
     *
     * <p>Polls with the native timer so the wait neither recurses through the wrapper above nor
     * inflates the count it is waiting on.</p>
     *
     * @return {Promise<boolean>} true when the queue drained, false when the wait was capped
     */
    status.whenIdle = function whenIdle(maxWaitMillis) {
        var deadline = Date.now() + (maxWaitMillis > 0 ? maxWaitMillis : 3000);
        return new Promise(function settleWhenDrained(resolve) {
            (function poll() {
                if (status.pending <= 0) {
                    resolve(true);
                } else if (Date.now() >= deadline) {
                    resolve(false);
                } else {
                    nativeSetTimeout.call(window, poll, 50);
                }
            }());
        });
    };

    window.setTimeout = function setTimeoutCompatible(handler, delay) {
        return schedule(
                nativeSetTimeout,
                window,
                handler,
                delay,
                Array.prototype.slice.call(arguments, 2));
    };
    window.setInterval = function setIntervalCompatible(handler, delay) {
        return schedule(
                nativeSetInterval,
                window,
                handler,
                delay,
                Array.prototype.slice.call(arguments, 2));
    };
    document.addEventListener("submit", function blockSubmitAfterTimerFailure(event) {
        if (status.failed) {
            event.preventDefault();
            event.stopImmediatePropagation();
            showFailureNotice();
        }
    }, true);
    document.addEventListener("securitypolicyviolation", function detectBlockedTimerScript(event) {
        // The shim routes string handlers through an injected <script>, so violations normally arrive
        // as "inline". A native setTimeout("code", n) running before the shim installs is reported as
        // "eval" instead, and would otherwise leave status.failed false while the timer never ran.
        if ((event.blockedURI === "inline" || event.blockedURI === "eval")
                && String(event.violatedDirective).indexOf("script-src") === 0) {
            markFailure(new Error("Content Security Policy blocked timer compatibility"));
        }
    });
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function showPendingFailure() {
            if (status.failed) {
                showFailureNotice();
            }
        });
    }
    status.installed = true;
}(window, document));

/*
 * Answers obsolete clinical-data fetches from data embedded in the page.
 *
 * Some stored forms plot measurement history fetched from
 * "oscarEncounter/oscarMeasurements/SetupDisplayHistory.do?type=HT". That action still exists under a
 * new name, but the PDF render surface cannot reach it: it reads its demographic from the HTTP
 * session, requires _measurement write, and returns a full JSP page — and the render browser holds no
 * session by design. EFormRenderPdfHtmlComposer therefore inlines the history, and this shim serves it.
 *
 * The interception must be SYNCHRONOUS. The calling forms use xmlhttp.open(url, false) and read the
 * parsed result on the line after send() returns, so anything deferred arrives after the form has
 * already given up and plotted nothing.
 */
(function installCarlosEformLegacyFetchCompatibility(window, document) {
    "use strict";

    if (!window.XMLHttpRequest || window.__carlosEformLegacyFetch) {
        return;
    }

    // Matched on the path tail: the forms assemble the URL from window.location at runtime, so this
    // is the only stable part of it.
    var LEGACY_MEASUREMENT_ROUTE = "oscarMeasurements/SetupDisplayHistory";
    var PAYLOAD_ELEMENT_ID = "carlos-legacy-measurement-history";
    var TYPE_PATTERN = /[?&]type=([A-Za-z0-9_]{1,32})/;

    var status = {installed: false, served: 0};
    window.__carlosEformLegacyFetch = status;

    /**
     * @return {?string} the embedded response for this URL, or null to leave the request alone.
     *
     * Returning null on a missing or unparseable payload is deliberate. The request then goes to the
     * network and fails there, which the renderer's completeness gate counts as missing content —
     * whereas answering with an empty body would produce a blank chart on a passing render, which
     * nobody would notice.
     */
    function embeddedResponseFor(url) {
        var text = String(url == null ? "" : url);
        if (text.indexOf(LEGACY_MEASUREMENT_ROUTE) < 0) {
            return null;
        }
        var element = document.getElementById(PAYLOAD_ELEMENT_ID);
        var type = TYPE_PATTERN.exec(text);
        if (!element || !type) {
            return null;
        }
        var byType;
        try {
            byType = JSON.parse(element.textContent);
        } catch (error) {
            return null;
        }
        return typeof byType[type[1]] === "string" ? byType[type[1]] : null;
    }

    var nativeOpen = window.XMLHttpRequest.prototype.open;
    var nativeSend = window.XMLHttpRequest.prototype.send;

    window.XMLHttpRequest.prototype.open = function openCompatible(method, url) {
        var body = embeddedResponseFor(url);
        if (body === null) {
            // Clear any marker from a previous use of this same object before delegating.
            delete this.__carlosLegacyResponse;
            return nativeOpen.apply(this, arguments);
        }
        this.__carlosLegacyResponse = body;
        return undefined;
    };

    window.XMLHttpRequest.prototype.send = function sendCompatible() {
        if (typeof this.__carlosLegacyResponse !== "string") {
            return nativeSend.apply(this, arguments);
        }
        var body = this.__carlosLegacyResponse;
        function readOnly(value) {
            return {configurable: true, get: function () { return value; }};
        }
        // Own accessors shadow the prototype's read-only ones for this instance.
        Object.defineProperty(this, "readyState", readOnly(4));
        Object.defineProperty(this, "status", readOnly(200));
        Object.defineProperty(this, "statusText", readOnly("OK"));
        Object.defineProperty(this, "responseText", readOnly(body));
        Object.defineProperty(this, "response", readOnly(body));
        status.served += 1;
        // Dispatch rather than calling this.onreadystatechange directly. Handler properties are
        // themselves registered listeners, so dispatching notifies both them and any
        // addEventListener callers exactly once; calling the property directly would strand a
        // listener-based caller waiting on a request that is never going to the network.
        this.dispatchEvent(new Event("readystatechange"));
        this.dispatchEvent(new Event("load"));
        this.dispatchEvent(new Event("loadend"));
        return undefined;
    };

    status.installed = true;
}(window, document));
