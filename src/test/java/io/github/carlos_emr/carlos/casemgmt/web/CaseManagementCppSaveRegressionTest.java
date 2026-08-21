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
            resolveProjectPath(Path.of("src/main/webapp/js/newCaseManagementView.js.jsp"));
    private static final Path NOTE_ISSUE_LIST_JSP =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/noteIssueList.jsp"));

    @Test
    @DisplayName("CPP saves should refresh Unresolved Issues without relying on a missing form element (#3422)")
    void shouldRefreshUnresolvedIssues_afterCppSave() throws IOException {
        String js = Files.readString(CASE_MGMT_VIEW_JS_JSP, StandardCharsets.UTF_8);

        int callbackStart = js.indexOf("function onIssueUpdate()");
        assertThat(callbackStart).as("CPP issue update callback must exist").isGreaterThan(0);
        // Bound the slice by the next function declaration rather than by a specific
        // indentation of the closing brace, which a reformat would silently break.
        int nextFunction = js.indexOf("function ", callbackStart + 1);
        int callbackEnd = nextFunction < 0 ? js.length() : nextFunction;
        assertThat(callbackEnd).isGreaterThan(callbackStart);
        String callbackBody = js.substring(callbackStart, callbackEnd);

        assertThat(callbackBody)
                .as("the encounter demographic is already available as module state; the form element is absent")
                .doesNotContain("$(\"demographicNo\")")
                .contains("&& demographicNo")
                .contains("encodeURIComponent(demographicNo)")
                .as("both query parameters carry encoded values; reloadURL was already "
                        + "encoded before #3422 and must stay that way")
                .contains("encodeURIComponent(ctx + \"/encounter/displayIssues\")")
                .contains("cmd=unresolvedIssues")
                .contains("loadDiv('unresolvedIssueslist', reloadUrl, 0)");
    }

    @Test
    @DisplayName("Repeated issue fragment updates should not redeclare block-scoped variables (#3422)")
    void shouldScopeFragmentVariables_whenIssueUpdatesRepeat() throws IOException {
        String jsp = Files.readString(NOTE_ISSUE_LIST_JSP, StandardCharsets.UTF_8);

        String noteTextAssignment = "var noteTxt = \"${carlos:forJavaScriptBlock(noteTxt)}\";";
        int noteTextStart = jsp.indexOf(noteTextAssignment);
        assertThat(noteTextStart).as("the AJAX note-text assignment must exist").isGreaterThanOrEqualTo(0);
        int fragmentScriptStart = jsp.lastIndexOf("<script type=\"text/javascript\">", noteTextStart);
        assertThat(fragmentScriptStart).as("the AJAX note text must be inside a script block").isGreaterThanOrEqualTo(0);
        int fragmentScriptEnd = jsp.indexOf("</script>", fragmentScriptStart);
        assertThat(fragmentScriptEnd)
                .as("the AJAX note text must be inside the same script block being tested")
                .isGreaterThan(noteTextStart);
        String fragmentScript = jsp.substring(fragmentScriptStart, fragmentScriptEnd + "</script>".length());

        assertThat(fragmentScript)
                .as("the script containing the AJAX note text may run repeatedly and must isolate all declarations")
                .containsPattern("<script type=\"text/javascript\">\\s*\\(function \\(\\) \\{")
                .containsPattern("\\}\\(\\)\\);\\s*</script>")
                .as("stored clinical text in the fragment must use JavaScript-block encoding")
                .doesNotContain("fn:escapeXml(noteTxt)")
                .contains(noteTextAssignment)
                .contains("${carlos:forJavaScriptBlock(caseManagementEntryForm.caseNote.encounter_type)}");
    }

    @Test
    @DisplayName("Note styling should read the note index from page scope, not the empty scriptlet local (#3422)")
    void shouldReadNoteIndexFromPageScope_forNoteStyling() throws IOException {
        String jsp = Files.readString(NOTE_ISSUE_LIST_JSP, StandardCharsets.UTF_8);

        // The scriptlet declares `String noteIndex = ""` and never reassigns it, while
        // <c:set var="noteIndex"> carries the real index in page scope. Reading the
        // scriptlet local yielded the element id "bgColour", which never resolves, so
        // note colour styling silently no-opped.
        assertThat(jsp)
                .doesNotContain("<%=noteIndex%>")
                .contains("\"bgColour\" + \"${carlos:forJavaScriptBlock(noteIndex)}\"")
                .contains("\"summary\" + \"${carlos:forJavaScriptBlock(noteIndex)}\"");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir"))).toAbsolutePath().normalize();

        // Walk to the filesystem root. A launcher that starts inside the package
        // directory sits well below the repository root, so any fixed depth limit
        // turns a resolvable path into a class-initialization failure.
        while (current != null) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        throw new IllegalStateException("Unable to resolve project path: " + relativePath);
    }
}
