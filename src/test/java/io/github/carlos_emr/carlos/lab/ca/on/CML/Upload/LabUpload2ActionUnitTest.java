// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.lab.ca.on.CML.Upload;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.on.CML.ABCDParser;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Exercises CML upload authorization, key validation, and the real archive writer.
 * @since 2026-09-20
 */
class LabUpload2ActionUnitTest extends CarlosUnitTestBase {
    @TempDir Path root;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo info;
    private SecurityInfoManager security;

    @BeforeEach
    void setUpAction() {
        request = new MockHttpServletRequest();
        request.getSession().setAttribute("user", "999998");
        request.setParameter("key", "fixture-key");
        response = new MockHttpServletResponse();
        info = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(eq(info), eq("_lab"), eq("w"), isNull())).thenReturn(true);
    }

    @Test
    void shouldRejectUpload_whenLabWritePrivilegeMissing() {
        when(security.hasPrivilege(eq(info), eq("_lab"), eq("w"), isNull())).thenReturn(false);
        assertThatThrownBy(() -> execute(null)).isInstanceOf(SecurityException.class)
                .hasMessageContaining("_lab");
    }

    @Test
    void shouldDenyUpload_whenKeyDoesNotMatch() {
        request.setParameter("key", "wrong-key");
        try (MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class)) {
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("CML_UPLOAD_KEY")).thenReturn("fixture-key");

            assertThat(execute(null)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("outcome")).isEqualTo("accessDenied");
        }
    }

    @Test
    void shouldParseSavedFile_whenAuthorizedUploadSucceeds() throws Exception {
        Path uploaded = Files.writeString(root.resolve("source.hl7"), "MSH|fixture CML content");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        Connection database = mock(Connection.class);
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class);
             MockedConstruction<ABCDParser> parsers = mockConstruction(ABCDParser.class, (parser, context) ->
                     doAnswer(invocation -> {
                         BufferedReader reader = invocation.getArgument(0);
                         assertThat(reader.readLine()).isEqualTo("MSH|fixture CML content");
                         return null;
                     }).when(parser).parse(any(BufferedReader.class)))) {
            paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile()))
                    .thenReturn(uploaded.toFile());
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            when(properties.getProperty("CML_UPLOAD_KEY")).thenReturn("fixture-key");
            duplicateCheck.when(() -> FileUploadCheck.addFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenAnswer(invocation -> {
                        InputStream stream = invocation.getArgument(1);
                        assertThat(stream.readAllBytes()).isEqualTo("MSH|fixture CML content".getBytes(StandardCharsets.UTF_8));
                        return 1;
                    });
            jdbc.when(LegacyJdbcQuery::getConnection).thenReturn(database);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("outcome")).isEqualTo("uploaded");
            assertThat(parsers.constructed()).hasSize(1);
            verify(parsers.constructed().get(0)).save(database);
            try (var children = Files.list(documentDir)) {
                var archived = children.toList();
                assertThat(archived).hasSize(1);
                assertThat(archived.get(0).getFileName().toString()).startsWith("LabUpload.source.hl7.");
                assertThat(archived.get(0)).hasBinaryContent("MSH|fixture CML content".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private String execute(Path uploaded) {
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            LabUpload2Action action = new LabUpload2Action();
            if (uploaded != null) action.setImportFile(uploaded.toFile());
            return action.execute();
        }
    }
}
