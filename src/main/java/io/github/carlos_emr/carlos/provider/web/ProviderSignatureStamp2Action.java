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

import org.apache.struts2.ActionSupport;
import io.github.carlos_emr.CarlosProperties;
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
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.Iterator;

import org.springframework.dao.DataAccessException;

/**
 * Manages provider signature stamp images for consultations, prescriptions, and eForms.
 *
 * <p>Supports uploading a signature image file or saving a drawn signature from an HTML5 canvas.
 * The stored stamp image is consumed by consultation requests, prescription previews, and eForms
 * (via the {@link UserProperty#CONSULT_SIGNATURE_PREFIX} naming convention). Signatures are stored
 * as {@code consult_sig_<providerNo>.png} in the eForm image directory.</p>
 *
 * <p>Routes based on the {@code method} request parameter:
 * {@code upload}, {@code saveDrawn}, {@code delete}, {@code check}.
 * All methods require HTTP POST except {@code check}, which also accepts GET.</p>
 *
 * @since 2026-03-09
 */
public class ProviderSignatureStamp2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final UserPropertyDAO userPropertyDAO = SpringUtils.getBean(UserPropertyDAO.class);

    private static final int MAX_SIGNATURE_BYTES = 512 * 1024;
    private static final int MAX_SIGNATURE_WIDTH = 1000;
    private static final int MAX_SIGNATURE_HEIGHT = 400;

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeJson(response, "{\"success\":false,\"error\":\"Session expired\"}");
            return NONE;
        }
        String method = request.getParameter("method");
        if (method == null) {
            writeJson(response, "{\"success\":false,\"error\":\"No method specified\"}");
            return NONE;
        }

        // check is read-only and only requires read privilege
        String requiredAccess = "check".equals(method) ? "r" : "w";
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_pref", requiredAccess, null)) {
            throw new SecurityException("missing required sec object (_pref)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod()) && !"check".equals(method)) {
            writeJson(response, "{\"success\":false,\"error\":\"POST required\"}");
            return NONE;
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        if (providerNo == null || !providerNo.matches("[0-9]+")) {
            writeJson(response, "{\"success\":false,\"error\":\"Invalid session\"}");
            return NONE;
        }

        switch (method) {
            case "upload":
                return handleUpload(request, response, providerNo);
            case "saveDrawn":
                return handleSaveDrawn(request, response, providerNo);
            case "delete":
                return handleDelete(response, providerNo);
            case "check":
                return handleCheck(request, response, providerNo);
            default:
                writeJson(response, "{\"success\":false,\"error\":\"Unknown method\"}");
                return NONE;
        }
    }

    private String handleUpload(HttpServletRequest request, HttpServletResponse response, String providerNo) {
        if (image == null || imageFileName == null || imageFileName.isEmpty()) {
            writeJson(response, "{\"success\":false,\"error\":\"No file selected\"}");
            return NONE;
        }

        if (image.length() > MAX_SIGNATURE_BYTES) {
            writeJson(response, "{\"success\":false,\"error\":\"File too large\"}");
            return NONE;
        }

        File validatedImage;
        try {
            validatedImage = PathValidationUtils.validateUpload(image);
        } catch (SecurityException e) {
            MiscUtils.getLogger().warn("Signature stamp upload blocked by path validation for provider {}", providerNo);
            writeJson(response, "{\"success\":false,\"error\":\"Upload rejected\"}");
            return NONE;
        }

        String signatureName = UserProperty.CONSULT_SIGNATURE_PREFIX + providerNo + ".png";
        try {
            File imageFolder = getImageFolder();

            // Read the uploaded image with dimension validation to prevent decompression bombs
            BufferedImage bufferedImage;
            try (InputStream fis = new FileInputStream(validatedImage)) {
                bufferedImage = readValidatedImage(fis);
            }
            if (bufferedImage == null) {
                writeJson(response, "{\"success\":false,\"error\":\"Invalid image file\"}");
                return NONE;
            }

            File destinationFile = PathValidationUtils.validatePath(signatureName, imageFolder);
            ImageIO.write(bufferedImage, "png", destinationFile);

            userPropertyDAO.saveProp(providerNo, UserProperty.PROVIDER_CONSULT_SIGNATURE, signatureName);

            writeJson(response, buildSuccessJson(request));

        } catch (IOException e) {
            MiscUtils.getLogger().error("Signature stamp upload I/O failed for provider {}", providerNo, e);
            writeJson(response, "{\"success\":false,\"error\":\"Upload failed\"}");
        } catch (DataAccessException e) {
            MiscUtils.getLogger().error("Signature stamp upload DB save failed for provider {}", providerNo, e);
            writeJson(response, "{\"success\":false,\"error\":\"Could not save signature preference\"}");
        }
        return NONE;
    }

    private String handleSaveDrawn(HttpServletRequest request, HttpServletResponse response, String providerNo) {
        String signatureData = request.getParameter("signatureData");
        if (signatureData == null || signatureData.isEmpty()) {
            writeJson(response, "{\"success\":false,\"error\":\"No signature data\"}");
            return NONE;
        }

        // Strip data URL prefix if present
        String base64Data = signatureData;
        if (base64Data.contains(",")) {
            base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
        }

        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            MiscUtils.getLogger().warn("Invalid base64 signature data from provider {}", providerNo);
            writeJson(response, "{\"success\":false,\"error\":\"Invalid signature data\"}");
            return NONE;
        }

        if (imageBytes.length > MAX_SIGNATURE_BYTES) {
            writeJson(response, "{\"success\":false,\"error\":\"Signature data too large\"}");
            return NONE;
        }

        // Validate it's actually an image with dimension check to prevent decompression bombs
        BufferedImage bufferedImage;
        try {
            bufferedImage = readValidatedImage(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            MiscUtils.getLogger().warn("Could not read drawn signature image from provider {}", providerNo);
            writeJson(response, "{\"success\":false,\"error\":\"Invalid image data\"}");
            return NONE;
        }
        if (bufferedImage == null) {
            writeJson(response, "{\"success\":false,\"error\":\"Invalid image data\"}");
            return NONE;
        }

        String signatureName = UserProperty.CONSULT_SIGNATURE_PREFIX + providerNo + ".png";
        try {
            File imageFolder = getImageFolder();
            File destinationFile = PathValidationUtils.validatePath(signatureName, imageFolder);
            ImageIO.write(bufferedImage, "png", destinationFile);

            userPropertyDAO.saveProp(providerNo, UserProperty.PROVIDER_CONSULT_SIGNATURE, signatureName);

            writeJson(response, buildSuccessJson(request));

        } catch (IOException e) {
            MiscUtils.getLogger().error("Signature stamp save I/O failed for provider {}", providerNo, e);
            writeJson(response, "{\"success\":false,\"error\":\"Save failed\"}");
        } catch (DataAccessException e) {
            MiscUtils.getLogger().error("Signature stamp save DB failed for provider {}", providerNo, e);
            writeJson(response, "{\"success\":false,\"error\":\"Could not save signature preference\"}");
        }
        return NONE;
    }

    private String handleDelete(HttpServletResponse response, String providerNo) {
        UserProperty prop;
        try {
            prop = userPropertyDAO.getProp(providerNo, UserProperty.PROVIDER_CONSULT_SIGNATURE);
        } catch (DataAccessException e) {
            MiscUtils.getLogger().error("Signature stamp delete: DB lookup failed for provider {}", providerNo, e);
            writeJson(response, "{\"success\":false,\"error\":\"Delete failed\"}");
            return NONE;
        }

        if (prop != null) {
            // Use the expected deterministic filename rather than trusting the stored value
            String expectedName = UserProperty.CONSULT_SIGNATURE_PREFIX + providerNo + ".png";
            try {
                File imageFolder = getImageFolder();
                File sigFile = new File(imageFolder, expectedName);
                sigFile = PathValidationUtils.validateExistingPath(sigFile, imageFolder);
                if (sigFile.exists() && !sigFile.delete()) {
                    MiscUtils.getLogger().warn("Could not delete signature file for provider {}: {}", providerNo, sigFile.getAbsolutePath());
                }
            } catch (SecurityException e) {
                MiscUtils.getLogger().warn("Suspicious signature path for provider {}: {}", providerNo, e.getMessage());
                writeJson(response, "{\"success\":false,\"error\":\"Delete failed\"}");
                return NONE;
            } catch (IOException e) {
                MiscUtils.getLogger().error("Signature stamp delete: I/O error for provider {}", providerNo, e);
                writeJson(response, "{\"success\":false,\"error\":\"Delete failed\"}");
                return NONE;
            }

            try {
                userPropertyDAO.delete(prop);
            } catch (DataAccessException e) {
                MiscUtils.getLogger().error("Signature stamp delete: DB delete failed for provider {} (file may already be removed)", providerNo, e);
                writeJson(response, "{\"success\":false,\"error\":\"Delete failed\"}");
                return NONE;
            }
        }
        writeJson(response, "{\"success\":true}");
        return NONE;
    }

    private String handleCheck(HttpServletRequest request, HttpServletResponse response, String providerNo) {
        UserProperty prop;
        try {
            prop = userPropertyDAO.getProp(providerNo, UserProperty.PROVIDER_CONSULT_SIGNATURE);
        } catch (DataAccessException e) {
            MiscUtils.getLogger().error("Signature stamp check: DB lookup failed for provider {}", providerNo, e);
            writeJson(response, "{\"error\":\"Could not check signature status\",\"exists\":false,\"imageUrl\":\"\"}");
            return NONE;
        }

        boolean exists = false;
        String imageUrl = "";

        if (prop != null && prop.getValue() != null && !prop.getValue().trim().isEmpty()) {
            // Use the expected deterministic filename rather than trusting the stored value
            String expectedName = UserProperty.CONSULT_SIGNATURE_PREFIX + providerNo + ".png";
            try {
                File imageFolder = getImageFolder();
                File sigFile = new File(imageFolder, expectedName);
                sigFile = PathValidationUtils.validateExistingPath(sigFile, imageFolder);
                if (sigFile.exists()) {
                    exists = true;
                    imageUrl = request.getContextPath() + "/provider/providerSignatureImage.do";
                }
            } catch (SecurityException e) {
                MiscUtils.getLogger().warn("Suspicious signature path during check for provider {}: {}", providerNo, e.getMessage());
            } catch (IOException e) {
                MiscUtils.getLogger().error("Signature stamp check: I/O error for provider {}", providerNo, e);
                writeJson(response, "{\"error\":\"Could not check signature status\",\"exists\":false,\"imageUrl\":\"\"}");
                return NONE;
            }
        }

        writeJson(response, "{\"exists\":" + exists + ",\"imageUrl\":\""
                + Encode.forJavaScript(imageUrl) + "\"}");
        return NONE;
    }

    private static String buildSuccessJson(HttpServletRequest req) {
        String imageUrl = req.getContextPath() + "/provider/providerSignatureImage.do";
        return "{\"success\":true,\"imageUrl\":\"" + Encode.forJavaScript(imageUrl) + "\"}";
    }

    private static File getImageFolder() throws IOException {
        File imageFolder = new File(CarlosProperties.getInstance().getEformImageDirectory() + "/");
        if (!imageFolder.exists() && !imageFolder.mkdirs()) {
            throw new IOException("Could not create eform image directory: " + imageFolder.getAbsolutePath());
        }
        return imageFolder;
    }

    /**
     * Reads an image from the given stream, checking pixel dimensions before full decode
     * to guard against decompression bombs (small compressed files that expand to huge bitmaps).
     *
     * @param inputStream the image data stream
     * @return the decoded {@link BufferedImage}, or {@code null} if the stream is not a recognized
     *         image format or the dimensions exceed {@link #MAX_SIGNATURE_WIDTH}/{@link #MAX_SIGNATURE_HEIGHT}
     * @throws IOException if an I/O error occurs reading the stream
     */
    private static BufferedImage readValidatedImage(InputStream inputStream) throws IOException {
        ImageInputStream iis = ImageIO.createImageInputStream(inputStream);
        if (iis == null) {
            return null;
        }
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_SIGNATURE_WIDTH || height > MAX_SIGNATURE_HEIGHT) {
                    MiscUtils.getLogger().warn("Signature image rejected: dimensions {}x{} exceed maximum {}x{}",
                            width, height, MAX_SIGNATURE_WIDTH, MAX_SIGNATURE_HEIGHT);
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } finally {
            iis.close();
        }
    }

    private void writeJson(HttpServletResponse resp, String json) {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        try {
            PrintWriter writer = resp.getWriter();
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            MiscUtils.getLogger().debug("Failed to write JSON response (client may have disconnected)", e);
        }
    }

    // Struts2 file upload fields
    private File image;
    private String imageFileName;
    private String imageFileContentType;

    /** @return File the uploaded signature image temporary file */
    public File getImage() { return image; }

    /** @return String the original filename of the uploaded image */
    public String getImageFileName() { return imageFileName; }

    /** @return String the MIME content type of the uploaded image */
    public String getImageFileContentType() { return imageFileContentType; }

    /** @param image File the uploaded signature image temporary file, injected by Struts2 */
    @StrutsParameter
    public void setImage(File image) { this.image = image; }

    /** @param imageFileName String the original filename, injected by Struts2 */
    @StrutsParameter
    public void setImageFileName(String imageFileName) { this.imageFileName = imageFileName; }

    /** @param imageFileContentType String the MIME content type, injected by Struts2 */
    @StrutsParameter
    public void setImageFileContentType(String imageFileContentType) { this.imageFileContentType = imageFileContentType; }
}
