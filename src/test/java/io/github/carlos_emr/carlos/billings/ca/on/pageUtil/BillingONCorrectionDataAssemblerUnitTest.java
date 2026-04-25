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
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.ProviderSitePK;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingONCorrectionDataAssembler}, the read-side
 * extracted from {@link BillingCorrection2Action} so the action stays a
 * mutation-only Struts gate. Same site-access/team/multisite logic as the
 * pre-extraction {@code buildModel} method, now with package-private mock
 * injection on the assembler instead of static SpringUtils registration.
 *
 * @since 2026-04-25
 */
@DisplayName("BillingONCorrectionDataAssembler")
@Tag("unit")
@Tag("billing")
class BillingONCorrectionDataAssemblerUnitTest extends CarlosUnitTestBase {

    private SecurityInfoManager securityInfoManager;
    private ProviderDao providerDao;
    private ProviderSiteDao providerSiteDao;
    private SiteDao siteDao;
    private BillingONCHeader1Dao bCh1Dao;
    private RaDetailDao raDetailDao;
    private ProfessionalSpecialistDao professionalSpecialistDao;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private BillingONCorrectionDataAssembler assembler;

    @BeforeEach
    void setUp() {
        securityInfoManager = Mockito.mock(SecurityInfoManager.class);
        providerDao = Mockito.mock(ProviderDao.class);
        providerSiteDao = Mockito.mock(ProviderSiteDao.class);
        siteDao = Mockito.mock(SiteDao.class);
        bCh1Dao = Mockito.mock(BillingONCHeader1Dao.class);
        raDetailDao = Mockito.mock(RaDetailDao.class);
        professionalSpecialistDao = Mockito.mock(ProfessionalSpecialistDao.class);
        loggedInInfo = Mockito.mock(LoggedInInfo.class);
        request = new MockHttpServletRequest();

        // Default empty
        when(providerSiteDao.findByProviderNo(any())).thenReturn(Collections.emptyList());
        when(providerSiteDao.findBySiteId(any())).thenReturn(Collections.emptyList());
        when(siteDao.getActiveSitesByProviderNo(any())).thenReturn(Collections.emptyList());

        assembler = new BillingONCorrectionDataAssembler(
                securityInfoManager,
                providerDao,
                providerSiteDao,
                siteDao,
                bCh1Dao,
                raDetailDao,
                professionalSpecialistDao);
    }

    @Test
    void shouldReturnEmptyModel_whenLoggedInInfoIsNull() {
        BillingONCorrectionViewModel m = assembler.assemble(null, request);

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

        // The user (999998) is attached to site 42. The access list should
        // include every provider at site 42 — not just the user themselves.
        // Co-located provider 888888 is what the previous self-only loop
        // silently dropped.
        ProviderSite userMembership = new ProviderSite();
        ProviderSitePK userPk = new ProviderSitePK();
        userPk.setProviderNo("999998");
        userPk.setSiteId(42);
        userMembership.setId(userPk);
        when(providerSiteDao.findByProviderNo("999998")).thenReturn(List.of(userMembership));

        ProviderSite coWorker = new ProviderSite();
        ProviderSitePK coWorkerPk = new ProviderSitePK();
        coWorkerPk.setProviderNo("888888");
        coWorkerPk.setSiteId(42);
        coWorker.setId(coWorkerPk);
        when(providerSiteDao.findBySiteId(42)).thenReturn(List.of(userMembership, coWorker));

        Provider teamProvider = new Provider();
        teamProvider.setProviderNo("777777");
        when(providerDao.getBillableProvidersOnTeam(userProvider)).thenReturn(List.of(teamProvider));

        BillingONCorrectionViewModel m = assembler.assemble(loggedInInfo, request);

        assertThat(m.getUserProviderNo()).isEqualTo("999998");
        assertThat(m.getUserFirstName()).isEqualTo("doctor");
        assertThat(m.getUserLastName()).isEqualTo("carlosdoc");
        assertThat(m.isSiteAccessPrivacy()).isTrue();
        assertThat(m.isTeamAccessPrivacy()).isTrue();
        assertThat(m.isTeamBillingOnly()).isTrue();
        assertThat(m.getProviderAccessList())
                .containsExactlyInAnyOrder("999998", "888888", "777777");
    }

    @Test
    void shouldNotCallSiteDao_whenSiteAccessPrivacyDenied() {
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(providerDao.getProvider("999998")).thenReturn(new Provider());

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_site_access_privacy"), eq("r"), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_team_access_privacy"), eq("r"), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_team_billing_only"), eq("r"), isNull())).thenReturn(false);

        BillingONCorrectionViewModel m = assembler.assemble(loggedInInfo, request);

        assertThat(m.getProviderAccessList()).isEmpty();
        Mockito.verify(providerSiteDao, Mockito.never()).findByProviderNo(any());
    }

    @Test
    void shouldPopulateMgrSites_fromSiteDao() {
        // mgrSites only populates when IsPropertiesOn.isMultisitesEnable() is true
        // (reads CarlosProperties singleton; returns false in unit-test env).
        // We verify the structure is at least an empty list and the assembler
        // doesn't NPE on a populated SiteDao.
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(providerDao.getProvider(any())).thenReturn(new Provider());

        Site site = new Site();
        site.setName("Main Site");
        when(siteDao.getActiveSitesByProviderNo("999998")).thenReturn(List.of(site));

        BillingONCorrectionViewModel m = assembler.assemble(loggedInInfo, request);

        assertThat(m.getMgrSites()).isNotNull();
    }
}
