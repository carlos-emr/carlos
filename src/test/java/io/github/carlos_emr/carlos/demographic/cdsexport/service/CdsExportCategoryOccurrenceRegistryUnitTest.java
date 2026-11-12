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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportCategoryOccurrenceRegistryUnitTest {
    private static CdsExportCategoryOccurrenceAssembler assembler(CdsCategory category) {
        var value = mock(CdsExportCategoryOccurrenceAssembler.class);
        when(value.category()).thenReturn(category);
        return value;
    }

    @Test
    void shouldDispatchEveryRegisteredCategory_withoutAnOrderDependentFallback() {
        var laboratory = assembler(CdsCategory.LABORATORY_RESULTS);
        var appointments = assembler(CdsCategory.APPOINTMENTS);
        var additional = assembler(CdsCategory.NEW_CATEGORY);
        var registry =
                new CdsExportCategoryOccurrenceRegistry(
                        List.of(additional, appointments, laboratory));
        assertThat(registry.find(CdsCategory.LABORATORY_RESULTS)).isSameAs(laboratory);
        assertThat(registry.find(CdsCategory.APPOINTMENTS)).isSameAs(appointments);
        assertThat(registry.find(CdsCategory.NEW_CATEGORY)).isSameAs(additional);
        assertThat(registry.find(CdsCategory.REPORTS)).isNull();
    }

    @Test
    void shouldRefuseTwoAssemblersForOneCategory_insteadOfSelectingTheFirst() {
        assertThatThrownBy(
                        () ->
                                new CdsExportCategoryOccurrenceRegistry(
                                        List.of(
                                                assembler(CdsCategory.LABORATORY_RESULTS),
                                                assembler(CdsCategory.LABORATORY_RESULTS))))
                .hasMessage("CDS_EXPORT_CATEGORY_ASSEMBLER_AMBIGUOUS");
    }

    @Test
    void shouldRefuseShadowingTheCoordinatedDemographicHistoryAndCoreNativeAssemblers_forCategoryOccurrenceRegistryContract() {
        var reserved = new HashSet<>(CdsExportNoteSourceReader.CATEGORIES);
        reserved.addAll(
                Set.of(
                        CdsCategory.DEMOGRAPHICS,
                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                        CdsCategory.MEDICATIONS_AND_TREATMENTS));
        for (var category : reserved)
            assertThatThrownBy(
                            () ->
                                    new CdsExportCategoryOccurrenceRegistry(
                                            List.of(assembler(category))))
                    .hasMessage("CDS_EXPORT_CATEGORY_ASSEMBLER_AMBIGUOUS");
    }

    @Test
    void shouldRejectAnUnclassifiedAssembler_atStartup() {
        assertThatThrownBy(() -> new CdsExportCategoryOccurrenceRegistry(List.of(assembler(null))))
                .hasMessage("CDS_EXPORT_CATEGORY_ASSEMBLER_AMBIGUOUS");
    }

    @Test
    void shouldFreezeDiscovery_beforeTheCallingCollectionCanChange() {
        var selected = assembler(CdsCategory.IMMUNIZATIONS);
        var values = new ArrayList<CdsExportCategoryOccurrenceAssembler>();
        values.add(selected);
        var registry = new CdsExportCategoryOccurrenceRegistry(values);
        values.clear();
        assertThat(registry.find(CdsCategory.IMMUNIZATIONS)).isSameAs(selected);
    }

    @Test
    void shouldLeaveAnAbsentCategoryUnavailable_withoutInventingAnExportImplementation() {
        assertThat(
                        new CdsExportCategoryOccurrenceRegistry(List.of())
                                .find(CdsCategory.CARE_ELEMENTS))
                .isNull();
    }
}
