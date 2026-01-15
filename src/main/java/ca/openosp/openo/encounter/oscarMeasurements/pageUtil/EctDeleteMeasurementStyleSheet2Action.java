//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.MeasurementCSSLocationDao;
import ca.openosp.openo.commn.dao.MeasurementGroupStyleDao;
import ca.openosp.openo.commn.model.MeasurementCSSLocation;
import ca.openosp.openo.commn.model.MeasurementGroupStyle;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.util.ConversionUtils;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class EctDeleteMeasurementStyleSheet2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

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
                            addActionError(getText("error.oscarEncounter.Measurements.cannotDeleteStyleSheet", new String[]{location.getLocation()}));
                            response.sendRedirect(request.getContextPath() + "/oscarEncounter/oscarMeasurements/DisplayMeasurementStyleSheet.jsp");
                            return NONE;
                        }

                        dao.remove(style);
                    }
                }
            }

            return SUCCESS;

        } else {
            throw new SecurityException("Access Denied!"); //missing required sec object (_admin)
        }
    }

    private String[] deleteCheckbox;

    public String[] getDeleteCheckbox() {
        return deleteCheckbox;
    }

    public void setDeleteCheckbox(String[] deleteCheckbox) {
        this.deleteCheckbox = deleteCheckbox;
    }
}
