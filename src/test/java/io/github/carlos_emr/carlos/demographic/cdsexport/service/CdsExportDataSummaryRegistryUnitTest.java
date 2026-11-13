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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.dao.CdsExportDataSummaryReadDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportTableSchemaDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSourceCatalogue.Disposition;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The one reviewed grouping registry agrees with the source catalogue and the pinned contract. */
@Tag("unit")
@Tag("cds-export")
class CdsExportDataSummaryRegistryUnitTest {
    private static final Set<String> CDS_REPORT_CLASSES =
            Set.of(
                    "Diagnostic Imaging Report",
                    "Diagnostic Test Report",
                    "Cardio Respiratory Report",
                    "Medical Records Report",
                    "Consultant Report",
                    "Lab Report",
                    "Other Letter");

    @Test
    void shouldRegisterExactlyTheDocumentTables_ofTheSourceCatalogue() {
        var catalogue = CdsExportSourceCatalogue.current();
        assertThat(CdsExportDataSummaryRegistry.tables())
                .containsExactlyInAnyOrderElementsOf(catalogue.tables(Disposition.DOCUMENT));
        assertThat(catalogue.tables(Disposition.DOCUMENT)).hasSize(94);
        assertThat(catalogue.tables(Disposition.LISTED)).hasSize(37);
        assertThat(catalogue.required(Set.of(CdsCategory.REPORTS)))
                .containsAll(catalogue.tables(Disposition.DOCUMENT));
        for (var category : CdsCategory.values())
            if (category != CdsCategory.REPORTS)
                assertThat(catalogue.required(Set.of(category)))
                        .doesNotContainAnyElementsOf(catalogue.tables(Disposition.DOCUMENT));
        assertThatThrownBy(() -> CdsExportDataSummaryRegistry.rule("allergies"))
                .hasMessage("CDS_EXPORT_DATA_SUMMARY_TABLE_UNREGISTERED");
    }

    @Test
    void shouldReadEveryDocumentTable_throughAPatientScopeAndARowIdentity() {
        for (String table : CdsExportDataSummaryRegistry.tables()) {
            assertThat(CdsExportDataSummaryReadDao.linked(table)).as(table).isTrue();
            var plan = CdsExportDataSummaryReadDao.plan(table);
            assertThat(plan.keys()).as(table).isNotEmpty();
            var physical = CdsExportSourceCatalogue.current().physical(table).orElseThrow();
            assertThat(plan.columns())
                    .as(table)
                    .containsExactlyElementsOf(
                            physical.columns().stream()
                                    .map(CdsExportTableSchemaDto.Column::name)
                                    .toList());
            // Every record root has one integer identity, so its record identity is stable.
            if (CdsExportDataSummaryRegistry.rule(table).root())
                assertThat(plan.numericIdentity()).as(table).isTrue();
        }
        assertThat(CdsExportDataSummaryReadDao.plan("form_boolean_value").keys())
                .containsExactly("form_name", "form_id", "field_name");
        assertThat(CdsExportDataSummaryReadDao.plan("formBCAR2020Data").keys())
                .containsExactly("form_id", "page_no", "field");
        assertThat(CdsExportDataSummaryReadDao.plan("client_image").binary())
                .containsExactly("contents");
        assertThatThrownBy(() -> CdsExportDataSummaryReadDao.plan("drugs"))
                .hasMessage("CDS_EXPORT_DATA_SUMMARY_TABLE_UNKNOWN");
    }

    @Test
    void shouldNameOnlyColumnsOfThePinnedContract_inEveryLinkDateAndBinaryRule() {
        var catalogue = CdsExportSourceCatalogue.current();
        for (String table : CdsExportDataSummaryRegistry.tables()) {
            var rule = CdsExportDataSummaryRegistry.rule(table);
            var columns = columns(catalogue, table);
            if (rule.event() != null) {
                assertThat(columns).as(table).contains(rule.event().dateColumn());
                if (rule.event().timeColumn() != null)
                    assertThat(columns).as(table).contains(rule.event().timeColumn());
            }
            var plan = CdsExportDataSummaryReadDao.plan(table);
            assertThat(plan.binary()).as(table).containsAll(rule.binaries().keySet());
            for (var link : rule.links()) {
                assertThat(CdsExportDataSummaryRegistry.rule(link.parent()).root()).isTrue();
                assertThat(columns).as(table).containsAll(link.childColumns());
                if (link.constantColumn() != null)
                    assertThat(columns).as(table).contains(link.constantColumn());
                assertThat(columns(catalogue, link.parent()))
                        .as(link.parent())
                        .containsAll(link.parentColumns());
            }
            if (rule.revisioned())
                assertThat(rule.kind()).isEqualTo(CdsExportDataSummaryRegistry.Kind.ENCOUNTER_FORM);
        }
    }

    @Test
    void shouldGiveEveryKind_aCdsReportClassAndASubClassWithinSixtyCharacters() {
        for (var kind : CdsExportDataSummaryRegistry.Kind.values()) {
            assertThat(CDS_REPORT_CLASSES).contains(kind.reportClass());
            assertThat(kind.label()).isNotBlank();
        }
        for (String table : CdsExportDataSummaryRegistry.tables()) {
            var rule = CdsExportDataSummaryRegistry.rule(table);
            var revision =
                    new CdsExportDataSummaryPlanner.Group(
                            rule.kind(), table, "1", java.util.List.of(), 20_000, 20_000, null, false);
            var detached =
                    new CdsExportDataSummaryPlanner.Group(
                            rule.kind(), table, "1", java.util.List.of(), 0, 0, null, true);
            assertThat(revision.subClass().length()).as(table).isLessThanOrEqualTo(60);
            assertThat(detached.subClass().length()).as(table).isLessThanOrEqualTo(60);
        }
    }

    @Test
    void shouldHoldOnlyThisChart_whenADataSummaryTableExceedsItsPerPatientBound() throws Exception {
        var reader =
                new CdsExportDataSummarySourceReader(
                        org.mockito.Mockito.mock(CdsExportDataSummaryReadDao.class),
                        CdsExportDataSummaryReadDao.plan("formRourke2009"));
        assertThatThrownBy(
                        () ->
                                reader.requireCapacity(
                                        CdsExportDataSummarySourceReader.MAXIMUM_ROWS + 1,
                                        100_000_000))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_DATA_SUMMARY_ROW_LIMIT");
        // The legacy 1,227-column form is bounded by fields as well as rows, and by a smaller
        // configured capture limit, and each excess holds rather than stopping the job.
        assertThatThrownBy(() -> reader.requireCapacity(400, 100_000_000))
                .hasMessage("CDS_EXPORT_DATA_SUMMARY_ROW_LIMIT");
        assertThatThrownBy(() -> reader.requireCapacity(100, 100_000))
                .hasMessage("CDS_EXPORT_DATA_SUMMARY_ROW_LIMIT");
        reader.requireCapacity(300, 100_000_000);
        assertThat(reader.categories()).containsExactly(CdsCategory.REPORTS);
    }

    private static Set<String> columns(CdsExportSourceCatalogue.Snapshot catalogue, String table) {
        var result = new HashSet<String>();
        catalogue.physical(table).orElseThrow().columns().forEach(column -> result.add(column.name()));
        return result;
    }
}
