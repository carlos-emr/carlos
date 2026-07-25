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
    void shouldMergeEveryCategory() {
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(2, 0, true, false)
                        .merge(new EFormRenderCompletenessReport(1, 3, false, true));

        assertThat(report.failedContentResources()).isEqualTo(3);
        assertThat(report.excludedContentElements()).isEqualTo(3);
        assertThat(report.signatureMissing()).isTrue();
        assertThat(report.timerCompatibilityFailure()).isTrue();
        assertThat(report.issueCount()).isEqualTo(8);
        assertThat(report.isComplete()).isFalse();
    }

    @Test
    @DisplayName("should produce a stable digest that changes with the issue set")
    void shouldDigestExactIssueSet() {
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(1, 2, false, true);

        assertThat(report.digest()).hasSize(64).isEqualTo(report.digest());
        assertThat(report.digest()).isNotEqualTo(
                new EFormRenderCompletenessReport(1, 3, false, true).digest());
        assertThat(EFormRenderCompletenessReport.complete().isComplete()).isTrue();
    }

    @Test
    @DisplayName("should reject invalid negative counters")
    void shouldRejectNegativeCounters() {
        assertThatThrownBy(() ->
                new EFormRenderCompletenessReport(-1, -2, false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
