/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Hashtable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AddEditDocument2Action}.
 *
 * @since 2026-04-21
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddEditDocument2Action Unit Tests")
@Tag("unit")
@Tag("documentManager")
class AddEditDocument2ActionTest extends CarlosUnitTestBase {
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String ECHART_UPLOAD_FILENAME = "echart-upload.pdf";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @TempDir
    private Path documentDir;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private AddEditDocument2Action action;
    private File tempUploadFile;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(true);

        action = new AddEditDocument2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempUploadFile != null) {
            Files.deleteIfExists(tempUploadFile.toPath());
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should capture Struts 7 docFile upload metadata")
    void shouldCaptureStruts7DocFileUploadMetadata() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = uploadedFile("docFile", tempUploadFile, ECHART_UPLOAD_FILENAME);

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo(ECHART_UPLOAD_FILENAME);
        assertThat(action.getDocFileContentType()).isEqualTo(PDF_CONTENT_TYPE);
    }

    @Test
    @DisplayName("should capture Struts 7 filedata upload metadata when docFile is absent")
    void shouldCaptureStruts7FiledataUploadMetadata_whenDocFileAbsent() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = uploadedFile("filedata", tempUploadFile, "html5-upload.pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo("html5-upload.pdf");
        assertThat(action.getDocFileContentType()).isEqualTo(PDF_CONTENT_TYPE);
    }

    @Test
    @DisplayName("should ignore Struts 7 uploads with unsupported input names")
    void shouldIgnoreUpload_whenInputNameIsUnsupported() {
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("otherFile");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNull();
        assertThat(action.getDocFileFileName()).isNull();
        assertThat(action.getDocFileContentType()).isNull();
    }

    @Test
    @DisplayName("should prefer docFile over filedata regardless of list ordering")
    void shouldPreferDocFileOverFiledata_regardlessOfListOrdering() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");
        UploadedFile filedataUpload = uploadedInput("filedata");
        UploadedFile docFileUpload = uploadedFile("docFile", tempUploadFile, ECHART_UPLOAD_FILENAME);

        // filedata appears first in the list - docFile must still win
        action.withUploadedFiles(List.of(filedataUpload, docFileUpload));

        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo(ECHART_UPLOAD_FILENAME);
    }

    @Test
    @DisplayName("should keep the first filedata upload when docFile is absent")
    void shouldKeepFirstFiledata_whenMultipleFiledataUploadsProvided() throws Exception {
        tempUploadFile = File.createTempFile("first-filedata-upload", ".pdf");
        UploadedFile firstFiledataUpload = uploadedFile("filedata", tempUploadFile, "first-upload.pdf");
        UploadedFile secondFiledataUpload = uploadedInput("filedata");

        action.withUploadedFiles(List.of(firstFiledataUpload, secondFiledataUpload));

        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo("first-upload.pdf");
    }

    @Test
    @DisplayName("should ignore later uploads after docFile is selected")
    void shouldIgnoreLaterUploads_whenDocFileIsSelected() throws Exception {
        tempUploadFile = File.createTempFile("docfile-upload", ".pdf");
        File laterUploadFile = File.createTempFile("later-upload", ".pdf");
        try {
            UploadedFile docFileUpload = uploadedFile("docFile", tempUploadFile, ECHART_UPLOAD_FILENAME);
            UploadedFile laterUpload = uploadedFile("filedata", laterUploadFile, "later-upload.pdf");

            action.withUploadedFiles(List.of(docFileUpload, laterUpload));

            assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
            assertThat(action.getDocFileFileName()).isEqualTo(ECHART_UPLOAD_FILENAME);
        } finally {
            Files.deleteIfExists(laterUploadFile.toPath());
        }
    }

    @Test
    @DisplayName("should return failAdd when uploaded document is missing")
    @SuppressWarnings("unchecked")
    void shouldReturnFailAdd_whenUploadedDocumentMissing() {
        action.setMode("add");
        action.setFunction("demographic");
        action.setFunctionId("123");
        action.setDocDesc("Consult note");
        action.setDocType("Consultant Report");

        String result = action.execute2();

        assertThat(result).isEqualTo("failAdd");
        Hashtable<String, String> errors = (Hashtable<String, String>) request.getAttribute("docerrors");
        assertThat(errors).containsEntry("uploaderror", "dms.error.uploadError");
    }

    @Test
    @DisplayName("should reject standard add upload source outside upload temp directories")
    @SuppressWarnings("unchecked")
    void shouldRejectStandardAddUploadSource_whenSourceIsOutsideUploadTempDirectories() {
        File outsideUploadSource = new File("pom.xml").getAbsoluteFile();
        assertThat(outsideUploadSource).isFile();

        action.setMode("add");
        action.setFunction("demographic");
        action.setFunctionId("123");
        action.setDocDesc("Consult note");
        action.setDocType("Consultant Report");
        action.setDocFile(outsideUploadSource);
        action.setDocFileFileName("outside.pdf");

        String result = action.execute2();

        assertThat(result).isEqualTo("failAdd");
        Hashtable<String, String> errors = (Hashtable<String, String>) request.getAttribute("docerrors");
        assertThat(errors).containsEntry("uploaderror", "dms.error.uploadError");
    }

    @Test
    @DisplayName("should reject standard edit upload source outside upload temp directories")
    @SuppressWarnings("unchecked")
    void shouldRejectStandardEditUploadSource_whenSourceIsOutsideUploadTempDirectories() {
        File outsideUploadSource = new File("pom.xml").getAbsoluteFile();
        assertThat(outsideUploadSource).isFile();
        String previousUpdateDocumentContent = CarlosProperties.getInstance()
                .getProperty("ALLOW_UPDATE_DOCUMENT_CONTENT");

        action.setMode("42");
        action.setDocDesc("Consult note");
        action.setDocType("Consultant Report");
        action.setDocFile(outsideUploadSource);
        action.setDocFileFileName("outside.pdf");

        try {
            CarlosProperties.getInstance().setProperty("ALLOW_UPDATE_DOCUMENT_CONTENT", "true");

            String result = action.execute2();

            assertThat(result).isEqualTo("failEdit");
            Hashtable<String, String> errors = (Hashtable<String, String>) request.getAttribute("docerrors");
            assertThat(errors).containsEntry("uploaderror", "dms.error.uploadError");
        } finally {
            if (previousUpdateDocumentContent == null) {
                CarlosProperties.getInstance().remove("ALLOW_UPDATE_DOCUMENT_CONTENT");
            } else {
                CarlosProperties.getInstance()
                        .setProperty("ALLOW_UPDATE_DOCUMENT_CONTENT", previousUpdateDocumentContent);
            }
        }
    }

    @Test
    @DisplayName("should return bad request when HTML5 upload source is outside upload temp directories")
    void shouldReturnBadRequestForHtml5UploadSource_whenSourceIsOutsideUploadTempDirectories() throws Exception {
        File outsideUploadSource = new File("pom.xml").getAbsoluteFile();
        assertThat(outsideUploadSource).isFile();

        action.setDocFile(outsideUploadSource);
        action.setDocFileFileName("outside.pdf");

        String result = action.html5MultiUpload();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getHeader("oscar_error")).contains("Invalid upload source");
    }

    @Test
    @DisplayName("should build document report redirect with allowlisted local parameters")
    void shouldBuildDocumentReportRedirect_withAllowlistedLocalParameters() {
        request.setContextPath("/oscar");
        request.addParameter("function", "demographic");
        request.addParameter("functionid", "123");
        request.addParameter("curUser", "prov-1");
        request.addParameter("appointmentNo", "456");
        request.addParameter("parentAjaxId", "doc-panel:1");

        String redirect = AddEditDocument2Action.buildDocumentReportRedirect(request);

        assertThat(redirect).isEqualTo("/oscar/documentManager/ViewDocumentReport"
                + "?docerrors=docerrors"
                + "&function=demographic"
                + "&functionid=123"
                + "&curUser=prov-1"
                + "&appointmentNo=456"
                + "&parentAjaxId=doc-panel%3A1"
                + "&updateParent=true");
    }

    @Test
    @DisplayName("should strip unsafe values from document report redirect parameters")
    void shouldStripUnsafeValuesFromDocumentReportRedirectParameters() {
        request.setContextPath("/oscar");
        request.addParameter("function", "demographic");
        request.addParameter("functionid", "123\r\nLocation:https://evil.example");
        request.addParameter("curUser", "//evil.example");
        request.addParameter("appointmentNo", "456");
        request.addParameter("parentAjaxId", "doc<script>");

        String redirect = AddEditDocument2Action.buildDocumentReportRedirect(request);

        assertThat(redirect).isEqualTo("/oscar/documentManager/ViewDocumentReport"
                + "?docerrors=docerrors"
                + "&function=demographic"
                + "&functionid="
                + "&curUser="
                + "&appointmentNo=456");
        assertThat(redirect).doesNotContain("evil", "Location", "parentAjaxId", "script");
    }

    @Test
    @DisplayName("should propagate writeLocalFile validation failures")
    void shouldPropagateWriteLocalFileValidationFailures() throws IOException {
        String previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        try (ByteArrayInputStream input = new ByteArrayInputStream(new byte[]{1})) {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

            assertThatThrownBy(() -> AddEditDocument2Action.writeLocalFile(
                    input, ".hidden"))
                    .isInstanceOf(FileValidationException.class);
        } finally {
            if (previousDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
            }
        }
    }

    private UploadedFile uploadedFile(String inputName, File content, String originalName) {
        return new TestUploadedFile(inputName, content, originalName);
    }

    private UploadedFile uploadedInput(String inputName) {
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn(inputName);
        return uploadedFile;
    }

    private static final class TestUploadedFile implements UploadedFile {
        private static final long serialVersionUID = 1L;

        private final String inputName;
        private final File content;
        private final String originalName;

        private TestUploadedFile(String inputName, File content, String originalName) {
            this.inputName = inputName;
            this.content = content;
            this.originalName = originalName;
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
            return PDF_CONTENT_TYPE;
        }

        @Override
        public String getInputName() {
            return inputName;
        }
    }
}
