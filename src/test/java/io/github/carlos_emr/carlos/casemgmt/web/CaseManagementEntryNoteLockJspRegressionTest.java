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
 * The classic note editor takes its edit lock by POST once it has loaded.
 *
 * <p>Opening the editor by GET (a link, a note search result) takes no lock
 * ({@link CaseManagementEntryNoteOpenUnitTest}), and its Save needs this session's lock. So the
 * page posts {@code method=isNoteEdited} on load, as the chart's own editor does, and when another
 * provider holds the note or the request fails it shows the existing lost-lock message at once,
 * before anything is typed. A page without the editor form (no right to write notes) takes no lock.
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
    @DisplayName("should take the note lock on load, before the autosave timer, only when the editor form is there")
    void shouldTakeTheNoteLockOnLoad_whenTheEditorFormIsPresent() {
        String init = function("init");

        assertThat(page).contains("<body onload=\"init()\">");
        assertThat(init).contains("if (document.getElementById('caseManagementEntryForm')) takeNoteLock();");
        assertThat(init.indexOf("takeNoteLock();")).isLessThan(init.indexOf("setTimer();"));
        assertThat(page).contains("<form name=\"caseManagementEntryForm\" id=\"caseManagementEntryForm\"");
    }

    @Test
    @DisplayName("should post method=isNoteEdited for this chart's patient and note to the editor action")
    void shouldPostTheLockRequest_toTheEditorAction() {
        String lock = function("takeNoteLock");

        assertThat(lock).contains("lockRequest.open('POST', '<%=request.getContextPath() %>/CaseManagementEntry', true);");
        assertThat(lock).contains("\"method=isNoteEdited&demographicNo=\" + encodeURIComponent(demographicNo)");
        assertThat(lock).contains("\"&noteId=\" + encodeURIComponent(noteId)");
        assertThat(lock).contains("\"chartContext=\" + encodeURIComponent(chartContext)");
        assertThat(lock).contains("var demographicNo = '${carlos:forJavaScript(param.demographicNo)}';");
        assertThat(lock).doesNotContain("'GET'");
    }

    @Test
    @DisplayName("should show the lost-lock message at once unless this session got the lock")
    void shouldWarnAtOnce_whenTheLockIsNotTaken() {
        String lock = function("takeNoteLock");

        assertThat(page).contains("<fmt:message var=\"noteLockLostLabel\" key=\"encounter.noteLockLostError.msg\"/>");
        assertThat(lock).contains("alert('${carlos:forJavaScriptBlock(noteLockLostLabel)}');");
        assertThat(lock).contains("answer = JSON.parse(lockRequest.responseText).isNoteEdited;");
        assertThat(lock).contains("if (lockRequest.status === 200) {");
        assertThat(lock).contains("if (answer !== 'unlocked' && answer !== 'user') warn();");
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
