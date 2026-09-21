package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("integration")
@Tag("service")
@Isolated
class SmsSendTransactionBoundaryIntegrationTest extends CarlosTestBase {
    @Autowired
    private SmsTransactionDao dao;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceUnit(unitName = "entityManagerFactory")
    private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldCommitClaimBeforeProviderCall_whenCallerHasAnOuterTransaction() {
        AtomicReference<Long> sentId = new AtomicReference<>();
        SmsTransactionService recorder = (SmsTransactionService) transactional(new JpaSmsTransactionService(
                dao, mock(ApplicationEventPublisher.class), transactionManager));
        SmsProviderClient provider = new StubSmsProviderClient() {
            @Override
            public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                Long id = Long.valueOf(clientReferenceId.substring("sms-transaction-".length()));
                sentId.set(id);
                try (EntityManager observer = entityManagerFactory.createEntityManager()) {
                    SmsTransaction committed = observer.find(SmsTransaction.class, id);
                    assertThat(committed).isNotNull();
                    assertThat(committed.getStatus()).isEqualTo(SmsStatus.SENDING);
                }
                return super.send(command, clientReferenceId);
            }
        };
        SmsSendService service = (SmsSendService) transactional(new SmsSendService(new SmsSendValidator(),
                command -> SmsConsentDecisionDto.permitted(SmsConsentStatus.OPT_IN, 4321, Instant.parse("2026-09-01T14:30:00Z")), new SmsProviderClientResolver(List.of(provider)), recorder,
                type -> true, new SmsDefaultProviderResolver(() -> "STUB")));
        try {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(service.send(SmsSendCommand.patientMessage(123, "416-555-1212", "synthetic boundary test", "999998"))
                    .status()).isEqualTo(SmsStatus.SENT);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        } finally {
            if (sentId.get() != null) {
                try (EntityManager cleanup = entityManagerFactory.createEntityManager()) {
                    cleanup.getTransaction().begin();
                    cleanup.createQuery("DELETE FROM SmsTransaction t WHERE t.id = :id")
                            .setParameter("id", sentId.get()).executeUpdate();
                    cleanup.getTransaction().commit();
                }
            }
        }
    }

    private Object transactional(Object target) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return factory.getProxy();
    }
}
