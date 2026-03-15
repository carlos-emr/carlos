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

@Repository
public class HRMDocumentCommentDao extends AbstractDaoImpl<HRMDocumentComment> {

    public HRMDocumentCommentDao() {
        super(HRMDocumentComment.class);
    }

    @SuppressWarnings("unchecked")
    public List<HRMDocumentComment> getCommentsForDocument(Integer documentId) {
        String sql = "select x from " + this.modelClass.getName() + " x where x.hrmDocumentId=?1 and x.deleted=0 order by commentTime desc";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, documentId);
        return query.getResultList();
    }

    public void deleteComment(Integer commentId) {
        HRMDocumentComment comment = this.find(commentId);
        if (comment != null) {
            comment.setDeleted(true);
            this.merge(comment);
        }
    }

}
