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
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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
class AddEditDocument2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private ProgramManager2 mockProgramManager;

    @Mock
    private ProgramManager mockLegacyProgramManager;

    @Mock
    private CaseManagementNoteLinkDAO mockCaseManagementNoteLinkDao;

    @Mock
    private CaseManagementNoteDAO mockCaseManagementNoteDao;

    @Mock
    private TicklerLinkDao mockTicklerLinkDao;

    @Mock
    private TicklerManager mockTicklerManager;

    @Mock
    private ProviderDao mockProviderDao;

    @Mock
    private CtlDocTypeDao mockCtlDocTypeDao;

    @Mock
    private DemographicManager mockDemographicManager;

    @Mock
    private CtlDocumentDao mockCtlDocumentDao;

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
        registerMock(ProgramManager2.class, mockProgramManager);
        registerMock(ProgramManager.class, mockLegacyProgramManager);
        registerMock(CaseManagementNoteLinkDAO.class, mockCaseManagementNoteLinkDao);
        registerMock(CaseManagementNoteDAO.class, mockCaseManagementNoteDao);
        registerMock(TicklerLinkDao.class, mockTicklerLinkDao);
        registerMock(TicklerManager.class, mockTicklerManager);
        registerMock(ProviderDao.class, mockProviderDao);
        registerMock(CtlDocTypeDao.class, mockCtlDocTypeDao);
        registerMock(DemographicManager.class, mockDemographicManager);
        registerMock(CtlDocumentDao.class, mockCtlDocumentDao);
        lenient().when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(true);
        lenient().when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

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
    void shouldCaptureMetadata_whenStruts7DocFileUploaded() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getContent()).thenReturn(tempUploadFile);
        when(uploadedFile.getOriginalName()).thenReturn("echart-upload.pdf");
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo("echartupload.pdf");
        assertThat(action.getDocFileContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should capture Struts 7 filedata upload metadata when docFile is absent")
    void shouldCaptureStruts7FiledataUploadMetadata_whenDocFileAbsent() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("filedata");
        when(uploadedFile.getContent()).thenReturn(tempUploadFile);
        when(uploadedFile.getOriginalName()).thenReturn("html5-upload.pdf");
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo("html5upload.pdf");
        assertThat(action.getDocFileContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should fall back to temp filename when Struts 7 original name is null")
    void shouldFallBackToTempFilename_whenStruts7OriginalNameIsNull() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getContent()).thenReturn(tempUploadFile);
        when(uploadedFile.getOriginalName()).thenReturn(null);
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo(MiscUtils.sanitizeFileName(tempUploadFile.getName()));
        assertThat(action.getDocFileContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should preserve PDF extension when original name is null")
    void shouldPreservePdfExtension_whenOriginalNameIsNull() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".tmp");
        Files.writeString(tempUploadFile.toPath(), "%PDF- test");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getContent()).thenReturn(tempUploadFile);
        when(uploadedFile.getOriginalName()).thenReturn(null);
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFileFileName()).isEqualTo(expectedPdfFallbackName(tempUploadFile));
        assertThat(action.getDocFileContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should not trust upload content type for PDF fallback extension")
    void shouldNotPreservePdfExtension_whenOnlyContentTypeClaimsPdf() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".tmp");
        Files.writeString(tempUploadFile.toPath(), "not a pdf");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getContent()).thenReturn(tempUploadFile);
        when(uploadedFile.getOriginalName()).thenReturn(null);
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFileFileName()).isEqualTo(MiscUtils.sanitizeFileName(tempUploadFile.getName()));
        assertThat(action.getDocFileFileName()).doesNotEndWith(".pdf");
        assertThat(action.getDocFileContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should fall back to temp filename when Struts 7 original name is only a path")
    void shouldFallBackToTempFilename_whenStruts7OriginalNameIsOnlyAPath() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getContent()).thenReturn(tempUploadFile);
        when(uploadedFile.getOriginalName()).thenReturn("/");
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo(MiscUtils.sanitizeFileName(tempUploadFile.getName()));
        assertThat(action.getDocFileContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("should preserve sniffed PDF extension when original name is only a path")
    void shouldPreserveSniffedPdfExtension_whenOriginalNameIsOnlyAPath() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".tmp");
        Files.writeString(tempUploadFile.toPath(), "%PDF- test");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getContent()).thenReturn(tempUploadFile);
        when(uploadedFile.getOriginalName()).thenReturn("/");
        when(uploadedFile.getContentType()).thenReturn("application/octet-stream");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFileFileName()).isEqualTo(expectedPdfFallbackName(tempUploadFile));
        assertThat(action.getDocFileContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("should normalize Struts 7 original names containing path traversal")
    void shouldNormalizeFilename_whenOriginalNameContainsPathTraversal() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getContent()).thenReturn(tempUploadFile);
        when(uploadedFile.getOriginalName()).thenReturn("../patient.pdf");
        when(uploadedFile.getContentType()).thenReturn("application/pdf");

        action.withUploadedFiles(List.of(uploadedFile));

        assertThat(action.getDocFile()).isNotNull();
        assertThat(action.getDocFileFileName()).isEqualTo("patient.pdf");
    }

    @Test
    @DisplayName("should reject Struts 7 uploads when content is not file backed")
    void shouldRejectUpload_whenContentIsNotFileBacked() {
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getContent()).thenReturn("not-a-file");
        List<UploadedFile> uploads = List.of(uploadedFile);

        assertThatThrownBy(() -> action.withUploadedFiles(uploads))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("file-backed");
    }

    @Test
    @DisplayName("should reject Struts 7 uploads outside allowed temp directories")
    void shouldRejectUpload_whenSourceIsOutsideAllowedTempDirectory() throws Exception {
        Path outsideUploadDir = createTempDirectoryOutsideAllowedTemp();
        org.junit.jupiter.api.Assumptions.assumeTrue(outsideUploadDir != null,
                "Unable to create an upload directory outside allowed temp directories");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(outsideUploadDir) && Files.isWritable(outsideUploadDir),
                "Outside upload directory is not usable in this environment");
        Path outsideUpload = outsideUploadDir.resolve("document.pdf");
        Files.writeString(outsideUpload, "test");
        try {
            UploadedFile uploadedFile = mock(UploadedFile.class);
            when(uploadedFile.getInputName()).thenReturn("docFile");
            when(uploadedFile.getContent()).thenReturn(outsideUpload.toFile());
            List<UploadedFile> uploads = List.of(uploadedFile);

            assertThatThrownBy(() -> action.withUploadedFiles(uploads))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Invalid upload source");
        } finally {
            Files.deleteIfExists(outsideUpload);
            Files.deleteIfExists(outsideUploadDir);
        }
    }

    @Test
    @DisplayName("should prefer docFile over filedata regardless of list ordering")
    void shouldPreferDocFileOverFiledata_regardlessOfListOrdering() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");

        UploadedFile filedataUpload = mock(UploadedFile.class);
        when(filedataUpload.getInputName()).thenReturn("filedata");

        UploadedFile docFileUpload = mock(UploadedFile.class);
        when(docFileUpload.getInputName()).thenReturn("docFile");
        when(docFileUpload.getContent()).thenReturn(tempUploadFile);
        when(docFileUpload.getOriginalName()).thenReturn("echart-upload.pdf");
        when(docFileUpload.getContentType()).thenReturn("application/pdf");

        // filedata appears first in the list - docFile must still win
        action.withUploadedFiles(List.of(filedataUpload, docFileUpload));

        assertThat(action.getDocFile().getAbsolutePath()).isEqualTo(tempUploadFile.getAbsolutePath());
        assertThat(action.getDocFileFileName()).isEqualTo("echartupload.pdf");
    }

    @Test
    @DisplayName("should return write error when html5 upload file write fails")
    void shouldReturnWriteError_whenHtml5UploadFileWriteFails() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");
        Files.writeString(tempUploadFile.toPath(), "test");

        bindDocFileUpload(tempUploadFile, "echart-upload.pdf");
        action.setAppointmentNo("123");

        try (MockedStatic<AddEditDocument2Action> addEditDocumentActionMock = mockStatic(AddEditDocument2Action.class, CALLS_REAL_METHODS)) {
            addEditDocumentActionMock.when(() -> AddEditDocument2Action.writeLocalFile(
                    any(InputStream.class),
                    argThat(fileName -> isGeneratedStoredName(fileName, "echartupload.pdf")),
                    eq(false)))
                    .thenReturn(null);

            String result = action.html5MultiUpload();

            addEditDocumentActionMock.verify(() -> AddEditDocument2Action.writeLocalFile(
                    any(InputStream.class),
                    argThat(fileName -> isGeneratedStoredName(fileName, "echartupload.pdf")),
                    eq(false)));
            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getHeader("oscar_error")).isEqualTo(ResourceBundle.getBundle("oscarResources")
                    .getString("dms.addDocument.errorNoWrite"));
        }
    }

    @Test
    @DisplayName("should return bad request when html5 upload is missing")
    void shouldReturnBadRequest_whenHtml5UploadMissing() throws Exception {
        String result = action.html5MultiUpload();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getHeader("oscar_error")).isEqualTo(ResourceBundle.getBundle("oscarResources")
                .getString("dms.addDocument.errorZeroSize"));
    }

    @Test
    @DisplayName("should return bad request when html5 upload is empty")
    void shouldReturnBadRequest_whenHtml5UploadIsEmpty() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".txt");

        bindDocFileUpload(tempUploadFile, "echart-upload.txt", "text/plain");
        action.setAppointmentNo("123");

        String result = action.html5MultiUpload();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getHeader("oscar_error")).isEqualTo(ResourceBundle.getBundle("oscarResources")
                .getString("dms.addDocument.errorZeroSize"));
    }

    @Test
    @DisplayName("should delete partial file when html5 upload write is incomplete")
    void shouldDeletePartialFile_whenHtml5UploadWriteIsIncomplete() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");
        Files.writeString(tempUploadFile.toPath(), "complete-content");
        Path documentDir = Files.createTempDirectory("add-edit-document-output");
        File partialFile = documentDir.resolve("echartupload.pdf").toFile();
        Files.writeString(partialFile.toPath(), "partial");
        String originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

        bindDocFileUpload(tempUploadFile, "echart-upload.pdf");
        action.setAppointmentNo("123");

        try (MockedStatic<AddEditDocument2Action> addEditDocumentActionMock = mockStatic(AddEditDocument2Action.class, CALLS_REAL_METHODS)) {
            addEditDocumentActionMock.when(() -> AddEditDocument2Action.writeLocalFile(
                    any(InputStream.class),
                    argThat(fileName -> isGeneratedStoredName(fileName, "echartupload.pdf")),
                    eq(false)))
                    .thenReturn(partialFile);

            String result = action.html5MultiUpload();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(partialFile).doesNotExist();
        } finally {
            if (originalDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
            }
            Files.deleteIfExists(partialFile.toPath());
            Files.deleteIfExists(documentDir);
        }
    }

    @Test
    @DisplayName("should write html5 upload using stored document filename")
    void shouldWriteHtml5Upload_usingStoredDocumentFilename() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".txt");
        Files.writeString(tempUploadFile.toPath(), "complete-content");
        Path documentDir = Files.createTempDirectory("add-edit-document-output");
        String originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

        bindDocFileUpload(tempUploadFile, "echart-upload.txt", "text/plain");
        action.setAppointmentNo("123");
        AtomicReference<EDoc> savedDocument = new AtomicReference<>();

        try (MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class, CALLS_REAL_METHODS)) {
            eDocUtilMock.when(() -> EDocUtil.addDocumentSQL(any(EDoc.class))).thenAnswer(invocation -> {
                savedDocument.set(invocation.getArgument(0));
                return "321";
            });

            String result = action.html5MultiUpload();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(savedDocument.get()).isNotNull();
            try (var files = Files.list(documentDir)) {
                List<String> writtenFileNames = files.map(path -> path.getFileName().toString()).toList();
                assertThat(writtenFileNames).singleElement()
                        .satisfies(writtenFileName -> {
                            assertThat(writtenFileName).endsWith("echartupload.txt");
                            assertThat(writtenFileName).isNotEqualTo("echartupload.txt");
                            assertThat(savedDocument.get().getFileName()).isEqualTo(writtenFileName);
                        });
            }
            assertThat(documentDir.resolve("echartupload.txt")).doesNotExist();
        } finally {
            if (originalDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
            }
            try (var files = Files.list(documentDir)) {
                for (Path writtenFile : files.toList()) {
                    Files.deleteIfExists(writtenFile);
                }
            }
            Files.deleteIfExists(documentDir);
        }
    }

    @Test
    @DisplayName("should return write error when html5 upload disappears before execution")
    void shouldReturnWriteError_whenHtml5UploadDisappearsBeforeExecution() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".txt");
        Files.writeString(tempUploadFile.toPath(), "test");
        bindDocFileUpload(tempUploadFile, "echart-upload.txt", "text/plain");
        action.setAppointmentNo("123");

        Files.deleteIfExists(tempUploadFile.toPath());

        String result = action.html5MultiUpload();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader("oscar_error")).isEqualTo(ResourceBundle.getBundle("oscarResources")
                .getString("dms.addDocument.errorNoWrite"));
    }

    @Test
    @DisplayName("should return write error when html5 upload disappears after validation")
    void shouldReturnWriteError_whenHtml5UploadDisappearsAfterValidation() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".txt");
        Files.writeString(tempUploadFile.toPath(), "test");
        bindDocFileUpload(tempUploadFile, "echart-upload.txt");
        action.setAppointmentNo("123");

        Files.deleteIfExists(tempUploadFile.toPath());

        try (MockedStatic<PathValidationUtils> pathValidationUtilsMock = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            pathValidationUtilsMock.when(() -> PathValidationUtils.validateUpload(any(File.class)))
                    .thenReturn(tempUploadFile);

            String result = action.html5MultiUpload();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getHeader("oscar_error")).isEqualTo(ResourceBundle.getBundle("oscarResources")
                    .getString("dms.addDocument.errorNoWrite"));
        }
    }

    @Test
    @DisplayName("should preserve existing document when upload write fails")
    void shouldPreserveExistingDocument_whenUploadWriteFails() throws Exception {
        Path documentDir = Files.createTempDirectory("add-edit-document-output");
        Path existingDocument = documentDir.resolve("existing.pdf");
        Files.writeString(existingDocument, "original");
        String originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

        try (InputStream failingInputStream = new FailingUploadInputStream()) {
            assertThatThrownBy(() -> AddEditDocument2Action.writeLocalFile(failingInputStream, "existing.pdf"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("simulated write failure");

            assertThat(Files.readString(existingDocument)).isEqualTo("original");
            try (var files = Files.list(documentDir)) {
                assertThat(files.map(path -> path.getFileName().toString()).toList())
                        .containsExactly("existing.pdf");
            }
        } finally {
            if (originalDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
            }
            Files.deleteIfExists(existingDocument);
            Files.deleteIfExists(documentDir);
        }
    }

    @Test
    @DisplayName("should reject existing document when new upload target already exists")
    void shouldRejectExistingDocument_whenNewUploadTargetAlreadyExists() throws Exception {
        Path documentDir = Files.createTempDirectory("add-edit-document-output");
        Path existingDocument = documentDir.resolve("existing.pdf");
        Files.writeString(existingDocument, "original");
        String originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

        try (InputStream replacementInputStream = new ByteArrayInputStream("replacement".getBytes(StandardCharsets.UTF_8))) {
            assertThatThrownBy(() -> AddEditDocument2Action.writeLocalFile(replacementInputStream, "existing.pdf", false))
                    .isInstanceOf(FileAlreadyExistsException.class);

            assertThat(Files.readString(existingDocument)).isEqualTo("original");
            try (var files = Files.list(documentDir)) {
                assertThat(files.map(path -> path.getFileName().toString()).toList())
                        .containsExactly("existing.pdf");
            }
        } finally {
            if (originalDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
            }
            Files.deleteIfExists(existingDocument);
            Files.deleteIfExists(documentDir);
        }
    }

    @Test
    @DisplayName("should not count PDF pages outside document directory")
    void shouldNotCountPdfPages_whenFilenameTraversesOutsideDocumentDirectory() throws Exception {
        Path parentDir = Files.createTempDirectory("add-edit-document-pages");
        Path documentDir = Files.createDirectories(parentDir.resolve("docs"));
        Path insidePdf = documentDir.resolve("inside.pdf");
        Path outsidePdf = parentDir.resolve("outside.pdf");
        writeSimplePdf(insidePdf);
        writeSimplePdf(outsidePdf);
        String originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

        try {
            assertThat(AddEditDocument2Action.countNumOfPages("inside.pdf")).isEqualTo(1);
            assertThat(AddEditDocument2Action.countNumOfPages("../outside.pdf")).isZero();
        } finally {
            if (originalDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
            }
            Files.deleteIfExists(insidePdf);
            Files.deleteIfExists(outsidePdf);
            Files.deleteIfExists(documentDir);
            Files.deleteIfExists(parentDir);
        }
    }

    @Test
    @DisplayName("should encode add document redirect query parameters")
    void shouldEncodeRedirectQueryParameters_whenAddDocumentSucceeds() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".txt");
        Files.writeString(tempUploadFile.toPath(), "test");
        Path documentDir = Files.createTempDirectory("add-edit-document-output");
        String originalDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());

        action.setMode("add");
        action.setFunction("provider");
        action.setFunctionId("123");
        action.setDocDesc("Consult note");
        action.setDocType("Consultant Report");
        action.setDocCreator("999998");
        action.setResponsibleId("999998");
        action.setSource("local");
        action.setDocPublic("0");
        action.setObservationDate("2026-05-21");
        action.setAppointmentNo("45");
        action.setCurUser("user+name");
        action.setParentAjaxId("parent&updateParent=false");
        bindDocFileUpload(tempUploadFile, "consult-note.txt", "text/plain");

        request.addParameter("function", "provider&next=bad");
        request.addParameter("functionid", "123 456");
        request.addParameter("curUser", "request-user+name");
        request.addParameter("appointmentNo", "45&bad=true");
        request.addParameter("parentAjaxId", "request-parent&updateParent=false");

        try (MockedStatic<EDocUtil> eDocUtilMock = mockStatic(EDocUtil.class, CALLS_REAL_METHODS)) {
            eDocUtilMock.when(() -> EDocUtil.getDoctypes("provider"))
                    .thenReturn(new ArrayList<>(List.of("Consultant Report")));
            eDocUtilMock.when(() -> EDocUtil.addDocumentSQL(any(EDoc.class))).thenReturn("321");

            String result = action.execute2();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getRedirectedUrl())
                    .contains("/documentManager/ViewDocumentReport?docerrors=docerrors")
                    .contains("&function=provider")
                    .contains("&functionid=123")
                    .contains("&appointmentNo=45")
                    .contains("&parentAjaxId=parent%26updateParent%3Dfalse")
                    .contains("&updateParent=true")
                    .doesNotContain("&curUser=")
                    .doesNotContain("provider%26next%3Dbad")
                    .doesNotContain("123%20456")
                    .doesNotContain("45%26bad%3Dtrue")
                    .doesNotContain("request-parent");
        } finally {
            if (originalDocumentDir == null) {
                CarlosProperties.getInstance().remove("DOCUMENT_DIR");
            } else {
                CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", originalDocumentDir);
            }
            File[] writtenFiles = documentDir.toFile().listFiles();
            if (writtenFiles != null) {
                for (File writtenFile : writtenFiles) {
                    Files.deleteIfExists(writtenFile.toPath());
                }
            }
            Files.deleteIfExists(documentDir);
        }
    }

    @Test
    @DisplayName("should not expose direct upload file setters as Struts parameters")
    void shouldNotExposeDirectUploadFileSetters_asStrutsParameters() throws Exception {
        assertThatThrownBy(() -> AddEditDocument2Action.class.getMethod("setDocFile", File.class))
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> AddEditDocument2Action.class.getMethod("setFiledata", File.class))
                .isInstanceOf(NoSuchMethodException.class);
        assertThat(AddEditDocument2Action.class.getMethod("setDocFileFileName", String.class)
                .isAnnotationPresent(StrutsParameter.class)).isFalse();
        assertThat(AddEditDocument2Action.class.getMethod("setDocFileContentType", String.class)
                .isAnnotationPresent(StrutsParameter.class)).isFalse();
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
    @DisplayName("should return failEdit when replacement upload is empty")
    @SuppressWarnings("unchecked")
    void shouldReturnFailEdit_whenEditReplacementIsEmpty() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".txt");
        String originalAllowUpdate = CarlosProperties.getInstance().getProperty("ALLOW_UPDATE_DOCUMENT_CONTENT");
        CarlosProperties.getInstance().setProperty("ALLOW_UPDATE_DOCUMENT_CONTENT", "true");

        action.setMode("123");
        action.setFunction("demographic");
        action.setFunctionId("456");
        action.setDocDesc("Consult note");
        action.setDocType("Consultant Report");
        action.setDocCreator("999998");
        action.setResponsibleId("999998");
        action.setSource("local");
        action.setDocPublic("0");
        action.setObservationDate("2026-05-25");
        action.setAppointmentNo("0");
        bindDocFileUpload(tempUploadFile, "empty-replacement.txt", "text/plain");

        try {
            String result = action.execute2();

            assertThat(result).isEqualTo("failEdit");
            assertThat(request.getAttribute("editDocumentNo")).isEqualTo("123");
            Hashtable<String, String> errors = (Hashtable<String, String>) request.getAttribute("docerrors");
            assertThat(errors).containsEntry("uploaderror", "dms.error.uploadError");
        } finally {
            if (originalAllowUpdate == null) {
                CarlosProperties.getInstance().remove("ALLOW_UPDATE_DOCUMENT_CONTENT");
            } else {
                CarlosProperties.getInstance().setProperty("ALLOW_UPDATE_DOCUMENT_CONTENT", originalAllowUpdate);
            }
        }
    }

    @Test
    @DisplayName("should return failAdd with filenameinvalid error when uploaded filename is invalid")
    @SuppressWarnings("unchecked")
    void shouldReturnFailAdd_whenUploadedFilenameIsInvalid() throws Exception {
        tempUploadFile = File.createTempFile("add-edit-document", ".pdf");
        Files.writeString(tempUploadFile.toPath(), "pdf");

        // withUploadedFiles must not throw — invalid filenames are a user error, not a
        // security violation, so the error is deferred to execute time for a friendly response.
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getContent()).thenReturn(tempUploadFile);
        when(uploadedFile.getOriginalName()).thenReturn(".env");
        action.withUploadedFiles(List.of(uploadedFile));
        assertThat(action.getDocFile()).isNull();

        action.setMode("add");
        action.setFunction("demographic");
        action.setFunctionId("123");
        action.setDocDesc("Consult note");
        action.setDocType("Consultant Report");

        String result = action.execute2();

        assertThat(result).isEqualTo("failAdd");
        Hashtable<String, String> errors = (Hashtable<String, String>) request.getAttribute("docerrors");
        assertThat(errors).containsEntry("filenameinvalid", "dms.error.invalidFilename");
    }

    @Test
    @DisplayName("should return failAdd with filenameinvalid error when uploaded filename has blocked extension")
    @SuppressWarnings("unchecked")
    void shouldReturnFailAdd_whenUploadedFilenameHasBlockedExtension() throws Exception {
        for (String blockedName : List.of("shell.jsp", "report.pdf.jsp")) {
            tempUploadFile = File.createTempFile("add-edit-document", ".pdf");
            Files.writeString(tempUploadFile.toPath(), "pdf");

            bindDocFileUpload(tempUploadFile, blockedName);
            assertThat(action.getDocFile()).isNull();

            action.setMode("add");
            action.setFunction("demographic");
            action.setFunctionId("123");
            action.setDocDesc("Consult note");
            action.setDocType("Consultant Report");

            String result = action.execute2();

            assertThat(result).isEqualTo("failAdd");
            Hashtable<String, String> errors = (Hashtable<String, String>) request.getAttribute("docerrors");
            assertThat(errors).containsEntry("filenameinvalid", "dms.error.invalidFilename");

            Files.deleteIfExists(tempUploadFile.toPath());
            tempUploadFile = null;
            action = new AddEditDocument2Action();
        }
    }

    /**
     * Binds a temporary file through the same {@link AddEditDocument2Action#withUploadedFiles(List)}
     * path used by Struts 7 so tests do not reintroduce direct {@code File} upload setters.
     *
     * @param uploadFile File the temp upload content to expose through the mocked upload
     * @param originalName String the client filename to expose through the mocked upload
     */
    private void bindDocFileUpload(File uploadFile, String originalName) {
        bindDocFileUpload(uploadFile, originalName, "application/pdf");
    }

    private void bindDocFileUpload(File uploadFile, String originalName, String contentType) {
        UploadedFile uploadedFile = mock(UploadedFile.class);
        when(uploadedFile.getInputName()).thenReturn("docFile");
        when(uploadedFile.getContent()).thenReturn(uploadFile);
        when(uploadedFile.getOriginalName()).thenReturn(originalName);
        lenient().when(uploadedFile.getContentType()).thenReturn(contentType);

        action.withUploadedFiles(List.of(uploadedFile));
    }

    private Path createTempDirectoryOutsideAllowedTemp() throws IOException {
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

    private void writeSimplePdf(Path path) throws Exception {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            Document document = new Document();
            PdfWriter.getInstance(document, outputStream);
            document.open();
            document.add(new Paragraph("test"));
            document.close();
        }
    }

    private String expectedPdfFallbackName(File uploadFile) {
        String sanitizedTempName = MiscUtils.sanitizeFileName(uploadFile.getName());
        return FilenameUtils.removeExtension(sanitizedTempName) + ".pdf";
    }

    private boolean isGeneratedStoredName(String fileName, String sanitizedBaseName) {
        return fileName != null && fileName.endsWith(sanitizedBaseName) && !fileName.equals(sanitizedBaseName);
    }

    private static class FailingUploadInputStream extends InputStream {
        private boolean deliveredByte;

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (!deliveredByte) {
                buffer[offset] = 'x';
                deliveredByte = true;
                return 1;
            }
            throw new IOException("simulated write failure");
        }

        @Override
        public int read() throws IOException {
            if (!deliveredByte) {
                deliveredByte = true;
                return 'x';
            }
            throw new IOException("simulated write failure");
        }
    }
}
