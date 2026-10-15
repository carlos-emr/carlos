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

public class EctRemoveMeasurementMap2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Creates a new instance of EctEditMeasurementMapAction
     */
    public EctRemoveMeasurementMap2Action() {
    }

    public String execute() throws ServletException, IOException {
        // Writes only from the mapping forms' POST. Customize Measurements ▸ Remove/Remap
        // Measurement Mapping and the page's Search open the read-only ViewRemoveMeasurementMap
        // gate instead, so a GET here can only be a link, image tag or prefetch (issue #3335).
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null) || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.measurements", "w", null)) {

            String id = request.getParameter("id");
            String identifier = request.getParameter("identifier");
            String name = request.getParameter("name");
            String type = request.getParameter("type");
            String loinc_code = request.getParameter("loinc_code");
            // The recycle-bin entry is attributed to the signed-in provider, not to whatever
            // provider number the form carries.
            String providerNo = loggedInInfo.getLoggedInProviderNo();
            String outcome = "continue";

            if (id != null) {
                if (!id.matches("\\d{1,9}")) {
                    // No mapping row was chosen (or the id is not one): nothing to remove.
                    return "failure";
                }

                MeasurementMapConfig mmc = new MeasurementMapConfig();

                // these values will be set if the measurement is to be remapped instead of deleted
                // therefore it is first mapped then deleted
                if (identifier != null && name != null && type != null && loinc_code != null) {
                    if (!mmc.checkLoincMapping(loinc_code, type)) {
                        mmc.mapMeasurement(identifier, loinc_code, name, type);
                        mmc.removeMapping(id, providerNo);
                        outcome = "success";
                    } else {
                        outcome = "failedcheck";
                    }
                } else {
                    mmc.removeMapping(id, providerNo);
                    outcome = "success";
                }

            }

            return outcome;

        } else {
            throw new SecurityException("missing required sec object (_admin.measurements)");
        }

    }

}
