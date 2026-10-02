/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the packaged WAF's exemptions for the two report-authoring tools whose input is SQL
 * (exclusion ids 1400-1409 in the BEFORE-CRS file, carlos-emr/carlos#4133).
 *
 * <p>These rules are narrower than the clinical-prose ones on purpose: each unhooks only the
 * signature family the input is made of (SQLi; and XSS for the template document, which is
 * markup) from one named argument on one POST route. The table is the contract: a target, tag
 * or route that appears in the file without a row here fails the build, and so does a row the
 * file lost.</p>
 *
 * @since 2026-10-01
 */
@Tag("unit")
@Tag("security")
class ReportAuthoringWafExclusionRegressionTest {

    private static final Path EXCLUSIONS = resolveProjectPath(
            Path.of("debian", "assets", "modsecurity", "REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf"));

    /** A rule id in the report-authoring block's reserved range. */
    private static final Pattern RULE_ID = Pattern.compile("\\bid:(140\\d)\\b");

    /** Every ctl action in a rule, whatever its kind (ruleEngine, ruleRemoveById, ...). */
    private static final Pattern CTL_ACTION = Pattern.compile("ctl:([^,\"\\\\\\s]+)");

    /** id, route, exempted {@code tag;argument} pairs (exactly), phase. */
    static Stream<Arguments> reportAuthoringRules() {
        return Stream.of(
                Arguments.of("1400", "/carlos/oscarReport/RptByExample",
                        List.of("attack-sqli;sql"), 1),
                Arguments.of("1401", "/carlos/oscarReport/RptByExamplesFavorite",
                        List.of("attack-sqli;query", "attack-sqli;newQuery"), 1),
                Arguments.of("1402", "/carlos/oscarReport/reportByTemplate/addEditTemplatesAction",
                        List.of("attack-sqli;xmltext", "attack-xss;xmltext"), 2));
    }

    @ParameterizedTest(name = "rule {0}: POST {1}")
    @MethodSource("reportAuthoringRules")
    @DisplayName("each report-authoring rule should exempt exactly its SQL argument on its own POST route")
    void shouldExemptOnlyTheSqlArgument_forReportAuthoringRoute(String ruleId, String route,
                                                                  List<String> targets, int phase)
            throws IOException {
        String rule = readRule(ruleId);

        assertThat(rule)
                .contains("SecRule REQUEST_URI \"@rx ^" + route + "(?:[;?]|$)\"")
                .contains("\"id:" + ruleId + ",phase:" + phase + ",pass,nolog,chain\"")
                .contains("SecRule REQUEST_METHOD \"@streq POST\"")
                .doesNotContain("ruleEngine");

        // Per-argument tag removal only, and exactly the listed pairs: any other ctl action
        // (ruleEngine=Off, ruleRemoveById, ruleRemoveTargetById, ...) fails here.
        List<String> found = new ArrayList<>();
        Matcher matcher = CTL_ACTION.matcher(rule);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        assertThat(found).as("rule %s carries exactly the listed ctl target removals", ruleId)
                .containsExactlyInAnyOrderElementsOf(targets.stream()
                        .map(pair -> "ruleRemoveTargetByTag=" + pair.replace(";", ";ARGS:"))
                        .toList());
    }

    @Test
    @DisplayName("the template-editor rule should apply only to the add and edit operations")
    void shouldChainOnAddOrEdit_forTemplateEditorRule() throws IOException {
        // ManageTemplates2Action reads xmltext only for action=add|edit; delete and the bare
        // page ignore it, so the exemption must not reach them. A body-argument key needs phase 2.
        String rule = readRule("1402");
        assertThat(rule).contains("SecRule ARGS:action \"@rx ^(?:add|edit)$\"");
        assertThat(rule.indexOf("SecRule ARGS:action"))
                .as("the operation match precedes every target removal")
                .isLessThan(rule.indexOf("ctl:ruleRemoveTargetByTag"));
    }

    @Test
    @DisplayName("the report-authoring block should carry no rule beyond the table")
    void shouldMatchTable_forReportAuthoringRuleIds() throws IOException {
        // Every id in the block's 1400-1409 range on a non-comment line, whatever its phase or
        // actions, so an extra rule cannot hide behind a different action string.
        List<String> idsInFile = new ArrayList<>();
        for (String line : read().split("\n")) {
            if (line.stripLeading().startsWith("#")) {
                continue;
            }
            Matcher id = RULE_ID.matcher(line);
            while (id.find()) {
                idsInFile.add(id.group(1));
            }
        }
        assertThat(idsInFile).containsExactlyInAnyOrderElementsOf(
                reportAuthoringRules().map(arguments -> (String) arguments.get()[0]).toList());
    }

    @Test
    @DisplayName("the report-authoring block should sit before the clinical-prose survey block")
    void shouldPrecedeClinicalProseBlock_forXssPolicy() throws IOException {
        // ClinicalProseWafExclusionRegressionTest asserts that nothing from the clinical-prose
        // heading to the end of the file removes attack-xss. 1402 must remove it (the template
        // document is markup), so this block has to stay above that heading.
        String exclusions = read();
        assertThat(exclusions.indexOf("id:1402,"))
                .isGreaterThan(0)
                .isLessThan(exclusions.indexOf("Clinician free text on the rest of the application"));
    }

    @Test
    @DisplayName("Messenger Doc2PDF should need no exclusion because it no longer receives page HTML")
    void shouldNotExemptDoc2Pdf_forMessengerAttachments() throws IOException {
        // The attachment chooser posts item keys and the server renders the pages itself, so the
        // route must stay fully inspected; an exclusion for srcText would reopen #4133's flaw.
        assertThat(read()).doesNotContain("messenger/Doc2PDF").doesNotContain("ARGS:srcText");
    }

    private static String readRule(String ruleId) throws IOException {
        String exclusions = read();
        int idPosition = exclusions.indexOf("\"id:" + ruleId + ",");
        assertThat(idPosition).as("exclusion %s is present", ruleId).isGreaterThanOrEqualTo(0);
        int ruleStart = exclusions.lastIndexOf("SecRule REQUEST_URI", idPosition);
        assertThat(ruleStart).as("exclusion %s starts with a REQUEST_URI match", ruleId).isGreaterThanOrEqualTo(0);
        int ruleEnd = exclusions.indexOf("\n\n", idPosition);
        String rule = exclusions.substring(ruleStart, ruleEnd < 0 ? exclusions.length() : ruleEnd);
        // The slice must be this rule alone: a second REQUEST_URI inside it would mean the
        // blank-line boundary was lost and another rule's actions are being read as this one's.
        assertThat(rule.indexOf("SecRule REQUEST_URI", 1)).as("exclusion %s is read on its own", ruleId).isLessThan(0);
        assertThat(rule).as("exclusion %s is chained to a method match", ruleId).contains("SecRule REQUEST_METHOD");
        // One chain, end to end: every link but the last says chain, and no second id starts a
        // detached rule whose actions would apply without the route and POST conditions.
        String[] links = rule.split("SecRule ", -1);
        for (int i = 1; i < links.length - 1; i++) {
            assertThat(links[i]).as("exclusion %s link %d chains to the next", ruleId, i).contains("chain");
        }
        assertThat(rule.split("\"id:", -1)).as("exclusion %s carries a single rule id", ruleId).hasSize(2);
        assertThat(links[links.length - 1]).as("exclusion %s ends with the ctl actions", ruleId)
                .contains("ctl:ruleRemoveTargetByTag");
        return rule;
    }

    private static String read() throws IOException {
        return Files.readString(EXCLUSIONS, StandardCharsets.UTF_8).replace("\r\n", "\n");
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
        throw new IllegalStateException("Could not locate " + relativePath + " from " + System.getProperty("user.dir"));
    }
}
