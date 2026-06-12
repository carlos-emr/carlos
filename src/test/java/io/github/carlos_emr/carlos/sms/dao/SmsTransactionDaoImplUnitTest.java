package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private Query updateQuery;

    @Mock
    private Query selectQuery;

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
    @DisplayName("claimDueOutboundQueue skips querying when SMS provider is missing")
    void shouldReturnEmptyList_whenQueueProviderIsMissing() {
        SmsTransactionDaoImpl dao = newDao();

        List<SmsTransaction> transactions = dao.claimDueOutboundQueue(null, copyOfFixedNow(), 100);

        assertThat(transactions).isEmpty();
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("claimDueOutboundQueue atomically updates due rows and reads them back by token")
    void shouldBindParameters_whenFindingDueQueue() {
        SmsTransactionDaoImpl dao = newDao();
        Date now = copyOfFixedNow();
        SmsTransaction expected = new SmsTransaction();
        when(entityManager.createNativeQuery(anyString())).thenReturn(updateQuery);
        when(entityManager.createNativeQuery(anyString(), eq(SmsTransaction.class))).thenReturn(selectQuery);
        when(updateQuery.setParameter(1, "SENDING")).thenReturn(updateQuery);
        when(updateQuery.setParameter(2, now)).thenReturn(updateQuery);
        when(updateQuery.setParameter(3, now)).thenReturn(updateQuery);
        when(updateQuery.setParameter(eq(4), anyString())).thenReturn(updateQuery);
        when(updateQuery.setParameter(5, "OUTBOUND")).thenReturn(updateQuery);
        when(updateQuery.setParameter(6, "STUB")).thenReturn(updateQuery);
        when(updateQuery.setParameter(7, "QUEUED")).thenReturn(updateQuery);
        when(updateQuery.setParameter(8, now)).thenReturn(updateQuery);
        when(updateQuery.setParameter(9, 500)).thenReturn(updateQuery);
        when(updateQuery.executeUpdate()).thenReturn(1);
        when(selectQuery.setParameter(eq(1), anyString())).thenReturn(selectQuery);
        when(selectQuery.getResultList()).thenReturn(List.of(expected));

        List<SmsTransaction> transactions = dao.claimDueOutboundQueue(SmsProviderType.STUB, now, 5_000);

        assertThat(transactions).singleElement().isSameAs(expected);
        ArgumentCaptor<String> updateSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(updateSqlCaptor.capture());
        assertThat(updateSqlCaptor.getValue())
                .contains("UPDATE sms_transaction")
                .contains("SET status = ?1")
                .contains("attempt_count = attempt_count + 1")
                .contains("claim_token = ?4")
                .contains("ORDER BY created_at ASC")
                .contains("LIMIT ?9");
        ArgumentCaptor<String> selectSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(selectSqlCaptor.capture(), eq(SmsTransaction.class));
        assertThat(selectSqlCaptor.getValue())
                .contains("WHERE claim_token = ?1")
                .contains("ORDER BY created_at ASC");
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(updateQuery).setParameter(eq(4), tokenCaptor.capture());
        verify(selectQuery).setParameter(1, tokenCaptor.getValue());
        verify(updateQuery).setParameter(1, "SENDING");
        verify(updateQuery).setParameter(5, "OUTBOUND");
        verify(updateQuery).setParameter(6, "STUB");
        verify(updateQuery).setParameter(7, "QUEUED");
        verify(updateQuery).setParameter(9, 500);
    }

    @Test
    @DisplayName("claimStaleOutboundSendingForRecovery atomically updates stale rows and reads them back by token")
    void shouldBindParameters_whenFindingStaleSending() {
        SmsTransactionDaoImpl dao = newDao();
        Date staleBefore = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        Date recoveryAt = Date.from(Instant.parse("2026-06-08T12:05:00Z"));
        SmsTransaction expected = new SmsTransaction();
        when(entityManager.createNativeQuery(anyString())).thenReturn(updateQuery);
        when(entityManager.createNativeQuery(anyString(), eq(SmsTransaction.class))).thenReturn(selectQuery);
        when(updateQuery.setParameter(1, recoveryAt)).thenReturn(updateQuery);
        when(updateQuery.setParameter(2, recoveryAt)).thenReturn(updateQuery);
        when(updateQuery.setParameter(eq(3), anyString())).thenReturn(updateQuery);
        when(updateQuery.setParameter(4, "OUTBOUND")).thenReturn(updateQuery);
        when(updateQuery.setParameter(5, "STUB")).thenReturn(updateQuery);
        when(updateQuery.setParameter(6, "SENDING")).thenReturn(updateQuery);
        when(updateQuery.setParameter(7, staleBefore)).thenReturn(updateQuery);
        when(updateQuery.setParameter(8, 10)).thenReturn(updateQuery);
        when(updateQuery.executeUpdate()).thenReturn(1);
        when(selectQuery.setParameter(eq(1), anyString())).thenReturn(selectQuery);
        when(selectQuery.getResultList()).thenReturn(List.of(expected));

        List<SmsTransaction> transactions = dao.claimStaleOutboundSendingForRecovery(
                SmsProviderType.STUB,
                staleBefore,
                recoveryAt,
                10
        );

        assertThat(transactions).singleElement().isSameAs(expected);
        ArgumentCaptor<String> updateSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(updateSqlCaptor.capture());
        assertThat(updateSqlCaptor.getValue())
                .contains("UPDATE sms_transaction")
                .contains("last_attempt_at = ?1")
                .contains("claim_token = ?3")
                .contains("last_attempt_at IS NOT NULL")
                .contains("ORDER BY last_attempt_at ASC")
                .contains("LIMIT ?8");
        ArgumentCaptor<String> selectSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(selectSqlCaptor.capture(), eq(SmsTransaction.class));
        assertThat(selectSqlCaptor.getValue())
                .contains("WHERE claim_token = ?1")
                .contains("ORDER BY id ASC");
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(updateQuery).setParameter(eq(3), tokenCaptor.capture());
        verify(selectQuery).setParameter(1, tokenCaptor.getValue());
        verify(updateQuery).setParameter(1, recoveryAt);
        verify(updateQuery).setParameter(4, "OUTBOUND");
        verify(updateQuery).setParameter(5, "STUB");
        verify(updateQuery).setParameter(6, "SENDING");
        verify(updateQuery).setParameter(7, staleBefore);
        verify(updateQuery).setParameter(8, 10);
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
