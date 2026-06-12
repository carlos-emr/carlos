/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.provider.web;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Signature stamp JSP regressions")
@Tag("unit")
class SignatureStampJspRegressionTest {
    private static final Path RX_PREVIEW_JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "rx", "Preview2.jsp");
    private static final Path CONSULT_JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "encounter", "oscarConsultationRequest", "ConsultationFormRequest.jsp");

    @Test
    @DisplayName("Rx preview should request the signing provider's stamp and verify the file exists")
    void shouldUseProviderSpecificStampRoute_inRxPreview() throws Exception {
        String jsp = Files.readString(RX_PREVIEW_JSP, StandardCharsets.UTF_8);
        String normalizedJsp = normalizeWhitespace(jsp);

        assertThat(normalizedJsp)
                .contains("boolean hasRxStampSignature = false;")
                .contains("if (signingProvider != null && !signingProvider.trim().isEmpty())")
                .contains("PathValidationUtils.validatePath(UserProperty.CONSULT_SIGNATURE_PREFIX + signingProvider + \".png\", imageFolder);")
                .contains("/provider/providerSignatureImage?providerNo=")
                .contains("SafeEncode.forUriComponent(signingProvider)")
                .doesNotContain("UserProperty rxSigProp =")
                .doesNotContain("startimageUrl = request.getContextPath() + \"/provider/providerSignatureImage\";");
    }

    @Test
    @DisplayName("Consultation request should only enable stamp mode when the file exists")
    void shouldRequireUsableStampFile_inConsultationRequest() throws Exception {
        String jsp = Files.readString(CONSULT_JSP, StandardCharsets.UTF_8);
        String normalizedJsp = normalizeWhitespace(jsp);

        assertThat(normalizedJsp)
                .contains("boolean hasStampSignature = false;")
                .contains("consultSigFile.exists()")
                .contains("/provider/providerSignatureImage?providerNo=<%=SafeEncode.forUriComponent(providerNo)%>")
                .doesNotContain("boolean hasStampSignature = (consultSigProp != null && consultSigProp.getValue() != null && !consultSigProp.getValue().trim().isEmpty());");
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
