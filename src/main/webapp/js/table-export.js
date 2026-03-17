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

    // Characters that trigger formula execution in Excel/LibreOffice when they
    // appear at the start of a cell value (CSV injection / formula injection).
    var FORMULA_PREFIX_RE = /^[=+\-@\t\r]/;

    function escapeCsvCell(text) {
        // Guard against formula injection: prefix with a tab so spreadsheet
        // applications treat the cell as a text value, not a formula.
        if (FORMULA_PREFIX_RE.test(text)) {
            text = "\t" + text;
        }
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

            var output = "";
            for (var r = 0; r < table.rows.length; r++) {
                var row = table.rows[r];
                for (var c = 0; c < row.cells.length; c++) {
                    if (c > 0) output += CSV_SEP;
                    output += escapeCsvCell(row.cells[c].textContent.trim());
                }
                output += CSV_NEWLINE;
            }

            anchor.href = "data:application/csv;base64," + toBase64(output);
            return true;
        }
    };
})();
