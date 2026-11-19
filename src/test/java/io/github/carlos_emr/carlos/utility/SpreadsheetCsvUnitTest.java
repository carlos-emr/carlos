/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Spreadsheet CSV cell policy")
class SpreadsheetCsvUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+SUM(A1)", "-cmd", "@SUM(A1)", "  =1+1",
            " \t@SUM(A1)", "\tname", "\rname", "\nname", "\r\n=1+1",
            "\u0000=1+1", "\u001B+1", "\u00A0=1+1", "\u2003@SUM(A1)", "\uFEFF=1+1",
            "＝1+1", "＋SUM(A1)", "－cmd", "＠SUM(A1)", " \uFEFF＝1+1",
            "-", "-.", "-e2", "-1e", "-1E+", "-1e2+3", "--12", "-12.5.6", "-NaN", "-Infinity",
            "\t-12.5", "\r-12.5", "\n-12.5",
            "=HYPERLINK(\"synthetic\",\"quoted,\r\nvalue\")"})
    void shouldGuardFormulaLikeText_withLeadingWhitespaceOrControls(String text) {
        assertThat(SpreadsheetCsv.sanitizeCell(text)).isEqualTo("\t" + text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Zoë", "O'Neil", "a,b", "a\"b", "a\r\nb", "value=1", "42",
            "-12", "-12.50", "-0", "-.5", "-1.", "-1.2E-7", "-2e+10", "  ordinary text",
            "  -12.50", "\u00A0-12.50", "\u2003-1.2E-7", "\uFEFF-12.50"})
    void shouldPreserveTextAndNumericSemantics_whenCellIsSafe(String text) {
        assertThat(SpreadsheetCsv.sanitizeCell(text)).isEqualTo(text);
    }

    @ParameterizedTest
    @NullSource
    void shouldRepresentNullAsEmpty_forMissingCell(String text) {
        assertThat(SpreadsheetCsv.sanitizeCell(text)).isEmpty();
    }

    /**
     * Issue #5846: {@code js/table-export.js} applies the same policy in the browser, and
     * {@code scripts/table-export.test.js} reads the same fixture, so the two cannot drift apart.
     */
    @Test
    void shouldDecideEverySharedFixtureCase_likeTheBrowserExport() throws IOException {
        JsonNode cases = new ObjectMapper().readTree(Files.readString(
                resolveProjectPath(Path.of("scripts", "fixtures", "spreadsheet-csv-cell-cases.json")),
                StandardCharsets.UTF_8)).get("cases");
        assertThat(cases).isNotEmpty();
        for (JsonNode testCase : cases) {
            String input = testCase.get("input").asText();
            assertThat(SpreadsheetCsv.sanitizeCell(input))
                    .as("fixture input %s", input)
                    .isEqualTo(testCase.get("expected").asText());
        }
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir"))).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return relativePath;
    }

    @Test
    void shouldHandleLongNumericAndFormulaLikeText_withoutBacktracking() {
        String amount = "-" + "1".repeat(100_000) + ".5E-7";
        String expression = amount + "+1";
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(2), () -> {
            assertThat(SpreadsheetCsv.sanitizeCell(amount)).isEqualTo(amount);
            assertThat(SpreadsheetCsv.sanitizeCell(expression)).isEqualTo("\t" + expression);
        });
    }
}
