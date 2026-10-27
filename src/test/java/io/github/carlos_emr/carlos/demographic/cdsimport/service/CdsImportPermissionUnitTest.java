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

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
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

    private static Fixture fixture() {
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilegeReadOnly(any(), eq("_demographic"), eq("w"), isNull())).thenReturn(true);
        var access = new CdsImportAccessService(security, new CdsImportConfigurationService(new Properties()), mock(CdsCourseService.class));
        var entities = mock(EntityManager.class);
        Security account = new Security(); account.setSecurityNo(9); account.setProviderNo("77");
        Provider provider = new Provider(); provider.setProviderNo("77"); provider.setStatus("1");
        when(entities.find(Security.class, 9)).thenReturn(account);
        when(entities.find(Provider.class, "77")).thenReturn(provider);
        ReflectionTestUtils.setField(access, "entityManager", entities);
        LoggedInInfo actor = new LoggedInInfo(); actor.setLoggedInSecurity(account); actor.setLoggedInProvider(provider);
        return new Fixture(access, security, actor);
    }
    private record Fixture(CdsImportAccessService access, SecurityInfoManager security, LoggedInInfo actor) { }
}
