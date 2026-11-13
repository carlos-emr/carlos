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
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;

@Tag("unit")
@Tag("cds-export")
class CdsExportSourceCatalogueUnitTest {
    private final CdsExportSourceCatalogueDao source = mock(CdsExportSourceCatalogueDao.class);
    private final CdsExportSourceCatalogueReader reader =
            new CdsExportSourceCatalogueReader(source);

    private CdsExportCommand command(Set<CdsCategory> categories) {
        return new CdsExportCommand(
                "test",
                "999998",
                null,
                CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(),
                List.of(27),
                categories,
                "recipient",
                "audit");
    }

    @Test
    void shouldBindEveryRuling_toTheExactShippedOntarioAndBcPhysicalContracts() throws Exception {
        var catalogue = CdsExportSourceCatalogue.current();
        assertThat(catalogue.tables()).hasSize(511);
        assertThat(catalogue.profiles())
                .extracting(profile -> profile.tables().size())
                .containsExactly(458, 491);
        assertThat(catalogue.tables().get("eChart"))
                .isEqualTo(CdsExportSourceCatalogue.Disposition.REQUIRE_EMPTY);
        assertThat(catalogue.tables().get("allergies"))
                .isEqualTo(CdsExportSourceCatalogue.Disposition.MAPPED_SOURCE);
        assertThat(catalogue.tables().get("provider_facility"))
                .isEqualTo(CdsExportSourceCatalogue.Disposition.OPERATIONAL);
        assertThat(catalogue.tables().get("billing_filename_import"))
                .isEqualTo(CdsExportSourceCatalogue.Disposition.OPERATIONAL);
        assertThat(
                        new CdsExportSourceSchemaValidator()
                                .verify(catalogue.scope(), Set.of("catalogueSha256")))
                .matches("[0-9a-f]{64}");
        assertThatThrownBy(() -> catalogue.tables().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalogue.profiles().getFirst().tables().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void
            shouldKeepTheReaderRecipeIndependentOfTheCurrentlyRegisteredBeanList_forSourceCatalogueContract() {
        var catalogue = CdsExportSourceCatalogue.current();
        var installed = new ArrayList<CdsExportNativeSourceReader>();
        installed.add(reader);
        for (String table : catalogue.required(Set.of(CdsCategory.DEMOGRAPHICS))) {
            var candidate = mock(CdsExportNativeSourceReader.class);
            when(candidate.table()).thenReturn(table);
            when(candidate.categories()).thenReturn(Set.of(CdsCategory.DEMOGRAPHICS));
            when(candidate.supportedCategories()).thenReturn(Set.of(CdsCategory.DEMOGRAPHICS));
            when(candidate.forRequest(any())).thenReturn(candidate);
            when(candidate.requiredTables()).thenReturn(Set.of());
            installed.add(candidate);
        }
        var service =
                new CdsExportNativeCaptureService(
                        installed,
                        mock(CdsExportStagingFactory.class),
                        mock(CdsExportRunDao.class),
                        mock(PlatformTransactionManager.class));
        var required = service.requiredTables(command(Set.of(CdsCategory.DEMOGRAPHICS)));
        assertThat(required)
                .contains("clinical_source_catalogue", "demographic", "provider", "demographiccust")
                .doesNotContain("drugs", "cds_laboratory_result");
        installed.removeIf(candidate -> candidate.table().equals("demographiccust"));
        var incomplete =
                new CdsExportNativeCaptureService(
                        installed,
                        mock(CdsExportStagingFactory.class),
                        mock(CdsExportRunDao.class),
                        mock(PlatformTransactionManager.class));
        assertThatThrownBy(
                        () -> incomplete.requiredTables(command(Set.of(CdsCategory.DEMOGRAPHICS))))
                .hasMessage("CDS_EXPORT_CAPTURE_DEPENDENCY_MISSING");
        verifyNoInteractions(source);
    }

    @Test
    void shouldScopeUnmappedPatientRecords_withoutApprovingTheirClinicalContent() {
        var catalogue = CdsExportSourceCatalogue.current();
        assertThat(catalogue.patientScopes().get("form2MinWalk")).containsExactly("demographic_no");
        assertThat(catalogue.tables().get("form2MinWalk"))
                .isEqualTo(CdsExportSourceCatalogue.Disposition.REQUIRE_EMPTY);
        assertThat(catalogue.patientScopes().get("relationships"))
                .containsExactly("demographic_no", "relation_demographic_no");
        assertThat(catalogue.tables().get("admission"))
                .isEqualTo(CdsExportSourceCatalogue.Disposition.ACCESS_CONTROL);
        assertThat(catalogue.clinicalRemainders().get("admission"))
                .containsExactly("admission_notes", "discharge_notes", "radioDischargeReason");
        assertThatThrownBy(() -> catalogue.patientScopes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> catalogue.patientScopes().get("relationships").clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRefuseTheUnclassifiedSourcePresence_atBothIndependentCountAndValueBoundaries() {
        doThrow(
                        new CdsExportReviewException(
                                CdsExportReviewException.Reason.CLINICAL_SOURCE_UNREVIEWED))
                .when(source)
                .requireUnclassifiedSourcesEmpty(eq(27), any(Runnable.class));
        assertThatThrownBy(() -> reader.count(27))
                .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED");
        assertThatThrownBy(() -> reader.capture(27, null))
                .hasMessage("CDS_EXPORT_CLINICAL_SOURCE_UNREVIEWED");
        verify(source, times(2)).requireUnclassifiedSourcesEmpty(eq(27), any(Runnable.class));
    }

    @Test
    void shouldFreezeEachRequestProjection_withoutChangingTheInstalledSingletonScope() {
        var selected = reader.forRequest(command(Set.of(CdsCategory.DEMOGRAPHICS)));
        assertThat(selected.categories()).containsExactly(CdsCategory.DEMOGRAPHICS);
        assertThat(reader.categories()).hasSize(16);
        assertThat(selected.requiredTables()).contains("demographic").doesNotContain("appointment");
        assertThat(reader.requiredTables())
                .contains(
                        "appointment",
                        "cds_laboratory_result",
                        "measurements",
                        "native_document_files");
        verifyNoInteractions(source);
    }
}
