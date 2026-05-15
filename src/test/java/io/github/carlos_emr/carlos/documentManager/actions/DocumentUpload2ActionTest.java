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
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
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
        action.setFiledata(tempUploadFile);
        action.setFiledataFileName("scan.pdf");
        action.setFiledataContentType("application/pdf");

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
        action.setFiledata(tempUploadFile);
        action.setFiledataFileName("my..file.pdf");
        action.setFiledataContentType("application/pdf");

        String result = action.executeUpload();

        Path writtenFile = incomingDocumentDir.resolve("123").resolve("Fax").resolve("my.file.pdf");
        assertThat(result).isNull();
        assertThat(request.getSession().getAttribute("preferredQueue")).isEqualTo("123");
        assertThat(response.getContentAsString()).contains("my.file.pdf");
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
        action.setFiledata(tempUploadFile);
        action.setFiledataFileName("scan.pdf");
        action.setFiledataContentType("application/pdf");

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
        action.setFiledata(tempUploadFile);
        action.setFiledataFileName("failed upload.txt");
        action.setFiledataContentType("text/plain");

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
}
