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
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.base.HibernateTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SecUserRoleDao#hasAdminRole(String)}.
 *
 * <p>An admin role assignment grants administrative access only while it is active
 * ({@code activeyn = 1}). An explicitly deactivated ({@code activeyn = 0}) or legacy
 * {@code NULL} admin assignment must not be treated as granting admin access.</p>
 *
 * @see SecUserRoleDao#hasAdminRole(String)
 */
@DisplayName("SecUserRoleDao hasAdminRole Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecUserRoleDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private SecUserRoleDao secUserRoleDao;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    /**
     * Persists a {@link Secuserrole} with an explicit activeyn value (which may be
     * {@code null} to model a legacy row that predates the column being populated).
     *
     * @param providerNo String the provider number
     * @param roleName   String the role name
     * @param activeyn   Integer the active status (1 = active, 0 = inactive, null = legacy)
     */
    private void persistRole(String providerNo, String roleName, Integer activeyn) {
        Secuserrole role = new Secuserrole();
        role.setProviderNo(providerNo);
        role.setRoleName(roleName);
        role.setOrgcd("ORG1");
        role.setActiveyn(activeyn);
        role.setLastUpdateDate(new Date());
        hibernateTemplate.execute(session -> { session.persist(role); return null; });
    }

    @Test
    @Tag("read")
    @DisplayName("should report admin access when the admin role is active")
    void shouldReportAdminAccess_whenAdminRoleIsActive() {
        persistRole("HAR100", "admin", 1);
        hibernateTemplate.flush();

        assertThat(secUserRoleDao.hasAdminRole("HAR100")).isTrue();
    }

    @Test
    @Tag("read")
    @DisplayName("should deny admin access when the admin role is inactive")
    void shouldDenyAdminAccess_whenAdminRoleIsInactive() {
        persistRole("HAR200", "admin", 0);
        hibernateTemplate.flush();

        assertThat(secUserRoleDao.hasAdminRole("HAR200")).isFalse();
    }

    @Test
    @Tag("read")
    @DisplayName("should deny admin access when the admin role has a legacy NULL activeyn")
    void shouldDenyAdminAccess_whenAdminRoleHasLegacyNullActiveyn() {
        persistRole("HAR300", "admin", null);
        hibernateTemplate.flush();

        assertThat(secUserRoleDao.hasAdminRole("HAR300")).isFalse();
    }

    @Test
    @Tag("read")
    @DisplayName("should deny admin access when the provider only has a non-admin active role")
    void shouldDenyAdminAccess_whenProviderOnlyHasNonAdminActiveRole() {
        persistRole("HAR400", "doctor", 1);
        hibernateTemplate.flush();

        assertThat(secUserRoleDao.hasAdminRole("HAR400")).isFalse();
    }
}
