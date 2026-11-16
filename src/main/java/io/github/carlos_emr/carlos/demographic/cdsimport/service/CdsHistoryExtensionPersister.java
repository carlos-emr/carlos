/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.commn.model.CdsImportUnit;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import jakarta.persistence.EntityManager;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Preparation outside the clinical transaction, then one extension row on its proven parent note. */
final class CdsHistoryExtensionPersister {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CdsFieldParser fields;
    private final CdsNativePreparationService preparation;
    private final CaseManagementNoteExtDAO extensions;

    CdsHistoryExtensionPersister(CdsFieldParser fields, CdsNativePreparationService preparation,
            CaseManagementNoteExtDAO extensions) {
        this.fields = fields;
        this.preparation = preparation;
        this.extensions = extensions;
    }

    CdsHistoryExtensionNativeDto normalize(CdsRecordDto record, CdsImportCommand command) throws IOException {
        String section = fields.firstToken(record, "ParentSection", 32);
        String parent = fields.firstToken(record, "ParentOrdinal", 20);
        String digest = fields.firstToken(record, "ParentDigest", 64);
        String key = fields.firstText(record, "Key", 64);
        String value = fields.firstText(record, "Value", CdsHistoryExtensionReader.VALUE_CAPACITY);
        String dateText = fields.firstToken(record, "Date", 128);
        String applyText = fields.firstToken(record, "Apply", 5);
        String warning = fields.firstToken(record, "Warning", 64);
        if (!"HistoryExtension".equals(record.section()) || applyText == null || !Set.of("true", "false").contains(applyText)
                || warning != null && !warning.equals("CDS_HISTORY_EXTENSION_CARRIER_NOT_APPLIED"))
            throw new CdsImportException("HISTORY_EXTENSION_SPOOL_INVALID");
        var diagnostics = new ArrayList<CdsClinicalDiagnosticDto>();
        if (warning != null) diagnostics.add(new CdsClinicalDiagnosticDto(warning, key));
        LocalDate date = null;
        if (dateText != null) {
            if (command.timeshiftDays() != 0 && Set.of("YYYY", "YYYY-MM").contains(value == null ? "" : value))
                throw new CdsImportException("HISTORY_EXTENSION_PARTIAL_DATE_SHIFT_REQUIRES_REVIEW");
            try {
                date = LocalDate.from(DateTimeFormatter.ISO_DATE.parse(dateText));
                if (date.getYear() < 1 || date.getYear() > 9999) throw new DateTimeException("Native DATE range");
                date = date.plusDays(command.timeshiftDays());
                if (date.getYear() < 1 || date.getYear() > 9999) throw new DateTimeException("Native DATE range");
                date = CdsTemporalDto.dateStorageAnchor(date).toLocalDate();
            } catch (DateTimeException | IllegalArgumentException invalid) {
                date = null;
                diagnostics.add(new CdsClinicalDiagnosticDto("CDS_HISTORY_EXTENSION_DATE_RETAINED_IN_SOURCE", key));
            }
        }
        if (date != null && command.timeshiftDays() != 0)
            diagnostics.add(new CdsClinicalDiagnosticDto("CDS_SYNTHETIC_DATE_SHIFT_APPLIED", key));
        return new CdsHistoryExtensionNativeDto(section, Long.parseLong(parent), digest, key,
                value, date, Boolean.parseBoolean(applyText), diagnostics);
    }

    void prepare(String run, long epoch, String unit, CdsRecordDto record, CdsImportCommand command)
            throws IOException {
        var value = normalize(record, command);
        try {
            preparation.store(run, epoch, unit, record.effectiveSha256(), command, value);
        } catch (CdsImportException capacity) {
            if (!"NATIVE_PREPARATION_CAPACITY_EXCEEDED".equals(capacity.getCode())) throw capacity;
            // JSON escaping can exceed the preparation column even though the original fits
            // MEDIUMTEXT. Keep the complete source and explicitly report the native omission.
            var notices = new ArrayList<>(value.diagnostics());
            notices.add(new CdsClinicalDiagnosticDto("CDS_HISTORY_EXTENSION_VALUE_RETAINED_IN_SOURCE", value.key()));
            preparation.store(run, epoch, unit, record.effectiveSha256(), command,
                    new CdsHistoryExtensionNativeDto(value.parentSection(), value.parentOrdinal(),
                            value.parentDigest(), value.key(), null, value.date(), value.apply(), notices));
        }
    }

    Map<String, String> write(String run, long epoch, String unit, int patient, CdsRecordDto record,
            CdsImportCommand command, EntityManager manager, CdsTargetReceiptService receipts,
            Consumer<CdsClinicalDiagnosticDto> notices) throws IOException {
        var value = preparation.load(run, epoch, unit, record.effectiveSha256(), command,
                CdsHistoryExtensionNativeDto.class);
        var parents = manager.createQuery("select u from CdsImportUnit u, CdsImportDependency d "
                        + "where d.runId=:run and d.unitKey=:unit and u.runId=d.runId "
                        + "and u.unitKey=d.dependsOnUnitKey and u.category=:category and u.ordinal=:ordinal",
                        CdsImportUnit.class)
                .setParameter("run", run).setParameter("unit", unit)
                .setParameter("category", value.parentSection()).setParameter("ordinal", value.parentOrdinal())
                .getResultList();
        if (parents.size() != 1) throw new CdsImportException("HISTORY_EXTENSION_PARENT_NOT_BOUND");
        var parent = parents.getFirst();
        if (!Set.of("COMMITTED", "ALREADY_COMPLETE").contains(parent.getState())
                || !java.security.MessageDigest.isEqual(parent.getSourceDigest().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        value.parentDigest().getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                || !receipts.verify(new CdsImportReceiptDto(parent.getTargetReferences(),
                        parent.getTargetDigest(), false, parent.getNativeBaseline())))
            throw new CdsImportException("HISTORY_EXTENSION_PARENT_NOT_PROVEN");
        var references = JSON.readTree(parent.getTargetReferences()).path("refs");
        Long noteId = null;
        for (var reference : references) if (reference.path("table").asText().equals("casemgmt_note")) {
            if (noteId != null) throw new CdsImportException("HISTORY_EXTENSION_PARENT_NOT_PROVEN");
            noteId = Long.valueOf(reference.path("id").asText());
        }
        if (noteId == null || noteId < 1 || ((Number) manager.createNativeQuery(
                "select count(*) from casemgmt_note where note_id=?1 and demographic_no=?2")
                .setParameter(1, noteId).setParameter(2, Integer.toString(patient)).getSingleResult()).longValue() != 1)
            throw new CdsImportException("HISTORY_EXTENSION_PARENT_PATIENT_MISMATCH");
        value.diagnostics().forEach(notices);
        if (!value.apply()) return Map.of("demographic", Integer.toString(patient));
        var row = new CaseManagementNoteExt();
        row.setNoteId(noteId);
        row.setKeyVal(value.key());
        row.setValue(value.value());
        row.setDateValue(value.date() == null ? (java.util.Date) null : CdsTemporalDto.dateStorageAnchor(value.date()));
        extensions.save(row);
        if (row.getId() == null || row.getId() < 1) throw new CdsImportException("HISTORY_EXTENSION_ID_MISSING");
        return Map.of("casemgmt_note_ext", row.getId().toString());
    }
}
