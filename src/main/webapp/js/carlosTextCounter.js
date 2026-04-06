/**
 * Minimal vanilla JS replacement for the legacy ylib.widget.TextCounter library.
 * Drop-in compatible — same constructor signature and DOM behavior.
 *
 * Creates a hidden counter span after the textarea that shows remaining characters
 * on focus, enforces character and line limits on keypress/keyup.
 *
 * @since 2026-04-06
 */

'use strict';

var ylib = ylib || {};
ylib.widget = ylib.widget || {};

/**
 * TextCounter — enforces character and line limits on a textarea with visual feedback.
 *
 * @param {string} elementId  - ID of the textarea element
 * @param {number} maxChars   - Maximum character count
 * @param {number} maxLines   - Maximum number of lines
 * @param {string} [textBefore='Chars left: '] - Prefix for counter display
 * @param {string} [textAfter='']              - Suffix for counter display
 */
ylib.widget.TextCounter = function (elementId, maxChars, maxLines, textBefore, textAfter) {
    var el = document.getElementById(elementId);
    if (!el) return;

    var prefix = (textBefore !== undefined) ? textBefore : 'Chars left: ';
    var suffix = (textAfter !== undefined) ? textAfter : '';

    var counter = document.createElement('span');
    counter.id = elementId + '-Counter';
    counter.className = 'TextCounter-Counter';
    counter.style.display = 'none';
    el.parentNode.insertBefore(counter, el.nextSibling);

    function enforce() {
        if (el.value.length > maxChars) {
            el.value = el.value.substring(0, maxChars);
        }
        var lines = el.value.split('\n');
        if (lines.length > maxLines) {
            el.value = lines.slice(0, maxLines).join('\n');
            if (el.value.length > maxChars) {
                el.value = el.value.substring(0, maxChars);
            }
        }
        counter.textContent = prefix + (maxChars - el.value.length) + suffix;
    }

    function onKey(e) {
        if (el.value.length >= maxChars && e.key && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
            e.preventDefault();
            return;
        }
        if (e.key === 'Enter' && el.value.split('\n').length >= maxLines) {
            e.preventDefault();
            return;
        }
    }

    el.addEventListener('keydown', onKey);
    el.addEventListener('keyup', enforce);
    el.addEventListener('change', enforce);
    el.addEventListener('input', enforce);
    el.addEventListener('focus', function () { counter.style.display = ''; enforce(); });
    el.addEventListener('blur', function () { counter.style.display = 'none'; });

    enforce();
};
