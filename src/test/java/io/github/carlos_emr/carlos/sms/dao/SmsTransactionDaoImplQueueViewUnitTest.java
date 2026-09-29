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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Mock
    private TypedQuery<Integer> patientQuery;

    @Test
    @DisplayName("should count nothing and skip the query when the overdue or stale cutoff is missing")
    void shouldSkipQuery_whenCutoffIsMissing() {
        SmsTransactionDaoImpl dao = newDao();

        assertThat(dao.countOverdueQueuedOutboundByProvider(null)).isEmpty();
        assertThat(dao.countStaleSendingOutboundByProvider(null)).isEmpty();
        assertThat(dao.findOverdueQueuedOutbound(SmsProviderType.STUB, null, 10)).isEmpty();
        assertThat(dao.findStaleSendingOutbound(null, new Date(), 10)).isEmpty();
        assertThat(dao.findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(), null, 10)).isEmpty();
        assertThat(dao.findRecentOutboundByStatuses(SmsProviderType.STUB, null, null, 10)).isEmpty();
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

        List<SmsQueueCountDto> counts = newDao().countConsentBlockedOutboundByProviderAndReason(null);

        assertThat(counts).containsExactly(new SmsQueueCountDto(SmsProviderType.STUB, null, 4));
        verify(query).setParameter("statuses", List.of(SmsStatus.CONSENT_BLOCKED, SmsStatus.OPTOUT_BLOCKED));
    }

    @Test
    @DisplayName("should limit the failed, blocked and recent-row queries to rows updated since the given time")
    void shouldBindSince_whenATimePeriodIsGiven() {
        Date since = Date.from(Instant.parse("2026-08-29T14:00:00Z"));
        stubQuery(List.of());
        SmsTransactionDaoImpl dao = newDao();

        dao.countFailedOutboundByProviderAndErrorCode(since);
        dao.countConsentBlockedOutboundByProviderAndReason(since);
        dao.findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), since, 50);

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(3)).createQuery(jpql.capture(), eq(Object[].class));
        // The time is a bound parameter: the query text names it and never holds the value.
        assertThat(jpql.getAllValues()).allSatisfy(text -> assertThat(text)
                .contains("AND t.updatedAt >= :since ")
                .doesNotContain("2026")
                .doesNotContain(String.valueOf(since.getTime())));
        assertThat(jpql.getAllValues().get(0)).contains(":since GROUP BY t.providerType, t.errorCode");
        assertThat(jpql.getAllValues().get(1)).contains(":since GROUP BY t.providerType, t.consentReasonCode");
        assertThat(jpql.getAllValues().get(2)).contains(":since ORDER BY t.updatedAt DESC");
        verify(query, times(3)).setParameter("since", since);
    }

    @Test
    @DisplayName("should bind the epoch as the start time for all time, keeping the query text the same")
    void shouldBindEpoch_whenAllTime() {
        stubQuery(List.of());
        SmsTransactionDaoImpl dao = newDao();

        dao.countFailedOutboundByProviderAndErrorCode(null);
        dao.countConsentBlockedOutboundByProviderAndReason(null);
        dao.findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), null, 50);

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(3)).createQuery(jpql.capture(), eq(Object[].class));
        assertThat(jpql.getAllValues()).allSatisfy(text -> assertThat(text).contains("AND t.updatedAt >= :since "));
        verify(query, times(3)).setParameter("since", Date.from(Instant.EPOCH));
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

        newDao().findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), null, 0);

        verify(query).setParameter("direction", SmsDirection.OUTBOUND);
        verify(query).setParameter("providerType", SmsProviderType.STUB);
        verify(query).setParameter("statuses", List.of(SmsStatus.FAILED));
        verify(query).setMaxResults(100);
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Object[].class));
        assertThat(jpql.getValue()).contains("ORDER BY t.updatedAt DESC").doesNotContain("messageBody");
    }

    @Test
    @DisplayName("should run exactly the counting query when no patient is left out")
    void shouldOmitExclusion_whenNoPatientIsExcluded() {
        Date since = Date.from(Instant.parse("2026-08-29T14:00:00Z"));
        stubQuery(List.of());
        SmsTransactionDaoImpl dao = newDao();

        dao.countFailedOutboundByProviderAndErrorCode(since);
        dao.countFailedOutboundByProviderAndErrorCode(since, List.of());
        dao.countFailedOutboundByProviderAndErrorCode(since, null);
        dao.countFailedOutboundByProviderAndErrorCode(since, Arrays.asList(null, null));
        dao.countConsentBlockedOutboundByProviderAndReason(since);
        dao.countConsentBlockedOutboundByProviderAndReason(since, List.of());

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(6)).createQuery(jpql.capture(), eq(Object[].class));
        List<String> failed = jpql.getAllValues().subList(0, 4);
        List<String> blocked = jpql.getAllValues().subList(4, 6);
        assertThat(failed).containsOnly(failed.get(0));
        assertThat(blocked).containsOnly(blocked.get(0));
        assertThat(jpql.getAllValues()).allSatisfy(text -> assertThat(text).doesNotContain("demographicNo"));
        verify(query, never()).setParameter(startsWith("excluded"), any());
    }

    @Test
    @DisplayName("should leave out the named patients' failed rows, keep rows without a patient, and bind them as one list")
    @SuppressWarnings("unchecked")
    void shouldBindOneList_whenExcludingPatientsFromFailedCounts() {
        Date since = Date.from(Instant.parse("2026-08-29T14:00:00Z"));
        stubQuery(List.<Object[]>of(new Object[]{SmsProviderType.STUB, "invalid_dst", 1L}));
        // 1001 patients, out of order, with a repeat and a gap: bound once, each patient once and in order.
        List<Integer> excluded = new ArrayList<>(IntStream.rangeClosed(1, 1001).boxed().toList());
        Collections.reverse(excluded);
        excluded.add(7);
        excluded.add(null);

        List<SmsQueueCountDto> counts = newDao().countFailedOutboundByProviderAndErrorCode(since, excluded);

        assertThat(counts).containsExactly(new SmsQueueCountDto(SmsProviderType.STUB, "invalid_dst", 1));
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Object[].class));
        // The numbers are a bound parameter: the query text is fixed and holds none of them.
        assertThat(jpql.getValue())
                .contains("AND t.updatedAt >= :since AND (t.demographicNo IS NULL OR "
                        + "t.demographicNo NOT IN (:excluded)) GROUP BY t.providerType, t.errorCode")
                .doesNotContainPattern("[0-9]{2,}");
        ArgumentCaptor<Object> bound = ArgumentCaptor.forClass(Object.class);
        verify(query).setParameter(eq("excluded"), bound.capture());
        assertThat((List<Integer>) bound.getValue())
                .hasSize(1001)
                .isSorted()
                .startsWith(1)
                .endsWith(1001);
        verify(query).setParameter("status", SmsStatus.FAILED);
        verify(query).setParameter("since", since);
    }

    @Test
    @DisplayName("should leave out the named patients' consent-blocked rows, of all time")
    @SuppressWarnings("unchecked")
    void shouldBindOneList_whenExcludingPatientsFromConsentBlockedCounts() {
        stubQuery(List.of());

        newDao().countConsentBlockedOutboundByProviderAndReason(null, List.of(4243, 4242));

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Object[].class));
        assertThat(jpql.getValue())
                .contains("AND t.status IN (:statuses) AND t.updatedAt >= :since AND (t.demographicNo IS NULL OR "
                        + "t.demographicNo NOT IN (:excluded)) GROUP BY t.providerType, t.consentReasonCode")
                .doesNotContain("424");
        verify(query).setParameter("since", Date.from(Instant.EPOCH));
        ArgumentCaptor<Object> bound = ArgumentCaptor.forClass(Object.class);
        verify(query).setParameter(eq("excluded"), bound.capture());
        assertThat((Collection<Integer>) bound.getValue()).containsExactly(4242, 4243);
        verify(query).setParameter("statuses", List.of(SmsStatus.CONSENT_BLOCKED, SmsStatus.OPTOUT_BLOCKED));
    }

    @Test
    @DisplayName("should run no patient query when no patient is named")
    void shouldSkipPatientQuery_whenNoPatientIsNamed() {
        SmsTransactionDaoImpl dao = newDao();

        assertThat(dao.findPatientsWithFailedOutbound(null, List.of())).isEmpty();
        assertThat(dao.findPatientsWithFailedOutbound(null, null)).isEmpty();
        assertThat(dao.findPatientsWithConsentBlockedOutbound(new Date(), List.of())).isEmpty();
        assertThat(dao.findPatientsWithConsentBlockedOutbound(new Date(), Arrays.asList(null, null))).isEmpty();
        verify(entityManager, never()).createQuery(anyString(), any(Class.class));
    }

    @Test
    @DisplayName("should find the named patients with failed rows, with patients, status and time as bound parameters")
    void shouldBindPatientsStatusAndSince_whenFindingPatientsWithFailedRows() {
        Date since = Date.from(Instant.parse("2026-08-29T14:00:00Z"));
        stubPatientQuery();
        when(patientQuery.getResultList()).thenReturn(List.of(4243, 4242));

        // Repeats and gaps are dropped, and the patients are bound in order.
        Set<Integer> patients = newDao().findPatientsWithFailedOutbound(since, Arrays.asList(4243, 4242, null, 4243));

        assertThat(patients).containsExactly(4242, 4243);
        verify(patientQuery).setParameter("direction", SmsDirection.OUTBOUND);
        verify(patientQuery).setParameter("statuses", List.of(SmsStatus.FAILED));
        verify(patientQuery).setParameter("demographicNos", List.of(4242, 4243));
        verify(patientQuery).setParameter("since", since);
        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Integer.class));
        // The patients are a bound parameter: the query text never holds a demographic number.
        assertThat(jpql.getValue())
                .startsWith("SELECT DISTINCT t.demographicNo FROM SmsTransaction t ")
                .contains("AND t.demographicNo IN (:demographicNos) ")
                .contains("AND t.updatedAt >= :since ")
                .doesNotContain("424")
                .doesNotContain("messageBody");
    }

    @Test
    @DisplayName("should find the named patients with consent-blocked rows across both blocking statuses, of all time")
    void shouldBindBothBlockingStatuses_whenFindingPatientsWithConsentBlocks() {
        stubPatientQuery();
        when(patientQuery.getResultList()).thenReturn(List.of(4242));

        Set<Integer> patients = newDao().findPatientsWithConsentBlockedOutbound(null, List.of(4242));

        assertThat(patients).containsExactly(4242);
        verify(patientQuery).setParameter("statuses", List.of(SmsStatus.CONSENT_BLOCKED, SmsStatus.OPTOUT_BLOCKED));
        verify(patientQuery).setParameter("demographicNos", List.of(4242));
        verify(patientQuery).setParameter("since", Date.from(Instant.EPOCH));
    }

    @Test
    @DisplayName("should name at most 1000 patients in one patient query and put the parts together")
    @SuppressWarnings("unchecked")
    void shouldQueryInParts_whenMoreThanAThousandPatientsAreNamed() {
        stubPatientQuery();
        when(patientQuery.getResultList())
                .thenReturn(List.of(1))
                .thenReturn(List.of(1001))
                .thenReturn(List.of(2001));
        List<Integer> patients = new ArrayList<>(IntStream.rangeClosed(1, 2001).boxed().toList());

        Set<Integer> found = newDao().findPatientsWithFailedOutbound(null, patients);

        assertThat(found).containsExactly(1, 1001, 2001);
        verify(entityManager, times(3)).createQuery(anyString(), eq(Integer.class));
        ArgumentCaptor<Object> bound = ArgumentCaptor.forClass(Object.class);
        verify(patientQuery, times(3)).setParameter(eq("demographicNos"), bound.capture());
        List<Collection<Integer>> parts = bound.getAllValues().stream()
                .map(part -> (Collection<Integer>) part)
                .toList();
        assertThat(parts).extracting(Collection::size).containsExactly(1000, 1000, 1);
        assertThat(parts.get(0)).contains(1, 1000).doesNotContain(1001);
        assertThat(parts.get(1)).contains(1001, 2000);
        assertThat(parts.get(2)).containsExactly(2001);
    }

    private void stubQuery(List<Object[]> results) {
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(query);
        when(query.getResultList()).thenReturn(results);
    }

    private void stubPatientQuery() {
        when(entityManager.createQuery(anyString(), eq(Integer.class))).thenReturn(patientQuery);
    }

    private SmsTransactionDaoImpl newDao() {
        SmsTransactionDaoImpl dao = new SmsTransactionDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        return dao;
    }
}
