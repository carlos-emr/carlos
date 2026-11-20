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
package io.github.carlos_emr.carlos.www;

import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.dao.FacilityMessageDao;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.services.OrganizationMessageManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link OrganizationMessage2Action}'s authorization split.
 *
 * <p>The facility-message banner on the provider schedule is a read-only broadcast: it must
 * render for any authenticated clinician. The management methods behind the same action
 * (list/edit/save) stay behind {@code _admin} write. See issue #3728.
 *
 * @since 2026-09-18
 */
@DisplayName("OrganizationMessage2Action Tests")
@Tag("unit")
@Tag("admin")
class OrganizationMessage2ActionUnitTest extends MessageBannerAuthorizationUnitTestBase {

    @Mock
    private OrganizationMessageManager mockOrganizationMessageManager;

    @Mock
    private FacilityDao mockFacilityDao;

    @Mock
    private FacilityMessageDao mockFacilityMessageDao;

    @Mock
    private ProgramManager mockProgramManager;

    @Mock
    private ProgramManager2 mockProgramManager2;

    private OrganizationMessage2Action action;

    @BeforeEach
    void setUp() {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(OrganizationMessageManager.class, mockOrganizationMessageManager);
        replaceSpringUtilsBean(FacilityDao.class, mockFacilityDao);
        replaceSpringUtilsBean(FacilityMessageDao.class, mockFacilityMessageDao);
        replaceSpringUtilsBean(ProgramManager.class, mockProgramManager);
        replaceSpringUtilsBean(ProgramManager2.class, mockProgramManager2);

        action = new OrganizationMessage2Action();
        injectField(action, "securityInfoManager", mockSecurityInfoManager);
        injectField(action, "mgr", mockOrganizationMessageManager);
        injectField(action, "facilityDao", mockFacilityDao);
        injectField(action, "facilityMessageDao", mockFacilityMessageDao);
        injectField(action, "programManager", mockProgramManager);
        injectField(action, "programManager2", mockProgramManager2);
    }


    @Override
    protected org.apache.struts2.ActionSupport bannerAction() {
        return action;
    }

    @Override
    protected void stubBannerRead() {
        when(mockProgramManager2.getCurrentProgramInDomain(any(LoggedInInfo.class), any()))
            .thenReturn(null);
        when(mockFacilityMessageDao.getMessagesByFacilityIdOrNullAndProgramIdOrNull(null, null))
            .thenReturn(java.util.Collections.emptyList());
    }

    @Override
    protected void verifyBannerRead() {
        // The facility view still scopes itself to the session's facility and the caller's own
        // program domain; this asserts it went through that path rather than short-circuiting.
        verify(mockFacilityMessageDao).getMessagesByFacilityIdOrNullAndProgramIdOrNull(null, null);
    }

    @Override
    protected Object managementCollaborator() {
        return mockOrganizationMessageManager;
    }

    @Override
    protected Object bannerCollaborator() {
        return mockFacilityMessageDao;
    }
}
