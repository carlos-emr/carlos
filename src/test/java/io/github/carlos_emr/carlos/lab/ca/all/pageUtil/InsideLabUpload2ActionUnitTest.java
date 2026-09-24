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
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.lab.FileUploadCheck;
import io.github.carlos_emr.carlos.lab.ca.all.upload.HandlerClassFactory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MessageHandler;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.unit.RecordingTransactionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

/** Exercises the inside-lab batch action through its file and handler boundaries.
 * @since 2026-09-20
 */
@Tag("unit")
@Tag("lab")
class InsideLabUpload2ActionUnitTest extends CarlosUnitTestBase {
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
        request.setParameter("type", "HL7");
        request.setRemoteAddr("192.0.2.10");
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
    void shouldRejectBatch_whenLabWritePrivilegeMissing() {
        when(security.hasPrivilege(eq(info), eq("_lab"), eq("w"), isNull())).thenReturn(false);
        assertThatThrownBy(() -> execute(null)).isInstanceOf(SecurityException.class)
                .hasMessageContaining("_lab");
    }

    @Test
    void shouldReturnInput_whenNoFilesUploaded() {
        assertThat(execute(null)).isEqualTo(ActionSupport.INPUT);
    }

    @Test
    void shouldMarkCompleted_whenSavedLabParses(@TempDir Path documentDir) throws Exception {
        Path source = Files.writeString(root.resolve("source.hl7"), "MSH|inside-lab fixture");
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.parse(eq(info), eq("InsideLabUpload2Action"), anyString(), eq(1), eq("192.0.2.10")))
                .thenAnswer(invocation -> {
                    // The handler stores the lab in the transaction holding its checksum row (id 1).
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(Thread.holdsLock(FileUploadCheck.class)).isTrue();
                    return "success";
                });
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             MockedStatic<HandlerClassFactory> handlers = mockStatic(HandlerClassFactory.class)) {
            paths.when(() -> PathValidationUtils.openValidatedUploadInputStream(source.toFile()))
                    .thenAnswer(invocation -> Files.newInputStream(source));
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            duplicateCheck.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenReturn(1);
            handlers.when(() -> HandlerClassFactory.getHandler("HL7")).thenReturn(handler);

            assertThat(execute(source)).isEqualTo(ActionSupport.SUCCESS);

            Map<?, ?> statuses = (Map<?, ?>) request.getAttribute("filesStatusMap");
            assertThat(statuses.get("source.hl7").toString()).isEqualTo("COMPLETED");
            assertThat(transactions.commits).isEqualTo(1);
            duplicateCheck.verify(() -> FileUploadCheck.addFile(anyString(), any(InputStream.class), anyString()), never());
            try (var children = Files.list(documentDir)) {
                var archived = children.toList();
                assertThat(archived).hasSize(1);
                assertThat(Files.readString(archived.get(0))).isEqualTo("MSH|inside-lab fixture");
            }
        }
    }

    @Test
    void shouldMarkExists_whenDuplicateCheckRejectsSavedLab(@TempDir Path documentDir) throws Exception {
        Path source = Files.writeString(root.resolve("duplicate.hl7"), "MSH|duplicate fixture");
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             MockedStatic<HandlerClassFactory> handlers = mockStatic(HandlerClassFactory.class)) {
            paths.when(() -> PathValidationUtils.openValidatedUploadInputStream(source.toFile()))
                    .thenAnswer(invocation -> Files.newInputStream(source));
            CarlosProperties properties = mock(CarlosProperties.class);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(true);

            assertThat(execute(source)).isEqualTo(ActionSupport.SUCCESS);

            Map<?, ?> statuses = (Map<?, ?>) request.getAttribute("filesStatusMap");
            assertThat(statuses.get("duplicate.hl7").toString()).isEqualTo("EXISTS");
            handlers.verifyNoInteractions();
            assertThat(transactions.begun).isZero();
        }
    }

    @Test
    void shouldRollBackChecksumAndMarkInvalid_whenHandlerRejectsLab(@TempDir Path documentDir) throws Exception {
        Path source = Files.writeString(root.resolve("rejected.hl7"), "MSH|rejected fixture");
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.parse(eq(info), eq("InsideLabUpload2Action"), anyString(), eq(1), eq("192.0.2.10"))).thenReturn(null);
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             MockedStatic<HandlerClassFactory> handlers = mockStatic(HandlerClassFactory.class)) {
            stubSavedUpload(paths, configuration, source, documentDir);
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            duplicateCheck.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenReturn(1);
            handlers.when(() -> HandlerClassFactory.getHandler("HL7")).thenReturn(handler);

            assertThat(execute(source)).isEqualTo(ActionSupport.SUCCESS);

            // A handler that gives up leaves no checksum behind (it rolled back with any partial
            // rows), so the file is not reported "Already uploaded" when it is sent again.
            Map<?, ?> statuses = (Map<?, ?>) request.getAttribute("filesStatusMap");
            assertThat(statuses.get("rejected.hl7").toString()).isEqualTo("INVALID");
            assertThat(transactions.rollbacks).isEqualTo(1);
            assertThat(transactions.commits).isZero();
        }
    }

    @Test
    void shouldRollBackChecksumAndMarkFailed_whenHandlerThrows(@TempDir Path documentDir) throws Exception {
        Path source = Files.writeString(root.resolve("broken.hl7"), "MSH|broken fixture");
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.parse(eq(info), eq("InsideLabUpload2Action"), anyString(), eq(1), eq("192.0.2.10")))
                .thenThrow(new IllegalStateException("routing failed"));
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<FileUploadCheck> duplicateCheck = mockStatic(FileUploadCheck.class, CALLS_REAL_METHODS);
             MockedStatic<HandlerClassFactory> handlers = mockStatic(HandlerClassFactory.class)) {
            stubSavedUpload(paths, configuration, source, documentDir);
            duplicateCheck.when(() -> FileUploadCheck.isFileRecorded(any(InputStream.class))).thenReturn(false);
            duplicateCheck.when(() -> FileUploadCheck.recordFile(anyString(), any(InputStream.class), eq("999998")))
                    .thenReturn(1);
            handlers.when(() -> HandlerClassFactory.getHandler("HL7")).thenReturn(handler);

            assertThat(execute(source)).isEqualTo(ActionSupport.SUCCESS);

            Map<?, ?> statuses = (Map<?, ?>) request.getAttribute("filesStatusMap");
            assertThat(statuses.get("broken.hl7").toString()).isEqualTo("FAILED");
            assertThat(transactions.rollbacks).isEqualTo(1);
            assertThat(transactions.commits).isZero();
        }
    }

    private void stubSavedUpload(MockedStatic<PathValidationUtils> paths, MockedStatic<CarlosProperties> configuration,
                                 Path source, Path documentDir) {
        paths.when(() -> PathValidationUtils.openValidatedUploadInputStream(source.toFile()))
                .thenAnswer(invocation -> Files.newInputStream(source));
        CarlosProperties properties = mock(CarlosProperties.class);
        configuration.when(CarlosProperties::getInstance).thenReturn(properties);
        when(properties.getProperty("DOCUMENT_DIR")).thenReturn(documentDir.toString());
    }

    private String execute(Path source) {
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            InsideLabUpload2Action action = new InsideLabUpload2Action();
            if (source != null) {
                action.setImportFiles(List.of(source.toFile()));
                action.setImportFilesFileName(List.of(source.getFileName().toString()));
                action.setImportFilesContentType(List.of("text/plain"));
            }
            return action.execute();
        }
    }
}
