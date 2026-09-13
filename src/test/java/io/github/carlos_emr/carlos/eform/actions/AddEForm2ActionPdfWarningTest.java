package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("AddEForm2Action PDF warning")
@Tag("unit")
@Tag("fast")
class AddEForm2ActionPdfWarningTest extends CarlosUnitTestBase {

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private EformDataManager eformDataManager;
    @Mock private DocumentAttachmentManager documentAttachmentManager;
    @Mock private EmailManager emailManager;
    @Mock private DemographicManager demographicManager;
    @Mock private LoggedInInfo loggedInInfo;

    private AutoCloseable mocks;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EformDataManager.class, eformDataManager);
        registerMock(DocumentAttachmentManager.class, documentAttachmentManager);
        registerMock(EmailManager.class, emailManager);
        registerMock(DemographicManager.class, demographicManager);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(loggedInInfo);
        when(demographicManager.getDemographicFormattedName(loggedInInfo, 123)).thenReturn("Doe, Jane");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should close with warning when preview pdf generation fails")
    void shouldCloseWithWarning_whenPreviewPdfGenerationFails() throws Exception {
        when(documentAttachmentManager.renderEFormWithAttachments(request, response))
                .thenThrow(new PDFGenerationException("render failed"));
        AddEForm2Action action = new AddEForm2Action();

        String result = action.closeWithPdfPreview(loggedInInfo, "123", "42");

        assertThat(result).isEqualTo("close");
        assertThat(request.getAttribute("warningMessage")).isEqualTo("This eForm was saved, but its PDF preview could not be generated.");
        assertThat(request.getAttribute("warningMessage").toString()).doesNotContain("render failed");
        assertThat(request.getAttribute("isSuccess_Autoclose")).isEqualTo("true");
        assertThat(request.getAttribute("fdid")).isEqualTo("42");
        assertThat(request.getAttribute("parentAjaxId")).isEqualTo("eforms");
        assertThat(request.getAttribute("errorMessage")).isNull();
        assertThat(request.getAttribute("eFormPDF")).isEqualTo("");
        assertThat(request.getAttribute("eFormPDFName").toString()).matches("\\d{4}_\\d{2}_\\d{2}_Doe\\.pdf");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"setPdfError", "setPdfWarning", "buildPdfPreviewName"})
    void shouldBoundEveryPdfDiagnosticWithoutChangingItsFailureContract(String operation) {
        var failure = new IllegalStateException("PRIVATE_RENDERER_PATH",
                new IllegalArgumentException("PRIVATE_RENDERER_CAUSE"));
        AddEForm2Action action = new AddEForm2Action();
        try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(AddEForm2Action.class)) {
            if ("buildPdfPreviewName".equals(operation)) {
                when(demographicManager.getDemographicFormattedName(loggedInInfo, 123)).thenThrow(failure);
                String filename = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        action, operation, loggedInInfo, "123");
                assertThat(filename).matches("\\d{4}_\\d{2}_\\d{2}_eform\\.pdf");
            } else {
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(action, operation,
                        "Safe operation-specific user guidance", failure);
                assertThat(request.getAttribute("setPdfError".equals(operation) ? "errorMessage" : "warningMessage"))
                        .isEqualTo("Safe operation-specific user guidance");
            }
            assertThat(logs.messages()).anyMatch(message -> message.contains("IllegalStateException"));
            assertThat(logs.messages().toString()).doesNotContain("PRIVATE_RENDERER_PATH", "PRIVATE_RENDERER_CAUSE");
            assertThat(logs.events()).allMatch(event -> event.getThrown() == null);
        }
    }

    @Test
    @DisplayName("should fall back to a generic preview filename when demographic number is invalid")
    void shouldUseFallbackPreviewFilename_whenDemographicNumberIsInvalid() throws Exception {
        when(documentAttachmentManager.renderEFormWithAttachments(request, response))
                .thenThrow(new PDFGenerationException("render failed"));
        AddEForm2Action action = new AddEForm2Action();

        String result = action.closeWithPdfPreview(loggedInInfo, "abc", "42");

        assertThat(result).isEqualTo("close");
        assertThat(request.getAttribute("eFormPDFName").toString()).matches("\\d{4}_\\d{2}_\\d{2}_eform\\.pdf");
        assertThat(request.getAttribute("warningMessage")).isEqualTo("This eForm was saved, but its PDF preview could not be generated.");
        assertThat(request.getAttribute("warningMessage").toString()).doesNotContain("render failed");
    }
    @Test
    @DisplayName("should close with warning when preview pdf path is missing")
    void shouldCloseWithWarning_whenPreviewPdfPathIsMissing() throws Exception {
        when(documentAttachmentManager.renderEFormWithAttachments(request, response)).thenReturn(null);
        AddEForm2Action action = new AddEForm2Action();

        String result = action.closeWithPdfPreview(loggedInInfo, "123", "42");

        assertThat(result).isEqualTo("close");
        assertThat(request.getAttribute("warningMessage")).isEqualTo("This eForm was saved, but its PDF preview could not be generated.");
        assertThat(request.getAttribute("eFormPDF")).isEqualTo("");
    }
}
