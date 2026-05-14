/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @TempDir
    private Path incomingDocumentDir;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private DocumentUpload2Action action;
    private File tempUploadFile;
    private String previousIncomingDocumentDir;

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
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(true);

        action = new DocumentUpload2Action();
        tempUploadFile = File.createTempFile("document-upload", ".pdf");
        Files.write(tempUploadFile.toPath(), new byte[]{1});

        previousIncomingDocumentDir = CarlosProperties.getInstance().getProperty("INCOMINGDOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("INCOMINGDOCUMENT_DIR", incomingDocumentDir.toString());
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
    void shouldAcceptIncomingPdfFilenameWithRepeatedDots() throws Exception {
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
        assertThat(response.getContentAsString()).contains(tempUploadFile.getName());
        assertThat(Files.readAllBytes(writtenFile)).containsExactly(1);
    }
}
