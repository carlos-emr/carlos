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

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

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
 * <p>Two properties of the day sheet shape the link:
 * <ul>
 *   <li>It does not take its provider from {@code provider_no}. It lists the providers of the
 *   <em>viewer's saved schedule view</em> (a group, or just the viewer for the default view) and
 *   only uses {@code provider_no} to pick a column among them, so for a provider outside that
 *   list it would render another provider's week. The link therefore asks for the single-provider
 *   (zoom) view, {@code view=1&curProvider=&curProviderName=}, which lists exactly the provider
 *   named.</li>
 *   <li>When the viewer's saved schedule view <em>is</em> one provider, the sheet shows that
 *   provider whatever the URL says. A week number for any other provider is then not linked.</li>
 *   <li>The sheet's schedule-view selector hides the providers in the viewer's
 *   {@code MyGroupAccessRestriction} rows. The month page does not apply that restriction, so a
 *   restricted provider is not linked either: a week number must not be a new route to a
 *   schedule the selector withholds.</li>
 * </ul>
 *
 * <p>A month that shows several providers ("All providers", or a group) has no single provider
 * to open the week for, so {@link #weekTarget} returns {@code null} there and the page renders
 * the week number as plain text.
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
     * The provider a week number opens the week of.
     *
     * @param providerNo   provider number, as the day sheet's {@code provider_no}
     * @param providerName the provider's name as the day sheet prints it
     */
    public record Target(String providerNo, String providerName) {
    }

    /**
     * Resolves the one provider the month view is showing.
     *
     * <p>Only reads the {@code providerview} value; whether that number belongs to an existing
     * provider is for {@link #weekTarget} to check.
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
     * Decides whether the week numbers of the month can link to a week view, and for whom.
     *
     * @param providerView      the month view's {@code providerview}
     * @param savedScheduleView the viewer's saved schedule view ({@code ProviderPreference
     *                          .getMyGroupNo()}): a group number, a provider number, {@code ".default"}
     *                          or {@code null}
     * @param providerNameOf    looks a provider number up and returns the name the day sheet
     *                          prints for it, or {@code null} when no such provider exists
     * @param isRestricted      whether the viewer's {@code MyGroupAccessRestriction} rows hide this
     *                          provider; the day sheet's schedule-view selector does not offer such
     *                          a provider, and the month page does not apply the restriction itself
     * @return the provider to open, or {@code null} when the week numbers stay plain text: the
     * month shows several providers, the provider does not exist or is restricted for the viewer,
     * or the viewer's saved schedule view is a different provider, which the day sheet would show
     * instead
     */
    public static Target weekTarget(String providerView, String savedScheduleView,
                                    UnaryOperator<String> providerNameOf, Predicate<String> isRestricted) {
        String provider = singleProvider(providerView);
        if (provider == null || isRestricted.test(provider)) {
            return null;
        }
        String name = providerNameOf.apply(provider);
        if (name == null) {
            return null;
        }
        if (savedScheduleView != null && !savedScheduleView.equals(provider)
                && providerNameOf.apply(savedScheduleView) != null) {
            return null;
        }
        return new Target(provider, name);
    }

    /**
     * Builds the week-view URL for a week number.
     *
     * @param contextPath the web application context path ({@code request.getContextPath()})
     * @param year        year of any day in the week; the sheet snaps to the start of the week
     * @param month       1-based month of that day
     * @param day         day of month of that day
     * @param target      the provider whose week to open, from {@link #weekTarget}
     * @return the URL, with the provider number and name encoded for a query string; {@code null}
     * when {@code target} is {@code null}. The caller encodes the URL for the HTML context it is
     * written into.
     */
    public static String href(String contextPath, int year, int month, int day, Target target) {
        if (target == null) {
            return null;
        }
        String provider = SafeEncode.forUriComponent(target.providerNo());
        return (contextPath == null ? "" : contextPath)
                + "/provider/providercontrol?year=" + year
                + "&month=" + month
                + "&day=" + day
                + "&view=1&curProvider=" + provider
                + "&curProviderName=" + SafeEncode.forUriComponent(target.providerName())
                + "&displaymode=day&dboperation=searchappointmentday&viewall=1"
                + "&weekView=true&provider_no=" + provider;
    }
}
