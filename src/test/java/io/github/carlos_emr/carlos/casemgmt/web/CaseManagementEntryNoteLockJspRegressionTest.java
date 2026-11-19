/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classic note keeps its text readable until the clinician chooses Edit.
 *
 * <p>Opening the editor by GET (a link, a note search result) takes no lock
 * ({@link CaseManagementEntryNoteOpenUnitTest}), and its Save needs this session's lock. So the
 * Edit button posts {@code method=isNoteEdited}. The form enables changes after the response confirms
 * session ownership and shows the existing lost-lock message when the request fails.
 * This is a source-pattern guard over the shipping JSP.</p>
 */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementEntryNoteLockJspRegressionTest {
    private static final Path EDITOR = Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/CaseManagementEntry.jsp");

    private static String page;

    @BeforeAll
    static void readPage() throws IOException {
        page = Files.readString(EDITOR, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should read the note without a lock or autosave before the clinician chooses Edit")
    void shouldLeaveTheNoteReadOnly_untilTheClinicianChoosesEdit() {
        String init = function("init");

        assertThat(init).doesNotContain("takeNoteLock()", "setTimer()");
        assertThat(page).contains("id=\"editLegacyNote\" onclick=\"takeNoteLock()\"", "wrap=\"soft\" readonly",
                "value=\"Save\" disabled data-note-lock-edit");
    }

    @Test
    @DisplayName("should post method=isNoteEdited for this chart's patient and note to the editor action")
    void shouldPostTheLockRequest_toTheEditorAction() {
        String lock = function("takeNoteLock");

        assertThat(lock).contains(
                "lockRequest.open('POST', '<%=request.getContextPath() %>/CaseManagementEntry', true);",
                "\"method=isNoteEdited&demographicNo=\" + encodeURIComponent(demographicNo)",
                "\"&noteId=\" + encodeURIComponent(noteId)",
                "\"chartContext=\" + encodeURIComponent(chartContext)",
                "var demographicNo = '${carlos:forJavaScript(param.demographicNo)}';")
                .doesNotContain("'GET'");
    }

    @Test
    @DisplayName("should show the lost-lock message at once unless this session got the lock")
    void shouldWarnAtOnce_whenTheLockIsNotTaken() {
        String lock = function("takeNoteLock");

        assertThat(page).contains("<fmt:message var=\"noteLockLostLabel\" key=\"encounter.noteLockLostError.msg\"/>");
        assertThat(lock).contains(
                "alert('${carlos:forJavaScriptBlock(noteLockLostLabel)}');",
                "owned = JSON.parse(lockRequest.responseText).ownedBySession === true;",
                "if (lockRequest.status === 200) {", "if (!owned)", "note.readOnly = false;", "setTimer();");
        assertThat(lock.indexOf("if (!owned)")).isLessThan(lock.indexOf("note.readOnly = false;"));
    }

    @Test
    void shouldUseEachIssueDescriptionAsItsCheckboxLabel() {
        assertThat(page).contains("id=\"note-issue-select-${status.index}\"",
                "<label for=\"note-issue-select-${status.index}\">${cbb.issueDisplay.description}</label>");
    }

    /** The body of a top-level page function, up to the next function at the same indent. */
    private static String function(String name) {
        Matcher start = Pattern.compile("\\n        function " + Pattern.quote(name) + "\\(\\) \\{").matcher(page);
        assertThat(start.find()).as("function %s() in %s", name, EDITOR).isTrue();
        int end = page.indexOf("\n        }\n", start.end());
        assertThat(end).as("end of function %s()", name).isPositive();
        return page.substring(start.start(), end);
    }
}
