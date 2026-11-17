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

/**
 * Locks in the eChart autosave fixes from PRs #1887 and the follow-up for #1890 so that
 * future refactors of {@code newCaseManagementView.js.jsp} cannot silently reintroduce:
 *
 * <ul>
 *   <li>The draft-destroying {@code method=cancel} submit on Exit-without-save (#1873).</li>
 *   <li>The race where a late-landing autosave XHR writes a draft after window close (#1890).</li>
 *   <li>The silent swallow of non-409 autosave failures (#1890).</li>
 * </ul>
 *
 * <p>This is a source-pattern regression guard in the style of the other
 * {@code *JspMigrationRegressionTest} classes in the repo — it reads the shipping JSP
 * source and asserts the fix markers are present. It is not a behavioral test; an
 * end-to-end assertion lives (or will live) in the Playwright UI-test suite for the
 * encounter module.</p>
 *
 * @since 2026-04-20
 */
@DisplayName("Case management autosave regressions")
@Tag("unit")
@Tag("casemgmt")
class CaseManagementAutoSaveRegressionTest {

    private static final Path CASE_MGMT_VIEW_JS_JSP =
            Path.of("src/main/webapp/js/newCaseManagementView.js.jsp");
    private static final Path CARLOS_AJAX_JS =
            Path.of("src/main/webapp/share/javascript/carlos-ajax.js");

    @Test
    @DisplayName("closeEnc should no longer submit method=cancel for dirty notes (#1873)")
    void closeEncShouldNotSubmitCancelForDirtyNotes() throws IOException {
        String js = Files.readString(CASE_MGMT_VIEW_JS_JSP, StandardCharsets.UTF_8);

        int closeEncStart = js.indexOf("function closeEnc(");
        assertThat(closeEncStart)
                .as("closeEnc handler must exist")
                .isGreaterThan(0);
        int closeEncEnd = js.indexOf("\n    }", closeEncStart);
        assertThat(closeEncEnd).isGreaterThan(closeEncStart);
        String closeEncBody = js.substring(closeEncStart, closeEncEnd);

        assertThat(closeEncBody)
                .as("closeEnc must not re-introduce the method=cancel submit that wiped autosave drafts (#1873)")
                .doesNotContain("frm.method.value = \"cancel\"")
                .doesNotContain("method.value = 'cancel'");
        assertThat(closeEncBody).contains("clearAutoSaveTimer()");
        assertThat(closeEncBody).contains("window.close()");
    }

    @Test
    @DisplayName("clearAutoSaveTimer should abort the in-flight autosave XHR (#1890)")
    void clearAutoSaveTimerShouldAbortInFlightXhr() throws IOException {
        String js = Files.readString(CASE_MGMT_VIEW_JS_JSP, StandardCharsets.UTF_8);

        assertThat(js)
                .as("module must track the in-flight autosave XHR so it can be aborted on close")
                .contains("var autoSaveXhr");
        assertThat(js)
                .as("autoSave must capture the XHR returned from CarlosAjax.request")
                .contains("autoSaveXhr = CarlosAjax.request");

        int clearStart = js.indexOf("function clearAutoSaveTimer()");
        assertThat(clearStart).isGreaterThan(0);
        int clearEnd = js.indexOf("\n    }", clearStart);
        assertThat(clearEnd).isGreaterThan(clearStart);
        String clearBody = js.substring(clearStart, clearEnd);

        assertThat(clearBody)
                .as("clearAutoSaveTimer must abort the in-flight autosave XHR, not just the timeout")
                .contains("clearTimeout(autoSaveTimer)")
                .contains("autoSaveXhr.abort()");
    }

    @Test
    @DisplayName("autoSave onFailure should log non-409 statuses instead of silently swallowing (#1890)")
    void autoSaveOnFailureShouldLogNon409Statuses() throws IOException {
        String js = Files.readString(CASE_MGMT_VIEW_JS_JSP, StandardCharsets.UTF_8);

        int autoSaveStart = js.indexOf("function autoSave()");
        assertThat(autoSaveStart).isGreaterThan(0);
        // autoSave contains nested callbacks: an indented closing brace can end
        // currentEditor rather than the autosave operation being checked.
        int autoSaveEnd = js.indexOf("\n    function backup()", autoSaveStart);
        assertThat(autoSaveEnd).isGreaterThan(autoSaveStart);
        String autoSaveBody = js.substring(autoSaveStart, autoSaveEnd);
        int currentEditorStart = autoSaveBody.indexOf("function currentEditor()");
        int failureStart = autoSaveBody.indexOf("function autosaveFailed(");
        int completionStart = autoSaveBody.indexOf("function autosaveComplete()");
        assertThat(currentEditorStart).isGreaterThan(0);
        assertThat(failureStart).isGreaterThan(currentEditorStart);
        assertThat(completionStart).isGreaterThan(failureStart);
        String currentEditorBody = autoSaveBody.substring(currentEditorStart, failureStart);
        String failureBody = autoSaveBody.substring(failureStart, completionStart);

        assertThat(failureBody)
                .as("non-409 failures must surface via console.error instead of being silently dropped")
                .contains("console.error", "showAutosaveStatus(\"failed\"");
        assertThat(currentEditorBody)
                .as("explicitly cancelled operations must be ignored while real status-zero failures remain visible")
                .contains("!operation.cancelled");
        assertThat(failureBody)
                .contains("if (!currentEditor()) return;", "req.status == 409", "showAutosaveStatus(\"conflict\"")
                .doesNotContain("req.status === 0");
        int failureCallbackStart = autoSaveBody.indexOf("onFailure: function (req)");
        int completionCallbackStart = autoSaveBody.indexOf("onComplete: autosaveComplete", failureCallbackStart);
        assertThat(failureCallbackStart).isGreaterThan(completionStart);
        assertThat(completionCallbackStart).isGreaterThan(failureCallbackStart);
        assertThat(autoSaveBody.substring(failureCallbackStart, completionCallbackStart))
                .as("the actual AJAX failure callback must route to the checked handler")
                .contains("autosaveFailed(req, false)");
        int timeoutStart = autoSaveBody.indexOf("autoSaveXhr.timeout = 45000");
        int timeoutCallbackStart = autoSaveBody.indexOf("autoSaveXhr.ontimeout = function", timeoutStart);
        assertThat(timeoutStart).isGreaterThan(completionCallbackStart);
        assertThat(timeoutCallbackStart).isGreaterThan(timeoutStart);
        assertThat(autoSaveBody.substring(timeoutCallbackStart))
                .as("a real XHR timeout must report failure and release the active autosave for retry")
                .contains("autosaveFailed({ status: 0, statusText: \"Request timed out\" }, false)", "autosaveComplete()");
        int statusStart = js.indexOf("function showAutosaveStatus(");
        assertThat(statusStart)
                .as("showAutosaveStatus must exist and be declared before autoSave")
                .isGreaterThanOrEqualTo(0)
                .isLessThan(autoSaveStart);
        String statusHelper = js.substring(statusStart, autoSaveStart);
        assertThat(statusHelper)
                .as("status updates must guard against the element being gone after chart close")
                .contains("if (!statusEl) return;");
    }

    @Test
    @DisplayName("CarlosAjax.request async path should return the XHR so callers can abort stale requests")
    void carlosAjaxAsyncShouldReturnXhr() throws IOException {
        String js = Files.readString(CARLOS_AJAX_JS, StandardCharsets.UTF_8);

        int asyncStart = js.indexOf("function requestAsync(");
        assertThat(asyncStart)
                .as("requestAsync function must exist")
                .isGreaterThan(0);
        int asyncEnd = js.indexOf("\n    }", asyncStart);
        assertThat(asyncEnd).isGreaterThan(asyncStart);
        String asyncBody = js.substring(asyncStart, asyncEnd);

        assertThat(asyncBody)
                .as("requestAsync must return the xhr so callers can abort stale in-flight requests")
                .containsPattern("return\\s+xhr\\s*;");
    }
}
