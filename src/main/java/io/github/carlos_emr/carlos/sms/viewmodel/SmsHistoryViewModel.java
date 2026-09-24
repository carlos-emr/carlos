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
package io.github.carlos_emr.carlos.sms.viewmodel;

import java.util.List;

/**
 * One page of a patient's SMS history, ready for {@code sms/smsHistory.jsp}.
 * <p>
 * Every value is a display string: no JPA entity, no message text and no full phone number reaches
 * the page. Built by {@code SmsHistoryViewModelAssembler}.
 *
 * @param demographicNo        the patient
 * @param patientDisplayName   {@code LAST, FIRST}, or empty when the patient cannot be loaded
 * @param rows                 this page's messages, newest first
 * @param page                 1-based page shown
 * @param pageCount            number of pages, at least 1
 * @param totalCount           number of messages for the patient
 * @param hasPreviousPage      whether a previous page exists
 * @param hasNextPage          whether a next page exists
 * @param canReadMessageBodies whether to offer "Show message" ({@code _msgSMS} read for the patient);
 *                             the read service enforces this again
 * @since 2026-09-24
 */
public record SmsHistoryViewModel(String demographicNo, String patientDisplayName, List<Row> rows, int page,
                                  int pageCount, long totalCount, boolean hasPreviousPage, boolean hasNextPage,
                                  boolean canReadMessageBodies) {

    public SmsHistoryViewModel {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    /**
     * One message as displayed.
     *
     * @param id            the {@code sms_transaction} id, used by the "Show message" form
     * @param createdAt     when the row was created, {@code yyyy-MM-dd HH:mm}
     * @param direction     {@code SmsDirection} name, shown through the {@code sms.direction.*} labels
     * @param purpose       {@code SmsMessagePurpose} name, shown through the {@code sms.purpose.*} labels
     * @param status        {@code SmsStatus} name, shown through the {@code sms.status.*} labels
     * @param phone         last four digits only, for example {@code ***1212}
     * @param consentReason consent reason code when consent blocked the send, otherwise empty
     * @param errorCode     provider or queue error code, otherwise empty
     * @param completedAt   when it was delivered, received or sent, in that order of preference
     * @param bodyStored    whether the text is still stored and can be opened
     */
    public record Row(String id, String createdAt, String direction, String purpose, String status, String phone,
                      String consentReason, String errorCode, String completedAt, boolean bodyStored) {
    }
}
