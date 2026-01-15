//CHECKSTYLE:OFF
/**
 * Copyright (c) 2014-2015. KAI Innovations Inc. All Rights Reserved.
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
package ca.openosp.openo.integration.mcedt.mailbox;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.OscarProperties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class User2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);

    @Override
    public String execute() throws Exception {
        if ("changePassword".equals(request.getParameter("method"))) {
            return changePassword();
        }
        if ("cancel".equals(request.getParameter("method"))) {
            return cancel();
        }
        request.getSession().setAttribute("mcedtUsername", OscarProperties.getInstance().getProperty("mcedt.service.user"));

        if (request.getSession().getAttribute("isPassChange") != null) {
            request.getSession().removeAttribute("isPassChange");
        }

        return SUCCESS;
    }

    public String changePassword() throws Exception {
        try {

            UserProperty prop = userPropertyDAO.getProp(UserProperty.MCEDT_ACCOUNT_PASSWORD);
            if (prop == null) {
                prop = new UserProperty();
                prop.setName(UserProperty.MCEDT_ACCOUNT_PASSWORD);
            }
            prop.setValue(this.getPassword());
            userPropertyDAO.saveProp(prop);
            request.getSession().setAttribute("isPassChange", "true");
        } catch (Exception e) {
            request.getSession().setAttribute("isPassChange", "false");
        }

        return SUCCESS;
    }

    public String cancel() throws Exception {
        if (request.getSession().getAttribute("isPassChange") != null) {
            request.getSession().removeAttribute("isPassChange");
        }
        if (request.getSession().getAttribute("mcedtUsername") != null) {
            request.getSession().removeAttribute("mcedtUsername");
        }
        return "cancel";
    }

    private String username;
    private String password;
    private String pin;
    private String propname;

    private String oldPassword;
    private String newPassword;
    private String confirmPassword;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public String getPropname() {
        return propname;
    }

    public void setPropname(String propname) {
        this.propname = propname;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}