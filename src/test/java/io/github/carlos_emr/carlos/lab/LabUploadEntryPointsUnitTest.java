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
package io.github.carlos_emr.carlos.lab;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabUpload2Action;
import io.github.carlos_emr.carlos.lab.ca.all.web.SubmitLabByForm2Action;
import io.github.carlos_emr.carlos.lab.ca.all.upload.HandlerClassFactory;
import io.github.carlos_emr.carlos.lab.ca.all.upload.ProviderLabRouting;
import io.github.carlos_emr.carlos.lab.ca.all.upload.handlers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.lab.ca.all.util.CMLLabHL7Generator;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.test.unit.RecordingTransactionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Checks rejection and routing rollback at the signed-feed and manual-form entry points.
 * @since 2026-09-25
 */
@Tag("unit")
@Tag("lab")
class LabUploadEntryPointsUnitTest extends CarlosUnitTestBase {
    @TempDir Path root;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FileUploadCheckDao dao;
    private MessageHandler handler;
    private RecordingTransactionManager transactions;

    @BeforeEach
    void setUpUpload() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        LoggedInInfo info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_lab"), eq("w"), isNull())).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        transactions = new RecordingTransactionManager();
        registerMock(PlatformTransactionManager.class, transactions);
        dao = mock(FileUploadCheckDao.class);
        doAnswer(invocation -> {
            invocation.<io.github.carlos_emr.carlos.commn.model.FileUploadCheck>getArgument(0).setId(1);
            return null;
        }).when(dao).persist(any());
        registerMock(FileUploadCheckDao.class, dao);
        handler = mock(MessageHandler.class);
    }

    @Test
    void shouldRollBackAndReportFailure_whenSignedFeedParserRejects() throws Exception {
        runSignedFeed();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(request.getAttribute("outcome")).isEqualTo("upload failed");
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test
    void shouldReportServerFailureInsteadOfDuplicate_whenSignedFeedLookupFails() throws Exception {
        when(dao.findByMd5Sum(anyString())).thenThrow(new IllegalStateException("synthetic failure"));
        runSignedFeed();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(request.getAttribute("outcome")).isEqualTo("exception");
        verifyNoInteractions(handler);
    }

    @Test
    void shouldReportConflictWithoutParsing_whenSignedFeedIsDuplicate() throws Exception {
        when(dao.findByMd5Sum(anyString())).thenReturn(List.of(new io.github.carlos_emr.carlos.commn.model.FileUploadCheck()));
        runSignedFeed();
        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(handler);
    }

    @Test
    void shouldRollBackManualSubmission_whenParserReturnsNoLabNumber() throws Exception {
        when(handler.parse(any(), anyString(), anyString(), anyInt(), anyString())).thenReturn("success");
        SubmitLabByForm2Action action = runManualForm(false);
        assertThat(action.getActionErrors()).isNotEmpty();
        assertThat(action.getActionMessages()).isEmpty();
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    @Test
    void shouldRollBackManualSubmission_whenProviderRoutingFails() throws Exception {
        when(handler.parse(any(), anyString(), anyString(), anyInt(), anyString())).thenReturn("success");
        when(handler.getLastLabNo()).thenReturn(42);
        SubmitLabByForm2Action action = runManualForm(true);
        assertThat(action.getActionErrors()).isNotEmpty();
        assertThat(action.getActionMessages()).isEmpty();
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    private void runSignedFeed() throws Exception {
        Path file = Files.writeString(root.resolve("synthetic.hl7"), "SYNTHETIC");
        request.setParameter("service", "synthetic");
        request.setParameter("key", "test");
        request.setParameter("signature", "test");
        request.setParameter("use_http_response_code", "true");
        PublicKey key = mock(PublicKey.class);
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class);
                MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class);
                MockedStatic<Utilities> utilities = mockStatic(Utilities.class);
                MockedStatic<HandlerClassFactory> handlers = mockStatic(HandlerClassFactory.class);
                MockedStatic<LabUpload2Action> feed = mockStatic(LabUpload2Action.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            paths.when(() -> PathValidationUtils.validateUpload(any(File.class))).thenReturn(file.toFile());
            InputStream encrypted = spy(new java.io.ByteArrayInputStream("SYNTHETIC".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            paths.when(() -> PathValidationUtils.openValidatedUploadInputStream(file.toFile())).thenReturn(encrypted);
            paths.when(() -> PathValidationUtils.validateExistingDocumentPath(file.toString())).thenReturn(file.toFile());
            utilities.when(() -> Utilities.saveFile(any(InputStream.class), anyString())).thenReturn(file.toString());
            handlers.when(() -> HandlerClassFactory.getHandler("CML")).thenReturn(handler);
            feed.when(() -> LabUpload2Action.getClientInfo("synthetic")).thenReturn(new ArrayList<>(List.of(key, "CML")));
            feed.when(() -> LabUpload2Action.decryptMessage(any(InputStream.class), eq("test"), eq(key)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            feed.when(() -> LabUpload2Action.validateSignature(key, "test", file.toFile())).thenReturn(true);
            LabUpload2Action action = new LabUpload2Action();
            action.setImportFile(file.toFile());
            action.execute();
            paths.verify(() -> PathValidationUtils.openValidatedUploadInputStream(file.toFile()));
            verify(encrypted, atLeastOnce()).close();
        }
    }

    private SubmitLabByForm2Action runManualForm(boolean failRouting) throws Exception {
        Path file = Files.writeString(root.resolve("synthetic.hl7"), "SYNTHETIC");
        request.setParameter("labname", "CML");
        request.setParameter("lab_req_date", "2026-09-25 12:00");
        request.setParameter("dob", "2000-01-01");
        request.setParameter("test_num", "0");
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class);
                MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class);
                MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class);
                MockedStatic<Utilities> utilities = mockStatic(Utilities.class);
                MockedStatic<HandlerClassFactory> handlers = mockStatic(HandlerClassFactory.class);
                MockedStatic<CMLLabHL7Generator> generator = mockStatic(CMLLabHL7Generator.class);
                MockedConstruction<ProviderLabRouting> routers = mockConstruction(ProviderLabRouting.class, (router, ignored) -> {
                    if (failRouting) doAnswer(invocation -> {
                        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                        throw new IllegalStateException("synthetic routing failure");
                    }).when(router).routeMagic(anyInt(), anyString(), anyString());
                })) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            CarlosProperties config = mock(CarlosProperties.class);
            properties.when(CarlosProperties::getInstance).thenReturn(config);
            when(config.getProperty("DOCUMENT_DIR")).thenReturn(root.toString());
            paths.when(() -> PathValidationUtils.validateExistingPath(eq(file.toString()), any(File.class))).thenReturn(file.toFile());
            utilities.when(() -> Utilities.saveFile(any(InputStream.class), anyString())).thenReturn(file.toString());
            handlers.when(() -> HandlerClassFactory.getHandler("CML")).thenReturn(handler);
            generator.when(() -> CMLLabHL7Generator.generate(any())).thenReturn("MSH|SYNTHETIC");
            SubmitLabByForm2Action action = new SubmitLabByForm2Action() {
                @Override public String getText(String key) { return key; }
            };
            assertThat(action.saveManage()).isEqualTo("manage");
            if (failRouting) verify(routers.constructed().get(0)).routeMagic(42, "999998", "HL7");
            return action;
        }
    }
}
