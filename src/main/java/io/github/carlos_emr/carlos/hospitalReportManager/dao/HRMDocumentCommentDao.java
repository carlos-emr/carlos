/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.hospitalReportManager.dao;

import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentComment;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link HRMDocumentComment} entities, managing comments
 * attached to HRM documents with soft-delete support.
 *
 * @see HRMDocumentComment
 * @since 2012-04-04
 */
@Repository
public class HRMDocumentCommentDao extends AbstractDaoImpl<HRMDocumentComment> {

    public HRMDocumentCommentDao() {
        super(HRMDocumentComment.class);
    }

    @SuppressWarnings("unchecked")
    /**
     * Returns all non-deleted comments for a given HRM document, ordered by most recent first.
     *
     * @param documentId Integer the HRM document ID
     * @return List&lt;HRMDocumentComment&gt; the non-deleted comments, newest first
     */
    public List<HRMDocumentComment> getCommentsForDocument(Integer documentId) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.hrmDocumentId=?1 and x.deleted=false order by commentTime desc";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, documentId);
        return query.getResultList();
    }

    /**
     * Soft-deletes a comment by setting its deleted flag to {@code true}.
     *
     * @param commentId Integer the comment ID to delete
     */
    public void deleteComment(Integer commentId) {
        HRMDocumentComment comment = this.find(commentId);
        if (comment != null) {
            comment.setDeleted(true);
            this.merge(comment);
        }
    }

}
