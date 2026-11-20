/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.log.LogAction;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real production action and JPA row locks; only HTTP identity/access decisions are mocked. */
@Tag("integration")
@Tag("document")
@Isolated("Uses per-thread static servlet/security bindings around real JPA transactions")
class MetadataDocumentPersistenceIntegrationTest extends CarlosTestBase {
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DocumentDao documents;
    @Autowired CtlDocumentDao links;
    @Autowired PatientLabRoutingDao patients;
    @Autowired ProviderInboxRoutingDao inbox;
    @PersistenceContext(unitName = "entityManagerFactory") EntityManager entityManager;

    private TransactionTemplate transaction() {
        TransactionTemplate result = new TransactionTemplate(transactionManager);
        result.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        result.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return result;
    }

    private int fixture() {
        return transaction().execute(status -> {
            Document document = new Document(); document.setDocfilename("metadata-owned.pdf");
            document.setDocdesc("original metadata"); document.setDoctype("Lab"); document.setStatus('A');
            document.setDoccreator("991811"); document.setResponsible("991811");
            document.setContenttype("application/pdf"); document.setPublic1(0); document.setNumberofpages(3);
            document.setRestrictToProgram(false); documents.persist(document); entityManager.flush();
            CtlDocument link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic", 991810, document.getId()));
            link.setStatus("A"); links.persist(link);
            return document.getId();
        });
    }

    private void cleanup(int id) {
        transaction().executeWithoutResult(status -> {
            for (ProviderInboxItem item : inbox.getProvidersWithRoutingForDocument("DOC", id)) inbox.remove(item.getId());
            for (PatientLabRouting route : patients.findByLabNoAndLabType(id, "DOC")) patients.remove(route.getId());
            for (CtlDocument link : links.findByDocumentNoAndModule(id, "demographic")) links.remove(link.getId());
            documents.remove(id);
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T intercept(Class<T> type, T bean, java.util.function.BiFunction<String, Object[], Runnable> after) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Runnable callback = after.apply(method.getName(), args);
            try {
                Object result = method.invoke(bean, args);
                if (callback != null) callback.run();
                return result;
            } catch (InvocationTargetException error) {throw error.getCause();}
        });
    }

    private MockHttpServletResponse save(int id, String providerNo, String description,
                                         DocumentDao documentBean, PatientLabRoutingDao patientBean, boolean ajax, boolean routeProvider) {
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setMethod("POST");
        request.setParameter("documentId", String.valueOf(id)); request.setParameter("demog", "991810");
        request.setParameter("documentDescription", description); request.setParameter("docType", "Consult");
        request.setParameter("observationDate", "2026-09-28");
        if (routeProvider) request.setParameter("flagproviders", providerNo);
        Provider provider = new Provider(); provider.setProviderNo(providerNo);
        LoggedInInfo info = new LoggedInInfo(); info.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
        try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class);
             MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedStatic<LogAction> audit = mockStatic(LogAction.class)) {
            spring.when(() -> SpringUtils.getBean(any(Class.class))).thenAnswer(call -> {
                Class<?> type = call.getArgument(0);
                if (type == DocumentDao.class) return documentBean;
                if (type == PatientLabRoutingDao.class) return patientBean;
                if (type == SecurityInfoManager.class) return security;
                return applicationContext.getBean(type);
            });
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            ManageDocument2Action action = new ManageDocument2Action();
            if (ajax) action.documentUpdateAjax(); else action.documentUpdate();
            return response;
        }
    }

    @Test void failureAfterRealProviderAndPatientRoutingRollsBackMetadataAndBothRoutes() throws Exception {
        int id = fixture();
        AtomicBoolean persisted = new AtomicBoolean();
        PatientLabRoutingDao failing = intercept(PatientLabRoutingDao.class, patients, (method, arguments) ->
            "persist".equals(method) ? () -> {
                entityManager.flush();
                assertThat(inbox.getProvidersWithRoutingForDocument("DOC", id)).isNotEmpty();
                assertThat(patients.findByLabNoAndLabType(id, "DOC")).hasSize(1);
                assertThat(documents.find(id).getDocdesc()).isEqualTo("would roll back");
                persisted.set(true);
                throw new IllegalStateException("Injected failure after real route persistence");
            } : null);
        try {
            MockHttpServletResponse response = save(id, "991811", "would roll back", documents, failing, false, true);
            assertThat(response.getStatus()).isEqualTo(500); assertThat(persisted).isTrue();
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).path("accepted").asBoolean()).isFalse();
            transaction().executeWithoutResult(status -> {
                assertThat(documents.find(id).getDocdesc()).isEqualTo("original metadata");
                assertThat(documents.find(id).getDoctype()).isEqualTo("Lab");
                assertThat(patients.findByLabNoAndLabType(id, "DOC")).isEmpty();
                assertThat(inbox.getProvidersWithRoutingForDocument("DOC", id)).isEmpty();
                assertThat(links.findByDocumentNoAndModule(id, "demographic")).hasSize(1);
            });
        } finally {cleanup(id);}
    }

    @Test void twoIndependentSessionsWaitOnRealRowLockAndCreateExactlyOneDocPatientRoute() throws Exception {
        int id = fixture();
        CountDownLatch firstLocked = new CountDownLatch(1), releaseFirst = new CountDownLatch(1), secondAttempt = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        DocumentDao first = intercept(DocumentDao.class, documents, (method, args) -> "findForPageMutation".equals(method) ? () -> {
            firstLocked.countDown();
            try {if (!releaseFirst.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("First lock release timed out");}
            catch (InterruptedException interrupted) {Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted);}
        } : null);
        DocumentDao second = intercept(DocumentDao.class, documents, (method, args) -> {
            if ("findForPageMutation".equals(method)) secondAttempt.countDown();
            return null;
        });
        try {
            Future<MockHttpServletResponse> a = workers.submit(() -> save(id, "991811", "first session", first, patients, true, false));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<MockHttpServletResponse> b = workers.submit(() -> save(id, "991812", "second session", second, patients, true, false));
            assertThat(secondAttempt.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> b.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            releaseFirst.countDown();
            assertThat(a.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
            assertThat(b.get(5, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
            transaction().executeWithoutResult(status -> {
                assertThat(patients.findByLabNoAndLabType(id, "DOC")).hasSize(1)
                        .allSatisfy(route -> assertThat(route.getDemographicNo()).isEqualTo(991810));
                assertThat(documents.find(id).getDocdesc()).isEqualTo("second session");
                assertThat(links.findByDocumentNoAndModule(id, "demographic")).hasSize(1);
            });
        } finally {
            releaseFirst.countDown(); workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); cleanup(id);
        }
    }
}
