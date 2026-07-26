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
