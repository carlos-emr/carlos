/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentExtraReviewerDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.DocumentExtraReviewer;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Document review attestation through the real {@link AddEditDocument2Action}, real DAOs and real
 * JPA row locks (issue #6081). Only the HTTP identity, access decisions and audit sink are mocked.
 *
 * <p>The edit page cannot know whether the review it shows is still current, so these cases post
 * the flag a stale page would send and check what is stored.</p>
 *
 * @since 2026-11-09
 */
@Tag("integration")
@Tag("document")
@DisplayName("Document review persistence")
@Isolated("Uses per-thread static servlet/security bindings around real JPA transactions")
// The action owns its transactions, as it does in production; CarlosTestBase's per-test
// transaction would otherwise hold the row lock and hide the commit being tested.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DocumentReviewPersistenceIntegrationTest extends CarlosTestBase {
    private static final int PATIENT = 991820;
    private static final String FIRST = "991821";
    private static final String SECOND = "991822";

    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DocumentDao documents;
    @Autowired CtlDocumentDao links;
    @Autowired DocumentExtraReviewerDao extraReviewers;
    @PersistenceContext(unitName = "entityManagerFactory") EntityManager entityManager;

    private Integer documentNo;

    @BeforeEach
    void forgetCachedDocumentDao() throws Exception {
        // EDocUtil caches the DAO it first resolves; make each case resolve the one it is given.
        cachedEDocUtilDao(null);
    }

    @AfterEach
    void cleanup() throws Exception {
        cachedEDocUtilDao(null);
        if (documentNo == null) return;
        int id = documentNo;
        transaction().executeWithoutResult(status -> {
            for (DocumentExtraReviewer review : extraReviewers.findByDocumentNo(id)) extraReviewers.remove(review.getId());
            for (CtlDocument link : links.findByDocumentNoAndModule(id, "demographic")) links.remove(link.getId());
            if (documents.find(id) != null) documents.remove(id);
        });
    }

    @Test
    @DisplayName("should record a first review when the page still shows a review that was cleared")
    void shouldRecordFirstReview_whenStalePageShowsAClearedReview() {
        documentNo = fixture(null, null);
        // The page was rendered while FIRST's review existed, so it posts an additional review.
        String result = review(documentNo, SECOND, false, true, documents);

        assertThat(result).isEqualTo("successEdit");
        transaction().executeWithoutResult(status -> {
            Document stored = documents.find(documentNo);
            assertThat(stored.getReviewer()).isEqualTo(SECOND);
            assertThat(stored.getReviewdatetime()).isNotNull();
            assertThat(extraReviewers.findByDocumentNo(documentNo)).isEmpty();
        });
    }

    @Test
    @DisplayName("should add an additional review when the page still shows the document as unreviewed")
    void shouldAddAdditionalReview_whenStalePageShowsTheDocumentUnreviewed() {
        Date firstReview = new Date(1_700_000_000_000L);
        documentNo = fixture(FIRST, firstReview);
        // The page was rendered before FIRST reviewed it, so it posts a first review.
        String result = review(documentNo, SECOND, true, false, documents);

        assertThat(result).isEqualTo("successEdit");
        transaction().executeWithoutResult(status -> {
            Document stored = documents.find(documentNo);
            assertThat(stored.getReviewer()).isEqualTo(FIRST);
            assertThat(stored.getReviewdatetime().getTime()).isEqualTo(firstReview.getTime());
            assertThat(extraReviewers.findByDocumentNo(documentNo))
                    .extracting(DocumentExtraReviewer::getReviewerProviderNo).containsExactly(SECOND);
        });
    }

    @Test
    @DisplayName("should store each provider once when the same provider clicks Reviewed twice")
    void shouldStoreOneReview_whenTheSameProviderPostsTwice() {
        documentNo = fixture(null, null);
        assertThat(review(documentNo, FIRST, true, false, documents)).isEqualTo("successEdit");
        assertThat(review(documentNo, FIRST, true, false, documents)).isEqualTo("successEdit");

        transaction().executeWithoutResult(status -> {
            assertThat(documents.find(documentNo).getReviewer()).isEqualTo(FIRST);
            assertThat(extraReviewers.findByDocumentNo(documentNo)).isEmpty();
        });
    }

    @Test
    @DisplayName("should keep both reviews when two providers review an unreviewed document at the same time")
    void shouldKeepBothReviews_whenTwoProvidersReviewConcurrently() throws Exception {
        documentNo = fixture(null, null);
        int id = documentNo;
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempt = new CountDownLatch(1);
        DocumentDao holdsLock = intercept(documents, (method, args) -> "findForPageMutation".equals(method) ? () -> {
            firstLocked.countDown();
            await(releaseFirst);
        } : null);
        DocumentDao waits = intercept(documents, (method, args) -> {
            if ("findForPageMutation".equals(method)) secondAttempt.countDown();
            return null;
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            // Both pages were rendered unreviewed, so both post a first review.
            Future<String> first = workers.submit(() -> review(id, FIRST, true, false, holdsLock));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> second = workers.submit(() -> review(id, SECOND, true, false, waits));
            assertThat(secondAttempt.await(5, TimeUnit.SECONDS)).isTrue();
            // The second save waits on the first one's row lock instead of deciding from a stale read.
            assertThatThrownBy(() -> second.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("successEdit");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("successEdit");

            transaction().executeWithoutResult(status -> {
                assertThat(documents.find(id).getReviewer()).isEqualTo(FIRST);
                assertThat(extraReviewers.findByDocumentNo(id))
                        .extracting(DocumentExtraReviewer::getReviewerProviderNo).containsExactly(SECOND);
            });
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("should show a message and store nothing when the document is deleted before the save")
    void shouldReportMissingDocument_whenDocumentIsDeletedBeforeTheLockedSave() {
        documentNo = fixture(null, null);
        int id = documentNo;
        // The row disappears after the access check and the pre-upload check, just before the lock.
        DocumentDao vanishing = (DocumentDao) Proxy.newProxyInstance(DocumentDao.class.getClassLoader(),
                new Class<?>[]{DocumentDao.class}, (proxy, method, args) -> {
                    if ("findForPageMutation".equals(method.getName())) {
                        transaction().executeWithoutResult(status -> {
                            for (CtlDocument link : links.findByDocumentNoAndModule(id, "demographic")) links.remove(link.getId());
                            documents.remove(id);
                        });
                    }
                    return invoke(documents, method, args);
                });
        MockHttpServletRequest[] request = new MockHttpServletRequest[1];

        String result = review(id, SECOND, true, false, vanishing, request);

        assertThat(result).isEqualTo("failEdit");
        assertThat(request[0].getAttribute("docerrors")).isEqualTo(
                new java.util.Hashtable<>(Map.of("documentmissing", "dms.error.documentMissing")));
        transaction().executeWithoutResult(status -> {
            assertThat(documents.find(id)).isNull();
            assertThat(extraReviewers.findByDocumentNo(id)).isEmpty();
        });
    }

    private int fixture(String reviewer, Date reviewed) {
        return transaction().execute(status -> {
            Document document = new Document();
            document.setDocfilename("review-6081.pdf");
            document.setDocdesc("Synthetic review document");
            document.setDoctype("Lab");
            document.setStatus('A');
            document.setDoccreator(FIRST);
            document.setResponsible(FIRST);
            document.setContenttype("application/pdf");
            document.setPublic1(0);
            document.setNumberofpages(1);
            document.setRestrictToProgram(false);
            document.setReviewer(reviewer);
            document.setReviewdatetime(reviewed);
            documents.persist(document);
            entityManager.flush();
            CtlDocument link = new CtlDocument();
            link.setId(new CtlDocumentPK("demographic", PATIENT, document.getId()));
            link.setStatus("A");
            links.persist(link);
            return document.getId();
        });
    }

    private String review(int id, String providerNo, boolean reviewDoc, boolean extraReviewDoc, DocumentDao documentBean) {
        return review(id, providerNo, reviewDoc, extraReviewDoc, documentBean, new MockHttpServletRequest[1]);
    }

    private String review(int id, String providerNo, boolean reviewDoc, boolean extraReviewDoc, DocumentDao documentBean,
                          MockHttpServletRequest[] requestOut) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        requestOut[0] = request;
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        LoggedInInfo info = new LoggedInInfo();
        info.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class);
             MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedStatic<LogAction> audit = mockStatic(LogAction.class)) {
            spring.when(() -> SpringUtils.getBean(any(Class.class))).thenAnswer(call -> {
                Class<?> type = call.getArgument(0);
                if (type == DocumentDao.class) return documentBean;
                if (type == SecurityInfoManager.class) return security;
                return applicationContext.getBean(type);
            });
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new org.springframework.mock.web.MockHttpServletResponse());
            AddEditDocument2Action action = new AddEditDocument2Action();
            action.setMode(String.valueOf(id));
            action.setFunction("demographic");
            action.setFunctionId(String.valueOf(PATIENT));
            action.setDocDesc("Synthetic review document");
            action.setDocType("Lab");
            action.setAppointmentNo("0");
            action.setReviewDoc(reviewDoc);
            action.setExtraReviewDoc(extraReviewDoc);
            // What a stale page or a forged post carries; never stored.
            action.setReviewerId("999995");
            action.setExtraReviewerId("999994");
            String result = action.execute2();
            if ("successEdit".equals(result)) {
                audit.verify(() -> LogAction.addLog(eq(providerNo), eq(LogConst.UPDATE), eq(LogConst.CON_DOCUMENT),
                        eq(String.valueOf(id)), anyString(), eq(String.valueOf(PATIENT))));
            } else {
                audit.verify(() -> LogAction.addLog(anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString()), never());
            }
            return result;
        }
    }

    private TransactionTemplate transaction() {
        TransactionTemplate result = new TransactionTemplate(transactionManager);
        result.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        result.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return result;
    }

    private static DocumentDao intercept(DocumentDao bean, BiFunction<String, Object[], Runnable> after) {
        return (DocumentDao) Proxy.newProxyInstance(DocumentDao.class.getClassLoader(), new Class<?>[]{DocumentDao.class},
                (proxy, method, args) -> {
                    Runnable callback = after.apply(method.getName(), args);
                    Object result = invoke(bean, method, args);
                    if (callback != null) callback.run();
                    return result;
                });
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Lock release timed out");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static void cachedEDocUtilDao(DocumentDao value) throws Exception {
        Field field = EDocUtil.class.getDeclaredField("documentDao");
        field.setAccessible(true);
        field.set(null, value);
    }
}
