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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientFileDto;
import java.time.LocalDate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportLayoutUnitTest {
    private final CdsExportPathComposer paths = new CdsExportPathComposer();

    @Test void shouldApplyPhysicianPatientAndBirthDateLayout_forDp0111() {
        var path = paths.compose(patient("Élodie", "O'Neill"));
        assertThat(path.physicianFolder()).isEqualTo("Alice_Test_123456");
        assertThat(path.patientXmlFile()).isEqualTo("Élodie_O'Neill_C27_04031971.xml");
    }

    @Test void shouldPreserveDistinctNames_withoutLegacyFilenameSanitization() {
        assertThat(paths.compose(patient("Élodie", "Smith")).patientXmlFile())
                .isNotEqualTo(paths.compose(patient("Elodie", "Smith")).patientXmlFile());
        assertThat(paths.compose(patient("Anne-Marie", "O'Neill")).patientXmlFile())
                .isEqualTo("Anne-Marie_O'Neill_C27_04031971.xml");
    }

    @Test void shouldRefuseUnrepresentableComponents_withoutTruncationOrPathFallback() {
        for (String name : new String[]{"../name", "a/b", "a\\b", "a:b", "a\nname", " name", "name.", "a\uD800"}) {
            assertThatThrownBy(() -> paths.compose(patient(name, "Test")))
                    .hasMessage("CDS_EXPORT_FILENAME_NAME_REQUIRES_REVIEW");
        }
        assertThatThrownBy(() -> paths.compose(patient("a".repeat(1025), "Test")))
                .hasMessage("CDS_EXPORT_FILENAME_NAME_REQUIRES_REVIEW");
    }

    @Test void shouldPreserveLongUnicodeLogicalNames_withoutApplyingPhysicalStagingFilenameLimits() {
        assertThat(paths.compose(patient("界".repeat(100), "Test")).patientXmlFile())
                .isEqualTo("界".repeat(100) + "_Test_C27_04031971.xml");
    }

    @Test void shouldRefuseMissingIdentity_insteadOfUsingAnOperatorOrCurrentDate() {
        var p = patient("Patient", "Test");
        assertThatThrownBy(() -> paths.compose(new CdsExportPatientFileDto(p.physicianFirstName(),
                p.physicianLastName(), null, p.patientFirstName(), p.patientLastName(),
                p.senderPatientId(), p.dateOfBirth())))
                .hasMessage("CDS_EXPORT_PHYSICIAN_BILLING_NUMBER_REQUIRED");
        assertThatThrownBy(() -> paths.compose(new CdsExportPatientFileDto(p.physicianFirstName(),
                p.physicianLastName(), p.physicianBillingNumber(), p.patientFirstName(),
                p.patientLastName(), p.senderPatientId(), null)))
                .hasMessage("CDS_EXPORT_EXACT_BIRTH_DATE_REQUIRED");
    }

    @Test void shouldIdentifyActualSchemaVersion_andKeepLogicalProfilesDistinct() {
        var header = new CdsExportHeaderComposer();
        String omd = header.comment("2026.08.0-beta7-SNAPSHOT", CdsLogicalProfile.ONTARIO_MD_5_2);
        String pcon = header.comment("2026.08.0-beta7-SNAPSHOT", CdsLogicalProfile.PCON_2024_02);
        assertThat(omd).contains("CARLOS EMR 2026.08.0-beta7-SNAPSHOT",
                "EMR_Data_Migration_Schema.xsd version 1.0", "2017-08-04", "omd-5.2");
        assertThat(pcon).contains("pcon-2024-02").isNotEqualTo(omd);
        assertThatThrownBy(() -> header.comment("broken--comment", CdsLogicalProfile.ONTARIO_MD_5_2))
                .hasMessage("CDS_EXPORT_APPLICATION_VERSION_INVALID");
    }

    private CdsExportPatientFileDto patient(String first, String last) {
        return new CdsExportPatientFileDto("Alice", "Test", "123456", first, last, "C27",
                LocalDate.of(1971, 3, 4));
    }
}
