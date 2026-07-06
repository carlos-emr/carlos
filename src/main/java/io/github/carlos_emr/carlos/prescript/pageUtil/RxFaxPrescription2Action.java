/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionFaxService;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionFaxViewModel;
import io.github.carlos_emr.carlos.form.pdfservlet.PrescriptionPdfComposer;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * POST-only Struts boundary for queuing prescription PDF fax jobs.
 */
public class RxFaxPrescription2Action extends ActionSupport {

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
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.WRITE, null)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return NONE;
        }

        response.setContentType("text/html");
        PrintWriter writer = response.getWriter();

        String faxNo = request.getParameter("pharmaFax");
        if (faxNo != null) {
            faxNo = faxNo.trim().replaceAll("\\D", "");
        }

        if (faxNo == null || faxNo.length() < 7) {
            writer.println("<div id='fax-failure'><h3>Error: Valid fax number not found!</h3></div>");
            writer.flush();
            return NONE;
        }

        String demographicNo = request.getParameter("demographic_no");
        if (demographicNo == null || !demographicNo.matches("\\d+")) {
            writer.println("<div id='fax-failure'><h3>Error: Valid demographic number not found!</h3></div>");
            writer.flush();
            return NONE;
        }

        ServletContext servletContext = ServletActionContext.getServletContext();
        if (servletContext == null) {
            servletContext = request.getServletContext();
        }

        try (ByteArrayOutputStream baosPDF = prescriptionPdfComposer.compose(request, servletContext)) {
            PrescriptionFaxViewModel faxViewModel =
                    prescriptionFaxService.createFaxJob(loggedInInfo, request, baosPDF);
            if (faxViewModel.validFaxNumber()) {
                writer.println("<div id='fax-success' style='color:green;'><h3>Fax successfully generated</h3><p>"
                        + SafeEncode.forHtml(faxViewModel.pharmacyName())
                        + " ("
                        + SafeEncode.forHtml(faxViewModel.faxNumber())
                        + ")</p><br><p>This window will close in <b>3</b> seconds...</p></div><script>setTimeout(() => window.top.close(), 3000);</script>");
            } else {
                writer.println("<div id='fax-failure'><h3>Error: No matching clinic fax configuration found!</h3></div>");
            }
        }

        writer.flush();
        return NONE;
    }
}
