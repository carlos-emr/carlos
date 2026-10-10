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
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.utility.FileValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reloads authorized evidence and chart comparison locally. None of the chart is sent to an agent. */
@Service
public class ChartUpdateContext {
    /** The source file is absent and the native viewer has no stored HTML fallback. */
    public static final class OriginalDocumentMissingException extends IllegalStateException {
        public OriginalDocumentMissingException() {
            super("The original document file is missing. Choose another document or ask an administrator to restore it.");
        }
    }

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;
    private final SecurityInfoManager security;
    private final DocumentManager documents;
    private final CaseManagementManager notes;
    private final TicklerDao ticklers;
    private final ProgramProviderDAO programs;
    private final ProgramManager programManager;
    private final TicklerManager ticklerAccess;
    private final ChartUpdateChartRecords records;

    public ChartUpdateContext(SecurityInfoManager security, DocumentManager documents, CaseManagementManager notes,
            TicklerDao ticklers, ProgramProviderDAO programs, ProgramManager programManager, TicklerManager ticklerAccess,
            ChartUpdateChartRecords records) {
        this.security = security;
        this.documents = documents;
        this.notes = notes;
        this.ticklers = ticklers;
        this.programs = programs;
        this.programManager = programManager;
        this.ticklerAccess = ticklerAccess;
        this.records = records;
    }

    public record Entry(String id, String kind, String text, String entryText, String dueDate, String assignee,
            java.util.Set<String> destinations) implements Serializable {
        public Entry { destinations = java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(destinations)); }
        public Entry(String id, String kind, String text) { this(id, kind, text, text, "", ""); }
        public Entry(String id, String kind, String text, String entryText, String dueDate, String assignee) {
            this(id, kind, text, entryText, dueDate, assignee, java.util.Set.of());
        }
        public String getId() { return id; }
        public String getKind() { return kind; }
        public String getText() { return text; }
        public List<String> getDestinations() { return destinations.stream().sorted().toList(); }
        public String getDestinationCodes() { return String.join(" ", getDestinations()); }
    }
    public record Snapshot(int documentId, int patientId, String patientLabel, String title, String date, String source,
            String sourceHash, String fingerprint, String program, String role, List<Entry> entries) {
    }

    /** True only when chart updates, document summaries and summary generation are all switched on. */
    public static boolean enabled() {
        CarlosProperties properties = CarlosProperties.getInstance();
        return List.of(ChartUpdateProposals.ENABLED, DocumentSummaryService.ENABLED_PROPERTY,
                ClinicalSummaryGenerationService.ENABLED_PROPERTY).stream()
                .allMatch(flag -> "true".equals(properties.getProperty(flag, "false")));
    }

    public static void requireEnabled() {
        if (!enabled()) throw new IllegalStateException("Chart updates are disabled.");
    }

    // Loading evidence also records a synchronous access audit. MySQL enforces
    // read-only connections, so the audit insert needs a writable transaction.
    @Transactional
    public Snapshot load(LoggedInInfo user, int documentId) {
        requireEnabled();
        if (user == null || documentId <= 0 || !security.hasPrivilege(user, "_edoc", "r", null)) deny();
        // This boundary is only called before writes or after their transaction completes.
        // Discard Open-EntityManager-in-View identity-cache entries before rechecking freshness.
        entityManager.clear();
        var link = documents.getCtlDocumentByDocumentId(user, documentId);
        if (link == null || link.getId() == null || !link.isDemographicDocument()
                || !Objects.equals(link.getId().getDocumentNo(), documentId)
                || link.getId().getModuleId() == null || link.getId().getModuleId() <= 0) deny();
        int patient = link.getId().getModuleId();
        for (String permission : List.of("_edoc", "_demographic", "_eChart", "_tickler")) {
            if (!security.hasPrivilege(user, permission, "r", patient)) deny();
        }
        if (!security.isAllowedAccessToPatientRecord(user, patient)) deny();
        Demographic demographic = entityManager.find(Demographic.class, patient);
        if (demographic == null) deny();
        if (!"true".equals(CarlosProperties.getInstance().getProperty("program_domain.show_echart", "false"))
                && !notes.isClientInProgramDomain(user.getLoggedInProviderNo(), String.valueOf(patient))
                && !notes.isClientReferredInProgramDomain(user.getLoggedInProviderNo(), String.valueOf(patient))) deny();
        Object context = user.getSession() == null ? null : user.getSession().getAttribute("case_program_id");
        if (context == null || !context.toString().matches("[1-9][0-9]{0,8}")) {
            throw new IllegalStateException("Open this patient's eChart to establish program context first.");
        }
        String program = context.toString();
        var membership = programs.getProgramProvider(user.getLoggedInProviderNo(), Long.valueOf(program));
        if (membership == null || membership.getRoleId() == null || membership.getRoleId() <= 0
                || !programManager.hasAccessBasedOnCurrentFacility(user, Integer.valueOf(program))) deny();
        boolean visible = EDocUtil.listDocs(user, "demographic", String.valueOf(patient), "all", EDocUtil.PRIVATE,
                EDocUtil.EDocSort.OBSERVATIONDATE, "active").stream()
                .anyMatch(document -> String.valueOf(documentId).equals(document.getDocId()));
        if (!visible) deny();
        var document = documents.getDocument(user, documentId);
        if (document == null || !Objects.equals(document.getDocumentNo(), documentId) || document.getStatus() != 'A') deny();
        ClinicalSummaryTextExtractor.Extract extract;
        try {
            extract = ClinicalSummaryTextExtractor.document(document.getDocfilename(), document.getContenttype());
        } catch (IOException invalid) {
            if ((invalid instanceof NoSuchFileException || invalid instanceof FileNotFoundException)
                    && (document.getDocxml() == null || document.getDocxml().isBlank())) {
                throw new OriginalDocumentMissingException();
            }
            throw new IllegalStateException("Document text is unavailable. Reopen the original.");
        } catch (FileValidationException invalidName) {
            // A stored filename that is not one path component, or resolves outside DOCUMENT_DIR: the text is
            // unavailable, as for any unreadable file. Authorization failures are other SecurityExceptions.
            throw new IllegalStateException("Document text is unavailable. Reopen the original.");
        }
        if (!extract.complete() || extract.text().isBlank()) {
            throw new IllegalStateException("Complete readable document text is required for chart-update proposals.");
        }
        List<Entry> entries = new ArrayList<>();
        List<CaseManagementNote> allNotes = notes.getNotes(String.valueOf(patient));
        if (allNotes.stream().anyMatch(note -> !String.valueOf(patient).equals(note.getDemographic_no()))) deny();
        List<CaseManagementNote> candidates = allNotes.stream().filter(note -> note.isSigned() && !note.isArchived() && !note.isLocked())
                .filter(note -> note.getNote() != null && !note.getNote().isBlank())
                .filter(note -> note.getReporter_caisi_role() != null && note.getReporter_caisi_role().matches("[1-9][0-9]{0,8}"))
                .toList();
        var readableSections = ChartUpdateSections.CODES.stream()
                .filter(code -> ChartUpdateSections.accessible(security, user, patient, code, "r")).collect(java.util.stream.Collectors.toSet());
        for (CaseManagementNote note : notes.filterNotes(user, user.getLoggedInProviderNo(), candidates, program)) {
            var destinations = note.getIssues().stream().filter(issue -> issue.getIssue() != null)
                    .map(issue -> issue.getIssue().getCode()).filter(readableSections::contains)
                    .collect(java.util.stream.Collectors.toSet());
            if (!destinations.isEmpty()) {
                entries.add(new Entry("note-" + note.getId(), "history", note.getNote(), note.getNote(), "", "", destinations));
            }
        }

        List<Tickler> activeTicklers = ticklers.findActiveByDemographicNo(patient);
        if (activeTicklers.stream().anyMatch(tickler -> !Objects.equals(tickler.getDemographicNo(), patient))) deny();
        for (Tickler tickler : ticklerAccess.filterTicklersByAccess(activeTicklers, user.getLoggedInProviderNo(), program)) {
            if (!Objects.equals(tickler.getDemographicNo(), patient)) deny();
            if (tickler.getProgramId() != null && !programManager.hasAccessBasedOnCurrentFacility(user, tickler.getProgramId())) continue;
            entries.add(new Entry("tickler-" + tickler.getId(), "tickler", Objects.toString(tickler.getMessage(), "")
                    + "\nDue: " + date(tickler.getServiceDate()) + "\nAssigned to: " + tickler.getTaskAssignedTo(),
                    Objects.toString(tickler.getMessage(), ""), date(tickler.getServiceDate()), tickler.getTaskAssignedTo()));
        }
        entries.addAll(records.load(user, patient));
        entries.sort(Comparator.comparing(Entry::id));
        String date = date(document.getObservationdate());
        String sourceHash = ChartUpdateProposals.hash(extract.text());
        String patientLabel = demographic.getFormattedName();
        String fingerprint = ChartUpdateProposals.hash(documentId + "\n" + patient + "\n" + patientLabel + "\n" + program + "\n"
                + membership.getRoleId() + "\n" + sourceHash + "\n" + document.getDocfilename() + "\n"
                + document.getUpdatedatetime() + "\n" + document.getDocdesc() + "\n" + date + "\n" + entries);
        LogAction.addLogSynchronous(user, "ChartUpdates.read", "documentId=" + documentId + ",demographicNo=" + patient);
        return new Snapshot(documentId, patient, patientLabel, document.getDocdesc(), date, extract.text(), sourceHash,
                fingerprint, program, membership.getRoleId().toString(), List.copyOf(entries));
    }

    private static String date(java.util.Date value) {
        if (value == null) return "";
        // java.sql.Date has no toInstant(); use the calendar date in the application zone.
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(value);
    }

    public void requireWrite(LoggedInInfo user, int patient, String kind) {
        if (!security.hasPrivilege(user, "tickler".equals(kind) ? "_tickler" : "_eChart", "w", patient)) deny();
    }

    public List<String> writableSections(LoggedInInfo user, int patient) {
        return ChartUpdateSections.CODES.stream().filter(code -> ChartUpdateSections.accessible(security, user, patient, code, "w")).toList();
    }

    public void requireSectionWrite(LoggedInInfo user, int patient, String destination) {
        if (!ChartUpdateSections.CODES.contains(destination)) throw new IllegalArgumentException("Choose an available chart section.");
        if (!ChartUpdateSections.accessible(security, user, patient, destination, "w")) deny();
    }

    // Rx and allergy landing pages replace session-wide patient/stash state; do not embed them.
    public String nativeReviewUrl(LoggedInInfo user, int patient, String destination) {
        String permission = switch (destination) {

            case "Preventions" -> "_prevention";
            case "Demographics" -> "_demographic";
            default -> "";
        };
        if (permission.isEmpty() || !security.hasPrivilege(user, permission, "r", patient)
                || !security.hasPrivilege(user, permission, "w", patient)) return "";
        return switch (destination) {

            case "Preventions" -> "/prevention/ViewPreventionIndex?demographic_no=" + patient;
            case "Demographics" -> "/demographic/DemographicEdit?demographic_no=" + patient;
            default -> "";
        };
    }

    private static void deny() { throw new SecurityException("Chart-update access unavailable"); }
}
