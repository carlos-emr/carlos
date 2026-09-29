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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@DisplayName("SMS queue page-view audit record")
class SmsQueueViewAuditRecorderUnitTest {
    private final OscarLogDao oscarLogDao = mock(OscarLogDao.class);
    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

    @Test
    @DisplayName("should record the viewer and the patients shown, sorted and once each")
    void shouldRecordViewerAndShownPatients_whenPageIsViewed() {
        Security security = mock(Security.class);
        when(security.getSecurityNo()).thenReturn(1001);
        when(loggedInInfo.getLoggedInSecurity()).thenReturn(security);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(loggedInInfo.getIp()).thenReturn("203.0.113.7");

        new SmsQueueViewAuditRecorder(oscarLogDao).recordViewed(loggedInInfo, List.of("456", "123", "456"));

        ArgumentCaptor<OscarLog> log = ArgumentCaptor.forClass(OscarLog.class);
        verify(oscarLogDao).persist(log.capture());
        assertThat(log.getValue())
                .extracting(OscarLog::getProviderNo, OscarLog::getSecurityId, OscarLog::getIp, OscarLog::getAction,
                        OscarLog::getContent, OscarLog::getData)
                .containsExactly("999998", 1001, "203.0.113.7", "read", "sms_queue",
                        "demographicNumbersShown=123,456");
    }

    @Test
    @DisplayName("should record an empty list when no patient number was shown")
    void shouldRecordEmptyList_whenNoPatientIsShown() {
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        new SmsQueueViewAuditRecorder(oscarLogDao).recordViewed(loggedInInfo, List.of());

        ArgumentCaptor<OscarLog> log = ArgumentCaptor.forClass(OscarLog.class);
        verify(oscarLogDao).persist(log.capture());
        assertThat(log.getValue().getData()).isEqualTo("demographicNumbersShown=");
    }
}
