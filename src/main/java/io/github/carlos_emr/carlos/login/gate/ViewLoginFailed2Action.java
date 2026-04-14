/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.login.gate;

import org.apache.struts2.ActionSupport;

/**
 * Thin public shim that forwards to {@code /WEB-INF/jsp/login/loginfailed.jsp}.
 * Anonymous-reachable at {@code /login/LoginFailed.do}. Login2Action's
 * struts {@code <result name="error">} forwards to the WEB-INF JSP
 * directly; this action exists only for the rare external caller that
 * needs a client-redirectable public URL (e.g. JSP scriptlet
 * {@code sendRedirect}).
 */
public final class ViewLoginFailed2Action extends ActionSupport {

    @Override
    public String execute() throws Exception {
        return SUCCESS;
    }
}
