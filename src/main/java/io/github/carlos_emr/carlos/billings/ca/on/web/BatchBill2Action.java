/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.time.DateUtils;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BatchBillingViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BatchBillingViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BatchBillingRemovalService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BatchBillingSubmissionService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnHeaderCreationService;
import io.github.carlos_emr.carlos.commn.dao.BatchBillingDAO;
import io.github.carlos_emr.carlos.commn.model.BatchBilling;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
/**
 * Manages the saved batch-billing entries: list / select view (default
 * {@code execute}), {@code doBatchBill} which expands the selected batch
 * into one OHIP claim per appointment, and {@code remove} for deletion.
 * All three methods require {@code _billing w}.
 */

public class BatchBill2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private final BillingOnHeaderCreationService headerCreationService;
    private final SecurityInfoManager securityInfoManager;
    private final BatchBillingViewModelAssembler batchBillingAssembler;
    private final BatchBillingSubmissionService batchBillingSubmissionService;
    private final BatchBillingRemovalService batchBillingRemovalService;
    private final BatchBillingDAO batchBillingDAO;

    public BatchBill2Action(BillingOnHeaderCreationService headerCreationService,
                            SecurityInfoManager securityInfoManager,
                            BatchBillingViewModelAssembler batchBillingAssembler,
                            BatchBillingSubmissionService batchBillingSubmissionService,
                            BatchBillingRemovalService batchBillingRemovalService,
                            BatchBillingDAO batchBillingDAO) {
        this.headerCreationService = headerCreationService;
        this.securityInfoManager = securityInfoManager;
        this.batchBillingAssembler = batchBillingAssembler;
        this.batchBillingSubmissionService = batchBillingSubmissionService;
        this.batchBillingRemovalService = batchBillingRemovalService;
        this.batchBillingDAO = batchBillingDAO;
    }


    @Override
    public String execute() throws Exception {
        BatchCommand command = BatchCommand.from(request.getParameter("method"));
        boolean requestWillMutate = command.requiresPost()
                || request.getParameterValues("bill") != null;
        if (requestWillMutate && rejectNonPostMutation()) {
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }
        switch (command) {
            case DO_BATCH_BILL:
                return doBatchBill();
            case REMOVE:
                return remove();
            case ADD:
                return add();
            case DEFAULT:
                break;
            default:
                break;
        }

        String[] billingInfo = request.getParameterValues("bill");
        if (billingInfo != null) {
            return doBatchBill();
        }
        // Assemble the JSP view model so batchBilling.jsp can render purely
        // via EL/JSTL. The JSP scriptlet body previously called four
        // SpringUtils.getBean lookups inline plus per-row provider /
        // demographic resolution; that all moves into the assembler.
        BatchBillingViewModel batchModel = batchBillingAssembler.assemble(request);
        request.setAttribute("batchModel", batchModel);

        // Returning null leaves Struts with no result to render -> empty body.
        // Render the batch-billing form view. Saves return through the same path.
        return SUCCESS;

    }

    private enum BatchCommand {
        DEFAULT("", false),
        DO_BATCH_BILL("doBatchBill", true),
        REMOVE("remove", true),
        ADD("add", true);

        private final String method;
        private final boolean requiresPost;

        BatchCommand(String method, boolean requiresPost) {
            this.method = method;
            this.requiresPost = requiresPost;
        }

        private boolean requiresPost() {
            return requiresPost;
        }

        private static BatchCommand from(String method) {
            for (BatchCommand command : values()) {
                if (command.method.equals(method)) {
                    return command;
                }
            }
            return DEFAULT;
        }
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String doBatchBill() {

        if (rejectNonPostMutation()) {
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        ResourceBundle oscarResource = ResourceBundle.getBundle("oscarResources", request.getLocale());
        Date billingDate;
        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
        String strDate = request.getParameter("BillDate");
        if (strDate == null) {
            billingDate = new Date();
        } else {
            try {
                billingDate = dateFmt.parse(strDate);
            } catch (ParseException e) {
                MiscUtils.getLogger().error("Error", e);
                request.setAttribute("error", oscarResource.getString("billing.batchbilling.badDate"));
                return "error";
            }
        }

        String clinic_view = request.getParameter("clinic_view");
        String curUser = (String) request.getSession().getAttribute("user");
        String[] billingInfo = request.getParameterValues("bill");
        //create the invoice and update batch_billing table
        if (billingInfo != null) {
            // Pre-validate every row's shape and demo-no parse BEFORE any
            // createBill / merge fires. A mid-loop crash on row N otherwise
            // committed bills 0..N-1 and left the operator without a signal
            // which posted.
            for (int idx = 0; idx < billingInfo.length; ++idx) {
                try {
                    parseBatchBillRow(billingInfo[idx]);
                } catch (IllegalArgumentException nfe) {
                    MiscUtils.getLogger().error(
                            "BatchBill doBatchBill: row {} malformed or invalid",
                            idx, nfe);
                    request.setAttribute("error", oscarResource.getString("billing.batchbilling.badRow"));
                    return "error";
                }
            }

            java.util.List<BatchBillingSubmissionService.Row> rows = new java.util.ArrayList<>();
            for (String raw : billingInfo) {
                BatchBillRow row = parseBatchBillRow(raw);
                rows.add(new BatchBillingSubmissionService.Row(
                        row.serviceCode(), row.dxCode(), row.demographicNo(), row.providerNo()));
            }
            try {
                BatchBillingSubmissionService.SubmitResult result =
                        batchBillingSubmissionService.submitAll(rows, clinic_view, billingDate, curUser);
                if (!result.failures().isEmpty()) {
                    MiscUtils.getLogger().warn("BatchBill doBatchBill: {} selected rows failed validation",
                            result.failures().size());
                    request.setAttribute("error", oscarResource.getString("billing.batchbilling.badRow"));
                    request.setAttribute("batchBillingFailures", result.failures());
                    return "error";
                }
            } catch (RuntimeException e) {
                MiscUtils.getLogger().error("BatchBill doBatchBill: batch creation rolled back", e);
                request.setAttribute("error", oscarResource.getString("billing.batchbilling.badRow"));
                return "error";
            }

        }

        try {
            String providersParam = request.getParameter("providers");
            String serviceCodeParam = request.getParameter("service_code");
            response.sendRedirect(request.getContextPath()
                    + "/billing/CA/ON/BatchBill?provider_no="
                    + URLEncoder.encode(providersParam == null ? "" : providersParam, StandardCharsets.UTF_8)
                    + "&service_code="
                    + URLEncoder.encode(serviceCodeParam == null ? "" : serviceCodeParam, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("BatchBill redirect failed after batch billing submit", e);
        }
        return null;
    }

    //Remove demographics from batch billing table
    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    @SuppressFBWarnings(value = "UNVALIDATED_REDIRECT", justification = "redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL")
    public String remove() {


        if (rejectNonPostMutation()) {
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String[] billingInfo = request.getParameterValues("bill");

        // Atomic remove via @Transactional service — a mid-loop failure
        // (stale row, FK constraint, concurrent edit) rolls back every
        // prior remove rather than silently desyncing the queue.
        if (billingInfo != null) {
            java.util.List<BatchBillingRemovalService.Row> rows =
                    new java.util.ArrayList<>();
            for (int idx = 0; idx < billingInfo.length; ++idx) {
                BatchBillRow row = parseBatchBillRow(billingInfo[idx]);
                rows.add(new BatchBillingRemovalService.Row(
                        row.demographicNo(), row.serviceCode()));
            }
            try {
                batchBillingRemovalService.removeAll(rows);
            } catch (BatchBillingRemovalService.RemovalRowMissingException missing) {
                // The typed exception's whole point is its .row() field —
                // without surfacing it the operator gets only an opaque
                // 500 with no row identifier. Render the row id on the
                // action result so the JSP can banner "Row not found:
                // demographicNo=N serviceCode=C".
                MiscUtils.getLogger().error(
                        "BatchBilling remove rolled back; row not found: demographicNo={} serviceCode={}",
                        LogSafe.sanitize(String.valueOf(missing.row().demographicNo())),
                        LogSafe.sanitize(missing.row().serviceCode()),
                        missing);
                addActionError(getText("batchbill.removeRowMissing",
                        new String[] {String.valueOf(missing.row().demographicNo()),
                                missing.row().serviceCode()}));
                request.setAttribute("removeRowMissing", missing.row());
                return ERROR;
            } catch (RuntimeException e) {
                MiscUtils.getLogger().error("BatchBilling remove rolled back; queue unchanged", e);
                throw e;
            }
        }

        try {
            String providersParam = request.getParameter("providers");
            String serviceCodeParam = request.getParameter("service_code");
            response.sendRedirect(request.getContextPath()
                    + "/billing/CA/ON/BatchBill?provider_no="
                    + URLEncoder.encode(providersParam == null ? "" : providersParam, StandardCharsets.UTF_8)
                    + "&service_code="
                    + URLEncoder.encode(serviceCodeParam == null ? "" : serviceCodeParam, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("BatchBill redirect failed after batch billing removal", e);
        }
        return null;

    }

    //Add demographic to batch billing table and allow update of record if already present
    public String add() {

        if (rejectNonPostMutation()) {
            return NONE;
        }
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String demographicNoParam = request.getParameter("demographic_no");
        if (demographicNoParam == null || demographicNoParam.trim().isEmpty()) {
            request.setAttribute("error", "Missing required parameter: demographic_no");
            return "error";
        }
        int demographicNo;
        try {
            demographicNo = Integer.parseInt(demographicNoParam.trim());
        } catch (NumberFormatException e) {
            request.setAttribute("error", "Invalid demographic_no format");
            return "error";
        }
        String providersParam = request.getParameter("providers");
        String billingProviderNo = providersParam != null ? providersParam.trim() : "";
        String creatorParam = request.getParameter("creator");
        String creatorProviderNo = creatorParam != null ? creatorParam.trim() : "";
        // service_code may be absent for bulk batch billing entries; passed through as-is, no downstream validation
        String serviceCodeParam = request.getParameter("xml_other1");
        String service_code = serviceCodeParam != null ? serviceCodeParam.trim() : null;
        String dxcodeRaw = request.getParameter("xml_diagnostic_detail");
        String dxcode = dxcodeRaw != null ? dxcodeRaw : "";
        String createdDate = request.getParameter("createdate");
        if (createdDate == null || createdDate.trim().isEmpty()) {
            request.setAttribute("error", "Missing required parameter: createdate");
            return "error";
        }
        final String createdDateFormat = "yyyy/MM/dd HH:mm:ss";
        Date date;
        try {
            date = DateUtils.parseDate(createdDate, new String[]{createdDateFormat});
        } catch (ParseException e) {
            request.setAttribute("error", "Invalid date format for createdate. Expected format: " + createdDateFormat);
            return "error";
        }
        Timestamp created = new Timestamp(date.getTime());
        int pipePos;

        if ((pipePos = dxcode.indexOf("|")) != -1) {
            dxcode = dxcode.substring(0, pipePos);
        }

        List<BatchBilling> batchBillingList = batchBillingDAO.find(demographicNo, service_code);
        BatchBilling batchBilling;

        if (batchBillingList.isEmpty()) {
            batchBilling = new BatchBilling();

            batchBilling.setDemographicNo(demographicNo);
            batchBilling.setBillingProviderNo(billingProviderNo);
            batchBilling.setServiceCode(service_code);
            batchBilling.setDxcode(dxcode);
            batchBilling.setCreateDate(created);
            batchBilling.setCreator(creatorProviderNo);

            batchBillingDAO.persist(batchBilling);
        } else {
            batchBilling = batchBillingList.get(0);
            batchBilling.setBillingProviderNo(billingProviderNo);
            batchBilling.setDxcode(dxcode);
            batchBilling.setCreateDate(created);
            batchBilling.setCreator(creatorProviderNo);

            batchBillingDAO.merge(batchBilling);
        }


        return "saved";

    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private boolean rejectNonPostMutation() {
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        response.setHeader("Allow", "POST");
        try {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        } catch (IOException e) {
            throw new RuntimeException("BatchBill 405 response failed", e);
        }
        return true;
    }

    private BatchBillRow parseBatchBillRow(String raw) {
        String[] temp = raw == null ? new String[0] : raw.split(";", -1);
        if (temp.length != 4) {
            throw new IllegalArgumentException("expected 4 fields, got " + temp.length);
        }
        try {
            return new BatchBillRow(temp[0], temp[1], Integer.parseInt(temp[2]), temp[3]);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("demographic_no is non-numeric", nfe);
        }
    }

    private record BatchBillRow(String serviceCode, String dxCode, Integer demographicNo, String providerNo) { }
}
