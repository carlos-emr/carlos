/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueCountDto;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueRowDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mock-based checks of the Administration &gt; SMS queue-view queries in {@link SmsTransactionDaoImpl}: guard
 * clauses, outbound-only parameter binding, limits, and the mapping of projection rows. The SQL itself runs
 * against H2 in {@code SmsTransactionDaoQueueViewIntegrationTest}.
 *
 * @since 2026-09-28
 */
@Tag("unit")
@Tag("dao")
@ExtendWith(MockitoExtension.class)
@DisplayName("SmsTransactionDaoImpl queue-view queries")
class SmsTransactionDaoImplQueueViewUnitTest {
    private static final Instant CREATED = Instant.parse("2026-09-28T13:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-09-28T13:10:00Z");
    private static final Instant LAST_ATTEMPT = Instant.parse("2026-09-28T13:05:00Z");

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Object[]> query;

    @Test
    @DisplayName("should count nothing and skip the query when the overdue or stale cutoff is missing")
    void shouldSkipQuery_whenCutoffIsMissing() {
        SmsTransactionDaoImpl dao = newDao();

        assertThat(dao.countOverdueQueuedOutboundByProvider(null)).isEmpty();
        assertThat(dao.countStaleSendingOutboundByProvider(null)).isEmpty();
        assertThat(dao.findOverdueQueuedOutbound(SmsProviderType.STUB, null, 10)).isEmpty();
        assertThat(dao.findStaleSendingOutbound(null, new Date(), 10)).isEmpty();
        assertThat(dao.findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(), 10)).isEmpty();
        assertThat(dao.findRecentOutboundByStatuses(SmsProviderType.STUB, null, 10)).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), eq(Object[].class));
    }

    @Test
    @DisplayName("should count outbound rows only, grouped by SMS provider and status name")
    void shouldMapStatusCounts_forOutboundRowsOnly() {
        stubQuery(List.of(
                new Object[]{SmsProviderType.STUB, SmsStatus.QUEUED, 2L},
                new Object[]{SmsProviderType.VOIPMS, SmsStatus.FAILED, 1L}));

        List<SmsQueueCountDto> counts = newDao().countOutboundByProviderAndStatus();

        assertThat(counts).containsExactly(
                new SmsQueueCountDto(SmsProviderType.STUB, "QUEUED", 2),
                new SmsQueueCountDto(SmsProviderType.VOIPMS, "FAILED", 1));
        verify(query).setParameter("direction", SmsDirection.OUTBOUND);
    }

    @Test
    @DisplayName("should count overdue queued rows per SMS provider against the given cutoff")
    void shouldBindQueuedStatusAndCutoff_whenCountingOverdue() {
        Date dueBefore = Date.from(Instant.parse("2026-09-28T13:55:00Z"));
        stubQuery(List.<Object[]>of(new Object[]{SmsProviderType.STUB, 3L}));

        Map<SmsProviderType, Long> counts = newDao().countOverdueQueuedOutboundByProvider(dueBefore);

        assertThat(counts).isEqualTo(Map.of(SmsProviderType.STUB, 3L));
        verify(query).setParameter("direction", SmsDirection.OUTBOUND);
        verify(query).setParameter("status", SmsStatus.QUEUED);
        verify(query).setParameter("dueBefore", dueBefore);
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Object[].class));
        assertThat(jpql.getValue()).contains("CASE WHEN t.attemptCount = 0 THEN t.createdAt "
                + "ELSE COALESCE(t.nextAttemptAt, t.createdAt) END < :dueBefore");
    }

    @Test
    @DisplayName("should count consent-blocked rows across both blocking statuses, grouped by reason code")
    void shouldBindBothBlockingStatuses_whenCountingConsentBlocks() {
        stubQuery(List.<Object[]>of(new Object[]{SmsProviderType.STUB, null, 4L}));

        List<SmsQueueCountDto> counts = newDao().countConsentBlockedOutboundByProviderAndReason();

        assertThat(counts).containsExactly(new SmsQueueCountDto(SmsProviderType.STUB, null, 4));
        verify(query).setParameter("statuses", List.of(SmsStatus.CONSENT_BLOCKED, SmsStatus.OPTOUT_BLOCKED));
    }

    @Test
    @DisplayName("should map a stale SENDING projection row and cap an oversized limit")
    void shouldMapProjectionRow_whenFindingStaleSends() {
        Date staleBefore = Date.from(Instant.parse("2026-09-28T13:55:00Z"));
        stubQuery(List.<Object[]>of(new Object[]{
                42L, SmsProviderType.STUB, SmsStatus.SENDING, 123, "+14165551212", 1, "PROVIDER_TIMEOUT", null,
                Timestamp.from(CREATED), Timestamp.from(UPDATED), null, Timestamp.from(LAST_ATTEMPT)}));

        List<SmsQueueRowDto> rows = newDao().findStaleSendingOutbound(SmsProviderType.STUB, staleBefore, 5_000);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(42L);
            assertThat(row.status()).isEqualTo(SmsStatus.SENDING);
            assertThat(row.demographicNo()).isEqualTo(123);
            assertThat(row.attemptCount()).isEqualTo(1);
            assertThat(row.errorCode()).isEqualTo("PROVIDER_TIMEOUT");
            assertThat(row.createdAt()).isEqualTo(CREATED);
            assertThat(row.updatedAt()).isEqualTo(UPDATED);
            assertThat(row.nextAttemptAt()).isNull();
            assertThat(row.lastAttemptAt()).isEqualTo(LAST_ATTEMPT);
            assertThat(row.toString()).doesNotContain("4165551212");
        });
        verify(query).setParameter("status", SmsStatus.SENDING);
        verify(query).setParameter("staleBefore", staleBefore);
        verify(query).setMaxResults(500);
    }

    @Test
    @DisplayName("should list recent rows newest first with the default limit when none is given")
    void shouldUseDefaultLimit_whenListingRecentRows() {
        stubQuery(List.of());

        newDao().findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), 0);

        verify(query).setParameter("direction", SmsDirection.OUTBOUND);
        verify(query).setParameter("providerType", SmsProviderType.STUB);
        verify(query).setParameter("statuses", List.of(SmsStatus.FAILED));
        verify(query).setMaxResults(100);
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Object[].class));
        assertThat(jpql.getValue()).contains("ORDER BY t.updatedAt DESC").doesNotContain("messageBody");
    }

    private void stubQuery(List<Object[]> results) {
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(query);
        when(query.getResultList()).thenReturn(results);
    }

    private SmsTransactionDaoImpl newDao() {
        SmsTransactionDaoImpl dao = new SmsTransactionDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        return dao;
    }
}
