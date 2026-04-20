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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.utility.LocaleUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DemographicEditHelper Tests")
@Tag("unit")
@Tag("demographic")
class DemographicEditHelperTest {

    private final String originalBaseName = LocaleUtils.BASE_NAME;

    @AfterEach
    void restoreLocaleUtilsBaseName() {
        LocaleUtils.BASE_NAME = originalBaseName;
    }

    @Test
    @DisplayName("should map supported and unknown sex codes to expected resource keys")
    void shouldMapSexCodesToExpectedResourceKeys() {
        assertThat(DemographicEditHelper.getGenderMessageKey("M")).isEqualTo("global.gender.male");
        assertThat(DemographicEditHelper.getGenderMessageKey("f")).isEqualTo("global.gender.female");
        assertThat(DemographicEditHelper.getGenderMessageKey(" X ")).isEqualTo("global.gender.intersex");
        assertThat(DemographicEditHelper.getGenderMessageKey("O")).isEqualTo("global.gender.other");
        assertThat(DemographicEditHelper.getGenderMessageKey(null)).isEqualTo("global.gender.undisclosed");
        assertThat(DemographicEditHelper.getGenderMessageKey("unknown")).isEqualTo("global.gender.undisclosed");
    }

    @Test
    @DisplayName("should return English fallback text when locale bundle is incomplete")
    void shouldReturnEnglishFallbackTextWhenLocaleBundleIsIncomplete() {
        LocaleUtils.BASE_NAME = "testbundles.genderMessages";

        assertThat(DemographicEditHelper.getGenderDisplayText(Locale.FRENCH, "F")).isEqualTo("Female");
        assertThat(DemographicEditHelper.getGenderDisplayText(Locale.FRENCH, null)).isEqualTo("Undisclosed");
    }
}
