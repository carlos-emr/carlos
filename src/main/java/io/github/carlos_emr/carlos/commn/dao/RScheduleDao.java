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

public interface RScheduleDao extends AbstractDao<RSchedule> {
    List<RSchedule> findByProviderAvailableAndDate(String providerNo, String available, Date sdate);

    Long search_rschedule_overlaps(String providerNo, Date d1, Date d2, Date d3, Date d4, Date d5, Date d6, Date d7, Date d8, Date d9, Date d10, Date d11, Date d12, Date d13, Date d14);

    Long search_rschedule_exists(String providerNo, Date d1, Date d2);

    RSchedule search_rschedule_current(String providerNo, String available, Date sdate);

    List<RSchedule> search_rschedule_future(String providerNo, String available, Date sdate);

    RSchedule search_rschedule_current1(String providerNo, Date sdate);

    RSchedule search_rschedule_current2(String providerNo, Date sdate);

    List<RSchedule> search_rschedule_future1(String providerNo, Date sdate);

    List<RSchedule> findByProviderNoAndDates(String providerNo, Date apptDate);
}
