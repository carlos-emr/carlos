/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
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
 * Pins the packaged WAF's exemption for the HTML source editor's save (exclusion 1050 in the
 * BEFORE-CRS file), including its single REQUEST_BODY exception.
 *
 * <p>Verified on a packaged Ubuntu 26.04 install while making the Visual eForm Editor the library's
 * default (#3166): saving a visual-editor form from "Edit HTML source (Advanced)" was answered 403,
 * and the audit log showed CRS 921110 (request smuggling) alone on REQUEST_BODY. The editor's own
 * stylesheet, emitted into every form it saves, contains {@code .gen-widget input,} followed by a
 * line break, which that unanchored pattern reads as a request line ("get input"). This test pins
 * the shape of the rule so the exception cannot quietly widen to other rules, targets or routes;
 * {@code scripts/eform-visual-editor-default-edit-playwright-checks.js} drives the save through the
 * front door.</p>
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("security")
@Tag("eform")
@DisplayName("eForm HTML editor save WAF exclusion")
class EFormEditFormWafExclusionRegressionTest {

    private static final Path EXCLUSIONS = resolveProjectPath(
            Path.of("debian", "assets", "modsecurity", "REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf"));
    private static final Path VISUAL_EDITOR_JSP = resolveProjectPath(
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "eform", "visualEformEditor.jsp"));

    /** Every ctl action in a rule, whatever its kind (ruleEngine, ruleRemoveById, ...). */
    private static final Pattern CTL_ACTION = Pattern.compile("ctl:([^,\"\\\\\\s]+)");

    @Test
    @DisplayName("should exempt only formHtml from the eForm families and only 921110 from the raw body, on POST")
    void shouldExemptOnlyFormHtmlAndOneBodyRule_forEditFormRoute() throws IOException {
        String rule = readRule("1050");

        assertThat(rule)
                .contains("SecRule REQUEST_URI \"@rx ^/carlos/eform/editForm(?:[;?]|$)\"")
                .contains("\"id:1050,phase:1,pass,nolog,chain\"")
                .contains("SecRule REQUEST_METHOD \"@streq POST\"");

        List<String> found = new ArrayList<>();
        Matcher matcher = CTL_ACTION.matcher(rule);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        assertThat(found).containsExactlyInAnyOrder(
                "ruleRemoveTargetByTag=attack-sqli;ARGS:formHtml",
                "ruleRemoveTargetByTag=attack-xss;ARGS:formHtml",
                "ruleRemoveTargetByTag=attack-rce;ARGS:formHtml",
                "ruleRemoveTargetByTag=attack-injection-php;ARGS:formHtml",
                "ruleRemoveTargetByTag=attack-protocol;ARGS:formHtml",
                "ruleRemoveTargetByTag=attack-lfi;ARGS:formHtml",
                "ruleRemoveTargetById=921110;REQUEST_BODY");
    }

    @Test
    @DisplayName("should still be needed: the visual editor's stylesheet carries the line 921110 matches")
    void shouldMatchVisualEditorStylesheet_forRequestBodyException() throws IOException {
        // If the editor's stylesheet stops emitting this selector at a line end, re-measure: the
        // REQUEST_BODY exception may then be removable.
        String jsp = Files.readString(VISUAL_EDITOR_JSP, StandardCharsets.UTF_8);
        assertThat(Pattern.compile("\\.gen-widget input,\\r?\\n").matcher(jsp).find()).isTrue();
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
