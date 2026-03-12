/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.provider.web;

import com.opensymphony.xwork2.ActionSupport;
import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

import org.owasp.encoder.Encode;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;

/**
 * Manages provider signature stamp images for consultations, prescriptions, and eForms.
 *
 * <p>Supports uploading a signature image file or saving a drawn signature from an HTML5 canvas.
 * Signatures are stored as {@code consult_sig_<providerNo>.png} in the eForm image directory,
 * following the naming convention required by eForms.</p>
 *
 * <p>Routes based on the {@code method} request parameter:
 * {@code upload}, {@code saveDrawn}, {@code delete}, {@code check}.</p>
 *
 * @since 2026-03-09
 */
public class ProviderSignatureStamp2Action extends ActionSupport {

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);

    private static final int MAX_DRAWN_SIGNATURE_BYTES = 512 * 1024;
    private static final String SIGNATURE_PREFIX = "consult_sig_";

    public String execute() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_pref", "w", null)) {
            throw new SecurityException("missing required sec object (_pref)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            String method = request.getParameter("method");
            if (!"check".equals(method)) {
                writeJson(response, "{\"success\":false,\"error\":\"POST required\"}");
                return NONE;
            }
        }

        String method = request.getParameter("method");
        if (method == null) {
            writeJson(response, "{\"success\":false,\"error\":\"No method specified\"}");
            return NONE;
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();

        switch (method) {
            case "upload":
                return handleUpload(providerNo);
            case "saveDrawn":
                return handleSaveDrawn(providerNo);
            case "delete":
                return handleDelete(providerNo);
            case "check":
                return handleCheck(providerNo);
            default:
                writeJson(response, "{\"success\":false,\"error\":\"Unknown method\"}");
                return NONE;
        }
    }

    private String handleUpload(String providerNo) {
        if (image == null || imageFileName == null || imageFileName.isEmpty()) {
            writeJson(response, "{\"success\":false,\"error\":\"No file selected\"}");
            return NONE;
        }

        try {
            PathValidationUtils.validateUpload(image);

            String signatureName = SIGNATURE_PREFIX + providerNo + ".png";
            File imageFolder = getImageFolder();

            // Read the uploaded image and re-write as PNG to ensure correct format
            BufferedImage bufferedImage = ImageIO.read(image);
            if (bufferedImage == null) {
                writeJson(response, "{\"success\":false,\"error\":\"Invalid image file\"}");
                return NONE;
            }

            File destinationFile = PathValidationUtils.validatePath(signatureName, imageFolder);
            ImageIO.write(bufferedImage, "png", destinationFile);

            userPropertyDAO.saveProp(providerNo, UserProperty.PROVIDER_CONSULT_SIGNATURE, signatureName);

            writeJson(response, buildSuccessJson(request, signatureName));

        } catch (Exception e) {
            MiscUtils.getLogger().error("Signature stamp upload failed", e);
            writeJson(response, "{\"success\":false,\"error\":\"Upload failed\"}");
        }
        return NONE;
    }

    private String handleSaveDrawn(String providerNo) {
        String signatureData = request.getParameter("signatureData");
        if (signatureData == null || signatureData.isEmpty()) {
            writeJson(response, "{\"success\":false,\"error\":\"No signature data\"}");
            return NONE;
        }

        try {
            // Strip data URL prefix if present
            String base64Data = signatureData;
            if (base64Data.contains(",")) {
                base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
            }

            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            if (imageBytes.length > MAX_DRAWN_SIGNATURE_BYTES) {
                writeJson(response, "{\"success\":false,\"error\":\"Signature data too large\"}");
                return NONE;
            }

            // Validate it's actually an image
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (bufferedImage == null) {
                writeJson(response, "{\"success\":false,\"error\":\"Invalid image data\"}");
                return NONE;
            }

            String signatureName = SIGNATURE_PREFIX + providerNo + ".png";
            File imageFolder = getImageFolder();
            File destinationFile = PathValidationUtils.validatePath(signatureName, imageFolder);

            // Write as PNG
            ImageIO.write(bufferedImage, "png", destinationFile);

            userPropertyDAO.saveProp(providerNo, UserProperty.PROVIDER_CONSULT_SIGNATURE, signatureName);

            writeJson(response, buildSuccessJson(request, signatureName));

        } catch (IllegalArgumentException e) {
            MiscUtils.getLogger().warn("Invalid base64 signature data from provider {}", providerNo);
            writeJson(response, "{\"success\":false,\"error\":\"Invalid signature data\"}");
        } catch (Exception e) {
            MiscUtils.getLogger().error("Signature stamp save failed", e);
            writeJson(response, "{\"success\":false,\"error\":\"Save failed\"}");
        }
        return NONE;
    }

    private String handleDelete(String providerNo) {
        try {
            UserProperty prop = userPropertyDAO.getProp(providerNo, UserProperty.PROVIDER_CONSULT_SIGNATURE);
            if (prop != null) {
                // Delete the file
                String fileName = prop.getValue();
                if (fileName != null && !fileName.isEmpty()) {
                    File imageFolder = getImageFolder();
                    File sigFile = new File(imageFolder, fileName);
                    try {
                        PathValidationUtils.validateExistingPath(sigFile, imageFolder);
                        sigFile.delete();
                    } catch (SecurityException e) {
                        MiscUtils.getLogger().warn("Suspicious signature path for provider {}: {}", providerNo, e.getMessage());
                    }
                }
                userPropertyDAO.delete(prop);
            }
            writeJson(response, "{\"success\":true}");
        } catch (Exception e) {
            MiscUtils.getLogger().error("Signature stamp delete failed", e);
            writeJson(response, "{\"success\":false,\"error\":\"Delete failed\"}");
        }
        return NONE;
    }

    private String handleCheck(String providerNo) {
        try {
            UserProperty prop = userPropertyDAO.getProp(providerNo, UserProperty.PROVIDER_CONSULT_SIGNATURE);
            boolean exists = false;
            String imageUrl = "";

            if (prop != null && prop.getValue() != null && !prop.getValue().trim().isEmpty()) {
                File imageFolder = getImageFolder();
                File sigFile = new File(imageFolder, prop.getValue());
                try {
                    PathValidationUtils.validateExistingPath(sigFile, imageFolder);
                    if (sigFile.exists()) {
                        exists = true;
                        imageUrl = request.getContextPath() + "/eform/displayImage.do?imagefile="
                                + Encode.forUriComponent(prop.getValue());
                    }
                } catch (SecurityException ignored) {
                    // File outside allowed directory
                }
            }

            writeJson(response, "{\"exists\":" + exists + ",\"imageUrl\":\""
                    + Encode.forJavaScript(imageUrl) + "\"}");
        } catch (Exception e) {
            MiscUtils.getLogger().error("Signature stamp check failed", e);
            writeJson(response, "{\"exists\":false,\"imageUrl\":\"\"}");
        }
        return NONE;
    }

    private static String buildSuccessJson(HttpServletRequest req, String signatureName) {
        String imageUrl = req.getContextPath() + "/eform/displayImage.do?imagefile="
                + Encode.forUriComponent(signatureName);
        return "{\"success\":true,\"imageUrl\":\"" + Encode.forJavaScript(imageUrl) + "\"}";
    }

    private static File getImageFolder() throws IOException {
        File imageFolder = new File(OscarProperties.getInstance().getEformImageDirectory() + "/");
        if (!imageFolder.exists() && !imageFolder.mkdirs()) {
            throw new IOException("Could not create eform image directory: " + imageFolder.getAbsolutePath());
        }
        return imageFolder;
    }

    private void writeJson(HttpServletResponse resp, String json) {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            PrintWriter writer = resp.getWriter();
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            MiscUtils.getLogger().error("Failed to write JSON response", e);
        }
    }

    // Struts2 file upload fields
    private File image;
    private String imageFileName;
    private String imageFileContentType;

    public File getImage() { return image; }

    @StrutsParameter
    public void setImage(File image) { this.image = image; }

    @StrutsParameter
    public void setImageFileName(String imageFileName) { this.imageFileName = imageFileName; }

    @StrutsParameter
    public void setImageFileContentType(String imageFileContentType) { this.imageFileContentType = imageFileContentType; }
}
