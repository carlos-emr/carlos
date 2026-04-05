<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
    <%@page contentType="text/javascript; charset=UTF-8" pageEncoding="UTF-8"%>
    <%@ taglib uri="jakarta.tags.core" prefix="c"%>
    <c:set var="ctx" value="${pageContext.request.contextPath}"	scope="request" />

// global message
    var msg;


    // --- Encounter Session Timer ---
    // Tracks elapsed time for the encounter session. Displayed in the header bar
    // with color changes at 20 min (green) and 50 min (yellow) as billing cues.
    var d = new Date();  // encounter start time, used by pasteTimer() for time stamps
    var totalSeconds = 0;
    var myVar = setInterval(setTime, 1000);
    var toggle = true;   // true = timer running, false = paused

    /** Toggles the encounter timer between play/pause. Updates the button SVG icon. */
    function toggleATimer(e) {
        const pause = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-pause-fill" viewBox="0 0 16 16">' +
            '<path d="M5.5 3.5A1.5 1.5 0 0 1 7 5v6a1.5 1.5 0 0 1-3 0V5a1.5 1.5 0 0 1 1.5-1.5m5 0A1.5 1.5 0 0 1 12 5v6a1.5 1.5 0 0 1-3 0V5a1.5 1.5 0 0 1 1.5-1.5"></path>' +
            '</svg>';
        const play = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-play-fill" viewBox="0 0 16 16">' +
            '<path d="m11.596 8.697-6.363 3.692c-.54.313-1.233-.066-1.233-.697V4.308c0-.63.692-1.01 1.233-.696l6.363 3.692a.802.802 0 0 1 0 1.393"></path>' +
            '</svg>';
        if (toggle) {
            e.innerHTML = play;
            clearInterval(myVar);
            toggle = false;
        } else {
            e.innerHTML = pause;
            myVar = setInterval(setTime, 1000);
            toggle = true;
        }
    }

    /** Appends a formatted start/end/elapsed time stamp into the active note textarea. */
    function pasteTimer() {
        var ed = new Date();
        $(caseNote).value += "\n"
            + "\n" + document.getElementById("startTag").value + ": "
            + d.getHours() + ":" + pad(d.getMinutes()) + "\n"
            + document.getElementById("endTag").value + ": "
            + ed.getHours() + ":" + pad(ed.getMinutes()) + "\n"
            + pad(parseInt(totalSeconds / 3600)) + ":"
            + pad(parseInt((totalSeconds / 60) % 60)) + ":"
            + pad(totalSeconds % 60);
        adjustCaseNote();
    }

    /** Increments the timer display every second; changes background at billing thresholds. */
    function setTime() {
        ++totalSeconds;
        const aTimerButton = document.getElementById("aTimer");
        if (!aTimerButton) {
            return;
        }
        if (totalSeconds > 5) {
            aTimerButton.innerHTML = pad(parseInt(totalSeconds / 60)) + ":" + pad(totalSeconds % 60);
        }
        if (totalSeconds === 1200) {
            aTimerButton.style.background = "#DFF0D8";
        } //1200 sec = 20 min light green
        if (totalSeconds === 3000) {
            aTimerButton.style.background = "#FDFEC7";
        } //3600 sec = 50 min light yellow
    }

    /** Zero-pads a number to two digits (e.g. 5 → "05"). */
    function pad(val) {
        var valString = val + "";
        if (valString.length < 2) {
            return "0" + valString;
        } else {
            return valString;
        }
    }

    /**
     * Smart Template Tab-Stop Feature
     *
     * Enables Tab key navigation between placeholder markers in clinical note templates.
     * - '?' placeholders: Tab highlights, Enter removes so you can type
     * - '<option1|option2>' sections: Tab cycles through options, Enter selects one
     * - Clicking inside a <...> section selects that individual option
     * - Wraps around to the top of the note when no more placeholders are found
     *
     * Ported from OSCAR19 newEncounterLayout.js.jsp (lines 420-752).
     * Cleaned up: removed IE codepath, const/let usage, fixed wrap-around scoping bug.
     *
     * @since 2026-02-23
     */
    var smartTmplVM = {
        STATE: {
            TRAVERSING: "TRAVERSING",
            SELECTED_PLACEHOLDER_TEXT: "SELECTED_PLACEHOLDER_TEXT",
            SELECTED_OPTION_TEXT: "SELECTED_OPTION_TEXT"
        },
        TMPL_DELIM_START: '<',
        TMPL_DELIM_END: '>',
        TMPL_DELIM_CHOICE: '|',
        TMPL_PLACEHOLDER_CHAR: '?'
    };

    var smartTmpl = (function () {
        let state = smartTmplVM.STATE.TRAVERSING;
        let prevEl = null;
        let clickHandler = null;
        let keydownHandler = null;

        function init(el) {
            if (!el) {
                return;
            }

            // Remove previous handlers from the old element to prevent accumulation
            if (prevEl) {
                if (clickHandler) { prevEl.removeEventListener('click', clickHandler); }
                if (keydownHandler) { prevEl.removeEventListener('keydown', keydownHandler); }
            }

            // Reset navigation state when switching to a different element so that
            // mid-template state (e.g. SELECTED_PLACEHOLDER_TEXT) from the previous
            // note does not carry over and affect the first keypress on the new note.
            state = smartTmplVM.STATE.TRAVERSING;

            clickHandler = function (event) {
                const value = event.target.value;
                if (!value) {
                    return;
                }

                const selection = getTextSelection(event.target);

                // Walk backward from click position to find opening '<'
                let sectionStart = selection.start || 0;
                let sectionStartFound = false;
                for (let i = sectionStart; i > 0; --i) {
                    if (value[i - 1] === smartTmplVM.TMPL_DELIM_END) {
                        return;
                    }
                    if (value[i - 1] === smartTmplVM.TMPL_DELIM_START) {
                        sectionStart = i - 1;
                        sectionStartFound = true;
                        break;
                    }
                }
                if (!sectionStartFound) {
                    return;
                }

                // Walk forward to find closing '>'
                let sectionEnd = sectionStart;
                for (let i = sectionStart; i < value.length - 1; ++i) {
                    if (value[i + 1] === smartTmplVM.TMPL_DELIM_START) {
                        return;
                    }
                    if (value[i + 1] === smartTmplVM.TMPL_DELIM_END) {
                        sectionEnd = i + 2;
                        break;
                    }
                }

                const section = value.substring(sectionStart, sectionEnd);
                if (!section || !section.length) {
                    return;
                }

                // Find the specific choice the user clicked on
                let choiceStart = selection.start;
                for (let i = selection.start; i > sectionStart; --i) {
                    if (value[i - 1] === smartTmplVM.TMPL_DELIM_START || value[i - 1] === smartTmplVM.TMPL_DELIM_CHOICE) {
                        choiceStart = i;
                        break;
                    }
                }

                let choiceEnd = choiceStart;
                for (let i = choiceEnd; i < sectionEnd; i++) {
                    if (value[i + 1] === smartTmplVM.TMPL_DELIM_END || value[i + 1] === smartTmplVM.TMPL_DELIM_CHOICE) {
                        choiceEnd = i;
                        break;
                    }
                }

                const choice = value.substring(choiceStart, choiceEnd + 1);
                if (!choice || !choice.length) {
                    return;
                }

                // Replace the entire <...> section with the clicked choice
                event.target.value = value.substring(0, sectionStart) + choice + value.substring(sectionEnd, value.length);
                // Restore cursor to end of inserted choice so it doesn't jump to position 0
                const newCursorPos = sectionStart + choice.length;
                event.target.setSelectionRange(newCursorPos, newCursorPos);
            };
            el.addEventListener('click', clickHandler);

            keydownHandler = function (event) {
                const value = event.target.value;

                // Proceed only if Tab or Enter key is pressed
                if (event.key === 'Tab' || event.key === 'Enter') {
                    let selectionStart = el.selectionStart;
                    let selectionEnd = el.selectionEnd;

                    // If nothing is highlighted and Enter is pressed, let it behave normally
                    if (selectionEnd === selectionStart && event.key === 'Enter') { return; }

                    const highlighted = value
                        .substring(selectionStart, selectionEnd)
                        .trim();

                    // Determine state based on what is currently highlighted
                    if (isHighlighting(el) && event.key === 'Enter') {
                        if (highlighted === smartTmplVM.TMPL_PLACEHOLDER_CHAR) {
                            state = smartTmplVM.STATE.SELECTED_PLACEHOLDER_TEXT;
                        } else if (isTemplateOptionSelection(value, selectionStart, selectionEnd)) {
                            state = smartTmplVM.STATE.SELECTED_OPTION_TEXT;
                        } else {
                            // Regular text selection — let Enter behave normally (insert newline)
                            return;
                        }
                    } else {
                        state = smartTmplVM.STATE.TRAVERSING;
                    }

                    switch (state) {
                        case smartTmplVM.STATE.TRAVERSING: {
                            // Search forward from cursor for next placeholder or option section
                            let placeholderCharIndex = findPlaceholderCharIndex(selectionEnd, value);
                            let selectionTextIndex = findSelectionTextIndex(selectionEnd, value);

                            if (
                                placeholderCharIndex === -1 &&
                                selectionTextIndex.startIndex === -1 &&
                                selectionTextIndex.endIndex === -1
                            ) {
                                // Nothing found after cursor — wrap to top and search again
                                placeholderCharIndex = findPlaceholderCharIndex(0, value);
                                selectionTextIndex = findSelectionTextIndex(0, value);
                            }

                            // No template markers anywhere — let Tab/Enter behave normally
                            if (
                                placeholderCharIndex === -1 &&
                                selectionTextIndex.startIndex === -1 &&
                                selectionTextIndex.endIndex === -1
                            ) {
                                return;
                            }

                            // Highlight whichever comes first: option section or placeholder
                            if (
                                selectionTextIndex.startIndex !== -1 &&
                                selectionTextIndex.endIndex !== -1
                            ) {
                                if (
                                    selectionTextIndex.startIndex < placeholderCharIndex ||
                                    placeholderCharIndex === -1
                                ) {
                                    el.setSelectionRange(
                                        selectionTextIndex.startIndex,
                                        selectionTextIndex.endIndex
                                    );
                                    state = smartTmplVM.STATE.SELECTED_OPTION_TEXT;
                                } else {
                                    el.setSelectionRange(
                                        placeholderCharIndex,
                                        placeholderCharIndex + smartTmplVM.TMPL_PLACEHOLDER_CHAR.length
                                    );
                                    state = smartTmplVM.STATE.SELECTED_PLACEHOLDER_TEXT;
                                }
                                event.preventDefault();
                            } else if (placeholderCharIndex !== -1) {
                                el.setSelectionRange(
                                    placeholderCharIndex,
                                    placeholderCharIndex + smartTmplVM.TMPL_PLACEHOLDER_CHAR.length
                                );
                                state = smartTmplVM.STATE.SELECTED_PLACEHOLDER_TEXT;
                                event.preventDefault();
                            }

                            break;
                        }

                        case smartTmplVM.STATE.SELECTED_PLACEHOLDER_TEXT: {
                            // Remove the '?' placeholder so the user can type in its place
                            if (highlighted === smartTmplVM.TMPL_PLACEHOLDER_CHAR) {
                                const updatedValue =
                                    value.substring(0, selectionStart) +
                                    value.substring(selectionEnd, value.length);
                                event.target.value = updatedValue;
                                el.setSelectionRange(selectionStart, selectionStart);
                            }
                            event.preventDefault();
                            break;
                        }

                        case smartTmplVM.STATE.SELECTED_OPTION_TEXT: {
                            // Replace entire <opt1|opt2> section with the highlighted option
                            // Use original (untrimmed) text for insertion, trimmed only for state detection
                            const highlightedForInsert = value.substring(selectionStart, selectionEnd);
                            // Search forward from current position for the closing '>'
                            selectionEnd = value.indexOf(smartTmplVM.TMPL_DELIM_END, selectionStart);
                            if (selectionEnd !== -1) {
                                selectionEnd += 1; // advance past the '>'
                                selectionStart = value.lastIndexOf(smartTmplVM.TMPL_DELIM_START, selectionEnd);
                                const updatedValue =
                                    value.substring(0, selectionStart) +
                                    highlightedForInsert +
                                    value.substring(selectionEnd, value.length);
                                event.target.value = updatedValue;
                                el.setSelectionRange(selectionStart, selectionStart + highlightedForInsert.length);
                            }
                            event.preventDefault();
                            break;
                        }

                        default:
                            break;
                    }
                } else {
                    state = smartTmplVM.STATE.TRAVERSING;
                }
            };
            el.addEventListener('keydown', keydownHandler);

            prevEl = el;
        }

        // Return the current text selection range for an element
        function getTextSelection(el) {
            return {
                start: el.selectionStart || 0,
                end: el.selectionEnd || 0
            };
        }

        // Check whether any text is currently highlighted
        function isHighlighting(el) {
            return el.selectionStart !== el.selectionEnd;
        }

        // Returns true only when the current selection is an option within a <opt1|opt2> block.
        // Checks that the character immediately before selectionStart is '<' or '|', and the
        // character immediately after selectionEnd is '|' or '>'.  This prevents Enter from
        // being swallowed when the user has selected arbitrary (non-template) text.
        function isTemplateOptionSelection(value, selectionStart, selectionEnd) {
            if (selectionStart === selectionEnd) { return false; }
            const left = value[selectionStart - 1];
            const right = value[selectionEnd];
            return (
                (left === smartTmplVM.TMPL_DELIM_START || left === smartTmplVM.TMPL_DELIM_CHOICE) &&
                (right === smartTmplVM.TMPL_DELIM_CHOICE || right === smartTmplVM.TMPL_DELIM_END)
            );
        }

        // Find the next '?' placeholder character at or after selectionEnd
        function findPlaceholderCharIndex(selectionEnd, value) {
            if (!value || !value.length) {
                return -1;
            }
            return value.indexOf(smartTmplVM.TMPL_PLACEHOLDER_CHAR, selectionEnd);
        }

        // Find the next option text range within <...> at or after selectionEnd
        function findSelectionTextIndex(selectionEnd, value) {
            let startIndex = -1;
            let endIndex = -1;

            if (!value || !value.length) {
                return { startIndex, endIndex };
            }

            try {
                startIndex = value.indexOf(smartTmplVM.TMPL_DELIM_START, selectionEnd);
                const pipeIndex = value.indexOf(smartTmplVM.TMPL_DELIM_CHOICE, selectionEnd);

                // Only treat a '|' as a valid delimiter if it is enclosed inside a <...> block.
                // A standalone '|' (e.g. a separator in clinical text "BP 120/80 | normal") must
                // not be mistaken for an option delimiter.
                let validPipeIndex = -1;
                if (pipeIndex !== -1) {
                    const precedingAngle = value.lastIndexOf(smartTmplVM.TMPL_DELIM_START, pipeIndex);
                    const followingClose = value.indexOf(smartTmplVM.TMPL_DELIM_END, pipeIndex);
                    const lastCloseBeforePipe = value.lastIndexOf(smartTmplVM.TMPL_DELIM_END, pipeIndex);
                    // Valid only if there is a '<' before the pipe that is not already closed
                    if (precedingAngle !== -1 && followingClose !== -1 && lastCloseBeforePipe < precedingAngle) {
                        validPipeIndex = pipeIndex;
                    }
                }

                if (startIndex === -1) { startIndex = validPipeIndex; }
                if (startIndex !== -1) {
                    if (validPipeIndex !== -1 && startIndex > validPipeIndex) {
                        // A valid '|' delimiter comes before '<', start selection there
                        startIndex = validPipeIndex;
                    }
                    endIndex = value
                        .substring(startIndex, value.length)
                        .indexOf(smartTmplVM.TMPL_DELIM_END);
                    if (endIndex !== -1) {
                        endIndex += startIndex + 1;
                    }
                }

                if (startIndex !== -1 && endIndex !== -1) {
                    startIndex++;
                    const substring = value.substring(startIndex, endIndex);
                    if (substring && substring.includes(smartTmplVM.TMPL_DELIM_CHOICE)) {
                        endIndex = substring.indexOf(smartTmplVM.TMPL_DELIM_CHOICE);
                    } else if (substring) {
                        endIndex = substring.indexOf(smartTmplVM.TMPL_DELIM_END);
                    }
                    if (endIndex !== -1) {
                        endIndex += startIndex;
                    }
                }
            } catch (e) {
                /* ignore selection index errors */
            }

            return { startIndex, endIndex };
        }

        return {
            init: init
        };
    })();

    /**
     * Template Shortcut Feature (Backslash Command Palette)
     *
     * Press '\' in the note textarea to open a floating search popup for
     * encounter note templates. Type to filter, Arrow keys to navigate,
     * Enter to select, Escape to dismiss. Shows all entries from the
     * autoCompList global (populated by ChartNotesAjax.jsp in the case-mgmt
     * context, which contains templates only; other contexts such as the
     * full encounter may also include calculators).
     *
     * Reuses existing ajaxInsertTemplate() for insertion and smartTmpl.init()
     * for tab-stop activation after template content is loaded.
     *
     * @since 2026-02-23
     */
    var templateShortcut = (function () {
        let currentEl = null;
        let popupEl = null;
        let selectedIndex = 0;
        let filteredItems = [];

        // Document-level delegation: fires only for the active case note textarea
        document.addEventListener('keydown', function (event) {
            if (event.key === '\\' && !popupEl && event.target && event.target.tagName === 'TEXTAREA') {
                if (typeof caseNote !== 'undefined' && event.target.id === caseNote) {
                    currentEl = event.target;
                    event.preventDefault();
                    showPopup();
                }
            }
        });

        function init(el) {
            if (!el) return;
            currentEl = el;
        }

        function getTemplateNames() {
            if (typeof autoCompList === 'undefined') return [];
            // Return all items: charting templates, forms, and eForms
            return autoCompList.slice();
        }

        function clearChildren(el) {
            while (el.firstChild) {
                el.removeChild(el.firstChild);
            }
        }

        function showPopup() {
            selectedIndex = 0;
            filteredItems = getTemplateNames();

            popupEl = document.createElement('div');
            popupEl.id = 'templateShortcutOverlay';
            popupEl.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,0.25);z-index:10000;display:flex;justify-content:center;padding-top:80px;';

            var container = document.createElement('div');
            container.style.cssText = 'background:#fff;border-radius:8px;box-shadow:0 4px 24px rgba(0,0,0,0.18);width:380px;max-height:420px;display:flex;flex-direction:column;align-self:flex-start;';

            var searchInput = document.createElement('input');
            searchInput.type = 'text';
            searchInput.placeholder = 'Search templates\u2026';
            searchInput.style.cssText = 'padding:10px 14px;border:none;border-bottom:1px solid #dee2e6;font-size:14px;outline:none;border-radius:8px 8px 0 0;width:100%;box-sizing:border-box;';

            var listEl = document.createElement('div');
            listEl.style.cssText = 'overflow-y:auto;max-height:340px;padding:4px 0;';

            container.appendChild(searchInput);
            container.appendChild(listEl);
            popupEl.appendChild(container);
            document.body.appendChild(popupEl);

            renderList(listEl);
            searchInput.focus();

            searchInput.addEventListener('input', function () {
                var query = searchInput.value.toLowerCase();
                filteredItems = getTemplateNames().filter(function (name) {
                    return name.toLowerCase().indexOf(query) !== -1;
                });
                selectedIndex = 0;
                renderList(listEl);
            });

            searchInput.addEventListener('keydown', function (event) {
                // Cap navigation to visible rows (renderList only shows up to 50 items)
                var visibleCount = Math.min(filteredItems.length, 50);
                if (event.key === 'ArrowDown') {
                    event.preventDefault();
                    if (selectedIndex < visibleCount - 1) {
                        selectedIndex++;
                        updateSelection(listEl);
                    }
                } else if (event.key === 'ArrowUp') {
                    event.preventDefault();
                    if (selectedIndex > 0) {
                        selectedIndex--;
                        updateSelection(listEl);
                    }
                } else if (event.key === 'Enter') {
                    event.preventDefault();
                    if (visibleCount > 0 && selectedIndex < visibleCount) {
                        selectTemplate(filteredItems[selectedIndex]);
                    }
                } else if (event.key === 'Escape') {
                    event.preventDefault();
                    closePopup();
                }
            });

            popupEl.addEventListener('mousedown', function (event) {
                if (event.target === popupEl) {
                    closePopup();
                }
            });
        }

        function renderList(listEl) {
            if (!listEl) return;
            clearChildren(listEl);

            if (filteredItems.length === 0) {
                var emptyEl = document.createElement('div');
                emptyEl.style.cssText = 'padding:12px 14px;color:#6c757d;font-size:13px;';
                emptyEl.textContent = 'No matching templates';
                listEl.appendChild(emptyEl);
                return;
            }

            var limit = Math.min(filteredItems.length, 50);
            // Clamp selectedIndex so it never references a row that doesn't exist
            if (limit > 0 && selectedIndex > limit - 1) { selectedIndex = limit - 1; }
            for (var i = 0; i < limit; i++) {
                var itemEl = document.createElement('div');
                itemEl.textContent = filteredItems[i];
                itemEl.dataset.index = i;
                itemEl.style.cssText = 'padding:7px 14px;cursor:pointer;font-size:13px;' +
                    (i === selectedIndex ? 'background:#e9ecef;' : '');
                itemEl.addEventListener('mouseenter', (function (idx) {
                    return function () {
                        selectedIndex = idx;
                        updateSelection(listEl);
                    };
                })(i));
                itemEl.addEventListener('click', (function (name) {
                    return function () {
                        selectTemplate(name);
                    };
                })(filteredItems[i]));
                listEl.appendChild(itemEl);
            }
        }

        function updateSelection(listEl) {
            if (!listEl) return;
            var items = listEl.children;
            for (var i = 0; i < items.length; i++) {
                items[i].style.background = (i === selectedIndex) ? '#e9ecef' : '';
            }
            if (items[selectedIndex]) {
                items[selectedIndex].scrollIntoView({ block: 'nearest' });
            }
        }

        function selectTemplate(name) {
            closePopup();
            // Mirror menuAction() behavior: set the search field value then trigger it
            var enTemplate = document.getElementById('enTemplate');
            if (enTemplate) {
                enTemplate.value = name;
                if (typeof menuAction === 'function') {
                    menuAction();
                }
            }
        }

        function closePopup() {
            if (popupEl) {
                popupEl.remove();
                popupEl = null;
            }
            if (currentEl) {
                currentEl.focus();
            }
        }

        return {
            init: init
        };
    })();

    // Encounter scroll routing:
    // - Over encMainDivWrapper: native scroll (notes area scrolls)
    // - Over any scrollable overlay (template popup, issue lists): native scroll
    // - Over left/right sidebars: scroll #navigation-layout (page scroll)
    // - Over CPP boxes/control panel: scroll #navigation-layout
    document.addEventListener('wheel', function (event) {
        if (event.ctrlKey || event.metaKey || event.altKey) { return; }

        var wrapper = document.getElementById('encMainDivWrapper');
        if (wrapper && wrapper.contains(event.target)) {
            // Let native scroll handle the notes area
            return;
        }

        // Let any scrollable overlay/popup handle its own scrolling
        for (var el = event.target instanceof Element ? event.target : event.target.parentElement; el && el !== document.body; el = el.parentElement) {
            var style = window.getComputedStyle(el);
            if (/(auto|scroll)/.test(style.overflowY) && el.scrollHeight > el.clientHeight) {
                return;
            }
        }

        // Sidebar/CPP areas: route scroll to navigation-layout
        var navLayout = document.getElementById('navigation-layout');
        if (navLayout) {
            event.preventDefault();
            navLayout.scrollTop += event.deltaY;
        }
    }, { passive: false, capture: true });

    /**
     * EChart Keyboard Shortcuts
     *
     * Alt-key shortcuts for common encounter actions. These mirror the toolbar
     * buttons in ChartNotes.jsp and navigation links in navigation.jsp, giving
     * providers keyboard-driven workflow without reaching for the mouse.
     *
     * Alt+1  Sign, Save & Bill — signs the note, flags for billing, submits and exits
     * Alt+2  Save              — saves the current note without signing or exiting
     * Alt+3  Sign & Save       — signs the note, submits and exits (no billing)
     * Alt+4  Exit              — exits the encounter (prompts if unsaved changes)
     * Alt+T  New Tickler       — opens the Add Tickler window for this patient
     * Alt+C  New Consult       — opens a new Consultation Request form for this patient
     * Alt+M  Medications (Rx)   — opens the Prescriptions (Rx) window for this patient
     *
     * Save/sign/exit shortcuts (Alt+1-4) are suppressed when focus is inside an
     * input or select to avoid interfering with normal typing, but still work from
     * the note textarea. Popup shortcuts (Alt+T/C/M) work from any context.
     * The Alt modifier was chosen because Ctrl combinations conflict with browser
     * defaults (Ctrl+S = save page, etc).
     *
     * @since 2026-04-04
     */
    document.addEventListener('keydown', function (event) {
        // Only handle plain Alt+key — ignore Ctrl+Alt and Cmd+Alt combos (used by OS/browser)
        if (!event.altKey || event.ctrlKey || event.metaKey) return;

        // Don't intercept Alt keys when typing in form fields (except the note
        // textarea, where providers spend most of their time and need quick access)
        var tag = event.target.tagName;
        var isFormField = (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT');

        var frm = document.forms['caseManagementEntryForm'];
        var handled = false;

        // Chrome/Edge report uppercase letters for Alt+key; normalize to lowercase
        var key = event.key.toLowerCase();

        switch (key) {
            case '1': // Alt+1: Sign, Save & Bill
                if (!isFormField || tag === 'TEXTAREA') {
                    frm.sign.value = 'on';
                    frm.toBill.value = 'true';
                    savePage('saveAndExit', '');
                    handled = true;
                }
                break;

            case '2': // Alt+2: Save
                if (!isFormField || tag === 'TEXTAREA') {
                    saveNoteAjax('save', 'list');
                    handled = true;
                }
                break;

            case '3': // Alt+3: Sign & Save
                if (!isFormField || tag === 'TEXTAREA') {
                    frm.sign.value = 'on';
                    savePage('saveAndExit', '');
                    handled = true;
                }
                break;

            case '4': // Alt+4: Exit
                if (!isFormField || tag === 'TEXTAREA') {
                    closeEnc(event);
                    handled = true;
                }
                break;

            case 't': // Alt+T: New Tickler
                window.open(ctx + '/tickler/ticklerAdd.jsp?demographic_no=' + demographicNo, '',
                    'height=600,width=700,scrollbars=yes,resizable=yes');
                handled = true;
                break;

            case 'c': // Alt+C: New Consult Request
                window.open(ctx + '/encounter/oscarConsultationRequest/ConsultationFormRequest.jsp?de=' + demographicNo, '',
                    'height=700,width=960,scrollbars=yes,resizable=yes');
                handled = true;
                break;

            case 'm': // Alt+M: Medications (Rx)
                // String.fromCharCode(38) produces '&' at runtime — a literal '&' in this .js.jsp
                // file may be misinterpreted by the JSP compiler as an HTML entity start.
                window.open(ctx + '/oscarRx/choosePatient.do?providerNo=' + providerNo + String.fromCharCode(38) + 'demographicNo=' + demographicNo, '',
                    'height=700,width=1027,scrollbars=yes,resizable=yes');
                handled = true;
                break;

            case 's': // Alt+S: Patient Search
                window.open(ctx + '/demographic/search.jsp', '',
                    'height=550,width=687,scrollbars=yes,resizable=yes');
                handled = true;
                break;
        }

        if (handled) {
            event.preventDefault();   // Suppress browser's default Alt+key behavior
            event.stopPropagation();  // Prevent other keydown listeners from reacting
        }
    });

    /* ------------------------------------------------------------------ *
     * EChart Keyboard Shortcuts Help Modal                                *
     * Press Alt+? to open/close. Also closes on Esc, X button, or backdrop. *
     * @since 2026-04-05                                                   *
     * ------------------------------------------------------------------ */
    (function () {
        var MODAL_ID    = 'carlosEncounterKbModal';
        var BACKDROP_ID = 'carlosEncounterKbBackdrop';

        function buildModal() {
            if (document.getElementById(MODAL_ID)) return;

            var style = document.createElement('style');
            style.textContent =
                '#' + BACKDROP_ID + '{' +
                '  position:fixed;inset:0;background:rgba(0,0,0,.55);z-index:9998;' +
                '  display:none;' +
                '}' +
                '#' + MODAL_ID + '{' +
                '  position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);' +
                '  background:#fff;border-radius:8px;padding:24px 28px 20px;' +
                '  z-index:9999;min-width:340px;max-width:480px;width:90%;' +
                '  box-shadow:0 8px 32px rgba(0,0,0,.28);font-family:inherit;' +
                '  display:none;' +
                '}' +
                '#' + MODAL_ID + ' h2{' +
                '  margin:0 0 16px;font-size:1.1rem;color:#333;' +
                '  padding-right:28px;border-bottom:1px solid #e5e7eb;padding-bottom:10px;' +
                '}' +
                '#' + MODAL_ID + ' table{width:100%;border-collapse:collapse;}' +
                '#' + MODAL_ID + ' th{text-align:left;font-size:.78rem;color:#6b7280;' +
                '  text-transform:uppercase;letter-spacing:.05em;padding-bottom:6px;}' +
                '#' + MODAL_ID + ' td{padding:5px 0;font-size:.9rem;color:#374151;}' +
                '#' + MODAL_ID + ' td:first-child{white-space:nowrap;padding-right:16px;}' +
                '#' + MODAL_ID + ' kbd{' +
                '  display:inline-block;padding:2px 6px;background:#f3f4f6;' +
                '  border:1px solid #d1d5db;border-radius:4px;font-size:.82rem;' +
                '  font-family:monospace;color:#111827;box-shadow:0 1px 0 #adb5bd;' +
                '}' +
                '#' + MODAL_ID + ' .carlos-kb-close{' +
                '  position:absolute;top:12px;right:14px;background:none;border:none;' +
                '  font-size:1.3rem;line-height:1;cursor:pointer;color:#6b7280;' +
                '  padding:4px 6px;border-radius:4px;' +
                '}' +
                '#' + MODAL_ID + ' .carlos-kb-close:hover{background:#f3f4f6;color:#111;}' +
                '#' + MODAL_ID + ' .carlos-kb-footer{' +
                '  margin-top:14px;padding-top:10px;border-top:1px solid #e5e7eb;' +
                '  font-size:.8rem;color:#9ca3af;text-align:center;' +
                '}';
            document.head.appendChild(style);

            var backdrop = document.createElement('div');
            backdrop.id = BACKDROP_ID;
            backdrop.setAttribute('aria-hidden', 'true');
            backdrop.addEventListener('click', closeModal);
            document.body.appendChild(backdrop);

            var modal = document.createElement('div');
            modal.id = MODAL_ID;
            modal.setAttribute('role', 'dialog');
            modal.setAttribute('aria-modal', 'true');
            modal.setAttribute('aria-label', 'Keyboard shortcuts');
            modal.innerHTML =
                '<button class="carlos-kb-close" aria-label="Close shortcuts" title="Close (Esc)">' +
                '\u00d7</button>' +
                '<h2>&#9000; EChart Keyboard Shortcuts</h2>' +
                '<table>' +
                '<thead><tr>' +
                '<th>Key</th>' +
                '<th>Action</th>' +
                '</tr></thead>' +
                '<tbody>' +
                '<tr><td><kbd>Alt</kbd>+<kbd>1</kbd></td><td>Sign, Save &amp; Bill</td></tr>' +
                '<tr><td><kbd>Alt</kbd>+<kbd>2</kbd></td><td>Save</td></tr>' +
                '<tr><td><kbd>Alt</kbd>+<kbd>3</kbd></td><td>Sign &amp; Save</td></tr>' +
                '<tr><td><kbd>Alt</kbd>+<kbd>4</kbd></td><td>Exit encounter</td></tr>' +
                '<tr><td><kbd>Alt</kbd>+<kbd>T</kbd></td><td>New Tickler</td></tr>' +
                '<tr><td><kbd>Alt</kbd>+<kbd>C</kbd></td><td>New Consult Request</td></tr>' +
                '<tr><td><kbd>Alt</kbd>+<kbd>M</kbd></td><td>Medications (Rx)</td></tr>' +
                '<tr><td><kbd>Alt</kbd>+<kbd>S</kbd></td><td>Patient Search</td></tr>' +
                '</tbody>' +
                '</table>' +
                '<div class="carlos-kb-footer">' +
                '<kbd>Alt</kbd>+<kbd>?</kbd> to show/hide &nbsp;&bull;&nbsp; <kbd>Esc</kbd> or click outside to close' +
                '</div>';
            modal.querySelector('.carlos-kb-close').addEventListener('click', closeModal);
            document.body.appendChild(modal);
        }

        function openModal() {
            buildModal();
            document.getElementById(BACKDROP_ID).style.display = 'block';
            document.getElementById(MODAL_ID).style.display    = 'block';
            document.getElementById(MODAL_ID).querySelector('.carlos-kb-close').focus();
        }

        function closeModal() {
            var m = document.getElementById(MODAL_ID);
            var b = document.getElementById(BACKDROP_ID);
            if (m) m.style.display = 'none';
            if (b) b.style.display = 'none';
        }

        function isOpen() {
            var m = document.getElementById(MODAL_ID);
            return m && m.style.display !== 'none';
        }

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                closeModal();
                return;
            }
            // Help modal: Alt+? — ignore Ctrl+Alt and Cmd+Alt combos
            if (e.altKey && !e.ctrlKey && !e.metaKey && e.key === '?') {
                var tag = e.target.tagName;
                // Don't intercept Alt+? when typing in form fields
                if (tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA') return;
                if (isOpen()) {
                    closeModal();
                } else {
                    openModal();
                }
                e.preventDefault();
            }
        });
    }());
