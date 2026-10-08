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

@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TicklerStatusTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private TicklerDao ticklers;
    @Autowired private TicklerUpdateDao history;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager entities;
    private record Fixture(int demographic, String provider) {}
    private final java.util.Map<Integer, Fixture> fixtures = new java.util.HashMap<>();
    private final LoggedInInfo login = mock(LoggedInInfo.class);

    @Test
    void shouldRefuseStaleAndRepeatedTransitions_withoutChangingStatusOrHistory() {
        int id = seed();
        try (var audit = mockStatic(LogAction.class)) {
            TicklerManager manager = manager(history, true);
            assertThat(manager.updateStatusIfCurrent(login, id, "999998", Tickler.STATUS.A, Tickler.STATUS.D)).isTrue();
            assertThat(manager.updateStatusIfCurrent(login, id, "999998", Tickler.STATUS.A, Tickler.STATUS.C)).isFalse();
            assertThat(manager.updateStatusIfCurrent(login, id, "999998", null, Tickler.STATUS.C)).isFalse();
            assertRows(id, Tickler.STATUS.D, 1);
            assertThat(manager.updateStatusIfCurrent(login, id, "999998", Tickler.STATUS.D, Tickler.STATUS.C)).isTrue();
            assertThat(manager.updateStatusIfCurrent(login, id, "999998", Tickler.STATUS.C, Tickler.STATUS.C)).isTrue();
            assertRows(id, Tickler.STATUS.C, 2);
        } finally { cleanup(id); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"history", "audit"})
    void shouldRollbackFlushedStatusAndHistory_whenTheWriteFails(String failure) {
        int id = seed();
        try (var audit = mockStatic(LogAction.class)) {
            TicklerUpdateDao writes = mock(TicklerUpdateDao.class);
            doAnswer(call -> {
                history.persist((TicklerUpdate) call.getArgument(0));
                entities.flush();
                if ("history".equals(failure)) throw new IllegalStateException("Injected flushed history failure");
                return null;
            }).when(writes).persist(any());
            if ("audit".equals(failure)) {
                audit.when(() -> LogAction.addLogSynchronous(eq(login), eq("TicklerManager.updateStatus"), anyString()))
                        .thenAnswer(call -> { entities.flush(); throw new IllegalStateException("Injected audit failure"); });
            }
            TicklerManager manager = manager(writes, true);
            assertThatThrownBy(() -> manager.updateStatusIfCurrent(login, id, "999998", Tickler.STATUS.A, Tickler.STATUS.C))
                    .isInstanceOf(IllegalStateException.class);
            assertRows(id, Tickler.STATUS.A, 0);
        } finally { cleanup(id); }
    }

    @Test
    void shouldRefreshAnAlreadyManagedTickler_beforeComparingTheExpectedStatus() {
        int id = seed();
        try (var audit = mockStatic(LogAction.class)) {
            TicklerManager manager = manager(history, true);
            new TransactionTemplate(transactions).executeWithoutResult(outer -> {
                Tickler stale = entities.find(Tickler.class, id);
                assertThat(stale.getStatus()).isEqualTo(Tickler.STATUS.A);
                TransactionTemplate other = new TransactionTemplate(transactions);
                other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                other.executeWithoutResult(inner -> {
                    Tickler changed = ticklers.findForUpdate(id);
                    changed.setStatus(Tickler.STATUS.D);
                    ticklers.merge(changed);
                });
                assertThat(manager.updateStatusIfCurrent(login, id, "999998", Tickler.STATUS.A, Tickler.STATUS.C)).isFalse();
                assertThat(stale.getStatus()).isEqualTo(Tickler.STATUS.D);
            });
            assertRows(id, Tickler.STATUS.D, 0);
        } finally { cleanup(id); }
    }

    @Test
    void shouldRefuseUnauthorizedAndMissingRows_withoutAddingHistory() {
        int id = seed();
        try (var audit = mockStatic(LogAction.class)) {
            TicklerManager denied = manager(history, false);
            assertThatThrownBy(() -> denied.updateStatusIfCurrent(login, id, "999998", Tickler.STATUS.A, Tickler.STATUS.C))
                    .isInstanceOf(RuntimeException.class);
            assertRows(id, Tickler.STATUS.A, 0);
            cleanup(id);
            assertThat(manager(history, true).updateStatusIfCurrent(login, id, "999998", Tickler.STATUS.A, Tickler.STATUS.C)).isFalse();
        } finally { cleanup(id); }
    }

    private TicklerManager manager(TicklerUpdateDao writes, boolean allowed) {
        TicklerManagerImpl target = new TicklerManagerImpl();
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(login), eq("_tickler"), anyString(), isNull())).thenReturn(allowed);
        ReflectionTestUtils.setField(target, "ticklerDao", ticklers);
        ReflectionTestUtils.setField(target, "ticklerUpdateDao", writes);
        ReflectionTestUtils.setField(target, "securityInfoManager", security);
        // Read the production @Transactional annotation; omitting it must break the rollback checks.
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (TicklerManager) proxy.getProxy();
    }

    private int seed() {
        return new TransactionTemplate(transactions).execute(status -> {
            Demographic patient = new Demographic();
            patient.setFirstName("Owned"); patient.setLastName("Status fixture");
            patient.setSex("U"); patient.setPatientStatus("AC");
            entities.persist(patient);
            String providerNo = "st" + UUID.randomUUID().toString().substring(0, 4);
            assertThat(entities.find(Provider.class, providerNo)).isNull();
            Provider provider = new Provider(providerNo, "Status fixture", "doctor", "M", "GP", "Owned");
            provider.setStatus("1");
            entities.persist(provider);
            Tickler row = new Tickler();
            row.setDemographicNo(patient.getDemographicNo()); row.setCreator(providerNo);
            row.setTaskAssignedTo(providerNo); row.setMessage("owned-status-" + UUID.randomUUID());
            ticklers.persist(row); entities.flush();
            fixtures.put(row.getId(), new Fixture(patient.getDemographicNo(), providerNo));
            return row.getId();
        });
    }

    private void assertRows(int id, Tickler.STATUS expected, long count) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(entities.find(Tickler.class, id).getStatus()).isEqualTo(expected);
            assertThat(entities.createQuery("select count(t) from TicklerUpdate t where t.ticklerNo=:id", Long.class)
                    .setParameter("id", id).getSingleResult()).isEqualTo(count);
        });
    }

    private void cleanup(int id) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
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
