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
package io.github.carlos_emr.carlos.lab.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Unlinks an HL7 lab report from the patient it was matched to and archives the readings the
 * report filed to the chart (issue #6214).
 *
 * <p>Unlink is how a clinician corrects a wrong patient match. Until now it only cleared the
 * report's patient routing, as OSCAR 19 does, and left the measurements filed from the report on
 * the chart: flowsheets, the Health Tracker, graphs, decision-support colours and the prevention
 * and clinical reports kept showing another patient's values with nothing to say where they came
 * from. By the clinical decision recorded on #6214, unlink now archives them. Every measurement
 * that carries the report's {@code lab_no} extension is moved to {@code measurementsDeleted}, the
 * same archive a clinician's deletion and a superseded report version use: nothing is deleted, the
 * archived copy keeps the measurement's id as {@code originalId} so its extensions stay
 * attributable, and an audit row names each archived reading. After the unlink no chart holds an
 * active reading from the report, which also clears readings left on a chart by an earlier unlink
 * or by a migrated OSCAR 19 chart when the same report is unlinked again.</p>
 *
 * <p>Matching the report to a patient again files its current version anew
 * ({@code CommonLabResultData.updatePatientLabRouting}, which files the current version when the
 * report has no measurements), so the readings follow the report to the chart it is linked to.</p>
 *
 * <p>Unlink stays a single-report operation and is never refused for what the chart holds
 * (#6120): a report with no filed readings unlinks exactly as before, and the other versions of the
 * accession keep their own routing and readings. The routing change, the archive, the return of
 * the report to its providers' inboxes and the audit rows commit together or not at all; a failed
 * audit write aborts the unlink rather than leaving an unrecorded archive.</p>
 *
 * @since 2026-11-25
 */
@Service
public class LabPatientUnlinkService {

    /** The routing type unlink acts on; HL7 reports are the only source filed as measurements. */
    private static final String HL7 = ProviderLabRoutingDao.LAB_TYPE.HL7.name();

    /** The measurement extension naming the report a reading was filed from ({@code Hl7textResultsData}). */
    static final String LAB_NO_EXTENSION = "lab_no";

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * What an unlink changed.
     *
     * @param unlinked whether the report had a patient routing to clear; {@code false} changes nothing
     * @param unlinkedDemographicNo the patient the report was routed to, or
     *                              {@link PatientLabRoutingDao#UNMATCHED} when it had none
     * @param archivedMeasurements how many readings filed from the report were archived
     */
    public record Result(boolean unlinked, int unlinkedDemographicNo, int archivedMeasurements) {
    }

    private final PatientLabRoutingDao patientRoutings;
    private final ProviderLabRoutingDao providerRoutings;
    private final MeasurementDao measurements;
    private final MeasurementsDeletedDao archive;
    private final SecurityInfoManager security;

    public LabPatientUnlinkService(PatientLabRoutingDao patientRoutings, ProviderLabRoutingDao providerRoutings,
                                   MeasurementDao measurements, MeasurementsDeletedDao archive,
                                   SecurityInfoManager security) {
        this.patientRoutings = patientRoutings;
        this.providerRoutings = providerRoutings;
        this.measurements = measurements;
        this.archive = archive;
        this.security = security;
    }

    /**
     * Unlinks one HL7 report from its patient, archives the readings it filed and puts it back in
     * its providers' inboxes as new.
     *
     * <p>The caller has checked the global {@code _lab} update privilege. The report lock is taken
     * first, so a patient match, a forward or a filing of the same report waits for this
     * transaction and never moves or files readings that are being archived. Every chart the
     * unlink changes (the patient the report is routed to, and any chart holding a reading filed
     * from it) must allow the caller to update its labs and must not be locked to the caller;
     * otherwise nothing is written. In ordinary use that is the one patient whose lab page the
     * clinician has open, which the page itself already required.</p>
     *
     * @param actor the logged-in provider making the correction
     * @param labNo the report to unlink
     * @param reason the clinician's reason, kept in the audit log only
     * @param remoteAddr the client address recorded in the audit log
     * @return what was changed; {@code unlinked} is {@code false} when the report has no patient
     *         routing, in which case nothing is changed
     * @throws IllegalArgumentException when {@code labNo} is not a report identifier
     * @throws SecurityException when a chart the unlink would change is closed to the caller
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result unlinkReport(LoggedInInfo actor, int labNo, String reason, String remoteAddr) {
        if (labNo <= 0) {
            throw new IllegalArgumentException("Invalid lab identifier");
        }
        providerRoutings.lockRoutingReport(labNo);
        List<PatientLabRouting> routings = patientRoutings.findByLabNoAndLabType(labNo, HL7);
        if (routings.isEmpty()) {
            return new Result(false, PatientLabRoutingDao.UNMATCHED, 0);
        }
        String report = String.valueOf(labNo);
        List<Measurement> readings = filedReadings(report);

        // Authorize every chart this unlink changes before anything is written.
        Set<Integer> charts = new LinkedHashSet<>();
        for (PatientLabRouting routing : routings) {
            charts.add(demographicOf(routing));
        }
        for (Measurement reading : readings) {
            charts.add(reading.getDemographicId() == null ? PatientLabRoutingDao.UNMATCHED : reading.getDemographicId());
        }
        for (int chart : charts) {
            requireLabUpdate(actor, chart);
        }

        String actorProviderNo = actor == null ? null : actor.getLoggedInProviderNo();
        int unlinkedFrom = PatientLabRoutingDao.UNMATCHED;
        for (PatientLabRouting routing : routings) {
            unlinkedFrom = demographicOf(routing);
            routing.setDemographicNo(PatientLabRoutingDao.UNMATCHED);
            routing.setDateModified(new Date());
            patientRoutings.merge(routing);
            if (logger.isDebugEnabled()) {
                logger.debug("Unlinked lab segmentID={} from demographic={}",
                        LogSafe.sanitizeObject(labNo), LogSafe.sanitizeObject(unlinkedFrom));
            }
            LogAction.addLogStrict(actorProviderNo, LogConst.UNLINK, LogConst.CON_HL7_LAB, report, remoteAddr,
                    String.valueOf(unlinkedFrom), reason);
        }

        archiveReadings(actorProviderNo, report, remoteAddr, readings);
        returnToProviderInboxes(actorProviderNo, labNo, remoteAddr, unlinkedFrom);
        return new Result(true, unlinkedFrom, readings.size());
    }

    /**
     * The readings filed from the report, whichever chart holds them, each once: a reading with a
     * repeated {@code lab_no} extension is returned once per extension row.
     */
    private List<Measurement> filedReadings(String report) {
        Map<Integer, Measurement> readings = new LinkedHashMap<>();
        for (Measurement reading : measurements.findByValue(LAB_NO_EXTENSION, report)) {
            readings.putIfAbsent(reading.getId(), reading);
        }
        return new ArrayList<>(readings.values());
    }

    /**
     * Requires the caller's lab update right on one chart, including a patient-specific
     * {@code _lab$N} rule, and that the chart is not locked to the caller: {@code _lab$N} alone
     * misses a {@code _demographic$N} or {@code _eChart$N} lock on the same patient. The unmatched
     * bucket names no chart and stays behind the caller's global gate.
     */
    private void requireLabUpdate(LoggedInInfo actor, int chart) {
        if (chart <= 0) {
            return;
        }
        if (!security.hasPrivilege(actor, "_lab", "u", String.valueOf(chart))) {
            throw new SecurityException("missing required sec object (_lab)");
        }
        LabPatientAccess.requirePatient(security, actor, chart);
    }

    /**
     * Moves the readings to the measurement archive and audits each one under its own patient and
     * measurement id. Values, comments and dates are copied unchanged; the extensions stay keyed by
     * the original id.
     */
    private void archiveReadings(String actorProviderNo, String report, String remoteAddr, List<Measurement> readings) {
        if (readings.isEmpty()) {
            return;
        }
        List<MeasurementsDeleted> copies = new ArrayList<>(readings.size());
        for (Measurement reading : readings) {
            copies.add(new MeasurementsDeleted(reading));
        }
        archive.batchPersistAtomically(copies);
        measurements.batchRemoveAtomically(readings);
        for (Measurement reading : readings) {
            // Identifiers only, as for a clinician's deletion: never the value or its comment.
            LogAction.addLogStrict(actorProviderNo, LogConst.ARCHIVE, LogConst.CON_MEASUREMENT,
                    String.valueOf(reading.getId()), remoteAddr, String.valueOf(reading.getDemographicId()),
                    "lab " + report + " unlinked");
        }
    }

    /**
     * Puts the report back in each routed provider's inbox as new, so the requisitioning provider
     * (or the unclaimed inbox) sees it again and can match it to the right patient. Unchanged from
     * the behaviour before #6214.
     */
    private void returnToProviderInboxes(String actorProviderNo, int labNo, String remoteAddr, int unlinkedFrom) {
        String newStatus = ProviderLabRoutingDao.STATUS.N.name();
        for (ProviderLabRoutingModel routing : providerRoutings.findAllLabRoutingByIdandType(labNo, HL7)) {
            String previousStatus = routing.getStatus();
            routing.setStatus(newStatus);
            String comment = routing.getComment() + " Lab unlinked from incorrect demographic number: " + unlinkedFrom;
            routing.setComment(comment.trim());
            routing.setTimestamp(new Date());
            providerRoutings.merge(routing);
            LogAction.addLogStrict(actorProviderNo, LogConst.UNLINK, LogConst.CON_HL7_LAB, String.valueOf(labNo),
                    remoteAddr, String.valueOf(unlinkedFrom),
                    "Provider " + routing.getProviderNo() + " Ack status from " + previousStatus + " to " + newStatus);
        }
    }

    private static int demographicOf(PatientLabRouting routing) {
        Integer demographicNo = routing.getDemographicNo();
        return demographicNo == null ? PatientLabRoutingDao.UNMATCHED : demographicNo;
    }
}
