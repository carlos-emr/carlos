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

import java.util.List;
import java.util.regex.Pattern;

import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The clinic (site) a login views on the multisite schedule pages.
 * <p>
 * A login is offered every active site, or, under site or team access privacy, only the sites it is linked to. The
 * schedule pages filter providers and appointments by the chosen clinic, so a chosen clinic counts only while it is
 * one of the offered sites: neither a remembered choice nor a request can make a page show another clinic.
 * Pages compute all of this per request; none of it may live in a JSP declaration, whose fields every login shares.
 *
 * @since 2026-10-18
 */
public final class SiteChoice {

    /** A CSS colour the pages may write into a style attribute: #rgb, #rrggbb or a named colour. */
    private static final Pattern CSS_COLOR = Pattern.compile("#[0-9A-Fa-f]{3}(?:[0-9A-Fa-f]{3})?|[A-Za-z]{1,30}");

    private SiteChoice() {
    }

    /**
     * Whether the login sees only its own sites (site or team access privacy).
     *
     * @param securityInfoManager the privilege lookup
     * @param loggedInInfo the login
     * @return true under {@code _site_access_privacy} or {@code _team_access_privacy} read
     */
    public static boolean hasAccessPrivacy(SecurityInfoManager securityInfoManager, LoggedInInfo loggedInInfo) {
        return securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_team_access_privacy", "r", null);
    }

    /**
     * The sites a login's clinic selector offers.
     *
     * @param siteDao the site lookup
     * @param providerNo the login's provider number
     * @param accessPrivacy whether the login holds site or team access privacy
     * @return the login's own active sites under access privacy, otherwise every active site
     */
    public static List<Site> offeredSites(SiteDao siteDao, String providerNo, boolean accessPrivacy) {
        return accessPrivacy ? siteDao.getActiveSitesByProviderNo(providerNo) : siteDao.getAllActiveSites();
    }

    /**
     * The chosen clinic, if the login is offered it.
     *
     * @param requested the site name a request or the session holds; {@code "none"} or null means All Clinics
     * @param offered the sites the login is offered
     * @return the matching offered site's name, or null (All Clinics) for anything else
     */
    public static String offeredOrNull(String requested, List<Site> offered) {
        if (requested == null || offered == null) {
            return null;
        }
        for (Site site : offered) {
            if (requested.equals(site.getName())) {
                return site.getName();
            }
        }
        return null;
    }

    /**
     * A site colour that is safe to write into a style attribute.
     *
     * @param color the stored colour
     * @param fallback what to use when the stored colour is missing or not a plain CSS colour
     * @return the colour, or the fallback
     */
    public static String cssColor(String color, String fallback) {
        return color != null && CSS_COLOR.matcher(color.trim()).matches() ? color.trim() : fallback;
    }
}
