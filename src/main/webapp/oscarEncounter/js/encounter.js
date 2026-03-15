/**
 * CARLOS EMR - Encounter Page JavaScript
 *
 * Consolidates inline scripts from the legacy Index2.jsp encounter page.
 * All server-side values are passed via the encounterConfig object defined
 * in encounter-head.jspf.
 *
 * Dependencies:
 *   - CarlosAjax (carlos-ajax.js — AJAX requests with CSRF support)
 *   - prototype-compat.js (shims for legacy popup menu scripts)
 *   - encounterConfig (inline JSP config block)
 *
 * Security note: AJAX responses that use innerHTML assignment are from trusted
 * internal server endpoints only. XSS is mitigated by server-side OWASP
 * encoding in those response JSPs.
 *
 * Migration note (Phase 4a): Prototype.js/Scriptaculous replaced with
 * vanilla JS + CarlosAjax. See docs/prototype-migration.md for details.
 */

/* global encounterConfig, CarlosAjax, changeObjectVisibility, getStyleObject, showPopup */

// ============================================================
// Size constants
// ============================================================
var X = 10;
var pBSmall = 30;
var small = 60;
var normal = 166;
var medium = 272;
var large = 378;
var full = 649;

// ============================================================
// State variables
// ============================================================
var handlePressState = 0;
var handlePressFocus;
var handlePressFocus2 = "none";
var keyPressed;
var textValue1;
var textValue2;
var textValue3;
var calculatorColour = "9900CC";
var measurementWindows = [];
var openWindows = {};
var autoCompleted = {};
var autoCompList = [];
var itemColours = {};
var curWin = 0;
var numMenus = 3;
var updateNeeded = false;

// ============================================================
// Autocomplete Initialization
// ============================================================
function initAutoComplete() {
    if (!encounterConfig) return;

    if (encounterConfig.calculators) {
        for (var i = 0; i < encounterConfig.calculators.length; i++) {
            var calc = encounterConfig.calculators[i];
            autoCompleted[calc.name] = calc.action;
            autoCompList.push(calc.name);
            itemColours[calc.name] = calculatorColour;
        }
    }

    if (encounterConfig.templates) {
        for (var j = 0; j < encounterConfig.templates.length; j++) {
            var tmpl = encounterConfig.templates[j];
            autoCompleted[tmpl] = "ajaxInsertTemplate('" + tmpl + "')";
            autoCompList.push(tmpl);
            itemColours[tmpl] = "99CCCC";
        }
    }
}

// ============================================================
// Row Sizing (consolidated from 30+ repetitive functions)
// ============================================================
function setRowSize(textareaNames, hiddenFieldName, sizeValue, scrollTarget) {
    for (var i = 0; i < textareaNames.length; i++) {
        document.encForm[textareaNames[i]].style.overflow = 'auto';
        document.encForm[textareaNames[i]].style.height = sizeValue;
    }
    document.encForm[hiddenFieldName].value = sizeValue;
    if (scrollTarget) {
        var el = document.encForm[scrollTarget] || document.getElementById(scrollTarget);
        if (el) el.scrollIntoView(true);
    }
}

function setPresBoxSize(sizeValue, scrollTarget) {
    document.getElementById('presBox').style.height = sizeValue;
    document.getElementById('allergyBox').style.height = sizeValue;
    document.encForm.presBoxSize.value = sizeValue;
    if (scrollTarget) {
        document.getElementById(scrollTarget).scrollIntoView(true);
    }
}

function reset() {
    setRowSize(['shTextarea', 'fhTextarea', 'mhTextarea'], 'rowOneSize', small);
    setRowSize(['ocTextarea', 'reTextarea'], 'rowTwoSize', small);
    setRowSize(['enTextarea'], 'rowThreeSize', large);
    setPresBoxSize(pBSmall);
}

function rowOneX()      { setRowSize(['shTextarea', 'fhTextarea', 'mhTextarea'], 'rowOneSize', X); }
function rowOneSmall()  { setRowSize(['shTextarea', 'fhTextarea', 'mhTextarea'], 'rowOneSize', small); }
function rowOneNormal() { setRowSize(['shTextarea', 'fhTextarea', 'mhTextarea'], 'rowOneSize', normal); }
function rowOneLarge()  { setRowSize(['shTextarea', 'fhTextarea', 'mhTextarea'], 'rowOneSize', large); }
function rowOneFull()   { setRowSize(['shTextarea', 'fhTextarea', 'mhTextarea'], 'rowOneSize', full, 'shInput'); }

function rowTwoX()      { setRowSize(['ocTextarea', 'reTextarea'], 'rowTwoSize', X); }
function rowTwoSmall()  { setRowSize(['ocTextarea', 'reTextarea'], 'rowTwoSize', small); }
function rowTwoNormal() { setRowSize(['ocTextarea', 'reTextarea'], 'rowTwoSize', normal); }
function rowTwoLarge()  { setRowSize(['ocTextarea', 'reTextarea'], 'rowTwoSize', large); }
function rowTwoFull()   { setRowSize(['ocTextarea', 'reTextarea'], 'rowTwoSize', full, 'ocInput'); }

function rowThreeX()      { setRowSize(['enTextarea'], 'rowThreeSize', X); }
function rowThreeSmall()  { setRowSize(['enTextarea'], 'rowThreeSize', small); }
function rowThreeNormal() { setRowSize(['enTextarea'], 'rowThreeSize', normal); }
function rowThreeMedium() { setRowSize(['enTextarea'], 'rowThreeSize', medium); }
function rowThreeLarge()  { setRowSize(['enTextarea'], 'rowThreeSize', large); }
function rowThreeFull()   { setRowSize(['enTextarea'], 'rowThreeSize', full, 'enInput'); }

function presBoxX()      { setPresBoxSize(X); }
function presBoxSmall()  { setPresBoxSize(pBSmall); }
function presBoxNormal() { setPresBoxSize(normal); }
function presBoxLarge()  { setPresBoxSize(large); }
function presBoxFull()   { setPresBoxSize(full, 'presTopTable'); }

// ============================================================
// Window Management
// ============================================================
function popupPage(vheight, vwidth, name, varpage) {
    var page = "" + varpage;
    name = name.replace(/\s+/g, "_");
    var windowprops = "height=" + vheight + ",width=" + vwidth +
        ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=600,screenY=200,top=0,left=0";
    openWindows[name] = window.open(page, name, windowprops);
    if (openWindows[name] != null) {
        if (openWindows[name].opener == null) {
            openWindows[name].opener = self;
            alert(encounterConfig.i18n.popupPageAlert);
        }
        openWindows[name].focus();
    }
}

function popupStart(vheight, vwidth, varpage) {
    var windowprops = "height=" + vheight + ",width=" + vwidth +
        ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
    var popup = window.open(varpage, "", windowprops);
    if (popup != null && popup.opener == null) {
        popup.opener = self;
    }
}

function popupStart1(vheight, vwidth, varpage) {
    var windowprops = "height=" + vheight + ",width=" + vwidth +
        ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
    window.open(varpage, encounterConfig.i18n.title, windowprops);
}

function closeEncounterWindow() {
    return window.confirm(encounterConfig.i18n.closeEncounterConfirm);
}

function onSplit() {
    document.forms['encForm'].btnPressed.value = 'Split Chart';
    return confirm(encounterConfig.i18n.confirmSplit);
}

function getAnotherEncounter(newAppointmentNo) {
    location = "./IncomingEncounter.do?appointmentList=true&appointmentNo=" + newAppointmentNo;
}

function measurementLoaded(name) {
    measurementWindows.push(openWindows[name]);
}

function onClosing() {
    for (var idx = 0; idx < measurementWindows.length; ++idx) {
        if (!measurementWindows[idx].closed)
            measurementWindows[idx].parentChanged = true;
    }
}

// ============================================================
// Text Manipulation
// ============================================================
function setCaretPosition(inpu, pos) {
    if (inpu.setSelectionRange) {
        inpu.focus();
        inpu.setSelectionRange(pos, pos);
    } else if (inpu.createTextRange) {
        var range = inpu.createTextRange();
        range.collapse(true);
        range.moveEnd('character', pos);
        range.moveStart('character', pos);
        range.select();
    }
}

function writeToEncounterNote(request) {
    var text = request.responseText;
    // Single-pass replacement prevents double-unescaping (CWE-116 / CodeQL js/double-escaping).
    // Sequential replace() calls would allow \u005Cu0022 to produce " by first turning
    // \u005C into \ and then \u0022 into ". A lookup-table with one regex pass is safe.
    var unescape = {
        '\\u000A': '\u000A',
        '\\u000D': '',
        '\\u003E': '>',
        '\\u003C': '<',
        '\\u005C': '\\',
        '\\u0022': '"',
        '\\u0027': "'"
    };
    text = text.replace(/\\u[0-9A-Fa-f]{4}/g, function (match) {
        return unescape.hasOwnProperty(match) ? unescape[match] : match;
    });

    document.encForm.enTextarea.value += "\n\n";
    var curPos = document.encForm.enTextarea.value.length;

    if (document.all) {
        var newLines = document.encForm.enTextarea.value.match(/.*\n.*/g);
        curPos -= newLines.length;
    }
    ++curPos;

    var newlinePos;
    if ((newlinePos = text.indexOf('\n')) == 0) {
        ++newlinePos;
        var subtxt = text.substr(newlinePos);
        curPos += subtxt.indexOf('\n');
    }

    document.encForm.enTextarea.value = document.encForm.enTextarea.value + text;
    setTimeout(function () { document.encForm.enTextarea.scrollTop = document.encForm.enTextarea.scrollHeight; }, 0);
    document.encForm.enTextarea.focus();
    setCaretPosition(document.encForm.enTextarea, curPos);
}

function ajaxInsertTemplate(varpage) {
    if (varpage != 'null') {
        var page = encounterConfig.ctx + "/oscarEncounter/InsertTemplate.do";
        var params = "templateName=" + varpage + "&version=2";
        CarlosAjax.request(page, {
            method: 'post',
            postBody: params,
            evalScripts: true,
            onSuccess: writeToEncounterNote,
            onFailure: function () {
                alert("Inserting template " + varpage + " failed");
            }
        });
    }
}

function insertTemplate(text) {
    document.encForm.enTextarea.value = document.encForm.enTextarea.value + "\n\n" + text;
    document.encForm.enTextarea.value = document.encForm.enTextarea.value.replace(/\\u003E/g, ">");
    document.encForm.enTextarea.value = document.encForm.enTextarea.value.replace(/\\u003C/g, "<");
    document.encForm.enTextarea.value = document.encForm.enTextarea.value.replace(/\\u005C/g, "\\");
    document.encForm.enTextarea.value = document.encForm.enTextarea.value.replace(/\\u0022/g, "\"");
    document.encForm.enTextarea.value = document.encForm.enTextarea.value.replace(/\\u0027/g, "'");
    window.setTimeout(function () { document.encForm.enTextarea.scrollTop = 2147483647; }, 0);
    document.encForm.enTextarea.focus();
}

// ============================================================
// Navigation
// ============================================================
function refresh() {
    var u = self.location.href;
    if (u.lastIndexOf("&status=B") > 0) {
        self.location.href = u.substring(0, u.lastIndexOf("&status=B")) + "&status=P";
    } else if (u.lastIndexOf("&status=") > 0) {
        self.location.href = u.substring(0, u.lastIndexOf("&status=")) + "&status=B";
    } else {
        history.go(0);
    }
    if (self.opener && !self.opener.closed && typeof self.opener.refresh === 'function') {
        self.opener.refresh();
    }
}

function onUnbilled(url) {
    if (confirm(encounterConfig.i18n.onUnbilledConfirm)) {
        popupPage(700, 720, 'unbilled', url);
    }
}

// ============================================================
// URL Encoding
// ============================================================
function urlencode(str) {
    return encodeURIComponent(str);
    // Legacy implementation below kept for reference only
    var ms = "%25#23 20+2B?3F<3C>3E{7B}7D[5B]5D|7C^5E~7E`60";
    var msi = 0;
    var i, c, rs, ts;
    while (msi < ms.length) {
        c = ms.charAt(msi);
        rs = ms.substring(++msi, msi + 2);
        msi += 2;
        i = 0;
        while (true) {
            i = str.indexOf(c, i);
            if (i == -1) break;
            ts = str.substring(0, i);
            str = ts + "%" + rs + str.substring(++i, str.length);
        }
    }
    return str;
}

// ============================================================
// AJAX / Left Nav Loading
// (uses CarlosAjax for trusted internal server responses —
// server-side OWASP encoding applied in response JSPs)
// ============================================================

/**
 * Sets trusted server HTML into a DOM element.
 * All callers receive content from internal CARLOS server endpoints
 * that apply OWASP encoding. This helper centralizes the assignment
 * for auditability.
 * @param {HTMLElement} el - target element
 * @param {string} html - trusted server response HTML
 */
function setTrustedHtml(el, html) {
    el.innerHTML = html; // trusted: server-side OWASP-encoded responses only
}

function updateDiv() {
    if (updateNeeded) {
        var div = document.getElementById("reloadDiv").value;
        popLeftColumn(encounterConfig.urls.navSections[div], div, div);
        updateNeeded = false;
    }
    setTimeout(updateDiv, 1000);
}

function clickLoadDiv(e) {
    var data = Array.from(arguments);
    e.preventDefault();
    e.stopPropagation();
    data.shift();
    loadDiv(data[0], data[1]);
}

function loadDiv(div, url) {
    CarlosAjax.request(url, {
        method: 'post',
        evalScripts: true,
        onSuccess: function (request) {
            setTrustedHtml(document.getElementById(div), request.responseText);
        },
        onFailure: function (request) {
            setTrustedHtml(document.getElementById(div), "<h3>" + div + "<\/h3>Error: " + request.status);
        }
    });
    return false;
}

function popLeftColumn(url, div, params) {
    params = "cmd=" + params;
    CarlosAjax.request(url, {
        method: 'post',
        postBody: params,
        evalScripts: true,
        onSuccess: function (request) {
            var el = document.getElementById(div);
            while (el.firstChild)
                el.removeChild(el.firstChild);
            setTrustedHtml(el, request.responseText);
            listDisplay(params);
        },
        onFailure: function (request) {
            setTrustedHtml(document.getElementById(div), "<h3>Error:<\/h3>" + request.status);
        }
    });
}

// ============================================================
// List Display (expand/collapse)
// ============================================================
var imgfunc = {};
var obj = {};

function listDisplay(Id, threshold) {
    if (threshold == 0) return;
    var listId = Id + "list";
    var list = document.getElementById(listId);
    var items = list.getElementsByTagName('li');
    items = Array.from(items);
    var topName = "img" + Id + "0";
    var midName = "img" + Id + (threshold - 1);
    var lastName = "img" + Id + (items.length - 1);
    var topImage = document.getElementById(topName);
    var midImage = document.getElementById(midName);
    var lastImage = document.getElementById(lastName);
    var expand;

    for (var idx = threshold; idx < items.length; ++idx) {
        if (items[idx].style.display == 'block') {
            items[idx].style.display = 'none';
            expand = true;
        } else {
            items[idx].style.display = 'block';
            expand = false;
        }
    }

    if (expand) {
        topImage.src = encounterConfig.images.transparent;
        lastImage.src = encounterConfig.images.transparent;
        midImage.src = encounterConfig.images.expand;
        midImage.title = (items.length - threshold) + " items more";
        if (imgfunc[topName]) topImage.removeEventListener("click", imgfunc[topName]);
        if (imgfunc[lastName]) lastImage.removeEventListener("click", imgfunc[lastName]);
        imgfunc[midName] = function(event) { return clickListDisplay.call(obj, event, Id, threshold); };
        midImage.addEventListener("click", imgfunc[midName]);
    } else {
        topImage.src = encounterConfig.images.collapse;
        lastImage.src = encounterConfig.images.collapse;
        midImage.src = encounterConfig.images.transparent;
        midImage.title = "";
        if (imgfunc[midName]) midImage.removeEventListener("click", imgfunc[midName]);
        imgfunc[topName] = function(event) { return clickListDisplay.call(obj, event, Id, threshold); };
        topImage.addEventListener("click", imgfunc[topName]);
        imgfunc[lastName] = function(event) { return clickListDisplay.call(obj, event, Id, threshold); };
        lastImage.addEventListener("click", imgfunc[lastName]);
    }
}

function clickListDisplay(e) {
    var data = Array.from(arguments);
    data.shift();
    listDisplay(data[0], data[1]);
}

// ============================================================
// Navbar Loader
// ============================================================
function navBarLoader() {
    var rightNavBar = document.getElementById("rightNavBar");
    if (rightNavBar != undefined) {
        this.maxRightNumLines = Math.floor(rightNavBar.offsetHeight / 14);
    } else {
        this.rightNumLines = 0;
    }
    this.maxLeftNumLines = Math.floor(document.getElementById("leftNavbar").offsetHeight / 14);
    this.arrLeftDivs = [];
    this.arrRightDivs = [];
    this.rightTotal = 0;
    this.leftTotal = 0;
    this.leftDivs = 10;
    this.rightDivs = 3;
    this.leftReported = 0;
    this.rightReported = 0;

    this.load = function () {
        var leftNavbar = encounterConfig.urls.leftNavbar;
        var URLs = [leftNavbar];
        for (var j = 0; j < URLs.length; ++j) {
            var navbar = (j == 0) ? "leftNavbar" : "rightNavBar";
            for (var idx in URLs[j]) {
                if (!URLs[j].hasOwnProperty(idx)) continue;
                var div = document.createElement("div");
                div.className = "leftBox";
                div.style.display = "block";
                div.id = idx;
                document.getElementById(navbar).appendChild(div);
                if (navbar == "leftNavbar") this.arrLeftDivs.push(div);
                if (navbar == "rightNavBar") this.arrRightDivs.push(div);
                this.popColumn(URLs[j][idx], idx, idx, navbar, this);
            }
        }
    };

    this.popColumn = function (url, div, params, navBar) {
        params = "reloadURL=" + url + "&numToDisplay=6&cmd=" + params;
        CarlosAjax.request(url, {
            method: 'post',
            postBody: params,
            evalScripts: true,
            onSuccess: function (request) {
                var el = document.getElementById(div);
                while (el.firstChild)
                    el.removeChild(el.firstChild);
                setTrustedHtml(el, request.responseText);
            },
            onFailure: function (request) {
                setTrustedHtml(document.getElementById(div), "<h3>Error:<\/h3>" + request.status);
            }
        });
    };

    this.display = function (navBar, div) {
        var reported = 0;
        var numDivs = 0;
        if (navBar == "leftNavbar") {
            this.leftTotal += parseInt(document.getElementById(div + "num").value) + 1;
            reported = ++this.leftReported;
            numDivs = this.leftDivs;
        } else if (navBar == "rightNavBar") {
            this.rightTotal += parseInt(document.getElementById(div + "num").value) + 1;
            reported = ++this.rightReported;
            numDivs = this.rightDivs;
        }
        if (reported == numDivs) {
            var overflow = this.leftTotal - this.maxLeftNumLines;
            if (navBar == "leftNavbar" && overflow > 0)
                this.adjust(this.arrLeftDivs, this.leftTotal, overflow);
            overflow = this.rightTotal - this.maxRightNumLines;
            if (navBar == "rightNavBar" && overflow > 0)
                this.adjust(this.arrRightDivs, this.rightTotal, overflow);
        }
    };

    this.adjust = function (divs, total, overflow) {
        for (var idx = 0; idx < divs.length; ++idx) {
            var numLines = parseInt(document.getElementById(divs[idx].id + "num").value);
            var num2reduce = Math.ceil(overflow * (numLines / total));
            if (num2reduce == numLines && num2reduce > 0) --num2reduce;
            listDisplay(divs[idx].id, numLines - num2reduce);
        }
    };
}

// ============================================================
// Menus
// ============================================================
function showMenu(menuNumber, eventObj) {
    return showPopup('menu' + menuNumber, eventObj);
}

function hideAllMenus() {
    for (var counter = 1; counter <= numMenus; counter++) {
        changeObjectVisibility('menu' + counter, 'hidden');
        var menuTitle = getStyleObject('menuTitle' + counter);
        if (menuTitle) menuTitle.backgroundColor = '#000000';
    }
}

// ============================================================
// UI Helpers
// ============================================================
function showpic(picture, id) {
    if (!document.getElementById) return;
    var targetElement = document.getElementById(picture);
    var bal = document.getElementById(id);
    var offsetTrail = document.getElementById(id);
    var offsetLeft = 0;
    var offsetTop = 0;
    while (offsetTrail) {
        offsetLeft += offsetTrail.offsetLeft;
        offsetTop += offsetTrail.offsetTop;
        offsetTrail = offsetTrail.offsetParent;
    }
    if (navigator.userAgent.indexOf("Mac") != -1 &&
        typeof document.body.leftMargin != "undefined") {
        offsetLeft += document.body.leftMargin;
        offsetTop += document.body.topMargin;
    }
    targetElement.style.left = offsetLeft + bal.offsetWidth;
    targetElement.style.top = offsetTop;
    targetElement.style.visibility = 'visible';
}

function hidepic(picture) {
    if (document.getElementById) {
        document.getElementById(picture).style.visibility = 'hidden';
    }
}

function grabEnter(event) {
    var keyCode = (window.event) ? window.event.keyCode : (event ? event.which : 0);
    if (keyCode == 13) {
        popupPage(600, 800, encounterConfig.i18n.popupSearchPageWindow,
            document.forms['ksearch'].channel.options[document.forms['ksearch'].channel.selectedIndex].value +
            urlencode(document.forms['ksearch'].keyword.value));
        return false;
    }
}

function grabEnterGetTemplate(event) {
    var keyCode = (window.event) ? window.event.keyCode : (event ? event.which : 0);
    if (keyCode == 13) return false;
}

function getActiveText() {
    if (document.all) {
        var text = document.selection.createRange().text;
        if (text != "" && document.ksearch.keyword.value == "")
            document.ksearch.keyword.value += text;
        if (text != "" && document.ksearch.keyword.value != "")
            document.ksearch.keyword.value = text;
    } else {
        var text2 = window.getSelection();
        if (text2.toString().length == 0) {
            var txtarea = document.encForm.enTextarea;
            var selStart = txtarea.selectionStart;
            var selEnd = txtarea.selectionEnd;
            if (selEnd == 1 || selEnd == 2) selEnd = txtarea.textLength;
            text2 = (txtarea.value).substring(selStart, selEnd);
        }
        document.ksearch.keyword.value = text2;
    }
    return true;
}

// ============================================================
// Template Autocompleter (vanilla JS dropdown)
// ============================================================

/**
 * Handles template/calculator selection from the autocompleter.
 * Actions are dispatched by name from the autoCompleted map,
 * which contains only trusted server-defined entries (calculator
 * configs and encounter template names).
 */
function menuAction() {
    var name = document.getElementById('enTemplate').value;
    var action = autoCompleted[name];
    if (!action) return;

    // Template actions are always ajaxInsertTemplate calls
    if (action.indexOf("ajaxInsertTemplate(") === 0) {
        var tmplName = action.replace("ajaxInsertTemplate('", "").replace("')", "");
        ajaxInsertTemplate(tmplName);
    } else if (action.indexOf("popupPage(") === 0) {
        // Calculator actions are popupPage calls - parse the arguments
        var argsStr = action.substring(action.indexOf("(") + 1, action.lastIndexOf(")"));
        var args = [];
        var current = "";
        var inQuote = false;
        var quoteChar = "";
        for (var ci = 0; ci < argsStr.length; ci++) {
            var ch = argsStr.charAt(ci);
            if (!inQuote && (ch === "'" || ch === '"')) {
                inQuote = true;
                quoteChar = ch;
            } else if (inQuote && ch === quoteChar) {
                inQuote = false;
            } else if (!inQuote && ch === ",") {
                args.push(current.trim());
                current = "";
                continue;
            } else {
                current += ch;
            }
        }
        args.push(current.trim());
        if (args.length >= 4) {
            popupPage(parseInt(args[0]), parseInt(args[1]), args[2], args[3]);
        }
    }
}

/**
 * Initializes a vanilla JS autocomplete dropdown for encounter templates
 * and calculators. Replaces the Scriptaculous Autocompleter.Local.
 * Must be called after DOM elements 'enTemplate' and 'enTemplate_list' exist.
 */
function initTemplateAutocompleter() {
    var input = document.getElementById('enTemplate');
    var listEl = document.getElementById('enTemplate_list');
    if (!input || !listEl) return;

    // Style the dropdown container for autocomplete behavior
    listEl.style.position = 'absolute';
    listEl.style.zIndex = '1000';
    listEl.style.display = 'none';
    listEl.style.maxHeight = '200px';
    listEl.style.overflowY = 'auto';
    listEl.style.backgroundColor = '#fff';
    listEl.style.border = '1px solid #ccc';

    input.addEventListener('input', function () {
        var term = this.value.toLowerCase();
        listEl.textContent = '';

        if (!term) {
            listEl.style.display = 'none';
            return;
        }

        var matches = autoCompList.filter(function (name) {
            return name.toLowerCase().indexOf(term) !== -1;
        });

        if (matches.length === 0) {
            listEl.style.display = 'none';
            return;
        }

        matches.forEach(function (name) {
            var item = document.createElement('div');
            item.className = 'dropdown-item';
            item.style.padding = '4px 8px';
            item.style.cursor = 'pointer';
            if (itemColours[name]) {
                item.style.borderLeft = '4px solid #' + itemColours[name];
            }
            item.textContent = name;
            item.addEventListener('mousedown', function (e) {
                // Use mousedown instead of click to fire before input blur
                e.preventDefault();
                input.value = name;
                listEl.textContent = '';
                listEl.style.display = 'none';
                menuAction();
            });
            item.addEventListener('mouseover', function () {
                this.style.backgroundColor = '#f0f0f0';
            });
            item.addEventListener('mouseout', function () {
                this.style.backgroundColor = '';
            });
            listEl.appendChild(item);
        });

        listEl.style.display = 'block';
    });

    input.addEventListener('blur', function () {
        // Delay hide to allow mousedown on items to fire first
        setTimeout(function () {
            listEl.style.display = 'none';
        }, 200);
    });

    input.addEventListener('keydown', function (e) {
        if (e.keyCode === 13) {
            // Enter key — select first visible match or trigger menuAction
            var firstItem = listEl.querySelector('.dropdown-item');
            if (firstItem && listEl.style.display !== 'none') {
                e.preventDefault();
                input.value = firstItem.textContent;
                listEl.textContent = '';
                listEl.style.display = 'none';
                menuAction();
            }
        } else if (e.keyCode === 27) {
            // Escape key — close dropdown
            listEl.textContent = '';
            listEl.style.display = 'none';
        }
    });
}

// ============================================================
// Page Lifecycle
// ============================================================
function loader() {
    window.focus();
    document.encForm.enTextarea.focus();
    document.encForm.enTextarea.value = document.encForm.enTextarea.value + "";
    document.encForm.enTextarea.scrollTop = document.encForm.enTextarea.scrollHeight;

    if (encounterConfig.popUrl) {
        window.setTimeout(function () { popupPage(700, 900, 'popUrl', encounterConfig.popUrl); }, 2);
    }

    initTemplateAutocompleter();

    var navBars = new navBarLoader();
    navBars.load();
}

// ============================================================
// Event Listeners
// ============================================================
document.onmouseup = getActiveText;
document.onclick = hideAllMenus;

window.addEventListener('load', loader);
window.addEventListener('beforeunload', onClosing);

initAutoComplete();
setTimeout(updateDiv, 1000);
