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
package io.github.carlos_emr.carlos.commn;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The clinic a login views on the multisite schedule pages counts only while the login is offered it.
 *
 * @since 2026-10-18
 */
@DisplayName("SiteChoice - offered clinics and the chosen clinic")
@Tag("unit")
@Tag("scheduling")
class SiteChoiceUnitTest {

    private static Site site(String name, String color) {
        Site site = new Site();
        site.setName(name);
        site.setBgColor(color);
        return site;
    }

    private static final List<Site> OFFERED = List.of(site("North Clinic", "#CCFFCC"), site("South Clinic", "#CCCCFF"));

    @Test
    @DisplayName("should keep a chosen clinic the login is offered")
    void shouldReturnSiteName_whenChosenSiteIsOffered() {
        assertThat(SiteChoice.offeredOrNull("South Clinic", OFFERED)).isEqualTo("South Clinic");
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "West Clinic", "south clinic", "South Clinic ", ""})
    @DisplayName("should treat a clinic the login is not offered as All Clinics")
    void shouldReturnNull_whenChosenSiteIsNotOffered(String requested) {
        assertThat(SiteChoice.offeredOrNull(requested, OFFERED)).isNull();
    }

    @Test
    @DisplayName("should treat no choice, or no offered clinics, as All Clinics")
    void shouldReturnNull_whenNothingIsChosenOrOffered() {
        assertThat(SiteChoice.offeredOrNull(null, OFFERED)).isNull();
        assertThat(SiteChoice.offeredOrNull("North Clinic", null)).isNull();
        assertThat(SiteChoice.offeredOrNull("North Clinic", List.of())).isNull();
    }

    @Test
    @DisplayName("should offer a login under access privacy only its own sites")
    void shouldOfferOwnSites_withAccessPrivacy() {
        SiteDao siteDao = mock(SiteDao.class);
        List<Site> own = List.of(site("North Clinic", "#CCFFCC"));
        when(siteDao.getActiveSitesByProviderNo("800001")).thenReturn(own);

        assertThat(SiteChoice.offeredSites(siteDao, "800001", true)).isSameAs(own);
        verify(siteDao, never()).getAllActiveSites();
    }

    @Test
    @DisplayName("should offer a login without access privacy every active site")
    void shouldOfferEveryActiveSite_withoutAccessPrivacy() {
        SiteDao siteDao = mock(SiteDao.class);
        when(siteDao.getAllActiveSites()).thenReturn(OFFERED);

        assertThat(SiteChoice.offeredSites(siteDao, "800001", false)).isSameAs(OFFERED);
        verify(siteDao, never()).getActiveSitesByProviderNo(anyString());
    }

    @Test
    void shouldLeaveSitePrivacyInactive_withTheShippedClinicConfiguration() throws IOException {
        Properties defaults = new Properties();
        try (var stream = Files.newInputStream(Path.of("src/main/resources/carlos.properties"))) {
            defaults.load(stream);
        }
        CarlosProperties config = mock(CarlosProperties.class);
        when(config.getProperty("multisites", "")).thenReturn(defaults.getProperty("multisites", ""));
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo login = mock(LoggedInInfo.class);
        when(security.hasPrivilege(login, "_site_access_privacy", "r", null)).thenReturn(true);
        try (var settings = mockStatic(CarlosProperties.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(config);
            assertThat(SiteChoice.isSiteAccessRestricted(security, login)).isFalse();
            verifyNoInteractions(security);
        }
    }

    @Test
    void shouldSkipTheSitePrivilegeLookup_forAnAbsentLogin() {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        try (var settings = mockStatic(IsPropertiesOn.class)) {
            settings.when(IsPropertiesOn::isMultisitesEnable).thenReturn(true);
            assertThat(SiteChoice.isSiteAccessRestricted(security, null)).isFalse();
            verifyNoInteractions(security);
        }
    }

    @ParameterizedTest
    @CsvSource({"false, true, false, false", "false, true, true, true", "true, true, false, true",
            "true, false, true, true", "true, false, false, false"})
    void shouldApplyTheSiteRestrictionOnlyInMultisiteMode_andKeepTeamPrivacyIndependent(
            boolean multisites, boolean siteGrant, boolean teamGrant, boolean restricted) {
        LoggedInInfo login = mock(LoggedInInfo.class);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(login, "_site_access_privacy", "r", null)).thenReturn(siteGrant);
        when(security.hasPrivilege(login, "_team_access_privacy", "r", null)).thenReturn(teamGrant);
        try (var settings = mockStatic(IsPropertiesOn.class)) {
            settings.when(IsPropertiesOn::isMultisitesEnable).thenReturn(multisites);
            assertThat(SiteChoice.isSiteAccessRestricted(security, login)).isEqualTo(multisites && siteGrant);
            assertThat(SiteChoice.hasAccessPrivacy(security, login)).isEqualTo(restricted);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"#FFF", "#ccffcc", "white", " #CCCCFF "})
    @DisplayName("should keep a plain CSS colour")
    void shouldKeepColor_whenColorIsPlainCss(String color) {
        assertThat(SiteChoice.cssColor(color, "")).isEqualTo(color.trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"red;display:none", "url(x)", "#12345", "#GGGGGG", "rgb(1,2,3)", "", "\"><b>"})
    @DisplayName("should use the fallback for anything but a plain CSS colour")
    void shouldReturnFallback_whenColorIsNotPlainCss(String color) {
        assertThat(SiteChoice.cssColor(color, "#FFFFFF")).isEqualTo("#FFFFFF");
        assertThat(SiteChoice.cssColor(null, "")).isEmpty();
    }
}
