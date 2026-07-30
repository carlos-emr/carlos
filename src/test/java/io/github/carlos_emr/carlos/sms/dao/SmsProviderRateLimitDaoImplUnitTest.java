package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.model.SmsProviderRateLimit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("dao")
@ExtendWith(MockitoExtension.class)
class SmsProviderRateLimitDaoImplUnitTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-06-08T12:00:00Z");

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<SmsProviderRateLimit> query;

    @Mock
    private Query nativeQuery;

    @Test
    @DisplayName("insertIfMissing skips insert when SMS provider is missing")
    void shouldSkipInsert_whenProviderIsMissing() {
        SmsProviderRateLimitDaoImpl dao = newDao();

        dao.insertIfMissing(null, fixedDate());

        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("insertIfMissing uses insert-ignore for SMS provider limiter rows")
    void shouldInsertIgnore_whenProviderLimiterIsMissing() {
        SmsProviderRateLimitDaoImpl dao = newDao();
        Date now = fixedDate();
        when(entityManager.createNativeQuery(contains("INSERT IGNORE INTO sms_provider_rate_limit")))
                .thenReturn(nativeQuery);
        when(nativeQuery.setParameter(1, SmsProviderType.VOIPMS.name())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(2, now)).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(3, now)).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(4, now)).thenReturn(nativeQuery);
        when(nativeQuery.executeUpdate()).thenReturn(1);

        dao.insertIfMissing(SmsProviderType.VOIPMS, now);

        verify(nativeQuery).setParameter(1, SmsProviderType.VOIPMS.name());
        verify(nativeQuery).setParameter(2, now);
        verify(nativeQuery).setParameter(3, now);
        verify(nativeQuery).setParameter(4, now);
        verify(nativeQuery).executeUpdate();
    }

    @Test
    @DisplayName("findByProviderTypeForUpdate skips querying when SMS provider is missing")
    void shouldReturnEmptyOptional_whenProviderIsMissing() {
        SmsProviderRateLimitDaoImpl dao = newDao();

        Optional<SmsProviderRateLimit> rateLimit = dao.findByProviderTypeForUpdate(null);

        assertThat(rateLimit).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(SmsProviderRateLimit.class));
    }

    @Test
    @DisplayName("findByProviderTypeForUpdate binds SMS provider and locks the limiter row")
    void shouldBindProviderAndLockRow_whenFindingProviderLimiter() {
        SmsProviderRateLimitDaoImpl dao = newDao();
        SmsProviderRateLimit expected = SmsProviderRateLimit.forProvider(SmsProviderType.STUB, fixedDate());
        when(entityManager.createQuery(anyString(), eq(SmsProviderRateLimit.class))).thenReturn(query);
        when(query.setParameter("providerType", SmsProviderType.STUB)).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(expected));

        Optional<SmsProviderRateLimit> rateLimit = dao.findByProviderTypeForUpdate(SmsProviderType.STUB);

        assertThat(rateLimit).containsSame(expected);
        verify(query).setParameter("providerType", SmsProviderType.STUB);
        verify(query).setMaxResults(1);
        verify(query).setLockMode(LockModeType.PESSIMISTIC_WRITE);
    }

    private SmsProviderRateLimitDaoImpl newDao() {
        SmsProviderRateLimitDaoImpl dao = new SmsProviderRateLimitDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        return dao;
    }

    private static Date fixedDate() {
        return Date.from(FIXED_NOW);
    }
}
