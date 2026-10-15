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
package io.github.carlos_emr.carlos.admin.service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.daos.security.SecuserroleDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secuserrole;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persists a login with its first role against H2 and reads back exactly the rows Assign Role
 * to Provider would have written (issue #2689): the login, one active role assignment, and the
 * {@code OSCAR} case-management program membership carrying that role.
 */
@DisplayName("ProviderLoginCreationService persistence")
@Tag("integration")
@Tag("security")
@Tag("create")
@Transactional
@Isolated("Temporarily sets the caisi and CASEMANAGEMENT properties")
class ProviderLoginCreationServiceIntegrationTest extends CarlosTestBase {

    private static final String PROVIDER_NO = "926891";
    private static final String USER_NAME = "fixture2689";
    private static final String ROLE_NAME = "fixture_2689_role";
    private static final int ROLE_NO = 926891;
    private static final String[] PROPERTIES = {"caisi", "CASEMANAGEMENT"};

    @Autowired
    private SecurityDao securityDao;
    @Autowired
    @Qualifier("secUserRoleDao")
    private SecuserroleDao secuserroleDao;
    @Autowired
    private SecRoleDao secRoleDao;
    @Autowired
    private ProgramDao programDao;
    @Autowired
    @Qualifier("programProviderDAO")
    private ProgramProviderDAO programProviderDao;
    @Autowired
    private SecurityInfoManager securityInfoManager;
    @Autowired
    private ProviderDao providerDao;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private final Map<String, String> originalProperties = new HashMap<>();
    private ProviderLoginCreationService service;
    private Long programId;

    @BeforeEach
    void setUp() {
        for (String name : PROPERTIES) {
            originalProperties.put(name, CarlosProperties.getInstance().getProperty(name));
        }
        CarlosProperties.getInstance().setProperty("caisi", "off");
        CarlosProperties.getInstance().setProperty("CASEMANAGEMENT", "all");

        Provider provider = new Provider(PROVIDER_NO, "Fixture", "doctor", "F", "GP", "RoleOnCreate");
        provider.setStatus("1");
        providerDao.saveProvider(provider);
        entityManager.createNativeQuery("MERGE INTO secrole (role_no, role_name) KEY(role_no) VALUES (?1, ?2)")
                .setParameter(1, ROLE_NO)
                .setParameter(2, ROLE_NAME)
                .executeUpdate();
        Program program = programDao.getProgramByName(ProviderLoginCreationService.CASE_MANAGEMENT_PROGRAM_NAME);
        if (program == null) {
            program = new Program();
            program.setName(ProviderLoginCreationService.CASE_MANAGEMENT_PROGRAM_NAME);
            program.setType(Program.COMMUNITY_TYPE);
            entityManager.persist(program);
        }
        entityManager.flush();
        programId = program.getId().longValue();

        service = new ProviderLoginCreationService(securityDao, secuserroleDao, secRoleDao,
                programDao, programProviderDao, securityInfoManager);
    }

    @AfterEach
    void restoreProperties() {
        originalProperties.forEach((name, value) -> {
            if (value == null) {
                CarlosProperties.getInstance().remove(name);
            } else {
                CarlosProperties.getInstance().setProperty(name, value);
            }
        });
    }

    private static Security newLogin() {
        Security login = new Security();
        login.setUserName(USER_NAME);
        login.setProviderNo(PROVIDER_NO);
        login.setPassword("not-a-real-hash");
        login.setLastUpdateDate(new Date());
        return login;
    }

    @Test
    @DisplayName("should store the login, one active role and the OSCAR program membership with that role")
    void shouldStoreLoginRoleAndMembership_whenRoleIsConfigured() {
        service.createLoginWithRole(newLogin(), ROLE_NAME);
        entityManager.flush();
        entityManager.clear();

        List<Security> logins = securityDao.findByUserName(USER_NAME);
        assertThat(logins).hasSize(1);
        assertThat(logins.get(0).getProviderNo()).isEqualTo(PROVIDER_NO);

        @SuppressWarnings("unchecked")
        List<Secuserrole> roles = secuserroleDao.findActiveByProviderNo(PROVIDER_NO);
        assertThat(roles).extracting(Secuserrole::getRoleName).containsExactly(ROLE_NAME);
        assertThat(roles.get(0).getLastUpdateDate()).isNotNull();

        ProgramProvider membership = programProviderDao.getProgramProvider(PROVIDER_NO, programId);
        assertThat(membership).isNotNull();
        assertThat(membership.getRoleId()).isEqualTo((long) ROLE_NO);
    }

    @Test
    @DisplayName("should store nothing when the chosen role is not configured")
    void shouldStoreNothing_whenRoleIsUnknown() {
        assertThatThrownBy(() -> service.createLoginWithRole(newLogin(), "no_such_role_2689"))
                .isInstanceOf(ProviderLoginCreationService.UnknownRoleException.class);
        entityManager.flush();
        entityManager.clear();

        assertThat(securityDao.findByUserName(USER_NAME)).isEmpty();
        assertThat(secuserroleDao.findByProviderNo(PROVIDER_NO)).isEmpty();
        assertThat(programProviderDao.getProgramProvider(PROVIDER_NO, programId)).isNull();
    }
}
