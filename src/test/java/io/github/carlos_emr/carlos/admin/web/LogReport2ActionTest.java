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
import java.util.Properties;
import java.util.Vector;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LogReport2Action}.
 *
 * @since 2026-04-19
 */
@DisplayName("LogReport2Action")
@Tag("unit")
@Tag("admin")
@Tag("security")
class LogReport2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager securityInfoManager;
    @Mock
    private ProviderDataDao providerDataDao;
    @Mock
    private OscarLogDao oscarLogDao;
    @Mock
    private LoggedInInfo loggedInInfo;

    private MockHttpServletRequest request;

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should return empty results when site-restricted user requests another provider")
    void shouldReturnEmptyResults_whenSiteRestrictedUserRequestsAnotherProvider() throws Exception {
        MockitoAnnotations.openMocks(this);

        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("submit", "true");
        request.setParameter("providerNo", "prov-outside-site");
        request.setParameter("content", "admin");
        request.setParameter("startDate", "2026-04-01");
        request.setParameter("endDate", "2026-04-19");
        request.getSession().setAttribute("user", "site-user");

        ProviderData allowedProvider = new ProviderData();
        allowedProvider.set("prov-allowed");
        allowedProvider.setFirstName("Alice");
        allowedProvider.setLastName("Smith");

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ProviderDataDao.class, providerDataDao);
        registerMock(OscarLogDao.class, oscarLogDao);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null)).thenReturn(true);
        when(providerDataDao.findByProviderSite("site-user")).thenReturn(List.of(allowedProvider));

        LogReport2Action action = new LogReport2Action();

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        @SuppressWarnings("unchecked")
        Vector<Properties> vec = (Vector<Properties>) request.getAttribute("vec");
        assertThat(vec).isEmpty();
        verify(oscarLogDao, never()).findForReport(any(), any(), any(), any(), any());
    }
}
