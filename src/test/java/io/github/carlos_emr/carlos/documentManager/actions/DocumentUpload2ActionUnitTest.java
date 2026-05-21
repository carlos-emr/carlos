/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.IncomingDocUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@DisplayName("DocumentUpload2Action")
class DocumentUpload2ActionUnitTest extends CarlosUnitTestBase {

    @TempDir
    Path incomingDocumentDir;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private String previousIncomingDocumentDir;
    private String previousAllowedFolders;

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(new MockHttpServletRequest());
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        CarlosProperties properties = CarlosProperties.getInstance();
        previousIncomingDocumentDir = properties.getProperty("INCOMINGDOCUMENT_DIR");
        previousAllowedFolders = properties.getProperty(IncomingDocUtil.ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY);
        properties.setProperty("INCOMINGDOCUMENT_DIR", incomingDocumentDir.toString());
        properties.remove(IncomingDocUtil.ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        restoreProperty("INCOMINGDOCUMENT_DIR", previousIncomingDocumentDir);
        restoreProperty(IncomingDocUtil.ALLOWED_INCOMING_DOC_FOLDERS_PROPERTY, previousAllowedFolders);
    }

    @Test
    @DisplayName("should suffix incoming document filename when destination exists")
    void shouldSuffixIncomingDocumentFilename_whenDestinationExists() throws Exception {
        Path mailDir = incomingDocumentDir.resolve("1").resolve("Mail");
        Files.createDirectories(mailDir);
        Files.writeString(mailDir.resolve("scan.pdf"), "existing", StandardCharsets.UTF_8);
        Path uploadFile = Files.createTempFile("incoming-upload-", ".pdf");
        Files.writeString(uploadFile, "new upload", StandardCharsets.UTF_8);

        DocumentUpload2Action action = new DocumentUpload2Action();
        Object validatedUpload = validatedUpload(uploadFile);

        String storedFileName = writeToIncomingDocs(action, validatedUpload, "1", "Mail", "scan.pdf");

        assertThat(storedFileName).isEqualTo("scan_1.pdf");
        assertThat(Files.readString(mailDir.resolve("scan.pdf"), StandardCharsets.UTF_8)).isEqualTo("existing");
        assertThat(Files.readString(mailDir.resolve("scan_1.pdf"), StandardCharsets.UTF_8)).isEqualTo("new upload");
    }

    private Object validatedUpload(Path uploadFile) throws Exception {
        Class<?> validatedUploadClass = Class.forName(DocumentUpload2Action.class.getName() + "$ValidatedUpload");
        Constructor<?> constructor = validatedUploadClass.getDeclaredConstructor(UploadedFile.class, File.class);
        constructor.setAccessible(true);
        return constructor.newInstance(mock(UploadedFile.class), uploadFile.toFile());
    }

    private String writeToIncomingDocs(
            DocumentUpload2Action action,
            Object validatedUpload,
            String queueId,
            String pdfDir,
            String fileName) throws Exception {
        Method method = DocumentUpload2Action.class.getDeclaredMethod(
                "writeToIncomingDocs",
                validatedUpload.getClass(),
                String.class,
                String.class,
                String.class);
        method.setAccessible(true);
        return (String) method.invoke(action, validatedUpload, queueId, pdfDir, fileName);
    }

    private void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            CarlosProperties.getInstance().remove(key);
            return;
        }
        CarlosProperties.getInstance().setProperty(key, previousValue);
    }
}
