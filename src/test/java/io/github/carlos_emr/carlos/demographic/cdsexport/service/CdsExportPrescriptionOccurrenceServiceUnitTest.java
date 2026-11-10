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
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.render;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportPrescriptionOccurrenceServiceUnitTest {
    static CdsExportCapturedTextRowDto parent(int patient) {
        var fields = new LinkedHashMap<String, String>();
        Plan.PRESCRIPTIONS.columns().forEach(c -> fields.put(c, null));
        fields.put("script_no", "71");
        fields.put("demographic_no", Integer.toString(patient));
        fields.put("textView", "Exact printed café 😀 prescription\r\nSIG");
        fields.put("rx_comments", "Clinical parent comment");
        return new CdsExportCapturedTextRowDto(
                "prescription", CdsExportPatientTableSourceReader.key(71), fields);
    }

    @Test
    void shouldFreezeTheExactPublicParentAuthor_withoutExposingPrivateProviderFields()
            throws Exception {
        var fields = new LinkedHashMap<>(parent(27).fields());
        fields.put("provider_no", "p27");
        var parent = new CdsExportCapturedTextRowDto("prescription", "0000000071", fields);
        var author =
                new CdsExportCapturedTextRowDto(
                        "provider",
                        CdsExportPublicProviderSourceReader.key("p27"),
                        Map.of(
                                "provider_no",
                                "p27",
                                "first_name",
                                "Native",
                                "last_name",
                                "Script Author",
                                "ohip_no",
                                "001234",
                                "practitionerNo",
                                "",
                                "practitionerNoType",
                                ""));
        var source = new CdsExportPrescriptionOccurrenceService.Source(parent, author);
        var assessments = CdsExportPrescriptionOccurrenceService.assessments(source);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(source.rows(), assessments, 1000, () -> {});
        render(
                CdsCategory.NEW_CATEGORY,
                xml -> CdsExportPrescriptionOccurrenceService.write(source, xml));
        assertThatThrownBy(
                        () ->
                                new CdsExportPrescriptionOccurrenceService.Source(
                                        parent,
                                        new CdsExportCapturedTextRowDto(
                                                "provider",
                                                "pbad",
                                                Map.of("provider_no", "different"))))
                .hasMessage("CDS_EXPORT_PRESCRIPTION_AUTHOR_SCOPE_INVALID");
    }

    @Test
    void shouldKeepLinkedAndStandaloneScriptHistoryAsIndependentlyAccountedClinicalContext()
            throws Exception {
        var parent = parent(27);
        var drug =
                CdsExportMedicationMapperUnitTest.medication(
                        CdsExportNativeMedicationMapperUnitTest.source());
        CdsExportPrescriptionOccurrenceService.requireOwnership(27, List.of(parent), List.of(drug));
        CdsExportPrescriptionOccurrenceService.requireOwnership(27, List.of(parent), List.of());
        var fields =
                CdsExportPrescriptionOccurrenceService.assessments(
                        new CdsExportPrescriptionOccurrenceService.Source(parent, null));
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(List.of(parent), fields, 1000, () -> {});
        render(
                CdsCategory.NEW_CATEGORY,
                xml ->
                        CdsExportPrescriptionOccurrenceService.write(
                                new CdsExportPrescriptionOccurrenceService.Source(parent, null),
                                xml));
    }

    @Test
    void shouldRefuseOrphanCrossPatientDuplicateAndSpoofedParents() throws Exception {
        var drug =
                CdsExportMedicationMapperUnitTest.medication(
                        CdsExportNativeMedicationMapperUnitTest.source());
        for (var parents :
                List.of(
                        List.<CdsExportCapturedTextRowDto>of(),
                        List.of(parent(28)),
                        List.of(parent(27), parent(27))))
            assertThatThrownBy(
                            () ->
                                    CdsExportPrescriptionOccurrenceService.requireOwnership(
                                            27, parents, List.of(drug)))
                    .hasMessage("CDS_EXPORT_PRESCRIPTION_OWNERSHIP_REQUIRES_REVIEW");
        var spoofed =
                new CdsExportCapturedTextRowDto("prescription", "0000000072", parent(27).fields());
        assertThatThrownBy(
                        () ->
                                CdsExportPrescriptionOccurrenceService.requireOwnership(
                                        27, List.of(spoofed), List.of(drug)))
                .isInstanceOf(java.io.IOException.class);
    }
}
