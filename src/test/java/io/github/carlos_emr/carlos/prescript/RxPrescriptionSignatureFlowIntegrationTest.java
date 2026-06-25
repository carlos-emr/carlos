/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Prescription signature browser flow integration")
@Tag("integration")
@Tag("prescript")
class RxPrescriptionSignatureFlowIntegrationTest {

    private static final Path STRUTS_PRESCRIPTION_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-prescription.xml");
    private static final Path STRUTS_INTEGRATION_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-integration.xml");
    private static final Path VIEW_SCRIPT_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp");
    private static final Path PREVIEW_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/rx/Preview2.jsp");

    @Test
    void shouldKeepPrescriptionSignatureUploadAndAssociationWired() throws Exception {
        String strutsPrescription = Files.readString(STRUTS_PRESCRIPTION_XML, StandardCharsets.UTF_8);
        String strutsIntegration = Files.readString(STRUTS_INTEGRATION_XML, StandardCharsets.UTF_8);
        String viewScript = Files.readString(VIEW_SCRIPT_JSP, StandardCharsets.UTF_8);

        assertThat(strutsIntegration)
                .contains("<action name=\"signature_pad/SaveSignatureUpload\"")
                .contains("class=\"io.github.carlos_emr.carlos.signature.action.SaveSignatureUpload2Action\"");
        assertThat(strutsPrescription)
                .contains("<action name=\"rx/saveDigitalSignature\"")
                .contains("class=\"io.github.carlos_emr.carlos.prescript.pageUtil.RxRePrescribe2Action\"")
                .contains("method=\"saveDigitalSignature\"")
                .contains("<allowed-methods>saveDigitalSignature</allowed-methods>");

        assertThat(viewScript)
                .contains("id=\"signatureFrame\"")
                .contains("saveToDB=true")
                .contains("ModuleType.PRESCRIPTION")
                .contains("/rx/saveDigitalSignature")
                .contains("method=saveDigitalSignature")
                .contains("setDigitalSignatureToRx(signId")
                .contains("refreshImage();");
    }

    @Test
    void shouldKeepStoredPrescriptionSignatureRenderingWired() throws Exception {
        String preview = Files.readString(PREVIEW_JSP, StandardCharsets.UTF_8);

        assertThat(preview)
                .contains("id=\"signature\"")
                .contains("ImageRenderingServlet.Source.signature_stored")
                .contains("bean.getStashItem(0).getDigitalSignatureId()")
                .contains("digitalSignatureId=\" + bean.getStashItem(0).getDigitalSignatureId()");
    }
}
