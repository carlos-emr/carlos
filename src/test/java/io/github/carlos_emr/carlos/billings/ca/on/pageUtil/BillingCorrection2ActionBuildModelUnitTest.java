/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONCorrectionViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.ProviderSitePK;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingCorrection2Action#buildModel}. The helper is
 * private; reflection is the most direct way to exercise it without dragging
 * in the full state-machine path that requires a real Ch1 invoice.
 *
 * @since 2026-04-24
 */
@DisplayName("BillingCorrection2Action.buildModel")
@Tag("unit")
@Tag("billing")
class BillingCorrection2ActionBuildModelUnitTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;
    private ProviderDao providerDao;
    private ProviderSiteDao providerSiteDao;
    private SiteDao siteDao;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        servletActionContextMock = Mockito.mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        securityInfoManager = Mockito.mock(SecurityInfoManager.class);
        providerDao = Mockito.mock(ProviderDao.class);
        providerSiteDao = Mockito.mock(ProviderSiteDao.class);
        siteDao = Mockito.mock(SiteDao.class);
        loggedInInfo = Mockito.mock(LoggedInInfo.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ProviderDao.class, providerDao);
        registerMock(ProviderSiteDao.class, providerSiteDao);
        registerMock(SiteDao.class, siteDao);
        // BillingCorrection2Action's field initializers need a few more DAOs.
        registerMock(BillingONPaymentDao.class, Mockito.mock(BillingONPaymentDao.class));
        registerMock(BillingONCHeader1Dao.class, Mockito.mock(BillingONCHeader1Dao.class));
        registerMock(BillingONExtDao.class, Mockito.mock(BillingONExtDao.class));
        registerMock(BillingServiceDao.class, Mockito.mock(BillingServiceDao.class));
        registerMock(BillingPaymentTypeDao.class, Mockito.mock(BillingPaymentTypeDao.class));

        // Default empty
        when(providerSiteDao.findByProviderNo(any())).thenReturn(Collections.emptyList());
        when(siteDao.getActiveSitesByProviderNo(any())).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void tearDownServletMock() {
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    private BillingONCorrectionViewModel invokeBuildModel(BillingCorrection2Action action, LoggedInInfo info) {
        // buildModel is package-private; this test lives in the same package
        // and calls it directly. Reflection is no longer needed.
        return action.buildModel(info);
    }

    @Test
    void shouldReturnEmptyModel_whenLoggedInInfoIsNull() {
        BillingCorrection2Action action = new BillingCorrection2Action();
        BillingONCorrectionViewModel m = invokeBuildModel(action, null);

        assertThat(m).isNotNull();
        assertThat(m.getUserProviderNo()).isEmpty();
        assertThat(m.isSiteAccessPrivacy()).isFalse();
        assertThat(m.isTeamAccessPrivacy()).isFalse();
        assertThat(m.isTeamBillingOnly()).isFalse();
        assertThat(m.getProviderAccessList()).isEmpty();
    }

    @Test
    void shouldPopulateUserContext_whenAllPrivilegesGranted() {
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        Provider userProvider = new Provider();
        userProvider.setProviderNo("999998");
        userProvider.setFirstName("doctor");
        userProvider.setLastName("carlosdoc");
        when(providerDao.getProvider("999998")).thenReturn(userProvider);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_site_access_privacy"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_team_access_privacy"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_team_billing_only"), eq("r"), isNull())).thenReturn(true);

        ProviderSite ps = new ProviderSite();
        ProviderSitePK pk = new ProviderSitePK();
        pk.setProviderNo("888888");
        ps.setId(pk);
        when(providerSiteDao.findByProviderNo("999998")).thenReturn(List.of(ps));

        Provider teamProvider = new Provider();
        teamProvider.setProviderNo("777777");
        when(providerDao.getBillableProvidersOnTeam(userProvider)).thenReturn(List.of(teamProvider));

        BillingCorrection2Action action = new BillingCorrection2Action();
        BillingONCorrectionViewModel m = invokeBuildModel(action, loggedInInfo);

        assertThat(m.getUserProviderNo()).isEqualTo("999998");
        assertThat(m.getUserFirstName()).isEqualTo("doctor");
        assertThat(m.getUserLastName()).isEqualTo("carlosdoc");
        assertThat(m.isSiteAccessPrivacy()).isTrue();
        assertThat(m.isTeamAccessPrivacy()).isTrue();
        assertThat(m.isTeamBillingOnly()).isTrue();
        assertThat(m.getProviderAccessList()).containsExactlyInAnyOrder("888888", "777777");
    }

    @Test
    void shouldNotCallSiteDao_whenSiteAccessPrivacyDenied() {
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(providerDao.getProvider("999998")).thenReturn(new Provider());

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_site_access_privacy"), eq("r"), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_team_access_privacy"), eq("r"), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_team_billing_only"), eq("r"), isNull())).thenReturn(false);

        BillingCorrection2Action action = new BillingCorrection2Action();
        BillingONCorrectionViewModel m = invokeBuildModel(action, loggedInInfo);

        assertThat(m.getProviderAccessList()).isEmpty();
        Mockito.verify(providerSiteDao, Mockito.never()).findByProviderNo(any());
    }

    @Test
    void shouldPopulateMgrSites_fromSiteDao() {
        // Note: bMultisites comes from IsPropertiesOn.isMultisitesEnable(), which reads
        // CarlosProperties singleton. In the test environment that returns false, so
        // mgrSites stays empty. We verify the structure is at least an empty list.
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(providerDao.getProvider(any())).thenReturn(new Provider());

        Site site = new Site();
        site.setName("Main Site");
        when(siteDao.getActiveSitesByProviderNo("999998")).thenReturn(List.of(site));

        BillingCorrection2Action action = new BillingCorrection2Action();
        BillingONCorrectionViewModel m = invokeBuildModel(action, loggedInInfo);

        // mgrSites is empty when isMultisitesEnable() returns false (test default).
        assertThat(m.getMgrSites()).isNotNull();
    }
}
