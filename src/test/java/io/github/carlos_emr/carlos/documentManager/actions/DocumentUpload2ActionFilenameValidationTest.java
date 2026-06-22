package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.IncomingDocUtil;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    private NioFileManager nioFileManager;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private File tempUploadFile;
    private File tempUploadDirectory;
    private File tempDestinationFile;
    private File tempDestinationDirectory;

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

        nioFileManager = mock(NioFileManager.class);
        registerMock(NioFileManager.class, nioFileManager);
        when(nioFileManager.deleteTempFile(nullable(String.class))).thenAnswer(invocation -> {
            String filePath = invocation.getArgument(0);
            return filePath != null && Files.deleteIfExists(Path.of(filePath));
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        Exception cleanupFailure = null;

        cleanupFailure = deleteIfExists(tempDestinationFile, cleanupFailure);
        cleanupFailure = deleteIfExists(tempUploadFile, cleanupFailure);
        cleanupFailure = deleteIfExists(tempDestinationDirectory, cleanupFailure);
        cleanupFailure = deleteIfExists(tempUploadDirectory, cleanupFailure);
        if (servletActionContextMock != null) {
            try {
                servletActionContextMock.close();
            } catch (Exception e) {
                cleanupFailure = appendCleanupFailure(cleanupFailure, e);
            }
        }

        if (cleanupFailure != null) {
            throw cleanupFailure;
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

    @Test
    @DisplayName("incoming docs upload should reject outside temp source before cleanup delete")
    void shouldRejectOutsideTempSource_beforeIncomingDocsCleanupDelete() throws Exception {
        request.addParameter("destination", "incomingDocs");
        DocumentUpload2Action action = outsideTempUploadAction("report.pdf");

        String result = action.executeUpload();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("Invalid file upload");
        assertThat(tempUploadFile).exists();
    }

    @Test
    @DisplayName("document upload should reject outside temp source before cleanup delete")
    void shouldRejectOutsideTempSource_beforeDocumentCleanupDelete() throws Exception {
        DocumentUpload2Action action = outsideTempUploadAction("report.pdf");

        String result = action.executeUpload();

        assertThat(result).isNull();
        assertThat(response.getContentAsString()).contains("Invalid file upload");
        assertThat(tempUploadFile).exists();
    }

    @Test
    @DisplayName("incoming docs upload should delete allowed temp source after successful cleanup")
    void shouldDeleteAllowedTempSource_afterIncomingDocsUploadSucceeds() throws Exception {
        DocumentUpload2Action action = incomingDocsAction("report.pdf");
        tempDestinationDirectory = Files.createTempDirectory("incoming-docs-target").toFile();

        assertThat(PathValidationUtils.isInAllowedTempDirectory(tempUploadFile)).isTrue();

        try (MockedStatic<IncomingDocUtil> incomingDocUtilMock = mockStatic(IncomingDocUtil.class)) {
            incomingDocUtilMock.when(() -> IncomingDocUtil.getAndCreateIncomingDocumentFilePath(null, null))
                    .thenReturn(tempDestinationDirectory.getPath());

            String result = action.executeUpload();
            tempDestinationFile = tempDestinationDirectory.toPath().resolve("report.pdf").toFile();

            assertThat(result).isNull();
            assertThat(response.getContentAsString())
                    .contains("\"size\":3")
                    .doesNotContain("error");
            assertThat(tempDestinationFile).exists();
            assertThat(tempUploadFile).doesNotExist();
        }
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

    private DocumentUpload2Action outsideTempUploadAction(String filename) throws Exception {
        Path outsideDir = createTempDirectoryOutsideAllowedTemp();
        tempUploadDirectory = outsideDir.toFile();
        Path outsideUpload = Files.writeString(outsideDir.resolve("document-upload.pdf"), "pdf");
        tempUploadFile = outsideUpload.toFile();

        assertThat(PathValidationUtils.isInAllowedTempDirectory(tempUploadFile)).isFalse();

        DocumentUpload2Action action = new DocumentUpload2Action();
        action.setFiledata(tempUploadFile);
        action.setFiledataFileName(filename);
        return action;
    }

    private Path createTempDirectoryOutsideAllowedTemp() throws Exception {
        List<Path> candidateParents = List.of(
                Path.of(System.getProperty("user.home", ".")),
                Path.of("/workspace"),
                Path.of(".").toAbsolutePath()
        );

        for (Path candidateParent : candidateParents) {
            if (!Files.isDirectory(candidateParent) || !Files.isWritable(candidateParent)) {
                continue;
            }

            Path candidate = Files.createTempDirectory(candidateParent, "outside-upload-");
            if (!PathValidationUtils.isInAllowedTempDirectory(candidate.toFile())) {
                return candidate;
            }
            Files.deleteIfExists(candidate);
        }

        org.junit.jupiter.api.Assumptions.assumeTrue(false, "Unable to create a test upload directory outside allowed temp roots");
        return null;
    }

    private Exception deleteIfExists(File file, Exception cleanupFailure) {
        if (file == null) {
            return cleanupFailure;
        }

        try {
            Files.deleteIfExists(file.toPath());
            return cleanupFailure;
        } catch (IOException e) {
            return appendCleanupFailure(cleanupFailure, e);
        }
    }

    private Exception appendCleanupFailure(Exception cleanupFailure, Exception exception) {
        if (cleanupFailure == null) {
            return exception;
        }
        cleanupFailure.addSuppressed(exception);
        return cleanupFailure;
    }
}
