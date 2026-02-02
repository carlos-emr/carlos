package ca.openosp.openo.billing.CA.ON.web;

import com.opensymphony.xwork2.ActionSupport;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.billing.CA.ON.util.EDTFolder;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.PathValidationUtils;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.utility.WebUtils;

/**
 * Struts2 action for managing Ontario Ministry of Health (MOH) billing file archival operations.
 *
 * <p>This action handles the secure movement of MOH billing files from active Electronic Data
 * Transfer (EDT) folders to an archive directory. It is part of the Ontario-specific billing
 * infrastructure for MCEDT (Medical Certificate Electronic Data Transfer) file management.</p>
 *
 * <p>The action provides critical file management functionality for healthcare billing workflows,
 * ensuring that processed MOH billing files are safely archived while maintaining data integrity
 * and security compliance. All file operations include path validation to prevent path traversal
 * attacks and unauthorized file access.</p>
 *
 * <p><strong>Security:</strong> Requires administrative privileges (_admin with write access) to
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
 * @since 2026-01-24
 */
public class MoveMOHFiles2Action extends ActionSupport {
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
     * @return String result code "Success" upon completion of the archival process
     * @throws Exception if an unexpected error occurs during execution
     * @throws SecurityException if the user lacks required administrative privileges (_admin with write access)
     */
    public String execute() throws Exception {
        if(!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        StringBuilder messages = new StringBuilder();
        StringBuilder errors = new StringBuilder();

        boolean isValid = true;
        String folderParam = request.getParameter("folder");
        if (folderParam == null || folderParam.isEmpty()) {
            errors.append("A folder must be selected.<br/>");
            isValid = false;
            // return "Unable to get folderParam";
        }

        String[] fileNames = request.getParameterValues("mohFile");
        if (fileNames == null) {
            errors.append("Please select file(s) to archive.<br/>");
            isValid = false;
            // return "Unable to get file names";
        }

        if (isValid) {
            String folderPath = getFolderPath(folderParam);
            for (String fileName : fileNames) {
                File file = getFile(folderPath, fileName);
                boolean isValidFileLocation = validateFileLocation(file);
                if (!isValidFileLocation) {
                    logger.warn("Invalid file location " + fileName);
                    continue;
                }

                if (file == null) {
                    logger.warn("Unable to get file " + folderPath + File.pathSeparator + fileName);

                    errors.append("Unable to find file " + fileName + ".<br/>");
                    continue;
                }

                if (file.exists()) {
                    boolean isMoved = moveFile(file);
                    if (isMoved) {
                        messages.append("Archived file " + file.getName() + " successfully.<br/>");
                    } else {
                        errors.append("Unable to archive " + file.getName());
                    }
                }
            }
        }
        
        WebUtils.addErrorMessage(request.getSession(), errors.toString());
        WebUtils.addInfoMessage(request.getSession(), messages.toString());

        return "Success";
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
    private boolean validateFileLocation(File file) {
        boolean result = false;
        try {
            for (EDTFolder folder : EDTFolder.values()) {
                File edtFolderFile = new File(folder.getPath());
                try {
                    PathValidationUtils.validateExistingPath(file, edtFolderFile);
                    result = true;
                    break;
                } catch (SecurityException e) {
                    // File not in this folder, try next
                }
            }
        } catch (Exception e) {
            logger.error("Unable to validate file location", e);
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
    private File getFile(String folderPath, String fileName) {
    try {
        fileName = URLDecoder.decode(fileName, "UTF-8");
    } catch (UnsupportedEncodingException e) {
        logger.error("Unable to decode " + fileName, e);
        return null;
    }

    return new File(folderPath, fileName);
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
    private boolean moveFile(File file) {
    File archiveDir = new File(EDTFolder.ARCHIVE.getPath());
    try {
        FileUtils.moveToDirectory(file, archiveDir, true);
    } catch (IOException e) {
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
     * @param folderName String identifier for the EDT folder (e.g., "INBOX", "OUTBOX", "ERROR")
     * @return String representing the absolute filesystem path for the specified EDT folder
     */
    private String getFolderPath(String folderName) {
    EDTFolder folder = EDTFolder.getFolder(folderName);
    return folder.getPath();
    }
}
