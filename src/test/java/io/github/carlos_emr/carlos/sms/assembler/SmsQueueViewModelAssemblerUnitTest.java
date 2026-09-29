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
package io.github.carlos_emr.carlos.sms.assembler;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueCountDto;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueRowDto;
import io.github.carlos_emr.carlos.sms.service.SmsConfigService;
import io.github.carlos_emr.carlos.sms.service.SmsPatientRestrictionLookup;
import io.github.carlos_emr.carlos.sms.service.SmsQueueProcessingService;
import io.github.carlos_emr.carlos.sms.service.SmsQueueScheduler;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueViewModel;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SmsQueueViewModelAssembler}: per-provider grouping, the overdue and stale thresholds, the scheduler
 * state, the redaction of every row (last four digits only, no message text), and the hiding of restricted
 * patients' messages from the lists and from the counts by code.
 *
 * @since 2026-09-28
 */
@Tag("unit")
@Tag("service")
@DisplayName("SMS queue view model assembler")
class SmsQueueViewModelAssemblerUnitTest {
    private static final Instant NOW = Instant.parse("2026-09-28T14:00:00Z");
    // Synthetic number; the digits before the last four must never reach the model.
    private static final String RECIPIENT = "+16135559876";
    private static final List<SmsStatus> BLOCKED = List.of(SmsStatus.CONSENT_BLOCKED, SmsStatus.OPTOUT_BLOCKED);
    private static final Date THIRTY_DAYS_AGO = Date.from(NOW.minus(Duration.ofDays(30)));
    /** The patients with a security entry of their own in most tests: only these are ever checked. */
    private static final Set<Integer> WITH_ENTRIES = Set.of(99, 100, 101);

    private final SmsTransactionDao dao = mock(SmsTransactionDao.class);
    private final SmsConfigService configService = mock(SmsConfigService.class);
    private final SmsQueueScheduler scheduler = mock(SmsQueueScheduler.class);
    private final SmsPatientRestrictionLookup restrictionLookup = mock(SmsPatientRestrictionLookup.class);

    @BeforeEach
    void setUp() {
        when(restrictionLookup.patientsWithOwnEntries()).thenReturn(WITH_ENTRIES);
    }

    @Test
    @DisplayName("should list every SMS provider in enum order with its own counts and every status in enum order")
    void shouldGroupCounts_perProvider() {
        when(dao.countOutboundByProviderAndStatus()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "QUEUED", 2),
                new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 1),
                new SmsQueueCountDto(SmsProviderType.STUB, "CONSENT_BLOCKED", 3),
                new SmsQueueCountDto(SmsProviderType.STUB, "OPTOUT_BLOCKED", 4),
                new SmsQueueCountDto(SmsProviderType.VOIPMS, "DELIVERED", 5)));

        SmsQueueViewModel model = assemble(() -> false, true);

        assertThat(model.providers()).extracting(SmsQueueViewModel.ProviderQueue::providerType)
                .containsExactly("VOIPMS", "CLOUDLI", "STUB");
        SmsQueueViewModel.ProviderQueue stub = provider(model, "STUB");
        assertThat(stub.outboundTotal()).isEqualTo(10);
        assertThat(stub.failedTotal()).isEqualTo(1);
        assertThat(stub.blockedTotal()).isEqualTo(7);
        // RECEIVED only describes inbound messages, so an outbound view leaves it out while it is zero.
        assertThat(stub.statusCounts()).containsExactly(
                new SmsQueueViewModel.StatusCount("QUEUED", 2),
                new SmsQueueViewModel.StatusCount("SENDING", 0),
                new SmsQueueViewModel.StatusCount("SENT", 0),
                new SmsQueueViewModel.StatusCount("DELIVERED", 0),
                new SmsQueueViewModel.StatusCount("FAILED", 1),
                // Both kinds of consent block share one line.
                new SmsQueueViewModel.StatusCount("BLOCKED_BY_CONSENT", 7));
        assertThat(provider(model, "VOIPMS").outboundTotal()).isEqualTo(5);
        assertThat(provider(model, "CLOUDLI").outboundTotal()).isZero();
        assertThat(provider(model, "CLOUDLI").statusCounts()).allSatisfy(count -> assertThat(count.count()).isZero());
    }

    @Test
    @DisplayName("should query the lists only for providers whose counts show something, capped at 50 rows")
    void shouldQueryLists_onlyWhenCountsArePositive() {
        when(dao.countOutboundByProviderAndStatus()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 1),
                new SmsQueueCountDto(SmsProviderType.STUB, "OPTOUT_BLOCKED", 1),
                new SmsQueueCountDto(SmsProviderType.VOIPMS, "FAILED", 1)));
        when(dao.countOverdueQueuedOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 1L));
        when(dao.countStaleSendingOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 1L));
        // VOIPMS has a failure of all time but none within the time period, so its list is not queried.
        when(dao.countFailedOutboundByProviderAndErrorCode(any())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 1)));
        when(dao.countConsentBlockedOutboundByProviderAndReason(any())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "SMS_CONSENT_OPT_OUT", 1)));

        assemble(() -> false, true);

        verify(dao).findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50));
        verify(dao).findStaleSendingOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50));
        verify(dao).findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), THIRTY_DAYS_AGO, 50);
        verify(dao).findRecentOutboundByStatuses(SmsProviderType.STUB, BLOCKED, THIRTY_DAYS_AGO, 50);
        verify(dao, never()).findOverdueQueuedOutbound(eq(SmsProviderType.VOIPMS), any(), anyInt());
        verify(dao, never()).findStaleSendingOutbound(eq(SmsProviderType.CLOUDLI), any(), anyInt());
        verify(dao, never()).findRecentOutboundByStatuses(eq(SmsProviderType.VOIPMS), any(), any(), anyInt());
    }

    @Test
    @DisplayName("should count overdue from five minutes ago and stale sends by the worker's own threshold")
    void shouldUseThresholds_fromOverdueGraceAndWorkerTimeout() {
        SmsQueueViewModel model = assemble(() -> false, true);

        verify(dao).countOverdueQueuedOutboundByProvider(Date.from(NOW.minus(Duration.ofMinutes(5))));
        verify(dao).countStaleSendingOutboundByProvider(
                Date.from(NOW.minus(SmsQueueProcessingService.DEFAULT_STALE_SENDING_TIMEOUT)));
        assertThat(model.overdueMinutes()).isEqualTo(5);
        assertThat(model.staleMinutes()).isEqualTo(SmsQueueProcessingService.DEFAULT_STALE_SENDING_TIMEOUT.toMinutes());
        assertThat(model.listLimit()).isEqualTo(50);
        assertThat(model.generatedAt()).isEqualTo("2026-09-28 14:00");
    }

    @Test
    @DisplayName("should count codes largest first and group a missing code under an empty code")
    void shouldSortCodeCounts_largestFirst() {
        when(dao.countFailedOutboundByProviderAndErrorCode(any())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, null, 2),
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_B", 5),
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 5)));
        when(dao.countConsentBlockedOutboundByProviderAndReason(any())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.VOIPMS, "SMS_CONSENT_OPT_OUT", 1)));

        SmsQueueViewModel model = assemble(() -> false, true);

        assertThat(provider(model, "STUB").failedByErrorCode()).containsExactly(
                new SmsQueueViewModel.CodeCount("ERR_A", 5),
                new SmsQueueViewModel.CodeCount("ERR_B", 5),
                new SmsQueueViewModel.CodeCount("", 2));
        assertThat(provider(model, "STUB").blockedByReason()).isEmpty();
        assertThat(provider(model, "VOIPMS").blockedByReason())
                .containsExactly(new SmsQueueViewModel.CodeCount("SMS_CONSENT_OPT_OUT", 1));
    }

    @Test
    @DisplayName("should show only the recipient's last four digits and no other digit of the number anywhere")
    void shouldMaskRecipient_toLastFourDigits() {
        when(dao.countOutboundByProviderAndStatus())
                .thenReturn(List.of(new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 1)));
        when(dao.countFailedOutboundByProviderAndErrorCode(any())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED", 1)));
        when(dao.findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), THIRTY_DAYS_AGO, 50))
                .thenReturn(List.of(new SmsQueueRowDto(42L, SmsProviderType.STUB, SmsStatus.FAILED, 4242, RECIPIENT,
                        3, "QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED", null,
                        NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), null,
                        NOW.minus(Duration.ofMinutes(61)))));

        SmsQueueViewModel model = assemble(() -> false, true);

        SmsQueueViewModel.Row row = provider(model, "STUB").recentFailed().rows().get(0);
        assertThat(row).isEqualTo(new SmsQueueViewModel.Row("42", "STUB", "FAILED", "2026-09-28 12:00",
                "2026-09-28 13:00", "", "2026-09-28 12:59", 3, "QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED", "", "4242",
                "***9876"));
        assertThat(model.toString()).doesNotContain("6135559876", "613555", "5559876", "+1613");
    }

    @Test
    @DisplayName("should show nothing for a number too short to mask")
    void shouldShowNothing_forNumberShorterThanSevenDigits() {
        assertThat(SmsQueueViewModelAssembler.lastFourDigits("12")).isEmpty();
        assertThat(SmsQueueViewModelAssembler.lastFourDigits("1234")).isEmpty();
        assertThat(SmsQueueViewModelAssembler.lastFourDigits("555-012")).isEmpty();
        assertThat(SmsQueueViewModelAssembler.lastFourDigits(null)).isEmpty();
        assertThat(SmsQueueViewModelAssembler.lastFourDigits("555-0199")).isEqualTo("***0199");
        assertThat(SmsQueueViewModelAssembler.lastFourDigits("416-555-0199")).isEqualTo("***0199");
    }

    @Test
    @DisplayName("should show a stored error code only when it looks like a code")
    void shouldReplaceErrorCode_whenItIsNotACode() {
        assertThat(SmsQueueViewModelAssembler.codeOnly("QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED"))
                .isEqualTo("QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED");
        assertThat(SmsQueueViewModelAssembler.codeOnly("carrier:30006")).isEqualTo("carrier:30006");
        assertThat(SmsQueueViewModelAssembler.codeOnly(null)).isEmpty();
        assertThat(SmsQueueViewModelAssembler.codeOnly("Invalid number +14165550199"))
                .isEqualTo(SmsQueueViewModelAssembler.NOT_A_CODE);
        assertThat(SmsQueueViewModelAssembler.codeOnly("x".repeat(65)))
                .isEqualTo(SmsQueueViewModelAssembler.NOT_A_CODE);
        // Shaped like a code, but really a phone number or a health number.
        assertThat(SmsQueueViewModelAssembler.codeOnly("416-555-0199"))
                .isEqualTo(SmsQueueViewModelAssembler.NOT_A_CODE);
        assertThat(SmsQueueViewModelAssembler.codeOnly("4165550199"))
                .isEqualTo(SmsQueueViewModelAssembler.NOT_A_CODE);
    }

    @Test
    @DisplayName("should count every stored value that is not a code on one line")
    void shouldMergeNonCodes_intoOneCountLine() {
        when(dao.countOutboundByProviderAndStatus()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 5L)));
        when(dao.countFailedOutboundByProviderAndErrorCode(any())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "Invalid number +14165550199", 2L),
                new SmsQueueCountDto(SmsProviderType.STUB, "416-555-0123", 1L),
                new SmsQueueCountDto(SmsProviderType.STUB, "CARRIER_REJECTED", 2L)));

        SmsQueueViewModel model = assemble(() -> false, true);

        assertThat(provider(model, "STUB").failedByErrorCode())
                .extracting(SmsQueueViewModel.CodeCount::code, SmsQueueViewModel.CodeCount::count)
                .containsExactlyInAnyOrder(
                        tuple(SmsQueueViewModelAssembler.NOT_A_CODE, 3L), tuple("CARRIER_REJECTED", 2L));
    }

    @Test
    @DisplayName("should ask about each patient once, however many rows name them")
    void shouldAskOncePerPatient_whenSeveralRowsNameThem() {
        when(dao.countOutboundByProviderAndStatus()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "QUEUED", 2L)));
        when(dao.countOverdueQueuedOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 2L));
        when(dao.findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50))).thenReturn(List.of(
                new SmsQueueRowDto(7L, SmsProviderType.STUB, SmsStatus.QUEUED, 99, RECIPIENT, 0, null, null,
                        NOW.minus(Duration.ofMinutes(30)), NOW.minus(Duration.ofMinutes(30)), null, null),
                new SmsQueueRowDto(8L, SmsProviderType.STUB, SmsStatus.QUEUED, 99, RECIPIENT, 0, null, null,
                        NOW.minus(Duration.ofMinutes(20)), NOW.minus(Duration.ofMinutes(20)), null, null)));
        AtomicInteger asked = new AtomicInteger();

        assembler(() -> false).assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> asked.incrementAndGet() > 0);

        assertThat(asked).hasValue(1);
    }

    @Test
    @DisplayName("should ask about each patient once across all four lists, whatever the answer")
    void shouldAskOncePerPatient_acrossLists() {
        stubAllFourLists(queued(7L, 99), sending(8L, 99), failed(9L, 100), blocked(10L, 100));
        AtomicInteger asked = new AtomicInteger();

        assembler(() -> false).assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> {
            asked.incrementAndGet();
            return patient != 100;
        });

        assertThat(asked).hasValue(2);
    }

    @Test
    @DisplayName("should leave out every row of a patient this viewer may not open, and count them per list")
    void shouldHideRows_whenViewerMayNotOpenThatPatient() {
        stubAllFourLists(queued(7L, 99), sending(8L, 99), failed(9L, 99), blocked(10L, 99));
        when(dao.findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50)))
                .thenReturn(List.of(queued(7L, 99), queued(11L, 100), queued(12L, 99)));

        SmsQueueViewModelAssembler.Result result =
                assembler(() -> false).assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> patient != 99);

        SmsQueueViewModel.ProviderQueue stub = provider(result.model(), "STUB");
        assertThat(stub.overdue().rows()).extracting(SmsQueueViewModel.Row::id, SmsQueueViewModel.Row::demographicNo)
                .containsExactly(tuple("11", "100"));
        assertThat(stub.overdue().hiddenCount()).isEqualTo(2);
        assertThat(List.of(stub.stale(), stub.recentFailed(), stub.recentBlocked())).allSatisfy(list -> {
            assertThat(list.rows()).isEmpty();
            assertThat(list.hiddenCount()).isEqualTo(1);
        });
        // Nothing of the hidden rows is in the model: not their ids, and no trace of the patient.
        assertThat(result.model().toString()).doesNotContain("id=7,", "id=8,", "id=9,", "id=10,", "id=12,",
                "demographicNo=99");
        assertThat(result.displayedDemographicNumbers()).containsExactly(100);
    }

    @Test
    @DisplayName("should keep hidden messages in the totals but leave them out of the counts by code")
    void shouldKeepTotalsAndExcludeFromCodeCounts_whenRowsAreHidden() {
        stubAllFourLists(queued(7L, 99), sending(8L, 99), failed(9L, 99), blocked(10L, 99));
        when(dao.findPatientsWithFailedOutbound(THIRTY_DAYS_AGO, WITH_ENTRIES)).thenReturn(Set.of(99));
        when(dao.findPatientsWithConsentBlockedOutbound(THIRTY_DAYS_AGO, WITH_ENTRIES)).thenReturn(Set.of(99));
        // Without patient 99's messages, nothing is left to count.
        when(dao.countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO, Set.of(99))).thenReturn(List.of());
        when(dao.countConsentBlockedOutboundByProviderAndReason(THIRTY_DAYS_AGO, Set.of(99))).thenReturn(List.of());

        SmsQueueViewModel.ProviderQueue stub = provider(assembler(() -> false)
                .assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> false).model(), "STUB");

        assertThat(stub.outboundTotal()).isEqualTo(4);
        assertThat(stub.statusCounts()).contains(new SmsQueueViewModel.StatusCount("QUEUED", 1),
                new SmsQueueViewModel.StatusCount("FAILED", 1));
        assertThat(stub.overdueCount()).isEqualTo(1);
        assertThat(stub.staleCount()).isEqualTo(1);
        assertThat(stub.failedCount()).isEqualTo(1);
        assertThat(stub.failedTotal()).isEqualTo(1);
        // The only message with this code is hidden, so the code is not in the model.
        assertThat(stub.failedByErrorCode()).isEmpty();
        assertThat(stub.blockedCount()).isEqualTo(1);
        assertThat(stub.blockedTotal()).isEqualTo(1);
        assertThat(stub.blockedByReason()).isEmpty();
        assertThat(stub.statusCounts()).extracting(SmsQueueViewModel.StatusCount::status)
                .doesNotContain("CONSENT_BLOCKED", "OPTOUT_BLOCKED");
    }

    @Test
    @DisplayName("should show the counts that leave a restricted patient out even when their code differs only in case")
    void shouldShowExcludingCounts_whenRestrictedCodeDiffersOnlyInLetterCase() {
        // On MariaDB the code columns ignore letter case when grouping: a visible patient's 'invalid_dst' and
        // restricted patient 99's 'INVALID_DST' come back as one group spelled 'invalid_dst'. Taking 99's
        // 'INVALID_DST' off by spelling would find nothing, so the query itself must leave 99 out.
        when(dao.countOutboundByProviderAndStatus()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 2)));
        when(dao.countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO)).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "invalid_dst", 2)));
        // 100 has messages too but is not restricted; 101 is restricted but has no message in this section.
        when(dao.findPatientsWithFailedOutbound(THIRTY_DAYS_AGO, WITH_ENTRIES)).thenReturn(Set.of(99, 100));
        when(dao.countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO, Set.of(99))).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "invalid_dst", 1)));

        SmsQueueViewModel.ProviderQueue stub = provider(assembler(() -> false)
                .assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> patient == 100).model(), "STUB");

        assertThat(stub.failedByErrorCode()).containsExactly(new SmsQueueViewModel.CodeCount("invalid_dst", 1));
        // The section's own count still includes the hidden message.
        assertThat(stub.failedCount()).isEqualTo(2);
        verify(dao).countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO, Set.of(99));
    }

    @Test
    @DisplayName("should leave a restricted patient's message out of the counts by code when it is beyond the list's 50 rows")
    void shouldExcludeFromCodeCounts_whenRestrictedMessageIsBeyondTheListLimit() {
        when(restrictionLookup.patientsWithOwnEntries()).thenReturn(Set.of(99));
        when(dao.countOutboundByProviderAndStatus()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 51)));
        when(dao.countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO)).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 30),
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_B", 21)));
        // The 50 newest all belong to a patient without an entry of their own; the 51st is patient 99's.
        when(dao.findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), THIRTY_DAYS_AGO, 50))
                .thenReturn(IntStream.rangeClosed(1, 50).mapToObj(id -> failed((long) id, 500)).toList());
        when(dao.findPatientsWithFailedOutbound(THIRTY_DAYS_AGO, Set.of(99))).thenReturn(Set.of(99));
        when(dao.countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO, Set.of(99))).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 30),
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_B", 20)));

        SmsQueueViewModelAssembler.Result result = assembler(() -> false)
                .assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> patient != 99);

        SmsQueueViewModel.ProviderQueue stub = provider(result.model(), "STUB");
        assertThat(stub.recentFailed().rows()).hasSize(50);
        assertThat(stub.recentFailed().hiddenCount()).isZero();
        assertThat(stub.failedByErrorCode()).containsExactly(
                new SmsQueueViewModel.CodeCount("ERR_A", 30),
                new SmsQueueViewModel.CodeCount("ERR_B", 20));
        // The section's own counts still include the hidden message.
        assertThat(stub.failedCount()).isEqualTo(51);
        assertThat(stub.failedTotal()).isEqualTo(51);
        assertThat(result.displayedDemographicNumbers()).containsExactly(500);
    }

    @Test
    @DisplayName("should run no extra count query for a section with no messages")
    void shouldRunNeitherQuery_whenSectionIsEmpty() {
        // Failed has messages, blocked has none within the time period.
        when(dao.countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO)).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 1)));
        when(dao.findPatientsWithFailedOutbound(THIRTY_DAYS_AGO, WITH_ENTRIES)).thenReturn(Set.of(99));

        assembler(() -> false).assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> false);

        verify(dao).findPatientsWithFailedOutbound(THIRTY_DAYS_AGO, WITH_ENTRIES);
        verify(dao).countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO, Set.of(99));
        verify(dao, never()).findPatientsWithConsentBlockedOutbound(any(), any());
        verify(dao, never()).countConsentBlockedOutboundByProviderAndReason(any(), any());
    }

    @Test
    @DisplayName("should replace what is not a code in the counts that leave restricted patients out")
    void shouldFilterCodes_inTheCountsWithoutRestrictedPatients() {
        when(dao.countFailedOutboundByProviderAndErrorCode(any())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "Invalid number +14165550199", 2L),
                new SmsQueueCountDto(SmsProviderType.STUB, "416-555-0123", 1L)));
        when(dao.findPatientsWithFailedOutbound(any(), any())).thenReturn(Set.of(99));
        when(dao.countFailedOutboundByProviderAndErrorCode(any(), any())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "Invalid number +14165550199", 2L)));

        SmsQueueViewModel.ProviderQueue stub = provider(assembler(() -> false)
                .assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> false).model(), "STUB");

        assertThat(stub.failedByErrorCode())
                .containsExactly(new SmsQueueViewModel.CodeCount(SmsQueueViewModelAssembler.NOT_A_CODE, 2));
        assertThat(stub.toString()).doesNotContain("4165550199");
    }

    @Test
    @DisplayName("should check nobody and run no extra count query when no patient has an entry of their own")
    void shouldSkipChecksAndPatientQueries_whenNoPatientHasOwnEntry() {
        when(restrictionLookup.patientsWithOwnEntries()).thenReturn(Set.of());
        stubAllFourLists(queued(7L, 99), sending(8L, 100), failed(9L, 99), blocked(10L, 101));
        AtomicInteger asked = new AtomicInteger();

        SmsQueueViewModelAssembler.Result result = assembler(() -> false)
                .assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> asked.incrementAndGet() < 0);

        assertThat(asked).hasValue(0);
        verify(dao, never()).findPatientsWithFailedOutbound(any(), any());
        verify(dao, never()).findPatientsWithConsentBlockedOutbound(any(), any());
        verify(dao, never()).countFailedOutboundByProviderAndErrorCode(any(), any());
        verify(dao, never()).countConsentBlockedOutboundByProviderAndReason(any(), any());
        SmsQueueViewModel.ProviderQueue stub = provider(result.model(), "STUB");
        assertThat(List.of(stub.overdue(), stub.stale(), stub.recentFailed(), stub.recentBlocked()))
                .allSatisfy(list -> {
                    assertThat(list.rows()).hasSize(1);
                    assertThat(list.hiddenCount()).isZero();
                });
        assertThat(stub.failedByErrorCode()).containsExactly(new SmsQueueViewModel.CodeCount("ERR_A", 1));
        assertThat(stub.blockedByReason())
                .containsExactly(new SmsQueueViewModel.CodeCount("SMS_CONSENT_OPT_OUT", 1));
        assertThat(result.displayedDemographicNumbers()).containsExactlyInAnyOrder(99, 100, 101);
    }

    @Test
    @DisplayName("should never ask about a patient without an entry of their own, and show their rows")
    void shouldNotCheckPatient_whenTheyHaveNoOwnEntry() {
        when(restrictionLookup.patientsWithOwnEntries()).thenReturn(Set.of(99));
        stubAllFourLists(queued(7L, 99), sending(8L, 500), failed(9L, 500), blocked(10L, 500));
        List<Integer> asked = new ArrayList<>();

        SmsQueueViewModelAssembler.Result result = assembler(() -> false)
                .assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> {
                    asked.add(patient);
                    return false;
                });

        assertThat(asked).containsExactly(99);
        assertThat(result.displayedDemographicNumbers()).containsExactly(500);
        // Only the patients with an entry of their own are looked for in the sections.
        verify(dao).findPatientsWithFailedOutbound(THIRTY_DAYS_AGO, Set.of(99));
        verify(dao).findPatientsWithConsentBlockedOutbound(THIRTY_DAYS_AGO, Set.of(99));
    }

    @Test
    @DisplayName("should run no count without restricted patients when none in the section is restricted")
    void shouldKeepCountsAndRunNoExcludingQuery_whenNoPatientInTheSectionIsRestricted() {
        stubAllFourLists(queued(7L, 100), sending(8L, 100), failed(9L, 100), blocked(10L, 100));
        when(dao.findPatientsWithFailedOutbound(THIRTY_DAYS_AGO, WITH_ENTRIES)).thenReturn(Set.of(100));
        when(dao.findPatientsWithConsentBlockedOutbound(THIRTY_DAYS_AGO, WITH_ENTRIES)).thenReturn(Set.of(100));

        SmsQueueViewModel.ProviderQueue stub = provider(assembler(() -> false)
                .assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> patient != 99).model(), "STUB");

        assertThat(stub.failedByErrorCode()).containsExactly(new SmsQueueViewModel.CodeCount("ERR_A", 1));
        assertThat(stub.blockedByReason())
                .containsExactly(new SmsQueueViewModel.CodeCount("SMS_CONSENT_OPT_OUT", 1));
        assertThat(stub.recentFailed().hiddenCount()).isZero();
        // 99 is restricted but has no message in either section, so nothing is counted again.
        verify(dao, never()).countFailedOutboundByProviderAndErrorCode(any(), any());
        verify(dao, never()).countConsentBlockedOutboundByProviderAndReason(any(), any());
    }

    @Test
    @DisplayName("should ask about each patient once across the lists and both sections' counts by code")
    void shouldAskOncePerPatient_acrossListsAndCodeCounts() {
        stubAllFourLists(queued(7L, 99), sending(8L, 99), failed(9L, 99), blocked(10L, 100));
        when(dao.findPatientsWithFailedOutbound(any(), any())).thenReturn(Set.of(99));
        when(dao.findPatientsWithConsentBlockedOutbound(any(), any())).thenReturn(Set.of(99, 100));
        List<Integer> asked = new ArrayList<>();

        assembler(() -> false).assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> {
            asked.add(patient);
            return patient != 99;
        });

        // 101 has an entry of their own but no message here, so nothing is asked about them.
        assertThat(asked).containsExactlyInAnyOrder(99, 100);
        verify(dao).countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO, Set.of(99));
        verify(dao).countConsentBlockedOutboundByProviderAndReason(THIRTY_DAYS_AGO, Set.of(99));
    }

    @Test
    @DisplayName("should hide a patient's rows and leave their messages out of the counts when the access check fails")
    void shouldHideAndExclude_whenAccessCheckThrows() {
        stubAllFourLists(queued(7L, 100), sending(8L, 100), failed(9L, 99), blocked(10L, 100));
        when(dao.countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO)).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 3)));
        when(dao.findPatientsWithFailedOutbound(any(), any())).thenReturn(Set.of(99, 100));
        when(dao.countFailedOutboundByProviderAndErrorCode(THIRTY_DAYS_AGO, Set.of(99))).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 1)));
        AtomicInteger askedAbout99 = new AtomicInteger();

        SmsQueueViewModelAssembler.Result result = assembler(() -> false)
                .assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> {
                    if (patient == 99) {
                        askedAbout99.incrementAndGet();
                        throw new IllegalStateException("synthetic lookup failure");
                    }
                    return true;
                });

        SmsQueueViewModel.ProviderQueue stub = provider(result.model(), "STUB");
        assertThat(stub.recentFailed().rows()).isEmpty();
        assertThat(stub.recentFailed().hiddenCount()).isEqualTo(1);
        assertThat(stub.failedByErrorCode()).containsExactly(new SmsQueueViewModel.CodeCount("ERR_A", 1));
        assertThat(stub.failedCount()).isEqualTo(3);
        assertThat(result.displayedDemographicNumbers()).containsExactly(100);
        // The failed check is remembered too: it is not tried again for the next row or count.
        assertThat(askedAbout99).hasValue(1);
    }

    @Test
    @DisplayName("should fail and query nothing when the patients with an entry of their own cannot be read")
    void shouldFail_whenPatientsWithOwnEntriesCannotBeRead() {
        when(restrictionLookup.patientsWithOwnEntries()).thenThrow(new IllegalStateException("synthetic read failure"));
        SmsQueueViewModelAssembler assembler = assembler(() -> false);

        assertThatThrownBy(() -> assembler.assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> true))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(dao);
    }

    @Test
    @DisplayName("should never hide a row that has no patient, and never ask about it")
    void shouldShowRow_whenItHasNoPatient() {
        when(dao.countOverdueQueuedOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 1L));
        when(dao.findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50)))
                .thenReturn(List.of(queued(7L, null)));
        AtomicInteger asked = new AtomicInteger();

        SmsQueueViewModelAssembler.Result result = assembler(() -> false)
                .assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> asked.incrementAndGet() < 0);

        SmsQueueViewModel.RowList overdue = provider(result.model(), "STUB").overdue();
        assertThat(overdue.rows()).extracting(SmsQueueViewModel.Row::id).containsExactly("7");
        assertThat(overdue.hiddenCount()).isZero();
        assertThat(asked).hasValue(0);
        assertThat(result.displayedDemographicNumbers()).isEmpty();
    }

    @Test
    @DisplayName("should hide a patient's rows when the access check fails")
    void shouldHideRows_whenAccessCheckThrows() {
        when(dao.countOverdueQueuedOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 2L));
        when(dao.findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50)))
                .thenReturn(List.of(queued(7L, 99), queued(8L, 100)));

        SmsQueueViewModelAssembler.Result result =
                assembler(() -> false).assemble(SmsQueueWindow.LAST_30_DAYS, true, patient -> {
                    if (patient == 99) {
                        throw new IllegalStateException("synthetic lookup failure");
                    }
                    return true;
                });

        SmsQueueViewModel.RowList overdue = provider(result.model(), "STUB").overdue();
        assertThat(overdue.rows()).extracting(SmsQueueViewModel.Row::id).containsExactly("8");
        assertThat(overdue.hiddenCount()).isEqualTo(1);
        assertThat(result.displayedDemographicNumbers()).containsExactly(100);
    }

    @Test
    @DisplayName("should return the patients shown, once each, even when the number column is off")
    void shouldReturnDisplayedPatients_whenNumberColumnIsOff() {
        stubAllFourLists(queued(7L, 99), sending(8L, 100), failed(9L, 99), blocked(10L, 101));

        SmsQueueViewModelAssembler.Result result =
                assembler(() -> false).assemble(SmsQueueWindow.LAST_30_DAYS, false, patient -> patient != 101);

        assertThat(result.displayedDemographicNumbers()).containsExactlyInAnyOrder(99, 100);
        // The page's model itself carries no demographic number for this viewer.
        assertThat(result.model().demographicNumbersShown()).isFalse();
        SmsQueueViewModel.ProviderQueue stub = provider(result.model(), "STUB");
        assertThat(List.of(stub.overdue(), stub.stale(), stub.recentFailed(), stub.recentBlocked()))
                .flatExtracting(SmsQueueViewModel.RowList::rows)
                .extracting(SmsQueueViewModel.Row::demographicNo)
                .containsExactly("", "", "");
        assertThat(result.toString()).doesNotContain("99", "100");
    }

    @Test
    @DisplayName("should limit the failed and blocked sections to the time period, counted from the clock")
    void shouldPassWindowStart_toFailedAndBlockedQueries() {
        Date sevenDaysAgo = Date.from(NOW.minus(Duration.ofDays(7)));
        when(dao.countOutboundByProviderAndStatus()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 9),
                new SmsQueueCountDto(SmsProviderType.STUB, "CONSENT_BLOCKED", 5),
                new SmsQueueCountDto(SmsProviderType.STUB, "OPTOUT_BLOCKED", 3)));
        when(dao.countFailedOutboundByProviderAndErrorCode(sevenDaysAgo)).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 2),
                new SmsQueueCountDto(SmsProviderType.STUB, null, 1)));
        when(dao.countConsentBlockedOutboundByProviderAndReason(sevenDaysAgo)).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "SMS_CONSENT_OPT_OUT", 4)));

        SmsQueueViewModel model =
                assembler(() -> false).assemble(SmsQueueWindow.LAST_7_DAYS, true, patient -> true).model();

        SmsQueueViewModel.ProviderQueue stub = provider(model, "STUB");
        assertThat(stub.failedCount()).isEqualTo(3);
        assertThat(stub.failedTotal()).isEqualTo(9);
        assertThat(stub.blockedCount()).isEqualTo(4);
        assertThat(stub.blockedTotal()).isEqualTo(8);
        verify(dao).findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), sevenDaysAgo, 50);
        verify(dao).findRecentOutboundByStatuses(SmsProviderType.STUB, BLOCKED, sevenDaysAgo, 50);
        // Overdue, stale and the counts by status are not limited to the time period.
        verify(dao).countOverdueQueuedOutboundByProvider(Date.from(NOW.minus(Duration.ofMinutes(5))));
        verify(dao).countOutboundByProviderAndStatus();
        assertThat(model.window()).isEqualTo("7d");
        assertThat(model.windowOptions()).containsExactly("7d", "30d", "90d", "all");
    }

    @Test
    @DisplayName("should pass no start time to the failed and blocked queries for all time")
    void shouldPassNoWindowStart_whenAllTime() {
        when(dao.countFailedOutboundByProviderAndErrorCode(isNull())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 2)));
        when(dao.countConsentBlockedOutboundByProviderAndReason(isNull())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "SMS_CONSENT_OPT_OUT", 4)));

        SmsQueueViewModel model =
                assembler(() -> false).assemble(SmsQueueWindow.ALL_TIME, true, patient -> true).model();

        verify(dao).countFailedOutboundByProviderAndErrorCode(null);
        verify(dao).countConsentBlockedOutboundByProviderAndReason(null);
        verify(dao).findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), null, 50);
        verify(dao).findRecentOutboundByStatuses(SmsProviderType.STUB, BLOCKED, null, 50);
        assertThat(model.window()).isEqualTo("all");
    }

    @Test
    @DisplayName("should show a run as finished when it ended while the scheduler state was being read")
    void shouldShowRunAsFinished_whenItEndedDuringTheRead() {
        Instant started = NOW.minus(Duration.ofSeconds(30));
        when(configService.storedSchedulerEnabled()).thenReturn(Optional.of(true));
        when(scheduler.isRunInProgress()).thenReturn(true);
        when(scheduler.lastRunStartedAt()).thenReturn(Optional.of(started));
        when(scheduler.lastCompletedRun()).thenReturn(Optional.of(new SmsQueueScheduler.CompletedRun(
                started, NOW.minus(Duration.ofSeconds(5)), SmsQueueScheduler.RunOutcome.COMPLETED, 3)));

        SmsQueueViewModel.Scheduler state = assemble(() -> true, true).scheduler();

        assertThat(state.runInProgress()).isFalse();
        assertThat(state.lastRunOutcome()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("should show a queued row's due time, falling back to its creation when no next attempt is set")
    void shouldShowDueTime_forQueuedRowsOnly() {
        when(dao.countOverdueQueuedOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 2L));
        when(dao.findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50))).thenReturn(List.of(
                new SmsQueueRowDto(7L, SmsProviderType.STUB, SmsStatus.QUEUED, null, RECIPIENT, 0, null, null,
                        NOW.minus(Duration.ofMinutes(30)), NOW.minus(Duration.ofMinutes(30)), null, null),
                new SmsQueueRowDto(8L, SmsProviderType.STUB, SmsStatus.QUEUED, 99, RECIPIENT, 1,
                        "QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED", null, NOW.minus(Duration.ofMinutes(40)),
                        NOW.minus(Duration.ofMinutes(20)), NOW.minus(Duration.ofMinutes(10)),
                        NOW.minus(Duration.ofMinutes(20)))));

        List<SmsQueueViewModel.Row> overdue = provider(assemble(() -> false, true), "STUB").overdue().rows();

        assertThat(overdue).extracting(SmsQueueViewModel.Row::id, SmsQueueViewModel.Row::dueAt,
                        SmsQueueViewModel.Row::demographicNo)
                .containsExactly(
                        tuple("7", "2026-09-28 13:30", ""),
                        tuple("8", "2026-09-28 13:50", "99"));
    }

    @Test
    @DisplayName("should leave demographic numbers out when the viewer may not read demographics")
    void shouldHideDemographicNumbers_whenViewerCannotReadDemographics() {
        when(dao.countOverdueQueuedOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 1L));
        when(dao.findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50))).thenReturn(List.of(
                new SmsQueueRowDto(8L, SmsProviderType.STUB, SmsStatus.QUEUED, 99, RECIPIENT, 1, null, null,
                        NOW.minus(Duration.ofMinutes(40)), NOW.minus(Duration.ofMinutes(20)), null, null)));

        SmsQueueViewModel model = assemble(() -> false, false);

        assertThat(model.demographicNumbersShown()).isFalse();
        assertThat(provider(model, "STUB").overdue().rows()).extracting(SmsQueueViewModel.Row::demographicNo)
                .containsExactly("");
    }

    @Test
    @DisplayName("should expose no message text, full number, name or operator message on a row")
    void shouldHaveNoSensitiveComponent_onRowRecords() {
        List<String> rowComponents = componentNames(SmsQueueViewModel.Row.class);
        assertThat(rowComponents).containsExactly("id", "providerType", "status", "createdAt", "updatedAt", "dueAt",
                "lastAttemptAt", "attemptCount", "errorCode", "consentReasonCode", "demographicNo",
                "recipientLastFour");
        assertThat(rowComponents).noneMatch(name -> name.toLowerCase().matches(".*(body|message|phone|name).*"));
        // The projection the rows come from never selects the body or free-text messages either.
        assertThat(componentNames(SmsQueueRowDto.class))
                .noneMatch(name -> name.toLowerCase().matches(".*(body|message|metadata|from).*"));
    }

    @Test
    @DisplayName("should show the saved scheduler setting and this server's last completed run")
    void shouldShowScheduler_fromStoredSettingAndThisServer() {
        when(configService.storedSchedulerEnabled()).thenReturn(Optional.of(false));
        when(scheduler.isRunning()).thenReturn(true);
        when(scheduler.isRunInProgress()).thenReturn(true);
        when(scheduler.lastRunStartedAt()).thenReturn(Optional.of(NOW.minus(Duration.ofSeconds(30))));
        when(scheduler.lastCompletedRun()).thenReturn(Optional.of(new SmsQueueScheduler.CompletedRun(
                NOW.minus(Duration.ofMinutes(2)), NOW.minus(Duration.ofMinutes(1)),
                SmsQueueScheduler.RunOutcome.COMPLETED, 4)));

        SmsQueueViewModel.Scheduler state = assemble(() -> true, true).scheduler();

        assertThat(state).isEqualTo(new SmsQueueViewModel.Scheduler(false, true, true, true,
                "2026-09-28 13:59", "2026-09-28 13:59", "COMPLETED", 4));
    }

    @Test
    @DisplayName("should take the start time from the finished run when no run is in progress")
    void shouldShowFinishedRunsOwnStart_whenNoRunIsInProgress() {
        when(configService.storedSchedulerEnabled()).thenReturn(Optional.of(true));
        when(scheduler.isRunInProgress()).thenReturn(false);
        // A newer run has started since; the page must not pair its start with the older run's result.
        when(scheduler.lastRunStartedAt()).thenReturn(Optional.of(NOW.minus(Duration.ofSeconds(2))));
        when(scheduler.lastCompletedRun()).thenReturn(Optional.of(new SmsQueueScheduler.CompletedRun(
                NOW.minus(Duration.ofMinutes(3)), NOW.minus(Duration.ofMinutes(1)),
                SmsQueueScheduler.RunOutcome.SENDING_OFF, 0)));

        SmsQueueViewModel.Scheduler state = assemble(() -> true, true).scheduler();

        assertThat(state).isEqualTo(new SmsQueueViewModel.Scheduler(true, true, false, false,
                "2026-09-28 13:57", "2026-09-28 13:59", "SENDING_OFF", 0));
    }

    @Test
    @DisplayName("should fall back to the scheduler property while nothing is saved, and show no run yet")
    void shouldFallBackToProperty_whenNothingSaved() {
        when(configService.storedSchedulerEnabled()).thenReturn(Optional.empty());

        SmsQueueViewModel.Scheduler state = assemble(() -> true, true).scheduler();

        assertThat(state).isEqualTo(new SmsQueueViewModel.Scheduler(true, false, false, false, "", "", "", 0));
    }

    /** One STUB row in each of the four lists, with counts of one everywhere. */
    private void stubAllFourLists(SmsQueueRowDto overdue, SmsQueueRowDto stale, SmsQueueRowDto failed,
                                  SmsQueueRowDto blocked) {
        when(dao.countOutboundByProviderAndStatus()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "QUEUED", 1),
                new SmsQueueCountDto(SmsProviderType.STUB, "SENDING", 1),
                new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 1),
                new SmsQueueCountDto(SmsProviderType.STUB, "CONSENT_BLOCKED", 1)));
        when(dao.countOverdueQueuedOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 1L));
        when(dao.countStaleSendingOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 1L));
        when(dao.countFailedOutboundByProviderAndErrorCode(any())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 1)));
        when(dao.countConsentBlockedOutboundByProviderAndReason(any())).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "SMS_CONSENT_OPT_OUT", 1)));
        when(dao.findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50)))
                .thenReturn(List.of(overdue));
        when(dao.findStaleSendingOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50)))
                .thenReturn(List.of(stale));
        when(dao.findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), THIRTY_DAYS_AGO, 50))
                .thenReturn(List.of(failed));
        when(dao.findRecentOutboundByStatuses(SmsProviderType.STUB, BLOCKED, THIRTY_DAYS_AGO, 50))
                .thenReturn(List.of(blocked));
    }

    private static SmsQueueRowDto queued(Long id, Integer demographicNo) {
        return row(id, SmsStatus.QUEUED, demographicNo, null, null);
    }

    private static SmsQueueRowDto sending(Long id, Integer demographicNo) {
        return row(id, SmsStatus.SENDING, demographicNo, null, null);
    }

    private static SmsQueueRowDto failed(Long id, Integer demographicNo) {
        return row(id, SmsStatus.FAILED, demographicNo, "ERR_A", null);
    }

    private static SmsQueueRowDto blocked(Long id, Integer demographicNo) {
        return row(id, SmsStatus.CONSENT_BLOCKED, demographicNo, null, "SMS_CONSENT_OPT_OUT");
    }

    private static SmsQueueRowDto row(Long id, SmsStatus status, Integer demographicNo, String errorCode,
                                      String consentReasonCode) {
        return new SmsQueueRowDto(id, SmsProviderType.STUB, status, demographicNo, RECIPIENT, 0, errorCode,
                consentReasonCode, NOW.minus(Duration.ofMinutes(30)), NOW.minus(Duration.ofMinutes(30)), null, null);
    }

    /** The page's model for the default time period and a viewer who may open every patient. */
    private SmsQueueViewModel assemble(BooleanSupplier schedulerProperty, boolean showDemographicNumbers) {
        return assembler(schedulerProperty)
                .assemble(SmsQueueWindow.LAST_30_DAYS, showDemographicNumbers, patient -> true)
                .model();
    }

    private SmsQueueViewModelAssembler assembler(BooleanSupplier schedulerProperty) {
        return new SmsQueueViewModelAssembler(dao, configService, scheduler, restrictionLookup,
                Clock.fixed(NOW, ZoneOffset.UTC), schedulerProperty);
    }

    private static SmsQueueViewModel.ProviderQueue provider(SmsQueueViewModel model, String providerType) {
        return model.providers().stream()
                .filter(queue -> queue.providerType().equals(providerType))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> componentNames(Class<? extends Record> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
