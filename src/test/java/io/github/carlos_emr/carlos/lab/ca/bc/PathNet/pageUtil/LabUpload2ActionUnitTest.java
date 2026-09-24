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
import io.github.carlos_emr.carlos.lab.ca.bc.PathNet.HL7.Message;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.unit.RecordingTransactionManager;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private RecordingTransactionManager transactions;

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
        transactions = new RecordingTransactionManager();
        registerMock(PlatformTransactionManager.class, transactions);
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
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             MockedConstruction<Connection> connections = mockConstruction(Connection.class, (connection, context) ->
                     when(connection.Retrieve(any(InputStream.class))).thenAnswer(invocation -> {
                         InputStream parserStream = invocation.getArgument(0);
                         assertThat(parserStream.readAllBytes()).isEqualTo("MSH|fixture PathNet content".getBytes(StandardCharsets.UTF_8));
                         // Messages are stored in the transaction holding the upload's checksum row.
                         assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                         return new ArrayList<>(List.of("MSH|stored message"));
                     }));
             MockedConstruction<Message> messages = mockConstruction(Message.class)) {
            paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile()))
                    .thenReturn(uploaded.toFile());
            List<TrackedStream> opened = trackOpenedStreams(paths, uploaded);
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class)))
                    .thenAnswer(invocation -> {
                        InputStream hashStream = invocation.getArgument(0);
                        assertThat(hashStream.readAllBytes()).isEqualTo("MSH|fixture PathNet content".getBytes(StandardCharsets.UTF_8));
                        // Rewrite the temp upload after hashing: parse and archive must still see the hashed bytes.
                        Files.writeString(uploaded, "MSH|replaced after duplicate check");
                        return false;
                    });
            duplicateCheck.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenAnswer(invocation -> {
                        InputStream recordStream = invocation.getArgument(1);
                        assertThat(recordStream.readAllBytes()).isEqualTo("MSH|fixture PathNet content".getBytes(StandardCharsets.UTF_8));
                        return 1;
                    });

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("outcome")).isEqualTo("success");
            assertThat(transactions.commits).isEqualTo(1);
            assertThat(connections.constructed()).hasSize(1);
            verify(messages.constructed().get(0)).Parse("MSH|stored message");
            verify(messages.constructed().get(0)).ToDatabase();
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
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             MockedConstruction<Connection> connections = mockConstruction(Connection.class)) {
            paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile()))
                    .thenReturn(uploaded.toFile());
            List<TrackedStream> opened = trackOpenedStreams(paths, uploaded);
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(true);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("outcome")).isEqualTo("uploadedPreviously");
            assertThat(connections.constructed()).isEmpty();
            assertThat(transactions.begun).isZero();
            try (var children = Files.list(documentDir)) {
                assertThat(children.toList()).isEmpty();
            }
            assertThat(opened).hasSize(1).allMatch(TrackedStream::isClosed);
        }
    }

    @Test
    void shouldRollBackChecksumAndReportException_whenMessageCannotBeStored() throws Exception {
        Path uploaded = Files.writeString(root.resolve("broken.hl7"), "MSH|broken PathNet content");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             MockedConstruction<Connection> connections = mockConstruction(Connection.class, (connection, context) ->
                     when(connection.Retrieve(any(InputStream.class))).thenReturn(new ArrayList<>(List.of("MSH|1", "MSH|2"))));
             MockedConstruction<Message> messages = mockConstruction(Message.class, (message, context) -> {
                 if (context.getCount() == 2) {
                     doThrow(new java.sql.SQLException("result insert failed")).when(message).ToDatabase();
                 }
             })) {
            paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile())).thenReturn(uploaded.toFile());
            trackOpenedStreams(paths, uploaded);
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            duplicateCheck.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenReturn(1);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            // The first message's rows and the checksum roll back with the failing second message,
            // so a retry stores the batch instead of being answered uploadedPreviously.
            assertThat(request.getAttribute("outcome")).isEqualTo("exception");
            assertThat(messages.constructed()).hasSize(2);
            assertThat(transactions.rollbacks).isEqualTo(1);
            assertThat(transactions.commits).isZero();
            try (var children = Files.list(documentDir)) {
                assertThat(children.toList()).hasSize(1);
            }
        }
    }

    @Test
    void shouldRollBackChecksumAndReportException_whenUploadHoldsNoMessages() throws Exception {
        Path uploaded = Files.writeString(root.resolve("empty.hl7"), "not a PathNet batch");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             MockedConstruction<Connection> connections = mockConstruction(Connection.class, (connection, context) ->
                     when(connection.Retrieve(any(InputStream.class))).thenReturn(null))) {
            paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile())).thenReturn(uploaded.toFile());
            trackOpenedStreams(paths, uploaded);
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            duplicateCheck.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenReturn(1);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            // Before, an unreadable batch kept its checksum and left the outcome empty.
            assertThat(request.getAttribute("outcome")).isEqualTo("exception");
            assertThat(transactions.rollbacks).isEqualTo(1);
            assertThat(transactions.commits).isZero();
        }
    }

    @Test
    void shouldRollBackChecksumAndReportException_whenBatchDeclaresZeroMessages() throws Exception {
        Path uploaded = Files.writeString(root.resolve("zero.hl7"), "<Batch MessageCount=\"0\"/>");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             // Retrieve answers an empty list, not null, for a well-formed batch declaring no messages.
             MockedConstruction<Connection> connections = mockConstruction(Connection.class, (connection, context) ->
                     when(connection.Retrieve(any(InputStream.class))).thenReturn(new ArrayList<>()));
             MockedConstruction<Message> messages = mockConstruction(Message.class)) {
            paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile())).thenReturn(uploaded.toFile());
            trackOpenedStreams(paths, uploaded);
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            duplicateCheck.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenReturn(1);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            // Committing here would record a checksum for a batch that stored nothing and refuse the retry.
            assertThat(request.getAttribute("outcome")).isEqualTo("exception");
            assertThat(messages.constructed()).isEmpty();
            assertThat(transactions.rollbacks).isEqualTo(1);
            assertThat(transactions.commits).isZero();
        }
    }

    @Test
    void shouldRollBackStoredMessagesAndReportException_whenArchiveCannotBeWritten() throws Exception {
        Path uploaded = Files.writeString(root.resolve("unarchived.hl7"), "MSH|unarchivable PathNet content");
        // A regular file where DOCUMENT_DIR should be: every archive write fails.
        Path notADirectory = Files.writeString(root.resolve("document-store"), "not a directory");
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             MockedConstruction<Connection> connections = mockConstruction(Connection.class, (connection, context) ->
                     when(connection.Retrieve(any(InputStream.class))).thenReturn(new ArrayList<>(List.of("MSH|1"))));
             MockedConstruction<Message> messages = mockConstruction(Message.class)) {
            paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile())).thenReturn(uploaded.toFile());
            trackOpenedStreams(paths, uploaded);
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(notADirectory.toString());
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            duplicateCheck.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenReturn(1);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            // Before, the messages and checksum committed first and the failed archive answered a
            // retryable "exception" that the retry then refused as uploadedPreviously.
            verify(messages.constructed().get(0)).ToDatabase();
            assertThat(request.getAttribute("outcome")).isEqualTo("exception");
            assertThat(transactions.rollbacks).isEqualTo(1);
            assertThat(transactions.commits).isZero();
        }
    }

    @Test
    void shouldKeepSingleArchive_whenCommitOutcomeIsUnknown() throws Exception {
        Path uploaded = Files.writeString(root.resolve("unacknowledged.hl7"), "MSH|unacknowledged PathNet content");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        transactions.failCommit = true;
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             MockedConstruction<Connection> connections = mockConstruction(Connection.class, (connection, context) ->
                     when(connection.Retrieve(any(InputStream.class))).thenReturn(new ArrayList<>(List.of("MSH|1"))));
             MockedConstruction<Message> messages = mockConstruction(Message.class)) {
            paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile())).thenReturn(uploaded.toFile());
            trackOpenedStreams(paths, uploaded);
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            duplicateCheck.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenReturn(1);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            // The batch may have committed with its archive, so the failure path must not archive
            // a second copy of it.
            assertThat(request.getAttribute("outcome")).isEqualTo("exception");
            try (var children = Files.list(documentDir)) {
                assertThat(children.toList()).hasSize(1);
            }
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
