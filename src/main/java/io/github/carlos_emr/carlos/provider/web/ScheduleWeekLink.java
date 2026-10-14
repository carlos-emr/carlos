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
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Builds the link behind a week number on the schedule month view
 * ({@code appointmentprovideradminmonth.jsp}).
 *
 * <p>CARLOS has one week view: the day sheet requested with {@code weekView=true} and a
 * {@code provider_no}, the page the "W" button of a provider column opens
 * ({@code goWeekView} in {@code appointmentprovideradminday.jsp}). It shows one provider, and
 * the day sheet snaps any date inside the week to the start of that week. The month view used
 * to link its week numbers to {@code displaymode=week&dboperation=searchapptweek}, a mode that
 * has never been in the {@code providercontrol.jsp} dispatch table, so every click answered
 * the 404 error page (issue #4540).
 *
 * <p>A month that shows several providers ("All providers", or a group) has no single provider
 * to open the week for, so {@link #href} returns {@code null} there and the page renders the
 * week number as plain text.
 *
 * @since 2026-10-14
 */
public final class ScheduleWeekLink {

    /** {@code providerview} value of the month view's "All providers" choice. */
    private static final String ALL_PROVIDERS = "all";

    /** {@code providerview} prefix the month view gives a provider group. */
    private static final String GROUP_PREFIX = "_grp_";

    private ScheduleWeekLink() {
    }

    /**
     * Resolves the one provider the month view is showing.
     *
     * <p>Only reads the {@code providerview} value; whether that number belongs to an existing
     * provider is for the caller to check.
     *
     * @param providerView the month view's {@code providerview}: a provider number,
     *                     {@code "all"}, or {@code "_grp_<group>"}
     * @return the provider number, or {@code null} when the month shows several providers or
     * none is known
     */
    public static String singleProvider(String providerView) {
        if (providerView == null) {
            return null;
        }
        String provider = providerView.trim();
        if (provider.isEmpty() || ALL_PROVIDERS.equals(provider) || provider.startsWith(GROUP_PREFIX)) {
            return null;
        }
        return provider;
    }

    /**
     * Builds the week-view URL for a week number.
     *
     * @param contextPath  the web application context path ({@code request.getContextPath()})
     * @param year         year of any day in the week; the sheet snaps to the start of the week
     * @param month        1-based month of that day
     * @param day          day of month of that day
     * @param providerView the month view's {@code providerview}, see {@link #singleProvider}
     * @return the URL with the same parameters as the "W" button, the provider number encoded
     * for a query string; {@code null} when the month shows several providers. The caller
     * encodes the URL for the HTML context it is written into.
     */
    public static String href(String contextPath, int year, int month, int day, String providerView) {
        String provider = singleProvider(providerView);
        if (provider == null) {
            return null;
        }
        return (contextPath == null ? "" : contextPath)
                + "/provider/providercontrol?year=" + year
                + "&month=" + month
                + "&day=" + day
                + "&view=0&displaymode=day&dboperation=searchappointmentday&viewall=1"
                + "&weekView=true&provider_no=" + SafeEncode.forUriComponent(provider);
    }
}
