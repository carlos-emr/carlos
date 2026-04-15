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
package io.github.carlos_emr.carlos.integration.mcedt.gate;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.integration.mcedt.McedtSecurity;

/**
 * Read-only view gate for MCEDT form JSPs that previously rendered by direct
 * JSP URL (e.g. {@code updateUpload.jsp}, {@code mailbox/addUpload.jsp}) but
 * now live under {@code /WEB-INF/jsp/}. Enforces the shared MCEDT privilege
 * check before the container forwards to the JSP result.
 *
 * @since 2026-03-20
 */
public final class ViewMcedtForm2Action extends ActionSupport {

    /**
     * Execute the privilege check and return success to forward to the JSP.
     *
     * @return String SUCCESS when the request is authorized
     * @throws Exception if the action fails during execution
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        McedtSecurity.requireRead(request);
        return SUCCESS;
    }
}
