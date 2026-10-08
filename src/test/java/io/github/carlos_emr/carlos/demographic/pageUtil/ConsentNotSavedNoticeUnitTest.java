/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.commn.model.ConsentType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConsentNotSavedNotice}: the value is built from consent type ids only,
 * and a value received from a request is used only when it is digits and commas.
 *
 * @since 2026-09-29
 */
@DisplayName("ConsentNotSavedNotice Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("demographic")
@Tag("consent")
class ConsentNotSavedNoticeUnitTest {

    private static ConsentType consentType(Integer id, String type, String name) {
        ConsentType consentType = new ConsentType();
        consentType.setId(id);
        consentType.setType(type);
        consentType.setName(name);
        return consentType;
    }

    @Test
    @DisplayName("should join the consent type ids with commas")
    void shouldJoinIds_forRefusedConsentTypes() {
        assertThat(ConsentNotSavedNotice.parameterValue(List.of(
                consentType(7, "email", "Email"), consentType(9, "sms", "SMS"), consentType(7, "email", "Email"))))
                .isEqualTo("7,9");
    }

    @Test
    @DisplayName("should build an empty value when nothing was refused")
    void shouldReturnEmptyValue_whenNothingRefused() {
        assertThat(ConsentNotSavedNotice.parameterValue(List.of())).isEmpty();
        assertThat(ConsentNotSavedNotice.parameterValue(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"7", "7,9", " 7,9 ", "0,123456789"})
    @DisplayName("should accept digits and commas")
    void shouldAcceptValue_whenDigitsAndCommasOnly(String value) {
        assertThat(ConsentNotSavedNotice.validated(value)).isEqualTo(value.trim());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", ",", "7,", ",7", "7,,9", "7;9", "-7", "7 9", "abc", "7<script>",
            "7&demographic_no=1", "1234567890", "7%2C9"})
    @DisplayName("should reject anything that is not digits and commas")
    void shouldRejectValue_whenNotDigitsAndCommas(String value) {
        assertThat(ConsentNotSavedNotice.validated(value)).isNull();
    }

    @Test
    @DisplayName("should append the parameter with the right separator")
    void shouldAppendParameter_withTheRightSeparator() {
        assertThat(ConsentNotSavedNotice.appendTo("/carlos/demographic/DemographicEdit?demographic_no=42", "7,9"))
                .isEqualTo("/carlos/demographic/DemographicEdit?demographic_no=42&consentNotSaved=7,9");
        assertThat(ConsentNotSavedNotice.appendTo("/carlos/demographic/DemographicEdit", "7"))
                .isEqualTo("/carlos/demographic/DemographicEdit?consentNotSaved=7");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "7&next=//example.org", "7\r\nSet-Cookie: x=1"})
    @DisplayName("should leave the URL unchanged for an empty or malformed value")
    void shouldLeaveUrlUnchanged_whenValueIsEmptyOrMalformed(String value) {
        assertThat(ConsentNotSavedNotice.appendTo("/carlos/demographic/DemographicEdit?demographic_no=42", value))
                .isEqualTo("/carlos/demographic/DemographicEdit?demographic_no=42");
    }

    @Test
    @DisplayName("should look up display names for the ids, in the page's order")
    void shouldReturnDisplayNames_forKnownIds() {
        List<ConsentType> shown = List.of(consentType(7, "email", "Email consent"),
                consentType(8, "fax", "Fax consent"), consentType(9, "sms", "SMS consent"));

        assertThat(ConsentNotSavedNotice.consentTypeNames("9,7,404", shown))
                .containsExactly("Email consent", "SMS consent");
    }

    @Test
    @DisplayName("should fall back to the type code when a consent type has no display name")
    void shouldUseTypeCode_whenDisplayNameIsMissing() {
        assertThat(ConsentNotSavedNotice.consentTypeNames("7", List.of(consentType(7, "email", null))))
                .containsExactly("email");
    }

    @Test
    @DisplayName("should return no names for a missing or malformed value")
    void shouldReturnNoNames_whenValueIsMissingOrMalformed() {
        List<ConsentType> shown = List.of(consentType(7, "email", "Email consent"));

        assertThat(ConsentNotSavedNotice.consentTypeNames(null, shown)).isEmpty();
        assertThat(ConsentNotSavedNotice.consentTypeNames("7 OR 1=1", shown)).isEmpty();
        assertThat(ConsentNotSavedNotice.consentTypeNames("7", null)).isEmpty();
    }
}
