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
package io.github.carlos_emr.carlos.admin.web;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.AuditLogManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class AuditLogPurge2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.auditLogPurge", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.auditLogPurge)");
        }


        String dt = request.getParameter("dateBegin");
        boolean view = dt == null && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()));
        if (view) return SUCCESS;
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }
        if (dt == null || dt.isBlank()) {
            request.setAttribute("msg", "No date parameter was sent to the server!");
            return SUCCESS;
        }

        Date endDateToPurge = null;
        if (dt != null && dt.length() > 0) {
            try {
                if (!dt.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("date format", dt, 0);
                LocalDate date = LocalDate.parse(dt);
                if (date.getYear() < 1) throw new DateTimeParseException("date year", dt, 0);
                endDateToPurge = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException e) {
                request.setAttribute("msg", "Error parsing date. Should be in yyyy-MM-dd format");
            }
        }

        if (endDateToPurge != null) {
            AuditLogManager manager = SpringUtils.getBean(AuditLogManager.class);
            try {
                int numRecords = manager.purgeAuditLog(LoggedInInfo.getLoggedInInfoFromSession(request), endDateToPurge);
                request.setAttribute("msg", "Success. Removed " + numRecords + " records from the audit log.");
            } catch (Exception e) {
                // Raw client/SQL exceptions may contain audit data or credentials.
                // A missing cause must not itself become a 500 after a safe refusal.
                request.setAttribute("msg", "Error. Audit purge did not finish. Completed backups were retained; "
                        + "consult the server log before retrying.");
            }
        }


        return SUCCESS;
    }
}
