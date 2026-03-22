/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.ScheduleDate;

/**
 * DAO interface for scheduling operations.
 *
 * @since 2001
 */

public interface ScheduleDateDao extends AbstractDao<ScheduleDate> {
    /**
     * Find By Provider No And Date.
     *
     * @param providerNo String the providerNo
     * @param date Date the date
     * @return ScheduleDate
     */
    ScheduleDate findByProviderNoAndDate(String providerNo, Date date);

    /**
     * Find By Provider Priority And Date Range.
     *
     * @param providerNo String the providerNo
     * @param priority char the priority
     * @param date Date the date
     * @param date2 Date the date2
     * @return List<ScheduleDate>
     */
    List<ScheduleDate> findByProviderPriorityAndDateRange(String providerNo, char priority, Date date, Date date2);

    /**
     * Find By Provider And Date Range.
     *
     * @param providerNo String the providerNo
     * @param date Date the date
     * @param date2 Date the date2
     * @return List<ScheduleDate>
     */
    List<ScheduleDate> findByProviderAndDateRange(String providerNo, Date date, Date date2);

    /**
     * Search_scheduledate_c.
     *
     * @param providerNo String the providerNo
     * @return List<ScheduleDate>
     */
    List<ScheduleDate> search_scheduledate_c(String providerNo);

    /**
     * Search_numgrpscheduledate.
     *
     * @param myGroupNo String the myGroupNo
     * @param sDate Date the sDate
     * @return List<ScheduleDate>
     */
    List<ScheduleDate> search_numgrpscheduledate(String myGroupNo, Date sDate);

    /**
     * Search_appttimecode.
     *
     * @param sDate Date the sDate
     * @param providerNo String the providerNo
     * @return List<Object[]>
     */
    List<Object[]> search_appttimecode(Date sDate, String providerNo);

    /**
     * Search_scheduledate_teamp.
     *
     * @param date Date the date
     * @param date2 Date the date2
     * @param status Character the status
     * @param providerNos List<String> the providerNos
     * @return List<ScheduleDate>
     */
    List<ScheduleDate> search_scheduledate_teamp(Date date, Date date2, Character status, List<String> providerNos);

    /**
     * Search_scheduledate_datep.
     *
     * @param date Date the date
     * @param date2 Date the date2
     * @param status Character the status
     * @return List<ScheduleDate>
     */
    List<ScheduleDate> search_scheduledate_datep(Date date, Date date2, Character status);

    /**
     * Find By Provider Start Date And Priority.
     *
     * @param providerNo String the providerNo
     * @param apptDate Date the apptDate
     * @param priority String the priority
     * @return List<ScheduleDate>
     */
    List<ScheduleDate> findByProviderStartDateAndPriority(String providerNo, Date apptDate, String priority);
}
