/**
 * Copyright (c) 2014-2015. KAI Innovations Inc. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.integration.mcedt.mailbox;

import static io.github.carlos_emr.carlos.integration.mcedt.McedtConstants.REQUEST_ATTR_KEY_RESOURCE_ID;
import static io.github.carlos_emr.carlos.integration.mcedt.McedtConstants.SESSION_KEY_MCEDT_UPDATES;
import static io.github.carlos_emr.carlos.integration.mcedt.McedtConstants.SESSION_KEY_MCEDT_UPLOADS;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.integration.mcedt.McedtConstants;
import io.github.carlos_emr.carlos.integration.mcedt.ResourceForm;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import ca.ontario.health.edt.Detail;
import ca.ontario.health.edt.DetailData;
import ca.ontario.health.edt.EDTDelegate;
import ca.ontario.health.edt.ResponseResult;
import ca.ontario.health.edt.TypeListData;
import ca.ontario.health.edt.TypeListResult;
import ca.ontario.health.edt.UpdateRequest;
import ca.ontario.health.edt.UploadData;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Defines utility methods for action classes.
 */
public class ActionUtils {
    private static Logger logger = MiscUtils.getLogger();
    private static final Object OUTBOX_STAGING_LOCK = new Object();

    static Detail getDetails(HttpServletRequest request) {
        Detail result = (Detail) request.getSession().getAttribute(McedtConstants.SESSION_KEY_RESOURCE_LIST);
        return result;
    }

    static void setDetails(HttpServletRequest request, Detail detail) {
        request.getSession().setAttribute(McedtConstants.SESSION_KEY_RESOURCE_LIST, detail); // nosemgrep: tainted-session-from-http-request -- MCEDT resource list from EDT service response
    }

    static void removeDetails(HttpServletRequest request) {
        request.getSession().removeAttribute(McedtConstants.SESSION_KEY_RESOURCE_LIST);
    }

    static List<DetailDataCustom> getResourceList(HttpServletRequest request) {
        List<DetailDataCustom> result = (List<DetailDataCustom>) request.getSession().getAttribute(McedtConstants.SESSION_KEY_CUSTOM_RESOURCE_LIST);
        return result;
    }

    static void setResourceList(HttpServletRequest request, List<DetailDataCustom> resourceList) {
        request.getSession().setAttribute(McedtConstants.SESSION_KEY_CUSTOM_RESOURCE_LIST, resourceList); // nosemgrep: tainted-session-from-http-request -- MCEDT resource list from EDT service response
    }

    static void removeResourceList(HttpServletRequest request) {
        request.getSession().removeAttribute(McedtConstants.SESSION_KEY_CUSTOM_RESOURCE_LIST);
    }

    static void clearUpdateList(HttpServletRequest request) {
        clear(request.getSession(), SESSION_KEY_MCEDT_UPDATES);
    }

    static List<UpdateRequest> getUpdateList(HttpServletRequest request) {
        return getUpdateList(request.getSession());
    }

    static List<UpdateRequest> getUpdateList(HttpSession session) {
        return getSessionList(session, SESSION_KEY_MCEDT_UPDATES);
    }

    static List<UploadData> getUploadList(HttpServletRequest request) {
        return getUploadList(request.getSession());
    }

    static void clearUploadList(HttpServletRequest request) {
        clear(request.getSession(), SESSION_KEY_MCEDT_UPLOADS);
    }

    static List<UploadData> getUploadList(HttpSession session) {
        return getSessionList(session, SESSION_KEY_MCEDT_UPLOADS);
    }

    private static void clear(HttpSession session, String sessionKey) {
        session.removeAttribute(sessionKey);
    }

    private static <T> List<T> getSessionList(HttpSession session, String sessionKey) {
        @SuppressWarnings("unchecked")
        List<T> result = (List<T>) session.getAttribute(sessionKey);
        if (result == null) {
            result = new ArrayList<T>();
            session.setAttribute(sessionKey, result); // nosemgrep: tainted-session-from-http-request -- initialized empty list, not from user input
        }
        return result;
    }

    static List<BigInteger> getResourceIds(HttpServletRequest request) {
        String[] resourceIds = request.getParameterValues(REQUEST_ATTR_KEY_RESOURCE_ID);

        List<BigInteger> ids = new ArrayList<BigInteger>();
        if (resourceIds == null) {
            return ids;
        }

        for (String i : resourceIds) {
            ids.add(BigInteger.valueOf(ConversionUtils.fromIntString(i)));
        }
        return ids;
    }

    static TypeListResult getTypeList(HttpServletRequest request) {
        return (TypeListResult) request.getSession().getAttribute(McedtConstants.SESSION_KEY_TYPE_LIST);
    }

    static String getServiceId(HttpServletRequest request) {
        return request.getParameter(McedtConstants.REQUEST_ATTR_KEY_SERVICE_ID);
    }

    static void setTypeList(HttpServletRequest request, TypeListResult result) {
        request.getSession().setAttribute(McedtConstants.SESSION_KEY_TYPE_LIST, result); // nosemgrep: tainted-session-from-http-request -- MCEDT type list from EDT service response
    }

    static void removeTypeList(HttpServletRequest request) {
        request.getSession().removeAttribute(McedtConstants.SESSION_KEY_TYPE_LIST);
    }

    /*KAI FUNCTIONS*/

    static BigInteger getUploadResourceId(HttpServletRequest request) {
        return (BigInteger) request.getSession().getAttribute(McedtConstants.SESSION_KEY_UPLOAD_RESOURCE_ID);
    }

    static void setUploadResourceId(HttpServletRequest request, BigInteger result) {
        request.getSession().setAttribute(McedtConstants.SESSION_KEY_UPLOAD_RESOURCE_ID, result); // nosemgrep: tainted-session-from-http-request -- resource ID from EDT service response object
    }

    static void removeUploadResourceId(HttpServletRequest request) {
        request.getSession().removeAttribute(McedtConstants.SESSION_KEY_UPLOAD_RESOURCE_ID);
    }

    static void setUploadedFileName(HttpServletRequest request, String result) {
        request.getSession().setAttribute(McedtConstants.SESSION_KEY_UPLOAD_FILENAME, result); // nosemgrep: tainted-session-from-http-request -- upload detail from validated MCEDT submission
    }

    static void removeUploadFileName(HttpServletRequest request) {
        request.getSession().removeAttribute(McedtConstants.SESSION_KEY_UPLOAD_FILENAME);
    }

    @SuppressWarnings("unchecked")
    static List<File> getSuccessfulUploads(HttpServletRequest request) {
        return (List<File>) request.getSession().getAttribute(McedtConstants.SESSION_SUCCESSFUL_UPLOADS);
    }

    static void setSuccessfulUploads(HttpServletRequest request, File result) {
        List<File> files = ActionUtils.getSuccessfulUploads(request);
        if (files == null) files = new ArrayList<File>();
        files.add(result);
        request.getSession().setAttribute(McedtConstants.SESSION_SUCCESSFUL_UPLOADS, files); // nosemgrep: tainted-session-from-http-request -- upload detail from validated MCEDT submission
    }

    static void removeSuccessfulUploads(HttpServletRequest request) {
        request.getSession().removeAttribute(McedtConstants.SESSION_SUCCESSFUL_UPLOADS);
    }

    @SuppressWarnings("unchecked")
    static List<ResponseResult> getUploadResponseResults(HttpServletRequest request) {
        return (List<ResponseResult>) request.getSession().getAttribute(McedtConstants.SESSION_KEY_UPLOAD_RESPONSE_RESULT);
    }

    static void setUploadResponseResults(HttpServletRequest request, ResponseResult result) {
        List<ResponseResult> results = ActionUtils.getUploadResponseResults(request);
        if (results == null) results = new ArrayList<ResponseResult>();
        results.add(result);
        request.getSession().setAttribute(McedtConstants.SESSION_KEY_UPLOAD_RESPONSE_RESULT, results); // nosemgrep: tainted-session-from-http-request -- upload detail from validated MCEDT submission
    }

    static void removeUploadResponseResults(HttpServletRequest request) {
        request.getSession().removeAttribute(McedtConstants.SESSION_KEY_UPLOAD_RESPONSE_RESULT);
    }

    @SuppressWarnings("unchecked")
    static List<ResponseResult> getSubmitResponseResults(HttpServletRequest request) {
        return (List<ResponseResult>) request.getSession().getAttribute(McedtConstants.SESSION_KEY_SUBMIT_RESPONSE_RESULT);
    }

    static void setSubmitResponseResults(HttpServletRequest request, ResponseResult result) {
        List<ResponseResult> results = ActionUtils.getSubmitResponseResults(request);
        if (results == null) results = new ArrayList<ResponseResult>();
        results.add(result);
        request.getSession().setAttribute(McedtConstants.SESSION_KEY_SUBMIT_RESPONSE_RESULT, results); // nosemgrep: tainted-session-from-http-request -- upload detail from validated MCEDT submission
    }

    static void removeSubmitResponseResults(HttpServletRequest request) {
        request.getSession().removeAttribute(McedtConstants.SESSION_KEY_SUBMIT_RESPONSE_RESULT);
    }

    public static List<File> getUploadList() {
        List<File> edtUploadList = new ArrayList<File>();
        CarlosProperties props = CarlosProperties.getInstance();
        String outboxPath = props.getProperty("ONEDT_OUTBOX", "");
        if (outboxPath == null || outboxPath.trim().isEmpty()) {
            logger.warn("ONEDT_OUTBOX is not configured; returning empty upload list");
            return edtUploadList;
        }
        File outbox = PathValidationUtils.resolveConfiguredDirectory(outboxPath, "ONEDT_OUTBOX");
        FileFilter fileFilter = new FileFilter() {
            public boolean accept(File file) {
                return file.isFile() && !file.isHidden();
            }
        };
        File[] toEdt = outbox.listFiles(fileFilter);
        if (toEdt == null) {
            logger.warn("ONEDT_OUTBOX directory is missing or unreadable; returning empty upload list");
            return edtUploadList;
        }
        Arrays.sort(toEdt, Comparator.comparingLong(File::lastModified));
        for (File file : toEdt) {
            edtUploadList.add(file);
        }
        return edtUploadList;
    }

    public static boolean isOHIPFile(String filename) {
        // A stray name shorter than the H<month> prefix used to throw here and abort staging.
        if (filename == null || filename.length() < 2) {
            return false;
        }
        String suffix = filename.substring(filename.lastIndexOf(".") + 1);
        String prefix = filename.substring(0, 2);
        if (suffix.length() == 3 && suffix.matches("\\d+") && prefix.matches("H[A-L]")) {
            return true;
        }
        return false;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public static boolean isOBECFile(String filename) {
        String suffix = filename.substring(filename.lastIndexOf(".") + 1);
        String prefix = "";
        if (filename.length() < 4) {
            logger.warn("Checked for OBEC file, found one with too short name: " + filename);
        } else {
            prefix = filename.substring(0, 4);
        }
        return suffix.equalsIgnoreCase("txt") && prefix.equals("OBEC");
    }

    /**
     * What opening the Upload tab does: copies the claim files Generate OHIP File wrote to
     * HOME_DIR since the last open into ONEDT_OUTBOX, then records how far that got as the
     * last open. The record never moves past a claim file that could not be copied (a full
     * disk, a permission problem), so the next open retries it; the files copied before it
     * are not copied again, since they may have been sent in the meantime.
     *
     * <p>One open at a time. Two opens that read the same last open both copy the same
     * files, each deleting the other's copy first, and the later record wins: a file one
     * of them failed to copy could then be left out of every later window.</p>
     *
     * <p>Nothing is copied while the record cannot be written: every open would copy the
     * same files again, including ones sent in between.</p>
     *
     * @return {@code true} when every claim file generated since the last open is in the
     *         outbox; {@code false} when one may be missing, so the Upload tab can say so.
     *         The reason is in the server log.
     */
    public static boolean stageGeneratedClaimFiles() {
        synchronized (OUTBOX_STAGING_LOCK) {
            LastOpen lastOpen = readLastOpen();
            if (!canRecordLastOpen()) {
                logger.error("The MCEDT outbox timestamp file cannot be written; generated OHIP claim files"
                        + " are not copied to the outbox until it can be.");
                return false;
            }
            Date endDate = new Date();
            Date stagedTo = moveOhipToOutBox(lastOpen.at(), endDate);
            boolean recorded = recordLastOpen(stagedTo);
            return lastOpen.known() && recorded && stagedTo.equals(endDate);
        }
    }

    /**
     * Copies the claim files in HOME_DIR modified inside the window into ONEDT_OUTBOX, oldest
     * first, stopping at the first one that cannot be copied. The window excludes its start
     * and includes its end, so the next window, which starts where this one is staged to,
     * neither repeats nor skips a file.
     *
     * @return how far the window is staged: {@code endDate} when every claim file in it was
     *         copied, just before the first one that was not, or {@code startDate} when the
     *         folders cannot be read
     */
    public static Date moveOhipToOutBox(Date startDate, Date endDate) {
        File outbox;
        File[] listed;
        try {
            CarlosProperties props = CarlosProperties.getInstance();
            File generatedFiles = PathValidationUtils.resolveConfiguredDirectory(props.getProperty("HOME_DIR", ""), "HOME_DIR");
            outbox = PathValidationUtils.resolveConfiguredDirectory(props.getProperty("ONEDT_OUTBOX", ""), "ONEDT_OUTBOX");
            FileFilter fileFilter = new FileFilter() {
                public boolean accept(File file) {
                    return (file.isFile() && !file.isHidden() && ActionUtils.isOHIPFile(file.getName()));
                }
            };
            listed = generatedFiles.listFiles(fileFilter);
        } catch (Exception e) {
            logger.error("Unable to copy OHIP files to outbox", e);
            return startDate;
        }
        if (listed == null) {
            // Unreadable or missing: nothing is known to be staged, so the window stays open.
            logger.error("HOME_DIR cannot be listed; generated OHIP claim files are not copied to the outbox.");
            return startDate;
        }
        // Each file's time is read once. Read again after a failed copy it is 0 for a file
        // that has gone (a failed generation deletes its own), which reopened the window back
        // to 1970; read again while sorting, a file replaced meanwhile broke the sort.
        List<ClaimFile> claimFiles = new ArrayList<>(listed.length);
        for (File file : listed) {
            claimFiles.add(new ClaimFile(file, file.lastModified()));
        }
        claimFiles.sort(Comparator.comparingLong(ClaimFile::modified));
        for (ClaimFile claimFile : claimFiles) {
            if (claimFile.modified() > startDate.getTime() && claimFile.modified() <= endDate.getTime()) {
                try {
                    copyFileToDirectory(claimFile.file(), outbox, false, true);
                } catch (Exception e) {
                    logger.error("Unable to copy OHIP files to outbox", e);
                    return new Date(claimFile.modified() - 1);
                }
            }
        }
        return endDate;
    }

    /** A listed claim file and its modification time, read once. */
    private record ClaimFile(File file, long modified) { }

    /*
	 * The method ActionUtils.moveObecToOutBox is slow with many files in 'OscarDocument/oscar/document/'.
	 * To optimize, we will move OBEC files during generation rather than during MCEDT mailbox opening.
	 * See ObecData.writeFile() for details on the updated process.
	 */
    public static void moveObecToOutBox(Date startDate, Date endDate) {
        try {
            CarlosProperties props = CarlosProperties.getInstance();
            File generatedFiles = PathValidationUtils.resolveConfiguredDirectory(props.getProperty("DOCUMENT_DIR", ""), "DOCUMENT_DIR");
            File outbox = PathValidationUtils.resolveConfiguredDirectory(props.getProperty("ONEDT_OUTBOX", ""), "ONEDT_OUTBOX");
            FileFilter fileFilter = new FileFilter() {
                public boolean accept(File file) {
                    return (file.isFile() && !file.isHidden() && ActionUtils.isOBECFile(file.getName()));
                }
            };
            File[] toOutbox = generatedFiles.listFiles(fileFilter);
            if (toOutbox != null) {
                Arrays.sort(toOutbox, new Comparator<File>() {
                    public int compare(File f1, File f2) {
                        return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
                    }
                });
                for (File file : toOutbox) {
                    if (new Date(file.lastModified()).after(startDate) && new Date(file.lastModified()).before(endDate))
                        copyFileToDirectory(file, outbox, false, true);
                }
            }

        } catch (Exception e) {
            logger.error("Unable to copy OBEC files to outbox", e);
        }
    }

    public static void createOnEDTOutboxDir() {
        CarlosProperties props = CarlosProperties.getInstance();
        String outboxPath = props.getProperty("ONEDT_OUTBOX", "");
        if (outboxPath == null || outboxPath.trim().isEmpty()) {
            logger.warn("ONEDT_OUTBOX is not configured; skipping outbox directory creation");
            return;
        }
        File dateDir = PathValidationUtils.resolveConfiguredDirectory(outboxPath, "ONEDT_OUTBOX");
        if (!dateDir.exists()) dateDir.mkdirs();
    }

    /**
     * The time of the last Upload-tab open: generated OHIP claim files written to HOME_DIR
     * after it are copied into the outbox on the next open. With no recorded open (a first
     * open, or an empty file) the window starts now, so earlier claim files are never staged
     * all at once.
     *
     * <p>The open is kept as an ISO-8601 instant to the millisecond. It used to be kept to
     * the minute ({@code dd-MM-yyyy HH:mm}), so the next window began at the top of that
     * minute and copied a file written earlier in it, already staged and possibly sent, into
     * the outbox again. A value in that form (from an earlier build, or from OSCAR, whose
     * document tree an import restores as it was) counts as no recorded open: it may be
     * months old, and staging from it would copy every claim file written since.</p>
     */
    public static Date getOutboxTimestamp() {
        return readLastOpen().at();
    }

    /** Records the last Upload-tab open; see {@link #getOutboxTimestamp()} for the format. */
    public static void setOutboxTimestamp(Date endDate) {
        recordLastOpen(endDate);
    }

    /**
     * The last open to stage from. {@code known} is {@code false} when the window starts now
     * because the record could not be used (unreadable, or in the old minute form), so claim
     * files generated since the real last open may be missing from the outbox; a first open
     * with no record yet has nothing to miss.
     */
    private record LastOpen(Date at, boolean known) { }

    private static LastOpen readLastOpen() {
        Date now = new Date();
        try {
            File dateFile = outboxTimestampFile();
            if (!dateFile.exists()) dateFile.createNewFile();
            try (BufferedReader br = new BufferedReader(new FileReader(dateFile))) {
                String temp = br.readLine();
                if (temp == null || temp.isBlank()) {
                    return new LastOpen(now, true);
                }
                return new LastOpen(Date.from(Instant.parse(temp.trim())), true);
            }
        } catch (DateTimeParseException e) {
            logger.warn("The last MCEDT Upload-tab open is not recorded in the current format; claim files"
                    + " generated before this open are not copied to the outbox. Add any still to be sent"
                    + " from the Upload tab.");
        } catch (Exception e) {
            logger.error("Unable to open outbox timestamp file", e);
        }
        return new LastOpen(now, false);
    }

    private static boolean recordLastOpen(Date endDate) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outboxTimestampFile()))) {
            bw.write(endDate.toInstant().toString());
            return true;
        } catch (Exception e) {
            logger.error("Unable to open outbox timestamp file", e);
            return false;
        }
    }

    private static boolean canRecordLastOpen() {
        try {
            Path record = outboxTimestampFile().toPath();
            return Files.isRegularFile(record) && Files.isWritable(record);
        } catch (Exception e) {
            logger.error("Unable to open outbox timestamp file", e);
            return false;
        }
    }

    /**
     * {@code <ONEDT_OUTBOX>/.timestamp}, for both the read and the write. The writer used
     * validateGeneratedSiblingPath, which appends to the directory's own name, so it wrote
     * {@code <parent>/outbox.timestamp} while the reader opened this file: the window was
     * always empty and no generated claim file was ever staged (#4577).
     */
    private static File outboxTimestampFile() {
        File dateDir = PathValidationUtils.resolveConfiguredDirectory(
                CarlosProperties.getInstance().getProperty("ONEDT_OUTBOX", ""), "ONEDT_OUTBOX");
        if (!dateDir.exists()) dateDir.mkdirs();
        return PathValidationUtils.validateGeneratedChildPath(".timestamp", dateDir);
    }


    static TypeListResult getTypeList(HttpServletRequest request, EDTDelegate delegate) {

        TypeListResult result = getTypeList(request);
        if (result == null) {
            try {
                result = delegate.getTypeList();
                setTypeList(request, result);
            } catch (Exception e) {
                logger.error("Unable to load type list", e);

                //saveErrors(request, ActionUtils.addMessage("resourceAction.getTypeList.fault", McedtMessageCreator.exceptionToString(e)));
            }
        }
        return result;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    static String getTypeDescription(ResourceForm form, String typeCode) {

        String typeDesc = "";
        for (TypeListData typeListData : form.getTypeListResult().getData()) {
            if (typeListData.getResourceType().trim().equalsIgnoreCase(typeCode.trim())) {
                typeDesc = typeListData.getDescriptionEn();
                break;
            }
        }
        return typeDesc;
    }

    static boolean filterResourceStatus(DetailData detailData) {
        boolean displayResource = true;

        switch (detailData.getStatus()) {
            case WIP:
                displayResource = false;
                break;
            case DOWNLOADABLE:
                displayResource = false;
                break;
            case DELETED:
                displayResource = false;
                break;
            default:
                displayResource = true;
        }

        return displayResource;
    }

    static boolean filterResourceStatus(DetailDataCustom detailData) {
        boolean displayResource = true;

        switch (detailData.getStatus()) {
            case WIP:
                displayResource = false;
                break;
            case DOWNLOADABLE:
                displayResource = false;
                break;
            case DELETED:
                displayResource = false;
                break;
            default:
                displayResource = true;
        }

        return displayResource;
    }

    static DetailDataCustom mapDetailData(ResourceForm form, DetailDataCustom detailDataK, DetailData detailData) {

        detailDataK.setCreateTimestamp(detailData.getCreateTimestamp());
        detailDataK.setDescription(detailData.getDescription());
        detailDataK.setModifyTimestamp(detailData.getModifyTimestamp());
        detailDataK.setResourceID(detailData.getResourceID());

        detailDataK.setResourceType(getTypeDescription(form, detailData.getResourceType()));

        detailDataK.setResult(detailData.getResult());
        detailDataK.setStatus(detailData.getStatus());
        detailDataK.setServiceId(form.getServiceIdSent());
        return detailDataK;
    }

    public static void moveFileToDirectory(File srcFile, File destDir, boolean createDestDir, boolean override) throws IOException {
        if (override) {
            File checkFile = PathValidationUtils.validateGeneratedChildPath(srcFile.getName(), destDir);
            if (checkFile.exists()) FileUtils.forceDelete(checkFile);
        }
        FileUtils.moveFileToDirectory(srcFile, destDir, createDestDir);
    }

    public static void copyFileToDirectory(File srcFile, File destDir, boolean createDestDir, boolean override) throws IOException {
        if (override) {
            File checkFile = PathValidationUtils.validateGeneratedChildPath(srcFile.getName(), destDir);
            if (checkFile.exists()) FileUtils.forceDelete(checkFile);
        }
        FileUtils.copyFileToDirectory(srcFile, destDir, createDestDir);
    }

    public static String getServiceId(String filename) {
        String serviceId;
        if (isOHIPFile(filename)) {
            serviceId = filename.substring(2, filename.lastIndexOf("."));
            if (serviceId.length() == 4) {
                serviceId = "0" + serviceId; //for group numbers, it has to be 5 digits
                return serviceId;
            }
            if (serviceId.length() == 6) return serviceId;
        }
        CarlosProperties props = CarlosProperties.getInstance();
        serviceId = props.getProperty("mcedt.service.id");
        return serviceId;
    }

    public static List<String> getServiceIds() {
        CarlosProperties props = CarlosProperties.getInstance();
        String id = props.getProperty("mcedt.service.id");
        String ids = props.getProperty("mcedt.service.designated.ids");
        List<String> serviceIds = new ArrayList<String>();
        serviceIds.addAll(Arrays.asList(ids.trim().split(",")));
        if (serviceIds.contains(id)) serviceIds.remove(id);
        if (!serviceIds.contains(id)) serviceIds.add(0, id);
        return serviceIds;
    }

    public static String getDefaultServiceId() {
        CarlosProperties props = CarlosProperties.getInstance();
        String serviceId = props.getProperty("mcedt.service.id");
        return serviceId;
    }
}
