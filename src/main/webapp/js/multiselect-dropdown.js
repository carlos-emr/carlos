/**
 * Vanilla JS multi-select dropdown component.
 * Replaces bootstrap-select for checkbox-style multi-select dropdowns.
 *
 * Usage: Add class="multiselect-dropdown" to a <select multiple> element.
 * Supports data-header, data-width, and data-subtext on <option> elements.
 *
 * API (via element.multiselectDropdown):
 *   .getValues()        - returns array of selected values
 *   .setValues(array)   - sets selected values programmatically
 *
 * Fires native 'change' event on the original <select> when selection changes.
 *
 * @since 2026-03-17
 */
(function () {
    'use strict';

    function initMultiselectDropdown(select) {
        if (select.multiselectDropdown) return;

        var header = select.getAttribute('data-header') || 'Select';
        var width = select.getAttribute('data-width') || 'auto';

        // Hide original select
        select.style.display = 'none';

        // Build wrapper
        var wrapper = document.createElement('div');
        wrapper.className = 'msd-wrapper';
        if (width !== 'auto') wrapper.style.width = width;
        select.parentNode.insertBefore(wrapper, select.nextSibling);

        // Toggle button
        var toggle = document.createElement('button');
        toggle.type = 'button';
        toggle.className = 'msd-toggle';
        toggle.textContent = header;
        wrapper.appendChild(toggle);

        // Dropdown panel
        var panel = document.createElement('div');
        panel.className = 'msd-panel';
        panel.style.display = 'none';

        // Header
        var headerEl = document.createElement('div');
        headerEl.className = 'msd-header';
        headerEl.textContent = header;
        panel.appendChild(headerEl);

        // Options
        var options = select.querySelectorAll('option');
        for (var i = 0; i < options.length; i++) {
            var opt = options[i];
            var item = document.createElement('label');
            item.className = 'msd-item';

            var cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.value = opt.value;
            cb.checked = opt.selected;

            var textSpan = document.createElement('span');
            textSpan.className = 'msd-label';
            textSpan.textContent = opt.textContent.trim();

            item.appendChild(cb);
            item.appendChild(textSpan);

            var subtext = opt.getAttribute('data-subtext');
            if (subtext) {
                var sub = document.createElement('span');
                sub.className = 'msd-subtext';
                sub.textContent = ' ' + subtext;
                item.appendChild(sub);
            }

            panel.appendChild(item);
        }

        wrapper.appendChild(panel);

        // Update toggle text and sync <select>
        function syncState() {
            var checked = panel.querySelectorAll('input[type=checkbox]:checked');
            var count = checked.length;
            if (count === 0) {
                toggle.textContent = header;
            } else if (count === 1) {
                toggle.textContent = checked[0].parentNode.querySelector('.msd-label').textContent;
            } else {
                toggle.textContent = count + ' selected';
            }

            // Sync back to original <select>
            for (var j = 0; j < options.length; j++) {
                var cb = panel.querySelectorAll('input[type=checkbox]')[j];
                options[j].selected = cb.checked;
            }

            // Fire native change event
            var evt;
            if (typeof Event === 'function') {
                evt = new Event('change', { bubbles: true });
            } else {
                evt = document.createEvent('Event');
                evt.initEvent('change', true, true);
            }
            select.dispatchEvent(evt);
        }

        // Checkbox change handler
        panel.addEventListener('change', function (e) {
            if (e.target.type === 'checkbox') {
                syncState();
            }
        });

        // Toggle open/close
        toggle.addEventListener('click', function (e) {
            e.stopPropagation();
            var isOpen = panel.style.display !== 'none';
            panel.style.display = isOpen ? 'none' : 'block';
        });

        // Close on outside click
        document.addEventListener('click', function (e) {
            if (!wrapper.contains(e.target)) {
                panel.style.display = 'none';
            }
        });

        // Public API
        select.multiselectDropdown = {
            getValues: function () {
                var vals = [];
                var cbs = panel.querySelectorAll('input[type=checkbox]:checked');
                for (var k = 0; k < cbs.length; k++) {
                    vals.push(cbs[k].value);
                }
                return vals;
            },
            setValues: function (vals) {
                var cbs = panel.querySelectorAll('input[type=checkbox]');
                for (var k = 0; k < cbs.length; k++) {
                    cbs[k].checked = vals.indexOf(cbs[k].value) !== -1;
                }
                syncState();
            }
        };

        // Initial sync
        syncState();
    }

    // Auto-init on DOM ready
    function initAll() {
        var selects = document.querySelectorAll('select.multiselect-dropdown');
        for (var i = 0; i < selects.length; i++) {
            initMultiselectDropdown(selects[i]);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll);
    } else {
        initAll();
    }
})();
