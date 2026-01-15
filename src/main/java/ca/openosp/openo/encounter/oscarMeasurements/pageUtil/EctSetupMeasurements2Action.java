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

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ca.openosp.openo.encounter.oscarMeasurements.bean.EctMeasurementTypesBeanHandler;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.encounter.oscarMeasurements.bean.EctMeasuringInstructionBeanHandler;
import ca.openosp.openo.encounter.pageUtil.EctSessionBean;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class EctSetupMeasurements2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_measurement", "r", null)) {
            throw new SecurityException("missing required sec object (_measurement)");
        }

        HttpSession session = request.getSession();
        //EctMeasurementsForm frm = (EctMeasurementsForm) form;

        String groupName = request.getParameter("groupName");
        EctValidation ectValidation = new EctValidation();
        String css = ectValidation.getCssPath(groupName);
        java.util.Calendar calender = java.util.Calendar.getInstance();
        String day = Integer.toString(calender.get(java.util.Calendar.DAY_OF_MONTH));
        String month = Integer.toString(calender.get(java.util.Calendar.MONTH) + 1);
        String year = Integer.toString(calender.get(java.util.Calendar.YEAR));
        String today = year + "-" + month + "-" + day;

        request.setAttribute("groupName", groupName);
        request.setAttribute("css", css);
        EctSessionBean bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean");

        String demo = null;
        if (bean != null) {
            request.getSession().setAttribute("EctSessionBean", bean);
            demo = bean.getDemographicNo();
        } else {
            demo = request.getParameter("demographicNo");
        }
        request.setAttribute("demographicNo", demo);
        EctMeasurementTypesBeanHandler hd = new EctMeasurementTypesBeanHandler(groupName, demo);
        for (int i = 0; i < hd.getMeasurementTypeVector().size(); i++) {
            this.setValue("date-" + i, today);
        }
        //session.setAttribute("EctMeasurementsForm", frm);
        session.setAttribute("measurementTypes", hd);
        Vector mInstrcVector = hd.getMeasuringInstrcHdVector();
        for (int i = 0; i < mInstrcVector.size(); i++) {
            EctMeasuringInstructionBeanHandler mInstrcs = (EctMeasuringInstructionBeanHandler) mInstrcVector.elementAt(i);
            String mInstrcName = "mInstrcs" + i;
            session.setAttribute(mInstrcName, mInstrcs);
        }


        return "continue";
    }

    public final Map values = new HashMap();

    public void setValue(String key, Object value) {
        values.put(key, value);
    }

    public Object getValue(String key) {
        return values.get(key);
    }
}
