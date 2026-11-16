/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billing.CA;

import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves billing patient labels from authorized patient identifiers instead of caller-supplied names.
 * @since 2026-11-09
 */
public final class BillingPatientContext {
    private static final String CACHE = BillingPatientContext.class.getName();
    private BillingPatientContext() { }

    /** The authoritative patient name, cached only within this request and keyed by patient ID. */
    public record Names(int demographicNo, String last, String first) {
        public String formatted() { return last + ", " + first; }
    }

    public static String name(HttpServletRequest request) {
        String id = request.getParameter("demographic_no");
        if (id == null || id.isBlank()) return "";
        Names result = lookup(request, id, false);
        return result == null ? "" : result.formatted();
    }

    public static String requiredName(HttpServletRequest request) {
        return names(request, request.getParameter("demographic_no")).formatted();
    }

    public static Names names(HttpServletRequest request, String id) { return lookup(request, id, true); }

    private static Names lookup(HttpServletRequest request, String id, boolean required) {
        final int demographicNo;
        try { demographicNo = Integer.parseInt(id); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("A valid patient identifier is required"); }
        if (demographicNo <= 0) throw new IllegalArgumentException("A valid patient identifier is required");
        if (request.getAttribute(CACHE) instanceof Names cached && cached.demographicNo() == demographicNo) return cached;
        var patient = SpringUtils.getBean(DemographicManager.class).getDemographic(
                LoggedInInfo.getLoggedInInfoFromSession(request), demographicNo);
        if (patient == null) {
            if (required) throw new IllegalArgumentException("The selected patient is unavailable");
            return null;
        }
        var result = new Names(demographicNo, java.util.Objects.toString(patient.getLastName(), ""),
                java.util.Objects.toString(patient.getFirstName(), ""));
        request.setAttribute(CACHE, result);
        return result;
    }
}
