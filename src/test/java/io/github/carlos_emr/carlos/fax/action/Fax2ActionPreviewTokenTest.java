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
package io.github.carlos_emr.carlos.fax.action;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that fax preview documents remain server-side and are referenced only by session tokens.
 *
 * @since 2026-05-26
 */
@DisplayName("Fax2Action preview tokens")
@Tag("unit")
@Tag("security")
class Fax2ActionPreviewTokenTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FaxManager faxManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        faxManager = mock(FaxManager.class);
        documentAttachmentManager = mock(DocumentAttachmentManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);

        registerMock(FaxManager.class, faxManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);

        when(securityInfoManager.hasPrivilege(nullable(LoggedInInfo.class), eq("_fax"), any(String.class), isNull()))
                .thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, 123)).thenReturn(true);
        when(faxManager.getFaxGatewayAccounts(loggedInInfo)).thenReturn(List.of(new FaxConfig()));
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should render preview when fax file token is valid")
    void shouldRenderPreview_whenFaxFileTokenIsValid() throws Exception {
        Path pdf = createPreviewPdf();
        Fax2Action action = prepareEformFax(pdf);
        String token = (String) request.getAttribute("faxFileToken");
        request.addParameter("faxFileToken", token);
        when(faxManager.resolveAndValidateFilePath(pdf.toString())).thenReturn(pdf);

        action.getPreview();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).containsExactly(Files.readAllBytes(pdf));
        verify(faxManager).resolveAndValidateFilePath(pdf.toString());
    }

    @Test
    @DisplayName("should deny preview when fax file token is invalid")
    void shouldDenyPreview_whenFaxFileTokenIsInvalid() {
        Fax2Action action = new Fax2Action();
        request.addParameter("faxFileToken", "unknown-token");
        request.addParameter("faxFilePath", "/tmp/tampered.pdf");

        action.getPreview();

        assertThat(response.getStatus()).isEqualTo(403);
        verify(faxManager, never()).resolveAndValidateFilePath("/tmp/tampered.pdf");
    }

    @Test
    @DisplayName("should ignore raw fax file path tampering when queueing fax")
    void shouldIgnoreRawFaxFilePathTampering_whenQueueingFax() throws Exception {
        Path pdf = createPreviewPdf();
        Fax2Action action = prepareEformFax(pdf);
        String token = (String) request.getAttribute("faxFileToken");
        String tamperedPath = "/tmp/tampered.pdf";
        request.addParameter("faxFileToken", token);
        action.setFaxFilePath(tamperedPath);
        action.setRecipient("Specialist");
        action.setRecipientFaxNumber("4165551234");
        action.setSenderFaxNumber("4165555678");
        action.setCoverpage("false");
        when(faxManager.createAndSaveFaxJob(eq(loggedInInfo), anyMap())).thenReturn(List.of(new FaxJob()));

        String result = action.queue();

        assertThat(result).isEqualTo("preview");
        verify(faxManager).validateFilePath(pdf.toString());
        verify(faxManager, never()).validateFilePath(tamperedPath);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(faxManager).createAndSaveFaxJob(eq(loggedInInfo), captor.capture());
        assertThat(captor.getValue()).containsEntry("faxFilePath", pdf.toString());
    }

    @Test
    @DisplayName("should keep raw fax paths out of cover page browser requests")
    void shouldKeepRawFaxPathsOutOfCoverPageBrowserRequests() throws Exception {
        String coverPage = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/fax/CoverPage.jsp"));

        assertThat(coverPage).contains("name=\"faxFileToken\"");
        assertThat(coverPage).contains("method=getPreview&faxFileToken=");
        assertThat(coverPage).doesNotContain("name=\"faxFilePath\"");
        assertThat(coverPage).doesNotContain("method=getPreview&faxFilePath=");
    }

    private Fax2Action prepareEformFax(Path pdf) throws Exception {
        when(documentAttachmentManager.renderEFormWithAttachments(request, response)).thenReturn(pdf);
        Fax2Action action = new Fax2Action();
        action.setTransactionType(FaxManager.TransactionType.EFORM.name());
        action.setTransactionId(456);
        action.setDemographicNo(123);

        assertThat(action.prepareFax()).isEqualTo("preview");
        assertThat(request.getAttribute("faxFileToken")).isNotNull();
        assertThat(request.getAttribute("faxFilePath")).isNull();
        return action;
    }

    private Path createPreviewPdf() throws Exception {
        Path pdf = tempDir.resolve("preview.pdf");
        Files.write(pdf, "%PDF-test".getBytes(StandardCharsets.UTF_8));
        return pdf;
    }
}
