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


/*
 * PreventionReport2Action.java
 *
 * Created on May 30, 2005, 7:52 PM
 */

package io.github.carlos_emr.carlos.prevention.pageUtil;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;


/**
 * Struts2 action behind the Preventions page's Print and Print Immunizations Only buttons.
 *
 * <p>The request names the patient ({@code demographicNo}), the ticked prevention types
 * ({@code printHP}, one value per type name) and whether to leave screenings out
 * ({@code immunizationOnly=true}). Everything printed is read from the chart by
 * {@link PreventionPrintLoader}; chart values posted with the request are ignored. Requires the
 * {@code _prevention} read privilege, and {@code _demographic} read for the patient through the
 * loader.</p>
 *
 * <p>The PDF is drawn into a buffer and checked before any header is set, so a failure can
 * still answer the Preventions page with its "An error occurred while trying to print" banner
 * (the {@code error} result) instead of a download whose body is that page's HTML: 400 when
 * nothing printable was selected, 500 when drawing failed. A malformed or unknown patient
 * number is answered with 400 and no page.</p>
 *
 * @see PreventionPrintLoader
 * @see PreventionPrintPdf
 * @since 2007-03-14
 */
public class PreventionPrint2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();
    private static final Pattern DEMOGRAPHIC_NO = Pattern.compile("[1-9]\\d{0,9}");
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.ISO_8859_1);

    private final transient SecurityInfoManager securityInfoManager;
    private final transient PreventionPrintLoader printLoader;
    private final transient Supplier<PreventionPrintPdf> pdfFactory;

    public PreventionPrint2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class), new PreventionPrintLoader(), PreventionPrintPdf::new);
    }

    PreventionPrint2Action(SecurityInfoManager securityInfoManager, PreventionPrintLoader printLoader,
                           Supplier<PreventionPrintPdf> pdfFactory) {
        this.securityInfoManager = securityInfoManager;
        this.printLoader = printLoader;
        this.pdfFactory = pdfFactory;
    }

    /**
     * Prints the ticked prevention types of the patient as a PDF download.
     *
     * @return {@code NONE} when the PDF, or a 400 for a malformed or unknown patient, was
     *         written; {@code "error"} to show the Preventions page with its print error banner
     * @throws IOException if the response cannot be written
     * @throws SecurityException if the logged-in user lacks {@code _prevention} read privilege
     */
    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_prevention", "r", null)) {
            throw new SecurityException("missing required sec object (_prevention)");
        }

        String demographicNo = request.getParameter("demographicNo");
        if (demographicNo == null || !DEMOGRAPHIC_NO.matcher(demographicNo).matches()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid demographic number");
            return NONE;
        }
        boolean immunizationsOnly = "true".equals(request.getParameter("immunizationOnly"));

        PreventionPrintLoader.Printout printout;
        try {
            printout = printLoader.load(loggedInInfo, demographicNo, request.getParameterValues("printHP"), immunizationsOnly);
        } catch (IllegalArgumentException _) {
            // No patient has that number (or it is not a number).
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Patient not found");
            return NONE;
        }
        if (printout.sections().isEmpty()) {
            return printError(request, response, HttpServletResponse.SC_BAD_REQUEST);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            pdfFactory.get().printPdf(printout, buffer);
        } catch (IOException | RuntimeException generationFailure) {
            // OpenPDF's DocumentException is a RuntimeException.
            // The exception names no patient data; the identifiers stay out of the log.
            logger.error("Unable to generate the prevention print", generationFailure);
            return printError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        byte[] pdf = buffer.toByteArray();
        if (pdf.length < PDF_MAGIC.length || !Arrays.equals(Arrays.copyOf(pdf, PDF_MAGIC.length), PDF_MAGIC)) {
            logger.error("The prevention print produced {} bytes that are not a PDF", pdf.length);
            return printError(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"Prevention.pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
        return NONE;
    }

    /** Shows the Preventions page with its print error banner; nothing has been written yet. */
    private static String printError(HttpServletRequest request, HttpServletResponse response, int status) {
        request.setAttribute("printError", Boolean.TRUE);
        response.setStatus(status);
        return ERROR;
    }

}
