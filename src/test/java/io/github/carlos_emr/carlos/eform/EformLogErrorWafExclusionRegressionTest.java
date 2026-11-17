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
 * Pins the packaged WAF's exemption for the eForm script-error report (exclusion 1075 in the
 * BEFORE-CRS file) and the client/server contract it depends on.
 *
 * <p>Verified on a packaged Ubuntu 26.04 install: the OSCAR Galaxy HPV-Cytology eForm throws
 * {@code Uncaught TypeError: $(...).error is not a function} when it opens, the floating toolbar's
 * {@code window.onerror} posts that message to {@code /eform/logEformError}, and CRS 932130 read
 * Chromium's {@code $(...)} callee abbreviation as shell command substitution, so the report was
 * answered 403 and the broken form was never flagged unstable. The real-engine harness
 * ({@code scripts/waf/check-clinical-policy.py}, {@code eform-error-*} cases) measured the families
 * that fire on ordinary browser messages: attack-rce, attack-xss and attack-sqli. This test pins
 * the shape of the rule; the harness pins its behaviour.</p>
 *
 * @since 2026-10-19
 */
@Tag("unit")
@Tag("security")
@Tag("eform")
@DisplayName("eForm script-error report WAF exclusion")
class EformLogErrorWafExclusionRegressionTest {

    private static final Path EXCLUSIONS = resolveProjectPath(
            Path.of("debian", "assets", "modsecurity", "REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf"));
    private static final Path TOOLBAR_JS = resolveProjectPath(
            Path.of("src", "main", "webapp", "eform", "eformFloatingToolbar", "eform_floating_toolbar.js"));
    private static final Path ACTION = resolveProjectPath(
            Path.of("src", "main", "java", "io", "github", "carlos_emr", "carlos", "eform", "EformLogError2Action.java"));

    /** Every ctl action in a rule, whatever its kind (ruleEngine, ruleRemoveById, ...). */
    private static final Pattern CTL_ACTION = Pattern.compile("ctl:([^,\"\\\\\\s]+)");

    @Test
    @DisplayName("should exempt only the error message, only from the measured families, only on POST to the report route")
    void shouldExemptOnlyErrorMessage_forScriptErrorReportRoute() throws IOException {
        String rule = readRule("1075");

        assertThat(rule)
                .contains("SecRule REQUEST_URI \"@rx ^/carlos/eform/logEformError(?:[;?]|$)\"")
                .contains("\"id:1075,phase:1,pass,nolog,chain\"")
                .contains("SecRule REQUEST_METHOD \"@streq POST\"");

        // Per-argument tag removal and exactly these three: a request-wide ruleRemoveById, another
        // argument (formId must stay scored) or another family (LFI/RFI/PHP/protocol) fails here.
        List<String> found = new ArrayList<>();
        Matcher matcher = CTL_ACTION.matcher(rule);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        assertThat(found).containsExactlyInAnyOrder(
                "ruleRemoveTargetByTag=attack-rce;ARGS:error",
                "ruleRemoveTargetByTag=attack-xss;ARGS:error",
                "ruleRemoveTargetByTag=attack-sqli;ARGS:error");
    }

    @Test
    @DisplayName("should sit above the clinical-prose block, whose rules may not remove attack-xss")
    void shouldPrecedeClinicalProseBlock_forXssPolicy() throws IOException {
        String exclusions = read();
        assertThat(exclusions.indexOf("\"id:1075,"))
                .isGreaterThan(0)
                .isLessThan(exclusions.indexOf("Clinician free text on the rest of the application"));
    }

    @Test
    @DisplayName("should exempt the argument name the toolbar posts and the action reads")
    void shouldMatchArgumentName_betweenToolbarActionAndRule() throws IOException {
        // The exclusion names ARGS:error. If either side renames the parameter the rule silently
        // stops applying and every report is scored again.
        String toolbar = Files.readString(TOOLBAR_JS, StandardCharsets.UTF_8);
        assertThat(toolbar).contains("eform.error = message;").contains("\"/eform/logEformError\"");
        String action = Files.readString(ACTION, StandardCharsets.UTF_8);
        assertThat(action).contains("request.getParameter(\"error\")").contains("request.getParameter(\"formId\")");
    }

    private static String readRule(String ruleId) throws IOException {
        String exclusions = read();
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
