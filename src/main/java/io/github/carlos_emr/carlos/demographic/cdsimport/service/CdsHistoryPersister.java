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

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsHistoryNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public CdsHistoryPersister(
            CdsFieldParser fields,
            CdsClinicalNormalizationService normalization,
            CdsImportConfigurationService configuration,
            CdsProviderResolver providers,
            CdsNativePreparationService preparation) {
        this.fields = fields;
        this.normalization = normalization;
        this.configuration = configuration;
        this.providers = providers;
        this.preparation = preparation;
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
        if (record.section().equals("ClinicalNotes")) {
            observation =
                    timestamp(
                            normalization.date(record, "EventDateTime", command),
                            "EventDateTime",
                            notice);
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
                                notice);
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
        note.setCreate_date(
                source.createdMillis() == null ? null : new Date(source.createdMillis()));
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
            var existing =
                    entityManager
                            .createQuery(
                                    "select i from CaseManagementIssue i where"
                                            + " i.demographic_no=:patient and i.issue_id=:issue",
                                    CaseManagementIssue.class)
                            .setParameter("patient", patient)
                            .setParameter("issue", category.getId())
                            .setMaxResults(2)
                            .getResultList();
            if (existing.size() > 1) throw new CdsImportException("HISTORY_NATIVE_ISSUE_AMBIGUOUS");
            CaseManagementIssue issue =
                    existing.isEmpty() ? new CaseManagementIssue() : existing.get(0);
            if (existing.isEmpty()) {
                issue.setDemographic_no(patient);
                issue.setIssue_id(category.getId());
                issue.setType("general");
                entityManager.persist(issue);
                entityManager.flush();
            }
            note.setIssues(Set.of(issue));
            references.put("casemgmt_issue", issue.getId().toString());
        }
        entityManager.persist(note);
        entityManager.flush();
        references.put("casemgmt_note", note.getId().toString());
        return references;
    }

    private Issue requireIssue(String code) {
        var matches =
                entityManager
                        .createQuery("select i from Issue i where i.code=:code", Issue.class)
                        .setParameter("code", code)
                        .setMaxResults(2)
                        .getResultList();
        if (matches.size() != 1)
            throw new CdsImportException("HISTORY_CATEGORY_MAPPING_REQUIRES_REVIEW");
        return matches.get(0);
    }

    private Long timestamp(
            CdsTemporalDto value, String path, Consumer<CdsClinicalDiagnosticDto> notice) {
        if (value.appliedShiftDays() != 0)
            notice.accept(new CdsClinicalDiagnosticDto("CDS_SYNTHETIC_DATE_SHIFT_APPLIED", path));
        if (value.earliest() != null
                && (value.earliest().getYear() < 1 || value.earliest().getYear() > 9999)) {
            notice.accept(new CdsClinicalDiagnosticDto("CDS_NOTE_DATE_RANGE_SOURCE_ONLY", path));
            return null;
        }
        if (value.precision() == CdsTemporalDto.Precision.FULL_DATE) {
            notice.accept(new CdsClinicalDiagnosticDto("CDS_NOTE_DATE_ONLY_SOURCE_VIEW", path));
            return null;
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
            if (value.offsetDateTime() != null)
                return value.offsetDateTime().toInstant().toEpochMilli();
            String zone = configuration.value("cds.import.clinicalZone", "");
            if (zone.isEmpty()) {
                notice.accept(new CdsClinicalDiagnosticDto("CDS_NOTE_TIME_ZONE_SOURCE_ONLY", path));
                return null;
            }
            return value.timestampStorageAnchor(ZoneId.of(zone)).getTime();
        } catch (IllegalArgumentException ambiguous) {
            notice.accept(
                    new CdsClinicalDiagnosticDto("CDS_NOTE_LOCAL_TIMESTAMP_SOURCE_ONLY", path));
            return null;
        }
    }

    private static boolean known(CdsFieldDto f) {
        return f.namespace().equals("cds") || f.namespace().equals("cds_dt");
    }
}
