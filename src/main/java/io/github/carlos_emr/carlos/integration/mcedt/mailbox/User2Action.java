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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.integration.mcedt.mailbox;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.struts2.ActionSupport;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Struts2 action for managing MCEDT mailbox user settings and service
 * type configuration for the logged-in provider.
 *
 * @since 2013-06-14
 */
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
        request.getSession().setAttribute("mcedtUsername", CarlosProperties.getInstance().getProperty("mcedt.service.user"));

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

    @StrutsParameter
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    @StrutsParameter
    public void setPassword(String password) {
        this.password = password;
    }

    public String getPin() {
        return pin;
    }

    @StrutsParameter
    public void setPin(String pin) {
        this.pin = pin;
    }

    public String getPropname() {
        return propname;
    }

    @StrutsParameter
    public void setPropname(String propname) {
        this.propname = propname;
    }

    public String getOldPassword() {
        return oldPassword;
    }

    @StrutsParameter
    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    @StrutsParameter
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    @StrutsParameter
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}