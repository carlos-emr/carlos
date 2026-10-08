/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SecurityEditTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private SecurityDao rows;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager entities;

    @Test
    void shouldDeleteAfterDetachedLookup_andRefuseASecondDeletion() throws Exception {
        int id = seed();
        try {
            Security detached = rows.find(id);
            new TransactionTemplate(transactions).executeWithoutResult(status ->
                    assertThat(entities.contains(detached)).isFalse());
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/SecurityDelete");
            request.setParameter("keyword", String.valueOf(id));
            MockHttpServletResponse response = new MockHttpServletResponse();
            CarlosMethodSecurity access = mock(CarlosMethodSecurity.class);
            when(access.hasAdminWrite()).thenReturn(true);
            LoggedInInfo login = mock(LoggedInInfo.class);
            when(login.getLoggedInProviderNo()).thenReturn("999998");
            try (var servlet = mockStatic(ServletActionContext.class);
                 var sessions = mockStatic(LoggedInInfo.class);
                 var audit = mockStatic(LogAction.class)) {
                servlet.when(ServletActionContext::getRequest).thenReturn(request);
                servlet.when(ServletActionContext::getResponse).thenReturn(response);
                sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
                SecurityDelete2Action action = new SecurityDelete2Action(rows, access);
                assertThat(action.execute()).isEqualTo("success");
                assertThat(request.getAttribute("msg"))
                        .isEqualTo("Security entry deleted for user: " + detached.getUserName());
                assertThat(rows.find(id)).isNull();
                audit.verify(() -> LogAction.addLog(eq("999998"), anyString(), anyString(),
                        eq(String.valueOf(id)), anyString()));
                audit.clearInvocations();
                assertThat(action.execute()).isEqualTo("success");
                assertThat(request.getAttribute("msg")).isEqualTo("Security entry not found.");
                audit.verifyNoInteractions();
            }
        } finally { cleanup(id); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"save", "rollbackOnly", "afterCommit"})
    void shouldRetainDraftAndAllowOnlySafeRetry_whenCompletionFails(String failure) throws Exception {
        int id = seed();
        try {
            String original = new TransactionTemplate(transactions).execute(status -> SecurityEditVersion.of(rows.find(id)));
            String name = new TransactionTemplate(transactions).execute(status -> rows.find(id).getUserName());
            AtomicBoolean fail = new AtomicBoolean(true);
            AtomicBoolean injected = new AtomicBoolean(false);
            SecurityDao dao = mock(SecurityDao.class);
            when(dao.findForUpdate(id)).thenAnswer(call -> rows.findForUpdate(id));
            when(dao.saveEntity(any())).thenAnswer(call -> {
                Security saved = rows.saveEntity(call.getArgument(0));
                entities.flush();
                if (fail.get() && "save".equals(failure)) {
                    injected.set(true);
                    throw new IllegalStateException("Injected flushed security save failure");
                }
                if (fail.get() && "afterCommit".equals(failure)) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCommit() {
                            injected.set(true);
                            throw new IllegalStateException("Injected acknowledgement failure");
                        }
                    });
                }
                return saved;
            });
            PlatformTransactionManager completion = new PlatformTransactionManager() {
                @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return transactions.getTransaction(definition); }
                @Override public void rollback(TransactionStatus status) { transactions.rollback(status); }
                @Override public void commit(TransactionStatus status) {
                    if (fail.get() && "rollbackOnly".equals(failure)) { injected.set(true); status.setRollbackOnly(); }
                    transactions.commit(status);
                }
            };
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/SecurityUpdate");
            request.setParameter("security_no", String.valueOf(id)); request.setParameter("user_name", name + "x");
            request.setParameter("provider_no", "999998"); request.setParameter("password", "*********");
            request.setParameter("conPassword", "*********"); request.setParameter("date_ExpireDate", "2100-01-01");
            request.setParameter("forcePasswordReset", "1"); request.setParameter("b_ExpireSet", "1");
            request.setParameter(SecurityEditVersion.PARAMETER, original); request.getSession().setAttribute("user", "999998");
            MockHttpServletResponse response = new MockHttpServletResponse();
            CarlosMethodSecurity access = mock(CarlosMethodSecurity.class); when(access.hasAdminWrite()).thenReturn(true);
            try (var spring = mockStatic(SpringUtils.class); var servlet = mockStatic(ServletActionContext.class);
                 var audit = mockStatic(LogAction.class); var mfa = mockStatic(MfaManager.class);
                 var configuration = mockStatic(CarlosProperties.class)) {
                spring.when(() -> SpringUtils.getBean(SecurityDao.class)).thenReturn(dao);
                spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(completion);
                servlet.when(ServletActionContext::getRequest).thenReturn(request);
                servlet.when(ServletActionContext::getResponse).thenReturn(response);
                configuration.when(CarlosProperties::getInstance).thenReturn(mock(CarlosProperties.class));
                String outcome = new SecurityUpdate2Action(access).execute();
                assertThat(injected).isTrue(); assertThat(outcome).isEqualTo("conflict");
                assertThat(response.getStatus()).isEqualTo(500);
                assertThat(request.getAttribute("securityUpdateCommitted")).isNull();
                assertThat(request.getParameter(SecurityEditVersion.PARAMETER)).isEqualTo(original);
                assertThat(((Security) request.getAttribute("securityEditDraft")).getUserName()).isEqualTo(name + "x");
                boolean committed = "afterCommit".equals(failure);
                assertRow(id, committed ? name + "x" : name, committed);
                fail.set(false); response.reset();
                assertThat(new SecurityUpdate2Action(access).execute()).isEqualTo(committed ? "conflict" : "success");
                assertThat(response.getStatus()).isEqualTo(committed ? 409 : 200);
                assertRow(id, name + "x", true);
            }
        } finally { cleanup(id); }
    }

    @Test
    void shouldRefreshManagedState_whenAnotherTransactionChangesTheResetFlagWithoutTimestamp() {
        int id = seed();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                Security stale = rows.find(id);
                String original = SecurityEditVersion.of(stale);
                TransactionTemplate other = new TransactionTemplate(transactions);
                other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                other.executeWithoutResult(inner -> entities.createQuery("update Security s set s.forcePasswordReset=true where s.id=:id")
                        .setParameter("id", id).executeUpdate());
                Security current = rows.findForUpdate(id);
                assertThat(current.isForcePasswordReset()).isTrue();
                assertThat(SecurityEditVersion.of(current)).isNotEqualTo(original);
            });
        } finally { cleanup(id); }
    }

    private void assertRow(int id, String userName, boolean changed) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Security row = rows.find(id);
            assertThat(row.getUserName()).isEqualTo(userName);
            assertThat(row.isForcePasswordReset()).isEqualTo(changed);
            assertThat(row.getBExpireset()).isEqualTo(changed ? 1 : 0);
            assertThat(row.getPassword()).isEqualTo("owned-password-hash");
            assertThat(row.getPin()).isEqualTo("owned-pin-hash");
            assertThat(entities.createQuery("select count(s) from Security s where s.id=:id", Long.class)
                    .setParameter("id", id).getSingleResult()).isEqualTo(1L);
        });
    }

    private int seed() {
        return new TransactionTemplate(transactions).execute(status -> {
            Security row = new Security(); row.setUserName("owned" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            row.setProviderNo("999998"); row.setPassword("owned-password-hash"); row.setPin("owned-pin-hash");
            row.setBExpireset(0); row.setBLocallockset(1); row.setBRemotelockset(1); row.setForcePasswordReset(false);
            row.setDateExpiredate(java.sql.Date.valueOf("2100-01-01")); rows.persist(row); entities.flush();
            return row.getId();
        });
    }

    private void cleanup(int id) {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                entities.createQuery("delete from Security s where s.id=:id").setParameter("id", id).executeUpdate());
    }
}
