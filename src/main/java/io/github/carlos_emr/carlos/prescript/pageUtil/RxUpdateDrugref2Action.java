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


/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package io.github.carlos_emr.carlos.prescript.pageUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

public class RxUpdateDrugref2Action extends ActionSupport {
    private static final Logger logger = MiscUtils.getLogger();

    /** RFC 8259 JSON content type. Replaces the legacy non-standard {@code text/x-json}. */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "w", null)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        if ("updateDB".equals(request.getParameter("method"))) {
            return updateDB();
        } else if ("verify".equals(request.getParameter("method"))) {
            return verify();
        }
        return getLastUpdate();
    }

    public String updateDB() throws IOException, ServletException {
        Map<String, Object> d = new HashMap<>();
        d.put("result", runOrFallback("updateDB", () -> new RxDrugRef().updateDB(), null));
        writeJson(d);
        return null;
    }

    private String verify() throws IOException, ServletException {
        // On failure, supply a payload with null fields — existing clients
        // (TopLinks2.jspf, updateDrugref.jsp) treat a null lastUpdate as
        // "DrugRef unavailable" and render a friendly banner instead of
        // the HTTP 500 errorpage.jsp painted into the Rx print-preview iframe.
        Map<String, String> fallback = new HashMap<>();
        fallback.put("lastUpdate", null);
        fallback.put("drugDatabase", null);
        fallback.put("version", null);
        writeJson(runOrFallback("verify", () -> new RxDrugRef().verify(), fallback));
        return null;
    }

    private String getLastUpdate() throws IOException, ServletException {
        Map<String, String> d = new HashMap<>();
        d.put("lastUpdate", runOrFallback("getLastUpdateTime", () -> new RxDrugRef().getLastUpdateTime(), null));
        writeJson(d);
        return null;
    }

    /**
     * Invokes a DrugRef call and returns its result, substituting {@code fallback}
     * (and logging) when the call throws. Failures are logged at {@code WARN} as a
     * compact one-liner and at {@code DEBUG} with the full stack trace, so that
     * repeated calls during a DrugRef outage (UI polling, admin retries) don't
     * flood the logs with stack traces at warn level.
     */
    private <T> T runOrFallback(String operation, Callable<T> call, T fallback) {
        try {
            return call.call();
        } catch (Exception e) {
            logger.warn("DrugRef {} failed; treating service as unavailable: {}", operation, e.toString());
            logger.debug("DrugRef {} failure details", operation, e);
            return fallback;
        }
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    private void writeJson(Object payload) throws IOException {
        response.setContentType(JSON_CONTENT_TYPE);
        ObjectNode json = (ObjectNode) objectMapper.valueToTree(payload);
        response.getWriter().write(json.toString());
    }
}
