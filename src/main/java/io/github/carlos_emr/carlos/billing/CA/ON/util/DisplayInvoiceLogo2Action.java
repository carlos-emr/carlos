/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

package io.github.carlos_emr.carlos.billing.CA.ON.util;

import io.github.carlos_emr.carlos.commn.model.Document;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.CarlosProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Serves the clinic's invoice letterhead, the image the Ontario third-party invoice
 * ({@code billingON3rdInv.jsp}) shows in place of the clinic's name, address and phone.
 *
 * <p>The letterhead is the active document of the {@code invoice_head_logo_doctype} type
 * ("invoice letterhead" by default), stored in DOCUMENT_DIR. Requires {@code _billing} read. The
 * invoice only ever shows an image, so only image types are served; no letterhead, a missing file or
 * a file of another type answers 404, and {@link #hasServableLogo()} tells the invoice to show the
 * clinic's details instead.</p>
 *
 * @since 2007-02-19
 */
public class DisplayInvoiceLogo2Action extends ActionSupport {
    /** The letterhead file extensions the invoice can show (any case), and the type each is served as. */
    private static final Map<String, String> IMAGE_TYPES;

    static {
        Map<String, String> types = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        types.put("png", "image/png");
        types.put("jpg", "image/jpeg");
        types.put("jpeg", "image/jpeg");
        types.put("jpe", "image/jpeg");
        types.put("gif", "image/gif");
        types.put("bmp", "image/bmp");
        IMAGE_TYPES = Collections.unmodifiableMap(types);
    }

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private HttpServletRequest request = ServletActionContext.getRequest();
    private HttpServletResponse response = ServletActionContext.getResponse();

    @Override
    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String logoPath = getLogoImgAbsPath();
        File file = logoPath.isEmpty() ? null : trustedFile(logoPath);
        String contentType = file == null ? null : imageContentType(file.getName());
        if (contentType == null || !file.isFile()) {
            // The invoice falls back to the clinic's details when hasServableLogo() is false, so this
            // answer is only seen by a stale page or a direct request.
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        response.setContentType(contentType);
        response.setContentLengthLong(file.length());
        response.setHeader("Content-Disposition", "inline; filename=\"" + headerSafeName(file.getName()) + "\"");
        Files.copy(file.toPath(), response.getOutputStream());
        return NONE;
    }

    /**
     * Whether the invoice can show the letterhead: one exists, its file is present and it is an image
     * type this action serves.
     *
     * @return true when the invoice should show the letterhead instead of the clinic's details
     */
    public static boolean hasServableLogo() {
        String logoPath = getLogoImgAbsPath();
        if (logoPath.isEmpty()) {
            return false;
        }
        File file = trustedFile(logoPath);
        return imageContentType(file.getName()) != null && file.isFile();
    }

    // FindSecBugs PATH_TRAVERSAL_IN: logoPath is the containment-validated path returned by getLogoImgAbsPath()
    // (validated within DOCUMENT_DIR there); resolveTrustedPath only canonicalizes the already-validated value.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "logoPath is already containment-validated within DOCUMENT_DIR by getLogoImgAbsPath(); resolveTrustedPath only canonicalizes a trusted value")
    private static File trustedFile(String logoPath) {
        return PathValidationUtils.resolveTrustedPath(new File(logoPath));
    }

    /** The content type to serve a letterhead file as, or null when the invoice cannot show it. */
    static String imageContentType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? null : IMAGE_TYPES.get(fileName.substring(dot + 1));
    }

    /** The file name as a quoted header value: anything but letters, digits, dot, dash and underscore becomes '_'. */
    private static String headerSafeName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public static String getLogoImgAbsPath() {
        String fileName = "";
        String logoDocType = CarlosProperties.getInstance().getProperty("invoice_head_logo_doctype");
        if (logoDocType == null || logoDocType.trim().isEmpty()) {
            logoDocType = "invoice letterhead";
        }
        logoDocType = logoDocType.trim();
        DocumentDao docDao = (DocumentDao) SpringUtils.getBean(DocumentDao.class);
        if (docDao == null) {
            MiscUtils.getLogger().info("Can't get DocumentDAO bean");
            return fileName;
        }
        List<Document> docList = docDao.findByDoctype(logoDocType);
        if (docList == null || docList.size() < 1) {
            MiscUtils.getLogger().info("Can't get document according to doctype: " + logoDocType);
            return fileName;
        }

        Document doc = docList.get(docList.size() - 1);
        if (doc != null) {
            fileName = doc.getDocfilename();
        }

        String document_dir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");

        File file = null;
        try {
            File directory = PathValidationUtils.resolveConfiguredDirectory(document_dir, "DOCUMENT_DIR");
            if (!directory.exists()) {
                MiscUtils.getLogger().info("Directory:  " + document_dir + " does not exist");
                return "";
            }
            if (document_dir.lastIndexOf(File.separator) != (document_dir.length() - 1)) {
                fileName = document_dir + File.separator + fileName;
            } else {
                fileName = document_dir + fileName;
            }

            file = PathValidationUtils.validateExistingPath(new File(fileName), directory);
            if (!file.exists()) {
                MiscUtils.getLogger().info("File: " + fileName);
                return "";
            }
            // Return the containment-validated path (not the raw fileName) so the caller operates
            // on the value PathValidationUtils actually validated.
            return file.getPath();
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

        return "";
    }
}
