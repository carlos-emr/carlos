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
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import org.apache.struts2.ActionSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.lab.service.Hl7PatientUnlinkService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class UnlinkDemographic2Action extends ActionSupport {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpServletRequest request = ServletActionContext.getRequest();
    private final HttpServletResponse response = ServletActionContext.getResponse();
    private final transient SecurityInfoManager security;
    private final transient Hl7PatientUnlinkService unlink;

    /** Struts creates this legacy route without arguments. */
    public UnlinkDemographic2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), SpringUtils.getBean(Hl7PatientUnlinkService.class));
    }

    UnlinkDemographic2Action(SecurityInfoManager security, Hl7PatientUnlinkService unlink) {
        this.security = security;
        this.unlink = unlink;
    }

    @Override
    public String execute() throws IOException {
        LoggedInInfo actor = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!security.hasPrivilege(actor, "_lab", "u", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        try {
            String[] reports = request.getParameterValues("labNo");
            String[] versions = request.getParameterValues("matchVersion");
            String[] reasons = request.getParameterValues("reason");
            if (reports == null || reports.length != 1 || versions == null || versions.length != 1
                    || reasons == null || reasons.length != 1) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            int report = Integer.parseInt(reports[0]);
            int patient = unlink.unlink(report, versions[0], reasons[0], actor, request.getRemoteAddr());
            var result = MAPPER.createObjectNode();
            result.put("success", true); result.put("unlinkedDemographicNo", patient);
            result.put("labNo", report); result.put("reason", reasons[0]);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            MAPPER.writeValue(response.getWriter(), result);
        } catch (Hl7PatientUnlinkService.StaleMatch stale) {
            response.sendError(HttpServletResponse.SC_CONFLICT, stale.getMessage());
        } catch (IllegalArgumentException invalid) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        } catch (SecurityException denied) {
            throw denied;
        } catch (RuntimeException failure) {
            // Clinical/DAO exception messages can contain results. Keep the response generic.
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn("HL7 unlink transaction failed: {}",
                    failure.getClass().getSimpleName());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unlink was not confirmed. Refresh the report and review its patient match before retrying.");
        }
        return NONE;
    }
}
