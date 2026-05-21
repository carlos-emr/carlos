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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.MeasurementCSSLocationDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementGroupStyleDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementCSSLocation;
import io.github.carlos_emr.carlos.commn.model.MeasurementGroupStyle;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;

import org.owasp.encoder.Encode;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class EctDeleteMeasurementStyleSheet2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws ServletException, IOException {

        if (securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null) || securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.measurements", "w", null)) {

            MeasurementGroupStyleDao dao = SpringUtils.getBean(MeasurementGroupStyleDao.class);
            MeasurementCSSLocationDao lDao = SpringUtils.getBean(MeasurementCSSLocationDao.class);

            if (deleteCheckbox != null) {
                for (int i = 0; i < deleteCheckbox.length; i++) {
                    List<MeasurementGroupStyle> styles = dao.findByCssId(ConversionUtils.fromIntString(deleteCheckbox[i]));

                    for (MeasurementGroupStyle style : styles) {
                        MeasurementCSSLocation location = lDao.find(ConversionUtils.fromIntString(deleteCheckbox[i]));
                        if (location != null) {
                            List<String> errors = new ArrayList<>();
                            // Error bundle contains embedded <li>...</li> and the JSP renders
                            // actionErrors unencoded, so HTML-encode the DB-sourced {0} value
                            // before substitution to prevent breakout of the list-item context.
                            errors.add(getText("error.encounter.Measurements.cannotDeleteStyleSheet",
                                    new String[]{Encode.forHtml(location.getLocation())}));
                            request.setAttribute("actionErrors", errors);
                            return "error";
                        }

                        dao.remove(style);
                    }
                }
            }

            return SUCCESS;

        } else {
            throw new SecurityException("Access Denied!"); //missing required security object: _admin
        }
    }

    private String[] deleteCheckbox;

    public String[] getDeleteCheckbox() {
        return deleteCheckbox;
    }

    @StrutsParameter
    public void setDeleteCheckbox(String[] deleteCheckbox) {
        this.deleteCheckbox = deleteCheckbox;
    }
}
