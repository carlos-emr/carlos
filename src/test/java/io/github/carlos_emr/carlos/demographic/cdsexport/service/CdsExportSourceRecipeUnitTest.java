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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMappingDao;
import io.github.carlos_emr.carlos.commn.dao.CdsExportRunDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionDao;
import io.github.carlos_emr.carlos.commn.dao.PreventionExtDao;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

@Tag("unit")
@Tag("cds-export")
class CdsExportSourceRecipeUnitTest {
    private final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);

    private CdsExportCommand command(Set<CdsCategory> categories) {
        return new CdsExportCommand(
                "recipe-proof",
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

    private CdsExportNativeSourceReader reader(
            String table, Set<CdsCategory> categories, Set<String> dependencies) {
        var reader = mock(CdsExportNativeSourceReader.class);
        when(reader.table()).thenReturn(table);
        when(reader.categories()).thenReturn(categories);
        when(reader.supportedCategories()).thenReturn(categories);
        when(reader.requiredTables()).thenReturn(dependencies);
        when(reader.forRequest(any())).thenReturn(reader);
        return reader;
    }

    private CdsExportNativeCaptureService service(CdsExportNativeSourceReader... readers) {
        return new CdsExportNativeCaptureService(
                List.of(readers),
                stores,
                mock(CdsExportRunDao.class),
                mock(PlatformTransactionManager.class));
    }

    @Test
    void shouldIncludePartialDates_forMedicationSelectionWithoutAllergies() {
        var dates = new CdsExportPartialDateSourceReader(mock(PartialDateDao.class));
        var demographic = reader("demographic", Set.of(CdsCategory.DEMOGRAPHICS), Set.of());
        var selected =
                service(demographic, dates)
                        .requiredTables(
                                command(
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.MEDICATIONS_AND_TREATMENTS)));
        assertThat(selected).containsExactlyInAnyOrder("demographic", "partial_date");
        verifyNoInteractions(stores);
    }

    private CdsExportNativeCaptureService preventionService() {
        var snapshots = mock(CdsExportPreventionSnapshotService.class);
        var access = mock(CdsExportPreventionAccessService.class);
        var preventions = mock(PreventionDao.class);
        var extensions = mock(PreventionExtDao.class);
        var dates = mock(PartialDateDao.class);
        return service(
                reader("demographic", Set.of(CdsCategory.DEMOGRAPHICS), Set.of()),
                new CdsExportPreventionConfigurationSourceReader(snapshots),
                new CdsExportPreventionScopeSourceReader(preventions, access),
                new CdsExportCvcImmunizationSourceReader(mock(CVCImmunizationDao.class)),
                new CdsExportCvcMappingSourceReader(mock(CVCMappingDao.class)),
                new CdsExportImmunizationSourceReader(preventions, snapshots, access),
                new CdsExportImmunizationExtSourceReader(extensions, snapshots, access),
                new CdsExportImmunizationPrecisionSourceReader(dates, snapshots, access),
                new CdsExportScreeningSourceReader(preventions, snapshots, access),
                new CdsExportScreeningExtSourceReader(extensions, snapshots, access),
                new CdsExportScreeningPrecisionSourceReader(dates, snapshots, access));
    }

    @Test
    void shouldCaptureScreeningsWithTheSharedClassification_forPastHealthWithoutVaccines() {
        var categories = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.PAST_HEALTH);
        var service = preventionService();
        var selected = service.requiredTables(command(categories));
        var expected = new HashSet<>(CdsExportPreventionSources.SCREENING_REQUIRED);
        expected.add("demographic");
        assertThat(selected).isEqualTo(expected);
        var control =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl
                                .class);
        service.authorizeScope(command(categories), 27, selected, control);
        verify(control).authorizeCategory(27, CdsCategory.PAST_HEALTH, false);
        verify(control, never()).authorizeCategory(27, CdsCategory.IMMUNIZATIONS, false);
    }

    @Test
    void shouldKeepScreeningsOutOfAnImmunizationOnlyCapture_forSourceRecipeContract() {
        var selected =
                preventionService()
                        .requiredTables(
                                command(Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS)));
        assertThat(selected)
                .containsAll(CdsExportImmunizationCaptureValidator.REQUIRED)
                .doesNotContainAnyElementsOf(CdsExportPreventionSources.SCREENING_TABLES);
    }

    @Test
    void shouldCaptureBothPartitionsSeparately_whenBothCategoriesAreSelected() {
        var selected =
                preventionService()
                        .requiredTables(
                                command(
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.IMMUNIZATIONS,
                                                CdsCategory.PAST_HEALTH)));
        assertThat(selected)
                .containsAll(CdsExportImmunizationCaptureValidator.REQUIRED)
                .containsAll(CdsExportPreventionSources.SCREENING_TABLES)
                .hasSize(11);
    }

    @Test
    void shouldExcludeUnselectedPayloads_andNotReadAnyClinicalValues() {
        var demographics = reader("demographic", Set.of(CdsCategory.DEMOGRAPHICS), Set.of());
        var reports = reader("document", Set.of(CdsCategory.REPORTS), Set.of());
        assertThat(
                        service(demographics, reports)
                                .requiredTables(command(Set.of(CdsCategory.DEMOGRAPHICS))))
                .containsExactly("demographic");
        verify(reports, never()).forRequest(any());
        verify(reports, never()).schema();
        verify(reports, never()).count(anyInt());
    }

    @Test
    void shouldRefuseMissingDependencies_beforeCapture() {
        var report =
                reader("document", Set.of(CdsCategory.REPORTS), Set.of("native_document_files"));
        assertThatThrownBy(
                        () ->
                                service(report)
                                        .requiredTables(
                                                command(
                                                        Set.of(
                                                                CdsCategory.DEMOGRAPHICS,
                                                                CdsCategory.REPORTS))))
                .hasMessage("CDS_EXPORT_CAPTURE_DEPENDENCY_MISSING");
        verify(report, never()).schema();
    }

    @Test
    void shouldRefuseDependenciesThatRequireUnselectedCategories_forSourceRecipeContract() {
        var demographic =
                reader("demographic", Set.of(CdsCategory.DEMOGRAPHICS), Set.of("document"));
        var report = reader("document", Set.of(CdsCategory.REPORTS), Set.of());
        assertThatThrownBy(
                        () ->
                                service(demographic, report)
                                        .requiredTables(command(Set.of(CdsCategory.DEMOGRAPHICS))))
                .hasMessage("CDS_EXPORT_CAPTURE_DEPENDENCY_MISSING");
    }

    @Test
    void shouldRefusePartialHistory_insteadOfCapturingUnselectedNoteCategories() {
        var notes = reader("casemgmt_note", CdsExportNoteSourceReader.CATEGORIES, Set.of());
        assertThatThrownBy(
                        () ->
                                service(notes)
                                        .requiredTables(
                                                command(
                                                        Set.of(
                                                                CdsCategory.DEMOGRAPHICS,
                                                                CdsCategory.CLINICAL_NOTES))))
                .hasMessage("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseARequestProjectionThatChangesItsRegisteredTable_forSourceRecipeContract() {
        var original = reader("demographic", Set.of(CdsCategory.DEMOGRAPHICS), Set.of());
        var changed = reader("document", Set.of(CdsCategory.DEMOGRAPHICS), Set.of());
        when(original.forRequest(any())).thenReturn(changed);
        assertThatThrownBy(
                        () ->
                                service(original)
                                        .requiredTables(command(Set.of(CdsCategory.DEMOGRAPHICS))))
                .hasMessage("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
    }

    @Test
    void shouldAuthorizeRetainedSources_withoutOpeningClinicalValuesOrIssuingCaptureTickets() {
        var source = reader("demographic", Set.of(CdsCategory.DEMOGRAPHICS), Set.of());
        var control =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl
                                .class);
        service(source)
                .authorizeScope(
                        command(Set.of(CdsCategory.DEMOGRAPHICS)),
                        27,
                        Set.of("demographic"),
                        control);
        verify(control).authorizePatient(27);
        verify(control).authorizeCategory(27, CdsCategory.DEMOGRAPHICS, false);
        verify(source).authorizeReadScope(27, control);
        verify(source, never()).schema();
        verify(source, never()).count(anyInt());
        verifyNoInteractions(stores);
    }

    @Test
    void shouldRefuseRetainedSourcesOutsideTheAcceptedCategoryScope_forSourceRecipeContract() {
        var source = reader("document", Set.of(CdsCategory.REPORTS), Set.of());
        var control =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl
                                .class);
        assertThatThrownBy(
                        () ->
                                service(source)
                                        .authorizeScope(
                                                command(Set.of(CdsCategory.DEMOGRAPHICS)),
                                                27,
                                                Set.of("document"),
                                                control))
                .hasMessage("CDS_EXPORT_CAPTURE_SCOPE_INVALID");
        verifyNoInteractions(control);
    }
}
