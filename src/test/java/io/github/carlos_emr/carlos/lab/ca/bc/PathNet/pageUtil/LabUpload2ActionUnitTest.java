// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.lab.ca.bc.PathNet.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.bc.PathNet.Connection;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

/** Exercises PathNet upload authorization and its independent file-reader lifecycle.
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
    void shouldExplainMissingUpload_whenNoFileProvided() {
        assertThat(execute(null)).isEqualTo(ActionSupport.SUCCESS);
        assertThat(request.getAttribute("outcome")).isEqualTo("exception");
    }

    @Test
    void shouldParseAndArchiveFromFreshStreams_whenDuplicateCheckConsumesInput() throws Exception {
        Path uploaded = Files.writeString(root.resolve("source.hl7"), "MSH|fixture PathNet content");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class);
             MockedConstruction<Connection> connections = mockConstruction(Connection.class, (connection, context) ->
                     when(connection.Retrieve(any(InputStream.class))).thenAnswer(invocation -> {
                         InputStream parserStream = invocation.getArgument(0);
                         assertThat(parserStream.readAllBytes()).isEqualTo("MSH|fixture PathNet content".getBytes(StandardCharsets.UTF_8));
                         return new ArrayList<String>();
                     }))) {
            paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile()))
                    .thenReturn(uploaded.toFile());
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.addFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenAnswer(invocation -> {
                        InputStream hashStream = invocation.getArgument(1);
                        assertThat(hashStream.readAllBytes()).isEqualTo("MSH|fixture PathNet content".getBytes(StandardCharsets.UTF_8));
                        return 1;
                    });

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("outcome")).isEqualTo("success");
            assertThat(connections.constructed()).hasSize(1);
            try (var children = Files.list(documentDir)) {
                var archived = children.toList();
                assertThat(archived).hasSize(1);
                assertThat(archived.get(0).getFileName().toString()).startsWith("LabUpload.source.hl7.");
                assertThat(archived.get(0)).hasBinaryContent("MSH|fixture PathNet content".getBytes(StandardCharsets.UTF_8));
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
