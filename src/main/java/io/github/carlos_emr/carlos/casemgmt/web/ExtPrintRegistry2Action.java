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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.casemgmt.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.casemgmt.util.ExtPrintRegistry;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * Authenticated chart print-extension registration endpoint. Only POST requests with
 * demographic write privilege may register; validation and capacity failures are explicit HTTP errors.
 *
 * @since 2019-10-11
 */
public class ExtPrintRegistry2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private static Logger logger = MiscUtils.getLogger();

    /**
     * Registers an extension using the authenticated POST contract.
     *
     * @return no view; registration is an AJAX operation
     * @throws IOException if an error response cannot be written
     */
    @Override
    public String execute() throws IOException {
        return register();
    }

    /**
     * Validates and registers an extension, including calls through the legacy method entry point.
     *
     * @return no view; malformed registrations receive 400 and a full registry receives 409
     * @throws IOException if an error response cannot be written
     */
    public String register() throws IOException {
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "w", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        String name = request.getParameter("name");
        String bean = request.getParameter("bean");
        try {
            ExtPrintRegistry.addEntry(name, bean);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid print extension registration");
            return NONE;
        } catch (IllegalStateException e) {
            response.sendError(HttpServletResponse.SC_CONFLICT, "Print extension registry is full");
            return NONE;
        }
        logger.info("ext print registry added {}:{}", LogSafe.sanitize(name), LogSafe.sanitize(bean)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
        return NONE;
    }
}
