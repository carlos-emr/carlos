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

import io.github.carlos_emr.carlos.commn.model.RSchedule;

/**
 * DAO interface for recurring schedule operations.
 *
 * @since 2001
 */

public interface RScheduleDao extends AbstractDao<RSchedule> {
    /**
     * Find By Provider Available And Date.
     *
     * @param providerNo String the providerNo
     * @param available String the available
     * @param sdate Date the sdate
     * @return List<RSchedule>
     */
    List<RSchedule> findByProviderAvailableAndDate(String providerNo, String available, Date sdate);

    /**
     * Search_rschedule_overlaps.
     *
     * @param providerNo String the providerNo
     * @param d1 Date the d1
     * @param d2 Date the d2
     * @param d3 Date the d3
     * @param d4 Date the d4
     * @param d5 Date the d5
     * @param d6 Date the d6
     * @param d7 Date the d7
     * @param d8 Date the d8
     * @param d9 Date the d9
     * @param d10 Date the d10
     * @param d11 Date the d11
     * @param d12 Date the d12
     * @param d13 Date the d13
     * @param d14 Date the d14
     * @return Long
     */
    Long search_rschedule_overlaps(String providerNo, Date d1, Date d2, Date d3, Date d4, Date d5, Date d6, Date d7, Date d8, Date d9, Date d10, Date d11, Date d12, Date d13, Date d14);

    /**
     * Search_rschedule_exists.
     *
     * @param providerNo String the providerNo
     * @param d1 Date the d1
     * @param d2 Date the d2
     * @return Long
     */
    Long search_rschedule_exists(String providerNo, Date d1, Date d2);

    /**
     * Search_rschedule_current.
     *
     * @param providerNo String the providerNo
     * @param available String the available
     * @param sdate Date the sdate
     * @return RSchedule
     */
    RSchedule search_rschedule_current(String providerNo, String available, Date sdate);

    /**
     * Search_rschedule_future.
     *
     * @param providerNo String the providerNo
     * @param available String the available
     * @param sdate Date the sdate
     * @return List<RSchedule>
     */
    List<RSchedule> search_rschedule_future(String providerNo, String available, Date sdate);

    /**
     * Search_rschedule_current1.
     *
     * @param providerNo String the providerNo
     * @param sdate Date the sdate
     * @return RSchedule
     */
    RSchedule search_rschedule_current1(String providerNo, Date sdate);

    /**
     * Search_rschedule_current2.
     *
     * @param providerNo String the providerNo
     * @param sdate Date the sdate
     * @return RSchedule
     */
    RSchedule search_rschedule_current2(String providerNo, Date sdate);

    /**
     * Search_rschedule_future1.
     *
     * @param providerNo String the providerNo
     * @param sdate Date the sdate
     * @return List<RSchedule>
     */
    List<RSchedule> search_rschedule_future1(String providerNo, Date sdate);

    /**
     * Find By Provider No And Dates.
     *
     * @param providerNo String the providerNo
     * @param apptDate Date the apptDate
     * @return List<RSchedule>
     */
    List<RSchedule> findByProviderNoAndDates(String providerNo, Date apptDate);
}
