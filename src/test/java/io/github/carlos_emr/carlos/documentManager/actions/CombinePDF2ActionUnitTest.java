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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Response framing of {@link CombinePDF2Action}, the Document Browser's combined preview and the
 * Document Report's Combine PDF download.
 *
 * <p>The inline preview used to set {@code Transfer-Encoding: chunked} by hand. Tomcat frames the
 * body itself, so the response carried the header twice and nginx refused it with a 502
 * ("upstream sent duplicate header line"), leaving the preview empty behind the packaged front
 * door (issue #4131). The action now sends an exact {@code Content-Length} instead.
 *
 * @since 2026-10-01
 */
@Tag("unit")
@Tag("documentManager")
@DisplayName("CombinePDF2Action response framing")
class CombinePDF2ActionUnitTest extends CarlosUnitTestBase {

    private static final byte[] PDF = "%PDF-1.4 combined fixture".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path documentDir;

    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfo;
    private MockedStatic<CarlosProperties> carlosProperties;
    private MockedStatic<ConcatPDF> concatPdf;
    private MockedConstruction<EDocUtil> eDocUtil;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        request = new MockHttpServletRequest("GET", "/documentManager/combinePDFs");
        response = new MockHttpServletResponse();
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

        LoggedInInfo user = mock(LoggedInInfo.class);
        loggedInInfo = mockStatic(LoggedInInfo.class);
        loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(jakarta.servlet.http.HttpServletRequest.class)))
                .thenReturn(user);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull())).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);

        Files.write(documentDir.resolve("a.pdf"), PDF);
        Files.write(documentDir.resolve("b.pdf"), PDF);
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        carlosProperties = mockStatic(CarlosProperties.class);
        carlosProperties.when(CarlosProperties::getInstance).thenReturn(properties);

        eDocUtil = mockConstruction(EDocUtil.class, (mock, context) -> {
            when(mock.getDocumentName("1")).thenReturn("a.pdf");
            when(mock.getDocumentName("2")).thenReturn("b.pdf");
        });
        concatPdf = mockStatic(ConcatPDF.class);
        concatPdf.when(() -> ConcatPDF.concat(anyList(), any(OutputStream.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, OutputStream.class).write(PDF);
            return 0;
        });
    }

    @AfterEach
    void tearDown() {
        concatPdf.close();
        eDocUtil.close();
        carlosProperties.close();
        loggedInInfo.close();
        servletActionContext.close();
    }

    @Test
    @DisplayName("should frame the inline preview with Content-Length and no manual Transfer-Encoding")
    void shouldFrameInlinePreview_withContentLengthAndNoTransferEncoding() throws Exception {
        request.addParameter("ContentDisposition", "inline");
        request.addParameter("docNo", "1", "2");

        String result = new CombinePDF2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getHeader("Transfer-Encoding")).isNull();
        assertThat(response.getContentLengthLong()).isEqualTo(PDF.length);
        assertThat(response.getContentAsByteArray()).isEqualTo(PDF);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Content-Disposition")).startsWith("inline;");
    }

    @Test
    @DisplayName("should frame the download the same way")
    void shouldFrameDownload_withContentLength() throws Exception {
        request.setMethod("POST");
        request.addParameter("docNo", "1", "2");

        String result = new CombinePDF2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getHeader("Transfer-Encoding")).isNull();
        assertThat(response.getContentLengthLong()).isEqualTo(PDF.length);
        assertThat(response.getHeader("Content-Disposition")).startsWith("attachment;");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).isEqualTo(PDF);
    }
}
