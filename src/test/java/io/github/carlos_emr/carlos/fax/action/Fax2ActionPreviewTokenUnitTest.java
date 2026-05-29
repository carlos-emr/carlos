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
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class Fax2ActionPreviewTokenUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FaxManager faxManager;
    private DocumentAttachmentManager documentAttachmentManager;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @TempDir
    Path tempDir;

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
        Fax2Action action = prepareAndVerifyEformFax(pdf);
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
    void shouldDenyPreview_whenFaxFileTokenIsInvalid() throws Exception {
        Fax2Action action = new Fax2Action();
        request.setParameter("faxFileToken", "unknown-token");
        request.setParameter("faxFilePath", "/tmp/tampered.pdf");

        action.getPreview();

        assertThat(response.getStatus()).isEqualTo(403);
        verify(faxManager, never()).resolveAndValidateFilePath("/tmp/tampered.pdf");
    }

    @Test
    @DisplayName("should deny preview when user lacks fax read privilege")
    void shouldDenyPreview_whenUserLacksFaxReadPrivilege() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "r", null)).thenReturn(false);
        request.setParameter("faxFileToken", "00000000-0000-0000-0000-000000000000");

        new Fax2Action().getPreview();

        assertThat(response.getStatus()).isEqualTo(403);
        verify(faxManager, never()).resolveAndValidateFilePath(any(String.class));
    }

    @Test
    @DisplayName("should deny prepare fax when user lacks fax read privilege")
    void shouldDenyPrepareFax_whenUserLacksFaxReadPrivilege() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "r", null)).thenReturn(false);
        Fax2Action action = new Fax2Action();
        action.setTransactionType(FaxManager.TransactionType.EFORM.name());
        action.setTransactionId(456);
        action.setDemographicNo(123);

        assertThatThrownBy(action::prepareFax)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_fax)");

        verify(faxManager, never()).getFaxGatewayAccounts(any(LoggedInInfo.class));
        verify(documentAttachmentManager, never()).renderEFormWithAttachments(request, response);
        assertThat(request.getAttribute("faxFileToken")).isNull();
    }

    @Test
    @DisplayName("should render image preview when token is valid")
    void shouldRenderImagePreview_whenFaxFileTokenIsValid() throws Exception {
        Path pdf = createPreviewPdf();
        Path image = tempDir.resolve("preview page.png");
        Files.write(image, "PNG-test".getBytes(StandardCharsets.UTF_8));
        Fax2Action action = prepareAndVerifyEformFax(pdf);
        String token = (String) request.getAttribute("faxFileToken");
        request.setParameter("faxFileToken", token);
        request.setParameter("showAs", "image");
        request.setParameter("pageNumber", "2");
        when(faxManager.getFaxPreviewImage(loggedInInfo, pdf.toString(), 2)).thenReturn(image);

        action.getPreview();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"preview%20page.png\"");
        assertThat(response.getContentAsByteArray()).containsExactly(Files.readAllBytes(image));
    }

    @Test
    @DisplayName("should render job preview without fax file token")
    void shouldRenderPreview_whenJobIdIsPresent() throws Exception {
        Path pdf = createPreviewPdf();
        FaxJob faxJob = new FaxJob();
        faxJob.setFile_name(pdf.toString());
        request.setParameter("jobId", "42");
        when(faxManager.getFaxJob(loggedInInfo, 42)).thenReturn(faxJob);
        when(faxManager.resolveAndValidateFilePath(pdf.toString())).thenReturn(pdf);

        new Fax2Action().getPreview();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).containsExactly(Files.readAllBytes(pdf));
    }

    @Test
    @DisplayName("should use token path, not tampered path, when queueing fax")
    void shouldUseTokenPathNotTamperedPath_whenQueueingFax() throws Exception {
        Path pdf = createPreviewPdf();
        Fax2Action action = prepareAndVerifyEformFax(pdf);
        String token = (String) request.getAttribute("faxFileToken");
        String tamperedPath = "/tmp/tampered.pdf";
        request.setParameter("faxFileToken", token);
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
    @DisplayName("should dispatch queue when request method is POST")
    void shouldDispatchQueue_whenRequestMethodIsPost() throws Exception {
        Path pdf = createPreviewPdf();
        Fax2Action action = prepareAndVerifyEformFax(pdf);
        String token = (String) request.getAttribute("faxFileToken");
        request.setMethod("POST");
        request.setParameter("method", "queue");
        request.setParameter("faxFileToken", token);
        action.setRecipient("Specialist");
        action.setRecipientFaxNumber("4165551234");
        action.setSenderFaxNumber("4165555678");
        action.setCoverpage("false");
        when(faxManager.createAndSaveFaxJob(eq(loggedInInfo), anyMap())).thenReturn(List.of(new FaxJob()));

        String result = action.execute();

        assertThat(result).isEqualTo("preview");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(faxManager).validateFilePath(pdf.toString());
        verify(faxManager).createAndSaveFaxJob(eq(loggedInInfo), anyMap());
    }

    @Test
    @DisplayName("should deny preview when fax file token was consumed by queue")
    void shouldDenyPreview_whenFaxFileTokenWasConsumedByQueue() throws Exception {
        Path pdf = createPreviewPdf();
        Fax2Action action = prepareAndVerifyEformFax(pdf);
        String token = (String) request.getAttribute("faxFileToken");
        request.setParameter("faxFileToken", token);
        action.setRecipient("Specialist");
        action.setRecipientFaxNumber("4165551234");
        action.setSenderFaxNumber("4165555678");
        action.setCoverpage("false");
        when(faxManager.createAndSaveFaxJob(eq(loggedInInfo), anyMap())).thenReturn(List.of(new FaxJob()));

        assertThat(action.queue()).isEqualTo("preview");
        action.getPreview();

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @ParameterizedTest
    @MethodSource("unsafeMutationRequests")
    @DisplayName("should reject unsafe HTTP methods before fax mutation dispatch")
    void shouldRejectMutation_whenRequestMethodIsUnsafe(String httpMethod, String dispatchMethod) {
        request.setMethod(httpMethod);
        if (dispatchMethod != null) {
            request.setParameter("method", dispatchMethod);
        }

        String result = new Fax2Action().execute();

        assertThat(result).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(faxManager, never()).flush(any(LoggedInInfo.class), nullable(String.class));
        verify(faxManager, never()).createAndSaveFaxJob(any(LoggedInInfo.class), anyMap());
        verify(faxManager, never()).validateFilePath(any(String.class));
    }

    @Test
    @DisplayName("should remove fax file token when cancelling fax")
    void shouldRemoveFaxFileToken_whenCancellingFax() throws Exception {
        Path pdf = createPreviewPdf();
        Fax2Action action = prepareAndVerifyEformFax(pdf);
        String token = (String) request.getAttribute("faxFileToken");
        request.setParameter("faxFileToken", token);

        assertThat(action.cancel()).isEqualTo("none");

        verify(faxManager).validateFilePath(pdf.toString());
        verify(faxManager).flush(loggedInInfo, pdf.toString());

        resetResponse();
        new Fax2Action().getPreview();

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("should deny cancel when user lacks fax write privilege")
    void shouldDenyCancel_whenUserLacksFaxWritePrivilege() throws Exception {
        Path pdf = createPreviewPdf();
        Fax2Action action = prepareAndVerifyEformFax(pdf);
        String token = (String) request.getAttribute("faxFileToken");
        request.setParameter("faxFileToken", token);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "w", null)).thenReturn(false);

        assertThatThrownBy(action::cancel)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_fax)");

        verify(faxManager, never()).validateFilePath(any(String.class));
        verify(faxManager, never()).flush(any(LoggedInInfo.class), any(String.class));
    }

    @Test
    @DisplayName("should flush without preview path when cancel token is malformed")
    void shouldFlushWithoutPreviewPath_whenCancelTokenIsMalformed() {
        Fax2Action action = newEformCancelAction();
        request.setParameter("faxFileToken", "not-a-uuid");

        assertThat(action.cancel()).isEqualTo("none");

        verify(faxManager, never()).validateFilePath(any(String.class));
        verify(faxManager).flush(eq(loggedInInfo), isNull());
    }

    @Test
    @DisplayName("should flush without preview path when cancel token is unknown")
    void shouldFlushWithoutPreviewPath_whenCancelTokenIsUnknown() {
        Fax2Action action = newEformCancelAction();
        request.setParameter("faxFileToken", "00000000-0000-0000-0000-000000000000");

        assertThat(action.cancel()).isEqualTo("none");

        verify(faxManager, never()).validateFilePath(any(String.class));
        verify(faxManager).flush(eq(loggedInInfo), isNull());
    }

    @Test
    @DisplayName("should deny oldest preview when token store exceeds limit")
    void shouldDenyOldestPreview_whenTokenStoreExceedsLimit() throws Exception {
        when(documentAttachmentManager.renderEFormWithAttachments(request, response)).thenReturn(createPreviewPdf());
        String firstToken = null;
        String newestToken = null;
        for (int i = 0; i < Fax2Action.MAX_PREVIEW_TOKENS_PER_SESSION + 1; i++) {
            Fax2Action action = new Fax2Action();
            action.setTransactionType(FaxManager.TransactionType.EFORM.name());
            action.setTransactionId(456);
            action.setDemographicNo(123);

            assertThat(action.prepareFax()).isEqualTo("preview");
            newestToken = (String) request.getAttribute("faxFileToken");
            if (firstToken == null) {
                firstToken = newestToken;
            }
        }

        request.setParameter("faxFileToken", firstToken);
        new Fax2Action().getPreview();

        assertThat(response.getStatus()).isEqualTo(403);

        resetResponse();
        request.setParameter("faxFileToken", newestToken);
        Path pdf = createPreviewPdf();
        when(faxManager.resolveAndValidateFilePath(pdf.toString())).thenReturn(pdf);
        new Fax2Action().getPreview();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should keep raw fax paths out of cover page browser requests")
    void shouldKeepRawFaxPathsOutOfCoverPageBrowserRequests() throws Exception {
        String coverPage = Files.readString(projectFile("src/main/webapp/WEB-INF/jsp/fax/CoverPage.jsp"),
                StandardCharsets.UTF_8);

        assertThat(coverPage)
                .contains("name=\"faxFileToken\"")
                .contains("method=getPreview&faxFileToken=")
                .doesNotContain("name=\"faxFilePath\"")
                .doesNotContain("method=getPreview&faxFilePath=")
                .doesNotContain("value=\"<carlos:encode value='${ documents }'");
    }

    private Fax2Action prepareAndVerifyEformFax(Path pdf) throws Exception {
        when(documentAttachmentManager.renderEFormWithAttachments(request, response)).thenReturn(pdf);
        Fax2Action action = new Fax2Action();
        action.setTransactionType(FaxManager.TransactionType.EFORM.name());
        action.setTransactionId(456);
        action.setDemographicNo(123);

        assertThat(action.prepareFax()).isEqualTo("preview");
        assertThat(request.getAttribute("faxFileToken")).isNotNull();
        assertThat(request.getAttribute("faxFilePath")).isNull();
        assertThat(request.getAttribute("documents")).isNull();
        return action;
    }

    private Fax2Action newEformCancelAction() {
        Fax2Action action = new Fax2Action();
        action.setTransactionType(FaxManager.TransactionType.EFORM.name());
        action.setTransactionId(456);
        action.setDemographicNo(123);
        return action;
    }

    private static Stream<Arguments> unsafeMutationRequests() {
        return Stream.of(
                Arguments.of("GET", "queue"),
                Arguments.of("HEAD", "queue"),
                Arguments.of("GET", "cancel"),
                Arguments.of("HEAD", "cancel"),
                Arguments.of("GET", null),
                Arguments.of("HEAD", null)
        );
    }

    private Path projectFile(String relativePath) throws Exception {
        Path location = Path.of(Fax2ActionPreviewTokenUnitTest.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        Path directory = Files.isRegularFile(location) ? location.getParent() : location;
        for (Path current = directory; current != null; current = current.getParent()) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate " + relativePath);
    }

    private Path createPreviewPdf() throws Exception {
        Path pdf = tempDir.resolve("preview.pdf");
        Files.write(pdf, "%PDF-test".getBytes(StandardCharsets.UTF_8));
        return pdf;
    }

    private void resetResponse() {
        response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }
}
