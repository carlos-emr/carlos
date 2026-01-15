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

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import ca.openosp.openo.encounter.pageUtil.EctSessionBean;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class EctSetupDisplayHistory2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private EctMeasurementsDataBeanHandler measurementsData;

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_measurement", "w", null)) {
            throw new SecurityException("missing required sec object (_measurement)");
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        EctSessionBean bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");
        request.getSession().setAttribute("EctSessionBean", bean);
        String type = request.getParameter("type");
        MiscUtils.getLogger().debug("Type: " + type);
        if (bean != null) {
            Integer demo = Integer.valueOf(bean.getDemographicNo());
            request.setAttribute("demographicNo", demo);
            if (type != null) {
                measurementsData = new EctMeasurementsDataBeanHandler(demo, type);
                if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
                    List<EctMeasurementsDataBean> measures = (List<EctMeasurementsDataBean>) measurementsData.getMeasurementsDataVector();
                    EctMeasurementsDataBeanHandler.addRemoteMeasurements(loggedInInfo, measures, type, demo);
                }
                request.setAttribute("type", type);
            }
        } else {
            MiscUtils.getLogger().debug("cannot get the EctSessionBean");
        }
        return "continue";
    }

    public EctMeasurementsDataBeanHandler getMeasurementsData() {
        return measurementsData;
    }
}
