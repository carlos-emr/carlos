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
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * The side effects of faxing a prescription: deciding whether the caller may send it, finding the
 * clinic's fax line, writing the fax files and queueing the {@link FaxJob}.
 *
 * <p>Only {@code io.github.carlos_emr.carlos.prescript.pageUtil.RxFaxPrescription2Action}, which
 * refuses anything but POST, calls it. It was moved out of {@link FrmCustomedPDFServlet}, which used to
 * answer a fax on any HTTP method (issue #3108).</p>
 */
@Service
public class PrescriptionFaxService {

    private static final Logger logger = MiscUtils.getLogger();

    private final FaxConfigDao faxConfigDao;
    private final FaxJobDao faxJobDao;
    private final FaxManager faxManager;
    private final SecurityInfoManager securityInfoManager;

    public PrescriptionFaxService(FaxConfigDao faxConfigDao, FaxJobDao faxJobDao, FaxManager faxManager,
            SecurityInfoManager securityInfoManager) {
        this.faxConfigDao = faxConfigDao;
        this.faxJobDao = faxJobDao;
        this.faxManager = faxManager;
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * True when the caller may fax prescriptions for the patient the request names: {@code _rx} WRITE on that
     * patient and global {@code _fax} WRITE. It reads only {@code demographic_no} (role-wide when absent or
     * malformed) and never the script id, so a refusal says nothing about whether a script exists. A patient
     * directive that refuses the check counts as no. The record-based checks
     * ({@link #isFaxDeniedByPrivilege}) still apply to every caller who passes.
     */
    public boolean mayFaxForRequestedPatient(LoggedInInfo loggedInInfo, String demographicNo) {
        String patient = canonicalPatientNumber(demographicNo);
        try {
            return securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, patient)
                    && securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null);
        } catch (PatientDirectiveException e) {
            logger.warn("A directive refused the fax permission check ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * The patient number in the form privilege objects are keyed by ({@code "0001"} becomes {@code "1"}), as
     * the later record checks read it; {@code null} when it is absent, malformed or not a positive int.
     */
    private static String canonicalPatientNumber(String demographicNo) {
        if (demographicNo == null || !demographicNo.matches("[0-9]{1,10}")) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(demographicNo);
            return parsed > 0 ? String.valueOf(parsed) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * True when {@code pdfId} is a valid fax document id: 1-128 of {@code [a-zA-Z0-9_-]}. It names the fax
     * files, so anything else is refused rather than rewritten (rewriting could make two ids collide).
     */
    public static boolean isValidDocumentId(String pdfId) {
        return pdfId != null && pdfId.matches("[a-zA-Z0-9_-]{1,128}");
    }

    /**
     * True only when the prescription exists with a patient and the caller may READ it but lacks
     * {@code _rx} WRITE for that patient, {@code _demographic} READ for that patient, or global
     * {@code _fax} WRITE. A missing session, absent row, or a caller without READ is NOT reported as a
     * privilege denial (it returns {@code false}) and is left to the signature gate, which reports those
     * as "not signed".
     *
     * <p>That collision is deliberate: a caller without READ is told exactly what a {@code scriptId}
     * matching no prescription produces, so the permission wording cannot reveal whether a script id
     * exists.</p>
     */
    public boolean isFaxDeniedByPrivilege(Prescription prescription, LoggedInInfo loggedInInfo) {
        if (loggedInInfo == null || prescription == null || prescription.getDemographicId() == null) {
            return false;
        }
        String patient = String.valueOf(prescription.getDemographicId());
        try {
            // The fax heads the page with the patient's name, date of birth and health number read
            // from the demographic record (bindPatientIdentity), so faxing needs _demographic READ
            // on the patient as well as _rx WRITE. Deciding that here, before any side effect, turns
            // what would otherwise surface as DemographicManager's bare RuntimeException — a 500
            // half-way through the fax — into the same deliberate permission refusal.
            return securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.READ, patient)
                    && (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, patient)
                        || !securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", SecurityInfoManager.READ, patient)
                        || !securityInfoManager.hasPrivilege(loggedInInfo, "_fax", SecurityInfoManager.WRITE, null));
        } catch (PatientDirectiveException e) {
            // hasPrivilege rethrows PatientDirectiveException (SecurityInfoManagerImpl); unguarded it
            // would fail the fax with a 500 instead of refusing it. Answer "not denied HERE" so the
            // request falls through to resolveSignatureImage, whose own guard withholds the signature
            // and produces the generic "not signed" reply. Nothing is authorized by this answer — it
            // only declines to emit the specific permission wording, which is right under a directive:
            // that wording would confirm the script exists. ONLY the directive is absorbed: a database
            // or wiring failure in the privilege lookup must abort the request, not let it proceed to
            // the later gates as if the caller had simply lacked a privilege.
            logger.warn("A directive refused the fax permission check; deferring to the signature gate ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    /** The active fax account for the clinic fax line, or {@code null} when none is configured. */
    public FaxConfig findActiveFaxConfig(String faxLine) {
        return faxConfigDao.getActiveConfigByNumber(faxLine);
    }

    /**
     * True when a fax job already names this file. A missing file does not mean its name is unowned: an
     * older WAITING job may still reference it after partial cleanup.
     */
    public boolean isFileNameOwnedByJob(String pdfFile) {
        return !faxJobDao.findByFileName(pdfFile).isEmpty();
    }

    /**
     * Persists the job and its correlated fax audit row in one transaction. Once this is called, an
     * exception does not prove the job was rolled back: the commit acknowledgement can be lost after the
     * job became visible.
     */
    public void queueFaxJob(LoggedInInfo loggedInInfo, FaxJob faxJob, int prescriptionId) {
        faxManager.persistAndLogFaxJob(loggedInInfo, faxJob, TransactionType.RX, prescriptionId);
    }

    /**
     * Writes the fax spool PDF, its tracking file and the document copy, each created new so an existing
     * file is never overwritten. Every target is resolved and validated before the first write, and on
     * any failure the files this call created are removed again.
     *
     * @param pdfid the document id ({@link #isValidDocumentId}); anything else is refused before any write
     * @throws FileAlreadyExistsException when a target already exists, as a replay of a queued job would
     *                                    find; its artifacts are left untouched
     * @throws IOException                when a write fails
     * @throws IllegalArgumentException   when {@code pdfid} is not a valid document id
     * @throws SecurityException          when a configured directory or a target path is rejected by
     *                                    {@link PathValidationUtils}
     */
    // FindSecBugs PATH_TRAVERSAL_IN: every target is resolved under a configured directory by PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "every target is resolved under a configured directory by PathValidationUtils before use")
    public PreparedFaxFiles prepareFaxFiles(String documentDir, String pdfid, String pdfFile, String faxNo,
            ByteArrayOutputStream baosPDF) throws IOException {
        if (!isValidDocumentId(pdfid)) {
            throw new IllegalArgumentException("Invalid fax document id");
        }
        // Resolve and validate EVERY target before the first write. A bad spool directory must not
        // leave a valid-looking orphan in DOCUMENT_DIR that rejects a retry of the same attempt id.
        File baseDirFile = PathValidationUtils.resolveConfiguredDirectory(documentDir, "DOCUMENT_DIR");
        File validatedPdfFile = PathValidationUtils.validatePath(pdfFile, baseDirFile);
        Path filepath = validatedPdfFile.toPath();

        String tempPath = CarlosProperties.getInstance().getProperty("fax_file_location", System.getProperty("java.io.tmpdir"));
        File tempDirFile = PathValidationUtils.resolveConfiguredDirectory(tempPath, "fax_file_location");
        File validatedTempPdf = PathValidationUtils.validatePath("prescription_" + pdfid + ".pdf", tempDirFile);
        Path tempPdf = validatedTempPdf.toPath();
        File validatedTxtFile = PathValidationUtils.validatePath("prescription_" + pdfid + ".txt", tempDirFile);
        Path trackingFile = validatedTxtFile.toPath();

        List<Path> createdFiles = new ArrayList<>(3);
        try {
            // Claim the spool names first. Publishing DOCUMENT_DIR before a later
            // collision would briefly expose new content to an older queue entry.
            // CREATE_NEW retains exclusive ownership for concurrent submissions,
            // including when spool and document directories are the same target.
            if (!sameFileTarget(filepath, tempPdf)) {
                writeNewPdfFile(tempPdf, baosPDF, createdFiles);
            }
            writeFaxTrackingFile(trackingFile, faxNo, createdFiles);
            writeNewPdfFile(filepath, baosPDF, createdFiles);
            return new PreparedFaxFiles(filepath, createdFiles);
        } catch (IOException | RuntimeException e) {
            cleanupCreatedFiles(createdFiles, e);
            throw e;
        }
    }

    private boolean sameFileTarget(Path first, Path second) throws IOException {
        if (first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())) {
            return true;
        }
        return first.getFileName().equals(second.getFileName())
                && Files.isSameFile(first.getParent(), second.getParent());
    }

    private void writeNewPdfFile(Path filepath, ByteArrayOutputStream baosPDF, List<Path> createdFiles)
            throws IOException {
        try (OutputStream fileOut = Files.newOutputStream(filepath,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            createdFiles.add(filepath);
            baosPDF.writeTo(fileOut); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- PDF bytes written to file, not HTTP response
        }
    }

    private void writeFaxTrackingFile(Path trackingFile, String faxNo, List<Path> createdFiles) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(trackingFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            createdFiles.add(trackingFile);
            if (faxNo != null) {
                out.write(faxNo);
            }
        }
    }

    private static void cleanupCreatedFiles(List<Path> createdFiles, Throwable originalFailure) {
        ListIterator<Path> iterator = createdFiles.listIterator(createdFiles.size());
        while (iterator.hasPrevious()) {
            Path createdFile = iterator.previous();
            try {
                Files.deleteIfExists(createdFile);
            } catch (IOException | RuntimeException cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
                logger.warn("Unable to clean up failed prescription fax artifact ({})",
                        cleanupFailure.getClass().getSimpleName());
            }
        }
    }

    /** The files one fax request created; the document PDF is what the fax job names. */
    public static final class PreparedFaxFiles {
        private final Path documentPdf;
        private final List<Path> createdFiles;

        private PreparedFaxFiles(Path documentPdf, List<Path> createdFiles) {
            this.documentPdf = documentPdf;
            this.createdFiles = List.copyOf(createdFiles);
        }

        public Path getDocumentPdf() {
            return documentPdf;
        }

        /** Removes the files this request created, newest first; cleanup failures are suppressed onto {@code failure}. */
        public void cleanupAfterFailure(Throwable failure) {
            cleanupCreatedFiles(createdFiles, failure);
        }
    }
}
