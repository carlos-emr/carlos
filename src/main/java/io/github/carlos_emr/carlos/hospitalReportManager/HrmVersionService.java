/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.*;
import org.springframework.stereotype.Service;

/** Validated history rows; a display order never changes HRM's current-version selection rule. */
@Service
public class HrmVersionService {
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

    /** Metadata for a group already resolved by the authorized patient attachment list. */
    public List<Version> describe(Collection<Integer> ids, Locale locale) {
        List<HRMDocument> rows = new ArrayList<>();
        for (Integer id : new LinkedHashSet<>(ids)) {
            HRMDocument row = documents.find(id.intValue());
            if (row == null) throw new IllegalStateException("An HRM version is no longer available; refresh the list");
            rows.add(row);
        }
        return format(rows, locale);
    }

    /** Request IDs are only hints: every rendered row is checked against the stored group and patient scope. */
    public History history(LoggedInInfo info, HRMDocument displayed, HRMReport report, String requested, Locale locale) {
        if (!security.hasPrivilege(info, "_hrm", "r", (String) null)) throw new SecurityException("missing required sec object (_hrm)");
        if (requested == null || requested.isBlank()) return new History(List.of(), false);
        if (requested.length() > 16384) return new History(List.of(), true);
        Set<Integer> scope = patientIds(displayed.getId());
        List<HRMDocument> rows = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        boolean incomplete = false;
        for (String value : requested.split(",", -1)) {
            int id;
            try { id = Integer.parseInt(value.trim()); }
            catch (NumberFormatException invalid) { incomplete = true; continue; }
            if (id <= 0) { incomplete = true; continue; }
            if (!seen.add(id) || id == displayed.getId()) continue;
            HRMDocument candidate = documents.find(id);
            if (candidate == null || !scope.equals(patientIds(id)) || !canRead(info, scope)) {
                incomplete = true; continue;
            }
            HRMReport other = HRMReportParser.parseReport(info, candidate.getReportFile());
            if (other == null || !sameGroup(report, other)) { incomplete = true; continue; }
            rows.add(candidate);
        }
        return new History(format(rows, locale), incomplete);
    }

    private boolean canRead(LoggedInInfo info, Set<Integer> scope) {
        for (int id : scope) if (id > 0 && !security.isAllowedAccessToPatientRecord(info, id)) return false;
        return true;
    }

    private Set<Integer> patientIds(int document) {
        Set<Integer> ids = new HashSet<>();
        for (var link : patients.findByHrmDocumentId(document)) ids.add(link.getDemographicNo());
        return ids;
    }

    static boolean sameGroup(HRMReport a, HRMReport b) {
        return a.getSendingFacilityId() != null && a.getSendingFacilityReportNo() != null
                && a.getDeliverToUserId() != null
                && Objects.equals(a.getSendingFacilityId(), b.getSendingFacilityId())
                && Objects.equals(a.getSendingFacilityReportNo(), b.getSendingFacilityReportNo())
                && Objects.equals(a.getDeliverToUserId(), b.getDeliverToUserId());
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
