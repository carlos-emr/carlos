package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("dao")
@ExtendWith(MockitoExtension.class)
class SmsTransactionDaoImplUnitTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-06-08T12:00:00Z");

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<SmsTransaction> query;

    @Test
    @DisplayName("findByDemographicNo skips querying when demographic is missing")
    void shouldReturnEmptyList_whenDemographicIsMissing() {
        SmsTransactionDaoImpl dao = newDao();

        List<SmsTransaction> transactions = dao.findByDemographicNo(null, 100);

        assertThat(transactions).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(SmsTransaction.class));
    }

    @Test
    @DisplayName("findByDemographicNo uses default limit for invalid limits")
    void shouldUseDefaultLimit_whenDemographicLimitIsInvalid() {
        SmsTransactionDaoImpl dao = newDao();
        when(entityManager.createQuery(anyString(), eq(SmsTransaction.class))).thenReturn(query);
        when(query.setParameter("demographicNo", 123)).thenReturn(query);
        when(query.setMaxResults(100)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        List<SmsTransaction> transactions = dao.findByDemographicNo(123, 0);

        assertThat(transactions).isEmpty();
        verify(query).setMaxResults(100);
    }

    @Test
    @DisplayName("findByDemographicNo caps overly large limits")
    void shouldCapLimit_whenDemographicLimitIsTooLarge() {
        SmsTransactionDaoImpl dao = newDao();
        when(entityManager.createQuery(anyString(), eq(SmsTransaction.class))).thenReturn(query);
        when(query.setParameter("demographicNo", 123)).thenReturn(query);
        when(query.setMaxResults(500)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        List<SmsTransaction> transactions = dao.findByDemographicNo(123, 5_000);

        assertThat(transactions).isEmpty();
        verify(query).setMaxResults(500);
    }

    @Test
    @DisplayName("findByProviderMessageId skips blank SMS provider ids")
    void shouldReturnEmptyOptional_whenProviderMessageIdIsBlank() {
        SmsTransactionDaoImpl dao = newDao();

        Optional<SmsTransaction> transaction = dao.findByProviderMessageId(SmsProviderType.STUB, " ");

        assertThat(transaction).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(SmsTransaction.class));
    }

    @Test
    @DisplayName("findByProviderMessageId binds SMS provider lookup parameters")
    void shouldBindParameters_whenFindingProviderMessage() {
        SmsTransactionDaoImpl dao = newDao();
        SmsTransaction expected = new SmsTransaction();
        when(entityManager.createQuery(anyString(), eq(SmsTransaction.class))).thenReturn(query);
        when(query.setParameter("providerType", SmsProviderType.STUB)).thenReturn(query);
        when(query.setParameter("providerMessageId", "provider-1")).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(expected));

        Optional<SmsTransaction> transaction = dao.findByProviderMessageId(SmsProviderType.STUB, "provider-1");

        assertThat(transaction).containsSame(expected);
        verify(query).setParameter("providerType", SmsProviderType.STUB);
        verify(query).setParameter("providerMessageId", "provider-1");
        verify(query).setMaxResults(1);
    }

    @Test
    @DisplayName("findByClientReferenceId skips blank client references")
    void shouldReturnEmptyOptional_whenClientReferenceIdIsBlank() {
        SmsTransactionDaoImpl dao = newDao();

        Optional<SmsTransaction> transaction = dao.findByClientReferenceId(SmsProviderType.STUB, " ");

        assertThat(transaction).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(SmsTransaction.class));
    }

    @Test
    @DisplayName("findByClientReferenceId binds SMS provider and client reference lookup parameters")
    void shouldBindParameters_whenFindingClientReference() {
        SmsTransactionDaoImpl dao = newDao();
        SmsTransaction expected = new SmsTransaction();
        when(entityManager.createQuery(anyString(), eq(SmsTransaction.class))).thenReturn(query);
        when(query.setParameter("providerType", SmsProviderType.STUB)).thenReturn(query);
        when(query.setParameter("clientReferenceId", "sms-transaction-1")).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(expected));

        Optional<SmsTransaction> transaction = dao.findByClientReferenceId(SmsProviderType.STUB, "sms-transaction-1");

        assertThat(transaction).containsSame(expected);
        verify(query).setParameter("providerType", SmsProviderType.STUB);
        verify(query).setParameter("clientReferenceId", "sms-transaction-1");
        verify(query).setMaxResults(1);
    }

    @Test
    @DisplayName("claimDueOutboundQueue skips querying when SMS provider is missing")
    void shouldReturnEmptyList_whenQueueProviderIsMissing() {
        SmsTransactionDaoImpl dao = newDao();

        List<SmsTransaction> transactions = dao.claimDueOutboundQueue(null, copyOfFixedNow(), 100);

        assertThat(transactions).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(SmsTransaction.class));
    }

    @Test
    @DisplayName("claimDueOutboundQueue locks due rows and marks them sending with a claim token")
    void shouldLockAndMarkSending_whenClaimingDueQueue() {
        SmsTransactionDaoImpl dao = newDao();
        Date now = copyOfFixedNow();
        SmsTransaction due = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        ReflectionTestUtils.setField(due, "id", 42L);
        stubTypedQuery(List.of(due));

        List<SmsTransaction> transactions = dao.claimDueOutboundQueue(SmsProviderType.STUB, now, 5_000);

        assertThat(transactions).singleElement().isSameAs(due);
        assertThat(due.getStatus()).isEqualTo(SmsStatus.SENDING);
        assertThat(due.getAttemptCount()).isEqualTo(1);
        assertThat(due.getClaimToken()).isNotBlank();
        assertThat(due.getClientReferenceId()).isEqualTo("sms-transaction-42");
        verify(query).setLockMode(LockModeType.PESSIMISTIC_WRITE);
        verify(query).setMaxResults(500); // limit capped at MAX_LIMIT
        verify(query).setParameter("status", SmsStatus.QUEUED);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("claimStaleOutboundSendingForRecovery locks stale sending rows and tags them with a claim token")
    void shouldLockAndTagStaleSending_whenRecovering() {
        SmsTransactionDaoImpl dao = newDao();
        Date staleBefore = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        Date recoveryAt = Date.from(Instant.parse("2026-06-08T12:05:00Z"));
        SmsTransaction stale = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        ReflectionTestUtils.setField(stale, "id", 42L);
        stale.markSending(Date.from(Instant.parse("2026-06-08T11:00:00Z")));
        stubTypedQuery(List.of(stale));

        List<SmsTransaction> transactions = dao.claimStaleOutboundSendingForRecovery(
                SmsProviderType.STUB,
                staleBefore,
                recoveryAt,
                10
        );

        assertThat(transactions).singleElement().isSameAs(stale);
        assertThat(stale.getStatus()).isEqualTo(SmsStatus.SENDING);
        assertThat(stale.getLastAttemptAt()).isEqualTo(recoveryAt);
        assertThat(stale.getClaimToken()).isNotBlank();
        assertThat(stale.getClientReferenceId()).isEqualTo("sms-transaction-42");
        verify(query).setLockMode(LockModeType.PESSIMISTIC_WRITE);
        verify(query).setMaxResults(10);
        verify(query).setParameter("status", SmsStatus.SENDING);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    private void stubTypedQuery(List<SmsTransaction> results) {
        when(entityManager.createQuery(anyString(), eq(SmsTransaction.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.setLockMode(any(LockModeType.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(results);
    }

    private SmsTransactionDaoImpl newDao() {
        SmsTransactionDaoImpl dao = new SmsTransactionDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        return dao;
    }

    private static Date copyOfFixedNow() {
        return Date.from(FIXED_NOW);
    }
}
