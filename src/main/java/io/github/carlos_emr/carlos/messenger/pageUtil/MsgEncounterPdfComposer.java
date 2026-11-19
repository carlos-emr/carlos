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
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.openpdf.text.DocumentException;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementPrint;
import io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Prints the Messenger chooser's chart-notes item (#5856): the patient's notes and CPP from the modern
 * chart, as the chart's own Print makes them ({@link CaseManagementPrint}), so the attachment reads
 * like a chart print and goes through the same note filters.
 *
 * <p>The legacy encounter item attached the patient's running encounter text and CPP, mirrored into
 * {@code eChart} on every save. The modern chart stopped that mirror, so the old item froze at the
 * cutover; this item restores what it gave from the current chart.</p>
 *
 * <p><b>What it prints is decided here only.</b> As decided in #5856: the CPP and every current
 * note, as the chart print's "All notes" gives them ({@link #ALL_NOTES}, {@link #CPP}), with no
 * prescriptions, labs, preventions or allergies (prescriptions are their own chooser item), no date
 * range and no selection. Because the attachment goes to someone else, locked notes are left out. The
 * print reads none of the Messenger request's parameters ({@link ParameterlessRequest}), so nothing
 * posted to the chooser can add to it.</p>
 *
 * <p>The caller has already checked the user's access to the patient and read on {@code _eChart},
 * {@code _demographic} and {@code _casemgmt.notes} for them, as the chart print does. The print itself
 * applies the chart's program note filter. A chart the print cannot show in full (an imported record
 * whose data the legacy print cannot project) is refused with {@link CdsLegacyProjectionException}, as
 * the chart print refuses it. Chart notes leaving the chart through Messenger are audited as the chart's own
 * Print is: attached ({@link #recordAttached}) or previewed ({@link #recordPreviewed}).</p>
 *
 * @since 2026-11-12
 */
public class MsgEncounterPdfComposer {

    /** Decided in #5856: every current note, not a selection or a date range. */
    static final boolean ALL_NOTES = true;
    /** Decided in #5856: with the CPP (social, family and medical history, concerns, ...). */
    static final boolean CPP = true;

    /** What the print shows, for the imported-record check: the notes and every CPP section. */
    static final Set<String> PRINTED_CATEGORIES = Set.of("ClinicalNotes", "PersonalHistory", "FamilyHistory",
            "PastHealth", "ProblemList", "RiskFactors", "AlertsAndSpecialNeeds");

    /** The audit row's data: which way the chart print left the chart. */
    static final String AUDIT_DATA = "messenger";
    /** The audit data of chart notes shown through Messenger Preview. */
    static final String PREVIEW_AUDIT_DATA = "messenger-preview";

    /** The chart's own print, separated so the composer can be unit tested without a PDF renderer. */
    @FunctionalInterface
    interface ChartPrint {
        /**
         * Prints into {@code out}; a print that cannot keep its finished PDF within {@code maxBytes} refuses
         * it ({@link CaseManagementPrint.CompletePdfTooLargeException}) before writing any of it.
         */
        void print(LoggedInInfo loggedInInfo, int demographicNo, boolean allNotes, boolean cpp,
                   HttpServletRequest request, OutputStream out, long maxBytes) throws IOException, DocumentException;
    }

    private final CdsLegacyProjectionService projections;
    private final ChartPrint chartPrint;

    /**
     * The composer the action uses: the projection service from Spring, and the chart's own print
     * ({@link CaseManagementPrint}) with locked notes left out, printing only the notes and, when
     * asked, the CPP.
     */
    public MsgEncounterPdfComposer() {
        this(SpringUtils.getBean(CdsLegacyProjectionService.class),
                (loggedInInfo, demographicNo, allNotes, cpp, request, out, maxBytes) -> {
                    CaseManagementPrint print = new CaseManagementPrint();
                    print.leaveOutLockedNotes();
                    // The print renders and merges on disk, then refuses a finished PDF over the cap
                    // before copying any of it here.
                    print.limitCompletePdfBytes(maxBytes);
                    // No selection, Rx, labs, preventions, allergies or date range: only notes and CPP.
                    print.doPrint(loggedInInfo, demographicNo, allNotes, new String[0], cpp,
                            false, false, false, false, false, null, null, new ParameterlessRequest(request), out);
                });
    }

    MsgEncounterPdfComposer(CdsLegacyProjectionService projections, ChartPrint chartPrint) {
        this.projections = Objects.requireNonNull(projections, "projections");
        this.chartPrint = Objects.requireNonNull(chartPrint, "chartPrint");
    }

    /**
     * {@link #compose} with the chart's own services, for {@link MsgAttachPDF2Action}: made when an
     * encounter is printed, not with the action, so building the action looks nothing up.
     */
    static byte[] composeWithChartServices(HttpServletRequest request, LoggedInInfo loggedInInfo, int demographicNo,
                                           long maxBytes) throws IOException, DocumentException {
        return new MsgEncounterPdfComposer().compose(request, loggedInInfo, demographicNo, maxBytes);
    }

    /**
     * Prints the chart notes of an authorized patient. Nothing is audited here: the caller audits what
     * leaves the chart ({@link #recordAttached}, {@link #recordPreviewed}).
     *
     * @param request the Messenger request (the print reads its session and attributes, never its
     *                parameters)
     * @param loggedInInfo the user, already authorized for the patient and the encounter
     * @param demographicNo the patient
     * @return the PDF; empty if the print wrote nothing
     * @throws CdsLegacyProjectionException if the chart print cannot show this patient's record
     * @throws IOException if the print fails
     * @throws DocumentException if the print fails
     */
    public byte[] compose(HttpServletRequest request, LoggedInInfo loggedInInfo, int demographicNo)
            throws IOException, DocumentException {
        return compose(request, loggedInInfo, demographicNo, Long.MAX_VALUE);
    }

    /**
     * {@link #compose(HttpServletRequest, LoggedInInfo, int)} for a caller that can use at most
     * {@code maxBytes}. The chart print renders and merges on disk (temporary files), as the chart's own
     * Print does; the finished PDF's size is then checked against {@code maxBytes} before any of it is
     * copied into memory, so a chart too large for its caller is never held here, copied or Base64-encoded.
     * The buffer below also refuses to grow past the cap, in case a print writes more than it said.
     * Rendering and merging themselves are not bounded by this (see {@link CaseManagementPrint}).
     *
     * @param maxBytes the most PDF bytes the caller can use
     * @return the PDF, at most {@code maxBytes} long; empty if the print wrote nothing
     * @throws PrintTooLargeException if the print would be longer than {@code maxBytes}; nothing is returned
     * @throws CdsLegacyProjectionException if the chart print cannot show this patient's record
     * @throws IOException if the print fails
     * @throws DocumentException if the print fails
     */
    public byte[] compose(HttpServletRequest request, LoggedInInfo loggedInInfo, int demographicNo, long maxBytes)
            throws IOException, DocumentException {
        projections.requireSupported(demographicNo, PRINTED_CATEGORIES);
        CappedPdfBuffer pdf = new CappedPdfBuffer(maxBytes);
        try {
            chartPrint.print(loggedInInfo, demographicNo, ALL_NOTES, CPP, request, pdf, maxBytes);
        } catch (IOException | RuntimeException failure) {
            // The print refuses a finished PDF over the cap with its own exception. Should a print write past
            // the cap anyway, it may wrap the buffer's refusal (OpenPDF converts it to its own runtime exception,
            // and the chart print rethrows any failure as a plain IOException), so the buffer, not the
            // exception's type, says whether the cap was reached.
            if (pdf.exceeded() || failure instanceof CaseManagementPrint.CompletePdfTooLargeException) {
                throw new PrintTooLargeException(maxBytes);
            }
            throw failure;
        }
        // A print that swallowed the refusal and finished anyway has written a truncated PDF: refused too.
        if (pdf.exceeded()) {
            throw new PrintTooLargeException(maxBytes);
        }
        return pdf.toByteArray();
    }

    /** The chart print is longer than its caller can use; none of it was kept. */
    public static final class PrintTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
        private final long maxBytes;

        PrintTooLargeException(long maxBytes) {
            // Sizes only: never the patient or any of the print.
            super("The chart print is larger than " + maxBytes + " bytes");
            this.maxBytes = maxBytes;
        }

        /** @return the cap the print reached */
        public long maxBytes() {
            return maxBytes;
        }
    }

    /**
     * The print's buffer, a second guard behind the print's own size check: refusing to grow past the
     * cap, the write that would pass it keeps nothing and
     * throws, as does every write after it, so a print that catches the refusal and carries on cannot grow
     * the buffer either. The buffer therefore never holds more than the cap (plus the array's growth
     * slack).
     */
    static final class CappedPdfBuffer extends ByteArrayOutputStream {
        private final long maxBytes;
        private boolean exceeded;

        CappedPdfBuffer(long maxBytes) {
            this.maxBytes = maxBytes;
        }

        boolean exceeded() {
            return exceeded;
        }

        /** Refuses a write that would pass the cap, and every write once one has. */
        private void reserve(int length) {
            if (exceeded || (long) count + length > maxBytes) {
                exceeded = true;
                throw new CapReached(maxBytes);
            }
        }

        @Override
        public synchronized void write(int b) {
            reserve(1);
            super.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            reserve(len);
            super.write(b, off, len);
        }

        @Override
        public void writeBytes(byte[] b) {
            write(b, 0, b.length);
        }

        @Override
        public synchronized void writeTo(OutputStream out) throws IOException {
            if (exceeded) {
                throw new PrintTooLargeException(maxBytes);
            }
            super.writeTo(out);
        }
    }

    /**
     * {@link ByteArrayOutputStream}'s writes declare no {@link IOException}, so the cap is reported
     * through this unchecked carrier; {@link #compose} recognises it by the buffer's state, whatever the
     * print wraps it in.
     */
    static final class CapReached extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CapReached(long maxBytes) {
            super("The chart print is larger than " + maxBytes + " bytes");
        }
    }

    /**
     * Records that the chart notes of a patient were attached to a message, as a chart print sent
     * through Messenger. Strict, as the chart print's own audit: if it cannot be recorded it throws, and
     * the caller stages nothing.
     */
    static void recordAttached(LoggedInInfo loggedInInfo, int demographicNo) {
        String patient = String.valueOf(demographicNo);
        LogAction.addLogStrict(loggedInInfo, "print", "echart", patient, patient, AUDIT_DATA);
    }

    /** Records chart notes leaving the chart through Messenger: previewed or attached. Strict. */
    static void recordDisclosed(LoggedInInfo loggedInInfo, int demographicNo, boolean preview) {
        if (preview) {
            recordPreviewed(loggedInInfo, demographicNo);
        } else {
            recordAttached(loggedInInfo, demographicNo);
        }
    }

    /**
     * Records that the chart notes of a patient were shown through Messenger Preview. The preview is the same
     * full PDF the chart print gives, and can be saved from the browser, so it is audited as the chart print is,
     * before the first byte is sent. Strict: if it cannot be recorded it throws, and nothing is sent.
     */
    static void recordPreviewed(LoggedInInfo loggedInInfo, int demographicNo) {
        String patient = String.valueOf(demographicNo);
        LogAction.addLogStrict(loggedInInfo, "print", "echart", patient, patient, PREVIEW_AUDIT_DATA);
    }

    /**
     * The Messenger request as the chart print sees it: its session, attributes and locale, but none
     * of its parameters. The print binds the patient itself; a chooser POST cannot add print
     * extensions or choose a chart context.
     */
    static final class ParameterlessRequest extends HttpServletRequestWrapper {

        ParameterlessRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            return null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Map.of();
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.emptyEnumeration();
        }

        @Override
        public String[] getParameterValues(String name) {
            return null;
        }

        @Override
        public String getQueryString() {
            return null;
        }
    }
}
