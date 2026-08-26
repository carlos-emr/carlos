/*
 * Runtime compatibility for stored eForms written against pre-migration CARLOS.
 *
 * Two adaptations live here:
 *
 *  - Timer callbacks. Modern CSP blocks native string callbacks such as setTimeout("code", delay),
 *    so those are executed through an injected script while every one-shot timeout is tracked for the
 *    PDF renderer. The browser remains the JavaScript parser: stored source is never scanned or
 *    rewritten on the server.
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
    var nativeClearTimeout = window.clearTimeout;
    var nativeSetInterval = window.setInterval;
    var nativeClearInterval = window.clearInterval;
    var countedTimeouts = new Set();
    // How many consecutive same-reference self-reschedules (a function's setTimeout callback
    // calling setTimeout(itself, ...) again while it is still running) stay counted before the
    // guard in schedule() below treats further reschedules as a repeating heartbeat/UI loop
    // rather than legitimate chained deferred work. See the comment at that guard for why a
    // single currently-running check cannot tell the two apart on its own.
    var SELF_RESCHEDULE_COUNT_LIMIT = 3;
    // Per-handler count of CONSECUTIVE self-reschedules within the CURRENT chain. Reset to zero
    // (by deleting the entry) once an invocation completes without rescheduling itself again, or
    // once a still-pending reschedule of that handler is cancelled -- see the reset sites below.
    // Without a reset, a function reference reused later for an unrelated, independent short
    // chain would inherit the previous chain's leftover count and could have its own first
    // reschedule wrongly excluded from the start.
    var selfRescheduleCounts = new Map();
    // Tracks which scheduled handle belongs to which function handler, solely so a cancellation
    // (clearTimeout/clearInterval) can reset that handler's selfRescheduleCounts entry too.
    var handleToHandler = new Map();
    // Once a self-reschedule is excluded from status.pending for exceeding
    // SELF_RESCHEDULE_COUNT_LIMIT, it becomes invisible to whenIdle()'s pending<=0 check --
    // otherwise a still-actively-rescheduling excluded chain could let whenIdle resolve true
    // (and the PDF be captured) before a later pass populates a field, even though the intent was
    // for a genuinely long-running loop to still be bounded by the render budget, not to
    // disappear from tracking entirely. Holds the handle of every excluded self-reschedule that is
    // still scheduled (not yet fired or cancelled); whenIdle() below waits while this is non-empty.
    // Tracked per HANDLE, not as a single deadline shared by the whole page: an earlier version
    // recorded one global "next fire time," which a cancelled excluded reschedule left stale --
    // whenIdle then waited out that now-irrelevant future time (or the render budget cap) for a
    // callback that would never run. Removing the handle here when it fires or is cancelled (see
    // the two removal sites below) means the set holds exactly the excluded work still genuinely
    // outstanding, nothing more. A chain that stops rescheduling empties the set as soon as its
    // last invocation completes; a repeating heartbeat keeps re-adding a fresh handle before the
    // old one is removed, so the set never stays empty and whenIdle correctly falls through to its
    // own maxWaitMillis deadline instead of waiting on it forever. See the whenIdle comment below.
    var excludedSelfReschedulePending = new Set();
    var status = {
        installed: false,
        failed: false,
        // Set by the sentinel appended to each injected string-timer script; see
        // executeStringCallback. Declared here so the shape is visible in one place.
        completed: false,
        // Scheduled-but-not-yet-run one-shot timeouts. The renderer awaits this reaching zero (see
        // whenIdle) so a capture cannot outrun the form's own deferred work.
        pending: 0,
        errorMessage: null
    };
    window.__carlosEformTimerCompat = status;

    function isPdfRenderAutoSubmit(handler) {
        // This is intentionally an exact allowlist, not source rewriting or a general attempt to
        // understand stored eForm JavaScript. Across the shared corpus this delayed callback is the
        // dominant timer (25/50): it clicks the form's submit button after printing. A passive PDF
        // render neither needs nor may perform that state-changing action, so waiting 1.8 seconds
        // for it only delays capture. The marker is injected solely by the PDF HTML composer; the
        // interactive eForm viewer retains the original callback unchanged.
        return window.__carlosEformPdfRender === true
                && typeof handler === "string"
                && /^\s*SubmitButton\s*\.\s*click\s*\(\s*\)\s*;?\s*$/.test(handler);
    }

    /**
     * Surfaces the advisory timer-failure banner for callers that submit via HTMLFormElement.submit().
     * That API deliberately fires no submit event, so the capture-phase listener below never runs for
     * the floating toolbar's save paths (remoteSave -> HTMLFormElement.submit()) -- only the legacy
     * SubmitButton.click() path is covered by the listener alone.
     *
     * <p>A failed legacy timer is ADVISORY, not blocking. The server render already treats
     * timerCompatibilityFailure as an advisory condition and DELIVERS the document (it is counted in
     * EFormRenderCompletenessReport.advisoryIssueCount, never withheld), so the print/save/fax paths
     * must reach that server gate rather than dead-ending here -- otherwise the clinician can never
     * produce a document the server would have delivered with a warning. This shows the notice so the
     * clinician is informed; it never aborts the submission.</p>
     *
     * @return {boolean} whether a legacy timer failed (the banner has been shown). Callers use this to
     *     warn, not to block.
     */
    status.warnBeforeSubmission = function warnBeforeSubmission() {
        if (status.failed) {
            showFailureNotice();
        }
        return status.failed;
    };

    function showFailureNotice() {
        if (!document.body || document.getElementById("carlos-eform-timer-compat-error")) {
            return;
        }
        // Advisory, not an error: a failed legacy timer no longer blocks saving or printing (the
        // server delivers the document with its own advisory banner), so this warns rather than
        // alarms -- amber/role="status" to match the toolbar's post-render advisory notice, not the
        // red/role="alert" of a hard failure. The id stays stable: the PDF render surface hides this
        // element by that id so the banner never prints (EFormBrowserPdfService render CSS).
        var notice = document.createElement("div");
        notice.id = "carlos-eform-timer-compat-error";
        notice.setAttribute("role", "status");
        notice.style.cssText = "position:fixed;z-index:2147483647;top:0;left:0;right:0;padding:10px;"
                + "background:#fff3cd;color:#664d03;border-bottom:1px solid #ffc107;"
                + "font:14px sans-serif;text-align:center";
        notice.textContent = "This eForm could not run a legacy timer, so some fields may be missing"
                + " content. Review it before saving or printing.";
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
        if (nativeTimer === nativeSetTimeout && isPdfRenderAutoSubmit(handler)) {
            status.suppressedAutoSubmits = (status.suppressedAutoSubmits || 0) + 1;
            // Preserve the timer-handle shape for form code that clears it, but do not enqueue a
            // callback that would submit or mutate state on the render-only surface.
            return nativeSetTimeout.call(window, function suppressedPdfAutoSubmit() {}, 0);
        }
        // Only one-shot timers are counted. A repeating setInterval would never drain, so waiting on
        // it would stall every render that uses one. Legacy string timers stay tracked at every delay
        // so a late one reports an incomplete render. Function callbacks are tracked only through the
        // existing four-second render budget: pages also schedule long-lived UI/heartbeat callbacks
        // that cannot affect this capture and would otherwise make every render wait until the cap.
        var delayMillis = delay == null ? 0 : Number(delay);
        var runningFunctionHandlers = status.runningFunctionHandlers || (status.runningFunctionHandlers = []);
        // A same-reference self-reschedule (handler calls setTimeout(handler, ...) again while it
        // is still on the stack) is ambiguous on its own: it is exactly how BOTH a repeating
        // heartbeat/UI loop AND a short chained-completion sequence (e.g. "poll until data is
        // ready, then populate the field and stop") reschedule themselves. Excluding every such
        // reschedule unconditionally (as this guard once did) stopped counting a chain after its
        // very first pass, so whenIdle could resolve -- and the PDF could be captured -- before a
        // still-pending later pass populated a field: the same blank-field race this tracking
        // exists to prevent, just moved one step later. Counting the first
        // SELF_RESCHEDULE_COUNT_LIMIT self-reschedules keeps a short chain fully covered while
        // still letting a loop that keeps rescheduling past that bound fall back to being governed
        // by the render budget cap, same as before.
        var isSelfReschedule = typeof handler === "function" && runningFunctionHandlers.indexOf(handler) >= 0;
        var selfRescheduleCount = 0;
        if (isSelfReschedule) {
            selfRescheduleCount = (selfRescheduleCounts.get(handler) || 0) + 1;
            selfRescheduleCounts.set(handler, selfRescheduleCount);
        }
        var selfRescheduleExcluded = isSelfReschedule && selfRescheduleCount > SELF_RESCHEDULE_COUNT_LIMIT;
        var counted = nativeTimer === nativeSetTimeout
                && (typeof handler === "string"
                        || (typeof handler === "function"
                                && isFinite(delayMillis) && delayMillis <= 4000
                                && !selfRescheduleExcluded));
        if (counted) {
            status.pending += 1;
        }
        if (typeof handler !== "string" && typeof handler !== "function") {
            return nativeTimer.apply(receiver, [handler, delay].concat(callbackArguments));
        }
        var handle;
        try {
            handle = nativeTimer.call(receiver, function runScheduledTimer() {
                try {
                    if (typeof handler === "string") {
                        return executeStringCallback(handler);
                    }
                    // Captured before invocation so the finally below can tell whether THIS
                    // invocation triggered a further self-reschedule (the count changed) or the
                    // chain ended here (the count is unchanged) -- see selfRescheduleCounts above.
                    var selfRescheduleCountBeforeInvocation = selfRescheduleCounts.get(handler);
                    runningFunctionHandlers.push(handler);
                    try {
                        return handler.apply(receiver, callbackArguments);
                    } finally {
                        runningFunctionHandlers.pop();
                        if (selfRescheduleCounts.get(handler) === selfRescheduleCountBeforeInvocation) {
                            selfRescheduleCounts.delete(handler);
                        }
                    }
                } finally {
                    handleToHandler.delete(handle);
                    // This handle's excluded reschedule (if it was one) fired: it is no longer
                    // outstanding, whether or not it rescheduled itself again -- a further
                    // reschedule adds its OWN new handle to the set during handler.apply() above,
                    // before this delete() for the old handle runs.
                    excludedSelfReschedulePending.delete(handle);
                    // delete() makes completion and cancellation mutually exclusive: whichever
                    // happens first owns the one matching decrement.
                    if (counted && countedTimeouts.delete(handle)) {
                        status.pending -= 1;
                    }
                }
            }, delay);
        } catch (error) {
            if (counted) {
                status.pending -= 1;
            }
            throw error;
        }
        if (counted) {
            countedTimeouts.add(handle);
        }
        if (typeof handler === "function") {
            handleToHandler.set(handle, handler);
        }
        // Matches the nativeTimer === nativeSetTimeout gate already in the counted condition
        // above: a repeating setInterval is never one-shot, so it must never enter pending
        // tracking of any kind (counted or excluded-but-pending). Without this guard, a
        // self-rescheduling setInterval registration could still be added here, and its handle
        // is only ever removed on its FIRST tick (see the finally block below) -- until then,
        // whenIdle() would refuse to resolve early for a timer that was supposed to be entirely
        // excluded, hitting the render's own cap instead.
        if (selfRescheduleExcluded && nativeTimer === nativeSetTimeout) {
            excludedSelfReschedulePending.add(handle);
        }
        return handle;
    }

    /**
     * Resolves once every scheduled one-shot timer has run, or once maxWaitMillis has elapsed.
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
     * <p>A self-reschedule excluded from {@code status.pending} for exceeding
     * {@code SELF_RESCHEDULE_COUNT_LIMIT} is invisible to the {@code pending <= 0} check below, so
     * this also requires {@code excludedSelfReschedulePending} to be empty before resolving early.
     * That set holds exactly the excluded handles still genuinely scheduled (not yet fired or
     * cancelled), so a chain that stops rescheduling empties it as soon as its last invocation
     * completes, while a repeating heartbeat/UI loop keeps a handle in it continuously and so
     * correctly falls through to {@code deadline} instead of silently letting the capture race
     * ahead of it.</p>
     *
     * @return {Promise<boolean>} true when the queue drained, false when the wait was capped
     */
    status.whenIdle = function whenIdle(maxWaitMillis) {
        var deadline = Date.now() + (maxWaitMillis > 0 ? maxWaitMillis : 3000);
        return new Promise(function settleWhenDrained(resolve) {
            (function poll() {
                var now = Date.now();
                if (now >= deadline) {
                    resolve(false);
                } else if (status.pending <= 0 && excludedSelfReschedulePending.size === 0) {
                    resolve(true);
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
    window.clearTimeout = function clearTimeoutCompatible(handle) {
        return cancelTrackedTimer(nativeClearTimeout, handle);
    };
    window.clearInterval = function clearIntervalCompatible(handle) {
        return cancelTrackedTimer(nativeClearInterval, handle);
    };
    function cancelTrackedTimer(nativeClear, handle) {
        if (countedTimeouts.delete(handle)) {
            status.pending -= 1;
        }
        // This handle will never fire now, so it can no longer be outstanding excluded work --
        // without this, whenIdle() would wait on a cancelled callback that will never run.
        excludedSelfReschedulePending.delete(handle);
        var cancelledHandler = handleToHandler.get(handle);
        if (cancelledHandler) {
            // The chain this handler was rescheduling ends here too: clear its count so a later,
            // unrelated scheduling of the same function reference starts counting from zero
            // instead of inheriting this cancelled chain's leftover count.
            handleToHandler.delete(handle);
            selfRescheduleCounts.delete(cancelledHandler);
        }
        return nativeClear.call(window, handle);
    }
    window.setInterval = function setIntervalCompatible(handler, delay) {
        return schedule(
                nativeSetInterval,
                window,
                handler,
                delay,
                Array.prototype.slice.call(arguments, 2));
    };
    document.addEventListener("submit", function warnOnSubmitAfterTimerFailure() {
        // A failed legacy timer is advisory, not blocking (see warnBeforeSubmission): the server
        // render delivers the document with a warning rather than withholding it, so a native
        // SubmitButton.click() submit must proceed to that gate. Surface the notice; do not cancel.
        if (status.failed) {
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

    /**
     * @return {?Document} the body parsed as HTML, or null if it cannot be parsed.
     *
     * The embedded payload is a fragment of table cells rather than a whole document, so it is
     * parsed as text/html — parsing it as XML would fail on the first unclosed tag and hand back a
     * parsererror document that looks like content.
     */
    function parseResponseXml(body) {
        try {
            return new DOMParser().parseFromString(body, "text/html");
        } catch (error) {
            return null;
        }
    }

    /**
     * Names shadowed on an intercepted instance by send(), removed here before every reuse.
     *
     * The synthetic getters are own properties, so they outlive the request that installed them and
     * permanently mask the prototype's real ones. An object reused for a genuine second request had
     * that request actually go out — and every reader still saw the FIRST embedded body, with
     * readyState frozen at 4 and status at 200, so a caller polling `readyState == 4 && status == 200`
     * observed an immediate false completion carrying another endpoint's response. On this surface
     * that means one patient's measurements answering a different question.
     */
    var SYNTHETIC_RESPONSE_PROPERTIES = [
        "readyState", "status", "statusText", "responseText", "response", "responseXML"
    ];

    window.XMLHttpRequest.prototype.open = function openCompatible(method, url) {
        // Unconditionally, and before deciding whether to intercept: a reused object must start from
        // the prototype's real getters whichever way this request is answered. They are declared
        // configurable, so deleting an own property restores the prototype's.
        for (var i = 0; i < SYNTHETIC_RESPONSE_PROPERTIES.length; i++) {
            delete this[SYNTHETIC_RESPONSE_PROPERTIES[i]];
        }
        var body = embeddedResponseFor(url);
        // Always open for real, even when this request will be answered locally. Skipping it left
        // the object UNSENT, and a caller that sets a request header between open() and send() —
        // which the growth-chart form does — then hit "InvalidStateError: Failed to execute
        // 'setRequestHeader'" and abandoned the request before send() was ever reached. The shim
        // never ran, the chart plotted nothing, and the only trace was a console error.
        //
        // Opening costs nothing on its own: XMLHttpRequest issues no traffic until send(), and the
        // intercepted path below never calls it.
        var opened = nativeOpen.apply(this, arguments);
        if (body === null) {
            // Clear any marker from a previous use of this same object before delegating.
            delete this.__carlosLegacyResponse;
        } else {
            this.__carlosLegacyResponse = body;
        }
        return opened;
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
        // responseXML too, parsed from the same body. Left unstubbed it returned the real XHR's
        // null, so a form reading the history as a DOM plotted nothing while status.served still
        // counted the request as delivered — a success the shim itself would have reported.
        Object.defineProperty(this, "responseXML", readOnly(parseResponseXml(body)));
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
