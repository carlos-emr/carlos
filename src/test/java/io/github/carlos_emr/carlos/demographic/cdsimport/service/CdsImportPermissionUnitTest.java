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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderFacilityDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class CdsImportPermissionUnitTest {
    @Test void shouldRefuseClinicalMigration_whenUserCanOnlyEditDemographics() {
        var fixture = fixture();
        assertThatThrownBy(() -> fixture.access().requireOperatorSession(fixture.actor()))
                .isInstanceOf(SecurityException.class).hasMessageContaining("import administration");
    }

    @Test void shouldAllowExistingAdministrator_whenLiveAccountAndDemographicRightsRemainValid() {
        var fixture = fixture();
        when(fixture.security().hasPrivilegeReadOnly(any(), eq("_admin"), eq("w"), isNull())).thenReturn(true);
        assertThatCode(() -> fixture.access().requireOperatorSession(fixture.actor())).doesNotThrowAnyException();
    }

    @Test void shouldAllowExplicitMigrationCapability_withoutGrantingGeneralAdministration() {
        var fixture = fixture();
        when(fixture.security().hasPrivilegeReadOnly(any(), eq("_cds_import"), eq("w"), isNull())).thenReturn(true);
        assertThatCode(() -> fixture.access().requireOperatorSession(fixture.actor())).doesNotThrowAnyException();
        verify(fixture.security()).hasPrivilegeReadOnly(any(), eq("_admin"), eq("w"), isNull());
    }

    @Test void shouldRefuseExpiredAccount_afterAnEarlierOperatorCheck() {
        var fixture=fixture();
        when(fixture.security().hasPrivilegeReadOnly(any(),eq("_admin"),eq("w"),isNull())).thenReturn(true);
        fixture.access().requireOperatorSession(fixture.actor());
        Security expired=new Security();expired.setSecurityNo(9);expired.setProviderNo("77");
        expired.setBExpireset(1);expired.setDateExpiredate(new java.util.Date(0));
        when(fixture.accounts().find(9)).thenReturn(expired);
        assertThatThrownBy(()->fixture.access().requireOperatorSession(fixture.actor())).isInstanceOf(SecurityException.class);
        verify(fixture.accounts(),times(2)).find(9);
    }

    private static Fixture fixture() {
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilegeReadOnly(any(), eq("_demographic"), eq("w"), isNull())).thenReturn(true);
        var providers=mock(ProviderDao.class);var accounts=mock(SecurityDao.class);
        var access = new CdsImportAccessService(security, new CdsImportConfigurationService(new Properties()),
                mock(CdsCourseService.class),mock(CdsProgramContextService.class),providers,accounts,mock(ProviderFacilityDao.class));
        Security account = new Security(); account.setSecurityNo(9); account.setProviderNo("77");
        Provider provider = new Provider(); provider.setProviderNo("77"); provider.setStatus("1");
        when(accounts.find(9)).thenReturn(account);
        when(providers.getProvider("77")).thenReturn(provider);
        LoggedInInfo actor = new LoggedInInfo(); actor.setLoggedInSecurity(account); actor.setLoggedInProvider(provider);
        return new Fixture(access, security, actor, accounts);
    }
    private record Fixture(CdsImportAccessService access, SecurityInfoManager security, LoggedInInfo actor, SecurityDao accounts) { }
}
