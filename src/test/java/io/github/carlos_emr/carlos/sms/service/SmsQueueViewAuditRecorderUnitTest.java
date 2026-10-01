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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueWindow;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@DisplayName("SMS queue page-view audit records")
class SmsQueueViewAuditRecorderUnitTest {
    private final OscarLogDao oscarLogDao = mock(OscarLogDao.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

    @Test
    @DisplayName("should record the page view and then one row per distinct patient, with the patient set on the row")
    void shouldRecordOneRowPerPatient_whenPatientsAreShown() {
        Security security = mock(Security.class);
        when(security.getSecurityNo()).thenReturn(1001);
        when(loggedInInfo.getLoggedInSecurity()).thenReturn(security);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(loggedInInfo.getIp()).thenReturn("203.0.113.7");

        new SmsQueueViewAuditRecorder(oscarLogDao)
                .recordViewed(loggedInInfo, SmsQueueWindow.LAST_7_DAYS, List.of(456, 123, 456));

        assertThat(persisted(3))
                .extracting(OscarLog::getProviderNo, OscarLog::getSecurityId, OscarLog::getIp, OscarLog::getAction,
                        OscarLog::getContent, OscarLog::getDemographicId, OscarLog::getData)
                .containsExactly(
                        tuple("999998", 1001, "203.0.113.7", "read", "sms_queue", null, "window=7d patientsShown=2"),
                        tuple("999998", 1001, "203.0.113.7", "read", "sms_queue", 123, "window=7d"),
                        tuple("999998", 1001, "203.0.113.7", "read", "sms_queue", 456, "window=7d"));
    }

    @Test
    @DisplayName("should still record the page view when no patient was shown")
    void shouldRecordPageView_whenNoPatientIsShown() {
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        new SmsQueueViewAuditRecorder(oscarLogDao).recordViewed(loggedInInfo, SmsQueueWindow.ALL_TIME, List.of());

        assertThat(persisted(1)).singleElement().satisfies(log -> {
            assertThat(log.getProviderNo()).isEqualTo("999998");
            assertThat(log.getDemographicId()).isNull();
            assertThat(log.getData()).isEqualTo("window=all patientsShown=0");
        });
    }

    @Test
    @DisplayName("should keep demographic numbers out of the free text of every row")
    void shouldKeepDemographicNumbersOutOfFreeText() {
        new SmsQueueViewAuditRecorder(oscarLogDao)
                .recordViewed(loggedInInfo, SmsQueueWindow.LAST_30_DAYS, List.of(987654));

        assertThat(persisted(2)).allSatisfy(log -> {
            assertThat(log.getData()).doesNotContain("987654");
            assertThat(log.getContentId()).isNull();
        });
    }

    @Test
    @DisplayName("should let a failed write through so the caller shows nothing")
    void shouldPropagate_whenAWriteFails() {
        doThrow(new IllegalStateException("audit write failed")).when(oscarLogDao).persist(any(OscarLog.class));
        SmsQueueViewAuditRecorder recorder = new SmsQueueViewAuditRecorder(oscarLogDao);

        assertThatThrownBy(() -> recorder.recordViewed(loggedInInfo, SmsQueueWindow.LAST_30_DAYS, List.of(123)))
                .isInstanceOf(IllegalStateException.class);
    }

    private List<OscarLog> persisted(int times) {
        ArgumentCaptor<OscarLog> log = ArgumentCaptor.forClass(OscarLog.class);
        verify(oscarLogDao, times(times)).persist(log.capture());
        return log.getAllValues();
    }
}
