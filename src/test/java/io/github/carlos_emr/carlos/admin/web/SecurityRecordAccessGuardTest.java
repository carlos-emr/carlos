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
package io.github.carlos_emr.carlos.admin.web;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.ProviderSitePK;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SecurityRecordAccessGuard}.
 *
 * @since 2026-05-31
 */
@DisplayName("SecurityRecordAccessGuard")
@Tag("unit")
@Tag("admin")
@Tag("security")
class SecurityRecordAccessGuardTest extends CarlosUnitTestBase {

    @Mock
    private SecurityDao securityDao;
    @Mock
    private ProviderSiteDao providerSiteDao;

    private static ProviderSite providerSite(String providerNo, int siteId) {
        ProviderSite providerSite = new ProviderSite();
        providerSite.setId(new ProviderSitePK(providerNo, siteId));
        return providerSite;
    }

    private static LoggedInInfo loggedInInfoForFacility(int facilityId) {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        Facility facility = new Facility();
        facility.setId(facilityId);
        loggedInInfo.setCurrentFacility(facility);
        return loggedInInfo;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("should return null when security id parameter is missing or non numeric")
    void shouldReturnNull_whenSecurityIdParameterIsMissingOrNonNumeric() {
        SecurityRecordAccessGuard guard = new SecurityRecordAccessGuard(securityDao, providerSiteDao);

        assertThat(guard.parseSecurityId(null)).isNull();
        assertThat(guard.parseSecurityId("   ")).isNull();
        assertThat(guard.parseSecurityId("abc")).isNull();
        assertThat(guard.parseSecurityId(" 42 ")).isEqualTo(42);
    }

    @Test
    @DisplayName("should allow access when provider belongs to current facility")
    void shouldAllowAccess_whenProviderBelongsToCurrentFacility() {
        SecurityRecordAccessGuard guard = new SecurityRecordAccessGuard(securityDao, providerSiteDao);
        LoggedInInfo loggedInInfo = loggedInInfoForFacility(7);
        Security security = new Security();
        security.setProviderNo("prov-7");

        when(providerSiteDao.findByProviderNo("prov-7"))
                .thenReturn(List.of(providerSite("prov-7", 7), providerSite("prov-7", 8)));

        assertThat(guard.hasCurrentFacilityAccess(loggedInInfo, security)).isTrue();
    }

    @Test
    @DisplayName("should deny access when provider is outside current facility")
    void shouldDenyAccess_whenProviderIsOutsideCurrentFacility() {
        SecurityRecordAccessGuard guard = new SecurityRecordAccessGuard(securityDao, providerSiteDao);
        LoggedInInfo loggedInInfo = loggedInInfoForFacility(7);

        when(providerSiteDao.findByProviderNo("prov-9"))
                .thenReturn(List.of(providerSite("prov-9", 9)));

        assertThat(guard.hasCurrentFacilityAccess(loggedInInfo, "prov-9")).isFalse();
    }
}
