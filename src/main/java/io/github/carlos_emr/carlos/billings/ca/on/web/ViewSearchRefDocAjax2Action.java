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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * AJAX endpoint for the referring-doctor autocomplete on
 * {@code billingON.jsp}. Returns a JSON array of suggestion objects
 * built by merging up to five search modes against
 * {@link ProfessionalSpecialistDao} and resolving the numeric
 * specialty code through {@link ConsultationServiceDao}.
 *
 * <p>Replaces the former {@code searchRefDocAjax.jsp} controller-in-a-JSP.
 * The specialty-name map is cached in {@link ServletContext} attribute
 * {@code specialtyNamesCache} so repeated keystrokes don't re-hit the DB.</p>
 *
 * <p>Returned shape (array element):
 * {@code {"value": "...", "lastName": "...", "firstName": "...",
 *         "specialtyType": "...", "streetAddress": "...",
 *         "phoneNumber": "...", "referralNo": "..."}}</p>
 *
 * @since 2026-04-26
 */
public class ViewSearchRefDocAjax2Action extends ActionSupport {

    private static final int MAX_RESULTS = 20;
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String SPECIALTY_CACHE_ATTR = "specialtyNamesCache";

    private final SecurityInfoManager securityInfoManager;
    private final ProfessionalSpecialistDao professionalSpecialistDao;
    private final ConsultationServiceDao consultationServiceDao;

    public ViewSearchRefDocAjax2Action(SecurityInfoManager securityInfoManager,
                                ProfessionalSpecialistDao professionalSpecialistDao,
                                ConsultationServiceDao consultationServiceDao) {
        this.securityInfoManager = securityInfoManager;
        this.professionalSpecialistDao = professionalSpecialistDao;
        this.consultationServiceDao = consultationServiceDao;
    }

    // FindSecBugs XSS_SERVLET: response is JSON/encoded/static/binary/text content, not an HTML XSS sink.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response is JSON/encoded/static/binary/text content, not an HTML XSS sink")
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (loggedInInfo == null) {
            try {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } catch (IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String term = request.getParameter("term");
        if (term == null) term = "";
        term = term.trim();

        Map<String, String> specialtyNames = getOrLoadSpecialtyNames(request.getServletContext());
        Map<String, ProfessionalSpecialist> merged = mergeSearchResults(term);

        List<ProfessionalSpecialist> results = new ArrayList<>(merged.values());
        int limit = Math.min(results.size(), MAX_RESULTS);

        ArrayNode array = JSON_MAPPER.createArrayNode();
        for (int i = 0; i < limit; i++) {
            array.add(toJsonNode(results.get(i), specialtyNames));
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().print(array.toString());
        } catch (IOException e) {
            MiscUtils.getLogger().warn("Failed to write referring-doctor search response", e);
        }
        return NONE;
    }

    /**
     * Loads the specialty serviceId → serviceDesc map, caching it in
     * application scope so repeated keystrokes don't re-hit the DB.
     * Specialties change infrequently; lookup is best-effort and returns
     * an empty map if the DAO call fails.
     */
    private Map<String, String> getOrLoadSpecialtyNames(ServletContext application) {
        @SuppressWarnings("unchecked")
        Map<String, String> cached = (Map<String, String>) application.getAttribute(SPECIALTY_CACHE_ATTR);
        if (cached != null) {
            return cached;
        }
        synchronized (application) {
            @SuppressWarnings("unchecked")
            Map<String, String> recheck = (Map<String, String>) application.getAttribute(SPECIALTY_CACHE_ATTR);
            if (recheck != null) {
                return recheck;
            }
            Map<String, String> fresh = new HashMap<>();
            try {
                List<ConsultationServices> all = consultationServiceDao.findAll();
                if (all != null) {
                    for (ConsultationServices cs : all) {
                        if (cs.getServiceId() != null && cs.getServiceDesc() != null) {
                            fresh.put(String.valueOf(cs.getServiceId()), cs.getServiceDesc());
                        }
                    }
                }
            } catch (Exception e) {
                // Cache poisoning guard: do NOT cache an empty map on
                // failure. A transient DAO error during the first lookup
                // would otherwise wedge specialty names to "" for the
                // lifetime of the JVM. Log so the next lookup retries the
                // load and the operator sees the failure.
                MiscUtils.getLogger().warn(
                        "Specialty-name lookup failed; returning empty map without caching", e);
                return fresh;
            }
            application.setAttribute(SPECIALTY_CACHE_ATTR, fresh);
            return fresh;
        }
    }

    private Map<String, ProfessionalSpecialist> mergeSearchResults(String term) {
        LinkedHashMap<String, ProfessionalSpecialist> merged = new LinkedHashMap<>();
        if (term.length() < 2) {
            return merged;
        }
        addAll(merged, professionalSpecialistDao.findByFullName(term, ""));
        addAll(merged, professionalSpecialistDao.findByFullName("", term));
        addAll(merged, professionalSpecialistDao.findByReferralNo(term + "%"));
        addAll(merged, professionalSpecialistDao.findByFullNameAndSpecialtyAndAddress(
                "", "", "", term, true));
        try {
            addAll(merged, professionalSpecialistDao.findByPhoneContains(term, MAX_RESULTS));
        } catch (UnsupportedOperationException unsupported) {
            // Phone search is genuinely best-effort: some DAO impls
            // legitimately don't support it. Narrowing the catch from
            // generic Exception means a real DB failure (connection drop
            // mid-search) propagates as expected instead of being silently
            // swallowed under the "best-effort" comment.
        }
        return merged;
    }

    private static void addAll(Map<String, ProfessionalSpecialist> merged,
                               List<ProfessionalSpecialist> rows) {
        if (rows == null) return;
        for (ProfessionalSpecialist ps : rows) {
            merged.putIfAbsent(dedupKey(ps), ps);
        }
    }

    private static String dedupKey(ProfessionalSpecialist ps) {
        String refNo = ps.getReferralNo();
        if (refNo != null && !refNo.isEmpty()) {
            return refNo;
        }
        return ps.getLastName() + "|" + ps.getFirstName();
    }

    private static ObjectNode toJsonNode(ProfessionalSpecialist ps,
                                         Map<String, String> specialtyNames) {
        String lastName     = nullToEmpty(ps.getLastName());
        String firstName    = nullToEmpty(ps.getFirstName());
        String specCode     = nullToEmpty(ps.getSpecialtyType());
        String specialty    = specialtyNames.getOrDefault(specCode, specCode);
        String address      = nullToEmpty(ps.getStreetAddress());
        String phone        = nullToEmpty(ps.getPhoneNumber());
        String referralNo   = nullToEmpty(ps.getReferralNo());

        ObjectNode node = JSON_MAPPER.createObjectNode();
        node.put("value", referralNo);
        node.put("lastName", lastName);
        node.put("firstName", firstName);
        node.put("specialtyType", specialty);
        node.put("streetAddress", address);
        node.put("phoneNumber", phone);
        node.put("referralNo", referralNo);
        return node;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
