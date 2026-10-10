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
package io.github.carlos_emr.carlos.form.pdfservlet;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@Tag("prescription")
class FrmCustomedPDFServletUnitTest extends PrescriptionPdfUnitTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    @DisplayName("should refuse a fax request that still reaches the PDF servlet, and send nothing")
    void shouldRefuseFaxRequest_whenItReachesThePdfServlet(String method) throws Exception {
        MockHttpServletRequest request = createPreviewRequest();
        request.setMethod(method);
        request.addParameter("__method", "oscarRxFax");
        stubStoredSignature();
        stubActiveFaxConfig();
        MockHttpServletResponse response = new MockHttpServletResponse();

        serviceAs(new FrmCustomedPDFServlet(), request, response, mock(LoggedInInfo.class));

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_GONE);
        assertThat(response.getContentAsString()).contains("fax-failure").doesNotContain("fax-success");
        verifyFaxWasNotQueued();
        verifyNoInteractions(faxConfigDao, faxJobDao, digitalSignatureManager);
    }

    @Test
    @DisplayName("should stream the preview PDF with a signature released on _rx read alone")
    void shouldStreamPreviewPdf_whenCallerHasRxRead() throws Exception {
        stubStoredSignature();
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.WRITE), anyString())).thenReturn(false);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        MockHttpServletResponse response = new MockHttpServletResponse();

        serviceAs(new FrmCustomedPDFServlet(), createPreviewRequest(), response, loggedInInfo);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(new String(response.getContentAsByteArray(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        verify(digitalSignatureManager).getDigitalSignature(SIGNATURE_ID);
        verify(securityInfoManager, never()).hasPrivilege(any(), eq("_rx"), eq(SecurityInfoManager.WRITE), anyString());
        verifyFaxWasNotQueued();
    }
}
