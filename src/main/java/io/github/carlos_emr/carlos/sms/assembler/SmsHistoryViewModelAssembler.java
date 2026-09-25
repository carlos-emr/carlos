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
package io.github.carlos_emr.carlos.sms.assembler;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.sms.SmsDirection;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsHistoryViewModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Assembles {@link SmsHistoryViewModel} for the patient SMS history popup ({@code sms/smsHistory.jsp}).
 * <p>
 * Rows are the patient's {@code sms_transaction} records, newest first, 25 per page. Only display-safe
 * values leave this class: no message text, and only the last four digits of the phone number. The
 * message text is reachable solely through {@code ViewSmsHistory2Action}'s audited "show message" path,
 * and {@link SmsHistoryViewModel#canReadMessageBodies()} only decides whether that button is offered;
 * the body-read service checks the privilege again.
 * <p>
 * Callers must have checked {@code _sms} and {@code _demographic} read for the patient.
 *
 * @since 2026-09-24
 */
@Service
public class SmsHistoryViewModelAssembler {
    /** Rows per page. */
    public static final int PAGE_SIZE = 25;

    private static final String MESSAGE_BODY_SECURITY_OBJECT = "_msgSMS";
    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";

    private final SmsTransactionDao smsTransactionDao;
    private final DemographicManager demographicManager;
    private final SecurityInfoManager securityInfoManager;

    public SmsHistoryViewModelAssembler(SmsTransactionDao smsTransactionDao, DemographicManager demographicManager,
                                        SecurityInfoManager securityInfoManager) {
        this.smsTransactionDao = smsTransactionDao;
        this.demographicManager = demographicManager;
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * @param loggedInInfo  the viewer, already authorized for {@code _sms} and {@code _demographic} read
     * @param demographicNo the patient
     * @param requestedPage 1-based page; out-of-range values are clamped to the first or last page
     * @return one page of display-ready rows plus the page position
     */
    public SmsHistoryViewModel assemble(LoggedInInfo loggedInInfo, int demographicNo, int requestedPage) {
        long totalCount = smsTransactionDao.countByDemographicNo(demographicNo);
        int pageCount = (int) Math.max(1, (totalCount + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 1, pageCount);
        List<SmsHistoryViewModel.Row> rows = smsTransactionDao
                .findByDemographicNo(demographicNo, (page - 1) * PAGE_SIZE, PAGE_SIZE)
                .stream()
                .map(SmsHistoryViewModelAssembler::toRow)
                .toList();
        boolean canReadMessageBodies = securityInfoManager.hasPrivilege(
                loggedInInfo, MESSAGE_BODY_SECURITY_OBJECT, SecurityInfoManager.READ, demographicNo);
        return new SmsHistoryViewModel(
                String.valueOf(demographicNo),
                patientDisplayName(loggedInInfo, demographicNo),
                rows,
                page,
                pageCount,
                totalCount,
                page > 1,
                page < pageCount,
                canReadMessageBodies
        );
    }

    private String patientDisplayName(LoggedInInfo loggedInInfo, int demographicNo) {
        Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographicNo);
        if (demographic == null) {
            return "";
        }
        return nullToEmpty(demographic.getLastName()) + ", " + nullToEmpty(demographic.getFirstName());
    }

    private static SmsHistoryViewModel.Row toRow(SmsTransaction transaction) {
        boolean inbound = transaction.getDirection() == SmsDirection.INBOUND;
        return new SmsHistoryViewModel.Row(
                String.valueOf(transaction.getId()),
                format(transaction.getCreatedAt()),
                code(transaction.getDirection()),
                code(transaction.getMessagePurpose()),
                code(transaction.getStatus()),
                lastFourDigits(inbound ? transaction.getFromPhoneNumber() : transaction.getToPhoneNumber()),
                nullToEmpty(transaction.getConsentReasonCode()),
                nullToEmpty(transaction.getErrorCode()),
                format(completedAt(transaction)),
                transaction.hasStoredMessageBody()
        );
    }

    /** The most final event the row has reached: delivered, received, or sent, in that order. */
    private static Date completedAt(SmsTransaction transaction) {
        if (transaction.getDeliveredAt() != null) {
            return transaction.getDeliveredAt();
        }
        if (transaction.getReceivedAt() != null) {
            return transaction.getReceivedAt();
        }
        return transaction.getSentAt();
    }

    /** Shows only the last four digits, so the history never displays a full phone number. */
    private static String lastFourDigits(String phoneNumber) {
        String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
        return digits.length() < 4 ? "" : "***" + digits.substring(digits.length() - 4);
    }

    /** The enum name; the page translates it through {@code sms.status.*}, {@code sms.direction.*} and so on. */
    private static String code(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private static String format(Date date) {
        return date == null ? "" : new SimpleDateFormat(DATE_TIME_PATTERN, Locale.ROOT).format(date);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
