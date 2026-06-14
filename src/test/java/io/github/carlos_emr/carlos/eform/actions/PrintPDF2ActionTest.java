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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.eform.util.EFormPrintPDFUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Properties;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * Integration tests for {@link PrintPDF2Action}.
 *
 * <p>Focuses on submit value validation before the action redirects to the
 * eForm PDF generation servlet.</p>
 *
 * @since 2026-06-08
 */
@DisplayName("PrintPDF2Action Integration Tests")
@Tag("integration")
@Tag("web")
@Tag("eform")
class PrintPDF2ActionTest extends CarlosWebTestBase {

    private static final String UNSUPPORTED_SUBMIT_MESSAGE = "Unsupported submit action";

    private PrintPDF2Action action;

    @BeforeEach
    void setUp() {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        allowPrivilege("_eform", "r");
        mockRequest.setMethod("POST");
        mockRequest.setRequestURI("/eform/efmPrintPDF");
        action = new PrintPDF2Action();
    }

    @Test
    @DisplayName("Should return 400 when submit is missing")
    void shouldReturnBadRequest_whenSubmitIsMissing() throws Exception {
        mockRequest.setParameter("demographic_no", "123");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(mockResponse.getErrorMessage()).isEqualTo(UNSUPPORTED_SUBMIT_MESSAGE);
        assertThat(mockResponse.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("Should return 400 when submit is unsupported")
    void shouldReturnBadRequest_whenSubmitIsUnsupported() throws Exception {
        mockRequest.setParameter("submit", "delete");
        mockRequest.setParameter("demographic_no", "123");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(mockResponse.getErrorMessage()).isEqualTo(UNSUPPORTED_SUBMIT_MESSAGE);
        assertThat(mockResponse.getRedirectedUrl()).isNull();
    }

    @Test
    @DisplayName("Should redirect to createpdf when submit is printAll")
    void shouldRedirectToCreatePdf_whenSubmitIsPrintAll() throws Exception {
        mockRequest.setContextPath("/carlos");
        mockRequest.setParameter("submit", "PrInTaLl");
        mockRequest.setParameter("demographic_no", "123&formId=999");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
        assertThat(mockResponse.getRedirectedUrl())
                .isEqualTo("/carlos/eform/createpdf?demographic_no=123%26formId%3D999&formId=0");
        assertThat(mockResponse.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("Should redirect to createpdf when submit is graph")
    void shouldRedirectToCreatePdf_whenSubmitIsGraph() throws Exception {
        Properties graphProperties = new Properties();
        graphProperties.setProperty("graphScore", "42");
        mockRequest.setContextPath("/carlos");
        mockRequest.setParameter("submit", "gRaPh");
        mockRequest.setParameter("demographic_no", "123");

        try (MockedStatic<EFormPrintPDFUtil> eformPrintPdfUtilMock = mockStatic(EFormPrintPDFUtil.class)) {
            eformPrintPdfUtilMock.when(() -> EFormPrintPDFUtil.getFrmRourkeGraph(
                    any(LoggedInInfo.class), any(Properties.class)))
                    .thenReturn(graphProperties);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
            assertThat(mockResponse.getRedirectedUrl()).isEqualTo("/carlos/eform/createpdf");
            assertThat(mockRequest.getAttribute("graphScore")).isEqualTo("42");
            assertThat(mockResponse.getErrorMessage()).isNull();
        }
    }
}
