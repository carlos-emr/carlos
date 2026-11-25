/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.*;
import org.springframework.stereotype.Service;

/** Validated history rows; a display order never changes HRM's current-version selection rule. */
@Service
public class HrmVersionService {
    private static final int MAX_HISTORY_IDS = 100;
    private final HRMDocumentDao documents;
    private final HRMDocumentToDemographicDao patients;
    private final SecurityInfoManager security;

    public HrmVersionService(HRMDocumentDao documents, HRMDocumentToDemographicDao patients,
            SecurityInfoManager security) {
        this.documents = documents; this.patients = patients; this.security = security;
    }

    public record Version(int id, String reportDate, String received, String label) {
        public int getId() { return id; }
        public String getLabel() { return label; }
    }
    public record History(List<Version> versions, boolean incomplete) {
        public History { versions = List.copyOf(versions); }
    }

    /** Incomplete source identifiers cannot establish that two messages are versions of one report. */
    public record ReportGroup(String facility, String reportNumber, String recipient, Set<Integer> patients, Integer singleDocument) {
        static ReportGroup single(int document) {
            return new ReportGroup(null, null, null, Set.of(), document);
        }

        /** Groups inbox messages using the same source fields and patient links as the chart viewer. */
        public static ReportGroup forLinks(HRMReport report, Collection<HRMDocumentToDemographic> links, int document) {
            return of(report, patientIds(links), document);
        }

        static ReportGroup of(HRMReport report, Set<Integer> patients, int document) {
            // The viewer cannot establish a shared patient for unfiled reports. Keep each one selectable.
            if (patients.isEmpty()) return single(document);
            String facility = report.getSendingFacilityId();
            String number = report.getSendingFacilityReportNo();
            String recipient = report.getDeliverToUserId();
            if (org.apache.commons.lang3.StringUtils.isAnyBlank(facility, number, recipient)) return single(document);
            return new ReportGroup(facility, number, recipient, Set.copyOf(patients), null);
        }
    }

    /** Request IDs are only hints: every rendered row is checked against the stored group and patient scope. */
    public History history(LoggedInInfo info, HRMDocument displayed, HRMReport report, String requested, Locale locale) {
        if (!security.hasPrivilege(info, "_hrm", "r", (String) null)) throw new SecurityException("missing required sec object (_hrm)");
        if (requested == null || requested.isBlank()) return new History(List.of(), false);
        if (requested.length() > 16384) return new History(List.of(), true);
        String[] candidates = requested.split(",", MAX_HISTORY_IDS + 1);
        if (candidates.length > MAX_HISTORY_IDS) return new History(List.of(), true);
        Set<Integer> scope = patientIds(patients.findByHrmDocumentId(displayed.getId()));
        if (scope.isEmpty() || !canRead(info, scope)) return new History(List.of(), true);
        var group = ReportGroup.of(report, scope, displayed.getId());
        List<HRMDocument> rows = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        boolean incomplete = false;
        for (String value : candidates) {
            int id;
            try { id = Integer.parseInt(value.trim()); }
            catch (NumberFormatException invalid) { incomplete = true; continue; }
            if (id <= 0) { incomplete = true; continue; }
            if (!seen.add(id) || id == displayed.getId()) continue;
            HRMDocument candidate = documents.find(id);
            if (candidate == null || !scope.equals(patientIds(patients.findByHrmDocumentId(id)))) {
                incomplete = true; continue;
            }
            HRMReport other = HRMReportParser.parseReport(info, candidate.getReportFile());
            if (other == null || !group.equals(ReportGroup.of(other, scope, id))) { incomplete = true; continue; }
            rows.add(candidate);
        }
        return new History(format(rows, locale), incomplete);
    }

    private boolean canRead(LoggedInInfo info, Set<Integer> scope) {
        for (int id : scope) if (!HRMReportAccess.mayReadPatient(security, info, id)) return false;
        return true;
    }

    static Set<Integer> patientIds(Collection<HRMDocumentToDemographic> links) {
        Set<Integer> ids = new HashSet<>();
        for (var link : links) {
            Integer patient = link.getDemographicNo();
            if (patient != null && patient > 0) ids.add(patient);
        }
        return ids;
    }

    static List<Version> format(List<HRMDocument> documents, Locale locale) {
        var bundle = ResourceBundle.getBundle("oscarResources", locale);
        DateFormat dates = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale);
        String unknown = bundle.getString("hrm.version.unknownDate");
        return documents.stream().sorted(Comparator.comparing(HRMDocument::getReportDate,
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HRMDocument::getTimeReceived, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HRMDocument::getId, Comparator.reverseOrder()))
                .map(d -> {
                    String report = d.getReportDate() == null ? unknown : dates.format(d.getReportDate());
                    String received = d.getTimeReceived() == null ? unknown : dates.format(d.getTimeReceived());
                    return new Version(d.getId(), report, received,
                            new MessageFormat(bundle.getString("hrm.version.label"), locale).format(new Object[]{report, received}));
                }).toList();
    }
}
