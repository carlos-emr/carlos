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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Who may open, read and change document description templates (issue #5057).
 *
 * <p>A template is either <b>clinic-wide</b> ({@code provider_no} is {@code NULL}) or a provider's
 * <b>own</b> ({@code provider_no} names that provider). The two lists have different owners:</p>
 * <ul>
 *   <li>The clinic-wide list (Administration ▸ Document Description Template,
 *       {@code admin/DisplayDocumentDescriptionTemplate?setDefault=true}) belongs to administration:
 *       {@code _admin} or {@code _admin.document}, read to open it and write to change it.
 *       {@code _admin.document} gates nothing else in CARLOS; the Administration menu has always
 *       offered this page to its holders, so the page and its writes honour it too.</li>
 *   <li>A provider's own list (Preferences ▸ Document description template, the same page without
 *       {@code setDefault}) is a personal preference, like the signature and printer editors:
 *       {@code _pref} write. Only the provider it belongs to can see or change it; no role reaches
 *       another provider's own entries, since no page ever offered that.</li>
 *   <li>The shortcut buttons of Incoming Docs read the clinic-wide list, or the signed-in provider's
 *       own list, with {@code _edoc} read.</li>
 * </ul>
 *
 * <p>The page gate, {@code displayDocumentDescriptionTemplate.jsp} and the JSON/write endpoint
 * ({@link DocumentDescriptionTemplate2Action}) all apply these rules, so a page never opens for a
 * user whose writes would then be refused, and a crafted request cannot reach past the page.</p>
 *
 * @since 2026-10-22
 */
public final class DocumentDescriptionTemplateAccess {

    /** Request parameter that selects the clinic-wide list on the template page. */
    public static final String CLINIC_LIST_PARAMETER = "setDefault";

    private static final String[] CLINIC_LIST_OBJECTS = {"_admin", "_admin.document"};
    private static final String OWN_LIST_OBJECT = "_pref";
    private static final String INCOMING_DOCS_OBJECT = "_edoc";

    private DocumentDescriptionTemplateAccess() {
    }

    /**
     * Whether the template page request asks for the clinic-wide list rather than the provider's own.
     *
     * @param request the page request
     * @return {@code true} when {@code setDefault=true}
     */
    public static boolean isClinicListRequest(HttpServletRequest request) {
        return "true".equals(request.getParameter(CLINIC_LIST_PARAMETER));
    }

    /** Whether the user may open the clinic-wide list. */
    public static boolean canReadClinicList(SecurityInfoManager security, LoggedInInfo user) {
        return holdsAny(security, user, "r", CLINIC_LIST_OBJECTS);
    }

    /** Whether the user may add, change or delete clinic-wide templates. */
    public static boolean canEditClinicList(SecurityInfoManager security, LoggedInInfo user) {
        return holdsAny(security, user, "w", CLINIC_LIST_OBJECTS);
    }

    /** Whether the user may open and change their own templates and their template preference. */
    public static boolean canEditOwnList(SecurityInfoManager security, LoggedInInfo user) {
        return holdsAny(security, user, "w", OWN_LIST_OBJECT);
    }

    /**
     * Whether the user may read templates at all: from either list's page or from Incoming Docs.
     * Which entries they may read is decided per entry by {@link #requireRead}.
     */
    public static boolean canReadTemplates(SecurityInfoManager security, LoggedInInfo user) {
        return canEditOwnList(security, user) || canReadClinicList(security, user)
                || holdsAny(security, user, "r", INCOMING_DOCS_OBJECT);
    }

    /**
     * Refuses to open the template page for a user who may not use the requested list.
     *
     * @param clinicList {@code true} for the clinic-wide list, {@code false} for the user's own list
     * @throws SecurityException when the user may not open that list
     */
    public static void requireOpenPage(SecurityInfoManager security, LoggedInInfo user, boolean clinicList) {
        if (clinicList ? !canReadClinicList(security, user) : !canEditOwnList(security, user)) {
            throw clinicList ? missing(CLINIC_LIST_OBJECTS) : missing(OWN_LIST_OBJECT);
        }
    }

    /**
     * Refuses a user with no way to read templates; the endpoint's entry check.
     *
     * @throws SecurityException when the user can open neither list and cannot read Incoming Docs
     */
    public static void requireAnyTemplateRight(SecurityInfoManager security, LoggedInInfo user) {
        if (!canReadTemplates(security, user)) {
            throw new SecurityException("missing required sec object (" + OWN_LIST_OBJECT + " | "
                    + INCOMING_DOCS_OBJECT + " | " + String.join(" | ", CLINIC_LIST_OBJECTS) + ")");
        }
    }

    /**
     * Refuses reading templates owned by {@code ownerProviderNo}.
     *
     * @param ownerProviderNo {@code null} for the clinic-wide list, otherwise the owning provider
     * @throws SecurityException when the user may not read templates, or the templates are another
     *         provider's own
     */
    public static void requireRead(SecurityInfoManager security, LoggedInInfo user, String ownerProviderNo) {
        requireAnyTemplateRight(security, user);
        if (ownerProviderNo != null && !isSignedInProvider(user, ownerProviderNo)) {
            throw new SecurityException("document description templates belong to another provider");
        }
    }

    /**
     * Refuses adding, changing or deleting a template owned by {@code ownerProviderNo}.
     *
     * @param ownerProviderNo {@code null} for a clinic-wide template, otherwise the owning provider
     * @throws SecurityException when the user may not change the clinic-wide list, or the template is
     *         not the user's own, or the user may not change their own list
     */
    public static void requireWrite(SecurityInfoManager security, LoggedInInfo user, String ownerProviderNo) {
        if (ownerProviderNo == null) {
            if (!canEditClinicList(security, user)) {
                throw missing(CLINIC_LIST_OBJECTS);
            }
            return;
        }
        if (!isSignedInProvider(user, ownerProviderNo)) {
            throw new SecurityException("document description templates belong to another provider");
        }
        if (!canEditOwnList(security, user)) {
            throw missing(OWN_LIST_OBJECT);
        }
    }

    /**
     * Refuses saving the user's own template preference (clinic-wide list or own list).
     *
     * @throws SecurityException when the user may not change their own list
     */
    public static void requireOwnPreferenceWrite(SecurityInfoManager security, LoggedInInfo user) {
        if (!canEditOwnList(security, user)) {
            throw missing(OWN_LIST_OBJECT);
        }
    }

    private static boolean isSignedInProvider(LoggedInInfo user, String providerNo) {
        return user != null && providerNo.equals(user.getLoggedInProviderNo());
    }

    private static boolean holdsAny(SecurityInfoManager security, LoggedInInfo user, String right, String... objects) {
        if (user == null) {
            return false;
        }
        for (String object : objects) {
            if (security.hasPrivilege(user, object, right, null)) {
                return true;
            }
        }
        return false;
    }

    private static SecurityException missing(String... objects) {
        return new SecurityException("missing required sec object (" + String.join(" | ", objects) + ")");
    }
}
