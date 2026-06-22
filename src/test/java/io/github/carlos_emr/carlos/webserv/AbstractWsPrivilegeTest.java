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

package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("AbstractWs privilege enforcement")
@Tag("unit")
@Tag("webservice")
class AbstractWsPrivilegeTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("requirePrivilege throws when access is missing")
    void shouldThrowSecurityException_whenAccessIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null))
                .thenReturn(false);

        TestAbstractWs service = new TestAbstractWs(loggedInInfo, securityInfoManager);

        assertThatThrownBy(() -> service.enforce("_appointment", "r"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");
    }

    @Test
    @DisplayName("schedule service checks appointment privilege before loading data")
    void shouldCheckAppointmentPrivilege_beforeLoadingData() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null))
                .thenReturn(true);
        when(scheduleManager.getAppointmentTypes()).thenReturn(Collections.emptyList());

        TestScheduleWs service = new TestScheduleWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);

        service.getAppointmentTypes();

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_appointment", "r", (String) null);
        verify(scheduleManager).getAppointmentTypes();
    }

    @Test
    @DisplayName("schedule service stops before loading data when privilege is missing")
    void shouldStopLoadingData_whenPrivilegeIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "r", null))
                .thenReturn(false);

        TestScheduleWs service = new TestScheduleWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);

        assertThatThrownBy(service::getAppointmentTypes)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_appointment)");

        verifyNoInteractions(scheduleManager);
    }


    @Test
    @DisplayName("provider properties allow self reads with pref privilege")
    void shouldAllowSelfReads_withPrefPrivilege() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ProviderManager2 providerManager = mock(ProviderManager2.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_pref", "r", null))
                .thenReturn(true);
        when(providerManager.getProviderProperties(loggedInInfo, "101", "faxnumber"))
                .thenReturn(List.of(new Property()));

        TestProviderWs service = new TestProviderWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "providerManager", providerManager);

        service.getProviderProperties("101", "faxnumber");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_pref", "r", (String) null);
        verify(providerManager).getProviderProperties(loggedInInfo, "101", "faxnumber");
    }

    @Test
    @DisplayName("provider properties require admin for cross-provider reads")
    void shouldRequireAdmin_forCrossProviderReads() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ProviderManager2 providerManager = mock(ProviderManager2.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null))
                .thenReturn(false);

        TestProviderWs service = new TestProviderWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "providerManager", providerManager);

        assertThatThrownBy(() -> service.getProviderProperties("202", "faxnumber"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");

        verifyNoInteractions(providerManager);
    }

    @Test
    @DisplayName("provider properties fall back to admin checks when no session is present")
    void shouldFallbackToAdminCheck_whenSessionIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        ProviderManager2 providerManager = mock(ProviderManager2.class);
        when(securityInfoManager.hasPrivilege((LoggedInInfo) null, "_admin", "r", null))
                .thenReturn(false);

        TestProviderWs service = new TestProviderWs(null, securityInfoManager);
        ReflectionTestUtils.setField(service, "providerManager", providerManager);

        assertThatThrownBy(() -> service.getProviderProperties("202", "faxnumber"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");

        verify(securityInfoManager).hasPrivilege(null, "_admin", "r", (String) null);
        verifyNoInteractions(providerManager);
    }

    @Test
    @DisplayName("demographic privilege checks pass null demographic numbers through unchanged")
    void shouldPassNullDemographicNumber_whenDemographicIdIsMissing() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        DemographicManager demographicManager = mock(DemographicManager.class);
        LoggedInInfo loggedInInfo = loggedInInfo("101");
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null))
                .thenReturn(false);

        TestDemographicWs service = new TestDemographicWs(loggedInInfo, securityInfoManager);
        ReflectionTestUtils.setField(service, "demographicManager", demographicManager);

        assertThatThrownBy(() -> service.getDemographic(null))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_demographic)");

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_demographic", "r", (String) null);
        verifyNoInteractions(demographicManager);
    }

    private static LoggedInInfo loggedInInfo(String providerNo) {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setLocale(Locale.CANADA);
        return loggedInInfo;
    }

    private static class TestAbstractWs extends AbstractWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestAbstractWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        void enforce(String objectName, String privilege) {
            requirePrivilege(objectName, privilege);
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestProviderWs extends ProviderWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestProviderWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestDemographicWs extends DemographicWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestDemographicWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }

    private static class TestScheduleWs extends ScheduleWs {
        private final LoggedInInfo loggedInInfo;
        private final SecurityInfoManager securityInfoManager;

        private TestScheduleWs(LoggedInInfo loggedInInfo, SecurityInfoManager securityInfoManager) {
            this.loggedInInfo = loggedInInfo;
            this.securityInfoManager = securityInfoManager;
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return loggedInInfo;
        }

        @Override
        protected SecurityInfoManager getSecurityInfoManager() {
            return securityInfoManager;
        }
    }
}
