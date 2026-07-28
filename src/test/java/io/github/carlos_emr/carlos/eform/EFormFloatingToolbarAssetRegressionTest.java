/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level pins for the eForm floating toolbar and the runtime compatibility shim.
 *
 * <p>The repository has no JavaScript unit-test harness, so browser behaviour is pinned two ways:
 * Playwright end-to-end checks, and source assertions like these that fail fast when a specific
 * defect is reintroduced. Each assertion below corresponds to a defect that shipped and was fixed;
 * the value here is regression detection, not coverage.</p>
 *
 * @since 2026-07-25
 */
@DisplayName("eForm floating toolbar asset")
@Tag("unit")
@Tag("eform")
class EFormFloatingToolbarAssetRegressionTest {

    private static final Path TOOLBAR_JS =
            Path.of("src", "main", "webapp", "eform", "eformFloatingToolbar", "eform_floating_toolbar.js");
    private static final Path RUNTIME_COMPAT_JS =
            Path.of("src", "main", "webapp", "eform", "eform-runtime-compat.js");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should clear only toolbar-created hidden inputs rather than selecting by bare id")
    void shouldClearOnlyToolbarCreatedInputs_whenClearingWorkflowFlags() throws IOException {
        String source = read(TOOLBAR_JS);

        // eForms are third-party HTML: a referral form may own a visible input with id "recipient".
        // Selecting by bare id deleted the clinician's field and silently dropped its submitted value.
        assertThat(source).contains("data-carlos-workflow-flag");
        assertThat(source).contains("input.dataset.carlosWorkflowFlag");
        assertThat(source)
                .as("clearWorkflowFlags must not enumerate bare element ids")
                .doesNotContain("['saveAndDownloadEForm', 'faxAction', 'emailAction', 'saveAsEdoc', "
                        + "'recipient', 'recipientFaxNumber']");
    }

    @Test
    @DisplayName("should guard remoteEdocument against a still-loading editor before setting its flag")
    void shouldGuardAgainstLoadingEditor_beforeSettingEdocFlag() throws IOException {
        String source = read(TOOLBAR_JS);
        int start = source.indexOf("function remoteEdocument()");
        assertThat(start).isNotNegative();
        String body = source.substring(start, source.indexOf("\n}", start));

        // Aborting after the flag is set strands saveAsEdoc=true, and a later plain Save rides it
        // into the save-as-eDoc workflow. The guard must precede clearWorkflowFlags/setHiddenFormInput.
        assertThat(body).contains("editorStillLoading()");
        assertThat(body.indexOf("editorStillLoading()"))
                .as("the loading guard must run before the action flag is set")
                .isLessThan(body.indexOf("setHiddenFormInput"));
    }

    @Test
    @DisplayName("should block save on timer-compat failure without relying on the submit event")
    void shouldBlockSave_whenTimerCompatibilityFailed() throws IOException {
        String toolbar = read(TOOLBAR_JS);
        String compat = read(RUNTIME_COMPAT_JS);

        // HTMLFormElement.submit() fires no submit event, so the shim's capture-phase listener never
        // sees the toolbar's save paths. remoteSave must consult the shim directly.
        assertThat(compat).contains("status.shouldBlockSubmission");
        assertThat(toolbar).contains("shouldBlockSubmission");
    }

    @Test
    @DisplayName("should abort the save when the eForm reports unsatisfied HTML5 constraints")
    void shouldAbortSave_whenFormConstraintsUnsatisfied() throws IOException {
        String toolbar = read(TOOLBAR_JS);

        // remoteSave submits through the eForm's own <input type="submit"> when it declares one, and
        // a native submit click runs constraint validation. A form with an empty required field
        // therefore never posts, but nothing throws — so without this guard remoteSave reported
        // success while remoteDownload/remoteFax/remoteEmail had already shown a LOCKED spinner and
        // set their workflow flag, stranding the clinician under an undismissable overlay on a form
        // that was never saved. Observed on a real clinic form with a required history field.
        assertThat(toolbar).contains("eFormValidationBlocked()");
        assertThat(toolbar).contains("checkValidity");
        // reportValidity names the offending field; a generic alert would not.
        assertThat(toolbar).contains("reportValidity");

        // Scope the ordering check to remoteSave's own body: these helpers are declared earlier in
        // the file, so a whole-file indexOf would compare against their definitions, not their calls.
        String saveBody = toolbar.substring(
                toolbar.indexOf("function remoteSave()"), toolbar.indexOf("data-poload"));
        int guard = saveBody.indexOf("if (eFormValidationBlocked())");
        assertThat(guard).as("the validity guard must be wired into remoteSave").isNotNegative();
        // The guard must precede the form mutations and the submit-bound spinner, or an abort leaves
        // the toolbar's own inputs behind on a form the clinician is still editing.
        // Match the call, not the bare name: the guard's own comment names appendImageInputs(), and
        // matching that instead compared the comment's position rather than the statement's.
        assertThat(guard)
                .as("validity must be checked before appendImageInputs()/moveSubject()")
                .isLessThan(saveBody.indexOf("appendImageInputs();"));
        // Unlike the editorStillLoading guard, this one runs AFTER the callers have set their
        // workflow flag, so it must clear the flag as well as the spinner.
        String guardBody = toolbar.substring(
                toolbar.indexOf("function eFormValidationBlocked()"),
                toolbar.indexOf("function remoteSave()"));
        assertThat(guardBody).contains("HideSpin()").contains("clearWorkflowFlags()");
    }

    @Test
    @DisplayName("should not blame the timer for an unrelated resource-load failure")
    void shouldIgnoreResourceLoadErrors_whenCapturingTimerFailures() throws IOException {
        String compat = read(RUNTIME_COMPAT_JS);

        // executeStringCallback attaches a capture-phase window "error" listener while it appends the
        // timer's script. Failed resource loads dispatch "error" too and reach that listener, so
        // without a target check the shim blamed the timer for any 404 that happened to land in the
        // few microseconds the listener was attached — and reported a compatibility failure that
        // blocked the whole render. It presented as flakiness: the same saved eForm rendered or was
        // refused run to run. Legacy forms deliberately carry such 404s (each asset is referenced
        // twice, once bare so the form opens off a local disk), so this fired across the corpus.
        assertThat(compat).contains("event.target !== window");
    }

    @Test
    @DisplayName("should track function timeout callbacks so the renderer can skip only blind waits")
    void shouldTrackFunctionTimeouts_whenWaitingForDeferredRenderWork() throws IOException {
        String compat = read(RUNTIME_COMPAT_JS);

        assertThat(compat)
                .contains("typeof handler !== \"function\"")
                .contains("delayMillis <= 4000")
                .contains("return handler.apply(receiver, callbackArguments)")
                .contains("Scheduled-but-not-yet-run one-shot timeouts");
    }

    @Test
    @DisplayName("should treat an eval-blocked timer script as a compatibility failure")
    void shouldDetectBlockedTimer_forEvalBlockedUri() throws IOException {
        String compat = read(RUNTIME_COMPAT_JS);

        // A native setTimeout("code", n) running before the shim installs is reported with
        // blockedURI "eval", not "inline"; matching only "inline" left status.failed false.
        assertThat(compat).contains("event.blockedURI === \"eval\"");
        assertThat(compat).contains("event.blockedURI === \"inline\"");
    }
}
