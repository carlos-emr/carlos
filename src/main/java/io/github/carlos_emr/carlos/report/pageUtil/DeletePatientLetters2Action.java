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


package io.github.carlos_emr.carlos.report.pageUtil;

import java.io.IOException;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.report.data.ManageLetters;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Manage Letters' Delete: archives one letter template, so Manage Letters and Generate Letters stop
 * listing it (the row and its file are kept).
 *
 * <p>Archiving changes what every user is offered, so it needs {@code _report} write and a POST;
 * any other method gets 405 before anything is read. A missing or malformed id (400), an id with
 * no template (404) or a failed save (500) shows Manage Letters again with the
 * {@code letterDeleteFailed} message instead of a silent reload.</p>
 *
 * @since 2006-11-06
 */
public class DeletePatientLetters2Action extends ActionSupport {
    /** A report_letters id: a positive int, no sign or spaces. */
    private static final Pattern TEMPLATE_ID = Pattern.compile("[1-9][0-9]{0,8}");

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger log = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_report", "w", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        // Archiving is a mutation: refuse GET/HEAD before reading the id or touching the DAO.
        if (!"POST".equals(request.getMethod())) {
            try {
                response.setHeader("Allow", "POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException e) {
                log.warn("Unable to send 405 for non-POST letter template delete", e);
            }
            return NONE;
        }

        String reportId = request.getParameter("reportID");
        if (reportId == null || !TEMPLATE_ID.matcher(reportId).matches()) {
            return deleteFailure(HttpServletResponse.SC_BAD_REQUEST);
        }
        int id = Integer.parseInt(reportId);
        try {
            if (!new ManageLetters().archiveReport(id)) {
                return deleteFailure(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (RuntimeException e) {
            log.error("Letter template {} was not archived", LogSafe.sanitize(String.valueOf(id)), e);
            return deleteFailure(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        return SUCCESS;
    }

    private String deleteFailure(int status) {
        response.setStatus(status);
        request.setAttribute("letterDeleteFailed", Boolean.TRUE);
        return INPUT;
    }
}
