/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.dxresearch.bean.dxCodeSearchBeanHandler;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class dxResearchCodeSearch2Action extends ActionSupport {

    /**
     * Allowlist of valid coding system identifiers accepted as the {@code codeType}
     * request parameter. Values outside this set are rejected to prevent trust
     * boundary violations (CWE-501).
     */
    private static final Set<String> ALLOWED_CODE_TYPES = Set.of(
            "icd9", "icd10", "ichppccode", "SnomedCore", "msp"
    );

    /**
     * Pattern that research keyword parameters must match. Permits characters
     * found in diagnostic codes and short search terms (letters, digits, dots,
     * hyphens, spaces) and enforces a maximum length to limit the attack surface.
     */
    private static final Pattern RESEARCH_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9.\\-\\s]{0,100}$");

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_dxresearch", "r", null)) {
            throw new RuntimeException("missing required sec object (_dxresearch)");
        }

        // --- Trust boundary: validate codeType against the known-good allowlist ---
        String codeType = request.getParameter("codeType");
        if (codeType == null || !ALLOWED_CODE_TYPES.contains(codeType)) {
            MiscUtils.getLogger().warn("dxResearchCodeSearch: rejected invalid codeType from request");
            throw new RuntimeException("invalid codeType parameter");
        }

        // --- Trust boundary: sanitize xml_research keywords before use ---
        String[] xml_research = new String[5];
        String[] paramNames = {"xml_research1", "xml_research2", "xml_research3", "xml_research4", "xml_research5"};
        for (int i = 0; i < paramNames.length; i++) {
            String value = request.getParameter(paramNames[i]);
            if (value != null && RESEARCH_CODE_PATTERN.matcher(value).matches()) {
                xml_research[i] = value;
            } else {
                // Replace unrecognised input with empty string rather than propagating
                // potentially malicious data across the trust boundary into session.
                if (value != null) {
                    MiscUtils.getLogger().warn("dxResearchCodeSearch: sanitised invalid {} parameter", paramNames[i]);
                }
                xml_research[i] = "";
            }
        }

        dxCodeSearchBeanHandler hd = new dxCodeSearchBeanHandler(codeType, xml_research);
        HttpSession session = request.getSession();
        session.setAttribute("allMatchedCodes", hd);
        session.setAttribute("codeType", codeType);

        return SUCCESS;
    }
}
