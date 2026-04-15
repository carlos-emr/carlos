/*
 * Lightweight popup utility for CARLOS EMR.
 * Drop-in replacement for nhpup_1.1.js — same API, no jQuery dependency.
 *
 * Usage:
 *   <span onmouseover="nhpup.popup('content')">hover me</span>
 *   <span onmouseover="nhpup.popup(htmlVar, {'width': 350})">hover me</span>
 *   <span onmouseover="nhpup.popup('content'); nhpup.attachHideHandler(event);">hover me</span>
 *
 * Security note: popup() sanitizes caller-provided markup before rendering.
 * Scriptable tags and event-handler attributes are removed to prevent XSS.
 */
var nhpup = (function () {
    "use strict";

    var pup = null;
    var minMargin = 15;
    var defaultWidth = 200;
    var blockedTags = new Set(["SCRIPT", "STYLE", "IFRAME", "OBJECT", "EMBED", "LINK", "META", "SVG", "MATH", "BASE"]);

    function sanitizeFragment(markup) {
        var parser = new DOMParser();
        var parsed = parser.parseFromString(String(markup == null ? "" : markup), "text/html");
        var fragment = document.createDocumentFragment();
        while (parsed.body.firstChild) {
            fragment.appendChild(parsed.body.firstChild);
        }

        var walker = document.createTreeWalker(fragment, NodeFilter.SHOW_ELEMENT);
        var nodes = [];
        while (walker.nextNode()) {
            nodes.push(walker.currentNode);
        }

        nodes.forEach(function (el) {
            if (blockedTags.has(el.tagName)) {
                el.remove();
                return;
            }

            Array.from(el.attributes).forEach(function (attr) {
                var name = attr.name.toLowerCase();
                var value = attr.value || "";
                var normalized = value.replace(/[\u0000-\u001f\u007f\s]+/g, "").toLowerCase();
                if (name.startsWith("on") || name === "srcdoc" || normalized.startsWith("javascript:")) {
                    el.removeAttribute(attr.name);
                }
            });
        });

        return fragment;
    }

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

            // Render sanitized HTML to prevent script/event-handler injection.
            pup.replaceChildren(sanitizeFragment(msg));
            pup.style.display = "block";

        },

        // Attaches a mouseout hide handler to the hovered element.
        // Call from inline onmouseover handlers that need auto-hide:
        //   onmouseover="nhpup.popup('content'); nhpup.attachHideHandler(event);"
        attachHideHandler: function (evt) {
            if (!evt || !evt.target) return;
            var target = evt.target;
            var hideHandler = function () {
                if (pup) pup.style.display = "none";
                target.removeEventListener("mouseout", hideHandler);
            };
            target.addEventListener("mouseout", hideHandler);
        }
    };
})();
