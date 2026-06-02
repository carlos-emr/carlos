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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.EpisodeDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.OscarRoleObjectPrivilege;

import java.util.Properties;
import java.util.Vector;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Encounter panel privilege regression")
@Tag("unit")
@Tag("encounter")
@Tag("security")
class EncounterPanelPrivilegeRegressionUnitTest extends CarlosUnitTestBase {

    private static final String ROLE_NAME = "doctor,999998";

    private AutoCloseable mocks;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<OscarRoleObjectPrivilege> rolePrivilegeMock;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private EpisodeDao episodeDao;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(EpisodeDao.class, episodeDao);

        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        rolePrivilegeMock = mockStatic(OscarRoleObjectPrivilege.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (rolePrivilegeMock != null) {
            rolePrivilegeMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should skip episode panel population when privilege is denied")
    void shouldSkipPanelPopulation_whenEpisodePrivilegeDenied() {
        PrivilegeStub privilegeStub = stubPrivilege("_newCasemgmt.episode", false);
        NavBarDisplayDAO dao = new NavBarDisplayDAO();

        boolean result = new EctDisplayEpisode2Action().getInfo(encounterSession(), request, dao);

        assertThat(result).isTrue();
        assertThat(dao.getLeftHeading()).isEmpty();
        assertThat(dao.getRightHeadingID()).isEmpty();
        assertThat(dao.getLeftPopup()).isNull();
        assertThat(dao.getRightPopup()).isNull();
        assertThat(dao.numItems()).isZero();
        assertThat(dao.numPopUpMenuItems()).isZero();
        verifyNoInteractions(episodeDao);
        rolePrivilegeMock.verify(() -> OscarRoleObjectPrivilege.getPrivilegeProp(privilegeStub.objectName()));
        rolePrivilegeMock.verify(() -> OscarRoleObjectPrivilege.checkPrivilege(
                ROLE_NAME, privilegeStub.properties(), privilegeStub.roles()));
    }

    @Test
    @DisplayName("should skip pregnancy panel population when privilege is denied")
    void shouldSkipPanelPopulation_whenPregnancyPrivilegeDenied() {
        PrivilegeStub privilegeStub = stubPrivilege("_newCasemgmt.pregnancy", false);
        NavBarDisplayDAO dao = new NavBarDisplayDAO();

        boolean result = new EctDisplayPregnancy2Action().getInfo(encounterSession(), request, dao);

        assertThat(result).isTrue();
        assertThat(dao.getLeftHeading()).isEmpty();
        assertThat(dao.getRightHeadingID()).isEmpty();
        assertThat(dao.getLeftPopup()).isNull();
        assertThat(dao.getRightPopup()).isNull();
        assertThat(dao.numItems()).isZero();
        assertThat(dao.numPopUpMenuItems()).isZero();
        verifyNoInteractions(episodeDao);
        rolePrivilegeMock.verify(() -> OscarRoleObjectPrivilege.getPrivilegeProp(privilegeStub.objectName()));
        rolePrivilegeMock.verify(() -> OscarRoleObjectPrivilege.checkPrivilege(
                ROLE_NAME, privilegeStub.properties(), privilegeStub.roles()));
    }

    private PrivilegeStub stubPrivilege(String objectName, boolean allowed) {
        Properties properties = new Properties();
        Vector<String> roles = new Vector<>();
        Vector<Object> privilegeConfig = new Vector<>();
        privilegeConfig.add(properties);
        privilegeConfig.add(roles);

        rolePrivilegeMock.when(() -> OscarRoleObjectPrivilege.getPrivilegeProp(objectName))
                .thenReturn(privilegeConfig);
        rolePrivilegeMock.when(() -> OscarRoleObjectPrivilege.checkPrivilege(ROLE_NAME, properties, roles))
                .thenReturn(allowed);

        return new PrivilegeStub(objectName, properties, roles);
    }

    private EctSessionBean encounterSession() {
        EctSessionBean bean = new EctSessionBean();
        bean.demographicNo = "123";
        bean.appointmentNo = "456";
        return bean;
    }

    private record PrivilegeStub(String objectName, Properties properties, Vector<String> roles) {
    }
}
