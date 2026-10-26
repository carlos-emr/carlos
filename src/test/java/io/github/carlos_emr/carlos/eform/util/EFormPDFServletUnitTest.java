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
package io.github.carlos_emr.carlos.eform.util;

import java.io.ByteArrayOutputStream;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EFormPDFServlet unit tests")
@Tag("unit")
@Tag("fast")
class EFormPDFServletUnitTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;

    @BeforeEach
    void registerSecurity() {
        securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
    }

    private void allowRead(String demographicNo) {
        when(securityInfoManager.hasPrivilege(
                nullable(LoggedInInfo.class), eq("_eform"), eq("r"), eq(demographicNo))).thenReturn(true);
    }

    @Test
    @DisplayName("should set inline PDF content disposition with the static filename")
    void shouldSetInlineContentDisposition_whenStreamingPdf() throws Exception {
        EFormPDFServlet servlet = new HeaderOnlyEFormPDFServlet();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/eform/EFormPDFServlet");
        request.setParameter("demographic_no", "123");
        allowRead("123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.doPost(request, response);

        assertThat(response.getHeader("Content-disposition")).isEqualTo("inline; filename=\"filename_.pdf\"");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("should refuse to generate a PDF for a demographic the caller may not read")
    void shouldRefuseGeneration_whenDemographicNotReadable() {
        // This servlet is mapped at /eform/createpdf independently of any action, so it is directly
        // reachable. It previously performed no authorization at all: authentication alone let any
        // user generate a PDF for any patient.
        EFormPDFServlet servlet = new HeaderOnlyEFormPDFServlet();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/eform/createpdf");
        request.setParameter("demographic_no", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> servlet.doPost(request, response))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eform");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    private static final class HeaderOnlyEFormPDFServlet extends EFormPDFServlet {
        @Override
        protected ByteArrayOutputStream generatePDFDocumentBytes(HttpServletRequest req, ServletContext ctx, int multiple) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.writeBytes(new byte[] {1, 2, 3, 4});
            return output;
        }

        @Override
        public ServletContext getServletContext() {
            return mock(ServletContext.class);
        }
    }
}
