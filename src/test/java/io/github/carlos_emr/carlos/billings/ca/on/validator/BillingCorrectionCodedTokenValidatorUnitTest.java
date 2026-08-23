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
package io.github.carlos_emr.carlos.billings.ca.on.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BillingCorrectionCodedTokenValidator")
@Tag("unit")
@Tag("billing")
class BillingCorrectionCodedTokenValidatorUnitTest {

    @ParameterizedTest
    @CsvSource({
            "rdohip, 123456",
            "hctype, ON",
            "demosex, 1",
    })
    void shouldReturnValue_whenCodedTokenPassesAllowlist(String elementName, String value) {
        assertThat(BillingCorrectionCodedTokenValidator.validate(elementName, value)).isEqualTo(value);
    }

    @Test
    void shouldCoalesceNullToEmpty_whenValueIsAbsent() {
        assertThat(BillingCorrectionCodedTokenValidator.validate("rdohip", null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "rdohip, 123<456, Referral doctor OHIP number",
            "hctype, O<N, Health card type",
            "demosex, 1';alert(1), Demographic sex code",
    })
    void shouldThrowValidationException_whenCodedTokenContainsMarkup(
            String elementName, String value, String messageFragment) {
        assertThatThrownBy(() -> BillingCorrectionCodedTokenValidator.validate(elementName, value))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining(messageFragment);
    }

    @Test
    void shouldThrowIllegalArgument_whenElementNameIsNotCoded() {
        assertThatThrownBy(() -> BillingCorrectionCodedTokenValidator.validate("rd", "anything"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectStoredContent_whenTamperedTokenIsEmbedded() {
        String tampered = "<rdohip>OK1234</rdohip><hctype>X&Y</hctype><demosex>1</demosex>";

        assertThatThrownBy(() -> BillingCorrectionCodedTokenValidator.validateStoredContent(tampered))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("Health card type");
    }

    @Test
    void shouldAcceptStoredContent_whenElementsAreAllowedAndValuesAreEscaped() {
        assertThatCode(() -> BillingCorrectionCodedTokenValidator
                .validateStoredContent("<rdohip>123456</rdohip><rd>Dr &lt;Ref&gt;</rd><xml_custom>safe &amp; sound</xml_custom>"))
                .doesNotThrowAnyException();
        assertThatCode(() -> BillingCorrectionCodedTokenValidator.validateStoredContent(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> BillingCorrectionCodedTokenValidator.validateStoredContent(""))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectStoredContent_whenRawMarkupAppearsInsideValue() {
        assertThatThrownBy(() -> BillingCorrectionCodedTokenValidator.validateStoredContent("<rd>Dr <Ref></rd>"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("unsupported structure");
    }

    @Test
    void shouldRejectStoredContent_whenElementHasAttributes() {
        assertThatThrownBy(() -> BillingCorrectionCodedTokenValidator.validateStoredContent("<rd class=\"x\">Ref</rd>"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("unsupported structure");
    }

    @Test
    void shouldRejectStoredContent_whenElementNamesDoNotMatch() {
        assertThatThrownBy(() -> BillingCorrectionCodedTokenValidator.validateStoredContent("<rd>Ref</hctype>"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("unsupported structure");
    }

    @Test
    void shouldRejectStoredContent_whenElementNameIsNotAllowed() {
        assertThatThrownBy(() -> BillingCorrectionCodedTokenValidator.validateStoredContent("<evil>Ref</evil>"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("unsupported element");
    }

    @ParameterizedTest
    @CsvSource(value = {
            "<rd>Ref</rd> <xml_custom>safe</xml_custom>|unsupported structure",
            "<rd>Ref</rd>garbage|unsupported structure",
            "<rd>Ref|unsupported structure",
            "<rd><xml_custom>safe</xml_custom></rd>|unsupported structure",
            "<RDOHIP>123456</RDOHIP>|unsupported element",
            "<xml>safe</xml>|unsupported element"
    }, delimiter = '|')
    void shouldRejectStoredContent_whenStructureOrElementBoundaryIsUnsupported(
            String content, String messageFragment) {
        assertThatThrownBy(() -> BillingCorrectionCodedTokenValidator.validateStoredContent(content))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining(messageFragment);
    }
}
