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
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * JSON endpoint that adds or removes clinic number entries.
 *
 * <p>Replaces the legacy {@code /admin/clinicNbrManage.json.jsp} which performed
 * DAO writes with no application-level authorization check. All mutating
 * dispatches now require {@code _admin w} (or {@code _admin.userAdmin w})
 * privilege and POST.</p>
 *
 * @since 2026-05-19
 */
@Component(ClinicNbrManage2Action.SPRING_BEAN_NAME)
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ClinicNbrManage2Action extends ActionSupport {

    public static final String SPRING_BEAN_NAME = "clinicNbrManage2Action";

    private static final Logger logger = MiscUtils.getLogger();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final transient ClinicNbrDao clinicNbrDao;
    private final transient CarlosMethodSecurity methodSecurity;

    /**
     * Creates the action for Struts-managed instantiation paths.
     *
     * <p>Delegates to the injected constructor after retrieving collaborators
     * from the Spring context.</p>
     *
     * @throws org.springframework.beans.BeansException if a required Spring bean is unavailable
     * @since 2026-05-19
     */
    public ClinicNbrManage2Action() {
        this(SpringUtils.getBean(ClinicNbrDao.class),
                SpringUtils.getBean(CarlosMethodSecurity.class));
    }

    /**
     * Creates the action with explicit collaborators for Spring injection and tests.
     *
     * @param clinicNbrDao DAO used to add and remove clinic number records
     * @param methodSecurity method-level authorization facade for admin write privileges
     * @since 2026-05-19
     */
    @Autowired
    public ClinicNbrManage2Action(ClinicNbrDao clinicNbrDao, CarlosMethodSecurity methodSecurity) {
        this.clinicNbrDao = clinicNbrDao;
        this.methodSecurity = methodSecurity;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!methodSecurity.hasAdminWrite()) {
            throw new SecurityException("missing required sec object (_admin or _admin.userAdmin)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            logger.warn("Rejected clinic-nbr-manage request with method {} from {}",
                    LogSafe.sanitize(String.valueOf(request.getMethod())),
                    LogSafe.sanitize(String.valueOf(request.getRemoteAddr())));
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
            return NONE;
        }

        String method = request.getParameter("method");
        ObjectNode json = MAPPER.createObjectNode();
        json.put("method", method);
        boolean success = false;
        String error = "";

        if ("remove".equals(method)) {
            String nbr = request.getParameter("nbr");
            try {
                Integer valOld = Integer.parseInt(nbr);
                json.put("nbr", valOld);
                int i = clinicNbrDao.removeEntry(valOld);
                if (i == 0) {
                    error = "Remove Failure: Could not remove entry from database.";
                } else {
                    success = true;
                }
            } catch (NumberFormatException e) {
                error = "Remove Failure: Invalid clinic number.";
            }
        } else if ("add".equals(method)) {
            String valNew = request.getParameter("nbr");
            String desc = request.getParameter("nbrDesc");
            json.put("nbr", valNew);
            json.put("nbrDesc", desc);
            if (valNew == null || valNew.isEmpty()) {
                error = "Add Failure: Cannot add NBR with empty value.";
            } else {
                int j = clinicNbrDao.addEntry(valNew, desc);
                if (j == 0) {
                    error = "Add Failure: Could not add entry to database.";
                } else {
                    success = true;
                }
            }
        } else {
            error = "Invalid method supplied.";
        }

        json.put("success", success);
        json.put("error", error);

        writeJson(response, json);
        return NONE;
    }

    private void writeJson(HttpServletResponse response, Object payload) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        MAPPER.writeValue(response.getOutputStream(), payload);
    }
}
