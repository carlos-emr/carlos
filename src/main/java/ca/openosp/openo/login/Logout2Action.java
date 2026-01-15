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

package ca.openosp.openo.login;

import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class Logout2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    public String execute() {
        String method = request.getParameter("method");

        if ("ssoLogout".equals(method)) {
            return ssoLogout();
        } else if ("sessionTimeout".equals(method)) {
            return sessionTimeout();
        } else if ("failure".equals(method)) {
            return failure();
        }

        // Default to logout method if unspecified
        return logout();
    }

    public String ssoLogout() {
        logout();
        return "logoutsso";
    }

    public String sessionTimeout() {
        return logout();
    }

    public String failure() {
        return logout();
    }

    public String logout() {

        HttpSession session = request.getSession(false);
        String login = request.getParameter("login");
        String errorMessage = request.getParameter("errorMessage");
        String nameId = request.getParameter("nameId");

        if (session != null) {
            String user = (String) session.getAttribute("user");
            session.invalidate();
            if (user != null) {
                LogAction.addLog(user, LogConst.LOGOUT, LogConst.CON_LOGIN, "", request.getRemoteAddr());
            }
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                cookie.setMaxAge(0);
                cookie.setPath("/");
                response.addCookie(cookie);
            }
        }
        return SUCCESS;
    }
}
