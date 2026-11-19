/*
 * Lightweight HTML table export utility for CARLOS EMR.
 * Drop-in replacement for ExcellentExport — same API, no dependencies.
 *
 * Usage:
 *   <a download="file.xls" href="#"
 *      onclick="return TableExport.excel(this, 'tableId', 'Sheet Name');">Excel</a>
 *   <a download="file.csv" href="#"
 *      onclick="return TableExport.csv(this, 'tableId');">CSV</a>
 */
var TableExport = (function () {
    "use strict";

    var CSV_SEP = ",";
    var CSV_NEWLINE = "\r\n";

    var excelTemplate =
        '<html xmlns:o="urn:schemas-microsoft-com:office:office"' +
        ' xmlns:x="urn:schemas-microsoft-com:office:excel"' +
        ' xmlns="http://www.w3.org/TR/REC-html40">' +
        "<head>" +
        '<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">' +
        "<!--[if gte mso 9]><xml><x:ExcelWorkbook><x:ExcelWorksheets>" +
        "<x:ExcelWorksheet><x:Name>{worksheet}</x:Name>" +
        "<x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions>" +
        "</x:ExcelWorksheet></x:ExcelWorksheets></x:ExcelWorkbook></xml><![endif]-->" +
        "</head><body><table>{table}</table></body></html>";

    function toBase64(str) {
        return btoa(unescape(encodeURIComponent(str)));
    }

    // Spreadsheet formula guard (issue #5846). This mirrors the server's cell policy,
    // io.github.carlos_emr.carlos.utility.SpreadsheetCsv.sanitizeCell, so a CSV built in
    // the browser guards exactly the cells a server-built CSV guards:
    //  - an initial tab, CR or LF is always guarded;
    //  - leading whitespace, Unicode spaces, control characters and a BOM cannot hide a
    //    formula marker (= + - @ and their full-width forms U+FF1D U+FF0B U+FF0D U+FF20);
    //  - a plain negative number (decimal or scientific) stays numeric.
    // A guarded cell gains a leading tab, which spreadsheet importers keep as text.
    // scripts/fixtures/spreadsheet-csv-cell-cases.json drives the tests on both sides.
    var SKIPPABLE_LEADING_RE = /[\s\u0000-\u0008\u000E-\u001F\u007F-\u009F]/;
    var FORMULA_MARKERS = "=+-@\uFF1D\uFF0B\uFF0D\uFF20";

    function skipDigits(text, start) {
        var cursor = start;
        while (cursor < text.length && text.charAt(cursor) >= "0" && text.charAt(cursor) <= "9") {
            cursor++;
        }
        return cursor;
    }

    // Linear scan (no regex backtracking): -12, -12.50, -.5, -1., -1.2E-7, -2e+10.
    function isNegativeNumber(text, minusIndex) {
        var start = minusIndex + 1;
        var cursor = skipDigits(text, start);
        var hasDigits = cursor > start;
        if (cursor < text.length && text.charAt(cursor) === ".") {
            start = cursor + 1;
            cursor = skipDigits(text, start);
            hasDigits = hasDigits || cursor > start;
        }
        if (!hasDigits) {
            return false;
        }
        if (cursor < text.length && (text.charAt(cursor) === "e" || text.charAt(cursor) === "E")) {
            cursor++;
            if (cursor < text.length && (text.charAt(cursor) === "+" || text.charAt(cursor) === "-")) {
                cursor++;
            }
            start = cursor;
            cursor = skipDigits(text, start);
            if (cursor === start) {
                return false;
            }
        }
        return cursor === text.length;
    }

    function sanitizeCell(text) {
        if (text === null || text === undefined || text === "") {
            return "";
        }
        var first = text.charAt(0);
        if (first === "\t" || first === "\r" || first === "\n") {
            return "\t" + text;
        }
        var index = 0;
        while (index < text.length && SKIPPABLE_LEADING_RE.test(text.charAt(index))) {
            index++;
        }
        if (index < text.length) {
            var marker = text.charAt(index);
            if (marker === "-" && isNegativeNumber(text, index)) {
                return text;
            }
            if (FORMULA_MARKERS.includes(marker)) {
                return "\t" + text;
            }
        }
        return text;
    }

    function escapeCsvCell(text) {
        text = sanitizeCell(text);
        var needsQuoting =
            text.indexOf(CSV_SEP) !== -1 ||
            text.indexOf("\r") !== -1 ||
            text.indexOf("\n") !== -1 ||
            text.indexOf('"') !== -1 ||
            text.charAt(0) === "\t"; // always quote tab-prefixed cells
        if (needsQuoting) {
            return '"' + text.replace(/"/g, '""') + '"';
        }
        return text;
    }

    return {
        /** The formula-guard cell policy, exposed for tests and other exporters. */
        sanitizeCell: sanitizeCell,

        excel: function (anchor, tableId, sheetName) {
            var table = document.getElementById(tableId);
            if (!table) return false;

            var html = excelTemplate
                .replace("{worksheet}", sheetName || "Worksheet")
                .replace("{table}", table.innerHTML);

            anchor.href = "data:application/vnd.ms-excel;base64," + toBase64(html);
            return true;
        },

        csv: function (anchor, tableId) {
            var table = document.getElementById(tableId);
            if (!table) return false;

            var output = "\uFEFF";
            for (var r = 0; r < table.rows.length; r++) {
                var row = table.rows[r];
                for (var c = 0; c < row.cells.length; c++) {
                    if (c > 0) output += CSV_SEP;
                    var cell = row.cells[c];
                    var text = cell.textContent;
                    // Preserve its initial control prefix for the spreadsheet cell guard.
                    var controlPrefix = text.match(/^[\t\r\n]+/);
                    text = text.trim();
                    if (cell.tagName === "TH") text = text.replace(/\s+/g, " ");
                    output += escapeCsvCell(controlPrefix ? controlPrefix[0] + text : text);
                }
                output += CSV_NEWLINE;
            }

            anchor.href = "data:application/csv;base64," + toBase64(output);
            return true;
        }
    };
})();
