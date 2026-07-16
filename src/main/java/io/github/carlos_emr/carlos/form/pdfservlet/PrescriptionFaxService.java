/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import org.openpdf.text.pdf.PdfReader;
import org.springframework.stereotype.Service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.commn.model.FaxJob.Direction;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Persists the generated prescription PDF and queues a fax job for asynchronous delivery.
 */
@Service
public class PrescriptionFaxService {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Upper bound on the client-supplied {@code pdfId}. It is only a filename discriminator, so a
     * generous cap keeps generated artifact names within filesystem limits and bounds regex work
     * without constraining any legitimate caller.
     */
    private static final int MAX_PDF_ID_LENGTH = 64;

    private final FaxJobDao faxJobDao;
    private final FaxConfigDao faxConfigDao;
    private final FaxManager faxManager;

    public PrescriptionFaxService(FaxJobDao faxJobDao, FaxConfigDao faxConfigDao, FaxManager faxManager) {
        this.faxJobDao = faxJobDao;
        this.faxConfigDao = faxConfigDao;
        this.faxManager = faxManager;
    }

    /**
     * Writes prescription fax artifacts and persists a waiting {@link FaxJob}.
     *
     * @param loggedInInfo current user context for audit logging
     * @param req request carrying the legacy Rx fax parameters
     * @param baosPDF generated prescription PDF bytes
     * @return status data for the legacy HTML response
     * @throws IOException when writing the PDF/tracking files or reading generated PDF metadata fails
     */
    // FindSecBugs PATH_TRAVERSAL_IN: filenames are constrained and validated against configured CARLOS directories.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "filenames are constrained and validated against configured CARLOS directories")
    public PrescriptionFaxViewModel createFaxJob(
            LoggedInInfo loggedInInfo, HttpServletRequest req, ByteArrayOutputStream baosPDF) throws IOException {

        String destinationFaxNo = normalizeFaxNumber(req.getParameter("pharmaFax"));
        validateDestinationFaxNumber(destinationFaxNo);
        String pharmaName = req.getParameter("pharmaName");
        String clinicFaxNo = normalizeFaxNumber(req.getParameter("clinicFax"));
        if (!isValidClinicFaxNumber(clinicFaxNo)) {
            return PrescriptionFaxViewModel.invalidClinicFax(pharmaName, destinationFaxNo);
        }
        String demo = req.getParameter("demographic_no");
        int demographicNo = validateDemographicNo(demo);

        String pdfId = validatePdfId(req.getParameter("pdfId"));
        String artifactBaseName = PathValidationUtils.validateGeneratedFileName(
                "prescription_" + pdfId + "_" + UUID.randomUUID());
        String pdfFile = PathValidationUtils.validateGeneratedFileName(artifactBaseName + ".pdf");
        List<FaxConfig> faxConfigs = faxConfigDao.findAll(null, null);
        FaxConfig matchedFaxConfig = findMatchingFaxConfig(faxConfigs, clinicFaxNo);
        if (matchedFaxConfig == null) {
            return PrescriptionFaxViewModel.noMatchingClinicFaxConfig(pharmaName, destinationFaxNo);
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        String documentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");

        File baseDirFile = new File(documentDir);
        // nosemgrep: java.lang.security.httpservlet-path-traversal -- generated filename is validated above.
        File validatedPdfFile = PathValidationUtils.validatePath(pdfFile, baseDirFile);
        Path filepath = validatedPdfFile.toPath();

        String tempPath = CarlosProperties.getInstance().getProperty(
                "fax_file_location", System.getProperty("java.io.tmpdir"));
        File tempDirFile = new File(tempPath);
        // nosemgrep: java.lang.security.httpservlet-path-traversal -- generated filename is validated above.
        File validatedTempPdf = PathValidationUtils.validatePath(pdfFile, tempDirFile);
        Path tempPdf = validatedTempPdf.toPath();

        String txtFileName = PathValidationUtils.validateGeneratedFileName(artifactBaseName + ".txt");
        // nosemgrep: java.lang.security.httpservlet-path-traversal -- generated filename is validated above.
        File validatedTxtFile = PathValidationUtils.validatePath(txtFileName, tempDirFile);
        Path txtPath = validatedTxtFile.toPath();

        boolean persisted = false;
        List<Path> createdArtifactPaths = new ArrayList<>();
        try {
            Files.createFile(filepath);
            createdArtifactPaths.add(filepath);
            try (java.io.OutputStream fileOut = Files.newOutputStream(filepath, StandardOpenOption.WRITE)) {
                baosPDF.writeTo(fileOut); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- PDF bytes written to file, not HTTP response
            }

            Files.createFile(tempPdf);
            createdArtifactPaths.add(tempPdf);
            try (java.io.OutputStream tempOut = Files.newOutputStream(tempPdf, StandardOpenOption.WRITE)) {
                Files.copy(filepath, tempOut);
            }

            Files.createFile(txtPath);
            createdArtifactPaths.add(txtPath);
            try (var out = Files.newBufferedWriter(txtPath, StandardCharsets.US_ASCII, StandardOpenOption.WRITE)) {
                out.write(destinationFaxNo);
            }

            int numPages;
            try (PdfReader pdfReader = new PdfReader(filepath.toString())) {
                numPages = pdfReader.getNumberOfPages();
            }

            FaxJob faxJob = new FaxJob();
            faxJob.setDestination(destinationFaxNo);
            faxJob.setFax_line(clinicFaxNo);
            faxJob.setFile_name(pdfFile);
            faxJob.setUser(matchedFaxConfig.getFaxUser());
            faxJob.setRecipient(pharmaName);
            faxJob.setNumPages(numPages);
            faxJob.setStamp(new Date());
            faxJob.setStatus(FaxJob.STATUS.WAITING);
            faxJob.setOscarUser(providerNo);
            faxJob.setDemographicNo(demographicNo);

            faxJob.setSenderEmail(matchedFaxConfig.getSenderEmail());
            faxJob.setDirection(Direction.OUT);

            faxJobDao.persist(faxJob);
            persisted = true;
            logPersistedFaxJob(loggedInInfo, faxJob, providerNo, pdfFile);

            return new PrescriptionFaxViewModel(true, pharmaName, destinationFaxNo);
        } catch (IOException | RuntimeException e) {
            if (!persisted) {
                cleanupUnpersistedArtifacts(e, createdArtifactPaths);
            }
            throw e;
        }
    }

    private String normalizeFaxNumber(String faxNumber) {
        if (faxNumber == null) {
            return "";
        }
        return faxNumber.trim().replaceAll("\\D", "");
    }

    private void validateDestinationFaxNumber(String faxNumber) {
        if (faxNumber.length() < 7) {
            throw new IllegalArgumentException("Invalid destination fax number");
        }
    }

    private boolean isValidClinicFaxNumber(String faxNumber) {
        return faxNumber.length() >= 7;
    }

    private FaxConfig findMatchingFaxConfig(List<FaxConfig> faxConfigs, String faxNumber) {
        for (FaxConfig faxConfig : faxConfigs) {
            if (normalizeFaxNumber(faxConfig.getFaxNumber()).equals(faxNumber)) {
                return faxConfig;
            }
        }
        return null;
    }

    private String validatePdfId(String rawPdfId) {
        if (rawPdfId == null || rawPdfId.isBlank() || rawPdfId.length() > MAX_PDF_ID_LENGTH
                || !rawPdfId.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid prescription PDF id");
        }
        return PathValidationUtils.validatePathComponent(rawPdfId, "prescription PDF id");
    }

    private int validateDemographicNo(String rawDemographicNo) {
        if (rawDemographicNo == null || !rawDemographicNo.matches("\\d+")) {
            throw new IllegalArgumentException("Invalid demographic number");
        }
        try {
            return Integer.parseInt(rawDemographicNo);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid demographic number", e);
        }
    }

    private void logPersistedFaxJob(LoggedInInfo loggedInInfo, FaxJob faxJob, String providerNo, String pdfFile) {
        try {
            faxManager.logFaxJob(loggedInInfo, faxJob, TransactionType.RX, -1);
            LogAction.addLog(providerNo, LogConst.SENT, LogConst.CON_FAX, "PRESCRIPTION " + pdfFile);
        } catch (RuntimeException auditFailure) {
            logger.warn("Rx fax audit logging failed after FaxJob was persisted", auditFailure);
        }
    }

    private void cleanupUnpersistedArtifacts(Throwable originalFailure, List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }
    }
}
