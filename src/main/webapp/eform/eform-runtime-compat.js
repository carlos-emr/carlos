/*
 * Runtime compatibility for stored eForms that use string timer callbacks.
 *
 * Modern CSP blocks native setTimeout("code", delay) and setInterval("code", delay). The browser
 * remains the JavaScript parser: stored source is never scanned or rewritten on the server.
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
        errorMessage: null
    };
    window.__carlosEformTimerCompat = status;

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
            executionError = event.error || new Error(event.message || "Timer callback failed");
        }
        script.textContent = String(source);
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
        if (executionError) {
            markFailure(executionError);
            throw executionError;
        }
    }

    function schedule(nativeTimer, receiver, handler, delay, callbackArguments) {
        if (typeof handler !== "string") {
            return nativeTimer.apply(receiver, [handler, delay].concat(callbackArguments));
        }
        return nativeTimer.call(receiver, function runStoredTimerSource() {
            executeStringCallback(handler);
        }, delay);
    }

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
        if (event.blockedURI === "inline"
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
