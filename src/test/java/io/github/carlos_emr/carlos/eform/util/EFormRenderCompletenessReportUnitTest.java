/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.util.Map;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EFormRenderCompletenessReport")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormRenderCompletenessReportUnitTest {

    @Test
    @DisplayName("should merge every incomplete-output category")
    void shouldMergeEveryCategory_whenCombiningReports() {
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(2, 0, 1, 0, true, false, true, false)
                        .merge(new EFormRenderCompletenessReport(1, 3, 2, 0, false, true, false, true));

        assertThat(report.failedContentResources()).isEqualTo(3);
        assertThat(report.excludedContentElements()).isEqualTo(3);
        assertThat(report.severeConsoleErrors()).isEqualTo(3);
        assertThat(report.signatureMissing()).isTrue();
        assertThat(report.timerCompatibilityFailure()).isTrue();
        assertThat(report.stabilizationCapped()).isTrue();
        assertThat(report.labDecisionSupportStubbed()).isTrue();
        assertThat(report.issueCount()).isEqualTo(13);
        assertThat(report.isComplete()).isFalse();
    }

    @Test
    @DisplayName("should produce a stable digest that changes with the issue set")
    void shouldDigestExactIssueSet_forApprovalBinding() {
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(1, 2, 0, 0, false, true, false, false);

        assertThat(report.digest()).hasSize(64).isEqualTo(report.digest());
        assertThat(report.digest()).isNotEqualTo(
                new EFormRenderCompletenessReport(1, 3, 0, 0, false, true, false, false).digest());
        assertThat(EFormRenderCompletenessReport.complete().isComplete()).isTrue();
    }

    @Test
    @DisplayName("should give every one of the nine components its own effect on the digest")
    void shouldDistinguishEveryComponent_inTheDigest() {
        // The record has NINE components but every other test here uses the 8-arg convenience
        // constructor, which defaults providerStampMissing to false. Measured by mutation: deleting
        // signatureMissing, providerStampMissing AND labDecisionSupportStubbed from the canonical
        // string left 156 tests green. A digest blind to signatureMissing means a clinician's
        // approval of one render is replayable against a DIFFERENT render that lost its signature —
        // which is precisely what binding approval to the digest exists to prevent.
        //
        // Pairwise, not just "differs from complete()": that also catches two components being
        // conflated or transposed in the canonical string, which a one-at-a-time check would miss.
        Map<String, EFormRenderCompletenessReport> oneComponentFlipped = new LinkedHashMap<>();
        oneComponentFlipped.put("failedContentResources",
                new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false, false));
        oneComponentFlipped.put("excludedContentElements",
                new EFormRenderCompletenessReport(0, 1, 0, 0, false, false, false, false, false));
        oneComponentFlipped.put("severeConsoleErrors",
                new EFormRenderCompletenessReport(0, 0, 1, 0, false, false, false, false, false));
        oneComponentFlipped.put("containedInteractions",
                new EFormRenderCompletenessReport(0, 0, 0, 1, false, false, false, false, false));
        oneComponentFlipped.put("signatureMissing",
                new EFormRenderCompletenessReport(0, 0, 0, 0, true, false, false, false, false));
        oneComponentFlipped.put("timerCompatibilityFailure",
                new EFormRenderCompletenessReport(0, 0, 0, 0, false, true, false, false, false));
        oneComponentFlipped.put("stabilizationCapped",
                new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, true, false, false));
        oneComponentFlipped.put("labDecisionSupportStubbed",
                new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, false, true, false));
        oneComponentFlipped.put("providerStampMissing",
                new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, false, false, true));

        // Derived from the record itself, so adding a tenth component fails here until it is pinned.
        assertThat(oneComponentFlipped)
                .describedAs("every record component must be represented")
                .hasSize(EFormRenderCompletenessReport.class.getRecordComponents().length);

        String baseline = EFormRenderCompletenessReport.complete().digest();
        Map<String, String> digests = new LinkedHashMap<>();
        oneComponentFlipped.forEach((name, report) -> {
            assertThat(report.digest())
                    .describedAs("flipping %s alone must change the digest", name)
                    .isNotEqualTo(baseline);
            digests.put(name, report.digest());
        });

        assertThat(digests.values())
                .describedAs("no two components may collapse to the same digest")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("should reject invalid negative counters")
    void shouldRejectNegativeCounters_forEveryCount() {
        assertThatThrownBy(() ->
                new EFormRenderCompletenessReport(-1, -2, 0, 0, false, false, false, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new EFormRenderCompletenessReport(0, 0, -1, 0, false, false, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should treat a capped stabilization as incomplete so it requires clinician approval")
    void shouldTreatStabilizationCap_asIncomplete() {
        // A page still mutating when the cap expired was captured mid-assembly. Before this signal
        // reached the report it was a WARN only: isComplete() stayed true and the half-built document
        // rendered, faxed and archived as if correct.
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, true, false);

        assertThat(report.isComplete()).isFalse();
        assertThat(report.issueCount()).isEqualTo(1);
        assertThat(report.digest()).isNotEqualTo(EFormRenderCompletenessReport.complete().digest());
    }

    @Test
    @DisplayName("should treat an uncaught page-script error as incomplete")
    void shouldTreatSevereConsoleError_asIncomplete() {
        // Resource-load and CSP entries are excluded upstream, so a severe console entry is an
        // uncaught page-script exception -- the only observable for a script that aborted midway
        // through injecting clinical content while every subresource still returned 200.
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(0, 0, 2, 0, false, false, false, false);

        assertThat(report.isComplete()).isFalse();
        assertThat(report.issueCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("should not block the document for a page-script error alone")
    void shouldNotBlockDocument_forPageScriptErrorAlone() {
        // Advisory, not ignored. Legacy corpus forms routinely throw once during load (a
        // getElementById(...) returning null for a field the form no longer has) while rendering
        // every bit of their clinical content, so blocking withheld complete documents far more
        // often than it caught truncated ones. It stays in the report so the reader is told.
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(0, 0, 1, 0, false, false, false, false);

        assertThat(report.hasBlockingOmissions()).isFalse();
        assertThat(report.blockingIssueCount()).isZero();
        assertThat(report.advisoryIssueCount()).isEqualTo(1);
        // Still reported: approval binds a digest over the COMPLETE issue set.
        assertThat(report.isComplete()).isFalse();
        assertThat(report.issueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should still block when a content failure accompanies a page-script error")
    void shouldStillBlock_whenContentFailureAccompaniesPageScriptError() {
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(1, 0, 1, 0, false, false, false, false);

        assertThat(report.hasBlockingOmissions()).isTrue();
        assertThat(report.blockingIssueCount()).isEqualTo(1);
        assertThat(report.advisoryIssueCount()).isEqualTo(1);
        assertThat(report.issueCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("should name the conditions present so a blocked render can be diagnosed")
    void shouldNameConditionsPresent_forOperatorDiagnosis() {
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(2, 0, 5, 1, false, true, false, false);

        // blockingOnly omits the advisory console count, so the log names exactly what refused.
        // blockingOnly lists only what actually refused, so the advisory conditions are absent.
        assertThat(report.describe(true)).isEqualTo("failedContentResources=2");
        assertThat(report.describe(false))
                .contains("severeConsoleErrors=5")
                .contains("containedInteractions=1")
                .contains("timerCompatibilityFailure")
                .contains("failedContentResources=2");
        // Identifiers and counts only: no URL, filename or rendered text may cross this boundary.
        assertThat(EFormRenderCompletenessReport.complete().describe(true)).isEqualTo("none");
        assertThat(EFormRenderCompletenessReport.complete().describe(false)).isEqualTo("none");
    }

    @Test
    @DisplayName("should not block for a failed legacy timer, but still report it")
    void shouldNotBlock_forFailedLegacyTimer() {
        // The renderer now waits for the form's string timers before capturing, so a failure here is
        // real rather than a race. It is still not grounds to withhold: across the shared-form corpus
        // the dominant such timer is setTimeout("SubmitButton.click()", 1800) - a form submitting
        // itself - whose failure on a render surface is the correct outcome.
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(0, 0, 0, 0, false, true, false, false);

        assertThat(report.hasBlockingOmissions()).isFalse();
        assertThat(report.advisoryIssueCount()).isEqualTo(1);
        assertThat(report.issueCount()).isEqualTo(1);
        assertThat(report.describe(false)).contains("timerCompatibilityFailure");
    }

    @Test
    @DisplayName("should not block for a dialog the renderer suppressed, but still report it")
    void shouldNotBlock_forSuppressedDialog() {
        // The renderer stubs alert/confirm/prompt/window.open because a modal would hang it. A
        // suppressed dialog removes nothing from the document - across the corpus these are
        // print-time warnings, tickler notices and data-quality messages - and blocking meant a form
        // that warns the user at print time could never be printed.
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(0, 0, 0, 2, false, false, false, false);

        assertThat(report.hasBlockingOmissions()).isFalse();
        assertThat(report.advisoryIssueCount()).isEqualTo(2);
        assertThat(report.describe(false)).contains("containedInteractions=2");
        assertThat(report.describe(true)).isEqualTo("none");
    }

    @Test
    @DisplayName("should report every non-console condition as blocking")
    void shouldReportEveryNonConsoleCondition_asBlocking() {
        // Guards the split itself: if a new component is added to the record and quietly lands on
        // the advisory side, this fails rather than silently weakening the gate.
        assertThat(new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false)
                .hasBlockingOmissions()).isTrue();
        assertThat(new EFormRenderCompletenessReport(0, 1, 0, 0, false, false, false, false)
                .hasBlockingOmissions()).isTrue();
        assertThat(new EFormRenderCompletenessReport(0, 0, 0, 0, true, false, false, false)
                .hasBlockingOmissions()).isTrue();
        assertThat(new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, true, false)
                .hasBlockingOmissions()).isTrue();
        assertThat(new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, false, true)
                .hasBlockingOmissions()).isTrue();
        assertThat(EFormRenderCompletenessReport.complete().hasBlockingOmissions()).isFalse();
    }

    @Test
    @DisplayName("should treat a stubbed lab decision-support script as incomplete")
    void shouldTreatStubbedLabDecisionSupport_asIncomplete() {
        // The stub is deployed under the real script filename, so the request returns 200 and the
        // network scan cannot distinguish it from a working form; without this signal a requisition
        // renders "complete" with unpopulated fields and no tickler.
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, false, true);

        assertThat(report.isComplete()).isFalse();
        assertThat(report.issueCount()).isEqualTo(1);
    }

    /**
     * Every component must be disclosed on every approval surface.
     *
     * <p>A clinician's approval is bound by {@code digest()}, which covers all components. If a
     * surface lists only some of them, the page can show every condition as {@code 0}/{@code false}
     * under a generic message while the render was in fact blocked by the omitted one — the
     * clinician then approves a document without being told what is wrong with it.</p>
     *
     * <p>This is a source scan rather than a reflective check because the disclosure sites are
     * hand-written {@code setAttribute}/{@code json.put} lists in three different files. A comment
     * asking future editors to keep them in step already existed at one of those sites and did not
     * prevent {@code providerStampMissing} from being omitted from two of the three, which is why
     * this is a test.</p>
     */
    @Test
    @DisplayName("should disclose every report component on every approval surface")
    void shouldDiscloseEveryComponent_onEveryApprovalSurface() throws Exception {
        List<String> components = Stream.of(EFormRenderCompletenessReport.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();
        assertThat(components).hasSizeGreaterThanOrEqualTo(9);

        List<String> surfaces = List.of(
                "src/main/java/io/github/carlos_emr/carlos/eform/actions/AddEForm2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/fax/action/Fax2Action.java",
                "src/main/java/io/github/carlos_emr/carlos/documentManager/actions/DocumentPreview2Action.java",
                "src/main/webapp/WEB-INF/jsp/eform/EFormRenderMissingContent.jsp",
                "src/main/webapp/WEB-INF/jsp/fax/EFormMissingContent.jsp",
                // The consumer of DocumentPreview2Action's JSON. Omitting it from this list is why
                // providerStampMissing was fixed on the producer and stayed missing on the page the
                // clinician actually reads.
                "src/main/webapp/WEB-INF/jsp/documentManager/attachDocument.jsp");

        for (String surface : surfaces) {
            String source = Files.readString(Path.of(surface), StandardCharsets.UTF_8);
            assertThat(components)
                    .describedAs("%s must publish every completeness component; approval binds a "
                            + "digest over all of them", surface)
                    .allSatisfy(component -> assertThat(source).contains(component));
        }
    }
}
