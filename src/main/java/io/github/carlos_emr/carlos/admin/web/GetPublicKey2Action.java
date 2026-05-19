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
package io.github.carlos_emr.carlos.admin.web;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.PrintWriter;

import io.github.carlos_emr.carlos.commn.model.PublicKey;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.web.admin.KeyManagerUIBean;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * JSON endpoint that returns the {@link PublicKey} record for a named service.
 *
 * <p>Replaces the legacy {@code /admin/keygen/getPublicKey.json.jsp} which had
 * no application-level authorization check. Requires {@code _admin r}
 * privilege; the underlying record exposes private key material so even read
 * access is restricted to administrators.</p>
 *
 * @since 2026-05-19
 */
@Component(GetPublicKey2Action.SPRING_BEAN_NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GetPublicKey2Action extends ActionSupport {

    public static final String SPRING_BEAN_NAME = "getPublicKey2Action";

    private final transient SecurityInfoManager securityInfoManager;

    public GetPublicKey2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        PublicKey publicKey = KeyManagerUIBean.getPublicKey(request.getParameter("id"));
        ObjectMapper mapper = new ObjectMapper();

        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            out.print(mapper.writeValueAsString(publicKey));
        }
        return NONE;
    }
}
