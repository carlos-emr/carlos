/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.sms.web;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sms.SmsMessageBodyReadReason;
import io.github.carlos_emr.carlos.sms.assembler.SmsHistoryViewModelAssembler;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.service.SmsMessageBodyReadService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.util.Objects;
import java.util.Optional;

/**
 * Patient SMS history popup ({@code sms/ViewSmsHistory}), opened from the patient master record.
 * <p>
 * A plain request lists the patient's messages (see {@link SmsHistoryViewModelAssembler}); it needs
 * {@code _sms} and {@code _demographic} read for that patient and never includes message text.
 * <p>
 * {@code method=showMessage} opens one message's full text. It is POST-only, because reading the text
 * writes an audit record and a GET would bypass CSRF protection. It takes a reason from
 * {@link SmsMessageBodyReadReason}, refuses a message that belongs to another patient (as 404, so ids
 * cannot be probed), and reads through {@link SmsMessageBodyReadService}, which requires {@code _msgSMS}
 * and commits the audit record before returning the text. Rows whose text was discarded (consent
 * blocked the send) are answered without reading, so no empty read is audited.
 *
 * @since 2026-09-24
 */
public class ViewSmsHistory2Action extends ActionSupport {
    static final String METHOD_SHOW_MESSAGE = "showMessage";
    static final String MESSAGE_RESULT = "message";

    private static final String SMS_SECURITY_OBJECT = "_sms";
    private static final String DEMOGRAPHIC_SECURITY_OBJECT = "_demographic";

    private final SecurityInfoManager securityInfoManager;
    private final SmsHistoryViewModelAssembler assembler;
    private final SmsTransactionDao smsTransactionDao;
    private final SmsMessageBodyReadService bodyReadService;

    public ViewSmsHistory2Action(SecurityInfoManager securityInfoManager, SmsHistoryViewModelAssembler assembler,
                                 SmsTransactionDao smsTransactionDao, SmsMessageBodyReadService bodyReadService) {
        this.securityInfoManager = securityInfoManager;
        this.assembler = assembler;
        this.smsTransactionDao = smsTransactionDao;
        this.bodyReadService = bodyReadService;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        if (METHOD_SHOW_MESSAGE.equals(request.getParameter("method"))) {
            return showMessage(request, response);
        }
        return history(request, response);
    }

    private String history(HttpServletRequest request, HttpServletResponse response) {
        Integer demographicNo = parseDemographicNo(request);
        if (demographicNo == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        LoggedInInfo loggedInInfo = requirePatientAccess(request, demographicNo);
        int page = parsePositiveInt(request.getParameter("page")).orElse(1);
        request.setAttribute("smsHistory", assembler.assemble(loggedInInfo, demographicNo, page));
        return SUCCESS;
    }

    // IMPROPER_UNICODE: case-insensitive comparison of the literal HTTP method name, not user-identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-insensitive comparison of the literal HTTP method name, not user-identity folding")
    private String showMessage(HttpServletRequest request, HttpServletResponse response) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        Integer demographicNo = parseDemographicNo(request);
        if (demographicNo == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        LoggedInInfo loggedInInfo = requirePatientAccess(request, demographicNo);
        Optional<SmsMessageBodyReadReason> reason =
                SmsMessageBodyReadReason.fromParameter(request.getParameter("reason"));
        Optional<Long> transactionId = parsePositiveLong(request.getParameter("smsTransactionId"));
        if (reason.isEmpty() || transactionId.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }
        SmsTransaction transaction = smsTransactionDao.find(transactionId.get());
        if (transaction == null || !Objects.equals(transaction.getDemographicNo(), demographicNo)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        request.setAttribute("smsHistoryDemographicNo", String.valueOf(demographicNo));
        request.setAttribute("smsMessageReason", reason.get().name());
        if (!transaction.hasStoredMessageBody()) {
            request.setAttribute("smsMessageNotStored", Boolean.TRUE);
            return MESSAGE_RESULT;
        }
        try {
            Optional<String> body = bodyReadService.readFullMessageBody(transaction, loggedInInfo, reason.get().name());
            request.setAttribute("smsMessageBody", body.orElse(""));
        } catch (AccessDeniedException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute("smsMessageDenied", Boolean.TRUE);
        }
        return MESSAGE_RESULT;
    }

    private LoggedInInfo requirePatientAccess(HttpServletRequest request, int demographicNo) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing required session");
        }
        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, SMS_SECURITY_OBJECT, SecurityInfoManager.READ, demographicNo)) {
            throw new SecurityException("missing required sec object (" + SMS_SECURITY_OBJECT + ")");
        }
        if (!securityInfoManager.hasPrivilege(
                loggedInInfo, DEMOGRAPHIC_SECURITY_OBJECT, SecurityInfoManager.READ, demographicNo)) {
            throw new SecurityException("missing required sec object (" + DEMOGRAPHIC_SECURITY_OBJECT + ")");
        }
        return loggedInInfo;
    }

    private static Integer parseDemographicNo(HttpServletRequest request) {
        return parsePositiveInt(request.getParameter("demographic_no")).orElse(null);
    }

    private static Optional<Long> parsePositiveLong(String value) {
        if (value == null || !value.matches("\\d{1,18}")) {
            return Optional.empty();
        }
        long parsed = Long.parseLong(value);
        return parsed > 0 ? Optional.of(parsed) : Optional.empty();
    }

    private static Optional<Integer> parsePositiveInt(String value) {
        if (value == null || !value.matches("\\d{1,9}")) {
            return Optional.empty();
        }
        int parsed = Integer.parseInt(value);
        return parsed > 0 ? Optional.of(parsed) : Optional.empty();
    }
}
