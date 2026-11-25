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
        String body = function("legacyNoteLockBody");

        assertThat(lock).contains(
                "lockRequest.open('POST', '<%=request.getContextPath() %>/CaseManagementEntry', true);",
                "lockRequest.send(legacyNoteLockBody('isNoteEdited'));")
                .doesNotContain("'GET'");
        assertThat(body).contains(
                "\"&noteId=\" + encodeURIComponent(noteId)",
                "(method === 'isNoteEdited' ? \"&classicEdit=true\" : \"\")",
                "\"method=\" + method + \"&demographicNo=\" + encodeURIComponent(demographicNo)",
                "\"chartContext=\" + encodeURIComponent(chartContext)",
                "var demographicNo = '${carlos:forJavaScript(param.demographicNo)}';");
    }

    @Test
    @DisplayName("should show the lost-lock message at once unless this session got the lock")
    void shouldWarnAtOnce_whenTheLockIsNotTaken() {
        String lock = function("takeNoteLock");

        assertThat(page).contains("<fmt:message var=\"noteLockLostLabel\" key=\"encounter.noteLockLostError.msg\"/>");
        assertThat(lock).contains(
                "alert('${carlos:forJavaScriptBlock(noteLockLostLabel)}');",
                "owned = answer.ownedBySession === true;",
                "if (lockRequest.status === 200) {", "if (!owned)", "beginLegacyEditing(true);");
        assertThat(lock.indexOf("if (!owned)")).isLessThan(lock.indexOf("beginLegacyEditing(true);"));
        // Editing starts only through this one function, which enables the fields and the autosave.
        assertThat(function("beginLegacyEditing")).contains("legacyNoteLockOwned = true;",
                "note.readOnly = false;", "setTimer();");
    }

    @Test
    @DisplayName("should give back a lock the Edit request took when Cancel was chosen while it was in flight")
    void shouldCoordinateCancelWithAPendingEdit_soNoLockOutlivesTheClosedReader() {
        String lock = function("takeNoteLock");
        String cancel = function("cancelLegacyNote");

        // Cancel waits for the answer instead of closing at once, and does not touch the form.
        assertThat(cancel).contains("if (legacyLockRequestPending) {", "legacyCancelRequested = true;");
        assertThat(cancel.indexOf("legacyLockRequestPending"))
                .isLessThan(cancel.indexOf("window.close();"));
        // The answer is read first; a cancelled reader never starts editing. A lock this attempt took,
        // or may have taken when the answer was lost, is given back before the window closes.
        assertThat(lock).contains("legacyLockRequestPending = true;", "legacyLockRequestPending = false;",
                "taken = answer.lockTaken === true;", "if (taken || !answered) {",
                "releaseLegacyNoteLock(closeLegacyWindow);");
        assertThat(lock.indexOf("if (legacyCancelRequested)")).isLessThan(lock.indexOf("beginLegacyEditing(true);"));
    }

    @Test
    @DisplayName("should name each Edit attempt, so Cancel gives back only what that attempt took, even after a lost answer")
    void shouldNameTheEditAttempt_inEditAndInCancelsRelease() {
        String lock = function("takeNoteLock");
        String body = function("legacyNoteLockBody");

        assertThat(page).contains("var legacyEditAttempt = '<%= SafeEncode.forJavaScript((String) request.getAttribute(",
                "CaseManagementEntry2Action.EDIT_ATTEMPT_ATTRIBUTE)) %>';",
                "<input type=\"hidden\" name=\"editAttempt\" id=\"editAttemptField\" value=\"\"/>");
        assertThat(function("newLegacyEditAttempt")).contains("window.crypto.getRandomValues(bytes);");
        // A retry after a lost answer repeats the attempt; a refused one starts afresh.
        assertThat(lock).contains("if (!legacyEditAttempt) legacyEditAttempt = newLegacyEditAttempt();",
                "if (answered && !owned) legacyEditAttempt = '';");
        assertThat(body).contains("\"&editAttempt=\" + encodeURIComponent(legacyEditAttempt)");
        assertThat(function("beginLegacyEditing")).contains("attemptField.value = legacyEditAttempt;");
    }

    @Test
    @DisplayName("should close the editor only after the server confirms the lock is given back, and offer Cancel again otherwise")
    void shouldCloseOnlyAfterAConfirmedRelease_andKeepARetry() {
        String release = function("releaseLegacyNoteLock");
        String cancel = function("cancelLegacyNote");

        assertThat(page).contains("<fmt:message var=\"noteLockReleaseFailedLabel\" key=\"encounter.noteLockReleaseFailed.msg\"/>");
        assertThat(release).contains("legacyNoteLockBody('releaseNoteLock')", "if (releaseRequest.status === 200) {",
                "if (state === 'released' || state === 'notHeld' || state === 'cancelled') {", "done();",
                "alert('${carlos:forJavaScriptBlock(noteLockReleaseFailedLabel)}');")
                .doesNotContain("window.close()");
        assertThat(release.indexOf("done();")).isLessThan(release.indexOf("noteLockReleaseFailedLabel"));
        // Cancel of an editor an attempt opened gives the lock back first, then submits the cancel.
        assertThat(cancel).contains("if (legacyReleasePending) return false;", "releaseLegacyNoteLock(close);",
                "form.elements['method'].value = 'cancel';", "form.submit();");
        assertThat(cancel.indexOf("releaseLegacyNoteLock(close);"))
                .isLessThan(cancel.lastIndexOf("return true;"));
    }

    @Test
    @DisplayName("should keep a clinician who just saved editing, from the server-verified lock only")
    void shouldContinueEditing_afterASaveThatForwardsBackToThePage() {
        // The action sets the attribute for a POST whose persisted lock owns the note the page shows;
        // a fresh read-only GET never does. Only that flag starts editing on load.
        assertThat(page).contains("var noteLockOwnedAtRender = <%= \"true\".equals(request.getAttribute(",
                "CaseManagementEntry2Action.NOTE_LOCK_OWNED_ATTRIBUTE)) %>;");
        assertThat(function("init")).contains("if (noteLockOwnedAtRender) beginLegacyEditing(false);");
        assertThat(function("init")).doesNotContain("takeNoteLock()", "legacyNoteLockOwned = true");
    }

    @Test
    @DisplayName("should show the form a read-only GET opened, not the active editor's, when the action sets one")
    void shouldShowTheReadersForm_whenTheActionKeepsItApart() {
        assertThat(page).contains("CaseManagementEntry2Action.READER_FORM_ATTRIBUTE);",
                "if (form == null) form=(CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);");
    }

    @Test
    void shouldUseEachIssueDescription_asItsCheckboxLabel() {
        assertThat(page).contains("id=\"note-issue-select-${status.index}\"",
                "<label for=\"note-issue-select-${status.index}\">${cbb.issueDisplay.description}</label>");
    }

    /** The body of a top-level page function, up to the next function at the same indent. */
    private static String function(String name) {
        Matcher start = Pattern.compile("\\n        function " + Pattern.quote(name) + "\\([^)]*\\) \\{").matcher(page);
        assertThat(start.find()).as("function %s() in %s", name, EDITOR).isTrue();
        int end = page.indexOf("\n        }\n", start.end());
        assertThat(end).as("end of function %s()", name).isPositive();
        return page.substring(start.start(), end);
    }
}
