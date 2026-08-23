package io.github.carlos_emr.carlos.eform.upload;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.EFormGroupDao;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("eForm upload filename validation")
@Tag("unit")
@Tag("security")
class EFormUploadFilenameValidationUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private SecurityInfoManager securityInfoManager;
    private MockHttpServletRequest request;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(CaseManagementManager.class, mock(CaseManagementManager.class));
        registerMock(CaseManagementNoteLinkDAO.class, mock(CaseManagementNoteLinkDAO.class));
        registerMock(EFormDataDao.class, mock(EFormDataDao.class));
        registerMock(EFormValueDao.class, mock(EFormValueDao.class));
        registerMock(EFormGroupDao.class, mock(EFormGroupDao.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(TicklerDao.class, mock(TicklerDao.class));
        registerMock(PreventionManager.class, mock(PreventionManager.class));
        registerMock(ProgramManager2.class, mock(ProgramManager2.class));
        registerMock(ConsultationRequestDao.class, mock(ConsultationRequestDao.class));
        registerMock(ProfessionalSpecialistDao.class, mock(ProfessionalSpecialistDao.class));
        registerMock(EFormDao.class, mock(EFormDao.class));
        when(securityInfoManager.hasPrivilege(any(), eq("_eform"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("image upload should reject hidden filename before writing")
    void shouldRejectImageUpload_whenFilenameIsHidden() throws Exception {
        Path upload = Files.createTempFile(tempDir, "image-upload", ".png");
        ImageUpload2Action action = new ImageUpload2Action();
        action.setImage(upload.toFile());
        action.setImageFileName(".hidden.png");

        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(action.getActionErrors())
                .anySatisfy(error -> assertThat(error).contains("Invalid filename"));
    }

    @Test
    @DisplayName("HTML upload should reject hidden filename before saving eForm")
    void shouldRejectHtmlUpload_whenFilenameIsHidden() throws Exception {
        Path upload = Files.createTempFile(tempDir, "html-upload", ".html");
        HtmlUpload2Action action = new HtmlUpload2Action();
        action.setFormHtml(upload.toFile());
        action.setFormHtmlFileName(".hidden.html");

        String result = action.execute();

        assertThat(result).isEqualTo("fail");
        assertThat(request.getAttribute("errorMessage").toString())
                .contains("Invalid filename");
    }

    @Test
    @DisplayName("HTML upload should reject blocked final extension before saving eForm")
    void shouldRejectHtmlUpload_whenFilenameHasBlockedFinalExtension() throws Exception {
        Path upload = Files.createTempFile(tempDir, "html-upload", ".html");
        Files.writeString(upload, "<html></html>");
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getContent()).thenReturn(upload.toFile());
        when(uploadedFile.getContentType()).thenReturn("text/html");
        when(uploadedFile.getOriginalName()).thenReturn("report.pdf.jsp");

        HtmlUpload2Action action = new HtmlUpload2Action();
        action.withUploadedFiles(List.of(uploadedFile));

        String result = action.execute();

        assertThat(result).isEqualTo("fail");
        assertThat(action.getFormHtmlFileName()).isNull();
        assertThat(request.getAttribute("errorMessage").toString())
                .contains("Invalid filename");
    }

    @Test
    @DisplayName("HTML upload should fall back to temp filename when original name is missing")
    void shouldFallbackToTempFilename_whenHtmlOriginalNameIsMissing() throws Exception {
        Path upload = Files.createTempFile(tempDir, "htmlupload", ".html");
        Files.writeString(upload, "<html></html>");
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getContent()).thenReturn(upload.toFile());
        when(uploadedFile.getContentType()).thenReturn("text/html");
        when(uploadedFile.getOriginalName()).thenReturn(null);

        HtmlUpload2Action action = new HtmlUpload2Action();
        action.withUploadedFiles(List.of(uploadedFile));

        try (MockedStatic<EFormUtil> eFormUtilMock = mockStatic(EFormUtil.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo("success");
            eFormUtilMock.verify(() -> EFormUtil.saveEForm(
                    isNull(),
                    isNull(),
                    eq(upload.getFileName().toString()),
                    anyString(),
                    eq(false),
                    eq(false),
                    isNull()));
        }
    }

    @Test
    @DisplayName("image upload should capture valid Struts upload source")
    void shouldCaptureImageUpload_whenStrutsUploadedFileIsValid() throws Exception {
        Path upload = Files.createTempFile(tempDir, "image-upload", ".png");
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getContent()).thenReturn(upload.toFile());
        when(uploadedFile.getContentType()).thenReturn("image/png");
        when(uploadedFile.getOriginalName()).thenReturn("diagram.png");

        ImageUpload2Action action = new ImageUpload2Action();
        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getImage()).isNotNull();
        assertThat(action.getImage().getCanonicalPath()).isEqualTo(upload.toFile().getCanonicalPath());
    }

    @Test
    @DisplayName("image upload should reject hidden Struts filename with user-facing error")
    void shouldReturnError_whenStrutsImageFilenameIsHidden() throws Exception {
        Path upload = Files.createTempFile(tempDir, "image-upload", ".png");
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getContent()).thenReturn(upload.toFile());
        when(uploadedFile.getContentType()).thenReturn("image/png");
        when(uploadedFile.getOriginalName()).thenReturn(".hidden.png");

        ImageUpload2Action action = new ImageUpload2Action();
        action.withUploadedFiles(List.of(uploadedFile));

        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(action.getActionErrors())
                .anySatisfy(error -> assertThat(error).contains("Invalid filename"));
    }

    @Test
    @DisplayName("image upload should reject source outside allowed temp directories")
    void shouldRejectImageUpload_whenSourceIsOutsideTempDirectories() throws Exception {
        Path outsideDir = Files.createTempDirectory(Path.of(System.getProperty("user.home")), "disallowed-upload-");
        Path outside = Files.createFile(outsideDir.resolve("image.png"));
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getContent()).thenReturn(outside.toFile());
        when(uploadedFile.getContentType()).thenReturn("image/png");
        when(uploadedFile.getOriginalName()).thenReturn("diagram.png");

        try {
            ImageUpload2Action action = new ImageUpload2Action();
            List<UploadedFile> uploadedFiles = List.of(uploadedFile);

            assertThatThrownBy(() -> action.withUploadedFiles(uploadedFiles))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Invalid upload source");
        } finally {
            Files.deleteIfExists(outside);
            Files.deleteIfExists(outsideDir);
        }
    }
}
