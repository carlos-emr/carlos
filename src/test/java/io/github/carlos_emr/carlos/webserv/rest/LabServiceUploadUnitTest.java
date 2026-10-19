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
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.lab.ca.all.upload.HandlerClassFactory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.unit.RecordingTransactionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.Hl7TextMessageTo1;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises REST upload outcomes through the real checksum transaction boundary.
 * @since 2026-09-25
 */
@Tag("unit")
@Tag("lab")
class LabServiceUploadUnitTest extends CarlosUnitTestBase {
    @TempDir Path root;
    private LabService service;
    private FileUploadCheckDao dao;
    private MessageHandler handler;
    private RecordingTransactionManager transactions;
    private Hl7TextMessageTo1 upload;

    @BeforeEach
    void setUpUpload() {
        LoggedInInfo info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        service = new LabService() {
            @Override protected LoggedInInfo getLoggedInInfo() { return info; }
        };
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_lab"), eq("w"), any())).thenReturn(true);
        ReflectionTestUtils.setField(service, "securityInfoManager", security);
        dao = mock(FileUploadCheckDao.class);
        doAnswer(invocation -> {
            invocation.<io.github.carlos_emr.carlos.commn.model.FileUploadCheck>getArgument(0).setId(1);
            return null;
        }).when(dao).persist(any());
        registerMock(FileUploadCheckDao.class, dao);
        transactions = new RecordingTransactionManager();
        registerMock(PlatformTransactionManager.class, transactions);
        handler = mock(MessageHandler.class);
        upload = new Hl7TextMessageTo1();
        upload.setFileName("synthetic.hl7");
        upload.setType("CML");
        upload.setBase64EncodedeMessage("U1lOVEhFVElD");
    }

    @Test
    void shouldRollBackChecksum_whenParserRejectsFile() throws Exception {
        when(handler.parse(any(), anyString(), anyString(), anyInt(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        });
        assertThat(upload()).isEqualTo(400);
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    @Test
    void shouldReportServerErrorInsteadOfDuplicate_whenLookupFails() throws Exception {
        when(dao.findByMd5Sum(anyString())).thenThrow(new IllegalStateException("synthetic database failure"));
        assertThat(upload()).isEqualTo(500);
        verifyNoInteractions(handler);
        verify(dao, never()).persist(any());
    }

    @Test
    void shouldSkipParsing_whenContentAlreadyRecorded() throws Exception {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of(new io.github.carlos_emr.carlos.commn.model.FileUploadCheck()));
        assertThat(upload()).isEqualTo(409);
        verifyNoInteractions(handler);
        verify(dao, never()).persist(any());
    }

    @Test
    void shouldCommitChecksum_whenParserSucceeds() throws Exception {
        when(handler.parse(any(), anyString(), anyString(), anyInt(), anyString())).thenReturn("success");
        assertThat(upload()).isEqualTo(200);
        assertThat(transactions.commits).isEqualTo(1);
        assertThat(transactions.rollbacks).isZero();
    }

    @Test
    void shouldRollBackInsteadOfClaimingContent_whenResponseLookupFails() throws Exception {
        when(handler.parse(any(), anyString(), anyString(), anyInt(), anyString())).thenReturn("success");
        when(handler.getLastLabNo()).thenReturn(42);
        var manager = mock(io.github.carlos_emr.carlos.managers.LabManager.class);
        ReflectionTestUtils.setField(service, "labManager", manager);
        when(manager.getHl7Message(any(), eq(42))).thenThrow(new IllegalStateException("synthetic lookup failure"));
        assertThat(upload()).isEqualTo(500);
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    @Test
    void shouldRejectMissingBody_whenUploadIsNull() {
        assertThat(service.uploadHl7Lab(null, new MockHttpServletRequest()).getStatus()).isEqualTo(400);
    }

    @Test
    void shouldRejectMalformedEncoding_whenBase64IsInvalid() {
        upload.setBase64EncodedeMessage("%invalid%");
        assertThat(service.uploadHl7Lab(upload, new MockHttpServletRequest()).getStatus()).isEqualTo(400);
        verifyNoInteractions(dao);
    }

    private int upload() throws Exception {
        Path file = Files.writeString(root.resolve("synthetic.hl7"), "SYNTHETIC");
        try (MockedStatic<Utilities> utilities = mockStatic(Utilities.class);
                MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class);
                MockedStatic<HandlerClassFactory> handlers = mockStatic(HandlerClassFactory.class)) {
            utilities.when(() -> Utilities.saveFile(any(InputStream.class), anyString())).thenReturn(file.toString());
            paths.when(() -> PathValidationUtils.validateExistingDocumentPath(file.toString())).thenReturn(file.toFile());
            handlers.when(() -> HandlerClassFactory.getHandler("CML")).thenReturn(handler);
            return service.uploadHl7Lab(upload, new MockHttpServletRequest()).getStatus();
        }
    }
}
