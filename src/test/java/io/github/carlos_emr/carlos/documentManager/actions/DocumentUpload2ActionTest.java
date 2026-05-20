/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.commn.dao.CtlDocTypeDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentUpload2Action Unit Tests")
@Tag("unit")
@Tag("documentManager")
class DocumentUpload2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private ProgramManager2 mockProgramManager;

    @TempDir
    private Path incomingDocumentDir;

    private Path documentDir;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private DocumentUpload2Action action;
    private File tempUploadFile;
    private String previousIncomingDocumentDir;
    private String previousDocumentDir;

    @BeforeEach
    void setUp() throws Exception {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        mockProgramManager = mock(ProgramManager2.class);
        registerMock(ProgramManager2.class, mockProgramManager);
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(true);

        action = new DocumentUpload2Action();
        tempUploadFile = File.createTempFile("document-upload", ".pdf");
        Files.write(tempUploadFile.toPath(), new byte[]{1});
        documentDir = Files.createDirectories(incomingDocumentDir.resolve("documents"));

        previousIncomingDocumentDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incomingDocumentDir.toString());
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempUploadFile != null) {
            Files.deleteIfExists(tempUploadFile.toPath());
        }
        if (previousIncomingDocumentDir == null) {
            CarlosProperties.getInstance().remove("INCOMINGDOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", previousIncomingDocumentDir);
        }
        if (previousDocumentDir == null) {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should not update preferred queue when incoming destination is invalid")
    void shouldNotUpdatePreferredQueue_whenIncomingDestinationIsInvalid() throws Exception {
        request.setParameter("destination", "incomingDocs");
        request.setParameter("queue", "123");
        request.setParameter("destFolder", "BadFolder");
        bindFiledataUpload(tempUploadFile, "scan.pdf", "application/pdf");

        String result = action.executeUpload();

        assertThat(result).isNull();
        assertThat(request.getSession().getAttribute("preferredQueue")).isNull();
        assertThat(response.getContentAsString()).contains("Invalid incoming document destination.");
    }

    @Test
    @DisplayName("should accept incoming PDF filename with repeated dots")
    void shouldAcceptIncomingPdfFilename_withRepeatedDots() throws Exception {
        request.setParameter("destination", "incomingDocs");
        request.setParameter("queue", "123");
        request.setParameter("destFolder", "Fax");
        bindFiledataUpload(tempUploadFile, "my..file.pdf", "application/pdf");

        String result = action.executeUpload();

        Path writtenFile = incomingDocumentDir.resolve("123").resolve("Fax").resolve("my.file.pdf");
        assertThat(result).isNull();
        assertThat(request.getSession().getAttribute("preferredQueue")).isEqualTo("123");
        assertThat(response.getContentAsString()).contains("my.file.pdf");
        assertThat(Files.readAllBytes(writtenFile)).containsExactly(1);
    }

    @Test
    @DisplayName("should normalize incoming request values before resolving folder paths")
    void shouldNormalizeIncomingValuesBeforePathResolution() throws Exception {
        request.setParameter("destination", "incomingDocs");
        request.setParameter("queue", " 123 ");
        request.setParameter("destFolder", " Fax ");
        bindFiledataUpload(tempUploadFile, "scan.pdf", "application/pdf");

        String result = action.executeUpload();

        Path writtenFile = incomingDocumentDir.resolve("123").resolve("Fax").resolve("scan.pdf");
        assertThat(result).isNull();
        assertThat(request.getSession().getAttribute("preferredQueue")).isEqualTo("123");
        assertThat(response.getContentAsString()).contains("scan.pdf");
        assertThat(Files.readAllBytes(writtenFile)).containsExactly(1);
    }

    @Test
    @DisplayName("should sanitize incoming filename path separators")
    void shouldSanitizeIncomingFilename_pathSeparators() throws Exception {
        request.setAttribute("user", "123");
        request.setParameter("destination", "incomingDocs");
        request.setParameter("queue", "123");
        request.setParameter("destFolder", "Fax");
        bindFiledataUpload(tempUploadFile, "nested/path\\deep/file.pdf", "application/pdf");

        String result = action.executeUpload();

        Path writtenFile = incomingDocumentDir.resolve("123").resolve("Fax").resolve("file.pdf");
        assertThat(result).isNull();
        assertThat(request.getSession().getAttribute("preferredQueue")).isEqualTo("123");
        assertThat(response.getContentAsString()).contains("file.pdf");
        assertThat(writtenFile).exists();
        assertThat(Files.readAllBytes(writtenFile)).containsExactly(1);
    }

    @Test
    @DisplayName("should not update preferred queue when incoming write fails")
    void shouldNotUpdatePreferredQueue_whenIncomingWriteFails() throws Exception {
        Files.createDirectories(incomingDocumentDir.resolve("123"));
        Files.write(incomingDocumentDir.resolve("123").resolve("Fax"), new byte[]{1});

        request.setParameter("destination", "incomingDocs");
        request.setParameter("queue", "123");
        request.setParameter("destFolder", "Fax");
        bindFiledataUpload(tempUploadFile, "scan.pdf", "application/pdf");

        String result = action.executeUpload();

        assertThat(result).isNull();
        assertThat(request.getSession().getAttribute("preferredQueue")).isNull();
        assertThat(response.getContentAsString()).contains("Failed to write file. Please contact administrator");
    }

    @Test
    @DisplayName("should delete temp upload and copied file when document persistence fails")
    void shouldDeleteTempUploadAndCopiedFile_whenDocumentPersistenceFails() throws Exception {
        request.getSession().setAttribute("user", "123");
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("123");
        registerEDocUtilStaticDependencies();
        bindFiledataUpload(tempUploadFile, "failed upload.txt", "text/plain");

        try (MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class)) {
            eDocUtilMock.when(() -> EDocUtil.addDocumentSQL(any()))
                    .thenThrow(new RuntimeException("database unavailable"));

            assertThatThrownBy(() -> action.executeUpload())
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("database unavailable");
        }

        assertThat(tempUploadFile).doesNotExist();
        try (var files = Files.list(documentDir)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    @DisplayName("should persist suffixed document filename when local upload destination collides")
    void shouldPersistSuffixedDocumentFilename_whenLocalUploadDestinationCollides() throws Exception {
        request.getSession().setAttribute("user", "123");
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("123");
        registerEDocUtilStaticDependencies();
        bindFiledataUpload(tempUploadFile, "duplicate.pdf", "application/pdf");
        Files.write(documentDir.resolve("20260519113000duplicate.pdf"), new byte[]{0});
        ArgumentCaptor<EDoc> persistedDocument = ArgumentCaptor.forClass(EDoc.class);

        try (MockedStatic<UtilDateUtilities> dateMock = mockStatic(UtilDateUtilities.class);
             MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class)) {
            dateMock.when(() -> UtilDateUtilities.DateToString(any(Date.class), eq("yyyyMMdd")))
                    .thenReturn("20260519");
            dateMock.when(() -> UtilDateUtilities.DateToString(any(Date.class), eq("HHmmss")))
                    .thenReturn("113000");
            eDocUtilMock.when(() -> EDocUtil.addDocumentSQL(any(EDoc.class))).thenReturn("42");

            String result = action.executeUpload();

            assertThat(result).isNull();
            eDocUtilMock.verify(() -> EDocUtil.addDocumentSQL(persistedDocument.capture()));
        }

        String actualFileName = persistedDocument.getValue().getFileName();
        assertThat(actualFileName).isEqualTo("20260519113000duplicate_1.pdf");
        assertThat(Files.readAllBytes(documentDir.resolve(actualFileName))).containsExactly(1);
        assertThat(response.getContentAsString()).contains(actualFileName);
    }

    @Test
    @DisplayName("should return upload error when local upload filename attempts are exhausted")
    void shouldReturnUploadError_whenLocalUploadFilenameAttemptsAreExhausted() throws Exception {
        request.getSession().setAttribute("user", "123");
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("123");
        registerEDocUtilStaticDependencies();
        bindFiledataUpload(tempUploadFile, "duplicate.pdf", "application/pdf");
        createCollisionAttempts("20260519113000duplicate.pdf");

        try (MockedStatic<UtilDateUtilities> dateMock = mockStatic(UtilDateUtilities.class);
             MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class)) {
            dateMock.when(() -> UtilDateUtilities.DateToString(any(Date.class), eq("yyyyMMdd")))
                    .thenReturn("20260519");
            dateMock.when(() -> UtilDateUtilities.DateToString(any(Date.class), eq("HHmmss")))
                    .thenReturn("113000");

            String result = action.executeUpload();

            assertThat(result).isNull();
            assertThat(response.getContentAsString())
                    .contains("Unable to create a unique document filename. Please try again.");
            eDocUtilMock.verify(() -> EDocUtil.addDocumentSQL(any(EDoc.class)), never());
        }
    }

    @Test
    @DisplayName("should preserve existing document when upload source validation fails")
    void shouldPreserveExistingDocument_whenUploadSourceValidationFails() throws Throwable {
        Path existingDocument = documentDir.resolve("existing.txt");
        Files.writeString(existingDocument, "keep");
        File outsideUploadSource = new File("pom.xml").getAbsoluteFile();
        assertThat(outsideUploadSource).isFile();

        assertThatThrownBy(() -> writeLocalFile(outsideUploadSource, "existing.txt"))
                .isInstanceOf(FileValidationException.class);

        assertThat(Files.readString(existingDocument)).isEqualTo("keep");
    }

    @Test
    @DisplayName("should append suffix instead of overwriting existing document destination")
    void shouldAppendSuffixInsteadOfOverwritingExistingDocumentDestination() throws Throwable {
        Path existingDocument = documentDir.resolve("existing.txt");
        Files.writeString(existingDocument, "keep");

        File copiedDocument = writeLocalFile(tempUploadFile, "existing.txt");

        assertThat(Files.readString(existingDocument)).isEqualTo("keep");
        assertThat(copiedDocument.getName()).isEqualTo("existing_1.txt");
        assertThat(Files.readAllBytes(copiedDocument.toPath())).containsExactly(1);
    }

    private void registerEDocUtilStaticDependencies() {
        createAndRegisterMock(ProgramManager.class);
        createAndRegisterMock(CaseManagementNoteLinkDAO.class);
        createAndRegisterMock(CaseManagementNoteDAO.class);
        createAndRegisterMock(TicklerLinkDao.class);
        createAndRegisterMock(TicklerManager.class);
        createAndRegisterMock(ProviderDao.class);
        createAndRegisterMock(CtlDocTypeDao.class);
        createAndRegisterMock(DemographicManager.class);
        createAndRegisterMock(CtlDocumentDao.class);
    }

    private File writeLocalFile(File sourceFile, String fileName) throws Throwable {
        Object validatedUpload = validatedUpload(sourceFile);
        Method writeLocalFile = DocumentUpload2Action.class
                .getDeclaredMethod("writeLocalFile", validatedUploadClass(), String.class);
        writeLocalFile.setAccessible(true);
        try {
            return (File) writeLocalFile.invoke(action, validatedUpload, fileName);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Object validatedUpload(File sourceFile) throws Throwable {
        Method from = validatedUploadClass().getDeclaredMethod("from", UploadedFile.class);
        from.setAccessible(true);
        try {
            return from.invoke(null, uploadedFile("filedata", sourceFile, sourceFile.getName(), "application/pdf"));
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Class<?> validatedUploadClass() throws ClassNotFoundException {
        return Class.forName(DocumentUpload2Action.class.getName() + "$ValidatedUpload");
    }

    private void bindFiledataUpload(File file, String originalName, String contentType) {
        action.withUploadedFiles(List.of(uploadedFile("filedata", file, originalName, contentType)));
    }

    private UploadedFile uploadedFile(String inputName, File file, String originalName, String contentType) {
        return new TestUploadedFile(inputName, file, originalName, contentType);
    }

    private static final class TestUploadedFile implements UploadedFile {
        private static final long serialVersionUID = 1L;

        private final String inputName;
        private final File content;
        private final String originalName;
        private final String contentType;

        private TestUploadedFile(String inputName, File content, String originalName, String contentType) {
            this.inputName = inputName;
            this.content = content;
            this.originalName = originalName;
            this.contentType = contentType;
        }

        @Override
        public Long length() {
            return content.length();
        }

        @Override
        public String getName() {
            return content.getName();
        }

        @Override
        public String getOriginalName() {
            return originalName;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public boolean delete() {
            return content.delete();
        }

        @Override
        public String getAbsolutePath() {
            return content.getAbsolutePath();
        }

        @Override
        public File getContent() {
            return content;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public String getInputName() {
            return inputName;
        }
    }

    private void createCollisionAttempts(String storageFileName) throws Exception {
        for (int attempt = 0; attempt < PathValidationUtils.MAX_UPLOAD_COLLISION_ATTEMPTS; attempt++) {
            Files.write(documentDir.resolve(fileNameWithCollisionSuffix(storageFileName, attempt)), new byte[]{0});
        }
    }

    private String fileNameWithCollisionSuffix(String fileName, int attempt) {
        if (attempt == 0) {
            return fileName;
        }

        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex > 0) {
            return fileName.substring(0, extensionIndex) + "_" + attempt + fileName.substring(extensionIndex);
        }
        return fileName + "_" + attempt;
    }
}
