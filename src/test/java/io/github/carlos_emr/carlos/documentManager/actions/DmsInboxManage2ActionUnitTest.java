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
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.daos.security.SecObjectNameDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.junit.jupiter.api.parallel.Isolated;
import io.github.carlos_emr.carlos.commn.dao.InboxResultsDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import java.util.TimeZone;
import java.util.Date;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;
import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.managers.ProgramManager2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Unit tests for {@link DmsInboxManage2Action} logout redirect short-circuiting.
 */
@Isolated("Exercises the legacy inbox in a daylight-saving time zone")
@ExtendWith(MockitoExtension.class)
@DisplayName("DmsInboxManage2Action logout redirect")
@Tag("unit")
@Tag("documentManager")
class DmsInboxManage2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private ProviderInboxRoutingDao providerInboxRoutingDao;

    @Mock
    private QueueDocumentLinkDao queueDocumentLinkDao;

    @Mock
    private SecObjectNameDao secObjectNameDao;

    @Mock
    private SecUserRoleDao secUserRoleDao;

    @Mock
    private QueueDao queueDao;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final DocumentDao documents = mock(DocumentDao.class);
    private final CtlDocumentDao links = mock(CtlDocumentDao.class);
    private final PatientLabRoutingDao patients = mock(PatientLabRoutingDao.class);
    private final ProgramManager2 programs = mock(ProgramManager2.class);
    private final QueueTransactions transactions = new QueueTransactions();

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ProviderInboxRoutingDao.class, providerInboxRoutingDao);
        registerMock(QueueDocumentLinkDao.class, queueDocumentLinkDao);
        registerMock(SecObjectNameDao.class, secObjectNameDao);
        registerMock(SecUserRoleDao.class, secUserRoleDao);
        registerMock(QueueDao.class, queueDao);
        registerMock(DocumentDao.class, documents);
        registerMock(CtlDocumentDao.class, links);
        registerMock(PatientLabRoutingDao.class, patients);
        registerMock(ProgramManager2.class, programs);
        registerMock(org.springframework.transaction.PlatformTransactionManager.class, transactions);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    @DisplayName("should reject non-POST addNewQueue with 405 before any privilege check or write")
    void shouldReturn405_whenAddNewQueueIsNotPost(String httpMethod) {
        request.setMethod(httpMethod);
        request.setParameter("method", "addNewQueue");
        request.setParameter("newQueueName", "Reject Probe");

        String result = new DmsInboxManage2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager, queueDao, secObjectNameDao);
    }

    @Test
    @DisplayName("should deny addNewQueue to a read-only _edoc user and write nothing")
    void shouldThrowSecurityException_whenAddNewQueueWithoutEdocWrite() {
        request.setMethod("POST");
        request.setParameter("method", "addNewQueue");
        request.setParameter("newQueueName", "Read Only Probe");
        when(securityInfoManager.hasPrivilege(nullable(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new DmsInboxManage2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_edoc)");
        verifyNoInteractions(queueDao, secObjectNameDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("should answer 400 rather than throw when addNewQueue has a blank or missing name")
    void shouldReturn400_whenAddNewQueueNameBlank(String name) {
        request.setMethod("POST");
        request.setParameter("method", "addNewQueue");
        request.setParameter("newQueueName", name);
        when(securityInfoManager.hasPrivilege(nullable(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(true);

        String result = new DmsInboxManage2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(queueDao, secObjectNameDao);
    }

    @Test
    @DisplayName("should answer 400 when addNewQueue has no newQueueName parameter")
    void shouldReturn400_whenAddNewQueueNameMissing() {
        request.setMethod("POST");
        request.setParameter("method", "addNewQueue");
        when(securityInfoManager.hasPrivilege(nullable(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(true);

        String result = new DmsInboxManage2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(queueDao, secObjectNameDao);
    }

    @Test
    @DisplayName("should create the queue and its security object on an authorised POST")
    void shouldCreateQueueAndSecObject_whenAddNewQueuePostedWithWrite() throws Exception {
        request.setMethod("POST");
        request.setParameter("method", "addNewQueue");
        request.setParameter("newQueueName", "  Lab Queue  ");
        when(securityInfoManager.hasPrivilege(nullable(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(true);
        when(queueDao.addNewQueue("Lab Queue")).thenReturn(true);
        when(queueDao.getLastId()).thenReturn("42");

        String result = new DmsInboxManage2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"addNewQueue\":true}");
        org.mockito.Mockito.verify(secObjectNameDao).saveOrUpdate(org.mockito.ArgumentMatchers.argThat(
                sbn -> "_queue.42".equals(sbn.getObjectname()) && "Lab Queue".equals(sbn.getDescription())));
    }

    @Test
    @DisplayName("should not create a security object when the queue insert fails")
    void shouldNotCreateSecObject_whenQueueInsertFails() throws Exception {
        request.setMethod("POST");
        request.setParameter("method", "addNewQueue");
        request.setParameter("newQueueName", "Dup");
        when(securityInfoManager.hasPrivilege(nullable(LoggedInInfo.class), eq("_edoc"), eq("w"), isNull()))
                .thenReturn(true);
        when(queueDao.addNewQueue("Dup")).thenReturn(false);

        new DmsInboxManage2Action().execute();

        assertThat(response.getContentAsString()).isEqualTo("{\"addNewQueue\":false}");
        verifyNoInteractions(secObjectNameDao);
    }

    @Test
    @DisplayName("should return NONE when index session is unauthenticated")
    void shouldReturnNone_whenIndexSessionUnauthenticated() {
        DmsInboxManage2Action action = new DmsInboxManage2Action();

        String result = action.prepareForIndexPage();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        verifyNoInteractions(secUserRoleDao, queueDocumentLinkDao, queueDao);
    }

    @Test
    @DisplayName("should return NONE when execute dispatches index with missing user role")
    void shouldReturnNone_whenExecuteDispatchesIndexWithMissingUserRole() {
        request.setParameter("method", "prepareForIndexPage");
        when(securityInfoManager.hasPrivilege(nullable(LoggedInInfo.class), eq("_edoc"), eq("r"), isNull()))
                .thenReturn(true);
        DmsInboxManage2Action action = new DmsInboxManage2Action();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        verifyNoInteractions(secUserRoleDao, queueDocumentLinkDao, queueDao);
    }

    @Test
    @DisplayName("should return NONE when content session is unauthenticated")
    void shouldReturnNone_whenContentSessionUnauthenticated() {
        DmsInboxManage2Action action = new DmsInboxManage2Action();

        String result = action.prepareForContentPage();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        verifyNoInteractions(secUserRoleDao, queueDocumentLinkDao);
    }

    @Test
    @DisplayName("should return NONE when queue session is unauthenticated")
    void shouldReturnNone_whenQueueSessionUnauthenticated() {
        DmsInboxManage2Action action = new DmsInboxManage2Action();

        String result = action.getDocumentsInQueues();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logoutPage");
        verifyNoInteractions(secUserRoleDao, queueDocumentLinkDao, queueDao);
    }

    @Test
    @DisplayName("should return NONE when redirect fails")
    void shouldReturnNone_whenRedirectFails() throws Exception {
        HttpServletResponse failingResponse = mock(HttpServletResponse.class);
        doThrow(new IOException("already committed")).when(failingResponse).sendRedirect("/carlos/logoutPage");
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(failingResponse);
        DmsInboxManage2Action action = new DmsInboxManage2Action();

        String result = action.prepareForIndexPage();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verifyNoInteractions(secUserRoleDao, queueDocumentLinkDao, queueDao);
    }

    @ParameterizedTest
    @CsvSource({"2026-03-08,2026-03-09T03:59:59.999Z", "2026-11-01,2026-11-02T04:59:59.999Z",
            "2026-03-10,2026-03-11T03:59:59.999Z"})
    void shouldPassCompleteLocalEndDate_whenDaylightSavingChanges(String selectedDate, String expectedInstant) {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
            request.getSession().setAttribute("userrole", "doctor");
            request.getSession().setAttribute("user", "991820");
            request.setParameter("view", "documents");
            request.setParameter("endDate", selectedDate);
            var inbox = mock(InboxResultsDao.class);
            registerMock(InboxResultsDao.class, inbox);
            registerMock(ProviderLabRoutingDao.class,
                    mock(ProviderLabRoutingDao.class));
            AtomicReference<Date> capturedEnd = new AtomicReference<>();
            RuntimeException queryReached = new RuntimeException("Stop after inspecting the query boundary");
            doAnswer(invocation -> {
                capturedEnd.set(invocation.getArgument(12));
                throw queryReached;
            }).when(inbox).populateDocumentResultsData(anyString(), nullable(String.class),
                    nullable(String.class), nullable(String.class), nullable(String.class), anyString(),
                    eq(true), eq(0), eq(20), eq(false), isNull(), isNull(), any(Date.class));
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> new DmsInboxManage2Action().prepareForContentPage()).isSameAs(queryReached);
            assertThat(capturedEnd.get().toInstant()).isEqualTo(Instant.parse(expectedInstant));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private Document prepareQueueFiling() {
        Provider provider = new Provider(); provider.setProviderNo("991820");
        LoggedInInfo info = new LoggedInInfo(); info.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        request.setMethod("POST"); request.setParameter("docid", "42"); request.setParameter("method", "updateDocStatusInQueue");
        Document document = new Document(42); document.setRestrictToProgram(false); document.setStatus('A');
        lenient().when(documents.find(42)).thenReturn(document);
        lenient().when(documents.findForPageMutation(42)).thenReturn(document);
        lenient().when(securityInfoManager.hasPrivilege(eq(info), eq("_edoc"), eq("w"), isNull())).thenReturn(true);
        lenient().when(securityInfoManager.hasPrivilege(eq(info), eq("_edoc"), eq("r"), isNull())).thenReturn(true);
        lenient().when(securityInfoManager.hasPrivilege(eq(info), eq("_edoc"), eq("w"), eq("10"))).thenReturn(true);
        lenient().when(securityInfoManager.isAllowedAccessToPatientRecord(eq(info), eq(10))).thenReturn(true);
        return document;
    }

    private QueueDocumentLink queue(int key, int queue, String status) {
        QueueDocumentLink link = new QueueDocumentLink(); link.setId(key); link.setDocId(42); link.setQueueId(queue); link.setStatus(status); return link;
    }

    private void fileQueue(boolean dispatch) {
        DmsInboxManage2Action action = new DmsInboxManage2Action();
        if (dispatch) action.execute(); else action.updateDocStatusInQueue();
    }

    private com.fasterxml.jackson.databind.JsonNode json() throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
    }

    private void noQueueMutation() {
        verify(queueDocumentLinkDao, never()).merge(any(QueueDocumentLink.class));
        verify(queueDocumentLinkDao, never()).setStatusInactive(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest @CsvSource({"GET,false", "HEAD,false", "GET,true", "HEAD,true"})
    void queueFilingRequiresPostForDirectAndDispatchedCalls(String verb, boolean dispatch) {
        prepareQueueFiling(); request.setMethod(verb); fileQueue(dispatch);
        assertThat(response.getStatus()).isEqualTo(405); assertThat(response.getHeader("Allow")).isEqualTo("POST");
        noQueueMutation(); verify(documents, never()).findForPageMutation(anyInt());
    }

    @ParameterizedTest @ValueSource(strings = {"", "0", "-1", " 42", "42 ", "2147483648", "x", "42,43"})
    void invalidDocumentIdentityIsRejectedBeforeMutation(String id) throws Exception {
        prepareQueueFiling(); request.setParameter("docid", id); fileQueue(false);
        assertThat(response.getStatus()).isEqualTo(400); assertThat(json().path("accepted").asBoolean()).isFalse(); noQueueMutation();
    }

    @Test void missingOrDuplicateDocumentIdentityIsRejected() {
        prepareQueueFiling(); request.removeParameter("docid"); fileQueue(false);
        assertThat(response.getStatus()).isEqualTo(400); noQueueMutation();
        response.reset(); request.setParameter("docid", new String[]{"42", "43"}); fileQueue(false);
        assertThat(response.getStatus()).isEqualTo(400); noQueueMutation();
    }

    @ParameterizedTest @ValueSource(strings = {"global", "ctl", "routing", "patientWrite", "program"})
    void actualStoredSourceGatesProtectQueueFiling(String denial) throws Exception {
        Document document = prepareQueueFiling();
        switch (denial) {
            case "global" -> when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), isNull())).thenReturn(false);
            case "ctl", "patientWrite" -> {
                CtlDocument link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic", 10, 42));
                CtlDocument second = new CtlDocument(); second.setId(new CtlDocumentPK("demographic", 20, 42));
                when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(denial.equals("ctl") ? List.of(link, second) : List.of(link));
                if (denial.equals("patientWrite")) when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), eq("10"))).thenReturn(false);
            }
            case "routing" -> {
                PatientLabRouting link = new PatientLabRouting(); link.setDemographicNo(20); link.setLabType("DOC");
                when(patients.findByLabNoAndLabType(42, "DOC")).thenReturn(List.of(link));
            }
            case "program" -> {document.setRestrictToProgram(true); document.setProgramId(17);}
            default -> throw new AssertionError();
        }
        fileQueue(false); assertThat(response.getStatus()).isEqualTo(403); assertThat(json().path("accepted").asBoolean()).isFalse(); noQueueMutation();
    }

    @ParameterizedTest @ValueSource(strings = {"A", "N", ""})
    void visibleSharedQueueDoesNotAuthorizeAnotherAffectedNamedQueue(String hiddenStatus) throws Exception {
        prepareQueueFiling(); QueueDocumentLink shared = queue(1, 1, "A"), denied = queue(2, 7, hiddenStatus);
        when(queueDocumentLinkDao.getQueueFromDocument(42)).thenReturn(List.of(shared, denied));
        fileQueue(false); assertThat(response.getStatus()).isEqualTo(403); noQueueMutation();
        assertThat(shared.getStatus()).isEqualTo("A"); assertThat(denied.getStatus()).isEqualTo(hiddenStatus);
        assertThat(json().path("accepted").asBoolean()).isFalse();
    }

    @Test void queueFilingReauthorizesAfterWaitingForDocumentLock() {
        Document document = prepareQueueFiling();
        when(documents.findForPageMutation(42)).thenAnswer(call -> {document.setRestrictToProgram(true); document.setProgramId(99); return document;});
        fileQueue(false); assertThat(response.getStatus()).isEqualTo(403); noQueueMutation(); assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test void queueAddedWhileWaitingMustBeAuthorizedBeforeAnyFetchedLinkChanges() {
        prepareQueueFiling(); QueueDocumentLink shared = queue(1, 1, "A"), hidden = queue(2, 9, "N");
        when(queueDocumentLinkDao.getQueueFromDocument(42)).thenReturn(List.of(shared));
        when(documents.findForPageMutation(42)).thenAnswer(call -> {
            when(queueDocumentLinkDao.getQueueFromDocument(42)).thenReturn(List.of(shared, hidden));
            return documents.find(42);
        });
        fileQueue(false); assertThat(response.getStatus()).isEqualTo(403); noQueueMutation(); assertThat(shared.getStatus()).isEqualTo("A");
    }

    @Test void successMergesOnlyExactFetchedAuthorizedNonInactiveLinks() throws Exception {
        prepareQueueFiling(); QueueDocumentLink shared = queue(1, 1, "A"), named = queue(2, 7, "N"), inactive = queue(3, 9, "I"), unspecified = queue(4, 10, null);
        when(queueDocumentLinkDao.getQueueFromDocument(42)).thenReturn(List.of(shared, named, inactive, unspecified));
        when(securityInfoManager.hasPrivilege(any(), eq("_queue.7"), eq("r"), isNull())).thenReturn(true);
        fileQueue(false); assertThat(response.getStatus()).isEqualTo(200);
        assertThat(json().path("success").asBoolean()).isTrue(); assertThat(json().path("accepted").asBoolean()).isTrue();
        assertThat(json().path("document").asInt()).isEqualTo(42);
        verify(queueDocumentLinkDao).merge(same(shared)); verify(queueDocumentLinkDao).merge(same(named));
        verify(queueDocumentLinkDao, never()).merge(same(inactive)); verify(queueDocumentLinkDao, never()).merge(same(unspecified));
        verify(queueDocumentLinkDao, never()).setStatusInactive(anyInt());
        assertThat(shared.getStatus()).isEqualTo("I"); assertThat(named.getStatus()).isEqualTo("I");
        assertThat(unspecified.getStatus()).isNull(); assertThat(transactions.commits).isEqualTo(1);
    }

    @Test void alreadyFiledDocumentIsIdempotentButStillNeedsSourceAuthorization() throws Exception {
        prepareQueueFiling(); when(queueDocumentLinkDao.getQueueFromDocument(42)).thenReturn(List.of(queue(1, 7, "I")));
        fileQueue(false); assertThat(response.getStatus()).isEqualTo(200); assertThat(json().path("accepted").asBoolean()).isTrue(); noQueueMutation();
        response.reset(); when(securityInfoManager.hasPrivilege(any(), eq("_edoc"), eq("w"), isNull())).thenReturn(false);
        fileQueue(false); assertThat(response.getStatus()).isEqualTo(403); noQueueMutation();
    }

    @Test void mergeFailureIsVisibleAndConfirmedRollbackIsUnaccepted() throws Exception {
        prepareQueueFiling(); QueueDocumentLink shared = queue(1, 1, "A");
        when(queueDocumentLinkDao.getQueueFromDocument(42)).thenReturn(List.of(shared));
        doThrow(new IllegalStateException("injected merge failure")).when(queueDocumentLinkDao).merge(shared);
        fileQueue(false); assertThat(response.getStatus()).isEqualTo(500); assertThat(json().path("accepted").asBoolean()).isFalse();
        assertThat(transactions.rollbacks).isEqualTo(1); verify(queueDocumentLinkDao, never()).setStatusInactive(anyInt());
    }

    @Test void unknownCommitMustForbidReplay() throws Exception {
        prepareQueueFiling(); when(queueDocumentLinkDao.getQueueFromDocument(42)).thenReturn(List.of(queue(1, 1, "A")));
        transactions.failCommit = true; fileQueue(false);
        assertThat(response.getStatus()).isEqualTo(500); assertThat(json().path("success").asBoolean()).isFalse();
        assertThat(json().path("accepted").asBoolean()).isTrue();
    }

    @Test void failedTransactionAdmissionBeforeCallbackIsKnownUnaccepted() throws Exception {
        prepareQueueFiling(); transactions.failBegin = true; fileQueue(false);
        assertThat(response.getStatus()).isEqualTo(500); assertThat(json().path("accepted").asBoolean()).isFalse(); noQueueMutation();
        verify(documents, never()).findForPageMutation(anyInt());
    }

    private static final class QueueTransactions extends org.springframework.transaction.support.AbstractPlatformTransactionManager {
        int commits, rollbacks; boolean failCommit, failBegin;
        @Override protected Object doGetTransaction() {return new Object();}
        @Override protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
            if (failBegin) throw new org.springframework.transaction.CannotCreateTransactionException("Unavailable connection");
        }
        @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
            if (failCommit) throw new org.springframework.transaction.TransactionSystemException("Unconfirmed commit"); commits++;
        }
        @Override protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus status) {rollbacks++;}
    }
}
