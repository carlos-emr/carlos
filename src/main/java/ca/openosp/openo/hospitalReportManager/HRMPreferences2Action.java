//CHECKSTYLE:OFF
/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package ca.openosp.openo.hospitalReportManager;

//import java.util.List;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class HRMPreferences2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    @Override
    public String execute() throws Exception {


        String userName = request.getParameter("userName");
        String location = request.getParameter("location");
        String interval = request.getParameter("interval");


        UserPropertyDAO userPropertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);

        try {
            UserProperty prop;

            if ((prop = userPropertyDao.getProp("hrm_username")) == null) {
                prop = new UserProperty();
            }
            prop.setName("hrm_username");
            prop.setValue(userName);
            userPropertyDao.saveProp(prop);

            if ((prop = userPropertyDao.getProp("hrm_location")) == null) {
                prop = new UserProperty();
            }
            prop.setName("hrm_location");
            prop.setValue(location);
            userPropertyDao.saveProp(prop);

            if ((prop = userPropertyDao.getProp("hrm_interval")) == null) {
                prop = new UserProperty();
            }
            prop.setName("hrm_interval");
            prop.setValue(interval);
            userPropertyDao.saveProp(prop);

            //SFTPConnector.setDownloadsDirectory(location);


            request.setAttribute("success", true);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Changing Preferences failed", e);
            request.setAttribute("success", false);
        }

        return SUCCESS;

    }


}
