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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EctConsultationFormRequest2Form#getFormattedHealthCard()}.
 *
 * <p>The consultation request header previously concatenated the number, version code and card
 * type with no separators. The values below are synthetic and do not identify a real card.</p>
 *
 * @since 2026-09-24
 */
@DisplayName("EctConsultationFormRequest2Form health card formatting")
@Tag("unit")
@Tag("encounter")
class EctConsultationFormRequest2FormHealthCardUnitTest {

    private static final String NUMBER = "0000000000";

    private static EctConsultationFormRequest2Form formWith(String number, String version, String type) {
        EctConsultationFormRequest2Form form = new EctConsultationFormRequest2Form();
        form.setPatientHealthNum(number);
        form.setPatientHealthCardVersionCode(version);
        form.setPatientHealthCardType(type);
        return form;
    }

    @Test
    @DisplayName("should separate number, version code and card type")
    void shouldSeparateAllParts_whenAllPartsPresent() {
        assertThat(formWith(NUMBER, "AB", "ON").getFormattedHealthCard()).isEqualTo("0000000000 AB (ON)");
    }

    @Test
    @DisplayName("should omit the version code when it is absent")
    void shouldOmitVersionCode_whenVersionCodeAbsent() {
        assertThat(formWith(NUMBER, null, "ON").getFormattedHealthCard()).isEqualTo("0000000000 (ON)");
    }

    @Test
    @DisplayName("should render the number alone when version and type are absent")
    void shouldRenderNumberOnly_whenVersionAndTypeAbsent() {
        assertThat(formWith(NUMBER, "", " ").getFormattedHealthCard()).isEqualTo(NUMBER);
    }

    @Test
    @DisplayName("should render the version and type when the number is absent")
    void shouldRenderVersionAndType_whenNumberAbsent() {
        assertThat(formWith(null, "AB", "ON").getFormattedHealthCard()).isEqualTo("AB (ON)");
    }

    @Test
    @DisplayName("should return empty text when nothing is on file")
    void shouldReturnEmpty_whenNothingOnFile() {
        assertThat(formWith(null, null, null).getFormattedHealthCard()).isEmpty();
    }
}
