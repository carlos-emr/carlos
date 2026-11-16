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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Owner decision 2026-11-14: a job that selects LaboratoryResults without Reports still writes the
 * legacy-format laboratory reports (MDS, CML, BC PathNet) in Reports, as Lab Report entries, and
 * nothing else of Reports; the patient is never held for them.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportLegacyLaboratoryReportsUnitTest {
    private static CdsExportCommand command(Set<CdsCategory> categories) {
        return new CdsExportCommand(
                "legacy-lab-proof",
                "999998",
                null,
                CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(),
                List.of(27),
                categories,
                "receiver",
                "audit");
    }

    @Test
    void shouldWriteOnlyTheLegacyReportsInReports_whenLaboratoryResultsIsSelectedWithoutReports() {
        var labs = command(Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS));
        assertThat(CdsExportLegacyLaboratoryReports.withoutReports(labs)).isTrue();
        assertThat(CdsExportLegacyLaboratoryReports.sections(labs.categories()))
                .containsExactlyInAnyOrder(
                        CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS, CdsCategory.REPORTS);
        // Read under LaboratoryResults' right: the job may not hold Reports' at all.
        assertThat(CdsExportLegacyLaboratoryReports.authority(labs))
                .isEqualTo(CdsCategory.LABORATORY_RESULTS);
        var legacy = CdsExportLegacyLaboratoryReports.tables();
        assertThat(CdsExportLegacyLaboratoryReports.onlyLegacy(labs, legacy)).isTrue();
        assertThat(CdsExportLegacyLaboratoryReports.onlyLegacy(labs, Set.of("mdsMSH"))).isTrue();
        // A scope with any other data-summary table is not this job's: it is refused upstream.
        var wider = new HashSet<>(legacy);
        wider.add("tickler");
        assertThat(CdsExportLegacyLaboratoryReports.onlyLegacy(labs, wider)).isFalse();
        assertThat(CdsExportLegacyLaboratoryReports.onlyLegacy(labs, Set.of("labRequestReportLink")))
                .isFalse();
    }

    @Test
    void shouldLeaveTheFileAsSelected_whenReportsIsSelectedOrLaboratoryResultsIsNot() {
        for (var selected :
                List.of(
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS, CdsCategory.REPORTS),
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS),
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS))) {
            var job = command(selected);
            assertThat(CdsExportLegacyLaboratoryReports.withoutReports(job)).as(selected.toString()).isFalse();
            assertThat(CdsExportLegacyLaboratoryReports.sections(selected)).isEqualTo(selected);
            assertThat(CdsExportLegacyLaboratoryReports.onlyLegacy(job, Set.of("mdsMSH"))).isFalse();
        }
        // With Reports selected the legacy reports are Reports' records, under Reports' right.
        assertThat(
                        CdsExportLegacyLaboratoryReports.authority(
                                command(
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.LABORATORY_RESULTS,
                                                CdsCategory.REPORTS))))
                .isEqualTo(CdsCategory.REPORTS);
    }

    @Test
    void shouldCountTheThreeLegacyKinds_andNoOtherDataSummary() {
        var counts =
                Map.of(
                        CdsExportDataSummaryRegistry.Kind.MDS_LAB_REPORT.label(), 2L,
                        CdsExportDataSummaryRegistry.Kind.CML_LAB_REPORT.label(), 1L,
                        CdsExportDataSummaryRegistry.Kind.PATHNET_LAB_REPORT.label(), 4L,
                        CdsExportDataSummaryRegistry.Kind.LAB_REQUISITION_LINK.label(), 3L,
                        CdsExportDataSummaryRegistry.Kind.TICKLER.label(), 5L);
        assertThat(CdsExportLegacyLaboratoryReports.count(counts)).isEqualTo(7L);
        assertThat(CdsExportLegacyLaboratoryReports.count(Map.of())).isZero();
    }
}
