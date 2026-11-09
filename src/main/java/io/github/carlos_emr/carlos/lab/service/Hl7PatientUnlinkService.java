/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.demographic.PatientReadAccess;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.upload.ProviderLabRouting;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.ContentDigests;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Unlinks a complete HL7 chain and archives its projections in the same transaction.
 * @since 2026-11-09
 */
@Service
public class Hl7PatientUnlinkService {
    private final PatientLabRoutingDao patients;
    private final ProviderLabRoutingDao providers;
    private final MeasurementDao measurements;
    private final MeasurementsDeletedDao deleted;
    private final MeasurementsExtDao extensions;
    private final Hl7TextMessageDao messages;
    private final SecurityInfoManager security;
    private final OscarLogDao audit;

    public Hl7PatientUnlinkService(PatientLabRoutingDao patients, ProviderLabRoutingDao providers,
            MeasurementDao measurements, MeasurementsDeletedDao deleted, MeasurementsExtDao extensions,
            Hl7TextMessageDao messages, SecurityInfoManager security,
            @org.springframework.beans.factory.annotation.Qualifier("oscarLogDaoImpl") OscarLogDao audit) {
        this.patients = patients; this.providers = providers; this.measurements = measurements;
        this.deleted = deleted; this.extensions = extensions; this.messages = messages; this.security = security;
        this.audit = audit;
    }

    /** Read-only optimistic version. Rendering a report never creates coordination rows. */
    public String version(int report) {
        List<Integer> versions = chain(report);
        return fingerprint(versions, routes(versions));
    }

    /** An out-of-order/new version must not undo a clinician's explicit unmatch. */
    public boolean hasUnmatchedVersion(int report, MessageHandler incoming) {
        var message = messages.find(report);
        if (message == null) throw new IllegalArgumentException("Report does not exist");
        String resolved = Hl7textResultsData.getMatchingLabs(Integer.toString(report));
        if (resolved == null || resolved.isBlank()) throw new IllegalStateException("Source chain is unavailable");
        if (!Arrays.asList(resolved.split(",")).contains(Integer.toString(report))) {
            throw new IllegalStateException("Source chain is unavailable");
        }
        for (String value : resolved.split(",")) {
            int version = Integer.parseInt(value.trim());
            if (version == report) continue;
            var candidate = messages.find(version);
            if (candidate == null) throw new IllegalStateException("Source version is unavailable");
            if (!Objects.equals(message.getType(), candidate.getType())) continue;
            MessageHandler previous = Factory.getHandler(Integer.toString(version));
            if (previous == null) throw new IllegalStateException("Source version is unreadable");
            if (!samePatient(incoming, previous) || !Objects.equals(normalized(incoming.getPatientLocation()),
                    normalized(previous.getPatientLocation()))) continue;
            if (patients.findByLabNoAndLabType(version, "HL7").stream()
                    .anyMatch(route -> PatientLabRoutingDao.UNMATCHED.equals(route.getDemographicNo()))) return true;
        }
        return false;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int unlink(int report, String expectedVersion, String reason, LoggedInInfo actor, String remoteAddress) {
        if (report <= 0 || reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new IllegalArgumentException("A report and reason of at most 1000 characters are required");
        }
        if (expectedVersion == null || !expectedVersion.matches("[0-9a-f]{64}")) throw new StaleMatch();
        Hl7ChainLocks.lockReport(report);
        List<Integer> versions = chain(report);
        versions.stream().sorted().forEach(providers::lockRoutingReport);
        List<PatientLabRouting> routes = new ArrayList<>();
        for (int id : versions) routes.addAll(patients.findForUpdate(id, "HL7"));
        if (!fingerprint(versions, routes).equals(expectedVersion)) throw new StaleMatch();
        var owners = routes.stream().map(PatientLabRouting::getDemographicNo)
                .filter(id -> id != null && id > 0).collect(Collectors.toSet());
        if (owners.size() != 1 || routes.stream().noneMatch(row -> row.getLabNo() == report
                && row.getDemographicNo() != null && row.getDemographicNo() > 0)) throw new StaleMatch();
        int patient = owners.iterator().next();
        PatientReadAccess.requirePatient(security, actor, patient, "_lab", "u");

        Map<Integer, Measurement> active = new LinkedHashMap<>();
        for (int id : versions) {
            for (Measurement measurement : measurements.findByValue("lab_no", Integer.toString(id))) {
                active.put(measurement.getId(), measurement);
            }
        }
        // Validate every affected owner/source before the first clinical write.
        for (Measurement measurement : active.values()) {
            PatientReadAccess.requirePatient(security, actor, measurement.getDemographicId(), "_lab", "u");
            var sources = extensions.getMeasurementsExtByMeasurementId(measurement.getId()).stream()
                    .filter(ext -> "lab_no".equals(ext.getKeyVal())).map(MeasurementsExt::getVal).distinct().toList();
            if (sources.size() != 1 || !versions.contains(Integer.valueOf(sources.getFirst()))) {
                throw new IllegalStateException("Measurement source ownership is ambiguous");
            }
        }
        for (Measurement measurement : active.values()) {
            deleted.persist(new MeasurementsDeleted(measurement));
            // Extensions remain attached to originalId for provenance. This explicit marker
            // distinguishes unlink archival from deliberate clinician deletion during repair.
            MeasurementsExt marker = new MeasurementsExt();
            marker.setMeasurementId(measurement.getId()); marker.setKeyVal("lab_projection_archive");
            marker.setVal("UNLINK"); extensions.persist(marker);
        }
        measurements.batchRemoveAtomically(new ArrayList<>(active.values()));
        for (PatientLabRouting route : routes) {
            route.setDemographicNo(PatientLabRoutingDao.UNMATCHED);
            route.setDateModified(new Date()); patients.merge(route);
        }
        ProviderLabRouting router = new ProviderLabRouting();
        for (int id : versions) {
            router.reconcileMrpRouting(id, "HL7", null, null);
            for (ProviderLabRoutingModel route : providers.findAllLabRoutingByIdandType(id, "HL7")) {
                String previous = route.getStatus();
                route.setStatus("N"); route.setTimestamp(new Date());
                route.setComment((Objects.toString(route.getComment(), "")
                        + " Lab unlinked from incorrect demographic number: " + patient).trim());
                providers.merge(route);
                audit(actor, id, patient, remoteAddress,
                        "Provider " + route.getProviderNo() + " Ack status from " + previous + " to N");
            }
            audit(actor, id, patient, remoteAddress, reason);
        }
        return patient;
    }

    private void audit(LoggedInInfo actor, int report, int patient, String remoteAddress, String reason) {
        OscarLog entry = new OscarLog();
        entry.setProviderNo(actor.getLoggedInProviderNo()); entry.setAction(LogConst.UNLINK);
        entry.setContent(LogConst.CON_HL7_LAB); entry.setContentId(Integer.toString(report));
        entry.setDemographicId(patient); entry.setIp(remoteAddress); entry.setData(reason);
        // This audit is a database write in the SAME transaction. A failed audit must abort
        // the unlink, rather than being swallowed by the legacy best-effort audit helper.
        audit.persist(entry);
    }

    private List<Integer> chain(int report) {
        if (report <= 0 || messages.find(report) == null) throw new IllegalArgumentException("Report does not exist");
        String resolved = Hl7textResultsData.getMatchingLabs(Integer.toString(report));
        if (resolved == null || resolved.isBlank()) throw new IllegalStateException("Source chain is unavailable");
        List<Integer> versions = Arrays.stream(resolved.split(",")).map(String::trim).map(Integer::valueOf).distinct().toList();
        if (!versions.contains(report)) throw new IllegalStateException("Source chain is unavailable");
        MessageHandler selected = Factory.getHandler(Integer.toString(report));
        Hl7TextMessage selectedMessage = messages.find(report);
        for (int id : versions) {
            Hl7TextMessage message = messages.find(id);
            MessageHandler candidate = Factory.getHandler(Integer.toString(id));
            if (message == null || !Objects.equals(message.getType(), selectedMessage.getType())
                    || selected == null || candidate == null
                    || !Objects.equals(normalized(selected.getPatientLocation()), normalized(candidate.getPatientLocation()))
                    || !samePatient(selected, candidate)) {
                throw new IllegalStateException("Source chain identity is ambiguous");
            }
        }
        return versions;
    }

    static boolean samePatient(MessageHandler left, MessageHandler right) {
        if (left == null || right == null) return false;
        String leftHin = normalized(left.getHealthNum()), rightHin = normalized(right.getHealthNum());
        boolean dob = !normalized(left.getDOB()).isEmpty() && normalized(left.getDOB()).equals(normalized(right.getDOB()));
        boolean name = !normalized(left.getFirstName()).isEmpty() && !normalized(left.getLastName()).isEmpty()
                && normalized(left.getFirstName()).equals(normalized(right.getFirstName()))
                && normalized(left.getLastName()).equals(normalized(right.getLastName()));
        if (!leftHin.isEmpty() && !rightHin.isEmpty()) return leftHin.equals(rightHin) && (dob || name);
        return dob && name;
    }

    private static String normalized(String value) { return Objects.toString(value, "").trim(); }

    private List<PatientLabRouting> routes(List<Integer> versions) {
        List<PatientLabRouting> rows = new ArrayList<>();
        for (int id : versions) rows.addAll(patients.findByLabNoAndLabType(id, "HL7"));
        return rows;
    }

    static String fingerprint(List<Integer> versions, List<PatientLabRouting> routes) {
        String reports = new TreeSet<>(versions).toString();
        String matches = routes.stream().map(row -> row.getId() + ":" + row.getLabNo() + ":"
                + row.getDemographicNo() + ":" + time(row.getCreated()) + ":" + time(row.getDateModified()))
                .sorted().collect(Collectors.joining("|"));
        return ContentDigests.sha256Hex(reports + ":" + matches);
    }

    private static long time(Date date) { return date == null ? Long.MIN_VALUE : date.getTime(); }

    public static final class StaleMatch extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public StaleMatch() { super("The patient match changed. Refresh the report before unlinking."); }
    }
}
