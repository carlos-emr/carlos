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


package ca.openosp.openo.report.oscarMeasurements.pageUtil;

import java.util.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public final class RptSelectCDMReport2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute()
            throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        HttpSession session = request.getSession();
        String CDMgroup = (String) this.getValue("CDMgroup");

        MiscUtils.getLogger().debug("The selected group is" + CDMgroup);
        RptMeasurementTypesBeanHandler hd = new RptMeasurementTypesBeanHandler(CDMgroup);
        Vector mInstrcVector = hd.getMeasuringInstrcBeanVector();

        for (int i = 0; i < mInstrcVector.size(); i++) {
            RptMeasuringInstructionBeanHandler mInstrcs = (RptMeasuringInstructionBeanHandler) mInstrcVector.elementAt(i);
            String mInstrcName = "mInstrcs" + i;
            session.setAttribute(mInstrcName, mInstrcs);

        }
        MiscUtils.getLogger().debug("the value of forward is :" + forward);
        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        int curDay = now.get(Calendar.DAY_OF_MONTH);
        String today = now.get(Calendar.YEAR) + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DATE);
        String lastYear = now.get(Calendar.YEAR) - 1 + "-" + (now.get(Calendar.MONTH) + 1) + "-" + now.get(Calendar.DATE);

        session.setAttribute("measurementTypes", hd);
        session.setAttribute("CDMGroup", CDMgroup);
        session.setAttribute("today", today);
        session.setAttribute("lastYear", lastYear);

        if (forward != null) {
            if (forward.compareTo("patientWhoMetGuideline") == 0) {
                return "patientWhoMetGuideline";
            } else if (forward.compareTo("patientInAbnormalRange") == 0) {
                return "patientInAbnormalRange";
            } else if (forward.compareTo("freqencyOfReleventTests") == 0) {
                return "freqencyOfReleventTests";
            } else if (forward.compareTo("medicationsPrescribed") == 0) {
                return "medicationsPrescribed";
            }
        }
        return "patientWhoMetGuideline";
    }

    private final Map values = new HashMap();

    public void setValue(String key, Object value) {
        values.put(key, value);
    }

    public Object getValue(String key) {
        return values.get(key);
    }

    private String forward;

    public String getForward() {
        return forward;
    }

    public void setForward(String forward) {
        this.forward = forward;
    }
}
