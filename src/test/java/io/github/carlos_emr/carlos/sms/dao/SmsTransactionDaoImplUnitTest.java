package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import jakarta.persistence.EntityManager;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("dao")
@ExtendWith(MockitoExtension.class)
class SmsTransactionDaoImplUnitTest {
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
    @DisplayName("findByProviderMessageId skips blank provider ids")
    void shouldReturnEmptyOptional_whenProviderMessageIdIsBlank() {
        SmsTransactionDaoImpl dao = newDao();

        Optional<SmsTransaction> transaction = dao.findByProviderMessageId(SmsProviderType.STUB, " ");

        assertThat(transaction).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(SmsTransaction.class));
    }

    @Test
    @DisplayName("findByProviderMessageId binds provider lookup parameters")
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
    @DisplayName("findDueOutboundQueue skips querying when provider is missing")
    void shouldReturnEmptyList_whenQueueProviderIsMissing() {
        SmsTransactionDaoImpl dao = newDao();

        List<SmsTransaction> transactions = dao.findDueOutboundQueue(null, new Date(), 100);

        assertThat(transactions).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(SmsTransaction.class));
    }

    @Test
    @DisplayName("findDueOutboundQueue binds queue parameters and caps limits")
    void shouldBindParameters_whenFindingDueQueue() {
        SmsTransactionDaoImpl dao = newDao();
        Date now = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        SmsTransaction expected = new SmsTransaction();
        when(entityManager.createQuery(anyString(), eq(SmsTransaction.class))).thenReturn(query);
        when(query.setParameter(eq("direction"), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.setParameter("providerType", SmsProviderType.STUB)).thenReturn(query);
        when(query.setParameter(eq("status"), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.setParameter("now", now)).thenReturn(query);
        when(query.setMaxResults(500)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(expected));

        List<SmsTransaction> transactions = dao.findDueOutboundQueue(SmsProviderType.STUB, now, 5_000);

        assertThat(transactions).singleElement().isSameAs(expected);
        verify(query).setParameter("providerType", SmsProviderType.STUB);
        verify(query).setParameter("now", now);
        verify(query).setMaxResults(500);
    }

    private SmsTransactionDaoImpl newDao() {
        SmsTransactionDaoImpl dao = new SmsTransactionDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        return dao;
    }
}
