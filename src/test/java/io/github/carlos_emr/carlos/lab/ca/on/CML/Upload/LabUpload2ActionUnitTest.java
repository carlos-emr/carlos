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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        request.setParameter("key", "fixture-key");
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
    void shouldRecordChecksumAndSaveInOneTransaction_whenAuthorizedUploadIsNew() throws Exception {
        Path uploaded = Files.writeString(root.resolve("source.hl7"), "MSH|fixture CML content");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        Connection database = mock(Connection.class);
        AtomicReference<BufferedReader> parserReader = new AtomicReference<>();
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> checksums = mockStatic(FileUploadCheck.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class);
             MockedConstruction<ABCDParser> parsers = mockConstruction(ABCDParser.class, (parser, context) -> {
                 doAnswer(invocation -> {
                     BufferedReader reader = invocation.getArgument(0);
                     parserReader.set(reader);
                     assertThat(reader.readLine()).isEqualTo("MSH|fixture CML content");
                     return null;
                 }).when(parser).parse(any(BufferedReader.class));
                 doAnswer(invocation -> {
                     assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                     return null;
                 }).when(parser).save(any(Connection.class));
             })) {
            stubAuthorizedUpload(paths, configuration, uploaded, documentDir);
            checksums.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            checksums.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenAnswer(invocation -> {
                        // Recorded inside the lab's transaction and under addFile's monitor, so the
                        // checksum exists exactly when the lab does and no other upload sees it early.
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                        assertThat(Thread.holdsLock(FileUploadCheck.class)).isTrue();
                        InputStream stream = invocation.getArgument(1);
                        assertThat(stream.readAllBytes()).isEqualTo("MSH|fixture CML content".getBytes(StandardCharsets.UTF_8));
                        return null;
                    });
            jdbc.when(LegacyJdbcQuery::getConnection).thenReturn(database);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("outcome")).isEqualTo("uploaded");
            assertThat(parsers.constructed()).hasSize(1);
            verify(parsers.constructed().get(0)).save(database);
            checksums.verify(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")));
            checksums.verify(() -> FileUploadCheck.addFile(anyString(), any(InputStream.class), anyString()), never());
            assertThat(transactions.commits).isEqualTo(1);
            assertThat(transactions.rollbacks).isZero();
            // The parser's reader over the archived lab must not leak a file handle.
            assertThatThrownBy(() -> parserReader.get().ready())
                    .isInstanceOf(IOException.class).hasMessageContaining("closed");
            try (var children = Files.list(documentDir)) {
                var archived = children.toList();
                assertThat(archived).hasSize(1);
                assertThat(archived.get(0).getFileName().toString()).startsWith("LabUpload.source.hl7.");
                assertThat(archived.get(0)).hasBinaryContent("MSH|fixture CML content".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void shouldReportUploadedPreviously_whenChecksumAlreadyRecorded() throws Exception {
        Path uploaded = Files.writeString(root.resolve("duplicate.hl7"), "MSH|duplicate CML content");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> checksums = mockStatic(FileUploadCheck.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class);
             MockedConstruction<ABCDParser> parsers = mockConstruction(ABCDParser.class)) {
            stubAuthorizedUpload(paths, configuration, uploaded, documentDir);
            checksums.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class)))
                    .thenAnswer(invocation -> {
                        // Checked under the same monitor, so an in-flight upload is never counted.
                        assertThat(Thread.holdsLock(FileUploadCheck.class)).isTrue();
                        InputStream stream = invocation.getArgument(0);
                        return "MSH|duplicate CML content".equals(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    });

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            // Before the fix a duplicate left the outcome empty, so the XML client saw <outcome/>.
            assertThat(request.getAttribute("outcome")).isEqualTo("uploadedPreviously");
            assertThat(parsers.constructed()).isEmpty();
            assertThat(transactions.begun).isZero();
            checksums.verify(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), anyString()), never());
            jdbc.verifyNoInteractions();
        }
    }

    @Test
    void shouldReportRetryableFailure_whenChecksumLookupFails() throws Exception {
        Path uploaded = Files.writeString(root.resolve("unconfirmed.hl7"), "MSH|unconfirmed CML content");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> checksums = mockStatic(FileUploadCheck.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class);
             MockedConstruction<ABCDParser> parsers = mockConstruction(ABCDParser.class)) {
            stubAuthorizedUpload(paths, configuration, uploaded, documentDir);
            checksums.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class)))
                    .thenThrow(new IllegalStateException("database unavailable"));

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            // Nothing is known about the content, so the client is told to retry, not "exception".
            assertThat(request.getAttribute("outcome")).isEqualTo("databaseNotStarted");
            assertThat(parsers.constructed()).isEmpty();
            assertThat(transactions.begun).isZero();
            jdbc.verifyNoInteractions();
        }
    }

    @Test
    void shouldRecordNothing_whenParsingFails() throws Exception {
        Path uploaded = Files.writeString(root.resolve("malformed.hl7"), "not a CML report");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> checksums = mockStatic(FileUploadCheck.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class);
             MockedConstruction<ABCDParser> parsers = mockConstruction(ABCDParser.class, (parser, context) ->
                     doThrow(new IllegalStateException("unparseable")).when(parser).parse(any(BufferedReader.class)))) {
            stubAuthorizedUpload(paths, configuration, uploaded, documentDir);
            checksums.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            // Parsing only reads the file, so no checksum exists to refuse a corrected retry.
            assertThat(request.getAttribute("outcome")).isEqualTo("exception");
            checksums.verify(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), anyString()), never());
            verify(parsers.constructed().get(0), never()).save(any());
            assertThat(transactions.begun).isZero();
            jdbc.verifyNoInteractions();
        }
    }

    @Test
    void shouldRollBackChecksumWithLab_whenSavingFails() throws Exception {
        Path uploaded = Files.writeString(root.resolve("unsaved.hl7"), "MSH|unsaved CML content");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        Connection database = mock(Connection.class);
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> checksums = mockStatic(FileUploadCheck.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class);
             MockedConstruction<ABCDParser> parsers = mockConstruction(ABCDParser.class, (parser, context) ->
                     doThrow(new IllegalStateException("result insert failed")).when(parser).save(any(Connection.class)))) {
            stubAuthorizedUpload(paths, configuration, uploaded, documentDir);
            checksums.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            checksums.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenAnswer(invocation -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                        return null;
                    });
            jdbc.when(LegacyJdbcQuery::getConnection).thenReturn(database);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            // The checksum was written in the transaction that rolled back, so it went with the
            // partial lab rows and a retry stores the lab; no separate cleanup can fail.
            assertThat(request.getAttribute("outcome")).isEqualTo("exception");
            checksums.verify(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")));
            assertThat(transactions.rollbacks).isEqualTo(1);
            assertThat(transactions.commits).isZero();
            verify(database).close();
        }
    }

    @Test
    void shouldRecordNothing_whenTransactionCannotStart() throws Exception {
        Path uploaded = Files.writeString(root.resolve("no-transaction.hl7"), "MSH|no transaction");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        transactions.failBegin = true;
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> checksums = mockStatic(FileUploadCheck.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class);
             MockedConstruction<ABCDParser> parsers = mockConstruction(ABCDParser.class)) {
            stubAuthorizedUpload(paths, configuration, uploaded, documentDir);
            checksums.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(request.getAttribute("outcome")).isEqualTo("exception");
            checksums.verify(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), anyString()), never());
            verify(parsers.constructed().get(0), never()).save(any());
            jdbc.verifyNoInteractions();
        }
    }

    @Test
    void shouldAttemptNoCleanup_whenCommitOutcomeIsUnknown() throws Exception {
        Path uploaded = Files.writeString(root.resolve("unconfirmed-commit.hl7"), "MSH|unconfirmed commit");
        Path documentDir = Files.createDirectory(root.resolve("document-store"));
        Connection database = mock(Connection.class);
        transactions.failCommit = true;
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> checksums = mockStatic(FileUploadCheck.class);
             MockedStatic<LegacyJdbcQuery> jdbc = mockStatic(LegacyJdbcQuery.class);
             MockedConstruction<ABCDParser> parsers = mockConstruction(ABCDParser.class)) {
            stubAuthorizedUpload(paths, configuration, uploaded, documentDir);
            checksums.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            jdbc.when(LegacyJdbcQuery::getConnection).thenReturn(database);

            assertThat(execute(uploaded)).isEqualTo(ActionSupport.SUCCESS);

            // Checksum and lab rows share the commit, so they stand or fall together; the action
            // touches neither afterwards, and a retry sees a consistent answer either way.
            assertThat(request.getAttribute("outcome")).isEqualTo("exception");
            verify(parsers.constructed().get(0)).save(database);
            checksums.verify(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")));
            checksums.verify(() -> FileUploadCheck.isFileRecorded(any(InputStream.class)));
            checksums.verifyNoMoreInteractions();
        }
    }

    /** Runs Spring's real commit/rollback lifecycle, including synchronizations, with no database. */
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private int begun;
        private int commits;
        private int rollbacks;
        private boolean failCommit;
        private boolean failBegin;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            if (failBegin) {
                throw new CannotCreateTransactionException("database unavailable");
            }
            begun++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) {
                throw new TransactionSystemException("commit acknowledgement lost");
            }
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }

    private void stubAuthorizedUpload(MockedStatic<PathValidationUtils> paths, MockedStatic<CarlosProperties> configuration,
                                      Path uploaded, Path documentDir) {
        paths.when(() -> PathValidationUtils.validateUpload(uploaded.toFile())).thenReturn(uploaded.toFile());
        CarlosProperties properties = mock(CarlosProperties.class);
        configuration.when(CarlosProperties::getInstance).thenReturn(properties);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
        when(properties.getProperty("CML_UPLOAD_KEY")).thenReturn("fixture-key");
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
