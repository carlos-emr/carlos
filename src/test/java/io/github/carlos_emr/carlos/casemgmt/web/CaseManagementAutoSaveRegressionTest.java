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
        int autoSaveEnd = js.indexOf("\n    }", autoSaveStart);
        String autoSaveBody = js.substring(autoSaveStart, autoSaveEnd);

        assertThat(autoSaveBody)
                .as("non-409 failures must surface via console.error instead of being silently dropped")
                .contains("console.error");
        assertThat(autoSaveBody)
                .as("aborted requests (status 0) from clearAutoSaveTimer must be treated as non-errors")
                .contains("req.status === 0");
        assertThat(autoSaveBody)
                .as("DOM writes on success must guard against the status element being gone post-close")
                .containsPattern("var\\s+statusEl\\s*=\\s*\\$\\(\"autosaveTime\"\\)");
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
        String asyncBody = js.substring(asyncStart, asyncEnd);

        assertThat(asyncBody)
                .as("requestAsync must return the xhr so callers can abort stale in-flight requests")
                .containsPattern("return\\s+xhr\\s*;");
    }
}
