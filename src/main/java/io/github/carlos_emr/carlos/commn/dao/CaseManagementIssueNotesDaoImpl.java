/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
/**
 * JPA implementation of {@link CaseManagementIssueNotesDao} for case management data access.
 *
 * @since 2001
 */

public class CaseManagementIssueNotesDaoImpl implements CaseManagementIssueNotesDao {

    @PersistenceContext(unitName = "entityManagerFactory")
    protected EntityManager entityManager = null;

    /** {@inheritDoc} */

    @Override
    public List<CaseManagementIssue> getNoteIssues(Integer noteId) {
        Query query = entityManager.createNativeQuery(
                "select casemgmt_issue.* from casemgmt_issue_notes, casemgmt_issue where note_id=?1 and casemgmt_issue_notes.id=casemgmt_issue.id",
                CaseManagementIssue.class);
        query.setParameter(1, noteId);

        @SuppressWarnings("unchecked")
        List<CaseManagementIssue> results = query.getResultList();
        return (results);
    }

    /** {@inheritDoc} */

    @Override
    public List<Integer> getNoteIdsWhichHaveIssues(String[] issueId) {
        if (issueId == null || issueId.length == 0)
            return null;
        if (issueId.length == 1 && issueId[0].equals(""))
            return null;

        // Build parameterized IN clause
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < issueId.length; i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("?").append(i + 1);
        }

        String sql = "select note_id from casemgmt_issue_notes where id in (" + placeholders.toString() + ")";

        Query query = entityManager.createNativeQuery(sql);

        for (int i = 0; i < issueId.length; i++) {
            query.setParameter(i + 1, issueId[i]);
        }

        @SuppressWarnings("unchecked")
        List<Integer> results = query.getResultList();
        return (results);
    }

}
