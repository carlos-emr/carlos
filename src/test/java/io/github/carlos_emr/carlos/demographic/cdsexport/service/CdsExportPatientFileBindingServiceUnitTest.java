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
import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit") @Tag("cds-export")
class CdsExportPatientFileBindingServiceUnitTest {
    private final Map<String, String> provider = new HashMap<>();
    private final Map<String, String> demographic = new HashMap<>();
    CdsExportPatientFileBindingServiceUnitTest() {
        CdsExportProviderRowDto.COLUMNS.forEach(column -> provider.put(column, null));
        provider.put("provider_no", "111111"); provider.put("first_name", "Physician"); provider.put("last_name", "Source"); provider.put("ohip_no", "123456");
        demographic.put("demographic_no", "27"); demographic.put("provider_no", "111111");
    }
    private CdsExportCapturedTextRowDto physician() { return new CdsExportCapturedTextRowDto("provider", CdsExportPublicProviderSourceReader.key("111111"), provider); }
    private CdsExportPatientFileDto bind(String selected, String birth, String patientId, List<CdsExportCapturedTextRowDto> providers) throws Exception {
        var rows = new ArrayList<>(providers); rows.add(new CdsExportCapturedTextRowDto("demographic", "0000000027", demographic));
        var xml = new CdsExportXmlValidationDto(patientId, "Patient", "Source", birth, Map.of(CdsCategory.DEMOGRAPHICS, 1L), 0, 0, 0, "a".repeat(64), 100);
        return CdsExportPatientFileBindingService.fromRows(27, selected, xml, rows, () -> {});
    }
    @Test void shouldUseFrozenRosterPhysicianAndActualXmlIdentity_withoutInventingClinicalMetadata() throws Exception {
        var result = bind("111111", "1971-03-04-05:00", "C27", List.of(physician()));
        assertThat(result.physicianFirstName()).isEqualTo("Physician"); assertThat(result.physicianBillingNumber()).isEqualTo("123456");
        assertThat(result.dateOfBirth()).isEqualTo(java.time.LocalDate.of(1971, 3, 4));
        assertThat(new CdsExportPathComposer().compose(result).patientXmlFile()).isEqualTo("Patient_Source_C27_04031971.xml");
    }
    @Test void shouldRefuseRosterOwnershipChangedSinceSelection() {
        assertThatThrownBy(() -> bind("222222", "1971-03-04", "C27", List.of(physician()))).hasMessage("CDS_EXPORT_SOURCE_CHANGED");
    }
    @Test void shouldInterpretLegalDateWhitespace_withoutChangingTheXmlDateOrShiftingItsCalendarDay() throws Exception {
        var result = bind("111111", " \t1971-03-04-05:00\r\n", "C27", List.of(physician()));
        assertThat(result.dateOfBirth()).isEqualTo(java.time.LocalDate.of(1971, 3, 4));
    }
    @Test void shouldRefuseMissingPhysician_insteadOfUsingTheOperator() {
        demographic.put("provider_no", null);
        assertThatThrownBy(() -> bind(null, "1971-03-04", "C27", List.of(physician()))).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }
    @Test void shouldRefuseMissingPublicPhysicianMetadata() {
        assertThatThrownBy(() -> bind("111111", "1971-03-04", "C27", List.of())).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }
    @Test void shouldRefuseCompetingPhysicianRows_evenWhenTheirNamesMatch() {
        assertThatThrownBy(() -> bind("111111", "1971-03-04", "C27", List.of(physician(), physician()))).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }
    @Test void shouldNeverSubstituteCpsoOrLocalProviderId_forMissingOhip() {
        provider.put("ohip_no", null); provider.put("practitionerNoType", "CPSO"); provider.put("practitionerNo", "123456");
        assertThatThrownBy(() -> bind("111111", "1971-03-04", "C27", List.of(physician()))).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }
    @Test void shouldRefusePartialBirthDates_insteadOfInventingAFilenameDate() {
        assertThatThrownBy(() -> bind("111111", "1971-03", "C27", List.of(physician()))).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }
    @Test void shouldRejectAFileForAnotherPatient() {
        assertThatThrownBy(() -> bind("111111", "1971-03-04", "C28", List.of(physician()))).hasMessage("CDS_EXPORT_PATIENT_FILE_BINDING_INVALID");
    }
    @Test void shouldRefuseUnrepresentablePhysicianNames_withoutStrippingCharacters() {
        provider.put("first_name", "Source/Name");
        assertThatThrownBy(() -> bind("111111", "1971-03-04", "C27", List.of(physician()))).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }
}
