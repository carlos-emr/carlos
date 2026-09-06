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
        String method = request.getParameter("method");

        // The privilege follows the method rather than gating the whole action on write.
        // updateDB rebuilds the DrugRef database and is a genuine mutation, so it keeps `w`.
        // verify and getLastUpdate only report status, and TopLinks2.jspf fires verify on every
        // Rx page load: gating those on `w` meant a prescriber with read-only _rx got a
        // SecurityException, the HTML 500 page in place of JSON, and therefore the permanent
        // "Drugref database is unavailable. Contact support." banner from that page's .catch --
        // on every visit, with DrugRef perfectly healthy.
        boolean mutating = "updateDB".equals(method);

        // updateDB rebuilds the DrugRef database, so it is a mutation and must not be reachable
        // by GET: a plain link or an <img src> would trigger a full rebuild, and CSRFGuard's
        // token check does not cover GET. Rejected before the privilege check so no side effect
        // — and no privilege probe — can hang off the wrong method. See the GET/HEAD rejection
        // contract in CLAUDE.md.
        if (mutating && !"POST".equals(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }


        // The gate follows the AUDIENCE of each method, not whether it mutates.
        //
        //  - `updateDB` and `status` belong to Administration > Update Drugref, which
        //    ViewUpdateDrugref2Action gates on `_admin` / `_admin.misc` read. A rebuild degrades
        //    prescribing for the thirty-odd minutes it takes, and `status` relays DrugRef's
        //    root-cause failure text -- a JDBC URL, a database host and user, a filesystem path.
        //    Both are administrative acts, so administration rights are what they require.
        //  - `verify` and `getLastUpdate` belong to prescribing: TopLinks2.jspf fires verify on
        //    every Rx page load. They carry only a date, a version and a database name.
        //
        // Requiring `_rx` on top of `_admin` for the first pair looked harmless and was not: an
        // administrator who is not a prescriber could open the page and then every call from it
        // failed with an HTML 500, which is the state this action exists to stop the page being
        // in. The admin page also fires `verify`, so administration rights satisfy that too.
        //
        // Evaluated lazily and cheapest-first, which matters here specifically: every
        // hasPrivilege call re-runs secUserRoleDao.findActiveByProviderNo (there is no role
        // cache), and TopLinks2.jspf fires `verify` on every Rx page load. Computing the
        // administration rights up front cost ordinary prescribers two extra role queries per
        // page load to answer a question `_rx` alone settles. The `||` order carries the
        // authorization, not just the performance: it is the same predicate either way, so
        // this stays a pure short-circuit and never widens who is allowed through.
        boolean administrative = mutating || "status".equals(method);
        if (administrative) {
            if (!hasAdministrationRights(loggedInInfo)) {
                throw new SecurityException("missing required sec object (_admin or _admin.misc)");
            }
        } else if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", "r", null)
                && !hasAdministrationRights(loggedInInfo)) {
            throw new SecurityException("missing required sec object (_rx)");
        }

        if (mutating) {
            return updateDB();
        } else if ("verify".equals(method)) {
            return verify();
        } else if ("status".equals(method)) {
            return status();
        }
        return getLastUpdate();
    }

    /**
     * @return whether the caller may perform the administrative operations of this action, which
     *         either administration security object grants. Not cached: each call re-reads the
     *         caller's active roles, so call it only on the branch that needs it.
     */
    private boolean hasAdministrationRights(LoggedInInfo loggedInInfo) {
        return securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)
                || securityInfoManager.hasPrivilege(loggedInInfo, "_admin.misc", "r", null);
    }

    public String updateDB() throws IOException, ServletException {
        Map<String, Object> d = new HashMap<>();
        // A null result means the call itself failed (DrugRef down, unreachable, or a fault):
        // the page shows that as an error rather than the silent nothing it used to render.
        d.put("result", runOrFallback("updateDB", () -> new RxDrugRef().updateDB(), null));
        writeJson(d);
        return NONE;
    }

    /**
     * Relays DrugRef's {@code getUpdateStatus}: whether the last update attempt is running,
     * succeeded or failed, and why. The admin page polls this after starting an update.
     *
     * <p>Falls back to {@code state=UNAVAILABLE} when DrugRef cannot answer, which is also what
     * a DrugRef build older than the method returns (an XML-RPC fault). The page then degrades
     * to the {@code verify} probe, whose {@code lastUpdate} still flips away from
     * {@code "updating"} when the run ends.</p>
     *
     * <p>The fallback comes from {@link RxDrugRef#unavailableStatus()} rather than being
     * hand-assembled, so it carries every key a successful answer carries and the two shapes
     * cannot drift apart. A client would otherwise see the documented keys on the success path
     * and {@code undefined} for the same keys on the outage path — the one path where it is
     * least able to cope with a surprise.</p>
     */
    private String status() throws IOException, ServletException {
        Map<String, String> fallback = RxDrugRef.unavailableStatus();
        writeJson(runOrFallback("getUpdateStatus", () -> new RxDrugRef().getUpdateStatus(), fallback));
        return NONE;
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
        return NONE;
    }

    private String getLastUpdate() throws IOException, ServletException {
        Map<String, String> d = new HashMap<>();
        d.put("lastUpdate", runOrFallback("getLastUpdateTime", () -> new RxDrugRef().getLastUpdateTime(), null));
        writeJson(d);
        return NONE;
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
