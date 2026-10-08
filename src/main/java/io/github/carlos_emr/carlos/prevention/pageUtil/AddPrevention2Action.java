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


package io.github.carlos_emr.carlos.prevention.pageUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.hl7.fhir.dstu3.model.Bundle;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.ConsentDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListItemDao;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.integration.fhir.api.DHIR;
import io.github.carlos_emr.carlos.integration.fhir.builder.FhirBundleBuilder;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.provider.model.PreventionManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;
import io.github.carlos_emr.carlos.prevention.PreventionSubmissionGuard;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Jay Gallagher
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class AddPrevention2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();



    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    CVCImmunizationDao cvcImmunizationDao = SpringUtils.getBean(CVCImmunizationDao.class);
    ConsentDao consentDao = SpringUtils.getBean(ConsentDao.class);
    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    DemographicExtDao demographicExtDao = SpringUtils.getBean(DemographicExtDao.class);
    LookupListDao lookupListDao = SpringUtils.getBean(LookupListDao.class);
    LookupListItemDao lookupListItemDao = SpringUtils.getBean(LookupListItemDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    PartialDateDao partialDateDao = SpringUtils.getBean(PartialDateDao.class);


    /** How long a repeated submission waits for the first one's save to finish. */
    static final long REPEAT_WAIT_MILLIS = 20_000;

    public AddPrevention2Action() {
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_prevention", "w", null)) {
            throw new SecurityException("missing required sec object (_prevention)");
        }

        String sessionUser = (String) request.getSession().getAttribute("user");
        if (sessionUser == null) {
            return "Logout";
        }

        // This mutating endpoint is POST-only. GET/HEAD form loads must target the
        // dedicated view gate /prevention/ViewAddPreventionData, which preserves
        // the historical _prevention w privilege but keeps the actual mutation path
        // behind POST + CSRFGuard only.
        String httpMethod = request.getMethod();
        if (!"POST".equalsIgnoreCase(httpMethod)) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        String preventionType = request.getParameter("prevention");
        String demographic_no = request.getParameter("demographic_no");
        String id = request.getParameter("id");
        if ("null".equals(id) || "".equals(id)) id = null;
        String delete = request.getParameter("delete");

        String action = request.getParameter("action");

        boolean submitToDhir = false;
        if (action != null && "Save & Submit".equals(action)) {
            submitToDhir = true;
        }
        String given = request.getParameter("given");
        String prevDate = request.getParameter("prevDate");
        String providerName = request.getParameter("providerName");
        String providerNo = request.getParameter("provider");

        String nextDate = request.getParameter("nextDate");
        String neverWarn = request.getParameter("neverWarn");

        //generic
        String snomedId = request.getParameter("snomedId");

        if (prevDate == null || prevDate.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        String refused = "0";
        if (given != null && given.equals("refused")) {
            refused = "1";
        } else if (given != null && given.equals("ineligible")) {
            refused = "2";
        } else if (given != null && given.equals("given_ext")) {
            refused = "3";
        } else if (given != null && given.equals("never")) {
            refused = "1";
        } else if (given != null && given.equals("previous")) {
            refused = "2";
        }


        if (neverWarn != null && neverWarn.equals("neverRemind")) {
            neverWarn = "1";
        } else {
            neverWarn = "0";
        }

        ArrayList<Map<String, String>> extraData = new ArrayList<Map<String, String>>();

        addHashtoArray(extraData, request.getParameter("location"), "location");
        addHashtoArray(extraData, request.getParameter("location2"), "location2");

        addHashtoArray(extraData, request.getParameter("din"), "din");

        String lotItem = request.getParameter("lotItem");
        if (lotItem != null && !lotItem.equals("-1") && !lotItem.equals("0")) {
            addHashtoArray(extraData, lotItem, "lot");
        } else {
            addHashtoArray(extraData, request.getParameter("lot"), "lot");
        }

        addHashtoArray(extraData, request.getParameter("route"), "route");

        String dose = request.getParameter("dose");
        String doseUnit = request.getParameter("doseUnit");
        if (doseUnit != null && doseUnit.length() > 0) {
            dose = (dose + " " + doseUnit).trim();
        }
        addHashtoArray(extraData, dose, "dose");
        addHashtoArray(extraData, request.getParameter("comments"), "comments");
        addHashtoArray(extraData, request.getParameter("result"), "result");
        addHashtoArray(extraData, request.getParameter("reason"), "reason");
        addHashtoArray(extraData, request.getParameter("neverReason"), "neverReason");
        addHashtoArray(extraData, request.getParameter("manufacture"), "manufacture");
        addHashtoArray(extraData, request.getParameter("dosage"), "dosage");
        addHashtoArray(extraData, request.getParameter("product"), "product");
        addHashtoArray(extraData, request.getParameter("workflowId"), "workflowId");
        addHashtoArray(extraData, request.getParameter("formId"), "formId");
        addHashtoArray(extraData, request.getParameter("dose1"), "dose1");
        addHashtoArray(extraData, request.getParameter("dose2"), "dose2");
        addHashtoArray(extraData, request.getParameter("chronic"), "chronic");
        addHashtoArray(extraData, request.getParameter("pregnant"), "pregnant");
        addHashtoArray(extraData, request.getParameter("remote"), "remote");
        addHashtoArray(extraData, request.getParameter("healthcareworker"), "healthcareworker");
        addHashtoArray(extraData, request.getParameter("householdcontact"), "householdcontact");
        addHashtoArray(extraData, request.getParameter("firstresponderpolice"), "firstresponderpolice");
        addHashtoArray(extraData, request.getParameter("firstresponderfire"), "firstresponderfire");
        addHashtoArray(extraData, request.getParameter("swineworker"), "swineworker");
        addHashtoArray(extraData, request.getParameter("poultryworker"), "poultryworker");
        addHashtoArray(extraData, request.getParameter("firstnations"), "firstnations");
        addHashtoArray(extraData, request.getParameter("name"), "name");
        addHashtoArray(extraData, request.getParameter("expiryDate"), "expiryDate");
        addHashtoArray(extraData, request.getParameter("providerName"), "providerName");

        if (request.getParameter("cvcName") != null && !request.getParameter("cvcName").equals("-1")) {
            addHashtoArray(extraData, request.getParameter("cvcName"), "brandSnomedId");
        }


        // One rendered form saves once (issue #4410): a double click, double Enter or slow-response
        // re-click repeats the same token, and the repeat is answered from the first save's outcome.
        // Posts without a token (the Rh-injection forms) are handled as before. Claimed before the
        // record is validated: once an edit or delete has saved, its original record is gone and a
        // repeat must still read as the saved no-op it is, not as a request for a missing record.
        PreventionSubmissionGuard.Claim claim = null;
        String submissionToken = request.getParameter(PreventionSubmissionGuard.PARAMETER);
        if (submissionToken != null) {
            PreventionSubmissionGuard.Attempt attempt = PreventionSubmissionGuard.attempt(request.getSession(),
                    submissionToken, demographic_no, id, REPEAT_WAIT_MILLIS);
            switch (attempt.verdict()) {
                case PROCEED -> claim = attempt.claim();
                // The first submission of this form saved: end as that save would have, the DHIR
                // review of a repeated "Save & Submit" included, without writing again.
                case ALREADY_SAVED -> {
                    return attempt.savedId() == null ? SUCCESS
                            : dhirResult(submitToDhir, snomedId, demographic_no, given, attempt.savedId());
                }
                case IN_PROGRESS -> {
                    return refuseSubmission("oscarprevention.addpreventiondata.submitInProgress");
                }
                default -> {
                    return refuseSubmission("oscarprevention.addpreventiondata.submitStale");
                }
            }
        }

        //let's do some validation
        List<String> valid = validate(preventionType, demographic_no, id);
        if (valid != null && valid.size() > 0) {
            closeClaim(claim);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        Integer preventionId;
        try {
            final String recordId = id;
            final String refusedFlag = refused;
            final String neverWarnFlag = neverWarn;
            final PreventionSubmissionGuard.Claim reservation = claim;
            java.util.function.Supplier<Integer> save = () -> {
                if (recordId == null) {
                    return PreventionData.insertPreventionData(sessionUser, demographic_no, prevDate,
                            providerNo, providerName, preventionType, refusedFlag, nextDate, neverWarnFlag, extraData, snomedId, null);
                } else if (delete != null) {
                    PreventionData.deletePreventionData(recordId, demographic_no);
                    return Integer.valueOf(recordId);
                }
                addHashtoArray(extraData, recordId, "previousId");
                return PreventionData.updatetPreventionData(recordId, sessionUser, demographic_no, prevDate,
                        providerNo, providerName, preventionType, refusedFlag, nextDate, neverWarnFlag, extraData, snomedId);
            };
            if (reservation == null) {
                preventionId = save.get();
            } else {
                // One outer transaction (PreventionData's own templates join it), so the claim
                // learns whether the write committed or rolled back.
                preventionId = new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class))
                        .execute(status -> {
                            reservation.storageStarted();
                            return save.get();
                        });
            }
            if (preventionId == null || preventionId <= 0) {
                throw new IllegalStateException("Prevention was not persisted");
            }
        } catch (IllegalArgumentException invalidInput) {
            closeClaim(claim);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("errors", List.of("Check the prevention date, next date, and patient record before saving."));
            return "form";
        } catch (RuntimeException saveFailure) {
            closeClaim(claim);
            MiscUtils.getLogger().error("Unable to save prevention", saveFailure);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.setAttribute("errors", List.of("Unable to save the prevention. No changes were saved. Please try again."));
            return "form";
        }

        if (claim != null) {
            claim.saved(preventionId);
        }
        PreventionManager prvMgr = SpringUtils.getBean(PreventionManager.class);
        try {
            prvMgr.removePrevention(demographic_no);
        } finally {
            // Released only after the cached chart summary is cleared, so a waiting repeat that
            // closes the popup refreshes the opener with the new record in it.
            closeClaim(claim);
        }

        return dhirResult(submitToDhir, snomedId, demographic_no, given, preventionId);
    }


    /**
     * The result after prevention {@code preventionId} was saved: the DHIR review page for a
     * "Save &amp; Submit" of a given immunization the patient consented to share, else the page
     * that closes the popup.
     */
    private String dhirResult(boolean submitToDhir, String snomedId, String demographic_no, String given, Integer preventionId) {
        if (submitToDhir) {
            CVCImmunization imm = cvcImmunizationDao.findBySnomedConceptId(snomedId);
            Consent ispaConsent = consentDao.findByDemographicAndConsentType(Integer.parseInt(demographic_no), "dhir_ispa_consent");
            Consent nonIspaConsent = consentDao.findByDemographicAndConsentType(Integer.parseInt(demographic_no), "dhir_non_ispa_consent");
            boolean hasIspaConsent = ispaConsent != null && !ispaConsent.isOptout();
            boolean hasNonIspaConsent = nonIspaConsent != null && !nonIspaConsent.isOptout();

            boolean ispa = Boolean.valueOf(imm != null && imm.isIspa());

            if ((ispa && hasIspaConsent) || (!ispa && hasNonIspaConsent)) {

                if ("given".equals(given) || "given_ext".equals(given)) {

                    FhirBundleBuilder fbb = DHIR.getFhirBundleBuilder(LoggedInInfo.getLoggedInInfoFromSession(request), Integer.parseInt(demographic_no), preventionId);

                    Bundle bundle = fbb.getBundle();
                    request.setAttribute("bundle", bundle);

                    Map<String, Bundle> bundles = (Map<String, Bundle>) request.getSession().getAttribute("bundles");
                    if (bundles == null) {
                        bundles = new HashMap<String, Bundle>();
                    }
                    bundles.put(bundle.getId(), bundle);
                    // nosemgrep: tainted-session-from-http-request -- bundles map contains DAO-sourced FHIR Bundle objects, not raw user input
                    request.getSession().setAttribute("bundles", bundles);

                    request.setAttribute("preventionId", preventionId);
                    request.setAttribute("demographicNo", demographic_no);
                    return "review";
                }
            }
        }
        return SUCCESS;
    }

    /** Releases or settles a submission claim; a no-op for a post that carried no token. */
    private static void closeClaim(PreventionSubmissionGuard.Claim claim) {
        if (claim != null) {
            claim.close();
        }
    }

    /**
     * Answers a repeated or stale submission with 409 and a fixed, localized plain-text message.
     * Nothing was written by this request.
     */
    // FindSecBugs XSS_SERVLET: only fixed resource-bundle messages, served as non-sniffable plain text.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "Only constant-key localized messages; UTF-8 text/plain and nosniff; no request values are written")
    private String refuseSubmission(String messageKey) {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try {
            response.getWriter().print(java.util.ResourceBundle.getBundle("oscarResources", request.getLocale()).getString(messageKey));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return NONE;
    }

    private List<String> validate(String preventionType, String demographic_no, String id) {
        List<String> result = new ArrayList<String>();

        PreventionDisplayConfig pdc = PreventionDisplayConfig.getInstance();
        HashMap<String, String> prevention = pdc.getPrevention(preventionType);
        if (prevention == null) {
            result.add("Invalid Prevention Type");
        }

        try {
            int demographicId = Integer.parseInt(demographic_no);
            if (demographicId <= 0 || !demographicDao.clientExists(demographicId)) {
                result.add("Patient not found");
            } else if (id != null) {
                PreventionData.requirePreventionInChart(Integer.parseInt(id), demographicId);
            }
        } catch (IllegalArgumentException invalidRecord) {
            result.add("Invalid patient or prevention record");
        }

        return result;
    }

    private void addHashtoArray(ArrayList<Map<String, String>> list, String s, String key) {
        if (s != null && key != null) {
            Map<String, String> h = new HashMap<String, String>();
            h.put(key, s);
            list.add(h);
        }
    }
}
