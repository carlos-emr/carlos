/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual queue filing action, real database rollback and contention between independent sessions. */
@Tag("integration") @Tag("document")
@Isolated("Per-thread static HTTP/security bindings around real JPA transactions")
class DmsQueueFilingPersistenceIntegrationTest extends CarlosTestBase {
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DocumentDao documents;
    @Autowired QueueDocumentLinkDao queues;
    @PersistenceContext(unitName = "entityManagerFactory") EntityManager entityManager;

    private TransactionTemplate transaction() {
        TransactionTemplate result = new TransactionTemplate(transactionManager);
        result.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        result.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); return result;
    }

    private int fixture() {
        return transaction().execute(status -> {
            Document document = new Document(); document.setDocfilename("queue-filing-owned.pdf"); document.setDocdesc("Owned queue filing");
            document.setDoctype("Lab"); document.setStatus('A'); document.setDoccreator("991821"); document.setResponsible("991821");
            document.setContenttype("application/pdf"); document.setPublic1(0); document.setNumberofpages(1); document.setRestrictToProgram(false);
            documents.persist(document); entityManager.flush();
            for (int queue : List.of(1, 7)) {
                QueueDocumentLink link = new QueueDocumentLink(); link.setDocId(document.getId()); link.setQueueId(queue); link.setStatus("A"); queues.persist(link);
            }
            return document.getId();
        });
    }

    private void cleanup(int id) {
        transaction().executeWithoutResult(status -> {
            for (QueueDocumentLink link : queues.getQueueFromDocument(id)) queues.remove(link.getId()); documents.remove(id);
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T intercept(Class<T> type, T bean, java.util.function.BiFunction<String, Object[], Runnable> callback) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Runnable after = callback.apply(method.getName(), args);
            try {Object result = method.invoke(bean, args); if (after != null) after.run(); return result;}
            catch (InvocationTargetException failure) {throw failure.getCause();}
        });
    }

    private MockHttpServletResponse file(int id, String providerNo, DocumentDao documentBean, QueueDocumentLinkDao queueBean) {
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setMethod("POST"); request.setParameter("docid", String.valueOf(id));
        Provider provider = new Provider(); provider.setProviderNo(providerNo);
        LoggedInInfo info = new LoggedInInfo(); info.setLoggedInProvider(provider); LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class); MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            spring.when(() -> SpringUtils.getBean(any(Class.class))).thenAnswer(call -> {
                Class<?> type = call.getArgument(0);
                if (type == DocumentDao.class) return documentBean;
                if (type == QueueDocumentLinkDao.class) return queueBean;
                if (type == SecurityInfoManager.class) return security;
                return applicationContext.getBean(type);
            });
            servlet.when(ServletActionContext::getRequest).thenReturn(request); servlet.when(ServletActionContext::getResponse).thenReturn(response);
            new DmsInboxManage2Action().updateDocStatusInQueue(); return response;
        }
    }

    @Test void failureAfterSecondRealQueueMergeRollsBackEveryStatus() throws Exception {
        int id = fixture(); AtomicInteger written = new AtomicInteger();
        QueueDocumentLinkDao failing = intercept(QueueDocumentLinkDao.class, queues, (method, args) -> "merge".equals(method) ? () -> {
            entityManager.flush();
            if (written.incrementAndGet() == 2) {
                assertThat(queues.getQueueFromDocument(id)).allSatisfy(link -> assertThat(link.getStatus()).isEqualTo("I"));
                throw new IllegalStateException("Injected failure after queue rows were written");
            }
        } : null);
        try {
            MockHttpServletResponse response = file(id, "991821", documents, failing);
            assertThat(written.get()).isEqualTo(2); assertThat(response.getStatus()).isEqualTo(500);
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).path("accepted").asBoolean()).isFalse();
            transaction().executeWithoutResult(status -> assertThat(queues.getQueueFromDocument(id)).hasSize(2)
                    .allSatisfy(link -> assertThat(link.getStatus()).isEqualTo("A")));
        } finally {cleanup(id);}
    }

    @Test void secondSessionWaitsForCommitThenFilesIdempotentlyWithoutRepeatingMerges() throws Exception {
        int id = fixture(); CountDownLatch firstLocked = new CountDownLatch(1), release = new CountDownLatch(1), secondAttempt = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2); AtomicInteger merges = new AtomicInteger();
        QueueDocumentLinkDao counting = intercept(QueueDocumentLinkDao.class, queues, (method, args) -> "merge".equals(method) ? () -> {merges.incrementAndGet();} : null);
        DocumentDao first = intercept(DocumentDao.class, documents, (method, args) -> "findForPageMutation".equals(method) ? () -> {
            firstLocked.countDown();
            try {if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Lock release timed out");}
            catch (InterruptedException failure) {Thread.currentThread().interrupt(); throw new IllegalStateException(failure);}
        } : null);
        DocumentDao second = intercept(DocumentDao.class, documents, (method, args) -> {
            if ("findForPageMutation".equals(method)) secondAttempt.countDown(); return null;
        });
        try {
            Future<MockHttpServletResponse> a = workers.submit(() -> file(id, "991821", first, counting));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<MockHttpServletResponse> b = workers.submit(() -> file(id, "991822", second, counting));
            assertThat(secondAttempt.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> b.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(a.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
            assertThat(b.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(200); assertThat(merges.get()).isEqualTo(2);
            transaction().executeWithoutResult(status -> assertThat(queues.getQueueFromDocument(id)).hasSize(2)
                    .allSatisfy(link -> assertThat(link.getStatus()).isEqualTo("I")));
        } finally {
            release.countDown(); workers.shutdownNow(); assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); cleanup(id);
        }
    }
}
