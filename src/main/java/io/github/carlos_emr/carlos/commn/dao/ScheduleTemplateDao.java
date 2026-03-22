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

import io.github.carlos_emr.carlos.commn.model.ScheduleTemplate;

/**
 * DAO interface for scheduling operations.
 *
 * @since 2001
 */

public interface ScheduleTemplateDao extends AbstractDao<ScheduleTemplate> {
    /**
     * Find By Summary.
     *
     * @param summary String the summary
     * @return List<ScheduleTemplate>
     */
    List<ScheduleTemplate> findBySummary(String summary);

    /**
     * Find Schedules.
     *
     * @param date_from Date the date_from
     * @param date_to Date the date_to
     * @param provider_no String the provider_no
     * @return List<Object[]>
     */
    List<Object[]> findSchedules(Date date_from, Date date_to, String provider_no);

    /**
     * Find Schedules.
     *
     * @param dateFrom Date the dateFrom
     * @param providerIds List<String> the providerIds
     * @return List<Object[]>
     */
    List<Object[]> findSchedules(Date dateFrom, List<String> providerIds);

    /**
     * Find By Provider No And Name.
     *
     * @param providerNo String the providerNo
     * @param name String the name
     * @return List<ScheduleTemplate>
     */
    List<ScheduleTemplate> findByProviderNoAndName(String providerNo, String name);

    /**
     * Find By Provider No.
     *
     * @param providerNo String the providerNo
     * @return List<ScheduleTemplate>
     */
    List<ScheduleTemplate> findByProviderNo(String providerNo);

    /**
     * Find Time Code By Provider No.
     *
     * @param providerNo String the providerNo
     * @param date Date the date
     * @return List<Object>
     */
    List<Object> findTimeCodeByProviderNo(String providerNo, Date date);

    /**
     * Find Time Code By Provider No2.
     *
     * @param providerNo String the providerNo
     * @param date Date the date
     * @return List<Object>
     */
    List<Object> findTimeCodeByProviderNo2(String providerNo, Date date);
}
