/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import org.apache.struts2.ActionSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.action.UploadedFilesAware;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.BatchEligibilityDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingClaimBatchAcknowledgementReportParser;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingClaimsErrorReportParser;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFileImportException;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingEdtObecOutputSpecificationParser;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingClaimsErrorReportImportService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnErrorReportService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URLEncoder;
import java.util.List;
import java.util.ArrayList;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
/**
 * Routes operator-uploaded MOH return files to the right parser based on
 * the leading filename character: {@code B*} → batch acknowledgement, {@code
 * E*} → claims error report, {@code R*} → batch eligibility / OBEC report.
 * Each branch delegates to the matching parser
 * ({@link BillingClaimBatchAcknowledgementReportParser},
 * {@link BillingClaimsErrorReportImportService},
 * {@link BillingEdtObecOutputSpecificationParser}) and renders the
 * resulting summary HTML. Requires {@code _billing w}.
 */
public class BillingDocumentErrorReportUpload2Action extends ActionSupport implements UploadedFilesAware {
    private final SecurityInfoManager securityInfoManager;
    private final BatchEligibilityDao batchEligibilityDao;
    private final DemographicCustDao demographicCustDao;
    private final DemographicManager demographicManager;
    private final ProviderDao providerDao;
    private final BillingOnErrorReportService errorReportService;

    public BillingDocumentErrorReportUpload2Action(SecurityInfoManager securityInfoManager,
                                                   DemographicManager demographicManager,
                                                   BatchEligibilityDao batchEligibilityDao,
                                                   DemographicCustDao demographicCustDao,
                                                   ProviderDao providerDao,
                                                   BillingOnErrorReportService errorReportService) {
        this.securityInfoManager = securityInfoManager;
        this.demographicManager = demographicManager;
        this.batchEligibilityDao = batchEligibilityDao;
        this.demographicCustDao = demographicCustDao;
        this.providerDao = providerDao;
        this.errorReportService = errorReportService;
    }
    public String execute() throws ServletException, IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String filename = request.getParameter("filename");

        if (StringUtils.isBlank(filename)) {
            if (file1 == null || StringUtils.isBlank(file1FileName)) {
                addActionError("No report file was uploaded.");
                return ERROR;
            }
            if (!saveFile(file1, file1FileName)) {
                addActionError("Report file could not be added.");
                return ERROR;
            } else {
                if (getData(loggedInInfo, file1FileName, "DOCUMENT_DIR", request)) {
                    // Use FilenameUtils to safely extract just the filename for report type check
                    String baseName = org.apache.commons.io.FilenameUtils.getName(file1FileName);
                    return (baseName != null && baseName.startsWith("L")) ? "outside" : SUCCESS;
                }
                else {
                    addActionError(getText("errors.incorrectFileFormat"));
                    return ERROR;
                }
            }
        } else {
            if (getData(loggedInInfo, filename, "ONEDT_INBOX", request)) {
                String baseName = org.apache.commons.io.FilenameUtils.getName(filename);
                return (baseName != null && baseName.startsWith("L")) ? "outside" : SUCCESS;
            } else if (getData(loggedInInfo, filename, "ONEDT_ARCHIVE", request)) {
                String baseName = org.apache.commons.io.FilenameUtils.getName(filename);
                return (baseName != null && baseName.startsWith("L")) ? "outside" : SUCCESS;
            } else {
                addActionError(getText("errors.incorrectFileFormat"));
                return ERROR;
            }
        }
    }

    /**
     * Save the uploaded report file under {@code DOCUMENT_DIR}.
     *
     * @param file the uploaded file (validated upstream via PathValidationUtils)
     * @param fileName the original filename used as the destination basename
     * @return {@code true} on successful copy, {@code false} on configuration
     *         or IO failure (the failure is logged on the way out)
     */
    public static boolean saveFile(File file, String fileName) {
        boolean isAdded = true;

        try {
            CarlosProperties props = CarlosProperties.getInstance();

            // Get target directory
            String place = props.getProperty("DOCUMENT_DIR");
            if (place == null || place.trim().isEmpty()) {
                MiscUtils.getLogger().error("DOCUMENT_DIR property is not configured");
                return false;
            }

            // Use PathValidationUtils to validate and get safe destination path
            File placeDir = new File(place).getCanonicalFile();
            if (!placeDir.exists() || !placeDir.isDirectory()) {
                MiscUtils.getLogger().error("DOCUMENT_DIR does not exist or is not a directory: {}", LogSanitizer.sanitize(place));
                return false;
            }

            File destFile;
            try {
                destFile = PathValidationUtils.validatePath(fileName, placeDir);
            } catch (SecurityException e) {
                MiscUtils.getLogger().error("Invalid filename provided: {}", LogSanitizer.sanitize(fileName));
                return false;
            }

            MiscUtils.getLogger().debug(destFile.getPath());

            try (InputStream stream = new FileInputStream(file);
                 OutputStream bos = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[2048];
                int bytesRead;
                while ((bytesRead = stream.read(buffer)) != -1) {
                    bos.write(buffer, 0, bytesRead);
                }
            }

            String inboxDir = props.getProperty("ONEDT_INBOX");
            if (inboxDir == null || inboxDir.trim().isEmpty()) {
                MiscUtils.getLogger().error("ONEDT_INBOX property is not configured");
                return false;
            }

            // Use PathValidationUtils to validate and get safe inbox path
            File inboxDirFile = new File(inboxDir).getCanonicalFile();
            if (!inboxDirFile.exists() || !inboxDirFile.isDirectory()) {
                MiscUtils.getLogger().error("ONEDT_INBOX does not exist or is not a directory: {}", LogSanitizer.sanitize(String.valueOf(inboxDir)));
                return false;
            }

            File inboxFile;
            try {
                inboxFile = PathValidationUtils.validatePath(destFile.getName(), inboxDirFile);
            } catch (SecurityException e) {
                MiscUtils.getLogger().error("Invalid filename for inbox: {}", LogSanitizer.sanitize(destFile == null ? "null" : destFile.getName()));
                return false;
            }

            org.apache.commons.io.FileUtils.copyFile(destFile, inboxFile);
        } catch (FileNotFoundException e) {
            MiscUtils.getLogger().error("File not found", e);
            return isAdded = false;

        } catch (IOException ioe) {
            MiscUtils.getLogger().error("Error", ioe);
            return isAdded = false;
        }

        return isAdded;
    }


    /**
     * Read the uploaded MOH report file, dispatch to the right parser by
     * filename prefix, and stash the parser result on the request for the
     * view JSP.
     *
     * @param loggedInInfo logged-in user (passed through to {@code generateReportR})
     * @param fileName the validated source filename inside {@code pathDir}
     * @param pathDir the {@code carlos.properties} key holding the base directory
     * @param request the in-flight request used to expose parser results to the view
     * @return {@code true} when the parser reports a clean read; {@code false}
     *         on missing file, validation failure, or partial-read mid-parse
     * @throws ServletException unused (kept for back-compat with caller signature)
     * @throws IOException unused (kept for back-compat with caller signature)
     */
    private boolean getData(LoggedInInfo loggedInInfo, String fileName, String pathDir, HttpServletRequest request)
            throws ServletException, IOException {
        boolean isGot = false;

        try {
            CarlosProperties props = CarlosProperties.getInstance();
            String filepath = props.getProperty(pathDir);
            boolean bNewBilling = "true".equals(props.getProperty("isNewONbilling", ""));

            if (filepath == null || filepath.isBlank()) {
                throw new IllegalStateException("Missing or empty path: " + pathDir);
            }

            // Use PathValidationUtils to validate and get safe file path
            File safeDir = new File(filepath).getCanonicalFile();
            File inputFile;
            try {
                inputFile = PathValidationUtils.validatePath(fileName, safeDir);
            } catch (SecurityException e) {
                MiscUtils.getLogger().error("Invalid filename provided: " + fileName);
                return false;
            }

            // Get the sanitized filename from the validated path
            String sanitizedFileName = inputFile.getName();
            if (sanitizedFileName.isEmpty()) {
                MiscUtils.getLogger().warn("Upload filename empty after sanitization; rejecting");
                return false;
            }

            String prefix = sanitizedFileName.substring(0, 1);
            ArrayList<String> messages = new ArrayList<String>();
            String ReportName = "";

            // try-with-resources to close the FileInputStream on every exit path.
            try (FileInputStream file = new FileInputStream(inputFile)) {
                MiscUtils.getLogger().debug("File path: {}",
                        LogSanitizer.sanitize(inputFile.getAbsolutePath()));

                if (prefix.compareTo("E") == 0 || prefix.compareTo("F") == 0) {
                    ReportName = "Claims Error Report";
                    BillingClaimsErrorReportParser hd = generateReportE(file, bNewBilling, sanitizedFileName);
                    request.setAttribute("claimsErrors", hd);
                    isGot = hd.isVerdict();
                } else if (prefix.compareTo("B") == 0) {
                    ReportName = "Claim Batch Acknowledgement Report";
                    BillingClaimBatchAcknowledgementReportParser hd = generateReportB(file);
                    request.setAttribute("batchAcks", hd);
                    isGot = hd.verdict;
                } else if (prefix.compareTo("X") == 0) {
                    ReportName = "Claim File Rejection Report";
                    messages = generateReportX(file);
                    request.setAttribute("messages", messages);
                    isGot = reportXIsGenerated;
                } else if (prefix.compareTo("R") == 0) {
                    ReportName = "EDT OBEC Output Specification";
                    BillingEdtObecOutputSpecificationParser hd = generateReportR(loggedInInfo, file, request);
                    request.setAttribute("outputSpecs", hd);
                    isGot = hd.verdict;
                } else if (prefix.compareTo("L") == 0) {
                    ReportName = "OUTSIDE USE REPORT";
                    request.setAttribute("backupfilepath", filepath);
                    request.setAttribute("filename", sanitizedFileName);
                    isGot = true;
                } else {
                    // Unrecognized prefix — supported set is E/F/B/X/R/L. The
                    // caller renders errors.incorrectFileFormat; without this
                    // log the operator can't see what prefix actually arrived.
                    MiscUtils.getLogger().warn(
                            "Unrecognized MOH report prefix [{}] for file {} — supported prefixes are E/F/B/X/R/L",
                            LogSanitizer.sanitize(prefix),
                            LogSanitizer.sanitize(sanitizedFileName));
                }
            }

            request.setAttribute("ReportName", ReportName);
        } catch (FileNotFoundException fnfe) {
            MiscUtils.getLogger().error("MOH report upload — file not found at validated path: {}",
                    LogSanitizer.sanitize(fileName), fnfe);
            return false;
        } catch (IOException ioe) {
            MiscUtils.getLogger().error("MOH report upload — IO error reading {}",
                    LogSanitizer.sanitize(fileName), ioe);
            return false;
        }

        return isGot;
    }

    /**
     * Generate the Claims Error Report (E-prefix files).
     *
     * @param file open input stream over the uploaded report
     * @param bB {@code true} when the new ON-billing import path is enabled
     *           (routes through {@link BillingClaimsErrorReportImportService}),
     *           {@code false} for the legacy parse-only path
     * @param filename original filename — used by the import service for audit
     * @return populated parser carrying parsed records and a {@code verdict}
     *         flag indicating whether the read completed cleanly
     */
    private BillingClaimsErrorReportParser generateReportE(FileInputStream file, boolean bB, String filename) {
        BillingClaimsErrorReportParser hd = null;
        if (bB) {
            // Fetch via SpringUtils so the @Transactional proxy is applied —
            // direct construction (`new`) bypasses the proxy and would
            // re-introduce the multi-tx-per-line behaviour where each row
            // committed independently with no atomic boundary.
            try {
                hd = io.github.carlos_emr.carlos.utility.SpringUtils
                        .getBean(BillingClaimsErrorReportImportService.class)
                        .importStream(file, filename);
            } catch (BillingFileImportException e) {
                MiscUtils.getLogger().error("Claims-error report import failed; transaction rolled back", e);
                hd = new BillingClaimsErrorReportParser(file);
                hd.setVerdict(false);
            }
        } else {
            hd = new BillingClaimsErrorReportParser(file);
        }

        return hd;
    }

    /**
     * Generate Claim Batch Acknowledgement Report (B).
     *
     * @param file
     * @return batch acknowledgement report parser
     */
    private BillingClaimBatchAcknowledgementReportParser generateReportB(FileInputStream file) {
        BillingClaimBatchAcknowledgementReportParser hd = new BillingClaimBatchAcknowledgementReportParser(
                file);

        return hd;
    }

    /** Flag indicating whether Report X generation succeeded. */
    private boolean reportXIsGenerated = true;

    /**
     * Generate Claim File Rejection Report (X).
     *
     * @param file the file input stream to process
     * @return list of message strings for the report
     */
    private ArrayList<String> generateReportX(FileInputStream file) {
        ArrayList<String> messages = new ArrayList<String>();
        messages.add("M01 | Message Reason         Length     Msg Type   Filler  Record Image");
        messages.add("M02 | File:    File Name    Date:   Mail Date   Time: Mail Time     Process Date");
        InputStreamReader reader = new InputStreamReader(file);
        BufferedReader input = new BufferedReader(reader);
        String nextline = null;
        try {
            while ((nextline = input.readLine()) != null) {
                String headerCount = nextline.substring(2, 3);

                if (headerCount.compareTo("1") == 0) {
                    String recordLength = nextline.substring(23, 28);
                    String msgType = nextline.substring(28, 31);
                    String filler = nextline.substring(32, 39);
                    String error = nextline.substring(39, 76);
                    String explain = nextline.substring(3, 23);
                    String msg = "M01 | " + explain + "   " + recordLength + "   " + msgType + "   " + filler + "   "
                            + URLEncoder.encode(error, "UTF-8");
                    messages.add(msg);

                }
                if (headerCount.compareTo("2") == 0) {
                    String mailFile = nextline.substring(8, 20);
                    String mailDate = nextline.substring(25, 33);
                    String mailTime = nextline.substring(38, 44);
                    String batchProcessDate = nextline.substring(50, 58);
                    String msg = "M02 | File:   " + mailFile + "    " + "Date:   " + mailDate + "   " + "Time: "
                            + mailTime + "     PDate: " + batchProcessDate;
                    messages.add(msg);
                }
            }

        } catch (IOException ioe) {
            reportXIsGenerated = false;
            MiscUtils.getLogger().error("I/O error parsing MOH X-report claim-rejection file", ioe);
        } catch (StringIndexOutOfBoundsException ioe) {
            // Without the offending line the operator has no way to tell which
            // record in the X-report is malformed. Log the sanitized line so
            // they can pinpoint the failure.
            reportXIsGenerated = false;
            MiscUtils.getLogger().warn(
                    "Malformed MOH X-report row (substring offset out of range): {}",
                    LogSanitizer.sanitize(nextline), ioe);
        }
        return messages;
    }

    /**
     * Generate the EDT OBEC Output Specification report (R-prefix files).
     *
     * @param loggedInInfo logged-in user — recorded as the eligibility-check
     *                     audit subject by the parser
     * @param file open input stream over the uploaded report
     * @return populated parser carrying the spec records and a {@code verdict}
     *         flag indicating whether the read completed cleanly
     */
    private BillingEdtObecOutputSpecificationParser generateReportR(LoggedInInfo loggedInInfo,
                                                                    FileInputStream file,
                                                                    HttpServletRequest request) {
        BillingEdtObecOutputSpecificationParser hd =
                new BillingEdtObecOutputSpecificationParser(loggedInInfo, file,
                        batchEligibilityDao, demographicManager, providerDao);

        // Atomic apply: any per-row failure rolls back every prior
        // ver="##" (HIN-flagged-invalid) mark in this file. Fetch via
        // SpringUtils so the @Transactional proxy applies (direct
        // construction would bypass it).
        try {
            io.github.carlos_emr.carlos.billings.ca.on.service.BillingObecOutputApplyService.ApplyResult applyResult =
                    io.github.carlos_emr.carlos.utility.SpringUtils
                    .getBean(io.github.carlos_emr.carlos.billings.ca.on.service.BillingObecOutputApplyService.class)
                    .applyOutputSpec(loggedInInfo, hd.getEdtObecOutputSpecificationRecords());
            request.setAttribute("obecApplyResult", applyResult);
        } catch (RuntimeException e) {
            MiscUtils.getLogger().error("OBEC output-spec apply rolled back; demographic graph unchanged", e);
            hd.verdict = false;
        }

        return hd;
    }
    private File file1; // Uploaded file
    private String file1FileName; // Name of the uploaded file
    private String file1ContentType; // Content type of the uploaded file

    private String filename; // Filename parameter from request

    @Override
    public void withUploadedFiles(List<UploadedFile> uploadedFiles) {
        if (uploadedFiles != null && !uploadedFiles.isEmpty()) {
            UploadedFile uploaded = uploadedFiles.get(0);
            this.file1 = PathValidationUtils.validateUpload(new File(uploaded.getAbsolutePath()));
            this.file1ContentType = uploaded.getContentType();
            this.file1FileName = uploaded.getOriginalName();
        }
    }

    public File getFile1() {
        return file1;
    }

    @StrutsParameter
    public void setFile1(File file1) {
        this.file1 = file1;
    }

    public String getFile1FileName() {
        return file1FileName;
    }

    @StrutsParameter
    public void setFile1FileName(String file1FileName) {
        this.file1FileName = file1FileName;
    }

    public String getFile1ContentType() {
        return file1ContentType;
    }

    @StrutsParameter
    public void setFile1ContentType(String file1ContentType) {
        this.file1ContentType = file1ContentType;
    }

    public String getFilename() {
        return filename;
    }

    @StrutsParameter
    public void setFilename(String filename) {
        this.filename = filename;
    }
}
