/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;
import io.github.carlos_emr.carlos.commn.model.SecObjPrivilegePrimaryKey;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecObjPrivilegeDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
class SecObjPrivilegeDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private SecObjPrivilegeDao secObjPrivilegeDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Test
    @DisplayName("findByFormNamePrivilegeAndProviderNo should ignore inactive provider roles")
    void shouldIgnoreProviderRoles_whenInactive() {
        createRole("inactiveRole", "999901", 0);
        createPrivilege("inactiveRole", "testFormInactive", "|r|");
        entityManager.flush();
        entityManager.clear();

        List<Object[]> result = secObjPrivilegeDao.findByFormNamePrivilegeAndProviderNo(
                "testFormInactive", "|r|", "999901");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByFormNamePrivilegeAndProviderNo should return active provider roles")
    void shouldReturnProviderRoles_whenActive() {
        createRole("activeRole", "999902", 1);
        createPrivilege("activeRole", "testFormActive", "|w|");
        entityManager.flush();
        entityManager.clear();

        List<Object[]> result = secObjPrivilegeDao.findByFormNamePrivilegeAndProviderNo(
                "testFormActive", "|w|", "999902");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)[0]).isInstanceOf(SecObjPrivilege.class);
        assertThat(result.get(0)[1]).isInstanceOf(Secuserrole.class);
    }

    private void createRole(String roleName, String providerNo, int activeyn) {
        Secuserrole role = new Secuserrole();
        role.setRoleName(roleName);
        role.setProviderNo(providerNo);
        role.setOrgcd("R0000001");
        role.setActiveyn(activeyn);
        role.setLastUpdateDate(new Date());
        entityManager.persist(role);
    }

    private void createPrivilege(String roleName, String objectName, String privilegeCode) {
        SecObjPrivilege privilege = new SecObjPrivilege();
        privilege.setId(new SecObjPrivilegePrimaryKey(roleName, objectName));
        privilege.setPrivilege(privilegeCode);
        privilege.setPriority(1);
        entityManager.persist(privilege);
    }
}
