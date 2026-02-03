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

import java.util.List;
import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.FlowSheetDrug;
import org.springframework.stereotype.Repository;

@Repository
public class FlowSheetDrugDaoImpl extends AbstractDaoImpl<FlowSheetDrug> implements FlowSheetDrugDao {

    public FlowSheetDrugDaoImpl() {
        super(FlowSheetDrug.class);
    }

    public FlowSheetDrug getFlowSheetDrug(Integer id) {
        return this.find(id);
    }

    public List<FlowSheetDrug> getFlowSheetDrugs(String flowsheet, Integer demographic) {
        Query query = entityManager.createQuery("SELECT fd FROM FlowSheetDrug fd WHERE fd.flowsheet=?1 and fd.archived=0 and fd.demographicNo=?2");
        query.setParameter(1, flowsheet);
        query.setParameter(2, demographic);

        @SuppressWarnings("unchecked")
        List<FlowSheetDrug> list = query.getResultList();
        return list;
    }
}
