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
package io.github.carlos_emr.carlos.casemgmt.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Case management CPP save regressions")
@Tag("unit")
@Tag("casemgmt")
class CaseManagementCppSaveRegressionTest {

    private static final Path CASE_MGMT_VIEW_JS_JSP =
            Path.of("src/main/webapp/js/newCaseManagementView.js.jsp");
    private static final Path NOTE_ISSUE_LIST_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/noteIssueList.jsp");

    @Test
    @DisplayName("CPP saves should refresh Unresolved Issues without relying on a missing form element (#3422)")
    void cppSaveShouldRefreshUnresolvedIssuesUsingEncounterDemographic() throws IOException {
        String js = Files.readString(CASE_MGMT_VIEW_JS_JSP, StandardCharsets.UTF_8);

        int callbackStart = js.indexOf("function onIssueUpdate()");
        assertThat(callbackStart).as("CPP issue update callback must exist").isGreaterThan(0);
        int callbackEnd = js.indexOf("\n    }", callbackStart);
        assertThat(callbackEnd).isGreaterThan(callbackStart);
        String callbackBody = js.substring(callbackStart, callbackEnd);

        assertThat(callbackBody)
                .as("the encounter demographic is already available as module state; the form element is absent")
                .doesNotContain("$(\"demographicNo\")")
                .contains("&& demographicNo")
                .contains("encodeURIComponent(demographicNo)")
                .contains("cmd=unresolvedIssues")
                .contains("loadDiv('unresolvedIssueslist', reloadUrl, 0)");
    }

    @Test
    @DisplayName("Repeated issue fragment updates should not redeclare block-scoped variables (#3422)")
    void repeatedIssueUpdatesShouldScopeFragmentVariables() throws IOException {
        String jsp = Files.readString(NOTE_ISSUE_LIST_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .as("the AJAX-inserted script may run repeatedly and must isolate all of its declarations")
                .containsPattern("<script type=\"text/javascript\">\\s*\\(function \\(\\) \\{")
                .containsPattern("\\}\\(\\)\\);\\s*</script>")
                .as("stored clinical text in the fragment must use JavaScript-block encoding")
                .doesNotContain("fn:escapeXml(noteTxt)")
                .contains("${carlos:forJavaScriptBlock(noteTxt)}")
                .contains("${carlos:forJavaScriptBlock(caseManagementEntryForm.caseNote.encounter_type)}");
    }
}
