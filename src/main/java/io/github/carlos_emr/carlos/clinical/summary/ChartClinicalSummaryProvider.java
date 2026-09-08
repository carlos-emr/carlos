/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.RxStatus;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Read-only, request-local chart extract. No model calls, persisted artifacts, or clinical inference. */
public final class ChartClinicalSummaryProvider implements ClinicalSummaryArtifactProvider {
    static final int NOTE_LIMIT = 50;
    private final SecurityInfoManager security = SpringUtils.getBean(SecurityInfoManager.class);
    private final DemographicManager demographics = SpringUtils.getBean(DemographicManager.class);
    private final CaseManagementManager notes = SpringUtils.getBean(CaseManagementManager.class);

    @Override
    public ClinicalSummaryArtifact load(LoggedInInfo user, ClinicalSummaryRequest request) {
        if (request == null || request.demographicNo() == null || request.demographicNo() <= 0
                || !request.equals(ClinicalSummaryRequest.chart(request.demographicNo()))) {
            throw new IllegalArgumentException("Explicit demographic scope required");
        }
        int demographicNo = request.demographicNo();
        if (user == null || !security.hasPrivilege(user, "_eChart", "r", demographicNo)) {
            throw new SecurityException("missing required sec object (_eChart)");
        }
        if (!security.hasPrivilege(user, "_demographic", "r", demographicNo)
                || !security.isAllowedAccessToPatientRecord(user, demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        CarlosProperties properties = CarlosProperties.getInstance();
        if (!"true".equals(properties.getProperty("program_domain.show_echart", "false"))
                && !notes.isClientInProgramDomain(user.getLoggedInProviderNo(), String.valueOf(demographicNo))
                && !notes.isClientReferredInProgramDomain(user.getLoggedInProviderNo(), String.valueOf(demographicNo))) {
            throw new SecurityException("missing required sec object (_eChart)");
        }
        Demographic patient = demographics.getDemographic(user, Integer.valueOf(demographicNo));
        if (patient == null) {
            throw new NoSuchElementException("Patient record unavailable");
        }
        requirePatient(demographicNo, patient.getDemographicNo());
        LogAction.addLogSynchronous(user, "ClinicalSummary.read", "demographicNo=" + demographicNo);

        ObjectNode artifact = new ObjectMapper().createObjectNode();
        String patientId = "demographic-" + demographicNo;
        artifact.put("schema_version", 1).put("artifact_id", "chart-" + demographicNo)
                .put("generated_at", Instant.now().toString()).put("workflow", "patient-overview")
                .put("model", "CARLOS chart extract; no model inference");
        artifact.putObject("patient_context").put("id", patientId)
                .put("label", value(patient.getDisplayName())).put("synthetic", false);
        for (String key : List.of("sources", "claims", "fact_ledger", "sections", "coverage", "validation")) {
            artifact.putArray(key);
        }
        addSource(artifact, patientId, "identity", "Patient identity", "Current record",
                "Demographic number: " + demographicNo + "\nName: " + value(patient.getDisplayName()), false);
        warning(artifact, "partial_chart", "Partial chart extract, not an AI summary. Includes unarchived prescription records, active allergy records, and up to 50 recent eligible signed notes. Labs, documents, forms and other chart sections are not included. Missing information is not a negative clinical finding.");

        ArrayNode medicationClaims = section(artifact, "medications", "Recorded prescriptions (unarchived)");
        if (security.hasPrivilege(user, "_rx", "r", demographicNo)) {
            for (Drug drug : SpringUtils.getBean(RxManager.class).getDrugs(user, demographicNo, RxStatus.CURRENT)) {
                requirePatient(demographicNo, drug.getDemographicId());
                if (drug.isArchived() || drug.isDeleted()) {
                    continue;
                }
                String text = "Medication: " + value(drug.getDrugName())
                        + "\nInstructions: " + value(drug.getSpecial())
                        + "\nPrescription date: " + date(drug.getRxDate())
                        + "\nRecorded end date: " + date(drug.getEndDate())
                        + "\nDiscontinued flag: " + drug.isDiscontinued()
                        + "\nPast medication flag: " + drug.isPastMed();
                addRecord(artifact, patientId, "rx-" + drug.getId(), "Prescription record",
                        date(drug.getRxDate()), text, text, "Medication", medicationClaims);
            }
            warning(artifact, "prescription_status", "Unarchived prescriptions are recorded orders, not confirmation of current use. Review dates, instructions and status flags against the chart.");
        } else {
            warning(artifact, "medications_unavailable", "Medication records are not included because medication read access is unavailable.");
        }

        ArrayNode allergyClaims = section(artifact, "allergies", "Recorded allergies (active records)");
        if (security.hasPrivilege(user, "_allergy", "r", demographicNo)) {
            for (Allergy allergy : SpringUtils.getBean(AllergyManager.class).getActiveAllergies(user, demographicNo)) {
                requirePatient(demographicNo, allergy.getDemographicNo());
                String text = "Allergy: " + value(allergy.getDescription())
                        + "\nRecorded reaction: " + value(allergy.getReaction())
                        + "\nRecorded severity: " + value(allergy.getSeverityOfReactionDesc());
                addRecord(artifact, patientId, "allergy-" + allergy.getId(), "Allergy record",
                        date(allergy.getEntryDate()), text, text, "Allergy", allergyClaims);
            }
        } else {
            warning(artifact, "allergies_unavailable", "Allergy records are not included because allergy read access is unavailable.");
        }

        ArrayNode noteClaims = section(artifact, "notes", "Recent signed note excerpts");
        Object program = user.getSession() == null ? null : user.getSession().getAttribute("case_program_id");
        // Never fall back to unfiltered notes when the chart's server-side program context is missing.
        if (program == null || !program.toString().matches("[1-9][0-9]{0,8}")) {
            warning(artifact, "notes_unavailable", "Notes are not included: an authorized chart program context is required. Open the patient eChart before this overview.");
        } else {
            List<CaseManagementNote> candidates = notes.getNotes(String.valueOf(demographicNo), NOTE_LIMIT + 1);
            for (CaseManagementNote note : candidates) {
                requirePatient(String.valueOf(demographicNo), note.getDemographic_no());
            }
            if (candidates.size() > NOTE_LIMIT) {
                warning(artifact, "note_limit", "Only the 50 most recent note revisions were considered, before visibility filtering. Older notes are not included.");
            }
            List<CaseManagementNote> eligible = candidates.stream().limit(NOTE_LIMIT)
                    .filter(note -> note.isSigned() && !note.isArchived() && !note.isLocked())
                    .filter(note -> note.getNote() != null && !note.getNote().isBlank())
                    .filter(note -> note.getReporter_caisi_role() != null
                            && note.getReporter_caisi_role().matches("[1-9][0-9]{0,8}"))
                    .toList();
            for (CaseManagementNote note : notes.filterNotes(user, user.getLoggedInProviderNo(), eligible, program.toString())) {
                requirePatient(String.valueOf(demographicNo), note.getDemographic_no());
                String body = note.getNote();
                String excerpt = body.length() <= 240 ? body : body.substring(0, 240) + "... [excerpt]";
                addRecord(artifact, patientId, "note-" + note.getId(), "Signed encounter note",
                        date(note.getObservation_date()), body, excerpt, "Note excerpt", noteClaims);
            }
            warning(artifact, "note_scope", "Notes are filtered using CARLOS program, role and configured facility access. Unsigned, archived, locked, blank or unclassified-role notes are omitted. Excerpts are not clinical summaries; review the full source text.");
        }
        return new ClinicalSummaryArtifact(artifact);
    }

    private static ArrayNode section(ObjectNode artifact, String id, String title) {
        return ((ArrayNode) artifact.get("sections")).addObject().put("id", id).put("title", title).putArray("claim_ids");
    }

    private static void addRecord(ObjectNode artifact, String patientId, String id, String title,
            String date, String sourceText, String claimText, String category, ArrayNode section) {
        addSource(artifact, patientId, id, title, date, sourceText, true);
        ((ArrayNode) artifact.get("claims")).addObject().put("id", "claim-" + id)
                .put("text", claimText).putArray("source_ids").add(id);
        ((ArrayNode) artifact.get("fact_ledger")).addObject().put("id", "fact-" + id)
                .put("category", category).put("text", claimText).putArray("source_ids").add(id);
        section.add("claim-" + id);
    }

    private static void addSource(ObjectNode artifact, String patientId, String id, String title,
            String date, String text, boolean cited) {
        ((ArrayNode) artifact.get("sources")).addObject().put("id", id).put("patient_id", patientId)
                .put("title", title + " (" + id + ")").put("date", date).put("text", text);
        ((ArrayNode) artifact.get("coverage")).addObject().put("source_id", id)
                .put("status", cited ? "cited" : "reviewed_not_cited")
                .put("reason", cited ? "Recorded fields or note excerpt reproduced without model inference."
                        : "Patient identity only; no clinical claim.");
    }

    private static void warning(ObjectNode artifact, String code, String message) {
        ((ArrayNode) artifact.get("validation")).addObject().put("severity", "warning")
                .put("code", code).put("message", message).putArray("source_ids");
    }

    private static void requirePatient(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new SecurityException("Chart source patient mismatch");
        }
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "Not recorded" : value;
    }

    private static String date(Date date) {
        return date == null ? "Not recorded" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
    }
}
