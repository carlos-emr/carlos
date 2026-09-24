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

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsHistoryViewModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class SmsHistoryViewModelAssemblerUnitTest {
    private static final int DEMOGRAPHIC_NO = 123;

    @Mock
    private SmsTransactionDao smsTransactionDao;
    @Mock
    private DemographicManager demographicManager;
    @Mock
    private SecurityInfoManager securityInfoManager;
    @Mock
    private LoggedInInfo loggedInInfo;

    @Test
    @DisplayName("assemble pages 25 rows at a time and reports the page position")
    void shouldPageRows_byTwentyFive() {
        when(smsTransactionDao.countByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(60L);
        when(smsTransactionDao.findByDemographicNo(DEMOGRAPHIC_NO, 25, 25)).thenReturn(List.of());

        SmsHistoryViewModel model = assembler().assemble(loggedInInfo, DEMOGRAPHIC_NO, 2);

        assertThat(model)
                .extracting(SmsHistoryViewModel::page, SmsHistoryViewModel::pageCount,
                        SmsHistoryViewModel::totalCount, SmsHistoryViewModel::hasPreviousPage,
                        SmsHistoryViewModel::hasNextPage)
                .containsExactly(2, 3, 60L, true, true);
        verify(smsTransactionDao).findByDemographicNo(DEMOGRAPHIC_NO, 25, 25);
    }

    @Test
    @DisplayName("assemble clamps a page past the end to the last page")
    void shouldClampPage_whenPastTheEnd() {
        when(smsTransactionDao.countByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(60L);
        when(smsTransactionDao.findByDemographicNo(DEMOGRAPHIC_NO, 50, 25)).thenReturn(List.of());

        SmsHistoryViewModel model = assembler().assemble(loggedInInfo, DEMOGRAPHIC_NO, 9);

        assertThat(model.page()).isEqualTo(3);
        assertThat(model.hasNextPage()).isFalse();
    }

    @Test
    @DisplayName("assemble shows one empty page when the patient has no messages")
    void shouldShowOneEmptyPage_whenNoMessages() {
        when(smsTransactionDao.countByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(0L);
        when(smsTransactionDao.findByDemographicNo(DEMOGRAPHIC_NO, 0, 25)).thenReturn(List.of());

        SmsHistoryViewModel model = assembler().assemble(loggedInInfo, DEMOGRAPHIC_NO, 0);

        assertThat(model)
                .extracting(SmsHistoryViewModel::page, SmsHistoryViewModel::pageCount,
                        SmsHistoryViewModel::hasPreviousPage, SmsHistoryViewModel::hasNextPage)
                .containsExactly(1, 1, false, false);
        assertThat(model.rows()).isEmpty();
    }

    @Test
    @DisplayName("assemble formats rows for display and shows only the last four digits of the number")
    void shouldFormatRows_withRedactedNumber() {
        SmsTransaction sent = outbound(11L);
        sent.markProviderResult(SmsProviderSendResultDto.accepted("provider-1", SmsStatus.SENT));
        SmsTransaction blocked = outbound(12L);
        blocked.markConsentBlocked(SmsConsentDecisionDto.blocked(
                SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", "No SMS consent is recorded for this patient."));
        when(smsTransactionDao.countByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(2L);
        when(smsTransactionDao.findByDemographicNo(DEMOGRAPHIC_NO, 0, 25)).thenReturn(List.of(sent, blocked));

        SmsHistoryViewModel model = assembler().assemble(loggedInInfo, DEMOGRAPHIC_NO, 1);

        assertThat(model.rows()).hasSize(2);
        SmsHistoryViewModel.Row sentRow = model.rows().get(0);
        assertThat(sentRow)
                .extracting(SmsHistoryViewModel.Row::id, SmsHistoryViewModel.Row::direction,
                        SmsHistoryViewModel.Row::purpose, SmsHistoryViewModel.Row::status,
                        SmsHistoryViewModel.Row::phone, SmsHistoryViewModel.Row::bodyStored)
                .containsExactly("11", "Outbound", "Patient message", "Sent", "***1212", true);
        assertThat(sentRow.createdAt()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
        assertThat(sentRow.completedAt()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");

        SmsHistoryViewModel.Row blockedRow = model.rows().get(1);
        assertThat(blockedRow)
                .extracting(SmsHistoryViewModel.Row::status, SmsHistoryViewModel.Row::consentReason,
                        SmsHistoryViewModel.Row::bodyStored, SmsHistoryViewModel.Row::completedAt)
                .containsExactly("Consent blocked", "SMS_CONSENT_UNKNOWN", false, "");
        assertThat(model.rows()).allSatisfy(row ->
                assertThat(row.phone()).doesNotContain("416").doesNotContain("555"));
    }

    @Test
    @DisplayName("assemble offers message text only to users who may read it for this patient")
    void shouldReflectMessageBodyPrivilege_forPatient() {
        when(smsTransactionDao.countByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(0L);
        when(smsTransactionDao.findByDemographicNo(DEMOGRAPHIC_NO, 0, 25)).thenReturn(List.of());
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_msgSMS", SecurityInfoManager.READ, DEMOGRAPHIC_NO))
                .thenReturn(true, false);

        assertThat(assembler().assemble(loggedInInfo, DEMOGRAPHIC_NO, 1).canReadMessageBodies()).isTrue();
        assertThat(assembler().assemble(loggedInInfo, DEMOGRAPHIC_NO, 1).canReadMessageBodies()).isFalse();
    }

    @Test
    @DisplayName("assemble shows the patient's name as LAST, FIRST")
    void shouldShowPatientName_asLastFirst() {
        Demographic demographic = new Demographic();
        demographic.setLastName("SMSTEST");
        demographic.setFirstName("CONSENT");
        when(demographicManager.getDemographic(loggedInInfo, DEMOGRAPHIC_NO)).thenReturn(demographic);
        when(smsTransactionDao.countByDemographicNo(DEMOGRAPHIC_NO)).thenReturn(0L);
        when(smsTransactionDao.findByDemographicNo(DEMOGRAPHIC_NO, 0, 25)).thenReturn(List.of());

        SmsHistoryViewModel model = assembler().assemble(loggedInInfo, DEMOGRAPHIC_NO, 1);

        assertThat(model.patientDisplayName()).isEqualTo("SMSTEST, CONSENT");
        assertThat(model.demographicNo()).isEqualTo("123");
    }

    private SmsHistoryViewModelAssembler assembler() {
        return new SmsHistoryViewModelAssembler(smsTransactionDao, demographicManager, securityInfoManager);
    }

    private static SmsTransaction outbound(long id) {
        SmsTransaction transaction = SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(DEMOGRAPHIC_NO, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }
}
