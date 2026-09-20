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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerMeasurementPersister.EntryOutcome;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerMeasurementPersister.ValidationFailure;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Orchestrates a Health Tracker save: parse the posted form, validate and persist
 * each measurement, then append a progress note for the rows the clinician opted
 * into.
 *
 * <p>Servlet-free by design — the action hands in a parameter accessor rather than
 * an {@code HttpServletRequest}, which is what makes the whole save path testable
 * without a container. See the package javadoc for the layering rationale.
 */
public class HealthTrackerSubmissionService {

    private static final Logger logger = MiscUtils.getLogger();

    private final HealthTrackerSubmissionParser parser;
    private final HealthTrackerMeasurementPersister persister;
    private final HealthTrackerNoteComposer noteComposer;
    private final CaseManagementManager caseManagementManager;
    private final SecRoleDao secRoleDao;

    public HealthTrackerSubmissionService(HealthTrackerSubmissionParser parser,
                                          HealthTrackerMeasurementPersister persister,
                                          HealthTrackerNoteComposer noteComposer,
                                          CaseManagementManager caseManagementManager,
                                          SecRoleDao secRoleDao) {
        this.parser = parser;
        this.persister = persister;
        this.noteComposer = noteComposer;
        this.caseManagementManager = caseManagementManager;
        this.secRoleDao = secRoleDao;
    }

    /**
     * Saves everything the clinician typed into the Health Tracker form.
     *
     * <p>Partial success is normal and intended: valid rows are written even when
     * a sibling row fails validation, matching how the page reports errors per
     * measurement rather than refusing the whole form.
     *
     * @param flowSheet the flowsheet named by the request, already merged with the
     *        provider/patient customizations
     * @param parameters accessor over the request parameters
     * @param demographicNo the patient whose tracker was open
     * @param providerNo the logged-in provider, recorded as the measurement author
     * @param appointmentNo the current appointment, or {@code 0} when there is none
     * @param programNo the CAISI program the note is filed under
     * @param defaultDateObserved the form's hidden fallback observation date
     *        ({@code yyyy-MM-dd})
     * @return the outcome; never {@code null}
     */
    public HealthTrackerSubmissionResult submit(MeasurementFlowSheet flowSheet,
                                                Function<String, String> parameters,
                                                int demographicNo,
                                                String providerNo,
                                                int appointmentNo,
                                                String programNo,
                                                String defaultDateObserved) {

        List<HealthTrackerEntry> entries = parser.parse(flowSheet, parameters, defaultDateObserved);

        List<HealthTrackerEntry> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        List<ValidationFailure> failures = new ArrayList<>();
        int persistedCount = 0;

        for (HealthTrackerEntry entry : entries) {
            EntryOutcome outcome = persister.persist(entry, demographicNo, providerNo, appointmentNo);
            if (outcome.valid()) {
                accepted.add(entry);
                if (outcome.persisted()) {
                    persistedCount++;
                }
            } else {
                rejected.add(entry.displayName() + ": " + entry.value());
                failures.addAll(outcome.failures());
            }
        }

        String noteText = noteComposer.compose(accepted, new Date());
        if (!noteText.isEmpty()) {
            saveNote(demographicNo, providerNo, programNo, noteText, appointmentNo);
        }

        return new HealthTrackerSubmissionResult(persistedCount, rejected, failures, noteText);
    }

    /**
     * Files the composed text as a signed encounter note.
     *
     * <p>Note creation is best effort: the measurements are already committed by
     * the time this runs, and losing the convenience note must not turn a
     * successful save into an error page for the clinician.
     */
    private void saveNote(int demographicNo, String providerNo, String programNo, String noteText, int appointmentNo) {
        try {
            Date now = new Date();
            CaseManagementNote note = new CaseManagementNote();
            note.setUpdate_date(now);
            note.setObservation_date(now);
            note.setDemographic_no(String.valueOf(demographicNo));
            note.setProviderNo(providerNo);
            note.setNote(noteText);
            note.setSigned(true);
            note.setSigning_provider_no(providerNo);
            note.setProgram_no(programNo);
            note.setReporter_caisi_role(doctorRoleId());
            note.setReporter_program_team("0");
            note.setLocked(false);
            note.setHistory(noteText);
            note.setPosition(0);
            note.setAppointmentNo(appointmentNo);

            caseManagementManager.saveNoteSimple(note);
        } catch (RuntimeException e) {
            logger.error("Health Tracker measurements saved but the progress note could not be filed for demographic {}",
                    LogSafe.sanitize(String.valueOf(demographicNo)), e);
        }
    }

    private String doctorRoleId() {
        SecRole doctorRole = secRoleDao.findByName("doctor");
        // Installations that renamed or removed the stock "doctor" role would
        // otherwise NPE here and lose the whole note.
        return doctorRole == null || doctorRole.getId() == null ? "0" : doctorRole.getId().toString();
    }
}
