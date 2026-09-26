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
 *
 * Provider linking rules were first implemented by Deval Italiya in
 * open-osp/Open-O pull request #196 (GPL); this CARLOS implementation is
 * adapted from that work.
 */
package io.github.carlos_emr.carlos.lab.service;

import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.hospitalReportManager.service.HrmProviderRoutingService;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Routes a lab or HRM report that has just been matched to a patient to that patient's Most
 * Responsible Provider (MRP) as well, when the clinic has turned Provider Linking Rules on.
 *
 * <p>Called from the three places a report meets a patient: HL7 upload
 * ({@code MessageUploader}), manual lab-to-patient matching ({@code PatientMatch2Action}) and
 * manual HRM-to-patient matching ({@code HRMModifyDocument2Action.assignDemographic}). With the
 * switch off every method is a no-op, so behaviour is exactly as before.</p>
 *
 * <p>The MRP is {@code demographic.provider_no}. It is skipped when blank, {@code 0} (no MRP),
 * {@code -1} (the HRM "unclaimed" marker) or not an active provider: routing a result to a
 * departed provider's inbox hides it from everyone who is still working. Routing is idempotent
 * (existing rows are left alone, so an acknowledged or filed result is never reopened) and applies
 * the MRP's forwarding rules, like any other delivery.</p>
 *
 * <p>No privilege is checked here: the decision must be the same whoever triggers the match, and
 * the callers have already authorised the match itself. Sending a result to the MRP widens who
 * can see it, but only to the provider the chart names as responsible for the patient. Each
 * automatic routing is written to the audit log once it has committed; nothing from the report is
 * logged.</p>
 *
 * @since 2026-09-26
 */
@Service
public class MrpRoutingService {

    /** Audit {@code action} for a report routed to the MRP by the rules. */
    public static final String AUDIT_ACTION = "route to MRP";
    /** Audit {@code content} for a report routed to the MRP by the rules. */
    public static final String AUDIT_CONTENT = "providerLinkingRules";

    private static final Logger logger = MiscUtils.getLogger();

    /** Lab type of scanned and uploaded eDocuments, which the rules do not cover. */
    private static final String DOCUMENT_LAB_TYPE = "DOC";
    private static final String HRM_TYPE = "HRM";
    private static final String NO_PROVIDER = "0";
    private static final String ACTIVE_STATUS = "1";

    private final ProviderLinkingRulesService providerLinkingRulesService;
    private final DemographicDao demographicDao;
    private final ProviderDao providerDao;
    private final HrmProviderRoutingService hrmProviderRoutingService;

    public MrpRoutingService(ProviderLinkingRulesService providerLinkingRulesService,
                             DemographicDao demographicDao,
                             ProviderDao providerDao,
                             HrmProviderRoutingService hrmProviderRoutingService) {
        this.providerLinkingRulesService = providerLinkingRulesService;
        this.demographicDao = demographicDao;
        this.providerDao = providerDao;
        this.hrmProviderRoutingService = hrmProviderRoutingService;
    }

    /**
     * Decides whether an HL7 upload should also go to the patient's MRP.
     *
     * <p>The upload has already resolved the matched patient's MRP (following a merged chart to
     * its head record), so it passes that provider number in rather than a patient.</p>
     *
     * @param mrpProviderNo the matched patient's {@code provider_no}, or {@code 0} / {@code null}
     *                      when no patient matched
     * @return {@code true} when the rules are on and the MRP is an active provider
     */
    public boolean shouldRouteUploadToMrp(String mrpProviderNo) {
        return providerLinkingRulesService.isEnabled() && isRoutableProvider(mrpProviderNo);
    }

    /**
     * Routes a manually matched lab, and every other version of it, to the patient's MRP.
     *
     * <p>Uses the same path as the inbox's manual "Send to MRP": all versions of the result are
     * routed and the unassigned ({@code 0}) rows are dropped, so the lab leaves the unmatched
     * queue once a real provider holds it.</p>
     *
     * @param labNo the lab segment just matched
     * @param labType the lab type ({@code HL7}, {@code MDS}, {@code CML}, ...); {@code DOC} is ignored
     * @param demographicNo the patient it was matched to
     * @param actorProviderNo the provider who made the match, for the audit log
     * @return {@code true} when the lab was routed to the MRP
     */
    public boolean routeMatchedLabToMrp(String labNo, String labType, Integer demographicNo, String actorProviderNo) {
        if (StringUtils.isBlank(labNo) || StringUtils.isBlank(labType)
                || DOCUMENT_LAB_TYPE.equals(labType) || HRM_TYPE.equals(labType)) {
            return false;
        }
        if (!providerLinkingRulesService.isEnabled()) {
            return false;
        }
        String mrp = resolveRoutableMrp(demographicNo);
        if (mrp == null) {
            return false;
        }

        ArrayList<String[]> labs = new ArrayList<>();
        labs.add(new String[]{labNo, labType});
        if (!CommonLabResultData.updateLabRouting(labs, mrp)) {
            // updateLabRouting logs its own failure; the match itself has already been saved.
            logger.warn("Provider linking rules could not route lab {} to the MRP", LogSafe.sanitize(labNo));
            return false;
        }
        audit(actorProviderNo, labType, labNo, mrp, demographicNo);
        return true;
    }

    /**
     * Routes a manually matched HRM report to the patient's MRP, applies the MRP's HRM forwarding
     * rules and clears the unclaimed row.
     *
     * <p>Must run inside the caller's report-locked transaction, so that a failure here rolls the
     * patient match back with it rather than leaving a half-routed report.</p>
     *
     * @param hrmDocumentId the report just matched
     * @param demographicNo the patient it was matched to
     * @param actorProviderNo the provider who made the match, for the audit log
     * @return {@code true} when the report is now routed to the MRP
     */
    public boolean routeMatchedHrmToMrp(int hrmDocumentId, Integer demographicNo, String actorProviderNo) {
        if (!providerLinkingRulesService.isEnabled()) {
            return false;
        }
        String mrp = resolveRoutableMrp(demographicNo);
        if (mrp == null) {
            return false;
        }
        hrmProviderRoutingService.assignProvider(hrmDocumentId, mrp);
        audit(actorProviderNo, HRM_TYPE, Integer.toString(hrmDocumentId), mrp, demographicNo);
        return true;
    }

    /**
     * Writes the audit row for an HL7 upload routed to the MRP by {@code MessageUploader}.
     *
     * @param labId the uploaded lab segment
     * @param mrpProviderNo the MRP it was routed to
     * @param actorProviderNo the uploading provider, or {@code null} for an automatic import
     */
    public void recordUploadRouting(String labId, String mrpProviderNo, String actorProviderNo) {
        audit(actorProviderNo, "HL7", labId, StringUtils.trim(mrpProviderNo), null);
    }

    /**
     * @param demographicNo the patient
     * @return the patient's MRP provider number when it is an active provider, else {@code null}
     */
    String resolveRoutableMrp(Integer demographicNo) {
        if (demographicNo == null) {
            return null;
        }
        Demographic demographic = demographicDao.getDemographicById(demographicNo);
        if (demographic == null) {
            return null;
        }
        String mrp = StringUtils.trimToNull(demographic.getProviderNo());
        return isRoutableProvider(mrp) ? mrp : null;
    }

    boolean isRoutableProvider(String providerNo) {
        String candidate = StringUtils.trimToNull(providerNo);
        if (candidate == null || NO_PROVIDER.equals(candidate)
                || HrmProviderRoutingService.UNCLAIMED_PROVIDER_NO.equals(candidate)) {
            return false;
        }
        Provider provider = providerDao.getProvider(candidate);
        return provider != null && ACTIVE_STATUS.equals(provider.getStatus());
    }

    private void audit(String actorProviderNo, String type, String reportId, String mrp, Integer demographicNo) {
        // The audit log is the authorised record of who can see what; the application log only
        // gets identifiers, sanitised, and never report content. Deferred to commit: an HRM match
        // rolled back after routing must not leave a routing in the audit log that never happened.
        CommittedAudit.write(() -> {
            LogAction.addLog(actorProviderNo, AUDIT_ACTION, AUDIT_CONTENT, type + ":" + reportId, null,
                    demographicNo == null ? null : demographicNo.toString(), "mrp=" + mrp);
            logger.info("Provider linking rules routed {} report {} to its MRP",
                    LogSafe.sanitize(type), LogSafe.sanitize(reportId));
        });
    }
}
