/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the packaged WAF's eForm signature exclusion (1040 in the BEFORE-CRS file) and the argument
 * name the page script, the save action and the rule must share (#3552).
 *
 * <p>1040 used to remove CRS 941130/941170 for the whole {@code /eform/addEForm} POST, because the
 * signature pads' data URIs sat in author-named fields. That also stopped those two rules scoring
 * every other field: a {@code data:text/html;base64} value in an ordinary field reached the
 * application unscored (reproduced on a packaged 2026.08 install). The page now posts those values
 * in {@link EFormDataUriCarrier#PARAMETER}, so the rule unhooks that one argument only. This test
 * pins the shape; {@code scripts/waf/check-clinical-policy.py} ({@code data-uri-*} cases) pins the
 * behaviour against the real engine.</p>
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("security")
@Tag("eform")
@DisplayName("eForm signature data-URI WAF exclusion")
class EformDataUriCarrierWafExclusionRegressionTest {

    private static final Path EXCLUSIONS = resolveProjectPath(
            Path.of("debian", "assets", "modsecurity", "REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf"));
    private static final Path TOOLBAR_JS = resolveProjectPath(
            Path.of("src", "main", "webapp", "eform", "eformFloatingToolbar", "eform_floating_toolbar.js"));
    private static final Path ACTION = resolveProjectPath(Path.of("src", "main", "java", "io", "github",
            "carlos_emr", "carlos", "eform", "actions", "AddEForm2Action.java"));

    /** Every ctl action in a rule, whatever its kind (ruleEngine, ruleRemoveById, ...). */
    private static final Pattern CTL_ACTION = Pattern.compile("ctl:([^,\"\\\\\\s]+)");

    @Test
    @DisplayName("should unhook only the carried argument, only on POST to the eForm save route")
    void shouldExemptOnlyCarrier_forEFormSaveRoute() throws IOException {
        String rule = readRule("1040");

        assertThat(rule)
                .contains("SecRule REQUEST_URI \"@rx ^/carlos/eform/addEForm(?:[;?]|$)\"")
                .contains("\"id:1040,phase:1,pass,nolog,chain\"")
                .contains("SecRule REQUEST_METHOD \"@streq POST\"");

        // A request-wide ruleRemoveById (the former shape), another argument or another family
        // fails here.
        List<String> found = new ArrayList<>();
        Matcher matcher = CTL_ACTION.matcher(rule);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        assertThat(found).containsExactlyInAnyOrder(
                "ruleRemoveTargetByTag=attack-sqli;ARGS:carlos-data-uri",
                "ruleRemoveTargetByTag=attack-xss;ARGS:carlos-data-uri",
                "ruleRemoveTargetByTag=attack-rce;ARGS:carlos-data-uri");
    }

    @Test
    @DisplayName("should never remove 941130 or 941170 for a whole request")
    void shouldNotRemoveDataUriRulesRequestWide_anywhere() throws IOException {
        String active = activeLines(Files.readString(EXCLUSIONS, StandardCharsets.UTF_8));
        assertThat(active).doesNotContain("ruleRemoveById=941130").doesNotContain("ruleRemoveById=941170");
    }

    @Test
    @DisplayName("should name the argument the page posts and the action restores")
    void shouldMatchArgumentName_betweenToolbarActionAndRule() throws IOException {
        // If any side renames the argument the rule silently stops applying and every signature
        // save is refused again (or the action stops restoring the signature).
        assertThat(EFormDataUriCarrier.PARAMETER).isEqualTo("carlos-data-uri");
        assertThat(EFormDataUriCarrier.REFERENCE_PREFIX).isEqualTo(EFormDataUriCarrier.PARAMETER + ":");
        String toolbar = Files.readString(TOOLBAR_JS, StandardCharsets.UTF_8);
        assertThat(toolbar)
                .contains("const PARAMETER = \"carlos-data-uri\";")
                .contains("const REFERENCE_PREFIX = \"carlos-data-uri:\";")
                .contains("const MAX_VALUES = " + EFormDataUriCarrier.MAX_VALUES + ";")
                .contains("const MAX_PAYLOAD_CHARS = " + EFormDataUriCarrier.MAX_BASE64_CHARS + ";");
        String action = Files.readString(ACTION, StandardCharsets.UTF_8);
        assertThat(action)
                .contains("request.getParameterValues(EFormDataUriCarrier.PARAMETER)")
                .contains("EFormDataUriCarrier.restore(paramValues, carriedDataUris)");
    }

    private static String activeLines(String text) {
        StringBuilder active = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n")) {
            if (!line.stripLeading().startsWith("#")) {
                active.append(line).append('\n');
            }
        }
        return active.toString();
    }

    private static String readRule(String ruleId) throws IOException {
        String exclusions = Files.readString(EXCLUSIONS, StandardCharsets.UTF_8).replace("\r\n", "\n");
        int idPosition = exclusions.indexOf("\"id:" + ruleId + ",");
        assertThat(idPosition).as("exclusion %s is present", ruleId).isGreaterThanOrEqualTo(0);
        int ruleStart = exclusions.lastIndexOf("SecRule REQUEST_URI", idPosition);
        assertThat(ruleStart).as("exclusion %s starts with a REQUEST_URI match", ruleId).isGreaterThanOrEqualTo(0);
        int ruleEnd = exclusions.indexOf("\n\n", idPosition);
        String rule = exclusions.substring(ruleStart, ruleEnd < 0 ? exclusions.length() : ruleEnd);
        // One rule, one chain: a second REQUEST_URI or id would mean another rule's actions are
        // being read as this one's, or that the ctl actions apply without the route and POST match.
        assertThat(rule.indexOf("SecRule REQUEST_URI", 1)).as("exclusion %s is read on its own", ruleId).isLessThan(0);
        assertThat(rule.split("\"id:", -1)).as("exclusion %s carries a single rule id", ruleId).hasSize(2);
        String[] links = rule.split("SecRule ", -1);
        for (int i = 1; i < links.length - 1; i++) {
            assertThat(links[i]).as("exclusion %s link %d chains to the next", ruleId, i).contains("chain");
        }
        assertThat(links[links.length - 1]).as("exclusion %s ends with the ctl actions", ruleId)
                .contains("ctl:ruleRemoveTargetByTag");
        return rule;
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
}
