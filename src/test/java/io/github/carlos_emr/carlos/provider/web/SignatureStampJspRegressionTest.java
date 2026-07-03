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
    private static final Path VISUAL_EDITOR_JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "eform", "visualEformEditor.jsp");

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
                .contains("String signatureProviderNo = providerNo;")
                .contains("signatureProviderNo = consultUtil.providerNo.trim();")
                .contains("signatureProviderNo = referringProviderDefault.trim();")
                .contains("consultSigFile.isFile()")
                .contains("/provider/providerSignatureImage?providerNo=<%=SafeEncode.forUriComponent(signatureProviderNo)%>")
                .doesNotContain("UserProperty consultSigProp = userPropertyDAO.getProp(providerNo, UserProperty.PROVIDER_CONSULT_SIGNATURE);");
    }

    @Test
    @DisplayName("Visual editor should preview signature stamps through the secured provider route")
    void shouldUseProviderSignaturePreview_inVisualEditor() throws Exception {
        String jsp = Files.readString(VISUAL_EDITOR_JSP, StandardCharsets.UTF_8);
        String normalizedJsp = normalizeWhitespace(jsp);

        assertThat(normalizedJsp)
                .contains("var OSCAR_PROVIDER_SIGNATURE_IMG_SRC = \"../provider/providerSignatureImage\";")
                .contains("var src = getSignatureStampPreviewSrc();")
                .contains("$img.on(\"error\", function() {")
                .contains("function getSignatureStampPreviewSrc(){")
                .contains("this.src = getBlankSignatureStampSrc();")
                .contains("if (this.src.indexOf(\"BNK.png\") === -1)")
                .doesNotContain("error: function() {");
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
