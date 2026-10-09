/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.admin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.LogoRejectedException;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Saving and removing the clinic's email footer logo (issue #3981): POST only, {@code _admin}
 * write, a fixed outcome code on the redirect, and an audit entry for each change.
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
@Tag("security")
@DisplayName("Save clinic email logo")
class SaveClinicEmailLogo2ActionUnitTest {

    @TempDir
    private Path tempDir;

    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final EmailFooterLogoService logoService = mock(EmailFooterLogoService.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;
    private MockedStatic<LogAction> logAction;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/admin/saveClinicEmailLogo");
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        logAction = mockStatic(LogAction.class);
    }

    @AfterEach
    void tearDown() {
        logAction.close();
        loggedInInfoStatic.close();
        servletActionContext.close();
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    @DisplayName("should refuse anything but POST before checking rights or touching the logo")
    void shouldRefuseNonPost_beforeAnything(String method) throws Exception {
        request.setMethod(method);
        request.addParameter("logoAction", "remove");

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, logoService);
    }

    @Test
    @DisplayName("should refuse a user without _admin write")
    void shouldRefuse_whenAdminWriteMissing() {
        request.addParameter("logoAction", "remove");

        assertThatThrownBy(() -> action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verifyNoInteractions(logoService);
    }

    @Test
    @DisplayName("should save the uploaded file, audit it and say so on the page")
    void shouldSaveLogo_whenUploadAccepted() throws Exception {
        allowAdminWrite();
        byte[] picture = {1, 2, 3};
        SaveClinicEmailLogo2Action action = action();
        action.withUploadedFiles(List.of(upload("other", new byte[] {9}), upload("logoFile", picture)));
        request.addParameter("logoAction", "upload");

        action.execute();

        verify(logoService).replace(picture, "999998");
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?logoSaved=true");
        logAction.verify(() -> LogAction.addLog("999998", "update", "emailFooterLogo", "", "127.0.0.1"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(EmailFooterLogoService.Rejection.class)
    @DisplayName("should send each refusal back as its fixed reason code, without an audit entry")
    void shouldRedirectWithReason_whenUploadRefused(EmailFooterLogoService.Rejection reason) throws Exception {
        allowAdminWrite();
        LogoRejectedException refused = mock(LogoRejectedException.class);
        when(refused.reason()).thenReturn(reason);
        when(logoService.replace(any(), anyString())).thenThrow(refused);
        SaveClinicEmailLogo2Action action = action();
        action.withUploadedFiles(List.of(upload("logoFile", new byte[] {1})));
        request.addParameter("logoAction", "upload");

        action.execute();

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?logoError=" + reason.name());
        logAction.verifyNoInteractions();
    }

    @Test
    @DisplayName("should report no file, and refuse a file over the limit unread")
    void shouldRefuseUnread_whenFileMissingOrTooBig() throws Exception {
        allowAdminWrite();
        request.addParameter("logoAction", "upload");

        action().execute();
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?logoError=EMPTY");

        response = new MockHttpServletResponse();
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        SaveClinicEmailLogo2Action action = action();
        action.withUploadedFiles(List.of(upload("logoFile", new byte[EmailFooterLogoService.MAX_BYTES + 1])));
        action.execute();
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?logoError=TOO_BIG");

        verifyNoInteractions(logoService);
    }

    @Test
    @DisplayName("should ignore an upload from outside the temporary upload folders")
    void shouldIgnoreUpload_whenOutsideUploadFolders() throws Exception {
        allowAdminWrite();
        UploadedFile outside = mock(UploadedFile.class);
        when(outside.getInputName()).thenReturn("logoFile");
        when(outside.getContent()).thenReturn(new java.io.File("/etc/hostname"));
        SaveClinicEmailLogo2Action action = action();
        action.withUploadedFiles(List.of(outside));
        request.addParameter("logoAction", "upload");

        action.execute();

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?logoError=EMPTY");
        verifyNoInteractions(logoService);
    }

    @Test
    @DisplayName("should remove the logo and say so, and say and audit nothing when there was none")
    void shouldRemoveLogo_andAuditOnlyRealRemoval() throws Exception {
        allowAdminWrite();
        request.addParameter("logoAction", "remove");
        when(logoService.remove("999998")).thenReturn(true, false);

        action().execute();
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?logoRemoved=true");
        logAction.verify(() -> LogAction.addLog("999998", "delete", "emailFooterLogo", "", "127.0.0.1"));

        response = new MockHttpServletResponse();
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        action().execute();
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail");
        logAction.verify(() -> LogAction.addLog(anyString(), eq("delete"), anyString(), anyString(), anyString()));
    }

    @Test
    @DisplayName("should answer 400 for an unknown action")
    void shouldAnswerBadRequest_whenActionUnknown() throws Exception {
        allowAdminWrite();
        request.addParameter("logoAction", "https://elsewhere.example");

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getRedirectedUrl()).isNull();
        verifyNoInteractions(logoService);
    }

    private void allowAdminWrite() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null)).thenReturn(true);
    }

    private SaveClinicEmailLogo2Action action() {
        return new SaveClinicEmailLogo2Action(securityInfoManager, logoService);
    }

    private UploadedFile upload(String inputName, byte[] bytes) throws Exception {
        Path file = Files.write(Files.createTempFile(tempDir, "logo", ".upload"), bytes);
        UploadedFile uploaded = mock(UploadedFile.class);
        when(uploaded.getInputName()).thenReturn(inputName);
        when(uploaded.getContent()).thenReturn(file.toFile());
        return uploaded;
    }
}
