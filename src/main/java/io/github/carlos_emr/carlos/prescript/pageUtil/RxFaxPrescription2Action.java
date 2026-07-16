/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.logging.log4j.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionFaxService;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionFaxViewModel;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionFaxViewModel.FailureReason;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionPdfComposer;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * POST-only Struts boundary for queuing prescription PDF fax jobs.
 */
public class RxFaxPrescription2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager;
    private final PrescriptionPdfComposer prescriptionPdfComposer;
    private final PrescriptionFaxService prescriptionFaxService;

    public RxFaxPrescription2Action() {
        this(
                SpringUtils.getBean(SecurityInfoManager.class),
                SpringUtils.getBean(PrescriptionPdfComposer.class),
                SpringUtils.getBean(PrescriptionFaxService.class));
    }

    RxFaxPrescription2Action(
            SecurityInfoManager securityInfoManager,
            PrescriptionPdfComposer prescriptionPdfComposer,
            PrescriptionFaxService prescriptionFaxService) {
        this.securityInfoManager = securityInfoManager;
        this.prescriptionPdfComposer = prescriptionPdfComposer;
        this.prescriptionFaxService = prescriptionFaxService;
    }

    /**
     * Queues a fax job for the generated Rx PDF after enforcing method and Rx write gates.
     *
     * @return {@link ActionSupport#NONE} because this action writes the legacy HTML response directly
     * @throws Exception when PDF generation or fax job creation fails after authorization
     */
    @Override
    // FindSecBugs XSS_SERVLET: response writes fixed status HTML and null-safe encodes dynamic values.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "response writes fixed status HTML and null-safe encodes dynamic values")
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return NONE;
        }

        // Resolve the target patient before authorizing so the Rx write check is scoped to that
        // demographic, matching RxWebService/RxManagerImpl. A malformed demographic_no falls back
        // to the general _rx write check and is reported to the user as a business error below.
        String demographicNo = request.getParameter("demographic_no");
        boolean demographicNoValid = demographicNo != null && demographicNo.matches("\\d+");
        String demographicScope = demographicNoValid ? demographicNo : null;

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, demographicScope)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        response.setContentType("text/html");
        PrintWriter writer = response.getWriter();

        String destinationFaxNo = request.getParameter("pharmaFax");
        if (destinationFaxNo != null) {
            destinationFaxNo = destinationFaxNo.trim().replaceAll("\\D", "");
        }

        if (destinationFaxNo == null || destinationFaxNo.length() < 7) {
            writer.println("<div id='fax-failure'><h3>Error: Valid fax number not found!</h3></div>");
            writer.flush();
            return NONE;
        }

        if (!demographicNoValid) {
            writer.println("<div id='fax-failure'><h3>Error: Valid demographic number not found!</h3></div>");
            writer.flush();
            return NONE;
        }

        ServletContext servletContext = ServletActionContext.getServletContext();
        if (servletContext == null) {
            servletContext = request.getServletContext();
        }

        ByteArrayOutputStream generatedPdf;
        try {
            generatedPdf = prescriptionPdfComposer.compose(request, servletContext);
        } catch (IOException | RuntimeException e) {
            logger.warn("Prescription PDF generation failed before Rx fax job creation", e);
            writer.println("<div id='fax-failure'><h3>Error: Unable to generate prescription PDF for fax.</h3></div>");
            writer.flush();
            return NONE;
        }

        try (ByteArrayOutputStream baosPDF = generatedPdf) {
            PrescriptionFaxViewModel faxViewModel =
                    prescriptionFaxService.createFaxJob(loggedInInfo, request, baosPDF);
            if (faxViewModel.validFaxNumber()) {
                writer.println("<div id='fax-success' style='color:green;'><h3>Fax successfully generated</h3><p>"
                        + SafeEncode.forHtmlContent(faxViewModel.pharmacyName())
                        + " ("
                        + SafeEncode.forHtmlContent(faxViewModel.faxNumber())
                        + ")</p><br><p>This window will close in <b>3</b> seconds...</p></div><script>setTimeout(() => window.top.close(), 3000);</script>");
            } else {
                if (faxViewModel.failureReason() == FailureReason.INVALID_CLINIC_FAX) {
                    writer.println("<div id='fax-failure'><h3>Error: Valid clinic fax number not found!</h3></div>");
                } else {
                    writer.println("<div id='fax-failure'><h3>Error: No matching clinic fax configuration found!</h3></div>");
                }
            }
        } catch (IOException | RuntimeException e) {
            logger.warn("Rx fax job creation failed after PDF generation", e);
            writer.println("<div id='fax-failure'><h3>Error: Unable to create prescription fax job.</h3></div>");
        }

        writer.flush();
        return NONE;
    }
}
