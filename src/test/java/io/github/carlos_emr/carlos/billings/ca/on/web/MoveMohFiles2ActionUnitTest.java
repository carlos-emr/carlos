/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billing.CA.ON.util.EDTFolder;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.ViewMohFilesViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.zip;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.WebUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MoveMohFiles2Action} pinning the result-name contract
 * and the privilege gate.
 *
 * <p>Pre-existing bug: the action returned the literal string {@code "Success"}
 * (capital S) but the struts mapping at {@code struts-billing.xml:213} declared
 * {@code <result name="success">}. Struts2 result names are case-sensitive and
 * the global-results don't catch unmatched names, so every successful archival
 * call produced a {@code ConfigurationException} 500 instead of the
 * {@code viewMOHFiles.jsp} forward. The fix is to return
 * {@link ActionSupport#SUCCESS} (the inherited constant equal to {@code "success"}).
 *
 * @since 2026-04-29
 */
@DisplayName("MoveMohFiles2Action")
@Tag("unit")
@Tag("billing")
class MoveMohFiles2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<LocaleUtils> localeUtilsMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        // ServletActionContext.getRequest()/getResponse() must be stubbed before
        // the action is constructed — both fields are populated by the action's
        // field initializers.
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        localeUtilsMock = mockStatic(LocaleUtils.class);
        localeUtilsMock.when(() -> LocaleUtils.getMessage(any(Locale.class), anyString()))
                .thenAnswer(invocation -> localizedTestMessage(invocation.getArgument(1)));

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (localeUtilsMock != null) localeUtilsMock.close();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    /**
     * The whole point of this test class: returning the literal {@code "Success"}
     * silently bypasses the {@code <result name="success">} forward. Pin the
     * inherited constant explicitly so any future drift is caught.
     */
    @Test
    void shouldReturnLowercaseSuccessConstant_whenInvokedWithMissingFolderParam() throws Exception {
        // No folder/mohFile params → execute() falls through to the assemble +
        // return path without entering the file-archival loop. We don't care
        // about the assembled view model here — only the result name.
        MoveMohFiles2Action action = new MoveMohFiles2Action();

        String result = action.execute();

        assertThat(result)
                .as("must equal ActionSupport.SUCCESS so the struts mapping resolves")
                .isEqualTo(ActionSupport.SUCCESS)
                .isEqualTo("success");
    }


    @Test
    void shouldStorePlainTextMessages_whenValidationFails() throws Exception {
        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) mockRequest.getSession()
                .getAttribute(WebUtils.ERROR_MESSAGE_SESSION_KEY);
        assertThat(errors)
                .contains("A folder must be selected.", "Please select file(s) to archive.");
        assertThat(errors.toString()).doesNotContain("<br/>");
        assertThat(mockRequest.getSession().getAttribute(WebUtils.INFO_MESSAGE_SESSION_KEY)).isNull();
    }

    @Test
    void shouldRejectBlankFolder_whenArchivingSelectedFiles() throws Exception {
        mockRequest.addParameter("folder", " ");
        mockRequest.addParameter("mohFile", "claim.000");
        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) mockRequest.getSession()
                .getAttribute(WebUtils.ERROR_MESSAGE_SESSION_KEY);
        assertThat(errors).contains("A folder must be selected.");
        assertThat(errors.toString()).doesNotContain("Unable to find file claim.000.");
        assertThat(mockRequest.getSession().getAttribute(WebUtils.INFO_MESSAGE_SESSION_KEY)).isNull();
    }

    @Test
    void shouldRejectUnknownFolder_withoutFallingBackToInbox() throws Exception {
        Path inbox = Files.createTempDirectory("moh-inbox");
        Path claim = Files.writeString(inbox.resolve("claim.000"), "claim");
        Object originalInboxPath = ReflectionTestUtils.getField(EDTFolder.INBOX, "path");
        ReflectionTestUtils.setField(EDTFolder.INBOX, "path", inbox.toString());
        mockRequest.addParameter("folder", "bogus");
        mockRequest.addParameter("mohFile", "claim.000");

        try {
            MoveMohFiles2Action action = new MoveMohFiles2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        } finally {
            ReflectionTestUtils.setField(EDTFolder.INBOX, "path", originalInboxPath);
        }

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) mockRequest.getSession()
                .getAttribute(WebUtils.ERROR_MESSAGE_SESSION_KEY);
        assertThat(errors).contains("Invalid folder selection.");
        assertThat(Files.exists(claim)).isTrue();
        assertThat(mockRequest.getSession().getAttribute(WebUtils.INFO_MESSAGE_SESSION_KEY)).isNull();
    }

    @Test
    void shouldRejectUnknownFolder_withoutUnzippingIntoInbox() throws Exception {
        Path inbox = Files.createTempDirectory("moh-inbox");
        Object originalInboxPath = ReflectionTestUtils.getField(EDTFolder.INBOX, "path");
        ReflectionTestUtils.setField(EDTFolder.INBOX, "path", inbox.toString());
        mockRequest.addParameter("folder", "bogus");
        mockRequest.addParameter("unzipfile", "claim.zip");

        try (MockedStatic<zip> zipMock = mockStatic(zip.class)) {
            MoveMohFiles2Action action = new MoveMohFiles2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            zipMock.verifyNoInteractions();
        } finally {
            ReflectionTestUtils.setField(EDTFolder.INBOX, "path", originalInboxPath);
        }

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) mockRequest.getSession()
                .getAttribute(WebUtils.ERROR_MESSAGE_SESSION_KEY);
        assertThat(errors).contains("Invalid folder selection.");
        assertThat(mockRequest.getSession().getAttribute(WebUtils.INFO_MESSAGE_SESSION_KEY)).isNull();
    }

    @Test
    void shouldArchiveSelectedFile_whenPostSubmissionIsValid() throws Exception {
        Path inbox = Files.createTempDirectory("moh-inbox");
        Path archive = Files.createTempDirectory("moh-archive");
        Path claim = Files.writeString(inbox.resolve("claim.000"), "claim");
        Object originalInboxPath = ReflectionTestUtils.getField(EDTFolder.INBOX, "path");
        Object originalArchivePath = ReflectionTestUtils.getField(EDTFolder.ARCHIVE, "path");
        ReflectionTestUtils.setField(EDTFolder.INBOX, "path", inbox.toString());
        ReflectionTestUtils.setField(EDTFolder.ARCHIVE, "path", archive.toString());
        mockRequest.addParameter("folder", "inbox");
        mockRequest.addParameter("mohFile", "claim.000");

        try {
            MoveMohFiles2Action action = new MoveMohFiles2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        } finally {
            ReflectionTestUtils.setField(EDTFolder.INBOX, "path", originalInboxPath);
            ReflectionTestUtils.setField(EDTFolder.ARCHIVE, "path", originalArchivePath);
        }

        assertThat(Files.exists(claim)).isFalse();
        assertThat(Files.exists(archive.resolve("claim.000"))).isTrue();
        @SuppressWarnings("unchecked")
        List<String> messages = (List<String>) mockRequest.getSession()
                .getAttribute(WebUtils.INFO_MESSAGE_SESSION_KEY);
        assertThat(messages).contains("Archived file claim.000 successfully.");
        assertThat(mockRequest.getSession().getAttribute(WebUtils.ERROR_MESSAGE_SESSION_KEY)).isNull();
    }

    @Test
    void shouldSubstitutePlaceholder_whenLocalizedPatternContainsApostrophe() throws Exception {
        localeUtilsMock.when(() -> LocaleUtils.getMessage(any(Locale.class), eq("billing.moveMohFiles.error.fileMissing")))
                .thenReturn("Impossible d'archiver {0}.");
        MoveMohFiles2Action action = new MoveMohFiles2Action();

        Method method = MoveMohFiles2Action.class
                .getDeclaredMethod("localizedMessage", String.class, Object[].class);
        method.setAccessible(true);
        String message = (String) method.invoke(
                action, "billing.moveMohFiles.error.fileMissing", new Object[]{"claim.000"});

        assertThat(message).isEqualTo("Impossible d'archiver claim.000.");
    }

    @Test
    void shouldPreserveEscapedApostrophe_whenLocalizedPatternAlreadyEscapesMessageFormatQuote() throws Exception {
        localeUtilsMock.when(() -> LocaleUtils.getMessage(any(Locale.class), eq("billing.moveMohFiles.error.fileMissing")))
                .thenReturn("Impossible d''archiver {0}.");
        MoveMohFiles2Action action = new MoveMohFiles2Action();

        Method method = MoveMohFiles2Action.class
                .getDeclaredMethod("localizedMessage", String.class, Object[].class);
        method.setAccessible(true);
        String message = (String) method.invoke(
                action, "billing.moveMohFiles.error.fileMissing", new Object[]{"claim.000"});

        assertThat(message).isEqualTo("Impossible d'archiver claim.000.");
    }

    @Test
    void shouldThrowSecurityException_whenLackingAdminWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin.billing"), eq("w"), isNull()))
                .thenReturn(false);

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.billing");
    }

    /**
     * GET without mohFile = render path (allowed). The action is dual-purpose
     * by design: GET renders the file listing, POST archives selected files.
     * The HttpMethodGuardFilter previously blocked all GETs to this URL; the
     * conditional gate restores legitimate render-on-GET while still
     * enforcing POST-only for archival.
     */
    @Test
    void shouldReturnSuccess_onGetWithoutMutationIntent() throws Exception {
        mockRequest.setMethod("GET");

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(mockRequest.getSession().getAttribute(WebUtils.ERROR_MESSAGE_SESSION_KEY)).isNull();
        assertThat(mockRequest.getSession().getAttribute(WebUtils.INFO_MESSAGE_SESSION_KEY)).isNull();
    }

    @Test
    void shouldWarnOnUnknownFolder_whenRenderingGetFallback() throws Exception {
        Path inbox = Files.createTempDirectory("moh-inbox");
        Object originalInboxPath = ReflectionTestUtils.getField(EDTFolder.INBOX, "path");
        ReflectionTestUtils.setField(EDTFolder.INBOX, "path", inbox.toString());
        mockRequest.setMethod("GET");
        mockRequest.addParameter("folder", "bogus");

        try {
            MoveMohFiles2Action action = new MoveMohFiles2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        } finally {
            ReflectionTestUtils.setField(EDTFolder.INBOX, "path", originalInboxPath);
        }

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) mockRequest.getSession()
                .getAttribute(WebUtils.ERROR_MESSAGE_SESSION_KEY);
        assertThat(errors).contains("Invalid folder selection.");
        assertThat(mockRequest.getSession().getAttribute(WebUtils.INFO_MESSAGE_SESSION_KEY)).isNull();
        ViewMohFilesViewModel model = (ViewMohFilesViewModel) mockRequest.getAttribute("mohModel");
        assertThat(model.isInbox()).isTrue();
        assertThat(mockRequest.getSession().getAttribute("backupfilepath")).isEqualTo(inbox.toString());
    }

    /**
     * GET with mohFile = mutation intent on the wrong method. Action must 405
     * with an Allow header rather than silently archiving via GET.
     */
    @Test
    void shouldReturn405WithAllowHeader_onGetWithMutationIntent() throws Exception {
        mockRequest.setMethod("GET");
        mockRequest.addParameter("mohFile", "claim.000");

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldReturn405WithAllowHeader_onGetWithUnzipMutationIntent() throws Exception {
        mockRequest.setMethod("GET");
        mockRequest.addParameter("folder", "inbox");
        mockRequest.addParameter("unzipfile", "claim.zip");

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldAllowUnzipOnlyPost_withoutArchiveSelectionError() throws Exception {
        mockRequest.setMethod("POST");
        mockRequest.addParameter("folder", "inbox");
        mockRequest.addParameter("unzipfile", "claim.zip");

        try (MockedStatic<zip> zipMock = mockStatic(zip.class)) {
            zipMock.when(() -> zip.unzipXML(anyString(), eq("claim.zip"))).thenReturn(true);
            MoveMohFiles2Action action = new MoveMohFiles2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        }

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        Object errors = mockRequest.getSession().getAttribute(WebUtils.ERROR_MESSAGE_SESSION_KEY);
        assertThat(errors == null ? "" : errors.toString())
                .doesNotContain("Please select file(s) to archive.<br/>");
        @SuppressWarnings("unchecked")
        List<String> messages = (List<String>) mockRequest.getSession()
                .getAttribute(WebUtils.INFO_MESSAGE_SESSION_KEY);
        assertThat(messages).contains("Extracted file claim.zip successfully.");
    }

    @Test
    void shouldTreatBlankMohFileParameterAsRenderOnly_onGet() throws Exception {
        mockRequest.setMethod("GET");
        mockRequest.addParameter("folder", "inbox");
        mockRequest.addParameter("mohFile", " ");

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void shouldReturn405WithAllowHeader_onGetWithAnyNonBlankMohFile() throws Exception {
        mockRequest.setMethod("GET");
        mockRequest.addParameter("mohFile", " ", "claim.000");

        MoveMohFiles2Action action = new MoveMohFiles2Action();

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldPropagateProgrammingErrors_fromFileLocationValidation() {
        MoveMohFiles2Action action = new MoveMohFiles2Action();

        try (MockedStatic<PathValidationUtils> pathMock = mockStatic(PathValidationUtils.class)) {
            pathMock.when(() -> PathValidationUtils.validateExistingPath(any(), any()))
                    .thenThrow(new NullPointerException("programming error"));

            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    action, "validateFileLocation", new File("claim.000")))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("programming error");
        }
    }

    @Test
    void shouldAddMissingFileError_whenSelectedFileDisappears() throws Exception {
        Path folder = Files.createTempDirectory("moh-files");
        Object originalInboxPath = ReflectionTestUtils.getField(EDTFolder.INBOX, "path");
        ReflectionTestUtils.setField(EDTFolder.INBOX, "path", folder.toString());
        mockRequest.addParameter("folder", "inbox");
        mockRequest.addParameter("mohFile", "claim.000");

        try {
            MoveMohFiles2Action action = new MoveMohFiles2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        } finally {
            ReflectionTestUtils.setField(EDTFolder.INBOX, "path", originalInboxPath);
        }

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) mockRequest.getSession()
                .getAttribute(WebUtils.ERROR_MESSAGE_SESSION_KEY);
        assertThat(errors).contains("Unable to find file claim.000.");
    }

    @Test
    void shouldRejectEncodedTraversal_whenResolvingMohFile() throws Exception {
        Path folder = Files.createTempDirectory("moh-files");
        MoveMohFiles2Action action = new MoveMohFiles2Action();

        File resolved = ReflectionTestUtils.invokeMethod(
                action, "getFile", folder.toString(), "..%2F..%2Fsecret.txt");

        assertThat(resolved).isNull();
    }

    private static String localizedTestMessage(String key) {
        return switch (key) {
            case "billing.moveMohFiles.error.folderRequired" -> "A folder must be selected.";
            case "billing.moveMohFiles.error.fileRequired" -> "Please select file(s) to archive.";
            case "billing.moveMohFiles.error.invalidFolder" -> "Invalid folder selection.";
            case "billing.moveMohFiles.error.fileMissing" -> "Unable to find file {0}.";
            case "billing.moveMohFiles.error.invalidFileLocation" -> "File is not in a valid location: {0}.";
            case "billing.moveMohFiles.info.archived" -> "Archived file {0} successfully.";
            case "billing.moveMohFiles.info.unzipped" -> "Extracted file {0} successfully.";
            case "billing.moveMohFiles.error.archiveFailed" -> "Unable to archive {0}.";
            default -> key;
        };
    }
}
