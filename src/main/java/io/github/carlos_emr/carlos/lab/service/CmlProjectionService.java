/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.demographic.PatientReadAccess;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.*;

/** Reconciles only explicit CML observation revisions, preserving raw reports and clinician intent.
 * @since 2026-11-09
 */
@Service
public class CmlProjectionService {
    public static final String ARCHIVE = "lab_proj_archive";
    private static final String ORIGIN = "lab_proj_origin";
    public enum Reason { READY, UNMATCHED, AMBIGUOUS_SOURCE, CLINICIAN_CHANGE, LEGACY_DELETION, WRONG_OWNER }
    public record Summary(String fingerprint, Reason reason, int archiveCount, int createCount,
            boolean spansReports) {
        public String getFingerprint() { return fingerprint; }
        public String getReason() { return reason.name(); }
        public int getArchiveCount() { return archiveCount; }
        public int getCreateCount() { return createCount; }
        public boolean isSpansReports() { return spansReports; }
        // EL 6 resolves every record property through name(), including derived properties.
        public boolean ready() { return reason == Reason.READY; }
        public boolean reviewNeeded() { return reason != Reason.READY && reason != Reason.UNMATCHED; }
        public boolean changeNeeded() { return archiveCount > 0 || createCount > 0; }
        public boolean isReady() { return ready(); }
        public boolean isReviewNeeded() { return reviewNeeded(); }
        public boolean isChangeNeeded() { return changeNeeded(); }
    }
    private record Expected(String value, String type, String instruction, String comments, Date observed, Map<String, String> source) { }
    private record Row(Measurement measurement, Map<String, String> extensions, CmlVersionPolicy.Observation source) { }
    private record Plan(Summary summary, int patient, Map<Integer, CMLHandler> reports,
            List<Row> archive, List<CmlVersionPolicy.Observation> create, Map<String, Expected> expected) { }

    private final PatientLabRoutingDao patients;
    private final ProviderLabRoutingDao providers;
    private final MeasurementDao measurements;
    private final MeasurementsDeletedDao deleted;
    private final MeasurementsExtDao extensions;
    private final Hl7TextMessageDao messages;
    private final MeasurementMapDao mappings;
    private final SecurityInfoManager security;
    private final OscarLogDao audit;

    public CmlProjectionService(PatientLabRoutingDao patients, ProviderLabRoutingDao providers,
            MeasurementDao measurements, MeasurementsDeletedDao deleted, MeasurementsExtDao extensions,
            Hl7TextMessageDao messages, MeasurementMapDao mappings, SecurityInfoManager security,
            @org.springframework.beans.factory.annotation.Qualifier("oscarLogDaoImpl") OscarLogDao audit) {
        this.patients = patients; this.providers = providers; this.measurements = measurements;
        this.deleted = deleted; this.extensions = extensions; this.messages = messages;
        this.mappings = mappings; this.security = security; this.audit = audit;
    }

    public static boolean sameSourcePatient(MessageHandler left, MessageHandler right) {
        return Hl7PatientUnlinkService.samePatient(left, right)
                && Objects.equals(left.getPatientLocation(), right.getPatientLocation());
    }

    /** Joins ingestion's transaction and accession lock; an incoming source may not be routed yet. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reconcile(int report, int patient) {
        Hl7ChainLocks.lockReport(report);
        Plan plan = prepare(report, patient, true, true);
        if (plan.summary().isReady() || plan.summary().reason() == Reason.CLINICIAN_CHANGE
                || plan.summary().reason() == Reason.LEGACY_DELETION) {
            CMLHandler incoming = plan.reports().get(report);
            for (int obr = 0; obr < incoming.getOBRCount(); obr++) {
                for (int obx = 0; obx < incoming.getOBXCount(obr); obx++) {
                    Hl7textResultsData.projectableValue(incoming.getOBXResult(obr, obx), Integer.toString(report), obr, obx);
                }
            }
            execute(plan, "SUPERSEDED");
        }
        if (plan.summary().isReviewNeeded()) MiscUtils.getLogger().warn("CML measurements require review: {}", plan.summary().reason());
    }

    /** Read-only report-page diagnostic; no coordination rows, clinical values or repair writes. */
    @Transactional(readOnly = true)
    public Summary status(int report, LoggedInInfo actor) {
        int patient = owner(report);
        if (patient <= 0) return new Summary("", Reason.UNMATCHED, 0, 0, false);
        PatientReadAccess.requirePatient(security, actor, patient, "_lab", "r");
        return prepare(report, patient, false, false).summary();
    }

    @Transactional(readOnly = true)
    public Summary preview(int report, LoggedInInfo actor) {
        authorizeAdmin(actor);
        int patient = owner(report);
        if (patient <= 0) return new Summary("", Reason.UNMATCHED, 0, 0, false);
        PatientReadAccess.requirePatient(security, actor, patient, "_lab", "u");
        PatientReadAccess.requirePatient(security, actor, patient, "_measurement", "u");
        return prepare(report, patient, false, false).summary();
    }

    /** Rebuilds the reviewed plan under the SAME locks as ingestion, matching and unlinking. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Summary apply(int report, String fingerprint, LoggedInInfo actor, String remoteAddress) {
        authorizeAdmin(actor);
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) throw new StalePlan();
        Hl7ChainLocks.lockReport(report);
        int patient = owner(report);
        if (patient <= 0) throw new StalePlan();
        PatientReadAccess.requirePatient(security, actor, patient, "_lab", "u");
        PatientReadAccess.requirePatient(security, actor, patient, "_measurement", "u");
        Plan plan = prepare(report, patient, true, false);
        if (!plan.summary().fingerprint().equals(fingerprint)) throw new StalePlan();
        if (!plan.summary().isReady()) throw new IllegalStateException("This projection requires clinical review");
        execute(plan, "REPAIR");
        if (plan.summary().isChangeNeeded()) {
            OscarLog entry = new OscarLog();
            entry.setProviderNo(actor.getLoggedInProviderNo()); entry.setAction("REPAIR");
            entry.setContent("HL7_LAB_PROJECTION"); entry.setContentId(Integer.toString(report));
            entry.setDemographicId(patient); entry.setIp(remoteAddress);
            entry.setData("Reviewed plan " + fingerprint + "; archived=" + plan.archive().size()
                    + "; created=" + plan.create().size());
            audit.persist(entry); // A failed audit rolls back the entire repair.
        }
        return prepare(report, patient, false, false).summary();
    }

    private void authorizeAdmin(LoggedInInfo actor) {
        if (!security.hasPrivilege(actor, "_admin", "w", null)) throw new SecurityException("Admin write privilege required");
    }

    private int owner(int report) {
        var rows = patients.findByLabNoAndLabType(report, "HL7");
        var owners = rows.stream().map(PatientLabRouting::getDemographicNo).filter(Objects::nonNull).distinct().toList();
        return owners.size() == 1 && owners.getFirst() > 0 ? owners.getFirst() : -1;
    }

    private Plan prepare(int report, int patient, boolean lock, boolean incoming) {
        try { return prepareKnown(report, patient, lock, incoming); }
        catch (CmlVersionPolicy.AmbiguousRevision ambiguous) {
            return new Plan(new Summary("", Reason.AMBIGUOUS_SOURCE, 0, 0, false), patient,
                    Map.of(), List.of(), List.of(), Map.of());
        }
    }

    private Plan prepareKnown(int report, int patient, boolean lock, boolean incoming) {
        var selected = Factory.getHandler(Integer.toString(report));
        if (!(selected instanceof CMLHandler)) throw new IllegalArgumentException("A CML report is required");
        var clinicalOrder = Arrays.stream(Hl7textResultsData.getMatchingLabs(Integer.toString(report)).split(","))
                .map(Integer::valueOf).distinct().toList();
        var ids = clinicalOrder.stream().sorted().toList();
        if (!ids.contains(report)) throw new IllegalStateException("CML source chain is unavailable");
        Map<Integer, CMLHandler> reports = new LinkedHashMap<>();
        List<CmlVersionPolicy.Observation> observations = new ArrayList<>();
        List<Row> active = new ArrayList<>();
        Map<String, Expected> expected = new LinkedHashMap<>();
        StringBuilder fingerprint = new StringBuilder(CmlVersionPolicy.pack(Integer.toString(patient),
                CarlosProperties.getInstance().getProperty("HL7_LAB_MEASUREMENT_FILTER", ".*"),
                CarlosProperties.getInstance().getProperty("MAP_BY_IDENTIFIER_AND_NAME", "false"),
                Locale.getDefault().toLanguageTag(), TimeZone.getDefault().getID()));
        Reason reason = Reason.READY;
        Reason protection = Reason.READY;
        Set<String> protectedKeys = new HashSet<>();
        for (int id : ids) {
            if (lock) providers.lockRoutingReport(id);
            var routes = lock ? patients.findForUpdate(id, "HL7") : patients.findByLabNoAndLabType(id, "HL7");
            if (reason == Reason.READY && routes.isEmpty() && !(incoming && id == report)) reason = Reason.UNMATCHED;
            for (var route : routes) {
                fingerprint.append(CmlVersionPolicy.pack("route", Integer.toString(route.getId()), Integer.toString(id),
                        Objects.toString(route.getDemographicNo(), null), dateValue(route.getCreated()),
                        dateValue(route.getDateModified())));
                if (!Integer.valueOf(patient).equals(route.getDemographicNo())) reason = Reason.WRONG_OWNER;
            }
            var message = messages.find(id);
            var handler = Factory.getHandler(Integer.toString(id));
            if (message == null || !"CML".equals(message.getType()) || !(handler instanceof CMLHandler cml)
                    || !sameSourcePatient(selected, cml)) throw new IllegalStateException("CML source identity is ambiguous");
            reports.put(id, cml);
            if (CmlVersionPolicy.hasUnsupportedStatus(cml)) reason = Reason.AMBIGUOUS_SOURCE;
            fingerprint.append(CmlVersionPolicy.pack("source", Integer.toString(id), message.getBase64EncodedeMessage()));
            var source = CmlVersionPolicy.observations(id, cml);
            observations.addAll(source);
            for (var observation : source) {
                var value = expected(cml, observation);
                expected.put(position(observation), value);
                fingerprint.append(CmlVersionPolicy.pack("expected", position(observation), CmlVersionPolicy.pack(value.value(), value.type(), value.instruction(), value.comments(),
                        dateValue(value.observed()), packMap(value.source()))));
            }
            Map<String, CmlVersionPolicy.Observation> byPosition = source.stream().collect(Collectors.toMap(
                    row -> row.obr() + "-" + row.obx(), row -> row));
            for (Measurement measurement : measurements.findByValue("lab_no", Integer.toString(id))) {
                Map<String, String> ext = extensionMap(measurement.getId());
                fingerprint.append(CmlVersionPolicy.pack("active", Integer.toString(measurement.getId()), Objects.toString(measurement.getDemographicId(), null),
                        dateValue(measurement.getCreateDate()), clinical(measurement, ext), packMap(ext)));
                var observation = byPosition.get(ext.get("other_id"));
                active.add(new Row(measurement, ext, observation));
                if (!Integer.valueOf(patient).equals(measurement.getDemographicId())) reason = Reason.WRONG_OWNER;
                if (!Integer.toString(id).equals(ext.get("lab_no")) || observation == null || ext.containsKey(ARCHIVE)) {
                    reason = Reason.AMBIGUOUS_SOURCE;
                }
            }
        }
        for (var archive : deleted.findByLabNumbers(ids.stream().map(String::valueOf).toList())) {
            Map<String, String> ext = extensionMap(archive.getOriginalId());
            fingerprint.append(CmlVersionPolicy.pack("archive", Integer.toString(archive.getId()),
                    Integer.toString(archive.getOriginalId()), dateValue(archive.getDateDeleted()),
                    packMap(ext), archive.getType(), archive.getDataField(), archive.getProviderNo(),
                    archive.getMeasuringInstruction(), archive.getComments(), dateValue(archive.getDateObserved()),
                    dateValue(archive.getDateEntered()), Objects.toString(archive.getDemographicNo(), null)));
            String marker = ext.get(ARCHIVE);
            if (!Set.of("SUPERSEDED", "REPAIR", "UNLINK").contains(Objects.toString(marker, ""))) {
                String original = ext.get("lab_no") + ":" + ext.get("other_id");
                var source = observations.stream().filter(row -> position(row).equals(original)).findFirst();
                if (source.isEmpty()) reason = Reason.AMBIGUOUS_SOURCE;
                else protectedKeys.add(source.get().key());
                if ("CLINICIAN".equals(marker)) protection = Reason.CLINICIAN_CHANGE;
                else if (protection == Reason.READY) protection = Reason.LEGACY_DELETION;
            }
        }
        Map<String, CmlVersionPolicy.Observation> winners;
        try { winners = CmlVersionPolicy.winners(observations); }
        catch (CmlVersionPolicy.AmbiguousRevision ambiguous) { winners = Map.of(); reason = Reason.AMBIGUOUS_SOURCE; }
        List<Row> archive = new ArrayList<>();
        Set<String> retained = new HashSet<>();
        for (Row row : active) {
            if (row.source() != null && !unchanged(row, expected.get(position(row.source())))) {
                protectedKeys.add(row.source().key()); protection = Reason.CLINICIAN_CHANGE;
            }
        }
        for (Row row : active) {
            if (row.source() == null || protectedKeys.contains(row.source().key())) continue;
            var winner = winners.get(row.source().key());
            if (winner != null && position(winner).equals(position(row.source())) && !winner.withdrawn()
                    && retained.add(winner.key())) continue;
            archive.add(row);
        }
        List<CmlVersionPolicy.Observation> create = winners.values().stream()
                .filter(row -> !row.withdrawn() && !protectedKeys.contains(row.key()) && !retained.contains(row.key())
                        && expected.get(position(row)).value() != null).toList();
        boolean spans = winners.values().stream().filter(row -> !row.withdrawn()).map(CmlVersionPolicy.Observation::report)
                .distinct().count() > 1 || clinicalOrder.getLast() == report
                && winners.values().stream().anyMatch(row -> !row.withdrawn() && row.report() != report);
        String hash = ContentDigests.sha256Hex(fingerprint.toString());
        return new Plan(new Summary(hash, reason == Reason.READY ? protection : reason, archive.size(), create.size(), spans), patient, reports,
                archive, create, expected);
    }

    private Expected expected(CMLHandler handler, CmlVersionPolicy.Observation row) {
        String value = handler.getOBXName(row.obr(), row.obx()).isEmpty() ? null
                : Hl7textResultsData.projectableValue(handler.getOBXResult(row.obr(), row.obx()));
        String type = "", instruction = "";
        String name = Boolean.parseBoolean(CarlosProperties.getInstance().getProperty("MAP_BY_IDENTIFIER_AND_NAME", "false"))
                ? handler.getOBXName(row.obr(), row.obx()).trim() : "%";
        for (Object[] mapping : mappings.findMeasurements("FLOWSHEET", handler.getOBXIdentifier(row.obr(), row.obx()).trim(), name)) {
            String nextType = ((MeasurementMap) mapping[1]).getIdentCode();
            String nextInstruction = ((MeasurementType) mapping[2]).getMeasuringInstruction();
            if (!type.isEmpty() && (!type.equals(nextType) || !Objects.equals(instruction, nextInstruction))) {
                throw new CmlVersionPolicy.AmbiguousRevision("CML measurement mapping is ambiguous");
            }
            type = nextType; instruction = nextInstruction;
        }
        StringBuilder comments = new StringBuilder();
        for (int n = 0; n < handler.getOBXCommentCount(row.obr(), row.obx()); n++) {
            if (comments.length() > 0) comments.append('\n');
            comments.append(handler.getOBXComment(row.obr(), row.obx(), n));
        }
        String comment = comments.toString().replaceAll("\\<br\\s?/?\\>", "");
        Date observed = Hl7textResultsData.parseObservationDate(handler.getTimeStamp(row.obr(), row.obx()));
        if (observed == null && value != null && !row.withdrawn()) {
            throw new CmlVersionPolicy.AmbiguousRevision("CML observation time is unavailable");
        }
        Map<String, String> source = new LinkedHashMap<>();
        source.put("lab_no", Integer.toString(row.report())); source.put("other_id", row.obr() + "-" + row.obx());
        source.put("identifier", handler.getOBXIdentifier(row.obr(), row.obx()).trim());
        source.put("name", handler.getOBXName(row.obr(), row.obx()).trim());
        source.put("labname", handler.getPatientLocation()); source.put("accession", handler.getAccessionNum());
        source.put("datetime", handler.getTimeStamp(row.obr(), row.obx())); source.put("request_datetime", handler.getRequestDate(row.obr()));
        String flag = handler.getOBXAbnormalFlag(row.obr(), row.obx());
        source.put("abnormal", flag != null && ("A".equals(flag) || flag.startsWith("H")) ? "A"
                : flag != null && flag.startsWith("L") ? "L" : "N");
        String status = handler.getOBXResultStatus(row.obr(), row.obx());
        if (status != null && !status.isEmpty()) source.put("olis_status", status);
        String unit = handler.getOBXUnits(row.obr(), row.obx());
        if (unit != null && !unit.isEmpty()) source.put("unit", unit);
        String[] range = Hl7textResultsData.splitRefRange(handler.getOBXReferenceRange(row.obr(), row.obx()));
        if (!range[0].isEmpty()) source.put("range", range[0]);
        else {
            if (!range[1].isEmpty()) source.put("minimum", range[1]);
            if (!range[2].isEmpty()) source.put("maximum", range[2]);
        }
        if (handler.isTestResultBlocked(row.obr(), row.obx())) source.put("blocked", "BLOCKED");
        return new Expected(value, type, instruction, comment, observed, source);
    }

    private Map<String, String> extensionMap(int measurement) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var extension : extensions.getMeasurementsExtByMeasurementId(measurement)) {
            String key = canonicalKey(extension.getKeyVal());
            if (key == null || result.containsKey(key)) {
                throw new CmlVersionPolicy.AmbiguousRevision("Measurement source extensions are ambiguous");
            }
            result.put(key, extension.getVal());
        }
        return result;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "Exact comparison with the legacy server-locale extraction, solely to preserve clinical edits; never identity or authorization")
    private boolean unchanged(Row row, Expected expected) {
        Measurement m = row.measurement();
        String origin = row.extensions().get(ORIGIN);
        if (origin != null) return origin.equals(ContentDigests.sha256Hex(clinical(m, row.extensions())));
        return expected != null && expected.value() != null && "0".equals(m.getProviderNo())
                && (expected.value().equals(m.getDataField()) || expected.value().toLowerCase(Locale.getDefault()).equals(m.getDataField()))
                && Objects.equals(expected.type(), m.getType()) && Objects.equals(expected.instruction(), m.getMeasuringInstruction())
                && Objects.equals(expected.comments(), Objects.toString(m.getComments(), ""))
                && Objects.equals(expected.observed(), m.getDateObserved())
                && expected.source().equals(clinicalExtensions(row.extensions()));
    }

    private static String canonicalKey(String key) {
        if ("lab_projection_archive".equals(key) || "lab_projection_archi".equals(key)) return ARCHIVE;
        if ("lab_projection_origin".equals(key) || "lab_projection_origi".equals(key)) return ORIGIN;
        return key;
    }

    private static Map<String, String> clinicalExtensions(Map<String, String> extensions) {
        Map<String, String> result = new TreeMap<>();
        extensions.forEach((key, value) -> { if (!key.startsWith("lab_proj_") && !key.startsWith("lab_projection_")) result.put(key, value); });
        return result;
    }

    private static String clinical(Measurement row, Map<String, String> extensions) {
        Map<String, String> source = clinicalExtensions(extensions);
        return CmlVersionPolicy.pack(row.getType(), row.getProviderNo(), row.getDataField(), row.getMeasuringInstruction(),
                row.getComments(), row.getDateObserved() == null ? null : Long.toString(row.getDateObserved().getTime()), packMap(source));
    }

    private static String dateValue(Date value) { return value == null ? null : Long.toString(value.getTime()); }

    private static String packMap(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        new TreeMap<>(values).forEach((key, value) -> result.append(CmlVersionPolicy.pack(key, value)));
        return result.toString();
    }

    private static String position(CmlVersionPolicy.Observation row) { return row.report() + ":" + row.obr() + "-" + row.obx(); }

    private void execute(Plan plan, String reason) {
        for (Row row : plan.archive()) {
            deleted.persist(new MeasurementsDeleted(row.measurement()));
            marker(row.measurement().getId(), ARCHIVE, reason);
        }
        measurements.batchRemoveAtomically(plan.archive().stream().map(Row::measurement).toList());
        for (int report : plan.create().stream().map(CmlVersionPolicy.Observation::report).distinct().toList()) {
            var selected = plan.create().stream().filter(row -> row.report() == report).toList();
            Set<Integer> before = measurements.findByValue("lab_no", Integer.toString(report)).stream()
                    .map(Measurement::getId).collect(Collectors.toSet());
            Hl7textResultsData.projectMeasurements(Integer.toString(report), Integer.toString(plan.patient()),
                    (obr, obx) -> selected.stream().anyMatch(row -> row.obr() == obr && row.obx() == obx));
            int created = 0;
            for (Measurement measurement : measurements.findByValue("lab_no", Integer.toString(report))) {
                if (before.contains(measurement.getId())) continue;
                var ext = extensionMap(measurement.getId());
                var observation = selected.stream().filter(row -> (row.obr() + "-" + row.obx()).equals(ext.get("other_id")))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Unexpected CML projection created"));
                Row row = new Row(measurement, ext, observation);
                if (!unchanged(row, plan.expected().get(position(observation)))) {
                    throw new IllegalStateException("CML extraction or mapping changed during reconciliation");
                }
                marker(measurement.getId(), ORIGIN, ContentDigests.sha256Hex(clinical(measurement, ext)));
                created++;
            }
            if (created != selected.size()) throw new IllegalStateException("CML projection was not completely written");
        }
    }

    private void marker(int measurement, String key, String value) {
        var extension = new MeasurementsExt(); extension.setMeasurementId(measurement);
        extension.setKeyVal(key); extension.setVal(value); extensions.persist(extension);
        var stored = extensions.getMeasurementsExtByMeasurementIdAndKeyVal(measurement, key);
        if (stored == null || !key.equals(stored.getKeyVal()) || !value.equals(stored.getVal())) {
            throw new IllegalStateException("Measurement provenance was not stored completely");
        }
    }

    public static final class StalePlan extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public StalePlan() { super("The projection changed. Preview the repair again."); }
    }
}
