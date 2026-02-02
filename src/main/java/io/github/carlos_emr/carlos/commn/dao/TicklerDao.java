//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Tickler;

public interface TicklerDao extends AbstractDao<Tickler> {

    public Tickler find(Integer id);

    public List<Tickler> findActiveByMessageForPatients(List<Integer> demographicNos, String remString);

    public List<Tickler> findActiveByDemographicNoAndMessage(Integer demoNo, String message);

    public List<Tickler> findActiveByDemographicNo(Integer demoNo);

    public List<Tickler> findByTicklerNoDemo(Integer ticklerNo, Integer demoNo);

    public List<Tickler> findByTicklerNoAssignedTo(Integer ticklerNo, String assignedTo, Integer demoNo);

    public List<Tickler> findByDemographicIdTaskAssignedToAndMessage(Integer demographicNo, String taskAssignedTo,
                                                                     String message);

    public List<Tickler> search_tickler_bydemo(Integer demographicNo, String status, Date beginDate, Date endDate);

    public List<Tickler> search_tickler(Integer demographicNo, Date endDate);

    public List<Tickler> listTicklers(Integer demographicNo, Date beginDate, Date endDate);

    public int getActiveTicklerCount(String providerNo);

    public int getActiveTicklerByDemoCount(Integer demographicNo);

    public List<Tickler> getTicklers(CustomFilter filter, int offset, int limit);

    public List<Tickler> getTicklers(CustomFilter filter);

    public int getNumTicklers(CustomFilter filter);
}
