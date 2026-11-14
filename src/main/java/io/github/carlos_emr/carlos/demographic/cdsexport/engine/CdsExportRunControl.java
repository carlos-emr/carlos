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
package io.github.carlos_emr.carlos.demographic.cdsexport.engine;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;

/** Per-attempt capabilities; identity/lease ownership never substitutes for current disclosure authority. */
public interface CdsExportRunControl {
    /** Outside source transactions; cheap local checks may throttle durable metadata probes. */
    void checkpoint();

    /** Inside an explicit read-only source transaction; metadata control uses an independent transaction. */
    void captureCheckpoint();

    /** Before/after capture, outside its admission barrier; no stale source-snapshot permission check. */
    void authorizePatient(int patient);

    /** The preferred pharmacy is a demographic wire field backed by the separate native Rx read privilege. */
    default void authorizePharmacy(int patient) {
        throw new SecurityException("CDS_EXPORT_PHARMACY_AUTHORIZATION_REQUIRED");
    }

    /** Fresh chart/category and patient program-domain read, even when the patient has no notes. */
    default void authorizeCaseManagement(int patient) {
        throw new SecurityException("CDS_EXPORT_CASE_MANAGEMENT_AUTHORIZATION_REQUIRED");
    }

    /** Fresh program/reporter-role checks for the exact note, outside the source transaction. */
    default void authorizeNote(
            CdsCategory category,
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto scope) {
        throw new SecurityException("CDS_EXPORT_NOTE_AUTHORIZATION_REQUIRED");
    }

    /**
     * Fresh program check for a patient issue owned by a program: true when every current identity
     * works in that program under the program part of the note rule. False holds the issue for
     * review; it never grants anything, so an adapter without this check stays fail-closed.
     */
    default boolean issueProgramReadable(int patient, CdsCategory category, String program) {
        return false;
    }

    /**
     * Fresh program check for a record restricted to a program (a document in Reports, a
     * prevention in Immunizations or PastHealth): true when every current exporting identity, the
     * effective provider and any operator, works in that program, which is any
     * {@code program_provider} membership in it, the rule the chart's document list applies.
     * The category's ordinary chart rights are required as well. False holds the patient; it never
     * grants anything, so an adapter without this check stays fail-closed.
     */
    default boolean restrictedProgramReadable(int patient, CdsCategory category, String program) {
        return false;
    }

    /**
     * Fresh check that every current identity may read the record's own security object for the
     * patient (consultations {@code _con}, encounter forms {@code _form}), as the chart requires
     * before it shows or prints that record. Only the clinician's printed form of a record
     * depends on it: false leaves the record exported as its data summary and never grants
     * anything, so an adapter without this check stays fail-closed.
     */
    default boolean recordReadable(int patient, String object) {
        return false;
    }

    /** One bounded patient batch; adapters may share only this fresh authorization transaction. */
    default void authorizeNotes(
            CdsCategory category,
            java.util.List<
                            io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                    .CdsExportNoteAccessDto>
                    scopes) {
        for (var scope : java.util.List.copyOf(scopes)) authorizeNote(category, scope);
    }

    /**
     * Starts one patient's authorization session, before any of the patient's data is read: the
     * adapter may decide the patient's fixed gates now, from the live database, and until
     * {@link #endPatient} or {@link #discardPatient} answer a check for this patient that
     * already granted access from the session instead of asking again. An adapter without
     * sessions answers every check fresh, which is always correct.
     *
     * @throws SecurityException when the exporting identities may no longer export at all
     */
    default void beginPatient(int patient) {}

    /**
     * Ends the patient's session after its last read and before its output is accepted, by
     * running every check that granted access again, uncached. A refusal or changed answer is a
     * {@link SecurityException} (the patient is held as {@code CDS_EXPORT_ACCESS_CHANGED}).
     */
    default void endPatient(int patient) {}

    /** Discards any session without checking; always safe, and called when a patient ends. */
    default void discardPatient() {}

    /**
     * Starts one re-authorization pass of an already captured patient: until
     * {@link #endAuthorizationPass}, the adapter may answer this patient's checks from the
     * authorization rows it reads now, from the live database, instead of once per check. No
     * patient data is read inside a pass, so it has no closing check, and nothing of it may be
     * kept once it ends. It is never opened inside a patient's session. An adapter without passes
     * answers every check fresh, which is always correct.
     *
     * @throws SecurityException when the exporting identities may no longer export at all
     */
    default void beginAuthorizationPass(int patient) {}

    /** Ends the re-authorization pass and discards everything it read; always safe. */
    default void endAuthorizationPass() {}

    /**
     * The checked capture reads of the patient whose session is open, for the patient's later
     * reads of the same sealed members to replay instead of decrypting and checking them again;
     * null when no session is open for this patient or on another thread. It is zeroed when the
     * session ends. An adapter without sessions returns null, and every read then reads and
     * checks its members afresh, which is always correct.
     */
    default io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportVerifiedCaptures
            verifiedCaptures(int patient) {
        return null;
    }

    /**
     * The tables whose column-visibility probe ({@code select d.* from T limit 0}) has already
     * succeeded in this attempt, updated by each capture's schema pass, so the probe runs once
     * per table and attempt instead of once per table and patient; every patient's capture still
     * verifies every table's structure. Null when the adapter keeps none: every capture then
     * probes every table, which is always correct.
     */
    default java.util.Set<String> schemaProbes() {
        return null;
    }

    /**
     * Fresh Reports and eForm read check for one patient, outside any source transaction, returning
     * the job's effective provider identity an eForm of that patient is rendered as. Never a system
     * identity; an adapter without a renderer identity refuses, so it stays fail-closed.
     */
    default io.github.carlos_emr.carlos.utility.LoggedInInfo authorizeRendering(int patient) {
        throw new SecurityException("CDS_EXPORT_RENDER_AUTHORIZATION_REQUIRED");
    }

    /** Actual sensitive laboratory occurrences must explicitly require their additional domain privilege. */
    void authorizeCategory(int patient, CdsCategory category, boolean sensitiveLaboratory);
}
