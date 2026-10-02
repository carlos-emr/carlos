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
package io.github.carlos_emr.carlos.consultation.dto;

import java.util.Date;

/**
 * Filter for the Consultations list page ({@code encounter/ViewConsultation}).
 *
 * <p>Carries the legacy team/date/sort/paging inputs of
 * {@code ConsultationRequestDao.getConsults(String, boolean, ...)} plus the two optional
 * filters added for issue #3976, so the list query does not grow another positional overload.
 * Every value is bound as a query parameter; {@code orderby}, {@code desc} and
 * {@code searchDate} are only compared against fixed tokens and never reach the query text.</p>
 *
 * @param team          {@code consultationRequests.sendTo}; null or empty means every team
 * @param showCompleted include status 4 (completed) requests
 * @param startDate     inclusive lower bound on the referral (or appointment) date; may be null
 * @param endDate       inclusive upper bound on the referral (or appointment) date; may be null
 * @param orderby       legacy sort token "1".."9"; null sorts by referral date descending
 * @param desc          "1" for descending order on the chosen sort token
 * @param searchDate    "1" filters the date range on the appointment date, anything else on the referral date
 * @param offset        first row to return; null means 0
 * @param limit         maximum rows; null means the DAO default, always capped at the DAO maximum
 * @param consultantId  {@code professionalSpecialists.specId} the request was sent to; null means any
 * @param mrpProviderNo provider number of the patient's most responsible provider
 *                      ({@code demographic.provider_no}); null or blank means any
 * @since 2026-09-30
 */
public record ConsultationListFilterDto(
        String team,
        boolean showCompleted,
        Date startDate,
        Date endDate,
        String orderby,
        String desc,
        String searchDate,
        Integer offset,
        Integer limit,
        Integer consultantId,
        String mrpProviderNo) {
}
