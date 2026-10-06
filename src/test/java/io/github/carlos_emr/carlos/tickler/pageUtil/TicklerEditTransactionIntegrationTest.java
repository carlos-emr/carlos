/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerUpdateDao;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.TicklerUpdate;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.managers.TicklerManagerImpl;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.TicklerCommentDao;
import io.github.carlos_emr.carlos.commn.model.TicklerComment;
import io.github.carlos_emr.carlos.documentManager.TicklerAttachmentService;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TicklerEditTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private TicklerDao ticklers;
    @Autowired private TicklerUpdateDao history;
    @Autowired private TicklerCommentDao comments;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager entities;
    private record Fixture(int demographic, String provider) {}
    private final java.util.Map<Integer, Fixture> fixtures = new java.util.HashMap<>();
    private final LoggedInInfo login = mock(LoggedInInfo.class);

    private static final class TestableEdit extends EditTickler2Action {
        @Override public String getText(String key) { return key; }
    }

    @ParameterizedTest
    @ValueSource(strings = {"history", "comment", "rollbackOnly", "afterCommit"})
    void shouldKeepWritesAtomicAndRetainTheDraft_whenCompletionFails(String failure) {
        int id = seed();
        try (var audit = mockStatic(LogAction.class)) {
            AtomicBoolean fail = new AtomicBoolean(true);
            String provider = fixtures.get(id).provider();
            when(login.getLoggedInProviderNo()).thenReturn(provider);
            var security = mock(SecurityInfoManager.class);
            when(security.hasPrivilege(eq(login), eq("_tickler"), anyString(), isNull())).thenReturn(true);
            var historyWrites = mock(TicklerUpdateDao.class);
            doAnswer(call -> {
                history.persist((TicklerUpdate) call.getArgument(0)); entities.flush();
                if (fail.get() && "history".equals(failure)) throw new IllegalStateException("Injected history failure");
                return null;
            }).when(historyWrites).persist(any());
            var commentWrites = mock(TicklerCommentDao.class);
            doAnswer(call -> {
                comments.persist((TicklerComment) call.getArgument(0)); entities.flush();
                if (fail.get() && "comment".equals(failure)) throw new IllegalStateException("Injected comment failure");
                return null;
            }).when(commentWrites).persist(any());
            TicklerManager realManager = manager(security, historyWrites, commentWrites);
            TicklerManager manager = mock(TicklerManager.class);
            when(manager.getTicklerForUpdate(login, id)).thenAnswer(call -> realManager.getTicklerForUpdate(login, id));
            when(manager.updateTickler(eq(login), any())).thenAnswer(call -> {
                boolean result = realManager.updateTickler(login, call.getArgument(1));
                entities.flush();
                if (fail.get() && "afterCommit".equals(failure)) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCommit() { throw new IllegalStateException("Injected acknowledgement failure"); }
                    });
                }
                return result;
            });
            PlatformTransactionManager completion = new PlatformTransactionManager() {
                @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return transactions.getTransaction(definition); }
                @Override public void commit(TransactionStatus status) {
                    if (fail.get() && "rollbackOnly".equals(failure)) status.setRollbackOnly();
                    transactions.commit(status);
                }
                @Override public void rollback(TransactionStatus status) { transactions.rollback(status); }
            };
            String version = new TransactionTemplate(transactions).execute(status -> TicklerEditVersion.of(ticklers.find(id)));
            Date originalDate = new TransactionTemplate(transactions).execute(status -> ticklers.find(id).getUpdateDate());
            boolean committed = "afterCommit".equals(failure);
            var request = new MockHttpServletRequest("POST", "/tickler/EditTickler");
            request.setParameter("ticklerNo", String.valueOf(id)); request.setParameter("method", "editTickler");
            request.setParameter(TicklerEditVersion.PARAMETER, version);
            request.setParameter("status", committed ? "A" : "C"); request.setParameter("priority", "Normal");
            request.setParameter("assignedToProviders", provider); request.setParameter("xml_appointment_date", "2026-08-01");
            request.setParameter("newMessage", "Retained owned draft");
            var response = new MockHttpServletResponse();
            var attachments = mock(TicklerAttachmentService.class);
            try (var spring = mockStatic(SpringUtils.class);
                 var servlet = mockStatic(ServletActionContext.class);
                 var loggedIn = mockStatic(LoggedInInfo.class)) {
                spring.when(() -> SpringUtils.getBean(TicklerManager.class)).thenReturn(manager);
                spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
                spring.when(() -> SpringUtils.getBean(TicklerAttachmentService.class)).thenReturn(attachments);
                spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(completion);
                servlet.when(ServletActionContext::getRequest).thenReturn(request);
                servlet.when(ServletActionContext::getResponse).thenReturn(response);
                loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
                assertThat(new TestableEdit().execute()).isEqualTo("conflict");
                assertThat(response.getStatus()).isEqualTo(500);
                assertThat(request.getParameter("newMessage")).isEqualTo("Retained owned draft");
                assertThat(request.getParameter(TicklerEditVersion.PARAMETER)).isEqualTo(version);
                assertRows(id, Tickler.STATUS.A, committed ? 1 : 0, committed ? 1 : 0);
                fail.set(false); response.reset();
                if (committed) {
                    // A same-timestamp comment-only commit must still invalidate its original form.
                    new TransactionTemplate(transactions).executeWithoutResult(status ->
                            entities.createQuery("update Tickler t set t.updateDate=:date where t.id=:id")
                                    .setParameter("date", originalDate).setParameter("id", id).executeUpdate());
                    assertThat(new TestableEdit().execute()).isEqualTo("conflict");
                    assertThat(response.getStatus()).isEqualTo(409);
                    assertRows(id, Tickler.STATUS.A, 1, 1);
                } else {
                    assertThat(new TestableEdit().execute()).isEqualTo("close");
                    assertRows(id, Tickler.STATUS.C, 1, 2);
                }
                verifyNoInteractions(attachments);
            }
        } finally { cleanup(id); }
    }

    @Test
    void shouldRefreshAlreadyLoadedHistory_whenAnotherTransactionAppendsAComment() {
        int id = seed();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Tickler stale = ticklers.find(id);
                String original = TicklerEditVersion.of(stale);
                TransactionTemplate other = new TransactionTemplate(transactions);
                other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                other.executeWithoutResult(inner -> {
                    TicklerComment comment = new TicklerComment(); comment.setTicklerNo(id);
                    comment.setMessage("Concurrent owned comment"); comment.setProviderNo(fixtures.get(id).provider());
                    comments.persist(comment);
                });
                assertThat(TicklerEditVersion.of(ticklers.findForUpdate(id))).isNotEqualTo(original);
            });
        } finally { cleanup(id); }
    }

    private TicklerManager manager(SecurityInfoManager security, TicklerUpdateDao updates, TicklerCommentDao commentWrites) {
        TicklerManagerImpl target = new TicklerManagerImpl();
        ReflectionTestUtils.setField(target, "ticklerDao", ticklers);
        ReflectionTestUtils.setField(target, "ticklerUpdateDao", updates);
        ReflectionTestUtils.setField(target, "ticklerCommentDao", commentWrites);
        ReflectionTestUtils.setField(target, "securityInfoManager", security);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (TicklerManager) proxy.getProxy();
    }

    private void assertRows(int id, Tickler.STATUS expected, long commentCount, long updateCount) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(entities.find(Tickler.class, id).getStatus()).isEqualTo(expected);
            assertThat(entities.createQuery("select count(c) from TicklerComment c where c.ticklerNo=:id", Long.class)
                    .setParameter("id", id).getSingleResult()).isEqualTo(commentCount);
            assertThat(entities.createQuery("select count(t) from TicklerUpdate t where t.ticklerNo=:id", Long.class)
                    .setParameter("id", id).getSingleResult()).isEqualTo(updateCount);
        });
    }

    private int seed() {
        return new TransactionTemplate(transactions).execute(status -> {
            Demographic patient = new Demographic();
            patient.setFirstName("Owned"); patient.setLastName("Status fixture");
            patient.setSex("U"); patient.setPatientStatus("AC");
            entities.persist(patient);
            String providerNo = "ed" + UUID.randomUUID().toString().substring(0, 4);
            assertThat(entities.find(Provider.class, providerNo)).isNull();
            Provider provider = new Provider(providerNo, "Status fixture", "doctor", "M", "GP", "Owned");
            provider.setStatus("1");
            entities.persist(provider);
            Tickler row = new Tickler();
            row.setDemographicNo(patient.getDemographicNo()); row.setCreator(providerNo);
            row.setTaskAssignedTo(providerNo); row.setMessage("owned-edit-" + UUID.randomUUID());
            row.setServiceDate(TicklerFormDate.parse("2026-08-01"));
            ticklers.persist(row); entities.flush();
            fixtures.put(row.getId(), new Fixture(patient.getDemographicNo(), providerNo));
            return row.getId();
        });
    }

    private void cleanup(int id) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            entities.createQuery("delete from TicklerComment c where c.ticklerNo=:id").setParameter("id", id).executeUpdate();
            entities.createQuery("delete from TicklerUpdate t where t.ticklerNo=:id").setParameter("id", id).executeUpdate();
            entities.createQuery("delete from Tickler t where t.id=:id").setParameter("id", id).executeUpdate();
            Fixture fixture = fixtures.get(id);
            if (fixture != null) {
                entities.createQuery("delete from Demographic d where d.demographicNo=:id").setParameter("id", fixture.demographic()).executeUpdate();
                entities.createQuery("delete from Provider p where p.providerNo=:id").setParameter("id", fixture.provider()).executeUpdate();
            }
        });
        fixtures.remove(id);
    }
}
