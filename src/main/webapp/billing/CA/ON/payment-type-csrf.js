/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/**
 * Shared CSRF and request-lifecycle helpers for the Ontario billing payment-type
 * pages (create, edit, delete). CSRFGuard's client script already adds the
 * CSRF-TOKEN header to hijacked XHRs, so these callers must send the token in the
 * form body ONLY: setting the header a second time produced a duplicated value
 * and a 403 on every save (issue #3786).
 *
 * @since 2026-09-19
 */

/**
 * @returns {Promise<void>} resolves once the page's CSRF bootstrap has settled,
 *                          or immediately when it already has; rejects if the
 *                          bootstrap itself failed
 */
async function paymentTypeCsrfTokenReady() {
    // csrf-token.jspf leaves window.csrfTokenReady null until DOMContentLoaded, so a
    // click on a button the parser has already rendered can arrive before the bootstrap
    // promise exists. Waiting for the event first keeps that click from reading the
    // still-empty hidden input and reporting a bootstrap failure that has not happened.
    // The continuation resumes as a microtask after the whole DOMContentLoaded dispatch,
    // so the fragment's own listener has installed the promise by then whichever order
    // the two listeners were registered in.
    if (!window.csrfTokenReady && document.readyState === "loading") {
        await new Promise(function (resolve) {
            document.addEventListener("DOMContentLoaded", resolve, {once: true});
        });
    }
    if (window.csrfTokenReady) await window.csrfTokenReady;
}

/** @returns {?string} the bootstrapped token, or null while the input is unpopulated. */
function paymentTypeTokenValue() {
    const input = document.querySelector("input[name='CSRF-TOKEN']");
    return input && input.value ? input.value : null;
}

/*
 * Where this script was served from, captured at load time. A plain .js file cannot
 * read request.getContextPath(), but both payment-type pages load this file from
 * <contextPath>/billing/CA/ON/payment-type-csrf.js, so trimming that suffix off its
 * own URL yields the prefix fetchCsrfToken needs. document.currentScript is only set
 * while the script is first executing, hence the capture here rather than at use.
 */
const PAYMENT_TYPE_SCRIPT_PATH = "/billing/CA/ON/payment-type-csrf.js";
const paymentTypeContextPath = (function () {
    const script = typeof document !== "undefined" && document.currentScript;
    const source = script && script.src ? String(script.src).split(/[?#]/)[0] : "";
    return source.endsWith(PAYMENT_TYPE_SCRIPT_PATH)
        ? source.slice(0, -PAYMENT_TYPE_SCRIPT_PATH.length)
        : null;
})();

/**
 * Refetches the token after the page bootstrap failed to produce one.
 *
 * A rejected window.csrfTokenReady stays rejected for the life of the document, so
 * awaiting it again on the next click can only re-throw — without this the first
 * transient bootstrap failure would brick every later save until a reload. This
 * mirrors the retry in efmformmanager.jsp.
 *
 * @returns {Promise<?string>} the token, or null when no retry is possible or it failed
 */
async function paymentTypeRefetchToken() {
    if (paymentTypeContextPath === null || typeof fetchCsrfToken !== "function") return null;
    try {
        await fetchCsrfToken(paymentTypeContextPath);
    } catch (error) {
        return null;
    }
    return paymentTypeTokenValue();
}

/**
 * Reads the bootstrapped CSRF token, waiting for the bootstrap first and refetching
 * once if it left no token behind.
 *
 * @returns {Promise<?string>} the token, or null after alerting the user when it
 *                             cannot be obtained — callers must not submit then
 */
async function paymentTypeCsrfToken() {
    try {
        await paymentTypeCsrfTokenReady();
    } catch (error) {
        // A failed bootstrap is recoverable; the refetch below decides.
    }
    const token = paymentTypeTokenValue() || await paymentTypeRefetchToken();
    if (token) return token;
    alert("Security token unavailable. Reload and try again.");
    return null;
}

let paymentTypeRequestPending = false;

/**
 * Claims the single in-flight slot and resolves the token for one mutation. A
 * repeated activation while a request is pending returns null so the page cannot
 * POST twice; a token failure releases the slot again so the user can retry.
 *
 * @returns {Promise<?string>} the token, or null when a request is already
 *                             pending or the token is unavailable
 */
async function paymentTypeBeginRequest() {
    if (paymentTypeRequestPending) return null;
    paymentTypeRequestPending = true;
    const token = await paymentTypeCsrfToken();
    if (!token) paymentTypeRequestPending = false;
    return token;
}
/** Releases the in-flight slot; wired to jQuery's ajax complete callback. */
function paymentTypeRequestComplete() {
    paymentTypeRequestPending = false;
}

/**
 * Handles a payment-type save response. Navigation happens only on an explicit
 * ret of 0, so an error body can never be mistaken for a successful save.
 *
 * @param {?Object} result - the parsed JSON response, or null
 */
function paymentTypeSaveResult(result) {
    if (result && (result.ret === "0" || result.ret === 0)) {
        alert("Success");
        history.back();
    } else {
        alert(result && result.reason ? String(result.reason) : "Payment type was not saved.");
    }
}

/**
 * Reports a transport-level failure in readable text. The server's own reason is
 * preferred, then jQuery's error detail, then the HTTP status; the legacy handler
 * stringified the response object and showed the user "[object JSON]".
 *
 * @param {?Object} request - the jqXHR, when jQuery supplied one
 * @param {string} status - jQuery's textStatus (e.g. "timeout", "error")
 * @param {*} error - jQuery's errorThrown
 */
function paymentTypeRequestFailed(request, status, error) {
    const reason = request && request.responseJSON && request.responseJSON.reason;
    const httpStatus = request && request.status ? "HTTP " + request.status : null;
    alert(String(reason || error || httpStatus || status || "Unknown error happened!"));
}
