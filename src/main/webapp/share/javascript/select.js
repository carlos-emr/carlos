// Copyright (c) 2006 - 2007 Gabriel Lanzani (http://www.glanzani.com.ar)
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
// Rewritten as vanilla JS for CARLOS EMR (2026) — replaces Prototype/Scriptaculous dependency.
// VERSION 1.0

/**
 * CarlosAutocomplete — vanilla JS autocomplete dropdown over a <select> element.
 *
 * Usage:
 *   new CarlosAutocomplete.SelectBox('mySelectId', { redirect: false, debug: false, autoSubmit: '' });
 *
 * Also exposed as Autocompleter.SelectBox for backward compatibility with existing JSP code.
 */
var CarlosAutocomplete = (function () {
    'use strict';

    /**
     * SelectBox constructor — wraps a <select> element with an autocomplete text input.
     *
     * @param {string} selectId - The ID of the <select> element to wrap
     * @param {Object} [options] - Configuration options
     * @param {boolean} [options.redirect=false] - Redirect to the selected option value
     * @param {boolean} [options.debug=false] - Show debug alerts
     * @param {string}  [options.autoSubmit=''] - Form ID to auto-submit on selection
     * @param {Function} [options.afterUpdateElement] - Callback after selection: fn(textInput, selectedLi)
     */
    function SelectBox(selectId, options) {
        var self = this;

        this.options = {
            redirect: false,
            debug: false,
            autoSubmit: '',
            afterUpdateElement: null
        };
        if (options) {
            for (var k in options) {
                if (options.hasOwnProperty(k)) {
                    this.options[k] = options[k];
                }
            }
        }

        var selectElement = document.getElementById(selectId);
        if (!selectElement) {
            console.error('CarlosAutocomplete.SelectBox: select element not found: ' + selectId);
            return;
        }

        // Create text input
        this.input = document.createElement('input');
        this.input.type = 'text';
        this.input.id = selectId + '_combo';
        this.input.setAttribute('autocomplete', 'off');

        // Copy CSS classes from select to input
        selectElement.classList.forEach(function (cls) {
            self.input.classList.add(cls);
        });

        selectElement.parentNode.insertBefore(this.input, selectElement);

        // Create dropdown container
        this.dropdown = document.createElement('div');
        this.dropdown.id = selectId + '_options';
        this.dropdown.className = 'autocomplete';
        this.dropdown.style.display = 'none';
        this.dropdown.style.position = 'absolute';
        this.dropdown.style.zIndex = '1000';
        this.dropdown.style.maxHeight = '200px';
        this.dropdown.style.overflowY = 'auto';
        this.dropdown.style.backgroundColor = '#fff';
        this.dropdown.style.border = '1px solid #ccc';
        selectElement.parentNode.insertBefore(this.dropdown, selectElement);

        // Hide the original select
        if (!this.options.debug) {
            selectElement.style.display = 'none';
        }

        this.selectElement = selectElement;
        this.selectId = selectId;
        this.allItems = [];
        this.filteredItems = [];
        this.activeIndex = -1;
        this.isOpen = false;

        // Collect options from the select element
        var optionList = selectElement.getElementsByTagName('option');
        for (var i = 0; i < optionList.length; i++) {
            var opt = optionList[i];
            this.allItems.push({
                text: opt.textContent || opt.innerText || '',
                value: opt.value || '',
                index: i
            });
            if (opt.getAttribute('selected') !== null) {
                this.input.value = opt.textContent || opt.innerText || '';
            }

            if (this.options.debug) {
                alert('option ' + (opt.textContent || opt.innerText || '') + ' added');
            }
        }

        // Set initial value from selected index
        if (selectElement.selectedIndex >= 0) {
            var selectedOption = selectElement.options[selectElement.selectedIndex];
            this.input.value = selectedOption.textContent || selectedOption.innerText || '';
        }

        if (this.options.debug) {
            alert('input ' + this.input.id + ' and div ' + this.dropdown.id + ' created');
        }

        // Set up the default afterUpdateElement callback for select synchronization
        var userAfterUpdate = this.options.afterUpdateElement;
        this.options.afterUpdateElement = function (text, li) {
            // Sync the original select element and fire change event
            var opts = selectElement.getElementsByTagName('option');
            for (var j = 0; j < opts.length; j++) {
                if (opts[j].value === li.dataset.value) {
                    selectElement.selectedIndex = j;
                    selectElement.dispatchEvent(new Event('change', { bubbles: true }));
                    break;
                }
            }
            if (self.options.redirect) {
                document.location.href = li.dataset.value;
            }
            if (self.options.autoSubmit) {
                var autoSubmitElement = document.getElementById(self.options.autoSubmit);
                if (autoSubmitElement) {
                    autoSubmitElement.submit();
                }
            }
            // Call user-provided callback if any
            if (userAfterUpdate) {
                userAfterUpdate(text, li);
            }
        };

        // Event listeners
        this.input.addEventListener('click', function () { self.activate(); });
        this.input.addEventListener('keyup', function (e) { self.onKeyUp(e); });
        this.input.addEventListener('keydown', function (e) { self.onKeyDown(e); });

        // Close dropdown when clicking outside (store reference for cleanup)
        this._documentClickHandler = function (e) {
            if (e.target !== self.input && !self.dropdown.contains(e.target)) {
                self.close();
            }
        };
        document.addEventListener('click', this._documentClickHandler);
    }

    /**
     * Clean up event listeners. Call when the autocomplete is no longer needed.
     */
    SelectBox.prototype.destroy = function () {
        if (this._documentClickHandler) {
            document.removeEventListener('click', this._documentClickHandler);
            this._documentClickHandler = null;
        }
    };

    SelectBox.prototype.activate = function () {
        this.input.removeAttribute('readonly');
        this.input.readOnly = false;
        this.filterAndShow('');
    };

    SelectBox.prototype.filterAndShow = function (query) {
        var self = this;
        var lowerQuery = query.toLowerCase();

        this.filteredItems = this.allItems.filter(function (item) {
            return lowerQuery === '' || item.text.toLowerCase().indexOf(lowerQuery) !== -1;
        });

        // Build dropdown using safe DOM methods (no innerHTML)
        while (this.dropdown.firstChild) {
            this.dropdown.removeChild(this.dropdown.firstChild);
        }
        var ul = document.createElement('ul');
        ul.style.listStyle = 'none';
        ul.style.margin = '0';
        ul.style.padding = '0';

        this.filteredItems.forEach(function (item, idx) {
            var li = document.createElement('li');
            li.textContent = item.text;
            li.dataset.value = item.value;
            li.dataset.index = idx;
            li.style.padding = '4px 8px';
            li.style.cursor = 'pointer';

            li.addEventListener('mouseenter', function () {
                self.highlightItem(idx);
            });
            li.addEventListener('click', function (e) {
                e.stopPropagation();
                self.selectItem(idx);
            });

            ul.appendChild(li);
        });

        this.dropdown.appendChild(ul);

        if (this.filteredItems.length === 0) {
            this.dropdown.style.display = 'none';
            this.isOpen = false;
            return;
        }

        // Position dropdown below the input
        var rect = this.input.getBoundingClientRect();
        var scrollTop = window.pageYOffset || document.documentElement.scrollTop;
        var scrollLeft = window.pageXOffset || document.documentElement.scrollLeft;
        this.dropdown.style.top = (rect.bottom + scrollTop) + 'px';
        this.dropdown.style.left = (rect.left + scrollLeft) + 'px';
        this.dropdown.style.width = rect.width + 'px';

        this.dropdown.style.display = 'block';
        this.isOpen = true;
        this.activeIndex = -1;
    };

    SelectBox.prototype.highlightItem = function (idx) {
        var items = this.dropdown.querySelectorAll('li');
        items.forEach(function (li, i) {
            li.style.backgroundColor = (i === idx) ? '#316ac5' : '';
            li.style.color = (i === idx) ? '#fff' : '';
        });
        this.activeIndex = idx;
    };

    SelectBox.prototype.selectItem = function (idx) {
        if (idx < 0 || idx >= this.filteredItems.length) return;

        var item = this.filteredItems[idx];
        this.input.value = item.text;
        this.close();

        // Fire afterUpdateElement callback
        if (this.options.afterUpdateElement) {
            // Create a li-like object with id and dataset for backward compat
            var fakeLi = { id: item.value, dataset: { value: item.value } };
            this.options.afterUpdateElement(this.input, fakeLi);
        }
    };

    SelectBox.prototype.close = function () {
        this.dropdown.style.display = 'none';
        this.isOpen = false;
        this.activeIndex = -1;
    };

    SelectBox.prototype.onKeyUp = function (e) {
        var key = e.keyCode || e.which;
        // Ignore navigation keys on keyup
        if (key === 38 || key === 40 || key === 13 || key === 27) return;

        this.filterAndShow(this.input.value);
    };

    SelectBox.prototype.onKeyDown = function (e) {
        if (!this.isOpen) return;

        var key = e.keyCode || e.which;
        switch (key) {
            case 40: // Down
                e.preventDefault();
                if (this.activeIndex < this.filteredItems.length - 1) {
                    this.highlightItem(this.activeIndex + 1);
                    this.scrollToActive();
                }
                break;
            case 38: // Up
                e.preventDefault();
                if (this.activeIndex > 0) {
                    this.highlightItem(this.activeIndex - 1);
                    this.scrollToActive();
                }
                break;
            case 13: // Enter
                e.preventDefault();
                if (this.activeIndex >= 0) {
                    this.selectItem(this.activeIndex);
                }
                break;
            case 27: // Escape
                e.preventDefault();
                this.close();
                break;
        }
    };

    SelectBox.prototype.scrollToActive = function () {
        var items = this.dropdown.querySelectorAll('li');
        if (this.activeIndex >= 0 && items[this.activeIndex]) {
            var li = items[this.activeIndex];
            var dropTop = this.dropdown.scrollTop;
            var dropBottom = dropTop + this.dropdown.clientHeight;
            if (li.offsetTop < dropTop) {
                this.dropdown.scrollTop = li.offsetTop;
            } else if (li.offsetTop + li.offsetHeight > dropBottom) {
                this.dropdown.scrollTop = li.offsetTop + li.offsetHeight - this.dropdown.clientHeight;
            }
        }
    };

    return {
        SelectBox: SelectBox
    };
})();

// Backward compatibility — existing code uses `new Autocompleter.SelectBox(...)`
if (typeof Autocompleter === 'undefined') {
    var Autocompleter = {};
}
Autocompleter.SelectBox = CarlosAutocomplete.SelectBox;

// Also provide Autocompleter.Local for template autocomplete in encounter pages
if (!Autocompleter.Local) {
    /**
     * Autocompleter.Local — vanilla JS replacement for Scriptaculous Autocompleter.Local.
     *
     * @param {string} inputId - The text input element ID
     * @param {string} dropdownId - The dropdown container element ID
     * @param {Array<string>} items - Array of strings to autocomplete against
     * @param {Object} [options] - Configuration options
     * @param {Object} [options.colours] - Map of item text to hex colour code
     * @param {Function} [options.afterUpdateElement] - Callback: fn(inputEl, selectedLi)
     */
    Autocompleter.Local = function (inputId, dropdownId, items, options) {
        var input = document.getElementById(inputId);
        var dropdown = document.getElementById(dropdownId);
        if (!input || !dropdown) return;

        options = options || {};
        var colours = options.colours || {};
        var afterUpdateElement = options.afterUpdateElement || null;
        var activeIndex = -1;
        var filteredItems = [];

        dropdown.style.position = 'absolute';
        dropdown.style.zIndex = '1000';
        dropdown.style.maxHeight = '200px';
        dropdown.style.overflowY = 'auto';
        dropdown.style.backgroundColor = '#fff';
        dropdown.style.border = '1px solid #ccc';
        dropdown.style.display = 'none';

        function renderDropdown(matches) {
            // Clear dropdown using safe DOM methods
            while (dropdown.firstChild) {
                dropdown.removeChild(dropdown.firstChild);
            }
            filteredItems = matches;
            if (matches.length === 0) {
                dropdown.style.display = 'none';
                return;
            }

            var ul = document.createElement('ul');
            ul.style.listStyle = 'none';
            ul.style.margin = '0';
            ul.style.padding = '0';

            matches.forEach(function (text, idx) {
                var li = document.createElement('li');
                li.textContent = text;
                li.style.padding = '4px 8px';
                li.style.cursor = 'pointer';
                if (colours[text]) {
                    li.style.backgroundColor = '#' + colours[text];
                }

                li.addEventListener('mouseenter', function () {
                    highlightItem(idx);
                });
                li.addEventListener('click', function (e) {
                    e.stopPropagation();
                    selectItem(idx);
                });
                ul.appendChild(li);
            });

            dropdown.appendChild(ul);

            // Position dropdown below the input
            var rect = input.getBoundingClientRect();
            var scrollTop = window.pageYOffset || document.documentElement.scrollTop;
            var scrollLeft = window.pageXOffset || document.documentElement.scrollLeft;
            dropdown.style.top = (rect.bottom + scrollTop) + 'px';
            dropdown.style.left = (rect.left + scrollLeft) + 'px';
            dropdown.style.width = rect.width + 'px';

            dropdown.style.display = 'block';
            activeIndex = -1;
        }

        function highlightItem(idx) {
            var lis = dropdown.querySelectorAll('li');
            lis.forEach(function (li, i) {
                if (i === idx) {
                    li.style.outline = '2px solid #316ac5';
                    li.style.backgroundColor = colours[li.textContent] ? '#' + colours[li.textContent] : '#316ac5';
                    li.style.color = '#fff';
                } else {
                    li.style.outline = '';
                    li.style.backgroundColor = colours[li.textContent] ? '#' + colours[li.textContent] : '';
                    li.style.color = '';
                }
            });
            activeIndex = idx;
        }

        function scrollToActive() {
            var lis = dropdown.querySelectorAll('li');
            if (activeIndex >= 0 && lis[activeIndex]) {
                var li = lis[activeIndex];
                var dropTop = dropdown.scrollTop;
                var dropBottom = dropTop + dropdown.clientHeight;
                if (li.offsetTop < dropTop) {
                    dropdown.scrollTop = li.offsetTop;
                } else if (li.offsetTop + li.offsetHeight > dropBottom) {
                    dropdown.scrollTop = li.offsetTop + li.offsetHeight - dropdown.clientHeight;
                }
            }
        }

        function selectItem(idx) {
            if (idx < 0 || idx >= filteredItems.length) return;
            var text = filteredItems[idx];
            // Grab the li BEFORE hiding the dropdown
            var selectedLi = dropdown.querySelectorAll('li')[idx];
            input.value = text;
            dropdown.style.display = 'none';
            activeIndex = -1;

            if (afterUpdateElement) {
                afterUpdateElement(input, selectedLi || { textContent: text });
            }
        }

        input.addEventListener('keyup', function (e) {
            var key = e.keyCode || e.which;
            if (key === 38 || key === 40 || key === 13 || key === 27) return;

            var query = input.value.toLowerCase();
            if (query.length === 0) {
                dropdown.style.display = 'none';
                return;
            }
            var matches = items.filter(function (item) {
                return item.toLowerCase().indexOf(query) !== -1;
            });
            renderDropdown(matches);
        });

        input.addEventListener('keydown', function (e) {
            if (dropdown.style.display === 'none') return;
            var key = e.keyCode || e.which;
            switch (key) {
                case 40: // Down
                    e.preventDefault();
                    if (activeIndex < filteredItems.length - 1) { highlightItem(activeIndex + 1); scrollToActive(); }
                    break;
                case 38: // Up
                    e.preventDefault();
                    if (activeIndex > 0) { highlightItem(activeIndex - 1); scrollToActive(); }
                    break;
                case 13: // Enter
                    e.preventDefault();
                    if (activeIndex >= 0) selectItem(activeIndex);
                    break;
                case 27: // Escape
                    e.preventDefault();
                    dropdown.style.display = 'none';
                    activeIndex = -1;
                    break;
            }
        });

        var docClickHandler = function (e) {
            if (e.target !== input && !dropdown.contains(e.target)) {
                dropdown.style.display = 'none';
                activeIndex = -1;
            }
        };
        document.addEventListener('click', docClickHandler);

        // Expose destroy for cleanup
        this.destroy = function () {
            document.removeEventListener('click', docClickHandler);
        };
    };
}
