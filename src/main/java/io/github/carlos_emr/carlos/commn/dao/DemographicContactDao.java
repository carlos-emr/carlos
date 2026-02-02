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

import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.DemographicContact;

public interface DemographicContactDao extends AbstractDao<DemographicContact> {

    public List<DemographicContact> findByDemographicNo(int demographicNo);

    public List<DemographicContact> findActiveByDemographicNo(int demographicNo);

    public List<DemographicContact> findByDemographicNoAndCategory(int demographicNo, String category);

    public List<DemographicContact> find(int demographicNo, int contactId);

    public List<DemographicContact> findAllByContactIdAndCategoryAndType(int contactId, String category, int type);

    public List<DemographicContact> findAllByDemographicNoAndCategoryAndType(int demographicNo, String category,
                                                                             int type);

    public List<DemographicContact> findSDMByDemographicNo(int demographicNo);
}
