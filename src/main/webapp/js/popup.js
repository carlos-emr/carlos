/*
 * Lightweight popup utility for CARLOS EMR.
 * Drop-in replacement for nhpup_1.1.js — same API, no jQuery dependency.
 *
 * Usage:
 *   <span onmouseover="nhpup.popup('content')">hover me</span>
 *   <span onmouseover="nhpup.popup(htmlVar, {'width': 350})">hover me</span>
 *
 * Security note: popup() sets innerHTML because callers pass pre-built HTML
 * table markup from same-origin JavaScript variables (e.g., phone/address
 * history tables, healthcare team detail tables). The content originates from
 * server-side OWASP-encoded data, not direct user input.
 */
var nhpup = (function () {
    "use strict";

    var pup = null;
    var minMargin = 15;
    var defaultWidth = 200;

    function ensureElement() {
        if (pup) return;
        pup = document.createElement("div");
        pup.id = "pup";
        pup.className = "pup";
        pup.style.cssText = "position:absolute;display:none;z-index:200;";
        document.body.appendChild(pup);
    }

    function nudge(x, y) {
        var winW = window.innerWidth + window.scrollX;
        var winH = window.innerHeight + window.scrollY;

        if (x + pup.offsetWidth + minMargin > winW) {
            x -= pup.offsetWidth + 2 * minMargin;
        }
        if (x < 0) x = 0;

        if (y + pup.offsetHeight > winH) {
            y -= pup.offsetHeight + minMargin;
        }
        return [x, y];
    }

    function onMouseMove(e) {
        if (!pup || pup.style.display === "none") return;
        var pos = nudge(e.pageX + 10, e.pageY);
        pup.style.left = pos[0] + "px";
        pup.style.top = pos[1] + "px";
    }

    document.addEventListener("DOMContentLoaded", function () {
        ensureElement();
        document.addEventListener("mousemove", onMouseMove);
    });

    return {
        popup: function (msg, config) {
            ensureElement();
            pup.className = "pup";
            pup.style.width = defaultWidth + "px";

            if (config) {
                if (config["class"]) pup.className += " " + config["class"];
                if (config.width) pup.style.width = config.width + "px";
            }

            // innerHTML is intentional — callers pass pre-built HTML tables
            // from same-origin server-rendered data (see security note above)
            pup.innerHTML = msg;  // nosemgrep: javascript.browser.security.insecure-document-method.insecure-document-method
            pup.style.display = "block";

            var evt = arguments.callee.caller && arguments.callee.caller.arguments
                ? arguments.callee.caller.arguments[0]
                : null;
            if (evt && evt.target) {
                var target = evt.target;
                var hideHandler = function () {
                    pup.style.display = "none";
                    target.removeEventListener("mouseout", hideHandler);
                };
                target.addEventListener("mouseout", hideHandler);
            }
        }
    };
})();
