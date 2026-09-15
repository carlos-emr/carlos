/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 *
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import org.apache.struts2.ActionSupport;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.struts2.ServletActionContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billing.CA.ON.util.EDTFolder;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.ViewMohFilesViewModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.FileSortByDate;
import io.github.carlos_emr.carlos.util.zip;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.WebUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts2 action for managing Ontario Ministry of Health (MOH) billing file archival operations.
 *
 * <p>This action handles the secure movement of MOH billing files from active Electronic Data
 * Transfer (EDT) folders to an archive directory. It is part of the Ontario-specific billing
 * infrastructure for MCEDT (Medical Claims Electronic Data Transfer) file management.</p>
 *
 * <p>The action provides critical file management functionality for healthcare billing workflows,
 * ensuring that processed MOH billing files are safely archived while maintaining data integrity
 * and security compliance. All file operations include path validation to prevent path traversal
 * attacks and unauthorized file access.</p>
 *
 * <p><strong>Security:</strong> Requires {@code _admin.billing} write access to
 * execute file archival operations. All file paths are validated using {@link PathValidationUtils}
 * to ensure files are within authorized EDT folder locations.</p>
 *
 * <p><strong>Healthcare Context:</strong> In Ontario's healthcare billing system, MOH files contain
 * sensitive billing data that must be processed and archived according to provincial regulations.
 * This action supports the billing workflow by managing the lifecycle of these files after they
 * have been processed for claims submission.</p>
 *
 * @see EDTFolder
 * @see PathValidationUtils
 * @see SecurityInfoManager
 * @since 2014-03-24 legacy MOH file-management flow; repackaged and
 *        POST-gated in the Ontario billing refactor.
 */
public class MoveMohFiles2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest(); 
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Executes the MOH file archival operation.
     *
     * <p>This method handles the main workflow for archiving selected MOH billing files from their
     * current EDT folder location to the archive directory. The process includes:</p>
     * <ul>
     *   <li>Security validation to ensure the user has administrative privileges</li>
     *   <li>Validation of request parameters (folder and file selection)</li>
     *   <li>Path validation to prevent unauthorized file access</li>
     *   <li>File movement to the archive directory using secure file operations</li>
     *   <li>Status message generation for success and error conditions</li>
     * </ul>
     *
     * <p><strong>Request Parameters:</strong></p>
     * <ul>
     *   <li><code>folder</code> - String identifying the source EDT folder (required)</li>
     *   <li><code>mohFile</code> - String array of filenames to archive (required, multiple values)</li>
     * </ul>
     *
     * <p><strong>Security:</strong> This operation requires the <code>_admin</code> security object
     * with write privileges. All file paths are validated to ensure they reside within authorized
     * EDT folder locations before any file operations are performed.</p>
     *
     * <p><strong>Error Handling:</strong> Individual file failures are logged and reported to the
     * user via session messages, but do not halt processing of remaining files. Success and error
     * messages are accumulated and displayed to the user via {@link WebUtils} session messaging.</p>
     *
     * @return String result code {@link ActionSupport#SUCCESS} ({@code "success"}) on completion of
     *         the archival process. The struts mapping is case-sensitive, so the inherited constant
     *         must be used rather than a literal string.
     * @throws Exception if an unexpected error occurs during execution
     * @throws SecurityException if the user lacks required administrative privileges (_admin.billing with write access)
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String execute() throws Exception {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin.billing", "w", null)) {
            throw new SecurityException("missing required sec object (_admin.billing)");
        }

        // Dual-purpose action: GET = render file listing, POST = mutate MOH
        // files. Only enforce POST when mutation-intent parameters are present:
        // `mohFile` archives selected files and `unzipfile` extracts a zip into
        // the EDT folder. Without this conditional gate, the HttpMethodGuardFilter
        // blocks the legitimate render-on-GET path; with an unconditional gate,
        // the page can't be loaded at all. Mirrors the AddEditServiceCode2Action
        // mutation-intent pattern.
        String[] mutationFiles = request.getParameterValues("mohFile");
        String unzipFile = request.getParameter("unzipfile");
        String[] selectedMutationFiles = mutationFiles == null
                ? null
                : Arrays.stream(mutationFiles)
                        .filter(fileName -> fileName != null && !fileName.isBlank())
                        .toArray(String[]::new);
        boolean hasMohFileMutationIntent = selectedMutationFiles != null && selectedMutationFiles.length > 0;
        boolean hasMutationIntent = hasMohFileMutationIntent
                || (unzipFile != null && !unzipFile.isBlank());
        if (hasMutationIntent && !"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required for MOH file mutation");
            return NONE;
        }

        String folderParam = request.getParameter("folder");
        EDTFolder resolvedFolder = resolveEdtFolder(folderParam);
        boolean shouldValidateSubmission = hasMutationIntent || "POST".equalsIgnoreCase(request.getMethod());
        boolean allowUnzip = false;
        if (shouldValidateSubmission) {
            List<String> messages = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            boolean isValid = true;

            if (folderParam == null || folderParam.isBlank()) {
                errors.add(localizedMessage("billing.moveMohFiles.error.folderRequired"));
                isValid = false;
            } else if (resolvedFolder == null) {
                errors.add(localizedMessage("billing.moveMohFiles.error.invalidFolder"));
                isValid = false;
            }

            // Reuse the array fetched above for the mutation-intent gate so the
            // request parameter is read once instead of twice.
            String[] fileNames = selectedMutationFiles == null ? new String[0] : selectedMutationFiles;
            if ((fileNames.length == 0)
                    && (unzipFile == null || unzipFile.isBlank())) {
                errors.add(localizedMessage("billing.moveMohFiles.error.fileRequired"));
                isValid = false;
            }

            if (isValid && hasMohFileMutationIntent) {
                String folderPath = getFolderPath(resolvedFolder);
                if (folderPath == null || folderPath.isEmpty()) {
                    errors.add(localizedMessage("billing.moveMohFiles.error.invalidFolder"));
                } else {
                    for (String fileName : fileNames) {
                        File file = getFile(folderPath, fileName);
                        if (file == null) {
                            logger.warn("Unable to get file {}{}{}", LogSafe.sanitize(folderPath), File.separator, LogSafe.sanitize(fileName)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe

                            errors.add(localizedMessage("billing.moveMohFiles.error.fileMissing", fileName));
                            continue;
                        }

                        boolean isValidFileLocation = validateFileLocation(file);
                        if (!isValidFileLocation) {
                            logger.warn("Invalid file location {}", LogSafe.sanitize(fileName)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe

                            errors.add(localizedMessage("billing.moveMohFiles.error.invalidFileLocation", fileName));
                            continue;
                        }

                        if (file.exists()) {
                            boolean isMoved = moveFile(file);
                            if (isMoved) {
                                messages.add(localizedMessage("billing.moveMohFiles.info.archived", file.getName()));
                            } else {
                                errors.add(localizedMessage("billing.moveMohFiles.error.archiveFailed", file.getName()));
                            }
                        } else {
                            logger.warn("Selected MOH file disappeared before archive {}", LogSafe.sanitize(fileName)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
                            errors.add(localizedMessage("billing.moveMohFiles.error.fileMissing", fileName));
                        }
                    }
                }
            }
            allowUnzip = isValid && unzipFile != null && !unzipFile.isBlank();

            HttpSession session = request.getSession();
            for (String error : errors) {
                WebUtils.addErrorMessage(session, error);
            }
            for (String message : messages) {
                WebUtils.addInfoMessage(session, message);
            }
        } else if (folderParam != null && !folderParam.isBlank() && resolvedFolder == null) {
            WebUtils.addErrorMessage(request.getSession(), localizedMessage("billing.moveMohFiles.error.invalidFolder"));
        }

        // Build the view model for the rendering JSP. The page is rendered both
        // on direct GET (folder listing) and after the POST that archives files,
        // so the assembler runs unconditionally before we forward.
        request.setAttribute("mohModel", buildViewModel(request, resolvedFolder, allowUnzip));
        request.setAttribute("__roleName", buildRoleName(request));

        return SUCCESS;
    }

    private String localizedMessage(String key, Object... args) {
        Locale locale = request == null || request.getLocale() == null ? Locale.getDefault() : request.getLocale();
        String pattern = escapeMessageFormatApostrophes(LocaleUtils.getMessage(locale, key));
        return new MessageFormat(pattern, locale).format(args);
    }

    private static String escapeMessageFormatApostrophes(String pattern) {
        StringBuilder escaped = new StringBuilder(pattern.length());
        for (int i = 0; i < pattern.length(); i++) {
            char current = pattern.charAt(i);
            if (current == '\'') {
                escaped.append("''");
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') {
                    i++;
                }
            } else {
                escaped.append(current);
            }
        }
        return escaped.toString();
    }

    /** Builds the {@code roleName} string the {@code <security:oscarSec>} tag wants. */
    private String buildRoleName(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) {
            logger.warn("viewMOHFiles: missing session role context for security tag");
            return ",";
        }
        Object userRole = session.getAttribute("userrole"); // nosemgrep: java.servlets.security.tainted-session-from-http-request.tainted-session-from-http-request -- read-only authenticated session role for security tag rendering; not request input and not written to session
        Object userId = session.getAttribute("user"); // nosemgrep: java.servlets.security.tainted-session-from-http-request.tainted-session-from-http-request -- read-only authenticated session provider id for security tag rendering; not request input and not written to session
        if (userRole == null || userId == null) {
            logger.warn("viewMOHFiles: missing session role context for security tag");
            return ",";
        }
        return String.valueOf(userRole) + "," + String.valueOf(userId);
    }

    /**
     * Defensive fallback used by the view JSP when {@code mohModel} is missing
     * (i.e., the JSP was reached without going through this action). The
     * caller is responsible for the privilege check before invoking.
     *
     * @param req live servlet request — folder param read from this request
     * @return view model (never null)
     */
    public ViewMohFilesViewModel assembleViewModelForFallback(HttpServletRequest req) {
        String folderParam = req.getParameter("folder");
        return buildViewModel(req, resolveEdtFolder(folderParam), false);
    }

    /**
     * Assembles the {@link ViewMohFilesViewModel} the view JSP renders.
     * This replicates the file-listing logic the JSP scriptlet performed
     * (folder resolution, optional unzip, file enumeration, date sorting,
     * URL-encoded link composition) so the JSP body becomes pure EL/JSTL.
     *
     * @param resolvedFolder validated folder from the request, or {@code null} to render the safe default
     * @param allowUnzip whether the request passed mutation validation and may unzip the selected file
     * @return populated view model (never null)
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private ViewMohFilesViewModel buildViewModel(HttpServletRequest req, EDTFolder resolvedFolder, boolean allowUnzip) {
        EDTFolder folder = resolvedFolder == null ? EDTFolder.INBOX : resolvedFolder;
        String folderPath = folder.getPath();

        if (folderPath == null || folderPath.isEmpty()) {
            logger.error("Unable to find the key ONEDT_{} in the properties file. Please check the value of this key or add it if it is missing.", folder.name());
            return ViewMohFilesViewModel.builder()
                    .selectedFolder(folder)
                    .projectHome(CarlosProperties.getInstance().getProperty("project_home", ""))
                    .build();
        }

        // Preserved from the legacy JSP — DocumentUploadServlet reads the
        // current folder path off the session under this key. folderPath is
        // resolved via the EDTFolder enum (closed set of property-driven
        // server-side paths), so it is NOT tainted user input despite being
        // selected by a request parameter.
        req.getSession().setAttribute("backupfilepath", folderPath); // nosemgrep: java.servlets.security.tainted-session-from-http-request.tainted-session-from-http-request, java.servlets.security.tainted-session-from-http-request-deepsemgrep.tainted-session-from-http-request-deepsemgrep, java.lang.security.audit.tainted-session-from-http-request.tainted-session-from-http-request -- folderPath comes from EDTFolder enum lookup, not user-controlled raw input

        // Optional unzip (was inline in the JSP). Errors are swallowed onto
        // unzipMSG so each file row can render the warning suffix; we keep
        // the same per-page semantics by storing a single message that the
        // assembler attaches to every file entry.
        String unzipMSG = "";
        String zname = req.getParameter("unzipfile");
        try {
            if (allowUnzip && zname != null && !zname.isEmpty()) {
                String safeZipName = FilenameUtils.getName(zname);
                File safeZipFile = PathValidationUtils.validatePath(safeZipName, new File(folderPath));
                Boolean unzipDone = zip.unzipXML(folderPath, safeZipFile.getName());
                if (!unzipDone) {
                    unzipMSG = "(Cannot unzip)";
                } else {
                    WebUtils.addInfoMessage(req.getSession(),
                            localizedMessage("billing.moveMohFiles.info.unzipped", safeZipFile.getName()));
                }
            }
        } catch (SecurityException e) {
            // Distinguish the path-traversal case from the IO-error case so
            // the operator can tell whether their file selection is invalid
            // vs the disk failed mid-unzip.
            logger.warn("viewMOHFiles: path traversal attempt blocked for unzipfile parameter");
            unzipMSG = "(Blocked: invalid file path)";
        } catch (Exception e) {
            logger.error("viewMOHFiles: unzip file Unhandled exception:", e);
            unzipMSG = "(Cannot unzip — see server logs)";
        }

        File f = new File(folderPath);
        File[] contents = f.exists() ? f.listFiles() : new File[]{};
        if (contents == null) {
            contents = new File[]{};
        }
        Arrays.sort(contents, new FileSortByDate());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        List<ViewMohFilesViewModel.FileEntry> entries = new ArrayList<>();
        for (File current : contents) {
            if (current.isDirectory() || current.getName().startsWith(".")) continue;
            if (current.getName().endsWith(".sh")) continue;
            String urlEncoded = URLEncoder.encode(current.getName(), StandardCharsets.UTF_8);
            String date = sdf.format(new Date(current.lastModified()));
            entries.add(new ViewMohFilesViewModel.FileEntry(
                    current.getName(), urlEncoded, date, unzipMSG));
        }

        return ViewMohFilesViewModel.builder()
                .selectedFolder(folder)
                .files(entries)
                .projectHome(CarlosProperties.getInstance().getProperty("project_home", ""))
                .unzipMessage(unzipMSG)
                .build();
    }

    /**
     * Validates that a file resides within an authorized EDT folder location.
     *
     * <p>This security method ensures that the specified file is located within one of the
     * authorized Electronic Data Transfer (EDT) folder paths. It iterates through all defined
     * EDT folders and uses {@link PathValidationUtils#validateExistingPath(File, File)} to
     * verify the file's location against each authorized directory.</p>
     *
     * <p>This validation is critical for preventing path traversal attacks and ensuring that
     * only files within approved MOH billing directories can be archived. The method uses a
     * try-each-folder approach, accepting the file if it validates against any of the authorized
     * EDT folder locations.</p>
     *
     * <p><strong>Security:</strong> Uses {@link PathValidationUtils} to perform secure path
     * validation, preventing directory traversal attacks and unauthorized file access. Any
     * validation failures are caught and the method continues checking other authorized folders.</p>
     *
     * @param file File object representing the file to validate (must not be null)
     * @return boolean true if the file is within an authorized EDT folder, false otherwise
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private boolean validateFileLocation(File file) {
        boolean result = false;
        for (EDTFolder folder : EDTFolder.values()) {
            File edtFolderFile = new File(folder.getPath());
            try {
                file = PathValidationUtils.validateExistingPath(file, edtFolderFile);
                result = true;
                break;
            } catch (SecurityException e) {
                // SecurityException covers two distinct cases — "not in
                // this folder" (expected, try next) and "path-traversal
                // attempt" (attack). Both look the same to the caller,
                // so log at DEBUG with sanitized path. A traversal probe
                // produces N consecutive DEBUG entries (one per folder)
                // and surfaces as a pattern in audit triage; legitimate
                // calls that hit the right folder break out before more
                // than one DEBUG fires.
                logger.debug("EDT folder {} rejected file path during validation: {}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        io.github.carlos_emr.carlos.utility.LogSafe.sanitize(folder.name()),
                        io.github.carlos_emr.carlos.utility.LogSafe.sanitize(
                                file == null ? "null" : file.getPath()));
            }
        }
        return result;
    }

    /**
     * Retrieves a File object for the specified filename within the given folder path.
     *
     * <p>This method creates a File object by combining the folder path and filename. It includes
     * URL decoding of the filename parameter to handle filenames that may have been URL-encoded
     * during HTTP transmission. This is necessary because filenames from web forms may contain
     * special characters that are URL-encoded.</p>
     *
     * <p><strong>Error Handling:</strong> If the filename cannot be decoded using UTF-8 encoding,
     * an error is logged and null is returned. The calling method should check for null and handle
     * the error appropriately.</p>
     *
     * @param folderPath String representing the absolute path to the folder containing the file
     * @param fileName String representing the URL-encoded filename to retrieve
     * @return File object representing the file at the specified path, or null if filename decoding fails
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    private File getFile(String folderPath, String fileName) {
        try {
            fileName = URLDecoder.decode(fileName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.error("Unable to decode {}", LogSafe.sanitize(fileName), e); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
            return null;
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            logger.warn("Rejected decoded file path: {}", LogSafe.sanitize(fileName)); // NOSONAR javasecurity:S5145 - sanitized with LogSafe
            return null;
        }
        String safeFileName = FilenameUtils.getName(fileName);

        // Sanitize + validate-within-allowed-directory in one step rather than
        // constructing the File from raw user input and validating after-the-
        // fact. This is what CodeQL's "uncontrolled data used in path
        // expression" finding asks for: the taint is removed at the point of
        // File construction. validateFileLocation() below remains as a
        // defence-in-depth check.
        try {
            return PathValidationUtils.validatePath(safeFileName, new File(folderPath));
        } catch (SecurityException e) {
            logger.warn("Rejected file path: {} in {}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(fileName), LogSafe.sanitize(folderPath), e);
            return null;
        }
    }

    /**
     * Moves a MOH billing file to the archive directory.
     *
     * <p>This method performs the actual file movement operation using Apache Commons FileUtils
     * to safely move the file to the designated archive directory. The archive directory is
     * automatically created if it does not exist (createDirectory parameter set to true).</p>
     *
     * <p>The method uses {@link FileUtils#moveToDirectory(File, File, boolean)} which provides
     * atomic file movement when possible and handles cross-filesystem moves gracefully. This
     * ensures data integrity during the archival process.</p>
     *
     * <p><strong>Error Handling:</strong> Any IOException during the move operation is caught,
     * logged, and results in a false return value. The original file remains in place if the
     * move fails.</p>
     *
     * @param file File object representing the MOH billing file to move to archive
     * @return boolean true if the file was successfully moved to the archive directory, false if the move failed
     */
    // FindSecBugs PATH_TRAVERSAL_IN: trusted configured archive directory is canonicalized before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "trusted configured archive directory is canonicalized before use")
    private boolean moveFile(File file) {
        try {
            File archiveDir = PathValidationUtils.resolveConfiguredDirectory(EDTFolder.ARCHIVE.getPath(), "ONEDT_ARCHIVE");
            FileUtils.moveToDirectory(file, archiveDir, true);
        } catch (IOException | SecurityException e) {
            logger.error("Unable to move", e);
            return false;
        }
        return true;
    }

    /**
     * Resolves an EDT folder name to its absolute filesystem path.
     *
     * <p>This method uses the {@link EDTFolder} enumeration to map a folder name identifier
     * to its configured absolute path. The EDTFolder enum provides centralized configuration
     * of all authorized Electronic Data Transfer folder locations for Ontario MOH billing files.</p>
     *
     * <p>The folder name parameter typically comes from a web form selection and identifies
     * which EDT folder category (e.g., inbox, outbox, error) the user is working with.</p>
     *
     * @param folder resolved EDT folder selected for the request
     * @return String representing the absolute filesystem path for the specified EDT folder
     */
    private String getFolderPath(EDTFolder folder) {
        if (folder == null) {
            return null;
        }
        return folder.getPath();
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of closed EDT folder enum names; invalid values are rejected before mutation.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of closed EDT folder enum names; invalid values are rejected before mutation")
    private static EDTFolder resolveEdtFolder(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return null;
        }
        String normalizedFolderName = folderName.trim();
        for (EDTFolder folder : EDTFolder.values()) {
            if (folder.name().equalsIgnoreCase(normalizedFolderName)) {
                return folder;
            }
        }
        return null;
    }
}
