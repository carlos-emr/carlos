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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsHistoryNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Clinical notes/history retain full bounded content or an explicit source-view marker, never a clipped clinical statement. */
@Service
public class CdsHistoryPersister {
    private static final Map<String, String> ISSUES =
            Map.of(
                    "PersonalHistory",
                    "SocHistory",
                    "FamilyHistory",
                    "FamHistory",
                    "PastHealth",
                    "MedHistory",
                    "ProblemList",
                    "Concerns",
                    "RiskFactors",
                    "RiskFactors",
                    "AlertsAndSpecialNeeds",
                    "Reminders");
    private static final Map<String, String> CONTENT =
            Map.of(
                    "FamilyHistory",
                    "ProblemDiagnosisProcedureDescription",
                    "PastHealth",
                    "PastHealthProblemDescriptionOrProcedures",
                    "ProblemList",
                    "ProblemDiagnosisDescription",
                    "RiskFactors",
                    "RiskFactor",
                    "AlertsAndSpecialNeeds",
                    "AlertDescription",
                    "ClinicalNotes",
                    "MyClinicalNotesContent");
    private final CdsFieldParser fields;
    private final CdsClinicalNormalizationService normalization;
    private final CdsImportConfigurationService configuration;
    private final CdsProviderResolver providers;
    private final CdsNativePreparationService preparation;

    private final CaseManagementNoteDAO notes;
    private final CaseManagementIssueDAO patientIssues;
    private final IssueDAO issues;
    private final CaseManagementNoteExtDAO extensions;

    public CdsHistoryPersister(
            CdsFieldParser fields,
            CdsClinicalNormalizationService normalization,
            CdsImportConfigurationService configuration,
            CdsProviderResolver providers,
            CdsNativePreparationService preparation,
            @Qualifier("CaseManagementNoteDAO") CaseManagementNoteDAO notes,
            @Qualifier("CaseManagementIssueDAO") CaseManagementIssueDAO patientIssues,
            @Qualifier("IssueDAO") IssueDAO issues,
            @Qualifier("CaseManagementNoteExtDAO") CaseManagementNoteExtDAO extensions) {
        this.fields = fields;
        this.normalization = normalization;
        this.configuration = configuration;
        this.providers = providers;
        this.preparation = preparation;
        this.notes = Objects.requireNonNull(notes);
        this.patientIssues = Objects.requireNonNull(patientIssues);
        this.issues = Objects.requireNonNull(issues);
        this.extensions = Objects.requireNonNull(extensions);
    }

    public void validate(CdsRecordDto record, CdsImportCommand command) {
        String code = ISSUES.get(record.section());
        if (code != null) requireIssue(code);
    }

    public void prepare(
            String run, long epoch, String unit, CdsRecordDto record, CdsImportCommand command)
            throws IOException {
        Map<String, CdsClinicalDiagnosticDto> warnings = new LinkedHashMap<>();
        Consumer<CdsClinicalDiagnosticDto> notice =
                n -> warnings.putIfAbsent(n.code() + "/" + n.field(), n);
        String contentPath = CONTENT.get(record.section());
        CdsFieldDto[] descriptor = {null};
        String[] participant = {null};
        boolean[] repeated = {false};
        fields.forEachField(
                record,
                f -> {
                    if (!known(f)) return;
                    String path = CdsFieldParser.unindexed(f.path());
                    if (record.section().equals("ClinicalNotes")
                            && f.kind().equals("TEXT")
                            && (path.equals("ParticipatingProviders/DateTimeNoteCreated")
                                    || path.startsWith(
                                            "ParticipatingProviders/DateTimeNoteCreated/")))
                        notice.accept(
                                new CdsClinicalDiagnosticDto(
                                        "CDS_NOTE_CREATED_TIME_RETAINED_IN_SOURCE",
                                        "ParticipatingProviders/DateTimeNoteCreated"));
                    if (descriptor[0] == null
                            && contentPath != null
                            && CdsFieldParser.unindexed(f.path()).equals(contentPath))
                        descriptor[0] = f;
                    if (f.path().startsWith("ParticipatingProviders[")) {
                        String group = f.path().substring(0, f.path().indexOf(']') + 1);
                        if (participant[0] == null) participant[0] = group;
                        else if (!participant[0].equals(group)) repeated[0] = true;
                    }
                });
        long limit = configuration.number("cds.import.native.maxNoteBytes", 262144);
        if (limit > 393216) throw new CdsImportException("NATIVE_NOTE_LIMIT_CONFIGURATION_INVALID");
        String content;
        if (descriptor[0] == null) {
            content =
                    "[CDS "
                            + record.section()
                            + ": no narrative body supplied. Separate clinical source fields retain"
                            + " this occurrence's complete values.]";
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_HISTORY_STRUCTURED_SOURCE_FIELDS", record.section()));
        } else {
            String logical = null;
            boolean oversized = false;
            try {
                logical = fields.readLogicalText(record, descriptor[0], (int) limit);
                oversized =
                        logical.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > limit;
            } catch (CdsInputException capacity) {
                if (!capacity.code().equals("CDS_TARGET_TEXT_CAPACITY_EXCEEDED")) throw capacity;
                oversized = true;
            }
            if (oversized) {
                content =
                        "[CDS "
                                + record.section()
                                + ": narrative exceeds the bounded native projection. Read the"
                                + " complete original and normalized values in this clinical"
                                + " category's CDS source fields. No narrative excerpt was"
                                + " substituted.]";
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_NOTE_COMPLETE_TEXT_SOURCE_VIEW", contentPath));
            } else content = logical;
        }
        Long observation = null, created = null;
        String author = null;
        String observationField = observationField(record, command);
        CdsTemporalDto observationDate = observationField == null ? null
                : normalization.date(record, observationField, command);
        if (observationDate != null) observation = timestamp(observationDate, observationField, notice, true);
        if (record.section().equals("ClinicalNotes")) {
            if (repeated[0])
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_MULTIPLE_NOTE_PARTICIPANTS_SOURCE_ONLY",
                                "ParticipatingProviders"));
            else if (participant[0] != null) {
                var matched =
                        providers.resolve(
                                fields.firstToken(
                                        record, "ParticipatingProviders/OHIPPhysicianId", 20),
                                null,
                                fields.firstText(
                                        record, "ParticipatingProviders/Name/FirstName", 250),
                                fields.firstText(
                                        record, "ParticipatingProviders/Name/LastName", 250),
                                command.matchProviderNames());
                author = matched.localProviderNo();
                if (!matched.matched())
                    notice.accept(
                            new CdsClinicalDiagnosticDto(
                                    "CDS_NOTE_PROVIDER_" + matched.outcome(),
                                    "ParticipatingProviders"));
                created =
                        timestamp(
                                normalization.date(
                                        record,
                                        "ParticipatingProviders/DateTimeNoteCreated",
                                        command),
                                "ParticipatingProviders/DateTimeNoteCreated",
                                notice,
                                false);
            }
        }
        preparation.store(
                run,
                epoch,
                unit,
                record.effectiveSha256(),
                command,
                new CdsHistoryNativeDto(
                        content,
                        ISSUES.get(record.section()),
                        observation,
                        created,
                        author,
                        observationDate,
                        observationField,
                        List.copyOf(warnings.values())));
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(
            String run,
            long epoch,
            String unit,
            int patient,
            CdsRecordDto record,
            CdsImportCommand command,
            Consumer<CdsClinicalDiagnosticDto> notices)
            throws IOException {
        var source =
                preparation.load(
                        run,
                        epoch,
                        unit,
                        record.effectiveSha256(),
                        command,
                        CdsHistoryNativeDto.class);
        source.diagnostics().forEach(notices);
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no(Integer.toString(patient));
        note.setNote(source.content());
        note.setProviderNo(source.providerNo() == null ? "" : source.providerNo());
        note.setSigning_provider_no("");
        note.setSigned(false);
        // create_date is a native revision Formula, not a source-created storage column.
        // Exact source creation values/precision remain in the ClinicalNotes source category.
        note.setObservation_date(
                source.observationMillis() == null ? null : new Date(source.observationMillis()));
        note.setUpdate_date(new Date());
        note.setUuid(UUID.randomUUID().toString());
        note.setHistory("");
        note.setProgram_no(command.programId() == null ? "" : command.programId().toString());
        note.setReporter_caisi_role("1");
        note.setReporter_program_team("0");
        Map<String, String> references = new LinkedHashMap<>();
        if (source.issueCode() != null) {
            Issue category = requireIssue(source.issueCode());
            var existing = patientIssues.findCandidatesByDemographicAndIssue(patient, category.getId());
            if (existing.size() > 1) throw new CdsImportException("HISTORY_NATIVE_ISSUE_AMBIGUOUS");
            CaseManagementIssue issue =
                    existing.isEmpty() ? new CaseManagementIssue() : existing.get(0);
            if (existing.isEmpty()) {
                issue.setDemographic_no(patient);
                issue.setIssue_id(category.getId());
                issue.setType("general");
                patientIssues.saveIssue(issue);
            }
            if (issue.getId() == null || issue.getId() < 1)
                throw new CdsImportException("HISTORY_NATIVE_ISSUE_ID_MISSING");
            note.setIssues(Set.of(issue));
            references.put("casemgmt_issue", issue.getId().toString());
        }
        notes.saveNote(note);
        if (note.getId() == null || note.getId() < 1)
            throw new CdsImportException("HISTORY_NATIVE_NOTE_ID_MISSING");
        references.put("casemgmt_note", note.getId().toString());
        if (source.observationDate() != null) {
            noteExtension(note.getId(), "cdsDatePrecision", source.observationDate().precision().name(), references);
            noteExtension(note.getId(), "cdsDateOriginal", source.observationDate().original(), references);
            noteExtension(note.getId(), "cdsDateField", source.observationField(), references);
        }
        return references;
    }

    private void noteExtension(Long note, String key, String value, Map<String, String> references) {
        if (value == null) return;
        var extension = new CaseManagementNoteExt();
        extension.setNoteId(note);
        extension.setKeyVal(key);
        extension.setValue(value);
        extensions.save(extension);
        if (extension.getId() == null || extension.getId() < 1)
            throw new CdsImportException("HISTORY_NATIVE_EXTENSION_ID_MISSING");
        references.put("casemgmt_note_ext#" + key, extension.getId().toString());
    }

    /** Prefer the section's onset/event date; an independent end/resolution date stays in source fields. */
    private String observationField(CdsRecordDto record, CdsImportCommand command) throws IOException {
        return switch (record.section()) {
            case "ClinicalNotes" -> "EventDateTime";
            case "FamilyHistory", "RiskFactors" -> "StartDate";
            case "ProblemList" -> "OnsetDate";
            case "AlertsAndSpecialNeeds" -> "DateActive";
            case "PastHealth" -> normalization.date(record, "OnsetOrEventDate", command).precision()
                    == CdsTemporalDto.Precision.UNKNOWN ? "ProcedureDate" : "OnsetOrEventDate";
            default -> null;
        };
    }

    private Issue requireIssue(String code) {
        var matches = issues.findCandidatesByCode(code);
        if (matches.size() != 1)
            throw new CdsImportException("HISTORY_CATEGORY_MAPPING_REQUIRES_REVIEW");
        return matches.get(0);
    }

    private Long timestamp(
            CdsTemporalDto value,
            String path,
            Consumer<CdsClinicalDiagnosticDto> notice,
            boolean nativeObservation) {
        if (value.appliedShiftDays() != 0)
            notice.accept(new CdsClinicalDiagnosticDto("CDS_SYNTHETIC_DATE_SHIFT_APPLIED", path));
        if (value.earliest() != null
                && (value.earliest().getYear() < 1 || value.earliest().getYear() > 9999)) {
            notice.accept(new CdsClinicalDiagnosticDto("CDS_NOTE_DATE_RANGE_SOURCE_ONLY", path));
            return null;
        }
        if (value.precision() == CdsTemporalDto.Precision.FULL_DATE) {
            notice.accept(new CdsClinicalDiagnosticDto("CDS_NOTE_DATE_ONLY_SOURCE_VIEW", path));
            if (!nativeObservation) return null;
        }
        if (!value.exactCalendarDate()) {
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            value.precision() == CdsTemporalDto.Precision.UNKNOWN
                                    ? "CDS_DATE_NOT_SUPPLIED"
                                    : "CDS_PARTIAL_DATE_RETAINED",
                            path));
            return null;
        }
        try {
            java.time.Instant instant;
            if (value.offsetDateTime() != null)
                instant = CdsTemporalDto.timestampStorageInstant(value.offsetDateTime().toInstant(), ZoneId.systemDefault()).toInstant();
            else {
                String zone = configuration.value("cds.import.clinicalZone", "");
                if (zone.isEmpty()) {
                    notice.accept(
                            new CdsClinicalDiagnosticDto("CDS_NOTE_TIME_ZONE_SOURCE_ONLY", path));
                    return null;
                }
                instant = value.timestampStorageAnchor(ZoneId.of(zone)).toInstant();
            }
            if (nativeObservation) {
                int nanos =
                        value.offsetDateTime() != null
                                ? value.offsetDateTime().getNano()
                                : value.localDateTime() == null ? 0 : value.localDateTime().getNano();
                if (nanos != 0)
                    notice.accept(
                            new CdsClinicalDiagnosticDto(
                                    "CDS_NOTE_SUBSECOND_PRECISION_RETAINED_IN_SOURCE", path));
                // Native DATETIME(0) must not let adopted SQL rounding roll the clinical day.
                instant = instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            }
            return instant.toEpochMilli();
        } catch (IllegalArgumentException ambiguous) {
            String code = ambiguous instanceof io.github.carlos_emr.carlos.commn.model.CdsClinicalException clinical
                    && clinical.code().startsWith("CDS_NATIVE_")
                    ? "CDS_NOTE_NATIVE_TIMESTAMP_SOURCE_ONLY" : "CDS_NOTE_LOCAL_TIMESTAMP_SOURCE_ONLY";
            notice.accept(
                    new CdsClinicalDiagnosticDto(code, path));
            return null;
        }
    }

    private static boolean known(CdsFieldDto f) {
        return f.namespace().equals("cds") || f.namespace().equals("cds_dt");
    }
}
