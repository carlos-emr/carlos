/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.prevention.web;

import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMedication;
import io.github.carlos_emr.carlos.commn.model.CVCMedicationLotNumber;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.JsonResponseWriter;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Authenticated lookup of the locally installed vaccine catalogue. */
public class VaccineCatalogue2Action extends ActionSupport {
    private final CanadianVaccineCatalogueManager catalogue = SpringUtils.getBean(CanadianVaccineCatalogueManager.class);
    private final SecurityInfoManager security = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        LoggedInInfo user = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (user == null) return error(response, HttpServletResponse.SC_UNAUTHORIZED, "Session expired");
        if (!security.hasPrivilege(user, "_prevention", SecurityInfoManager.READ, null)) {
            return error(response, HttpServletResponse.SC_FORBIDDEN, "Prevention access required");
        }
        if (!"POST".equals(request.getMethod())) {
            response.setHeader("Allow", "POST");
            return error(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
        }
        String operation = request.getParameter("method");
        if ("query".equals(operation)) {
            String query = request.getParameter("query");
            if (query == null || query.trim().length() < 3 || query.length() > 100) {
                return error(response, HttpServletResponse.SC_BAD_REQUEST, "Enter 3 to 100 characters");
            }
            StringBuilder matchedLot = new StringBuilder();
            List<CVCImmunization> matches = catalogue.query(query.trim(), true, true, true, false, matchedLot);
            CVCMedicationLotNumber selectedLot = matchedLot.length() == 0 ? null
                    : catalogue.findByLotNumber(user, matchedLot.toString());
            String lotConcept = selectedLot == null || selectedLot.getMedication() == null ? null
                    : selectedLot.getMedication().getSnomedCode();
            List<Map<String, Object>> results = new ArrayList<>();
            for (CVCImmunization item : matches) {
                if (item == null) continue;
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("name", item.getPicklistName() == null ? item.getDisplayName() : item.getPicklistName());
                result.put("generic", item.isGeneric());
                result.put("snomedId", item.getSnomedConceptId());
                result.put("genericSnomedId", item.isGeneric() ? item.getSnomedConceptId() : item.getParentConceptId());
                // A name match can accompany a lot match for a different vaccine.
                // Carry the lot only on the vaccine it actually belongs to.
                result.put("lotNumber", lotConcept != null && lotConcept.equals(item.getSnomedConceptId())
                        ? matchedLot.toString() : "");
                results.add(result);
                if (results.size() == 25) break;
            }
            JsonResponseWriter.write(response, Map.of("results", results));
        } else if ("getLotNumberAndExpiryDates".equals(operation)) {
            String conceptId = request.getParameter("snomedConceptId");
            if (conceptId == null || !conceptId.matches("[0-9]{1,18}")) {
                return error(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid vaccine concept");
            }
            CVCMedication medication = catalogue.getMedicationBySnomedConceptId(conceptId);
            List<Map<String, Object>> results = new ArrayList<>();
            if (medication != null && medication.getLotNumberList() != null) {
                for (CVCMedicationLotNumber lot : medication.getLotNumberList()) {
                    if (lot.getLotNumber() == null) continue;
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("lotNumber", lot.getLotNumber());
                    result.put("expiryDate", lot.getExpiryDate() == null ? null : Map.of("time", lot.getExpiryDate().getTime()));
                    results.add(result);
                }
            }
            results.sort(Comparator.comparing(row -> (String) row.get("lotNumber")));
            JsonResponseWriter.write(response, results);
        } else {
            return error(response, HttpServletResponse.SC_BAD_REQUEST, "Unknown catalogue operation");
        }
        return NONE;
    }

    private String error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        JsonResponseWriter.write(response, Map.of("error", message));
        return NONE;
    }
}
