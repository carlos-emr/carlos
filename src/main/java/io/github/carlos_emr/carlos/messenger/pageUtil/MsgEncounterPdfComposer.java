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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.openpdf.text.DocumentException;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrint;
import io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Prints the Messenger chooser's current-encounter item (#5856): the patient's notes and CPP from the
 * modern chart, as the chart's own Print makes them ({@link CaseManagementPrint}), so the attachment
 * reads exactly like a chart print and goes through the same note filters.
 *
 * <p>The legacy encounter item attached the patient's running encounter text and CPP, mirrored into
 * {@code eChart} on every save. The modern chart stopped that mirror, so the old item froze at the
 * cutover; this item restores what it gave from the current chart.</p>
 *
 * <p><b>What it prints is decided here only</b> ({@link #ALL_NOTES}, {@link #CPP}). Provisional, as
 * recommended to Ben (D278, option A): the CPP and every current note, as the chart print's "All
 * notes" gives them. Prescriptions are their own chooser item; labs, preventions and allergies were
 * never part of the encounter item.</p>
 *
 * <p>The caller has already checked the user's access to the patient and read on
 * {@code _eChart}, {@code _demographic} and {@code _casemgmt.notes} for them, as the chart print
 * does. The print itself applies the chart's program note filter. A chart the print cannot show in
 * full (an imported record whose data the legacy print cannot project) is refused with
 * {@link CdsLegacyProjectionException}, as the chart print refuses it. A printed encounter is
 * audited as a chart print, marked as sent through Messenger, before it is handed back.</p>
 *
 * @since 2026-11-12
 */
public class MsgEncounterPdfComposer {

    /** D278, provisional option A: every current note, not a selection or a date range. */
    static final boolean ALL_NOTES = true;
    /** D278, provisional option A: with the CPP (social, family and medical history, concerns, ...). */
    static final boolean CPP = true;

    /** What the print shows, for the imported-record check: the notes and every CPP section. */
    static final Set<String> PRINTED_CATEGORIES = Set.of("ClinicalNotes", "PersonalHistory", "FamilyHistory",
            "PastHealth", "ProblemList", "RiskFactors", "AlertsAndSpecialNeeds");

    /** The audit row's data: which way the chart print left the chart. */
    static final String AUDIT_DATA = "messenger";

    /** The chart's own print, separated so the composer can be unit tested without a PDF renderer. */
    @FunctionalInterface
    interface ChartPrint {
        void print(LoggedInInfo loggedInInfo, int demographicNo, boolean allNotes, boolean cpp,
                   HttpServletRequest request, OutputStream out) throws IOException, DocumentException;
    }

    private final CdsLegacyProjectionService projections;
    private final ChartPrint chartPrint;

    public MsgEncounterPdfComposer() {
        this(SpringUtils.getBean(CdsLegacyProjectionService.class),
                (loggedInInfo, demographicNo, allNotes, cpp, request, out) -> new CaseManagementPrint().doPrint(
                        loggedInInfo, demographicNo, allNotes, new String[0], cpp,
                        false, false, false, false, false, null, null, request, out));
    }

    MsgEncounterPdfComposer(CdsLegacyProjectionService projections, ChartPrint chartPrint) {
        this.projections = Objects.requireNonNull(projections, "projections");
        this.chartPrint = Objects.requireNonNull(chartPrint, "chartPrint");
    }

    /**
     * {@link #compose} with the chart's own services, for {@link MsgAttachPDF2Action}: made when an
     * encounter is printed, not with the action, so building the action looks nothing up.
     */
    static byte[] composeWithChartServices(HttpServletRequest request, LoggedInInfo loggedInInfo, int demographicNo)
            throws IOException, DocumentException {
        return new MsgEncounterPdfComposer().compose(request, loggedInInfo, demographicNo);
    }

    /**
     * Prints the current encounter of an authorized patient.
     *
     * @param request the Messenger request (the print reads its session for the chart program)
     * @param loggedInInfo the user, already authorized for the patient and the encounter
     * @param demographicNo the patient
     * @return the PDF; empty if the print wrote nothing (then nothing is audited)
     * @throws CdsLegacyProjectionException if the chart print cannot show this patient's record
     * @throws IOException if the print fails
     * @throws DocumentException if the print fails
     */
    public byte[] compose(HttpServletRequest request, LoggedInInfo loggedInInfo, int demographicNo)
            throws IOException, DocumentException {
        projections.requireSupported(demographicNo, PRINTED_CATEGORIES);
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        chartPrint.print(loggedInInfo, demographicNo, ALL_NOTES, CPP, request, pdf);
        byte[] bytes = pdf.toByteArray();
        if (bytes.length > 0) {
            // Strict, as the chart print's own audit: an encounter whose disclosure cannot be recorded
            // does not leave the chart.
            String patient = String.valueOf(demographicNo);
            LogAction.addLogStrict(loggedInInfo, "print", "echart", patient, patient, AUDIT_DATA);
        }
        return bytes;
    }
}
