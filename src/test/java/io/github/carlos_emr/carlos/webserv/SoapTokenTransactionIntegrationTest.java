/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.commn.dao.SecurityTokenDao;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@Tag("security")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SoapTokenTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private SecurityTokenDao tokens;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager entityManager;
    private SoapTokenService service;
    private Security account;

    @BeforeEach
    void setup() {
        account = new Security();
        account.setId(987654321);
        account.setPassword("FAKE-hash");
        var target = new SoapTokenService(tokens, mock(SecurityInfoManager.class), Clock.systemUTC(),
                new SecureRandom(), Duration.ofHours(1));
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        service = (SoapTokenService) proxy.getProxy();
        clean();
    }

    @AfterEach
    void clean() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("DELETE FROM SecurityToken t WHERE t.data LIKE 'soap1:987654321:%'").executeUpdate();
            entityManager.createQuery("DELETE FROM OscarLog l WHERE l.securityId = 987654321 AND l.action IN ('SoapToken.issue', 'SoapToken.revokeAll')")
                    .executeUpdate();
        });
    }

    private long tokenCount() {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager.createQuery(
                "SELECT COUNT(t) FROM SecurityToken t WHERE t.data LIKE 'soap1:987654321:%'", Long.class).getSingleResult());
    }

    @Test
    void shouldRollBackThePersistedToken_whenStrictAuditFailsAtTheProxyBoundary() {
        try (var audit = mockStatic(LogAction.class)) {
            audit.when(() -> LogAction.addLogStrict(any(), eq("SoapToken.issue"), isNull(), isNull(), isNull(), anyString()))
                    .thenThrow(new IllegalStateException("FAKE-audit-unavailable"));
            assertThatThrownBy(() -> service.issue(account)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("FAKE-audit-unavailable");
        }
        assertThat(tokenCount()).isZero();
    }

    @Test
    void shouldRestoreTheToken_whenRevocationAuditCannotCommit() {
        String token = service.issue(account);
        var actor = new io.github.carlos_emr.carlos.utility.LoggedInInfo();
        actor.setLoggedInSecurity(account);
        try (var audit = mockStatic(LogAction.class)) {
            audit.when(() -> LogAction.addLogStrict(any(), eq("SoapToken.revokeAll"), isNull(), isNull(), isNull(), anyString()))
                    .thenThrow(new IllegalStateException("FAKE-audit-unavailable"));
            assertThatThrownBy(() -> service.revokeAll(actor, account.getId())).hasMessage("FAKE-audit-unavailable");
        }
        assertThat(tokenCount()).isEqualTo(1);
        assertThat(service.validate(account, token)).isTrue();
        assertThat(service.revokeAll(actor, account.getId())).isEqualTo(1);
        assertThat(service.validate(account, token)).isFalse();
        assertThat(tokenCount()).isZero();
    }

    @Test
    void shouldCommitTheTokenAndAudit_beforeReturningTheCredential() {
        String token = service.issue(account);
        assertThat(tokenCount()).isEqualTo(1);
        assertThat(service.validate(account, token)).isTrue();
        long audits = new TransactionTemplate(transactionManager).execute(status -> entityManager.createQuery(
                "SELECT COUNT(l) FROM OscarLog l WHERE l.securityId = 987654321 AND l.action = 'SoapToken.issue'",
                Long.class).getSingleResult());
        assertThat(audits).isEqualTo(1);
    }
}
