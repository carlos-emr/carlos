package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("Document upload filename validation")
@Tag("unit")
@Tag("security")
class DocumentUpload2ActionFilenameValidationTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private SecurityInfoManager securityInfoManager;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private File tempUploadFile;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        when(securityInfoManager.hasPrivilege(nullable(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempUploadFile != null) {
            Files.deleteIfExists(tempUploadFile.toPath());
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("incoming docs upload should reject hidden filename with JSON error")
    void incomingDocsUploadShouldRejectHiddenFilenameWithJsonError() throws Exception {
        DocumentUpload2Action action = incomingDocsAction(".hidden.pdf");

        String result = action.executeUpload();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("Invalid filename");
    }

    @Test
    @DisplayName("incoming docs upload should reject missing filename with JSON error")
    void incomingDocsUploadShouldRejectMissingFilenameWithJsonError() throws Exception {
        DocumentUpload2Action action = incomingDocsAction(null);

        String result = action.executeUpload();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("Invalid filename");
    }

    @Test
    @DisplayName("incoming docs upload should keep PDF-only error for non-PDF filename")
    void incomingDocsUploadShouldKeepPdfOnlyErrorForNonPdfFilename() throws Exception {
        DocumentUpload2Action action = incomingDocsAction("report.txt");

        String result = action.executeUpload();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("Only .pdf file can be uploaded");
    }

    private DocumentUpload2Action incomingDocsAction(String filename) throws Exception {
        tempUploadFile = File.createTempFile("document-upload", ".pdf");
        Files.writeString(tempUploadFile.toPath(), "pdf");
        request.addParameter("destination", "incomingDocs");

        DocumentUpload2Action action = new DocumentUpload2Action();
        action.setFiledata(tempUploadFile);
        action.setFiledataFileName(filename);
        return action;
    }
}
