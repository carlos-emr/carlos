/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.casemgmt.common;

/**
 * JSP custom tag that conditionally renders its body based on the provider's
 * role-based access rights within the case management module.
 *
 * <p>Evaluates whether the specified provider has the given access type for
 * the specified access name, demographic number, and program. Supports an
 * optional {@code reverse} attribute to invert the access check logic.</p>
 *
 * @since 2026-03-17
 */
public class SecurityAccess extends BasicTag {

    private String accessName;
    private String accessType;
    private String providerNo;
    private String demoNo;
    private String programId;
    private String reverse;


    /**
     * Sets the name of the access right to check (e.g., "read", "write").
     *
     * @param accessName String the access right name
     */
    public void setAccessName(String accessName) {
        this.accessName = accessName;
    }

    /**
     * Sets the provider number whose access is being evaluated.
     *
     * @param providerNo String the healthcare provider identifier
     */
    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    /**
     * Sets the demographic (patient) number for the access check.
     *
     * @param demoNo String the patient demographic number
     */
    public void setDemoNo(String demoNo) {
        this.demoNo = demoNo;
    }

    /**
     * Sets the type of access being checked.
     *
     * @param accessType String the access type identifier
     */
    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    /**
     * Returns the program identifier for the access check.
     *
     * @return String the program ID
     */
    public String getProgramId() {
        return programId;
    }

    /**
     * Sets the program identifier for the access check.
     *
     * @param programId String the program ID
     */
    public void setProgramId(String programId) {
        this.programId = programId;
    }

    /**
     * Sets whether to invert the access check result.
     * When set to "true", the tag body is rendered only if the provider
     * does NOT have the specified access.
     *
     * @param reverse String "true" to invert the access check, any other value for normal behavior
     */
    public void setReverse(String reverse) {
        this.reverse = reverse;
    }

    /**
     * Evaluates the access check and determines whether to include the tag body.
     * Normalizes empty or "null" program IDs to "0" before performing the check.
     *
     * @return int {@code EVAL_BODY_INCLUDE} if access is granted (or denied when reversed),
     *         {@code SKIP_BODY} otherwise
     */
    public int doStartTag() {
        if ("".equalsIgnoreCase(programId) || "null".equalsIgnoreCase(programId)) programId = "0";
        boolean hasAccess = getCaseManagementManager().hasAccessRight(accessName, accessType, providerNo, demoNo, programId);
        if (reverse != null && reverse.equals("true")) {
            hasAccess = !hasAccess;
        }
        if (hasAccess) return EVAL_BODY_INCLUDE;
        else return SKIP_BODY;
    }
}
