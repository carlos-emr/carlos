/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.EFormValueDao;
import io.github.carlos_emr.carlos.commn.model.EFormValue;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.eform.data.EForm;

/**
 * The purpose of this servlet is to allow a local process to convert an eform html page into a pdf file.
 */
public final class EFormViewForPdfGenerationServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // ensure it's a local machine request... no one else should be calling this servlet.
        String remoteAddress = request.getRemoteAddr();
        logger.debug("EformPdfServlet request from : " + remoteAddress);
        if (!"127.0.0.1".equals(remoteAddress)) {
            logger.warn("Unauthorised request made to EFormViewForPdfGenerationServlet from address : " + remoteAddress);
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return; // Critical: stop execution for non-localhost requests
        }

        // Add security headers to prevent browser rendering
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'");
        
        boolean prepareForFax = "true".equals(request.getParameter("prepareForFax"));
        String id = request.getParameter("fdid");
        String providerId = request.getParameter("providerId");
        
        // Validate required parameters
        if (id == null || id.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing required parameter: fdid");
            return;
        }
        
        // Validate id is a valid integer
        int formDataId;
        try {
            formDataId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid parameter: fdid must be a valid number");
            return;
        }
        
        EForm eForm = new EForm(id);
        eForm.setSignatureCode(request.getContextPath(), request.getHeader("User-Agent"), eForm.getDemographicNo(), providerId);
        eForm.setContextPath(request.getContextPath());
        String projectHome = CarlosProperties.getInstance().getProperty("project_home");


        EFormValueDao efvDao = (EFormValueDao) SpringUtils.getBean(EFormValueDao.class);
        List<EFormValue> eFormValues = efvDao.findByFormDataId(formDataId);
        for (EFormValue value : eFormValues) {
            if (value.getVarName().equals("Letter")) {
                String html = value.getVarValue();
                html = html.replace("/imageRenderingServlet", "/EFormSignatureViewForPdfGenerationServlet");
                if (prepareForFax) {
                    html = "<div style=\"position:relative\"><div style=\"position:absolute; margin-top:35px;\">" + html + "</div></div>";
                }
                html = "<html><body style='width:640px;'>" + html + "</body></html>";
                eForm.setFormHtml(html);
            }
            if (value.getVarName().equals("signatureValue")) {

                // Checking to see if there are any parameters for the signature in the html.
                String html = eForm.getFormHtml();
                String signatureInit = "signatureControl.initialize\\s*\\(\\s*\\{\\s*eform:true,\\s+height:(\\d+),\\s+width:(\\d+),\\s+top:(\\d+),\\s+left:(\\d+)\\s*\\}\\s*\\)";
                Pattern pattern = Pattern.compile(signatureInit);
                Matcher matcher = pattern.matcher(html);
                boolean matchFound = matcher.find();
                if (matchFound && matcher.groupCount() == 4) {
                    String sign = value.getVarValue();
                    sign = sign.replace("/imageRenderingServlet", "/EFormSignatureViewForPdfGenerationServlet");
                    String left = matcher.group(4), top = matcher.group(3), width = matcher.group(2), height = matcher.group(1);
                    eForm.setFormHtml(html.replace("<div id=\"signatureDisplay\"></div>", String.format("<div id=\"signatureDisplay\"><img src=\"%s\" style=\"position:absolute;left:%s;top:%s;width:%s;height:%s;\" /> </div>", sign, left, top, width, height)));
                }
            }
        }

        eForm.setFormHtml(eForm.getFormHtml().replace("../eform/displayImage.do", "/" + projectHome + "/EFormImageViewForPdfGenerationServlet"));
        eForm.setFormHtml(eForm.getFormHtml().replace("${oscar_image_path}", "/" + projectHome + "/EFormImageViewForPdfGenerationServlet?imagefile="));
        eForm.setFormHtml(eForm.getFormHtml().replace("$%7Boscar_image_path%7D", "/" + projectHome + "/EFormImageViewForPdfGenerationServlet?imagefile="));
        eForm.setFormHtml(eForm.getFormHtml().replace("<div class=\"DoNotPrint\" style=\"", "<div class=\"DoNotPrint\" style=\"display:none;"));
        eForm.setImagePath(request.getContextPath());
        eForm.setNowDateTime();

        response.setContentType("text/html;charset=UTF-8");
        response.getOutputStream().write(eForm.getFormHtml().getBytes(Charset.forName("UTF-8")));
    }
}
