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

import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;

/**
 * @author jackson bi
 */
@Repository
public class QueueDocumentLinkDaoImpl extends AbstractDaoImpl<QueueDocumentLink> implements QueueDocumentLinkDao {

    public QueueDocumentLinkDaoImpl() {
        super(QueueDocumentLink.class);
    }

    @Override
    public List<QueueDocumentLink> getQueueDocLinks() {
        Query query = entityManager.createQuery("SELECT q from QueueDocumentLink q");

        @SuppressWarnings("unchecked")
        List<QueueDocumentLink> queues = query.getResultList();
        return queues;
    }
    @Override
    /**
     * Retrieves a list of active QueueDocumentLink objects from the database.
     */
    public List<QueueDocumentLink> getActiveQueueDocLink() {
        Query query = entityManager.createNativeQuery("SELECT q.* FROM queue_document_link q LEFT JOIN document d ON q.document_id = d.document_no WHERE q.status = ?1 ORDER BY CASE WHEN d.updatedatetime IS NULL THEN 1 ELSE 0 END, d.updatedatetime ASC", QueueDocumentLink.class);
        query.setParameter(1, "A");

        @SuppressWarnings("unchecked")
        List<QueueDocumentLink> queues = query.getResultList();

        return queues;
    }

    @Override
    public List<QueueDocumentLink> getQueueFromDocument(Integer docId) {
        Query query = entityManager.createQuery("SELECT q from QueueDocumentLink q where q.docId=?1");
        query.setParameter(1, docId);

        @SuppressWarnings("unchecked")
        List<QueueDocumentLink> queues = query.getResultList();

        return queues;
    }

    @Override
    public List<QueueDocumentLink> getDocumentFromQueue(Integer qId) {
        Query query = entityManager.createQuery("SELECT q from QueueDocumentLink q where queueId=?1");
        query.setParameter(1, qId);

        @SuppressWarnings("unchecked")
        List<QueueDocumentLink> queues = query.getResultList();

        return queues;
    }

    @Override
    public boolean hasQueueBeenLinkedWithDocument(Integer dId, Integer qId) {
        Query query = entityManager.createQuery("SELECT q from QueueDocumentLink q where q.docId=?1 and q.queueId=?2");
        query.setParameter(1, dId);
        query.setParameter(2, qId);
        @SuppressWarnings("unchecked")
        List<QueueDocumentLink> queues = query.getResultList();

        return (queues.size() > 0);
    }

    @Override
    public boolean setStatusInactive(Integer docId) {
        if (docId == null) return false;

        List<QueueDocumentLink> qs = getQueueFromDocument(docId);
        if (qs.size() > 0) {
            QueueDocumentLink q = qs.get(0);
            if (q.getStatus() != null && !q.getStatus().equals("I")) {
                q.setStatus("I");
                merge(q);
                return true;
            } else {
                return false;
            }
        }
        return false;
        //if status is not I, change to I
        //if status is I, do nothing
    }

    @Override
    public void addActiveQueueDocumentLink(Integer qId, Integer dId) {
        try {
            if (!hasQueueBeenLinkedWithDocument(dId, qId)) {
                QueueDocumentLink qdl = new QueueDocumentLink();
                qdl.setDocId(dId);
                qdl.setStatus("A");
                qdl.setQueueId(qId);
                persist(qdl);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }

    @Override
    public void addToQueueDocumentLink(Integer qId, Integer dId) {
        try {
            if (!hasQueueBeenLinkedWithDocument(dId, qId)) {
                QueueDocumentLink qdl = new QueueDocumentLink();
                qdl.setDocId(dId);
                qdl.setQueueId(qId);
                persist(qdl);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
    }
}
