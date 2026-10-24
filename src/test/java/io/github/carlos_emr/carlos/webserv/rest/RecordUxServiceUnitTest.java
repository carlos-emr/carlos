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
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.conversion.summary.Summary;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SummaryTo1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecordUxService} clinical-summary authorization.
 *
 * <p>Verifies that {@code getFullSummmary()} — the shared entry point for the
 * eight clinical-summary REST shortcuts (family history, medical history,
 * ongoing concerns, other meds, reminders, risk factors, allergies,
 * preventions) — enforces a patient-specific {@code _eChart} read privilege
 * before returning any PHI. Uses a testable subclass overriding
 * {@code getLoggedInInfo()} to avoid requiring the CXF HTTP request context.</p>
 *
 * @since 2026-06-24
 * @see RecordUxService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RecordUxService clinical-summary authorization")
@Tag("read")
class RecordUxServiceUnitTest extends CarlosUnitTestBase {

    private static final Integer DEMOGRAPHIC_NO = 12345;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private LoggedInInfo loggedInInfo;
    private RecordUxService service;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
        loggedInInfo.setIp("127.0.0.1");

        LoggedInInfo capturedInfo = loggedInInfo;
        service = new RecordUxService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should throw SecurityException without fetching summary when user lacks _eChart read privilege")
    void shouldThrowSecurityException_whenUserLacksEChartReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.getFullSummmary(DEMOGRAPHIC_NO, SummaryTo1.ALLERGIES))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_eChart)");

        // Bean lookup for the summary must never happen once access is denied.
        springUtilsMock.verify(() -> SpringUtils.getBean(anyString()), never());
    }

    @Test
    @DisplayName("should perform a patient-specific privilege check using the demographicNo")
    void shouldPerformPatientSpecificCheck_withDemographicNo() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> service.getFullSummmary(DEMOGRAPHIC_NO, SummaryTo1.MEDICALHISTORY_CODE))
                .isInstanceOf(SecurityException.class);

        verify(mockSecurityInfoManager).hasPrivilege(loggedInInfo, "_eChart", "r", DEMOGRAPHIC_NO);
    }

    @Test
    @DisplayName("should return the summary when user has _eChart read privilege")
    void shouldReturnSummary_whenUserHasEChartReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), anyInt())).thenReturn(true);

        SummaryTo1 expected = new SummaryTo1();
        Summary summaryBean = mock(Summary.class);
        when(summaryBean.getSummary(eq(loggedInInfo), eq(DEMOGRAPHIC_NO), eq(SummaryTo1.FAMILYHISTORY_CODE)))
                .thenReturn(expected);
        springUtilsMock.when(() -> SpringUtils.getBean(anyString())).thenReturn(summaryBean);

        SummaryTo1 result = service.getFullSummmary(DEMOGRAPHIC_NO, SummaryTo1.FAMILYHISTORY_CODE);

        assertThat(result).isSameAs(expected);
    }
}
