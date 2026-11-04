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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationMrpOptionDto;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConsultationListProviderScopeResolver}: the site/team access privacy
 * scope behind the Consultations list Provider (MRP) filter (issue #3976).
 *
 * @since 2026-09-30
 */
@DisplayName("ConsultationListProviderScopeResolver")
@Tag("unit")
@Tag("consultation")
class ConsultationListProviderScopeResolverUnitTest {

    private SecurityInfoManager securityInfoManager;
    private ProviderDataDao providerDataDao;
    private LoggedInInfo loggedInInfo;
    private boolean multisites;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        providerDataDao = mock(ProviderDataDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        multisites = false;
    }

    private ConsultationListProviderScopeResolver resolver() {
        return new ConsultationListProviderScopeResolver(securityInfoManager, providerDataDao, () -> multisites);
    }

    private void grant(String objectName) {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq(objectName), eq("r"), isNull()))
                .thenReturn(true);
    }

    private static ProviderData provider(String providerNo) {
        ProviderData data = new ProviderData();
        data.set(providerNo);
        return data;
    }

    @Test
    @DisplayName("should not restrict when neither privacy object applies")
    void shouldNotRestrict_whenNoPrivacyObjectGranted() {
        assertThat(resolver().resolveAllowedProviderNos(loggedInInfo)).isEmpty();
        verify(providerDataDao, never()).findByProviderTeam(anyString());
        verify(providerDataDao, never()).findByProviderSite(anyString());
    }

    @Test
    @DisplayName("should restrict to the team under team access privacy, with or without multisite")
    void shouldRestrictToTeam_whenTeamAccessPrivacyGranted() {
        grant("_team_access_privacy");
        when(providerDataDao.findByProviderTeam("999998")).thenReturn(List.of(provider("101"), provider("102")));

        assertThat(resolver().resolveAllowedProviderNos(loggedInInfo)).contains(Set.of("101", "102"));
    }

    @Test
    @DisplayName("should ignore site access privacy when multisite is off")
    void shouldNotRestrict_whenSitePrivacyGrantedWithoutMultisite() {
        grant("_site_access_privacy");

        assertThat(resolver().resolveAllowedProviderNos(loggedInInfo)).isEmpty();
        verify(providerDataDao, never()).findByProviderSite(anyString());
    }

    @Test
    @DisplayName("should restrict to shared sites under site access privacy in multisite mode")
    void shouldRestrictToSite_whenSitePrivacyGrantedWithMultisite() {
        grant("_site_access_privacy");
        multisites = true;
        when(providerDataDao.findByProviderSite("999998")).thenReturn(List.of(provider("201")));

        assertThat(resolver().resolveAllowedProviderNos(loggedInInfo)).contains(Set.of("201"));
    }

    @Test
    @DisplayName("should let team privacy win when both privacy objects apply, as the JSP does")
    void shouldPreferTeam_whenBothPrivacyObjectsGranted() {
        grant("_site_access_privacy");
        grant("_team_access_privacy");
        multisites = true;
        when(providerDataDao.findByProviderTeam("999998")).thenReturn(List.of(provider("101")));

        assertThat(resolver().resolveAllowedProviderNos(loggedInInfo)).contains(Set.of("101"));
        verify(providerDataDao, never()).findByProviderSite(anyString());
    }

    @Test
    @DisplayName("should restrict to nobody when the privacy lookup finds no providers")
    void shouldRestrictToEmptySet_whenScopeLookupReturnsNothing() {
        grant("_team_access_privacy");
        when(providerDataDao.findByProviderTeam(anyString())).thenReturn(null);

        assertThat(resolver().resolveAllowedProviderNos(loggedInInfo)).contains(Set.of());
    }

    @Test
    @DisplayName("should keep, trim or drop a requested provider according to the scope")
    void shouldSanitizeRequestedProvider_forScope() {
        Optional<Set<String>> scoped = Optional.of(Set.of("101"));

        assertThat(ConsultationListProviderScopeResolver.sanitizeFilterProviderNo(" 101 ", scoped)).isEqualTo("101");
        assertThat(ConsultationListProviderScopeResolver.sanitizeFilterProviderNo("202", scoped)).isNull();
        assertThat(ConsultationListProviderScopeResolver.sanitizeFilterProviderNo("202", Optional.empty())).isEqualTo("202");
        assertThat(ConsultationListProviderScopeResolver.sanitizeFilterProviderNo("  ", Optional.empty())).isNull();
        assertThat(ConsultationListProviderScopeResolver.sanitizeFilterProviderNo(null, Optional.empty())).isNull();
    }

    @Test
    @DisplayName("should keep only in-scope dropdown options in their original order")
    void shouldRestrictOptions_forScope() {
        ConsultationMrpOptionDto a = new ConsultationMrpOptionDto("101", "A", "A");
        ConsultationMrpOptionDto b = new ConsultationMrpOptionDto("202", "B", "B");
        ConsultationMrpOptionDto c = new ConsultationMrpOptionDto("303", "C", "C");

        assertThat(ConsultationListProviderScopeResolver.restrictOptions(List.of(a, b, c), Optional.of(Set.of("303", "101"))))
                .containsExactly(a, c);
        assertThat(ConsultationListProviderScopeResolver.restrictOptions(List.of(a, b), Optional.empty()))
                .containsExactly(a, b);
        assertThat(ConsultationListProviderScopeResolver.restrictOptions(null, Optional.empty())).isEmpty();
    }
}
