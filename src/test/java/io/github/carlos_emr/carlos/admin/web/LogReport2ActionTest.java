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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    private ProviderData createProvider(String providerNo, String firstName, String lastName) {
        ProviderData provider = new ProviderData();
        // ProviderData uses the legacy set(String) mutator for provider_no.
        provider.set(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        return provider;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("submit", "true");
        request.setParameter("startDate", "2026-04-01");
        request.setParameter("endDate", "2026-04-19");
        request.getSession().setAttribute("user", "site-user");

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ProviderDataDao.class, providerDataDao);
        registerMock(OscarLogDao.class, oscarLogDao);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
    }

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
        request.setParameter("providerNo", "prov-outside-site");
        request.setParameter("content", "admin");

        ProviderData allowedProvider = createProvider("prov-allowed", "Alice", "Smith");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null)).thenReturn(true);
        // The action sorts the returned list in place, so the DAO must return a mutable list.
        when(providerDataDao.findByProviderSite("site-user")).thenReturn(new ArrayList<>(List.of(allowedProvider)));

        String result = new LogReport2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        @SuppressWarnings("unchecked")
        Vector<Properties> vec = (Vector<Properties>) request.getAttribute("vec");
        assertThat(vec).isEmpty();
        verify(oscarLogDao, never()).findForReport(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should query all allowed providers when site-restricted user requests all providers")
    void shouldQueryAllAllowedProviders_whenSiteRestrictedUserRequestsAllProviders() throws Exception {
        request.setParameter("providerNo", "*");
        request.setParameter("content", "admin");

        ProviderData allowed1 = createProvider("prov-a", "Alice", "Smith");
        ProviderData allowed2 = createProvider("prov-b", "Bob", "Jones");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null)).thenReturn(true);
        when(providerDataDao.findByProviderSite("site-user")).thenReturn(new ArrayList<>(List.of(allowed1, allowed2)));
        when(oscarLogDao.findForReport(any(), any(), any(), any(), any())).thenReturn(List.of());

        String result = new LogReport2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> siteProviderNosCaptor = ArgumentCaptor.forClass(List.class);
        verify(oscarLogDao).findForReport(any(), any(), eq("admin"), isNull(), siteProviderNosCaptor.capture());
        assertThat(siteProviderNosCaptor.getValue()).containsExactlyInAnyOrder("prov-a", "prov-b");
    }

    @Test
    @DisplayName("should query specific provider without site filter when user is unrestricted")
    void shouldQuerySpecificProvider_whenUnrestrictedUserRequestsProvider() throws Exception {
        request.setParameter("providerNo", "prov-target");
        request.setParameter("content", "login");

        ProviderData target = createProvider("prov-target", "Target", "Provider");

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null)).thenReturn(false);
        when(providerDataDao.findAllOrderByLastName()).thenReturn(new ArrayList<>(List.of(target)));

        OscarLog sampleLog = new OscarLog();
        sampleLog.setAction("read");
        sampleLog.setContent("login");
        sampleLog.setContentId("42");
        sampleLog.setIp("10.0.0.1");
        sampleLog.setProviderNo("prov-target");
        sampleLog.setDemographicId(7);
        sampleLog.setData("line1\nline2");
        when(oscarLogDao.findForReport(any(), any(), any(), any(), any())).thenReturn(List.of(sampleLog));

        String result = new LogReport2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(oscarLogDao).findForReport(any(), any(), eq("login"), eq("prov-target"), isNull());

        @SuppressWarnings("unchecked")
        Vector<Properties> vec = (Vector<Properties>) request.getAttribute("vec");
        assertThat(vec).hasSize(1);
        Properties row = vec.get(0);
        assertThat(row.getProperty("action")).isEqualTo("read");
        assertThat(row.getProperty("content")).isEqualTo("login");
        assertThat(row.getProperty("provider_no")).isEqualTo("prov-target");
        assertThat(row.getProperty("demographic_no")).isEqualTo("7");
        // The action pre-encodes and injects <br/> line breaks for the data column.
        assertThat(row.getProperty("data")).isEqualTo("line1<br/>line2");
    }
}
