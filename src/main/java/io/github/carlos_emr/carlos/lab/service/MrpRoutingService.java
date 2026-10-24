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

import java.util.Arrays;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.lab.ca.all.upload.ProviderLabRouting;
import io.github.carlos_emr.carlos.utility.SpringUtils;

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
 * switch off no new rule-based access is added; obsolete automatic access is still revoked on
 * a corrected patient match. The legacy no-orderer upload fallback remains available.</p>
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean routeMatchedLabToMrp(String labNo, String labType, Integer demographicNo, String actorProviderNo) {
        if (StringUtils.isBlank(labNo) || StringUtils.isBlank(labType)
                || DOCUMENT_LAB_TYPE.equals(labType) || HRM_TYPE.equals(labType)) return false;
        List<Integer> versions = matchingLabIds(labNo, labType);
        ProviderLabRoutingDao dao = SpringUtils.getBean(ProviderLabRoutingDao.class);
        versions.stream().sorted().forEach(dao::lockRoutingReport);
        return reconcileLabVersions(versions, labType, demographicNo, actorProviderNo);
    }

    private boolean reconcileLabVersions(List<Integer> versions, String labType, Integer demographicNo, String actorProviderNo) {
        String mrp = providerLinkingRulesService.isEnabled() ? resolveRoutableMrp(demographicNo) : null;
        boolean created = false;
        ProviderLabRouting router = new ProviderLabRouting();
        for (int version : versions) {
            if (router.reconcileMrpRouting(version, labType, demographicNo, mrp)) {
                audit(actorProviderNo, labType, Integer.toString(version), mrp, demographicNo);
                created = true;
            }
        }
        return created;
    }

    /** Saves the patient and reconciles provider access as one transaction across all versions. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void matchPatientLab(String labNo, String labType, Integer demographicNo, String actorProviderNo) {
        if (demographicNo == null || demographicNo <= 0 || demographicDao.getDemographicById(demographicNo) == null) {
            throw new IllegalArgumentException("Patient does not exist");
        }
        List<Integer> versions = matchingLabIds(labNo, labType);
        ProviderLabRoutingDao dao = SpringUtils.getBean(ProviderLabRoutingDao.class);
        versions.stream().sorted().forEach(dao::lockRoutingReport);
        if (!CommonLabResultData.updatePatientLabRouting(versions, demographicNo.toString(), labType)) {
            throw new IllegalStateException("Patient match could not be saved");
        }
        reconcileLabVersions(versions, labType, demographicNo, actorProviderNo);
        CommittedAudit.write(() -> LogAction.addLog(actorProviderNo, "match patient", "labPatientRouting",
                labType + ":" + labNo, null, demographicNo.toString(), "patient and provider routing updated"));
    }

    private List<Integer> matchingLabIds(String labNo, String labType) {
        if (StringUtils.isBlank(labNo) || StringUtils.isBlank(labType)) {
            throw new IllegalArgumentException("Lab identifier and type are required");
        }
        int reportId = Integer.parseInt(labNo);
        requireReport(reportId, labType);
        String chain = new CommonLabResultData().getMatchingLabsForMutation(labNo, labType);
        if (StringUtils.isBlank(chain)) throw new IllegalStateException("Lab version lookup returned no report");
        List<Integer> versions = Arrays.stream(chain.split(","))
                .map(String::trim).map(Integer::valueOf).distinct().toList();
        if (!versions.contains(reportId)) throw new IllegalStateException("Lab version chain does not include the selected report");
        for (int version : versions) requireReport(version, labType);
        return versions;
    }

    private void requireReport(int reportId, String labType) {
        if (reportId <= 0) throw new IllegalArgumentException("Invalid lab identifier");
        boolean exists = switch (labType) {
            case "HL7" -> SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao.class).find(reportId) != null;
            case "MDS" -> SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.MdsMSHDao.class).find(reportId) != null;
            case "CML" -> SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.LabPatientPhysicianInfoDao.class).find(reportId) != null;
            case "BCP" -> SpringUtils.getBean(io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7MessageDao.class).find(reportId) != null;
            default -> throw new IllegalArgumentException("Unsupported lab source");
        };
        if (!exists) throw new IllegalArgumentException("Lab report does not exist");
    }

    /** Uses the persisted patient match for upload provenance; never invents independent access. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean routeUploadedLabToMrp(String labNo, String actorProviderNo) {
        int reportId = Integer.parseInt(labNo);
        Integer patient = uploadedPatient(reportId);
        if (patient == null) return false;
        String mrp = providerLinkingRulesService.isEnabled() ? resolveRoutableMrp(patient) : null;
        boolean created = new ProviderLabRouting().reconcileMrpRouting(reportId, "HL7", patient, mrp);
        if (created) audit(actorProviderNo, "HL7", labNo, mrp, patient);
        return created;
    }

    /**
     * Keeps the legacy no-orderer fallback while tracking why its MRP received the report.
     * An inactive or unknown MRP leaves the report in the unassigned inbox.
     * @param labNo uploaded HL7 report
     * @param actorProviderNo uploading actor, or null for automatic import
     * @return true when an active MRP holds the report, including an existing assignment
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean routeUploadedFallbackToMrp(String labNo, String actorProviderNo) {
        int reportId = Integer.parseInt(labNo);
        Integer patient = uploadedPatient(reportId);
        String mrp = resolveRoutableMrp(patient);
        if (mrp == null) return false;
        if (new ProviderLabRouting().reconcileMrpRouting(reportId, "HL7", patient, mrp)) {
            CommittedAudit.write(() -> LogAction.addLog(actorProviderNo, AUDIT_ACTION, "labPatientFallback",
                    "HL7:" + labNo, null, patient.toString(), "mrp=" + mrp));
        }
        return true;
    }

    private Integer uploadedPatient(int reportId) {
        SpringUtils.getBean(ProviderLabRoutingDao.class).lockRoutingReport(reportId);
        List<PatientLabRouting> matches = SpringUtils.getBean(PatientLabRoutingDao.class)
                .findByLabNoAndLabType(reportId, "HL7");
        List<Integer> patients = matches.stream().map(PatientLabRouting::getDemographicNo).distinct().toList();
        return patients.size() == 1 ? patients.get(0) : null;
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
     * @return {@code true} only when the direct MRP routing was newly created
     */
    public boolean routeMatchedHrmToMrp(int hrmDocumentId, Integer demographicNo, String actorProviderNo) {
        String mrp = providerLinkingRulesService.isEnabled() ? resolveRoutableMrp(demographicNo) : null;
        boolean created = hrmProviderRoutingService.reconcileMrpRouting(hrmDocumentId, demographicNo, mrp);
        if (created) {
            audit(actorProviderNo, HRM_TYPE, Integer.toString(hrmDocumentId), mrp, demographicNo);
        }
        return created;
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
            logger.info("Provider linking rules routed {} report {} to its MRP", // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                    LogSafe.sanitize(type), LogSafe.sanitize(reportId));
        });
    }
}
