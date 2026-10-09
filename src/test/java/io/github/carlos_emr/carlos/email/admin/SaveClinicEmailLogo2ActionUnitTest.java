/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.admin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collections;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.LogoRejectedException;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionInvocation;
import org.apache.struts2.ActionProxy;
import org.apache.struts2.dispatcher.LocalizedMessage;
import org.apache.struts2.dispatcher.multipart.MultiPartRequestWrapper;
import org.apache.struts2.interceptor.ActionFileUploadInterceptor;
import org.apache.struts2.interceptor.DefaultWorkflowInterceptor;
import org.apache.struts2.text.TextProvider;
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
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
        var stored = mock(io.github.carlos_emr.carlos.commn.model.EmailFooterLogo.class);
        when(stored.getId()).thenReturn(123);
        when(logoService.replace(any(), anyString())).thenReturn(stored);
        SaveClinicEmailLogo2Action action = action();
        action.withUploadedFiles(List.of(upload("other", new byte[] {9}), upload("logoFile", picture)));
        request.addParameter("logoAction", "upload");

        action.execute();

        verify(logoService).replace(picture, "999998");
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?logoSaved=true");
        logAction.verify(() -> LogAction.addLog("999998", "update", "emailFooterLogo", "123", "127.0.0.1"));
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
    @DisplayName("should report a server failure for an upload outside the temporary upload folders")
    void shouldReportServerFailure_whenOutsideUploadFolders() throws Exception {
        allowAdminWrite();
        UploadedFile outside = mock(UploadedFile.class);
        when(outside.getInputName()).thenReturn("logoFile");
        when(outside.getContent()).thenReturn(new java.io.File("/etc/hostname"));
        SaveClinicEmailLogo2Action action = action();
        action.withUploadedFiles(List.of(outside));
        request.addParameter("logoAction", "upload");

        action.execute();

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?logoError=UPLOAD_FAILED");
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

    @Test
    @DisplayName("should report a server failure when the bound upload disappears before it is read")
    void shouldReportServerFailure_whenBoundUploadDisappears() throws Exception {
        allowAdminWrite();
        UploadedFile uploaded = upload("logoFile", new byte[] {1});
        SaveClinicEmailLogo2Action action = action();
        action.withUploadedFiles(List.of(uploaded));
        Files.delete(((java.io.File) uploaded.getContent()).toPath());
        request.addParameter("logoAction", "upload");

        action.execute();

        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/admin/ViewConfigureEmail?logoError=UPLOAD_FAILED");
        verifyNoInteractions(logoService);
        logAction.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"struts.messages.upload.error.FileUploadSizeException",
            "struts.messages.upload.error.FileUploadByteCountLimitException",
            "struts.messages.error.uploading", "struts.messages.upload.error.FileUploadContentTypeException",
            "struts.messages.upload.error.FileUploadFileCountLimitException"})
    @DisplayName("should distinguish size and parser faults through the upload and workflow interceptors")
    void shouldSelectUploadOutcome_whenMultipartInterceptorRejects(String errorKey) throws Exception {
        MultiPartRequestWrapper multipart = mock(MultiPartRequestWrapper.class);
        when(multipart.hasErrors()).thenReturn(true);
        when(multipart.getErrors()).thenReturn(List.of(
                new LocalizedMessage(getClass(), errorKey, "Upload refused", new Object[0])));
        when(multipart.getFileParameterNames()).thenReturn(Collections.emptyEnumeration());
        String expected = (errorKey.endsWith("FileUploadSizeException") || errorKey.endsWith("FileUploadByteCountLimitException")) ? "uploadTooBig" : ActionSupport.INPUT;
        assertThat(runUploadInterceptors(multipart)).isEqualTo(expected);
        verifyNoInteractions(logoService);
        logAction.verifyNoInteractions();
    }

    @Test
    @DisplayName("should retain the size outcome when the file interceptor rejects an oversized file")
    void shouldSelectSizeOutcome_whenUploadFileExceedsInterceptorLimit() throws Exception {
        UploadedFile file = mock(UploadedFile.class);
        when(file.length()).thenReturn(1_048_577L);
        when(file.getOriginalName()).thenReturn("synthetic-logo.png");
        when(file.getName()).thenReturn("synthetic-upload");
        when(file.getContentType()).thenReturn("image/png");
        MultiPartRequestWrapper multipart = mock(MultiPartRequestWrapper.class);
        when(multipart.getFileParameterNames()).thenReturn(Collections.enumeration(List.of("logoFile")));
        when(multipart.getFiles("logoFile")).thenReturn(new UploadedFile[]{file});
        assertThat(runUploadInterceptors(multipart)).isEqualTo("uploadTooBig");
        verifyNoInteractions(logoService);
        logAction.verifyNoInteractions();
    }

    @Test
    @DisplayName("should use the generic upload outcome when parser errors include a non-size fault")
    void shouldSelectGenericOutcome_whenMultipartErrorsMixed() throws Exception {
        MultiPartRequestWrapper multipart = mock(MultiPartRequestWrapper.class);
        when(multipart.hasErrors()).thenReturn(true);
        when(multipart.getErrors()).thenReturn(List.of(
                new LocalizedMessage(getClass(), "struts.messages.upload.error.FileUploadSizeException",
                        "Size fault", new Object[0]),
                new LocalizedMessage(getClass(), "struts.messages.error.uploading", "Parser fault", new Object[0])));
        when(multipart.getFileParameterNames()).thenReturn(Collections.emptyEnumeration());
        assertThat(runUploadInterceptors(multipart)).isEqualTo(ActionSupport.INPUT);
        verifyNoInteractions(logoService);
    }

    // Use Struts' real file binding/validation and workflow; only the text provider and
    // servlet multipart parser are fixtures. An error must stop before execute/save/audit.
    private String runUploadInterceptors(MultiPartRequestWrapper multipart) throws Exception {
        SaveClinicEmailLogo2Action action = spy(action());
        doReturn("Upload refused").when(action).getText(anyString(), any(String[].class));
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(multipart);
        ActionInvocation invocation = mock(ActionInvocation.class);
        ActionContext context = mock(ActionContext.class);
        ActionProxy proxy = mock(ActionProxy.class);
        when(invocation.getAction()).thenReturn(action);
        when(invocation.getInvocationContext()).thenReturn(context);
        when(context.getServletRequest()).thenReturn(multipart);
        when(invocation.getProxy()).thenReturn(proxy);
        when(proxy.getMethod()).thenReturn("execute");
        TextProvider provider = mock(TextProvider.class);
        when(provider.hasKey(anyString())).thenReturn(true);
        when(provider.getText(anyString(), any(List.class))).thenReturn("Upload refused");
        ActionFileUploadInterceptor uploadInterceptor = new ActionFileUploadInterceptor() {
            @Override
            protected TextProvider getTextProvider(Object ignored) {
                return provider;
            }
        };
        uploadInterceptor.setMaximumSize(1_048_576L);
        when(invocation.invoke()).thenAnswer(call -> new DefaultWorkflowInterceptor().intercept(invocation));
        return uploadInterceptor.intercept(invocation);
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
