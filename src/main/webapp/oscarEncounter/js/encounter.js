/**
 * CARLOS EMR - Encounter Page JavaScript
 *
 * Consolidates inline scripts from the legacy Index2.jsp encounter page.
 * All server-side values are passed via the encounterConfig object defined
 * in encounter-head.jspf.
 *
 * Dependencies:
 *   - Prototype.js (Ajax.Request, $(), $A(), $F(), Event, Element)
 *   - Scriptaculous (Autocompleter.Local)
 *   - encounterConfig (inline JSP config block)
 *
 * Security note: AJAX responses that use $(div).update() are from trusted
 * internal server endpoints only. XSS is mitigated by server-side OWASP
 * encoding in those response JSPs.
 */

/* global encounterConfig, $, $A, $F, Ajax, Element, Event, Autocompleter,
          changeObjectVisibility, getStyleObject, showPopup */

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
        new Ajax.Request(page, {
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
    var ns = (navigator.appName == "Netscape") ? 1 : 0;
    if (ns) return escape(str);
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
// (uses Prototype.js Ajax.Request and $().update() for trusted
// internal server responses — server-side OWASP encoding applied)
// ============================================================
function updateDiv() {
    if (updateNeeded) {
        var div = $F("reloadDiv");
        popLeftColumn(encounterConfig.urls.navSections[div], div, div);
        updateNeeded = false;
    }
    setTimeout(updateDiv, 1000);
}

function clickLoadDiv(e) {
    var data = $A(arguments);
    Event.stop(e);
    data.shift();
    loadDiv(data[0], data[1]);
}

function loadDiv(div, url) {
    new Ajax.Request(url, {
        method: 'post',
        evalScripts: true,
        onSuccess: function (request) {
            $(div).update(request.responseText);
        },
        onFailure: function (request) {
            $(div).update("<h3>" + div + "<\/h3>Error: " + request.status);
        }
    });
    return false;
}

function popLeftColumn(url, div, params) {
    params = "cmd=" + params;
    new Ajax.Request(url, {
        method: 'post',
        postBody: params,
        evalScripts: true,
        onSuccess: function (request) {
            while ($(div).firstChild)
                $(div).removeChild($(div).firstChild);
            $(div).update(request.responseText);
            listDisplay(params);
        },
        onFailure: function (request) {
            $(div).update("<h3>Error:<\/h3>" + request.status);
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
    var list = $(listId);
    var items = list.getElementsByTagName('li');
    items = $A(items);
    var topName = "img" + Id + "0";
    var midName = "img" + Id + (threshold - 1);
    var lastName = "img" + Id + (items.length - 1);
    var topImage = $(topName);
    var midImage = $(midName);
    var lastImage = $(lastName);
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
        Element.stopObserving(topImage, "click", imgfunc[topName]);
        Element.stopObserving(lastImage, "click", imgfunc[lastName]);
        imgfunc[midName] = clickListDisplay.bindAsEventListener(obj, Id, threshold);
        Element.observe(midImage, "click", imgfunc[midName]);
    } else {
        topImage.src = encounterConfig.images.collapse;
        lastImage.src = encounterConfig.images.collapse;
        midImage.src = encounterConfig.images.transparent;
        midImage.title = "";
        Element.stopObserving(midImage, "click", imgfunc[midName]);
        imgfunc[topName] = clickListDisplay.bindAsEventListener(obj, Id, threshold);
        Element.observe(topImage, "click", imgfunc[topName]);
        imgfunc[lastName] = clickListDisplay.bindAsEventListener(obj, Id, threshold);
        Element.observe(lastImage, "click", imgfunc[lastName]);
    }
}

function clickListDisplay(e) {
    var data = $A(arguments);
    data.shift();
    listDisplay(data[0], data[1]);
}

// ============================================================
// Navbar Loader
// ============================================================
function navBarLoader() {
    if ($("rightNavBar") != undefined) {
        this.maxRightNumLines = Math.floor($("rightNavBar").getHeight() / 14);
    } else {
        this.rightNumLines = 0;
    }
    this.maxLeftNumLines = Math.floor($("leftNavbar").getHeight() / 14);
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
                $(navbar).appendChild(div);
                if (navbar == "leftNavbar") this.arrLeftDivs.push(div);
                if (navbar == "rightNavBar") this.arrRightDivs.push(div);
                this.popColumn(URLs[j][idx], idx, idx, navbar, this);
            }
        }
    };

    this.popColumn = function (url, div, params, navBar) {
        params = "reloadURL=" + url + "&numToDisplay=6&cmd=" + params;
        new Ajax.Request(url, {
            method: 'post',
            postBody: params,
            evalScripts: true,
            onSuccess: function (request) {
                while ($(div).firstChild)
                    $(div).removeChild($(div).firstChild);
                $(div).update(request.responseText);
            },
            onFailure: function (request) {
                $(div).update("<h3>Error:<\/h3>" + request.status);
            }
        });
    };

    this.display = function (navBar, div) {
        var reported = 0;
        var numDivs = 0;
        if (navBar == "leftNavbar") {
            this.leftTotal += parseInt($F(div + "num")) + 1;
            reported = ++this.leftReported;
            numDivs = this.leftDivs;
        } else if (navBar == "rightNavBar") {
            this.rightTotal += parseInt($F(div + "num")) + 1;
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
            var numLines = parseInt($F(divs[idx].id + "num"));
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
// Template Autocompleter (Scriptaculous)
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
 * Initializes the Scriptaculous Autocompleter for encounter templates.
 * Must be called after DOM elements 'enTemplate' and 'enTemplate_list' exist.
 */
function initTemplateAutocompleter() {
    var input = document.getElementById('enTemplate');
    var list = document.getElementById('enTemplate_list');
    if (input && list && typeof Autocompleter !== 'undefined') {
        new Autocompleter.Local('enTemplate', 'enTemplate_list', autoCompList, {
            colours: itemColours,
            afterUpdateElement: menuAction
        });
    }
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
        window.setTimeout(function () { popupPage(700, 900, encounterConfig.popUrl); }, 2);
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
