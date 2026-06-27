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
package io.github.carlos_emr.carlos.fax.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.dao.ServiceSpecialistsDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationServices;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.form.JSONUtil;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.List;

/**
 * GET-only JSON autocomplete endpoint returning combined pharmacy and specialist fax recipients.
 *
 * <p>Requires {@code _fax} read privilege. Returns up to {@value #MAX_RESULTS} results.
 *
 * <p>Response shape per item:
 * <ul>
 *   <li>{@code name} – display name (specialist: "Last, First"; pharmacy: "Name (City)")</li>
 *   <li>{@code fax}  – fax number string</li>
 *   <li>{@code badge} – label for the Bootstrap badge ("pharmacy" or service description)</li>
 *   <li>{@code type} – "PHARMACY" or "SPECIALIST"</li>
 * </ul>
 *
 * <p>Specialist entries respect {@code hideFromView}: specialists with that flag set are excluded.
 * A specialist enrolled in multiple services produces one entry per matching service, each with
 * its own service-description badge.
 *
 * @since 2026-06
 */
public class FaxRecipientSearch2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();
    private static final int MAX_RESULTS = 20;

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final PharmacyInfoDao pharmacyInfoDao = SpringUtils.getBean(PharmacyInfoDao.class);
    private final ServiceSpecialistsDao serviceSpecialistsDao = SpringUtils.getBean(ServiceSpecialistsDao.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"GET".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_fax", "r", null)) {
            // Return 403 JSON rather than throwing SecurityException: this is a
            // JSON-only autocomplete endpoint with no Struts exception mapping, so
            // an uncaught exception would bubble as a 500 HTML response that the
            // JS caller cannot parse. Standard page actions should throw instead.
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            JSONUtil.jsonResponse(response, objectMapper.createArrayNode().toString());
            return NONE;
        }

        String term = StringUtils.trimToEmpty(request.getParameter("term"));
        if (term.length() < 2) {
            JSONUtil.jsonResponse(response, objectMapper.createArrayNode().toString());
            return NONE;
        }

        ArrayNode results = objectMapper.createArrayNode();
        addSpecialistResults(term, results);
        addPharmacyResults(term, results);

        JSONUtil.jsonResponse(response, results.toString());
        return NONE;
    }

    /**
     * Appends specialist-with-service rows matching the keyword. One entry per (specialist, service)
     * tuple. Specialists without any service enrollment are not included here.
     */
    private void addSpecialistResults(String term, ArrayNode results) {
        int remaining = MAX_RESULTS - results.size();
        if (remaining <= 0) return;
        try {
            List<Object[]> rows = serviceSpecialistsDao.searchSpecialistsWithService(term, remaining);
            for (Object[] row : rows) {
                ProfessionalSpecialist spec = (ProfessionalSpecialist) row[1];
                ConsultationServices svc = (ConsultationServices) row[2];

                if (StringUtils.isBlank(spec.getFaxNumber())) continue;

                ObjectNode item = objectMapper.createObjectNode();
                item.put("name", spec.getLastName() + ", " + StringUtils.defaultString(spec.getFirstName()));
                item.put("fax", spec.getFaxNumber());
                item.put("badge", StringUtils.defaultIfBlank(svc.getServiceDesc(), "Specialist"));
                item.put("type", "SPECIALIST");
                results.add(item);
            }
        } catch (Exception e) {
            logger.warn("Error loading specialist fax autocomplete results", e);
        }
    }

    /**
     * Appends active pharmacy entries whose name or address matches the keyword.
     */
    private void addPharmacyResults(String term, ArrayNode results) {
        int remaining = MAX_RESULTS - results.size();
        if (remaining <= 0) return;
        try {
            // searchPharmacyByNameAddressCity matches name/address by first param, city by second.
            // Passing "" for city means any city is accepted.
            // Pass remaining as DB-level limit; the loop below still skips entries without a fax
            // number, so we may get fewer than remaining results — that's acceptable.
            List<PharmacyInfo> pharmacies = pharmacyInfoDao.searchPharmacyByNameAddressCity(term, "", remaining);
            for (PharmacyInfo ph : pharmacies) {
                if (remaining <= 0) break;
                if (StringUtils.isBlank(ph.getFax())) continue;
                remaining--;

                String displayName = StringUtils.defaultIfBlank(ph.getName(), "Unknown Pharmacy");
                String city = StringUtils.trimToEmpty(ph.getCity());
                if (!city.isEmpty()) {
                    displayName = displayName + " (" + city + ")";
                }

                ObjectNode item = objectMapper.createObjectNode();
                item.put("name", displayName);
                item.put("fax", ph.getFax());
                item.put("badge", "pharmacy");
                item.put("type", "PHARMACY");
                results.add(item);
            }
        } catch (Exception e) {
            logger.warn("Error loading pharmacy fax autocomplete results", e);
        }
    }
}
