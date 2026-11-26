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

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.integration.mcedt.McedtSecurity;
import io.github.carlos_emr.carlos.integration.mcedt.McedtCredentialService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class User2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final McedtCredentialService credentials;

    public User2Action() {
        this(SpringUtils.getBean(McedtCredentialService.class));
    }

    User2Action(McedtCredentialService credentials) {
        this.credentials = java.util.Objects.requireNonNull(credentials);
    }

    @Override
    public String execute() throws Exception {
        McedtSecurity.requireRead(request);
        if ("changePassword".equals(request.getParameter("method"))) {
            return changePassword();
        }
        if ("cancel".equals(request.getParameter("method"))) {
            return cancel();
        }
        request.getSession().setAttribute("mcedtUsername", CarlosProperties.getInstance().getProperty("mcedt.service.user")); // nosemgrep: tainted-session-from-http-request -- value from application properties


        return SUCCESS;
    }

    public String changePassword() throws Exception {
        McedtSecurity.requireWrite(request);
        McedtSecurity.requirePost(request);
        var session = request.getSession();
        var login = LoggedInInfo.getLoggedInInfoFromSession(session);
        String actor = login == null ? null : login.getLoggedInProviderNo();
        if (actor == null) {
            throw new SecurityException("MCEDT password change requires an authenticated provider");
        }
        try {
            credentials.savePassword(getPassword(), actor);
            request.setAttribute("isPassChange", true);
        } catch (RuntimeException failure) {
            if (isConfirmedNotSaved(failure)) {
                MiscUtils.getLogger().warn("MCEDT password change failed; credential storage was not updated");
                request.setAttribute("isPassChange", false);
            } else {
                // The service is transactional: an exception that did not come from its own body (a lost
                // commit acknowledgement, a failed rollback, a connection that dropped) says nothing about
                // whether the new password was stored. Do not tell the administrator it was not.
                MiscUtils.getLogger().warn("MCEDT password change could not be confirmed; credential storage may have been updated");
                request.removeAttribute("isPassChange");
                request.setAttribute("isPassChangeUnconfirmed", true);
            }
        }

        return SUCCESS;
    }

    /**
     * Whether {@code failure} proves the password was NOT stored: the service's own refusal (it throws
     * {@link IllegalStateException} before or while writing, and its transaction then rolls back) or a
     * transaction the framework reports as rolled back. Anything else, such as an exception raised at commit,
     * leaves the outcome unknown.
     */
    static boolean isConfirmedNotSaved(RuntimeException failure) {
        return failure.getClass() == IllegalStateException.class
                || failure instanceof org.springframework.transaction.UnexpectedRollbackException;
    }

    public String cancel() throws Exception {
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
