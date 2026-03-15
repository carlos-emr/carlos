/*
 * CARLOS EMR - Prototype.js Compatibility Shim
 *
 * Provides drop-in replacements for the most common Prototype.js APIs using
 * vanilla JS. This shim is ONLY for the encounter/case management module
 * (Phase 4) where 42KB+ of JS makes full rewrite impractical in one pass.
 * All other modules (Phases 1-3) are fully rewritten to vanilla JS directly.
 *
 * This shim lets us swap prototype.js -> prototype-compat.js in the encounter
 * module without changing the calling code, then progressively remove shim
 * usage in later cleanup passes.
 *
 * @since 2026-03-15
 */

/* global carlosExtractAndExecScripts */

// ---- $() — Element lookup by ID ----
// Only define if Prototype's $ is not loaded
if (typeof window.$ === 'undefined' && !window.Prototype) {
    // Multi-argument form: $(id1, id2, ...) returns Array of elements
    // Single-argument form: $(id) returns single element
    window.$ = function () {
        if (arguments.length > 1) {
            return Array.from(arguments).map(function (id) {
                return typeof id === 'string' ? document.getElementById(id) : id;
            });
        }
        var arg = arguments[0];
        return typeof arg === 'string' ? document.getElementById(arg) : arg;
    };
}

// ---- $F() — Form field value access ----
// Must handle checkbox/radio: return null when unchecked (Prototype dispatches
// through Form.Element.Serializers.inputSelector, lines 2873-2874)
window.$F = function (id) {
    var el = typeof id === 'string' ? document.getElementById(id) : id;
    if (!el) return '';
    if ((el.type === 'checkbox' || el.type === 'radio') && !el.checked) return null;
    return el.value;
};

// ---- $A() — Array.from() ----
window.$A = function (iterable) {
    return Array.from(iterable);
};

// ---- $$() — CSS selector, returns Array (not NodeList) ----
window.$$ = function (selector) {
    return Array.from(document.querySelectorAll(selector));
};

// ---- Array.prototype.invoke() — Batch method invocation ----
if (!Array.prototype.invoke) {
    Array.prototype.invoke = function (method) {
        var args = Array.prototype.slice.call(arguments, 1);
        return this.map(function (el) {
            if (el && typeof el[method] === 'function') {
                return el[method].apply(el, args);
            }
            return undefined;
        });
    };
}

// ---- Array.prototype.each() — Prototype Enumerable ----
if (!Array.prototype.each) {
    Array.prototype.each = function (fn) {
        this.forEach(fn);
        return this;
    };
}

// ---- String.prototype.strip() — equivalent to .trim() ----
if (!String.prototype.strip) {
    String.prototype.strip = function () {
        return this.trim();
    };
}

// ---- String.prototype.evalJSON() — JSON parsing ----
if (!String.prototype.evalJSON) {
    String.prototype.evalJSON = function () {
        return JSON.parse(this);
    };
}

// ---- HTMLElement.prototype extensions ----

// .update(html) — sets innerHTML and executes inline scripts
// IMPORTANT: Prototype's .update() EXECUTES inline scripts via evalScripts().
// Plain innerHTML does NOT execute scripts. The shim strips script tags,
// sets innerHTML, then creates dynamic script DOM elements to execute them.
if (!HTMLElement.prototype.update) {
    HTMLElement.prototype.update = function (html) {
        html = (html == null) ? '' : String(html);
        carlosExtractAndExecScripts(this, html);
        return this;
    };
}

// .hide() / .show() / .toggle()
if (!HTMLElement.prototype.hide) {
    HTMLElement.prototype.hide = function () {
        this.style.display = 'none';
        return this;
    };
}
if (!HTMLElement.prototype.show) {
    HTMLElement.prototype.show = function () {
        this.style.display = '';
        return this;
    };
}
// Only add .toggle if not already a native method (it is on dialog elements)
if (!HTMLElement.prototype.toggle) {
    HTMLElement.prototype.toggle = function () {
        this.style.display = (this.style.display === 'none') ? '' : 'none';
        return this;
    };
}

// .insert({bottom: html}) or .insert(html) — content insertion
if (!HTMLElement.prototype.insert) {
    HTMLElement.prototype.insert = function (content) {
        if (typeof content === 'string') {
            this.insertAdjacentHTML('beforeend', content);
        } else if (content && typeof content === 'object') {
            if (content.top) this.insertAdjacentHTML('afterbegin', content.top);
            if (content.bottom) this.insertAdjacentHTML('beforeend', content.bottom);
            if (content.before) this.insertAdjacentHTML('beforebegin', content.before);
            if (content.after) this.insertAdjacentHTML('afterend', content.after);
        }
        return this;
    };
}

// .setStyle({prop: val}) or .setStyle('prop', 'val') — inline style manipulation
if (!HTMLElement.prototype.setStyle) {
    HTMLElement.prototype.setStyle = function (stylesOrProp, value) {
        if (typeof stylesOrProp === 'string') {
            this.style[stylesOrProp] = value;
        } else {
            for (var prop in stylesOrProp) {
                if (stylesOrProp.hasOwnProperty(prop)) {
                    this.style[prop] = stylesOrProp[prop];
                }
            }
        }
        return this;
    };
}

// .getStyle(prop) — computed style access
if (!HTMLElement.prototype.getStyle) {
    HTMLElement.prototype.getStyle = function (prop) {
        return this.style[prop] || getComputedStyle(this)[prop];
    };
}

// .getHeight() / .getWidth()
if (!HTMLElement.prototype.getHeight) {
    HTMLElement.prototype.getHeight = function () {
        return this.offsetHeight;
    };
}
if (!HTMLElement.prototype.getWidth) {
    HTMLElement.prototype.getWidth = function () {
        return this.offsetWidth;
    };
}

// .up(selector, index) — find ancestor matching selector
// NOTE: closest() is WRONG for .up() — it includes the element itself and
// cannot handle the level/index parameter. Must iterate parentElements.
if (!HTMLElement.prototype.up) {
    HTMLElement.prototype.up = function (selector, index) {
        if (!selector) return this.parentElement;
        var el = this.parentElement, count = 0;
        while (el) {
            if (el.matches && el.matches(selector)) {
                if (count === (index || 0)) return el;
                count++;
            }
            el = el.parentElement;
        }
        return null;
    };
}

// .down(selector) — find first descendant matching selector
if (!HTMLElement.prototype.down) {
    HTMLElement.prototype.down = function (selector) {
        return selector ? this.querySelector(selector) : this.firstElementChild;
    };
}

// .addClassName() / .removeClassName() / .hasClassName()
if (!HTMLElement.prototype.addClassName) {
    HTMLElement.prototype.addClassName = function (cls) {
        this.classList.add(cls);
        return this;
    };
}
if (!HTMLElement.prototype.removeClassName) {
    HTMLElement.prototype.removeClassName = function (cls) {
        this.classList.remove(cls);
        return this;
    };
}
if (!HTMLElement.prototype.hasClassName) {
    HTMLElement.prototype.hasClassName = function (cls) {
        return this.classList.contains(cls);
    };
}

// .observe() / .stopObserving() — event handling
if (!HTMLElement.prototype.observe) {
    HTMLElement.prototype.observe = function (eventName, handler) {
        this.addEventListener(eventName, handler);
        return this;
    };
}
if (!HTMLElement.prototype.stopObserving) {
    HTMLElement.prototype.stopObserving = function (eventName, handler) {
        this.removeEventListener(eventName, handler);
        return this;
    };
}

// ---- Element static methods (standalone function forms) ----
window.Element = window.Element || {};

if (!Element.observe) {
    Element.observe = function (el, eventName, handler) {
        if (typeof el === 'string') el = document.getElementById(el);
        if (el) el.addEventListener(eventName, handler);
    };
}
if (!Element.stopObserving) {
    Element.stopObserving = function (el, eventName, handler) {
        if (typeof el === 'string') el = document.getElementById(el);
        if (el) el.removeEventListener(eventName, handler);
    };
}
if (!Element.toggle) {
    Element.toggle = function (el) {
        if (typeof el === 'string') el = document.getElementById(el);
        if (el) el.style.display = (el.style.display === 'none') ? '' : 'none';
    };
}
if (!Element.remove) {
    Element.remove = function (el) {
        if (typeof el === 'string') el = document.getElementById(el);
        if (el && el.parentNode) el.parentNode.removeChild(el);
    };
}

// ---- getElementsBySelector (Prototype's querySelectorAll wrapper) ----
if (!HTMLElement.prototype.getElementsBySelector) {
    HTMLElement.prototype.getElementsBySelector = function (selector) {
        return Array.from(this.querySelectorAll(selector));
    };
}

// ---- Function.prototype.bindAsEventListener ----
// CRITICAL: Prototype's bindAsEventListener prepends the EVENT as the FIRST
// argument, then appends bound args. .bind() does the OPPOSITE.
if (!Function.prototype.bindAsEventListener) {
    Function.prototype.bindAsEventListener = function (context) {
        var fn = this, args = Array.prototype.slice.call(arguments, 1);
        return function (event) {
            return fn.apply(context, [event || window.event].concat(args));
        };
    };
}

// ---- Event helpers ----
if (!window.Event) window.Event = {};
Event.stop = function (e) {
    if (e) {
        if (e.preventDefault) e.preventDefault();
        if (e.stopPropagation) e.stopPropagation();
    }
};
Event.element = function (e) {
    return e.target;
};
Event.findElement = function (e, selector) {
    return e.target.closest(selector);
};
Event.pointerX = function (e) { return e.clientX; };
Event.pointerY = function (e) { return e.clientY; };

// Event.observe / Event.stopObserving — static equivalents of Element.observe
Event.observe = function (el, eventName, handler) {
    if (typeof el === 'string') el = document.getElementById(el);
    if (el) el.addEventListener(eventName, handler);
};
Event.stopObserving = function (el, eventName, handler) {
    if (typeof el === 'string') el = document.getElementById(el);
    if (el) el.removeEventListener(eventName, handler);
};

// Event key constants
Event.KEY_TAB = 9;
Event.KEY_RETURN = 13;
Event.KEY_ESC = 27;
Event.KEY_LEFT = 37;
Event.KEY_UP = 38;
Event.KEY_RIGHT = 39;
Event.KEY_DOWN = 40;
Event.KEY_DELETE = 46;
Event.KEY_HOME = 36;
Event.KEY_END = 35;
Event.KEY_PAGEUP = 33;
Event.KEY_PAGEDOWN = 34;

// ---- Form.serialize() ----
window.Form = window.Form || {};
Form.serialize = function (formOrId) {
    var form = typeof formOrId === 'string' ? document.getElementById(formOrId) : formOrId;
    return new URLSearchParams(new FormData(form)).toString();
};

// HTMLFormElement.prototype.serialize — instance method form
if (!HTMLFormElement.prototype.serialize) {
    HTMLFormElement.prototype.serialize = function () {
        return new URLSearchParams(new FormData(this)).toString();
    };
}

// ---- Position helpers ----
window.Position = window.Position || {};
Position.page = function (el) {
    if (typeof el === 'string') el = document.getElementById(el);
    var rect = el.getBoundingClientRect();
    return [rect.left + window.scrollX, rect.top + window.scrollY];
};
Position.positionedOffset = function (el) {
    if (typeof el === 'string') el = document.getElementById(el);
    return [el.offsetLeft, el.offsetTop];
};

// ---- Insertion helpers (legacy compatibility) ----
window.Insertion = window.Insertion || {};
Insertion.Bottom = function (el, html) {
    if (typeof el === 'string') el = document.getElementById(el);
    el.insertAdjacentHTML('beforeend', html);
};
Insertion.Top = function (el, html) {
    if (typeof el === 'string') el = document.getElementById(el);
    el.insertAdjacentHTML('afterbegin', html);
};
Insertion.After = function (el, html) {
    if (typeof el === 'string') el = document.getElementById(el);
    el.insertAdjacentHTML('afterend', html);
};
Insertion.Before = function (el, html) {
    if (typeof el === 'string') el = document.getElementById(el);
    el.insertAdjacentHTML('beforebegin', html);
};

// ---- Prototype.Browser stubs ----
window.Prototype = window.Prototype || {};
Prototype.Browser = {
    IE: false,
    Opera: false,
    WebKit: true,
    Gecko: false,
    MobileSafari: false
};

// ---- carlosExtractAndExecScripts utility ----
// Extracts script tags from HTML, sets innerHTML with non-script content,
// then creates dynamic script DOM elements to run them.
// Used by .update() and CarlosAjax.updater().
window.carlosExtractAndExecScripts = function (element, html) {
    var scriptPattern = /<script[\s\S]*?>([\s\S]*?)<\/script>/gi;
    var scripts = [];
    var match;

    while ((match = scriptPattern.exec(html)) !== null) {
        scripts.push(match[1]);
    }

    // Remove script tags from HTML and set as innerHTML
    element.innerHTML = html.replace(scriptPattern, '');

    // Run extracted scripts by creating dynamic script elements
    scripts.forEach(function (scriptContent) {
        if (scriptContent.trim()) {
            var script = document.createElement('script');
            script.textContent = scriptContent;
            document.head.appendChild(script);
            document.head.removeChild(script);
        }
    });
};
