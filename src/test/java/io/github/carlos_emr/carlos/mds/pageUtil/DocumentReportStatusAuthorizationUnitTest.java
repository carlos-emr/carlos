/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.mds.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Document ACK/comments must not bypass the same source boundary as metadata and queue filing. */
@Tag("unit")
class DocumentReportStatusAuthorizationUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oscarMDS/UpdateStatus");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final Transactions transactions = new Transactions();
    private DocumentDao documents;
    private PatientLabRoutingDao patients;
    private SecurityInfoManager security;
    private Document document;
    private LoggedInInfo info;

    @BeforeEach void prepare() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        documents = createAndRegisterMock(DocumentDao.class);
        patients = createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(CtlDocumentDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        createAndRegisterMock(ProgramManager2.class);
        registerMock(org.springframework.transaction.PlatformTransactionManager.class, transactions);
        Provider provider = new Provider(); provider.setProviderNo("991830");
        info = new LoggedInInfo(); info.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        request.setParameter("segmentID", "42"); request.setParameter("labType", "DOC");
        request.setParameter("status", "A"); request.setParameter("ajaxcall", "yes");
        request.setParameter("providerNo", "forged-provider"); request.setParameter("comment", "owned test comment");
        document = new Document(42); document.setRestrictToProgram(false);
        when(documents.find(42)).thenReturn(document); when(documents.findForPageMutation(42)).thenReturn(document);
        when(security.hasPrivilege(eq(info), anyString(), eq("w"), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(info), anyInt())).thenReturn(true);
    }

    private void invoke(boolean comment) {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            if (comment) new ReportStatusUpdate2Action().addComment(); else new ReportStatusUpdate2Action().executemain();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"doc", "Doc", "DOC ", " DOC", "DÓC", "", "HL7 ", "unknown"})
    void noncanonicalSourceCannotBypassDocumentChecksThroughDatabaseCollation(String type) {
        request.setParameter("labType", type);
        try (var data = mockStatic(CommonLabResultData.class)) {
            invoke(false); assertThat(response.getStatus()).isEqualTo(400);
            response.reset(); invoke(true); assertThat(response.getStatus()).isEqualTo(400);
            data.verifyNoInteractions(); verify(documents, never()).findForPageMutation(anyInt());
            logActionMock.verifyNoInteractions();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ambiguousSourceIsRejectedBeforeAnyLookup(boolean comment) {
        request.setParameter("labType", "HL7", "DOC");
        try (var data = mockStatic(CommonLabResultData.class)) {
            invoke(comment); assertThat(response.getStatus()).isEqualTo(400);
            data.verifyNoInteractions(); logActionMock.verifyNoInteractions();
        }
    }

    @Test void acknowledgementAuditsPatientResolvedAfterWaiting() {
        PatientLabRouting old = new PatientLabRouting(); old.setDemographicNo(76);
        PatientLabRouting current = new PatientLabRouting(); current.setDemographicNo(77);
        when(patients.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(old));
        when(documents.findForPageMutation(42)).thenAnswer(call -> {
            when(patients.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(current)); return document;
        });
        try (var data = mockStatic(CommonLabResultData.class)) {
            invoke(false); assertThat(response.getStatus()).isEqualTo(200);
            logActionMock.verify(() -> io.github.carlos_emr.carlos.log.LogAction.addLog("991830",
                    io.github.carlos_emr.carlos.log.LogConst.ACK, io.github.carlos_emr.carlos.log.LogConst.CON_HL7_LAB,
                    "42", request.getRemoteAddr(), "77"));
        }
    }

    @ParameterizedTest @CsvSource({"false,patient", "true,patient", "false,program", "true,program", "false,write", "true,write"})
    void sourceDenialPrecedesAnyRoutingOrAudit(boolean comment, String denied) {
        if ("patient".equals(denied)) {
            PatientLabRouting route = new PatientLabRouting(); route.setDemographicNo(77); route.setLabNo(42); route.setLabType("DOC");
            when(patients.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(route));
            when(security.isAllowedAccessToPatientRecord(info, 77)).thenReturn(false);
        } else if ("program".equals(denied)) {document.setRestrictToProgram(true); document.setProgramId(99);}
        else when(security.hasPrivilege(info, "_edoc", "w", (String) null)).thenReturn(false);
        try (var data = mockStatic(CommonLabResultData.class)) {
            invoke(comment);
            assertThat(response.getStatus()).isEqualTo(403);
            data.verifyNoInteractions(); logActionMock.verifyNoInteractions();
            verify(documents, never()).findForPageMutation(anyInt());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void permissionsAreRecheckedAfterWaitingForDocumentRow(boolean comment) {
        when(documents.findForPageMutation(42)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            when(security.hasPrivilege(info, "_edoc", "w", (String) null)).thenReturn(false);
            return document;
        });
        try (var data = mockStatic(CommonLabResultData.class)) {
            invoke(comment);
            assertThat(response.getStatus()).isEqualTo(403); assertThat(transactions.rollbacks).isEqualTo(1);
            data.verifyNoInteractions(); logActionMock.verifyNoInteractions();
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void authorizedRoutingRunsUnderLockAndCommitsBeforeSuccess(boolean comment) throws Exception {
        try (var data = mockStatic(CommonLabResultData.class)) {
            data.when(() -> CommonLabResultData.updateReportStatusWithOlderVersions(42, "991830", 'A', "owned test comment", "DOC", false, null))
                    .thenAnswer(call -> {assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue(); return 1;});
            data.when(() -> CommonLabResultData.updateReportStatus(42, "991830", 'A', "owned test comment", "DOC"))
                    .thenAnswer(call -> {assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue(); return true;});
            invoke(comment);
            assertThat(response.getStatus()).isEqualTo(200); assertThat(transactions.commits).isEqualTo(1);
            verify(documents).findForPageMutation(42);
            var result = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
            if (comment) assertThat(result.has("date")).isTrue(); else assertThat(result.path("clearedCount").asInt()).isEqualTo(1);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void commitFailureCannotEmitConfirmedSuccess(boolean comment) throws Exception {
        transactions.failCommit = true;
        try (var data = mockStatic(CommonLabResultData.class)) {
            invoke(comment);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getContentAsString()).doesNotContain("clearedCount", "date");
            logActionMock.verifyNoInteractions();
        }
    }

    private static final class Transactions extends org.springframework.transaction.support.AbstractPlatformTransactionManager {
        int commits, rollbacks; boolean failCommit;
        @Override protected Object doGetTransaction() {return new Object();}
        @Override protected void doBegin(Object value, org.springframework.transaction.TransactionDefinition definition) { }
        @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
            if (failCommit) throw new org.springframework.transaction.TransactionSystemException("Unconfirmed commit"); commits++;
        }
        @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {rollbacks++;}
    }
}
