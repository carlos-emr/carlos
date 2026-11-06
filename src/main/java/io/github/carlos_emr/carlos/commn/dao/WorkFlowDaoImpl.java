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
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.WorkFlow;
import org.springframework.stereotype.Repository;

@Repository
public class WorkFlowDaoImpl extends AbstractDaoImpl<WorkFlow> implements WorkFlowDao {

    public WorkFlowDaoImpl() {
        super(WorkFlow.class);
    }

    @Override
    public Integer findLatestRhFormId(int demographicNo, int workflowId) {
        Query query = entityManager.createNativeQuery(
                "SELECT ID FROM formRhImmuneGlobulin WHERE demographic_no = ?1 AND workflowId = ?2"
                + " ORDER BY formEdited DESC, ID DESC");
        query.setParameter(1, demographicNo);
        query.setParameter(2, workflowId);
        query.setMaxResults(1);
        List<?> rows = query.getResultList();
        return rows.isEmpty() ? null : ((Number) rows.get(0)).intValue();
    }

    public List<WorkFlow> findByWorkflowType(String type) {
        Query q = entityManager.createQuery("SELECT w FROM WorkFlow w WHERE w.workflowType=?1");
        q.setParameter(1, type);

        @SuppressWarnings("unchecked")
        List<WorkFlow> results = q.getResultList();

        return results;
    }

    public List<WorkFlow> findActiveByWorkflowType(String type) {
        Query q = entityManager.createQuery("SELECT w FROM WorkFlow w WHERE w.workflowType=?1 AND w.currentState <> ?2");
        q.setParameter(1, type);
        q.setParameter(2, "C");

        @SuppressWarnings("unchecked")
        List<WorkFlow> results = q.getResultList();

        return results;
    }

    public List<WorkFlow> findActiveByWorkflowTypeAndDemographicNo(String type, String demographicNo) {
        Query q = entityManager.createQuery("SELECT w FROM WorkFlow w WHERE w.workflowType=?1 AND w.demographicNo=?2 AND w.currentState <> ?3");
        q.setParameter(1, type);
        q.setParameter(2, demographicNo);
        q.setParameter(3, "C");

        @SuppressWarnings("unchecked")
        List<WorkFlow> results = q.getResultList();

        return results;
    }
}
