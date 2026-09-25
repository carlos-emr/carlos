/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
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
package io.github.carlos_emr.carlos.lab.ca.bc.PathNet.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.bc.PathNet.Connection;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
@Tag("unit")
@Tag("lab")
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
    void shouldParseAndArchiveHashedSnapshot_whenDuplicateCheckConsumesInput() throws Exception {
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
            List<TrackedStream> opened = trackOpenedStreams(paths, uploaded);
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.addFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenAnswer(invocation -> {
                        InputStream hashStream = invocation.getArgument(1);
                        assertThat(hashStream.readAllBytes()).isEqualTo("MSH|fixture PathNet content".getBytes(StandardCharsets.UTF_8));
                        // Rewrite the temp upload after hashing: parse and archive must still see the hashed bytes.
                        Files.writeString(uploaded, "MSH|replaced after duplicate check");
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
            // The upload is read once into a snapshot shared by all three readers, and the file stream is closed.
            assertThat(opened).hasSize(1).allMatch(TrackedStream::isClosed);
        }
    }

    @Test
    void shouldSkipParseAndArchive_whenDuplicateCheckRejectsUpload() throws Exception {
        Path uploaded = Files.writeString(root.resolve("duplicate.hl7"), "MSH|duplicate PathNet content");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class);
             MockedConstruction<Connection> connections = mockConstruction(Connection.class)) {
            paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile()))
                    .thenReturn(uploaded.toFile());
            List<TrackedStream> opened = trackOpenedStreams(paths, uploaded);
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.addFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenReturn(FileUploadCheck.UNSUCCESSFUL_SAVE);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("outcome")).isEqualTo("uploadedPreviously");
            assertThat(connections.constructed()).isEmpty();
            try (var children = Files.list(documentDir)) {
                assertThat(children.toList()).isEmpty();
            }
            assertThat(opened).hasSize(1).allMatch(TrackedStream::isClosed);
        }
    }

    /** Serves each validated reopen of {@code uploaded} as a stream whose closure the test can assert. */
    private static List<TrackedStream> trackOpenedStreams(MockedStatic<PathValidationUtils> paths, Path uploaded) {
        List<TrackedStream> opened = new CopyOnWriteArrayList<>();
        paths.when(() -> PathValidationUtils.openValidatedUploadInputStream(uploaded.toFile()))
                .thenAnswer(invocation -> {
                    TrackedStream stream = new TrackedStream(Files.readAllBytes(uploaded));
                    opened.add(stream);
                    return stream;
                });
        return opened;
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

    private static final class TrackedStream extends ByteArrayInputStream {
        private volatile boolean closed;

        private TrackedStream(byte[] content) {
            super(content);
        }

        boolean isClosed() {
            return closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
