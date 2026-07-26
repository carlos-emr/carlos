/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

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
        assertThat(report.describe(true))
                .isEqualTo("failedContentResources=2 containedInteractions=1 timerCompatibilityFailure");
        assertThat(report.describe(false))
                .contains("severeConsoleErrors=5")
                .contains("failedContentResources=2");
        // Identifiers and counts only: no URL, filename or rendered text may cross this boundary.
        assertThat(EFormRenderCompletenessReport.complete().describe(true)).isEqualTo("none");
        assertThat(EFormRenderCompletenessReport.complete().describe(false)).isEqualTo("none");
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
        assertThat(new EFormRenderCompletenessReport(0, 0, 0, 1, false, false, false, false)
                .hasBlockingOmissions()).isTrue();
        assertThat(new EFormRenderCompletenessReport(0, 0, 0, 0, true, false, false, false)
                .hasBlockingOmissions()).isTrue();
        assertThat(new EFormRenderCompletenessReport(0, 0, 0, 0, false, true, false, false)
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
}
