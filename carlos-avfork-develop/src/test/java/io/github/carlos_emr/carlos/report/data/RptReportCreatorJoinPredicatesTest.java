/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RptReportCreator#joinPredicates(String...)} — the helper
 * extracted from the 4 inline WHERE-assembly sites in {@code RptDownloadCSVServlet}.
 * Guards against stray {@code and  and} tokens when one or more filter fragments
 * are empty.
 */
@Tag("unit")
@Tag("report")
class RptReportCreatorJoinPredicatesTest {

    @Test
    @DisplayName("should return empty string when all fragments are empty")
    void shouldReturnEmpty_whenAllFragmentsEmpty() {
        assertThat(RptReportCreator.joinPredicates("", "", "")).isEmpty();
    }

    @Test
    @DisplayName("should return empty string for no fragments")
    void shouldReturnEmpty_whenNoFragments() {
        assertThat(RptReportCreator.joinPredicates()).isEmpty();
    }

    @Test
    @DisplayName("should return single fragment unchanged when only one non-empty fragment")
    void shouldReturnSingleFragment_whenOnlyOneNonEmpty() {
        assertThat(RptReportCreator.joinPredicates("a=1", "", "")).isEqualTo("a=1");
    }

    @Test
    @DisplayName("should join two non-empty fragments with ' and '")
    void shouldJoinTwoFragments_withAnd() {
        assertThat(RptReportCreator.joinPredicates("a=1", "b=2")).isEqualTo("a=1 and b=2");
    }

    @Test
    @DisplayName("should skip empty fragment in the middle, producing no stray conjunction")
    void shouldSkipEmptyMiddleFragment() {
        assertThat(RptReportCreator.joinPredicates("a=1", "", "c=3")).isEqualTo("a=1 and c=3");
    }

    @Test
    @DisplayName("should skip leading empty fragment")
    void shouldSkipLeadingEmpty() {
        assertThat(RptReportCreator.joinPredicates("", "b=2", "c=3")).isEqualTo("b=2 and c=3");
    }

    @Test
    @DisplayName("should skip trailing empty fragment")
    void shouldSkipTrailingEmpty() {
        assertThat(RptReportCreator.joinPredicates("a=1", "b=2", "")).isEqualTo("a=1 and b=2");
    }

    @Test
    @DisplayName("should treat null fragment as empty")
    void shouldTreatNull_asEmpty() {
        assertThat(RptReportCreator.joinPredicates("a=1", null, "c=3")).isEqualTo("a=1 and c=3");
    }

    @Test
    @DisplayName("should join three non-empty fragments")
    void shouldJoinThreeFragments() {
        assertThat(RptReportCreator.joinPredicates("a=1", "b=2", "c=3"))
                .isEqualTo("a=1 and b=2 and c=3");
    }
}
