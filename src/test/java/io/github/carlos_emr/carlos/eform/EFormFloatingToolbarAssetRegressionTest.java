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
                .contains("status.pending += 1;")
                .contains("typeof handler !== \"function\"")
                .contains("delayMillis <= 4000")
                .contains("return handler.apply(receiver, callbackArguments)")
                .contains("if (counted && countedTimeouts.delete(handle))")
                .contains("status.pending -= 1;")
                .contains("Scheduled-but-not-yet-run one-shot timeouts");
    }

    @Test
    @DisplayName("should stop tracking a counted timeout when form code cancels it")
    void shouldReleaseCountedTimeout_whenClearedBeforeRunning() throws IOException {
        String compat = read(RUNTIME_COMPAT_JS);

        assertThat(compat)
                .contains("status.pending += 1;")
                .contains("var countedTimeouts = new Set()")
                .contains("window.clearTimeout = function clearTimeoutCompatible(handle)")
                .contains("countedTimeouts.delete(handle)")
                .contains("window.clearInterval = function clearIntervalCompatible(handle)")
                .contains("return cancelTrackedTimer(nativeClearInterval, handle)")
                .contains("status.pending -= 1;");
    }

    @Test
    @DisplayName("should keep counting a short chained self-reschedule instead of treating its first pass as a heartbeat")
    void shouldBoundSelfRescheduleExclusion_soChainedDeferredWorkStaysCounted() throws IOException {
        String compat = read(RUNTIME_COMPAT_JS);

        // A same-reference self-reschedule (handler calls setTimeout(handler, ...) again while
        // still running) is how BOTH a repeating heartbeat/UI loop and a short chained-completion
        // sequence (poll until ready, then populate a field and stop) reschedule themselves.
        // Excluding every self-reschedule unconditionally stopped counting a chain after its first
        // pass, so whenIdle could resolve -- and the PDF could be captured -- before a still-pending
        // later pass populated a field. Counting the first SELF_RESCHEDULE_COUNT_LIMIT reschedules
        // keeps a short chain covered while a loop that keeps rescheduling past that bound still
        // falls back to the render budget cap.
        assertThat(compat)
                .contains("var SELF_RESCHEDULE_COUNT_LIMIT = 3;")
                .contains("var selfRescheduleCounts = new Map();")
                .contains("var isSelfReschedule = typeof handler === \"function\" "
                        + "&& runningFunctionHandlers.indexOf(handler) >= 0;")
                .contains("selfRescheduleCount = (selfRescheduleCounts.get(handler) || 0) + 1;")
                .contains("var selfRescheduleExcluded = isSelfReschedule "
                        + "&& selfRescheduleCount > SELF_RESCHEDULE_COUNT_LIMIT;")
                .contains("&& !selfRescheduleExcluded));");
    }

    @Test
    @DisplayName("should keep whenIdle waiting while an excluded self-reschedule chain is still actively rescheduling")
    void shouldExtendWhenIdleWait_whileExcludedSelfRescheduleStillActive() throws IOException {
        String compat = read(RUNTIME_COMPAT_JS);

        // Once a self-reschedule is excluded from status.pending, it is invisible to the
        // pending<=0 check whenIdle uses to resolve early -- without this signal, a
        // still-actively-rescheduling excluded chain could let whenIdle (and the PDF capture)
        // resolve before a later pass populates a field, even though the render budget cap was
        // supposed to still govern it. A genuinely repeating heartbeat keeps a handle in the
        // pending set continuously and falls through to the deadline; a chain that actually stops
        // empties the set as soon as its last invocation completes and resolves quickly.
        //
        // Tracked per HANDLE (added once scheduled, removed on fire or cancellation), not as a
        // single deadline shared by the whole page: a single global "next fire time" left stale by
        // a cancelled excluded reschedule would force whenIdle to wait out that now-irrelevant
        // future time for a callback that will never run.
        assertThat(compat)
                .contains("var excludedSelfReschedulePending = new Set();")
                .contains("excludedSelfReschedulePending.add(handle);")
                .contains("excludedSelfReschedulePending.delete(handle);")
                .contains("status.pending <= 0 && excludedSelfReschedulePending.size === 0");
    }

    @Test
    @DisplayName("should never register a setInterval handle as excluded-but-pending, matching intervals being excluded from all timer tracking")
    void shouldNotTrackSetIntervalHandles_inExcludedSelfReschedulePendingSet() throws IOException {
        String compat = read(RUNTIME_COMPAT_JS);

        // selfRescheduleExcluded on its own only checks whether the same function reference is
        // currently running -- it does not care whether THIS scheduling call was a setTimeout or
        // a setInterval. Without the nativeTimer === nativeSetTimeout guard here (matching the
        // identical guard already in the counted condition above), a self-rescheduling setInterval
        // registration could land in the excluded-but-pending set, and since its handle is only
        // ever removed on its first tick, whenIdle() would refuse to resolve early until that
        // first tick (which could be seconds away) or the render's own cap -- even though
        // setInterval is supposed to be entirely excluded from this tracking.
        assertThat(compat)
                .contains("if (selfRescheduleExcluded && nativeTimer === nativeSetTimeout) {")
                .contains("excludedSelfReschedulePending.add(handle);");
    }

    @Test
    @DisplayName("should reset a handler's self-reschedule count once its chain ends, so a later unrelated chain starts fresh")
    void shouldResetSelfRescheduleCount_whenChainEndsOrIsCancelled() throws IOException {
        String compat = read(RUNTIME_COMPAT_JS);

        // Without a reset, a function reference reused later for an unrelated, independent short
        // chain would inherit the previous chain's leftover count and could have its own first
        // reschedule wrongly excluded from the start. Reset on two events: the invocation
        // completing without rescheduling itself again (chain finished), and the still-pending
        // reschedule being cancelled (chain stopped externally).
        assertThat(compat)
                .contains("var selfRescheduleCountBeforeInvocation = selfRescheduleCounts.get(handler);")
                .contains("if (selfRescheduleCounts.get(handler) === selfRescheduleCountBeforeInvocation) {")
                .contains("selfRescheduleCounts.delete(handler);")
                .contains("var handleToHandler = new Map();")
                .contains("var cancelledHandler = handleToHandler.get(handle);")
                .contains("selfRescheduleCounts.delete(cancelledHandler);");
    }

    @Test
    @DisplayName("should suppress only the known delayed auto-submit callback on the PDF render surface")
    void shouldSuppressOnlyKnownAutoSubmit_whenRenderingPdf() throws IOException {
        String compat = read(RUNTIME_COMPAT_JS);

        assertThat(compat)
                .contains("function isPdfRenderAutoSubmit(handler)")
                .contains("window.__carlosEformPdfRender === true")
                .contains("typeof handler === \"string\"")
                .contains("/^\\s*SubmitButton\\s*\\.\\s*click\\s*\\(\\s*\\)\\s*;?\\s*$/")
                .contains("suppressedAutoSubmits");
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
