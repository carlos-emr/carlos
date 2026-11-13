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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.data;

import io.github.carlos_emr.carlos.utility.DigitalSignatureUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EForm#setSignatureCode(String, String, String, jakarta.servlet.http.HttpSession)},
 * which replaces the {@code ${oscar_signature_code}} marker with the signature pad's script variables.
 *
 * <p>The pad's request id must be one the session that opened the form was given: the capture
 * upload, the preview and the save accept only ids issued to that session
 * ({@link DigitalSignatureUtils#isIssued}).</p>
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("fast")
@DisplayName("EForm.setSignatureCode")
class EFormSignatureCodeUnitTest {

    /** The literal marker EForm replaces (EFormBase.signatureMarker). */
    private static final String SIGNATURE_MARKER = "${oscar_signature_code}";

    private static final Pattern REQUEST_ID_VARIABLE =
            Pattern.compile("var _signatureRequestId = '([0-9a-f]{32})';");

    private static EForm formWithSignatureMarker() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body><div id=\"signature\"></div>" + SIGNATURE_MARKER + "</body></html>");
        return eform;
    }

    private static String requestIdIn(EForm eform) {
        Matcher variable = REQUEST_ID_VARIABLE.matcher(eform.getFormHtml());
        assertThat(variable.find()).as("signature request id variable in the form").isTrue();
        return variable.group(1);
    }

    @Test
    @DisplayName("should write a request id issued to the session that opened the form")
    void shouldWriteRequestIdIssuedToOpeningSession_whenFormHasSignatureMarker() {
        MockHttpSession session = new MockHttpSession();
        EForm eform = formWithSignatureMarker();

        eform.setSignatureCode("/carlos", "Mozilla/5.0", "101", session);

        String requestId = requestIdIn(eform);
        assertThat(DigitalSignatureUtils.isIssued(session, requestId)).isTrue();
        assertThat(DigitalSignatureUtils.isIssued(new MockHttpSession(), requestId)).isFalse();
        // The preview image URL carries the same id as the pad's variable.
        assertThat(StringUtils.countMatches(eform.getFormHtml(), requestId)).isEqualTo(2);
        assertThat(eform.getFormHtml())
                .doesNotContain(SIGNATURE_MARKER)
                .contains("var _browserType = 'ALL';")
                .contains("var _demographicNo = '101';");
    }

    @Test
    @DisplayName("should issue a separate request id to each form one session opens")
    void shouldIssueSeparateRequestIds_whenOneSessionOpensTwoForms() {
        MockHttpSession session = new MockHttpSession();
        EForm first = formWithSignatureMarker();
        EForm second = formWithSignatureMarker();

        first.setSignatureCode("/carlos", "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)", "101", session);
        second.setSignatureCode("/carlos", "Mozilla/5.0", "101", session);

        String firstId = requestIdIn(first);
        String secondId = requestIdIn(second);
        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(DigitalSignatureUtils.isIssued(session, firstId)).isTrue();
        assertThat(DigitalSignatureUtils.isIssued(session, secondId)).isTrue();
        assertThat(first.getFormHtml()).contains("var _browserType = 'IPAD';");
    }

    @Test
    @DisplayName("should refuse to write the signature code without a session and leave the form unchanged")
    void shouldRefuseToWriteSignatureCode_withoutSession() {
        EForm eform = formWithSignatureMarker();
        String original = eform.getFormHtml();

        assertThatThrownBy(() -> eform.setSignatureCode("/carlos", "Mozilla/5.0", "101", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(eform.getFormHtml()).isEqualTo(original);
    }
}
