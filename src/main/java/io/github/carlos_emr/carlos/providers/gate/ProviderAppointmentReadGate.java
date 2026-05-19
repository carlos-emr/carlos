/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.providers.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Shared appointment-read gate used by provider schedule entry points.
 *
 * <p>The provider schedule landing JSP directly includes the day/month JSPs so the response
 * buffering filters can append their scripts without truncating the first render. Keep that direct
 * include paired with this shared privilege check so it remains aligned with the Struts view gates.</p>
 *
 * @since 2026-05-18
 */
public final class ProviderAppointmentReadGate {

    public static final String SECURITY_OBJECT = "_appointment";
    public static final String ACCESS_RIGHT = "r";

    private ProviderAppointmentReadGate() {
    }

    /**
     * Returns whether the current provider session may read appointment schedule views.
     */
    public static boolean hasAccess(SecurityInfoManager securityInfoManager, LoggedInInfo loggedInInfo) {
        return securityInfoManager != null
                && loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, SECURITY_OBJECT, ACCESS_RIGHT, null);
    }
}
