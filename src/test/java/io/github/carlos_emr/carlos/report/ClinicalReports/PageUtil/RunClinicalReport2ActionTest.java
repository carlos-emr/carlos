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
package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("RunClinicalReport2Action Tests")
@Tag("unit")
@Tag("report")
class RunClinicalReport2ActionTest {

    @Test
    @DisplayName("should evict oldest report when history reaches maximum size")
    void shouldEvictOldestReport_whenHistoryReachesMaximumSize() {
        ArrayList<Integer> clinicalReports = new ArrayList<>();
        IntStream.rangeClosed(1, RunClinicalReport2Action.MAX_CLINICAL_REPORT_HISTORY)
            .forEach(clinicalReports::add);

        RunClinicalReport2Action.trimClinicalReportHistory(clinicalReports);

        assertThat(clinicalReports)
            .hasSize(RunClinicalReport2Action.MAX_CLINICAL_REPORT_HISTORY - 1)
            .doesNotContain(1)
            .containsExactly(IntStream.rangeClosed(2, RunClinicalReport2Action.MAX_CLINICAL_REPORT_HISTORY)
                .boxed()
                .toArray(Integer[]::new));
    }

    @Test
    @DisplayName("should keep report history unchanged when under maximum size")
    void shouldKeepReportHistoryUnchanged_whenUnderMaximumSize() {
        ArrayList<Integer> clinicalReports = new ArrayList<>();
        IntStream.rangeClosed(1, RunClinicalReport2Action.MAX_CLINICAL_REPORT_HISTORY - 1)
            .forEach(clinicalReports::add);

        RunClinicalReport2Action.trimClinicalReportHistory(clinicalReports);

        assertThat(clinicalReports)
            .hasSize(RunClinicalReport2Action.MAX_CLINICAL_REPORT_HISTORY - 1)
            .containsExactly(IntStream.rangeClosed(1, RunClinicalReport2Action.MAX_CLINICAL_REPORT_HISTORY - 1)
                .boxed()
                .toArray(Integer[]::new));
    }
}
