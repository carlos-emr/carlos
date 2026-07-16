/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.ByteArrayOutputStream;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("EFormPDFServlet unit tests")
@Tag("unit")
@Tag("fast")
class EFormPDFServletUnitTest {

    @Test
    @DisplayName("should set inline PDF content disposition with the static filename")
    void shouldSetInlineContentDisposition_whenStreamingPdf() throws Exception {
        EFormPDFServlet servlet = new HeaderOnlyEFormPDFServlet();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/eform/EFormPDFServlet");
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.doPost(request, response);

        assertThat(response.getHeader("Content-disposition")).isEqualTo("inline; filename=\"filename_.pdf\"");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).containsExactly(1, 2, 3, 4);
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
