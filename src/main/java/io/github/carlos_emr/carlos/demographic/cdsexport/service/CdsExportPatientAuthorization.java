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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * One patient's authorization session inside one export worker.
 *
 * <p>Every capture, reconciliation, mapping and emission step asks the run control again whether
 * the exporting identities may read the patient, a category, a note, a program-restricted record
 * or a printed record. Answered fresh each time, a chart of a few hundred notes cost hundreds of
 * thousands of identity and privilege queries. Inside a session the first answer for each check
 * is kept, and {@link #finish()} runs every check that granted access again, uncached, after the
 * patient's last read and before its output is accepted. A grant revoked at any point while the
 * patient was being read is therefore still refused before anything of that patient is accepted
 * ({@code CDS_EXPORT_ACCESS_CHANGED}), as it was when every step asked afresh: the closing check is
 * at least as late as the last per-step check used to be.
 *
 * <p>The session belongs to exactly one patient and one worker thread. Checks for another patient
 * never use it, and the caller discards it when the patient is finished or held. Answers that
 * refused access are kept too: a refusal holds the patient or leaves a record out, so no grant
 * depends on it, and it is not repeated.
 */
final class CdsExportPatientAuthorization {

    /** The uncached checks, bound to the job's actor and command. */
    interface Fresh {
        void patient(int patient);

        void pharmacy(int patient);

        void caseManagement(int patient);

        void category(int patient, CdsCategory category, boolean sensitiveLaboratory);

        /** One note inside this session (chart gates and grants kept in {@code grants}). */
        void note(
                CdsCategory category,
                CdsExportNoteAccessDto scope,
                CdsExportAccessService.PatientGrants grants);

        /** Every given note of one category, uncached, in ascending note order (at most 8192). */
        void notes(CdsCategory category, List<CdsExportNoteAccessDto> scopes);

        boolean issueProgram(int patient, CdsCategory category, String program);

        boolean restrictedProgram(int patient, CdsCategory category, String program);

        boolean record(int patient, String object);
    }

    /** The largest batch the uncached note check accepts. */
    static final int NOTE_BATCH = 8192;

    private final int patient;
    private final Fresh fresh;
    private final CdsExportAccessService.PatientGrants grants;
    private final Set<String> granted = new LinkedHashSet<>();
    private final Map<String, Boolean> answers = new LinkedHashMap<>();
    private final Map<CdsCategory, TreeMap<Long, CdsExportNoteAccessDto>> notes =
            new EnumMap<>(CdsCategory.class);

    CdsExportPatientAuthorization(int patient, Fresh fresh) {
        this.fresh = Objects.requireNonNull(fresh);
        this.grants = new CdsExportAccessService.PatientGrants(patient);
        this.patient = patient;
    }

    int patient() {
        return patient;
    }

    void patient(int patient) {
        once(patient, "patient", () -> fresh.patient(patient));
    }

    void pharmacy(int patient) {
        once(patient, "pharmacy", () -> fresh.pharmacy(patient));
    }

    void caseManagement(int patient) {
        once(patient, "case-management", () -> fresh.caseManagement(patient));
    }

    void category(int patient, CdsCategory category, boolean sensitiveLaboratory) {
        once(
                patient,
                "category\u0000" + category.name() + '\u0000' + sensitiveLaboratory,
                () -> fresh.category(patient, category, sensitiveLaboratory));
    }

    void note(CdsCategory category, CdsExportNoteAccessDto scope) {
        Objects.requireNonNull(category);
        own(scope.patient());
        var seen = notes.computeIfAbsent(category, ignored -> new TreeMap<>());
        var prior = seen.get(scope.noteId());
        if (prior != null) {
            // One note has one physical scope; a different one cannot both match the chart.
            if (!prior.equals(scope)) throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
            return;
        }
        fresh.note(category, scope, grants);
        seen.put(scope.noteId(), scope);
    }

    boolean issueProgram(int patient, CdsCategory category, String program) {
        return answer(
                patient,
                "issue\u0000" + category.name() + '\u0000' + program,
                () -> fresh.issueProgram(patient, category, program));
    }

    boolean restrictedProgram(int patient, CdsCategory category, String program) {
        return answer(
                patient,
                "restricted\u0000" + category.name() + '\u0000' + program,
                () -> fresh.restrictedProgram(patient, category, program));
    }

    boolean record(int patient, String object) {
        return answer(patient, "record\u0000" + object, () -> fresh.record(patient, object));
    }

    /**
     * Runs every check that granted access again, uncached: the patient, pharmacy, case
     * management and category gates, every note of every category in batches, and every program
     * and record object that answered yes. Any refusal or changed answer is a
     * {@link SecurityException}, so the patient is held instead of accepted.
     */
    void finish() {
        for (String key : List.copyOf(granted)) {
            String[] parts = key.split("\u0000", -1);
            switch (parts[0]) {
                case "patient" -> fresh.patient(patient);
                case "pharmacy" -> fresh.pharmacy(patient);
                case "case-management" -> fresh.caseManagement(patient);
                case "category" ->
                        fresh.category(
                                patient, CdsCategory.valueOf(parts[1]), Boolean.parseBoolean(parts[2]));
                default -> throw new IllegalStateException("CDS_EXPORT_AUTHORIZATION_KEY_INVALID");
            }
        }
        for (var entry : notes.entrySet()) {
            var batch = new ArrayList<CdsExportNoteAccessDto>(NOTE_BATCH);
            for (var scope : entry.getValue().values()) {
                batch.add(scope);
                if (batch.size() == NOTE_BATCH) {
                    fresh.notes(entry.getKey(), List.copyOf(batch));
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) fresh.notes(entry.getKey(), List.copyOf(batch));
        }
        for (var entry : answers.entrySet()) {
            if (!entry.getValue()) continue;
            String[] parts = entry.getKey().split("\u0000", -1);
            boolean still =
                    switch (parts[0]) {
                        case "issue" ->
                                fresh.issueProgram(patient, CdsCategory.valueOf(parts[1]), parts[2]);
                        case "restricted" ->
                                fresh.restrictedProgram(
                                        patient, CdsCategory.valueOf(parts[1]), parts[2]);
                        case "record" -> fresh.record(patient, parts[1]);
                        default ->
                                throw new IllegalStateException(
                                        "CDS_EXPORT_AUTHORIZATION_KEY_INVALID");
                    };
            if (!still) throw new SecurityException("CDS_EXPORT_ACCESS_CHANGED");
        }
    }

    private void once(int patient, String key, Runnable check) {
        own(patient);
        if (granted.contains(key)) return;
        check.run();
        granted.add(key);
    }

    private boolean answer(int patient, String key, java.util.function.BooleanSupplier check) {
        own(patient);
        Boolean known = answers.get(key);
        if (known != null) return known;
        boolean answer = check.getAsBoolean();
        answers.put(key, answer);
        return answer;
    }

    private void own(int patient) {
        if (patient != this.patient) throw new IllegalStateException("CDS_EXPORT_SESSION_PATIENT_MISMATCH");
    }

    @Override
    public String toString() {
        return "CdsExportPatientAuthorization[protected]";
    }
}
