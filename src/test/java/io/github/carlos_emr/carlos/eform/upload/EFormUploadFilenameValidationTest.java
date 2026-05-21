package io.github.carlos_emr.carlos.eform.upload;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("eForm upload filename validation")
@Tag("unit")
@Tag("security")
class EFormUploadFilenameValidationTest extends CarlosUnitTestBase {

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
    void imageUploadShouldRejectHiddenFilenameBeforeWriting() throws Exception {
        Path upload = Files.createTempFile(tempDir, "image-upload", ".png");
        ImageUpload2Action action = new ImageUpload2Action();
        action.setImage(upload.toFile());
        action.setImageFileName(".hidden.png");

        String result = action.execute();

        assertThat(result).isEqualTo("error");
        assertThat(action.getActionErrors())
                .anySatisfy(error -> assertThat(error).contains("hidden files not allowed"));
    }

    @Test
    @DisplayName("HTML upload should reject hidden filename before saving eForm")
    void htmlUploadShouldRejectHiddenFilenameBeforeSavingEForm() throws Exception {
        Path upload = Files.createTempFile(tempDir, "html-upload", ".html");
        HtmlUpload2Action action = new HtmlUpload2Action();
        action.setFormHtml(upload.toFile());
        action.setFormHtmlFileName(".hidden.html");

        String result = action.execute();

        assertThat(result).isEqualTo("fail");
        assertThat(request.getAttribute("errorMessage").toString())
                .contains("hidden files not allowed");
    }
}
