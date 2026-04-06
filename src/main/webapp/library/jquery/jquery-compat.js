/**
 * jQuery 3.x Compatibility Polyfill for CARLOS EMR
 *
 * Restores jQuery APIs removed in 1.9–3.x so that vendored third-party plugins
 * (DataTables, fileupload, validate, tablesorter, autocomplete, etc.)
 * continue to work with jQuery 3.7.1.
 *
 * Must be loaded immediately after jquery-3.7.1.min.js and before any plugins.
 *
 * Polyfills provided:
 *   $.browser        (removed 1.9)
 *   $.isArray()      (removed 3.3 — alias for Array.isArray)
 *   $.isFunction()   (removed 3.3)
 *   $.type()         (removed 3.3)
 *   $.isNumeric()    (removed 3.3)
 *   $.parseJSON()    (removed 3.0 — alias for JSON.parse)
 *   $.trim()         (deprecated 3.5)
 *   .andSelf()       (removed 3.0 — alias for .addBack)
 *   $.isWindow()     (removed 3.3)
 *   .live()          (removed 1.9 — warning stub; callers must migrate to .on)
 *   .die()           (removed 1.9 — warning stub; callers must migrate to .off)
 *   .delegate()      (removed 3.0 — delegates to .on)
 *   .undelegate()    (removed 3.0 — delegates to .off)
 *   .bind()          (deprecated 3.0 — delegates to .on)
 *   .unbind()        (deprecated 3.0 — delegates to .off)
 */
(function ($) {
    "use strict";

    if (!$) return;

    // --- $.browser (removed jQuery 1.9) ---
    if (!$.browser) {
        var ua = navigator.userAgent.toLowerCase();
        $.browser = {
            version: (ua.match(/.+(?:rv|it|ra|ie)[\/: ]([\d.]+)/) || [0, '0'])[1],
            webkit: /webkit/.test(ua),
            opera: /opera/.test(ua),
            msie: /msie/.test(ua) || /trident/.test(ua),
            mozilla: /mozilla/.test(ua) && !/(compatible|webkit)/.test(ua),
            safari: /safari/.test(ua) && !/chrome/.test(ua)
        };
    }

    // --- $.isArray (removed jQuery 3.3) ---
    if (!$.isArray) {
        $.isArray = Array.isArray;
    }

    // --- $.isFunction (removed jQuery 3.3) ---
    if (!$.isFunction) {
        $.isFunction = function (obj) {
            return typeof obj === "function";
        };
    }

    // --- $.type (removed jQuery 3.3) ---
    if (!$.type) {
        var class2type = {};
        "Boolean Number String Function Array Date RegExp Object Error Symbol".split(" ").forEach(function (name) {
            class2type["[object " + name + "]"] = name.toLowerCase();
        });

        $.type = function (obj) {
            if (obj == null) {
                return obj + "";
            }
            return typeof obj === "object" || typeof obj === "function" ?
                class2type[Object.prototype.toString.call(obj)] || "object" :
                typeof obj;
        };
    }

    // --- $.isNumeric (removed jQuery 3.3) ---
    if (!$.isNumeric) {
        $.isNumeric = function (obj) {
            var type = typeof obj;
            return (type === "number" || type === "string") &&
                !isNaN(obj - parseFloat(obj));
        };
    }

    // --- $.parseJSON (removed jQuery 3.0) ---
    if (!$.parseJSON) {
        $.parseJSON = JSON.parse;
    }

    // --- $.trim (deprecated jQuery 3.5) ---
    if (!$.trim) {
        $.trim = function (text) {
            return text == null ? "" : (text + "").replace(/^[\s\uFEFF\xA0]+|[\s\uFEFF\xA0]+$/g, "");
        };
    }

    // --- .andSelf (removed jQuery 3.0) ---
    if (!$.fn.andSelf) {
        $.fn.andSelf = $.fn.addBack;
    }

    // --- .live / .die (removed jQuery 1.9) ---
    // Cannot be reliably polyfilled in jQuery 3.x because this.selector and
    // this.context were also removed. All application code callers have been
    // migrated to .on()/.off(). These stubs prevent "not a function" errors
    // and log a warning to aid debugging if any missed callers surface.
    if (!$.fn.live) {
        $.fn.live = function () {
            if (typeof console !== "undefined" && console.warn) {
                console.warn("jQuery .live() is removed. Migrate to .on(). Called with:", arguments);
            }
            return this;
        };
    }
    if (!$.fn.die) {
        $.fn.die = function () {
            if (typeof console !== "undefined" && console.warn) {
                console.warn("jQuery .die() is removed. Migrate to .off(). Called with:", arguments);
            }
            return this;
        };
    }

    // --- .delegate / .undelegate (deprecated jQuery 3.0) ---
    if (!$.fn.delegate) {
        $.fn.delegate = function (selector, types, data, fn) {
            return this.on(types, selector, data, fn);
        };
    }
    if (!$.fn.undelegate) {
        $.fn.undelegate = function (selector, types, fn) {
            return arguments.length === 1 ?
                this.off(selector, "**") :
                this.off(types, selector || "**", fn);
        };
    }

    // --- $.isWindow (removed jQuery 3.3) ---
    if (!$.isWindow) {
        $.isWindow = function (obj) {
            return obj != null && obj === obj.window;
        };
    }

    // --- .bind / .unbind (deprecated jQuery 3.0) ---
    if (!$.fn.bind) {
        $.fn.bind = function (types, data, fn) {
            return this.on(types, null, data, fn);
        };
    }
    if (!$.fn.unbind) {
        $.fn.unbind = function (types, fn) {
            return this.off(types, null, fn);
        };
    }

})(window.jQuery);
