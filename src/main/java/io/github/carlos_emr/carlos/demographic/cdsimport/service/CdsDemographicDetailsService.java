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

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.demographic.pageUtil.SpokenLangProperties;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Optional discrete details use exact native semantics; source history never becomes a guessed chart snapshot. */
@Service
public class CdsDemographicDetailsService {
    private static final Pattern HISTORY =
            Pattern.compile("^(Enrolment\\[1\\]/EnrolmentHistory\\[[1-9][0-9]*\\])(?:/(.*))?$");
    private static final Map<String, Integer> ENROLLMENT_FIELDS =
            Map.of(
                    "EnrollmentStatus",
                    1,
                    "EnrollmentDate",
                    128,
                    "EnrollmentTerminationDate",
                    128,
                    "TerminationReason",
                    2,
                    "EnrolledToPhysician/Name/FirstName",
                    50,
                    "EnrolledToPhysician/Name/LastName",
                    50,
                    "EnrolledToPhysician/OHIPPhysicianId",
                    20);
    private final CdsFieldParser fields;
    private final CdsProviderResolver providers;

    public CdsDemographicDetailsService(CdsFieldParser fields, CdsProviderResolver providers) {
        this.fields = fields;
        this.providers = providers;
    }

    public CdsDemographicDetailsDto prepare(CdsRecordDto record, CdsImportCommand command)
            throws IOException {
        return prepare(record, command, true);
    }

    public void validate(CdsRecordDto record, CdsImportCommand command) throws IOException {
        prepare(record, command, false);
    }

    private CdsDemographicDetailsDto prepare(
            CdsRecordDto record, CdsImportCommand command, boolean resolveProvider)
            throws IOException {
        Map<String, CdsClinicalDiagnosticDto> warnings = new LinkedHashMap<>();
        Consumer<CdsClinicalDiagnosticDto> notice =
                value -> warnings.putIfAbsent(value.code() + "/" + value.field(), value);
        Map<String, String> selected = new LinkedHashMap<>();
        Map<String, Integer> scalarLimits =
                Map.of(
                        "PreferredSpokenLanguage",
                        25,
                        "PersonStatusDate",
                        128,
                        "HealthCard/Expirydate",
                        128,
                        "NoteAboutPatient",
                        131072);
        Selection selection = new Selection();
        Builder[] current = {null};
        boolean[] enrolmentSeen = {false};
        try {
            fields.forEachField(
                    record,
                    field -> {
                        if (!known(field)) return;
                        String scalarPath = field.path().replaceAll("\\[[0-9]+\\]", "");
                        Integer scalarLimit = scalarLimits.get(scalarPath);
                        if (scalarLimit != null && !selected.containsKey(scalarPath)) {
                            try {
                                selected.put(
                                        scalarPath,
                                        fields.readLogicalText(record, field, scalarLimit));
                            } catch (IOException failure) {
                                throw new UncheckedIOException(failure);
                            }
                        }
                        if (field.path().startsWith("Enrolment[")) enrolmentSeen[0] = true;
                        var match = HISTORY.matcher(field.path());
                        if (!match.matches()) return;
                        String group = match.group(1), relative = match.group(2);
                        try {
                            if (current[0] == null || !current[0].group.equals(group)) {
                                if (current[0] != null) selection.offer(current[0], command);
                                current[0] = new Builder(group);
                            }
                            if (relative == null) return;
                            String plain = relative.replaceAll("\\[[0-9]+\\]", "");
                            Integer maximum = ENROLLMENT_FIELDS.get(plain);
                            if (maximum == null) return;
                            String value;
                            try {
                                value = fields.readLogicalText(record, field, maximum);
                            } catch (CdsInputException capacity) {
                                if (!"CDS_TARGET_TEXT_CAPACITY_EXCEEDED".equals(capacity.code()))
                                    throw capacity;
                                notice.accept(
                                        new CdsClinicalDiagnosticDto(
                                                "CDS_ENROLMENT_FIELD_SOURCE_ONLY", plain));
                                current[0].unrepresented.put(
                                        plain, field.sha256() + record.effectiveSha256());
                                return;
                            }
                            if (value != null) current[0].values.put(plain, value);
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    });
            if (current[0] != null) selection.offer(current[0], command);
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
        if (enrolmentSeen[0] && selection.count == 0)
            throw new CdsClinicalException("CDS_ENROLMENT_HISTORY_REQUIRED", "Enrolment");
        String spoken = selected.get("PreferredSpokenLanguage"), language = null;
        if (spoken != null) {
            language =
                    SpokenLangProperties.getInstance()
                            .getLangByCode(spoken.toUpperCase(java.util.Locale.ROOT));
            if (language == null || language.isEmpty()) {
                language = null;
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_SPOKEN_LANGUAGE_SOURCE_ONLY", "PreferredSpokenLanguage"));
            } else
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_SPOKEN_LANGUAGE_NORMALIZED", "PreferredSpokenLanguage"));
        }
        String status =
                canonicalDate(
                        selected.get("PersonStatusDate"), "PersonStatusDate", command, notice);
        String expiry =
                canonicalDate(
                        selected.get("HealthCard/Expirydate"),
                        "HealthCard/Expirydate",
                        command,
                        notice);
        String note = selected.get("NoteAboutPatient");
        if (note != null && note.getBytes(StandardCharsets.UTF_8).length > 65518) {
            note =
                    "[CDS patient note exceeds native storage. Read the complete original and"
                        + " normalized NoteAboutPatient in demographic CDS source fields; no"
                        + " excerpt was substituted.]";
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_PATIENT_NOTE_COMPLETE_TEXT_SOURCE_VIEW", "NoteAboutPatient"));
        }
        CdsDemographicDetailsDto.Enrollment roster = null;
        if (selection.count > 0) {
            notice.accept(
                    new CdsClinicalDiagnosticDto(
                            "CDS_ENROLMENT_HISTORY_STRUCTURED_SOURCE_ONLY",
                            "Enrolment/EnrolmentHistory"));
            if (selection.ambiguous || selection.best == null) {
                notice.accept(
                        new CdsClinicalDiagnosticDto(
                                "CDS_ENROLMENT_CURRENT_REQUIRES_REVIEW",
                                "Enrolment/EnrolmentHistory"));
            } else {
                Builder latest = selection.best;
                String local = null;
                if (resolveProvider) {
                    var provider =
                            providers.resolve(
                                    latest.values.get("EnrolledToPhysician/OHIPPhysicianId"),
                                    null,
                                    latest.values.get("EnrolledToPhysician/Name/FirstName"),
                                    latest.values.get("EnrolledToPhysician/Name/LastName"),
                                    command.matchProviderNames());
                    local = provider.localProviderNo();
                    if (!provider.matched()
                            && provider.outcome()
                                    != CdsProviderMatchDto.Outcome.SOURCE_IDENTITY_ABSENT)
                        notice.accept(
                                new CdsClinicalDiagnosticDto(
                                        "CDS_ENROLLED_PROVIDER_" + provider.outcome(),
                                        "Enrolment/EnrolmentHistory/EnrolledToPhysician"));
                }
                roster =
                        new CdsDemographicDetailsDto.Enrollment(
                                "1".equals(latest.values.get("EnrollmentStatus")) ? "RO" : "TE",
                                latest.start.toString(),
                                latest.end == null ? null : latest.end.toString(),
                                latest.values.get("TerminationReason"),
                                local);
                if (selection.duplicates)
                    notice.accept(
                            new CdsClinicalDiagnosticDto(
                                    "CDS_ENROLMENT_IDENTICAL_HISTORY_DUPLICATE",
                                    "Enrolment/EnrolmentHistory"));
            }
        }
        return new CdsDemographicDetailsDto(
                language,
                status,
                expiry,
                note,
                roster,
                selection.count,
                List.copyOf(warnings.values()));
    }

    private static String canonicalDate(
            String source,
            String field,
            CdsImportCommand command,
            Consumer<CdsClinicalDiagnosticDto> notice) {
        LocalDate value = full(source, field, command);
        String result = value == null ? null : value.toString();
        if (result != null && !result.equals(source))
            notice.accept(new CdsClinicalDiagnosticDto("CDS_DEMOGRAPHIC_DATE_NORMALIZED", field));
        return result;
    }

    private static String date(CdsTemporalDto value, String field) {
        if (value.precision() == CdsTemporalDto.Precision.UNKNOWN) return null;
        if (!value.exactCalendarDate()
                || value.earliest().getYear() < 1
                || value.earliest().getYear() > 9999)
            throw new CdsClinicalException("CDS_DEMOGRAPHIC_DATE_REQUIRES_REVIEW", field);
        return value.earliest().toString();
    }

    private static LocalDate full(String raw, String field, CdsImportCommand command) {
        if (raw == null || raw.isBlank()) return null;
        var temporal =
                io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal.parse(
                        null, null, raw, null, field);
        temporal =
                io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal.shift(
                        temporal, command.timeshiftDays(), field);
        return LocalDate.parse(date(temporal, field));
    }

    private static boolean known(CdsFieldDto field) {
        return "cds".equals(field.namespace())
                || "cds_dt".equals(field.namespace())
                || "cds_dt2".equals(field.namespace());
    }

    private static final class Builder {
        final String group;
        final Map<String, String> values = new LinkedHashMap<>(),
                unrepresented = new LinkedHashMap<>();
        LocalDate start, end, effective;
        String fingerprint;

        Builder(String group) {
            this.group = group;
        }

        void finish(CdsImportCommand command) {
            String state = values.get("EnrollmentStatus");
            start = full(values.get("EnrollmentDate"), "Enrolment/EnrollmentDate", command);
            end =
                    full(
                            values.get("EnrollmentTerminationDate"),
                            "Enrolment/EnrollmentTerminationDate",
                            command);
            if (!"0".equals(state) && !"1".equals(state)
                    || start == null
                    || "0".equals(state) && end == null)
                throw new CdsClinicalException(
                        "CDS_ENROLMENT_REQUIRED_VALUES_MISSING", "Enrolment/EnrolmentHistory");
            if (end != null && end.isBefore(start) || "1".equals(state) && end != null)
                throw new CdsClinicalException(
                        "CDS_ENROLMENT_DATE_STATE_CONFLICT", "Enrolment/EnrolmentHistory");
            effective = "0".equals(state) ? end : start;
            var canonical = new java.util.TreeMap<>(values);
            canonical.put("EnrollmentDate", start.toString());
            if (end != null) canonical.put("EnrollmentTerminationDate", end.toString());
            unrepresented.forEach((key, value) -> canonical.put("unrepresented/" + key, value));
            MessageDigest digest = CdsPaths.sha256();
            canonical.forEach(
                    (path, value) -> {
                        digest.update(path.getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        digest.update(value.getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                    });
            fingerprint = CdsPaths.hex(digest.digest());
        }
    }

    private static final class Selection {
        Builder best;
        long count;
        boolean ambiguous, duplicates;

        void offer(Builder value, CdsImportCommand command) {
            value.finish(command);
            count++;
            if (best == null || value.effective.isAfter(best.effective)) {
                best = value;
                ambiguous = false;
                duplicates = false;
            } else if (value.effective.equals(best.effective)) {
                if (!Objects.equals(value.fingerprint, best.fingerprint)) ambiguous = true;
                else duplicates = true;
            }
        }
    }
}
