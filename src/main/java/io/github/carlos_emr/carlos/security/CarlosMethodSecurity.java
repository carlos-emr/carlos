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
package io.github.carlos_emr.carlos.security;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.springframework.stereotype.Component;

/**
 * Spring method-security expression helper that delegates to CARLOS privileges.
 *
 * <p>This adapter keeps {@code @PreAuthorize} expressions small while preserving
 * the existing {@link SecurityInfoManager} authorization model and session lookup
 * used by Struts 2Actions.</p>
 *
 * @since 2026-05-06
 */
// Bean name "carlosMethodSecurity" is referenced by @PreAuthorize SpEL expressions
// in 2Action classes (e.g. @carlosMethodSecurity.hasPrivilege(...)). Spring would
// auto-generate this name from the class name, but the explicit value keeps the
// contract visible here and guards against silent breakage on class rename.
@Component("carlosMethodSecurity")
public class CarlosMethodSecurity {

    private final SecurityInfoManager securityInfoManager;

    public CarlosMethodSecurity(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Checks a CARLOS security object and privilege for the current Struts request.
     *
     * @param secObject the CARLOS security object, such as {@code _admin}
     * @param privilege the CARLOS privilege level, such as {@code r}, {@code w}, or {@code d}
     * @return {@code true} when the current session has the requested privilege
     */
    public boolean hasPrivilege(String secObject, String privilege) {
        HttpServletRequest request = ServletActionContext.getRequest();
        if (request == null) {
            return false;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            return false;
        }

        return securityInfoManager.hasPrivilege(loggedInInfo, secObject, privilege, null);
    }
}
