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


/*
 * EctEditMeasurementMapAction.java
 *
 * Created on September 29, 2007, 2:22 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementMapConfig;

/**
 * @author wrighd
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctAddMeasurementMap2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);


    /**
     * Creates a new instance of EctEditMeasurementMapAction
     */
    public EctAddMeasurementMap2Action() {
    }

    public String execute() throws ServletException, IOException {
        String identifier = request.getParameter("identifier");
        String loinc_code = request.getParameter("loinc_code");

        // Dual-purpose route. Customize Measurements ▸ Add Measurement Mapping and the page's
        // Search open it by GET with no code to map, and it renders the form; a request that
        // names both an identifier and a code writes a mapping, so that must be the form's POST,
        // never a link or image tag (issue #3335). HttpMethodGuardFilter leaves this name to the
        // action for that reason.
        if (loinc_code != null && identifier != null && !"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin.measurements", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.measurements)");
        }

        return loinc_code != null && identifier != null ? map(identifier, loinc_code) : "continue";
    }

    /**
     * Maps a measurement identifier to a LOINC code, unless that code is already mapped for the
     * identifier's lab type.
     *
     * @param identifier "code,labType,name"; the name may itself contain commas
     * @param loincCode the code to map to; {@code 0} is the form's "None Selected"
     * @return the Struts result: {@code success}, {@code failedcheck} or {@code failure}
     */
    private String map(String identifier, String loincCode) {
        String[] measurement = identifier.split(",", 3);
        if (measurement.length < 3 || loincCode.isBlank() || "0".equals(loincCode)) {
            // "None Selected" in either list (both submit "0"): nothing to map.
            return "failure";
        }
        MeasurementMapConfig mmc = new MeasurementMapConfig();
        if (mmc.checkLoincMapping(loincCode, measurement[1])) {
            return "failedcheck";
        }
        mmc.mapMeasurement(measurement[0], loincCode, measurement[2], measurement[1]);
        return "success";
    }

}
