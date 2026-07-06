/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.io.FileUtils;
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
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Persists the generated prescription PDF and queues a fax job for asynchronous delivery.
 */
@Service
public class PrescriptionFaxService {

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

        String faxNo = req.getParameter("pharmaFax");
        if (faxNo != null) {
            faxNo = faxNo.trim().replaceAll("\\D", "");
        }
        String pharmaName = req.getParameter("pharmaName");
        String faxNumber = req.getParameter("clinicFax");
        if (faxNumber != null) {
            faxNumber = faxNumber.trim().replaceAll("\\D", "");
        }
        String demo = req.getParameter("demographic_no");

        String pdfid = req.getParameter("pdfId");
        if (pdfid != null) {
            pdfid = pdfid.replaceAll("[^a-zA-Z0-9_-]", "");
        }
        String pdfFile = "prescription_" + pdfid + ".pdf";
        String documentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");

        File baseDirFile = new File(documentDir);
        File validatedPdfFile = PathValidationUtils.validatePath(pdfFile, baseDirFile);
        Path filepath = validatedPdfFile.toPath();

        if (!Files.exists(filepath)) {
            try (java.io.OutputStream fileOut = Files.newOutputStream(filepath)) {
                baosPDF.writeTo(fileOut); // nosemgrep: java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- PDF bytes written to file, not HTTP response
            }
        }

        String tempPath = CarlosProperties.getInstance().getProperty(
                "fax_file_location", System.getProperty("java.io.tmpdir"));
        File tempDirFile = new File(tempPath);
        File validatedTempPdf = PathValidationUtils.validatePath("prescription_" + pdfid + ".pdf", tempDirFile);
        Path tempPdf = validatedTempPdf.toPath();

        if (Files.exists(filepath) && !Files.exists(tempPdf)) {
            FileUtils.copyFile(filepath.toFile(), tempPdf.toFile());
        }

        File validatedTxtFile = PathValidationUtils.validatePath("prescription_" + pdfid + ".txt", tempDirFile);
        String txtFile = validatedTxtFile.toString();
        try (FileWriter fstream = new FileWriter(txtFile);
             BufferedWriter out = new BufferedWriter(fstream)) {
            if (faxNo != null) {
                out.write(faxNo);
            }
        }

        List<FaxConfig> faxConfigs = faxConfigDao.findAll(null, null);
        String providerNo = LoggedInInfo.getLoggedInInfoFromSession(req).getLoggedInProviderNo();
        boolean validFaxNumber = false;

        for (FaxConfig faxConfig : faxConfigs) {
            if (faxConfig.getFaxNumber().equals(faxNumber)) {
                int numPages;
                try (PdfReader pdfReader = new PdfReader(filepath.toString())) {
                    numPages = pdfReader.getNumberOfPages();
                }

                FaxJob faxJob = new FaxJob();
                faxJob.setDestination(faxNo);
                faxJob.setFax_line(faxNumber);
                faxJob.setFile_name(pdfFile);
                faxJob.setUser(faxConfig.getFaxUser());
                faxJob.setRecipient(pharmaName);
                faxJob.setNumPages(numPages);
                faxJob.setStamp(new Date());
                faxJob.setStatus(FaxJob.STATUS.WAITING);
                faxJob.setOscarUser(providerNo);
                faxJob.setDemographicNo(Integer.parseInt(demo));

                faxJob.setSenderEmail(faxConfig.getSenderEmail());
                faxJob.setDirection(Direction.OUT);

                faxJobDao.persist(faxJob);
                faxManager.logFaxJob(loggedInInfo, faxJob, TransactionType.RX, -1);
                validFaxNumber = true;
                break;
            }
        }

        if (validFaxNumber) {
            LogAction.addLog(providerNo, LogConst.SENT, LogConst.CON_FAX, "PRESCRIPTION " + pdfFile);
        }

        return new PrescriptionFaxViewModel(validFaxNumber, pharmaName, faxNo);
    }
}
