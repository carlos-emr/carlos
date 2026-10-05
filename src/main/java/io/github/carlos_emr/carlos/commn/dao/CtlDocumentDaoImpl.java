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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import org.springframework.stereotype.Repository;

@Repository
public class CtlDocumentDaoImpl extends AbstractDaoImpl<CtlDocument> implements CtlDocumentDao {

    public CtlDocumentDaoImpl() {
        super(CtlDocument.class);
    }

    @Override
    public CtlDocument getCtrlDocument(Integer docId) {
        Query query = entityManager.createQuery("select x from CtlDocument x where x.id.documentNo=?1");
        query.setParameter(1, docId);

        return (getSingleResultOrNull(query));
    }

    @Override
    public List<CtlDocument> findByDocumentNoAndModule(Integer ctlDocNo, String module) {
        Query query = entityManager
                .createQuery("select x from CtlDocument x where x.id.documentNo=?1 and x.id.module = ?2");
        query.setParameter(1, ctlDocNo);
        query.setParameter(2, module);

        @SuppressWarnings("unchecked")
        List<CtlDocument> cList = query.getResultList();
        return cList;
    }

    @Override
    public List<Integer> findDocumentNosForDemographic(Integer demographicNo, Collection<Integer> documentNos) {
        if (demographicNo == null || documentNos == null || documentNos.isEmpty()) {
            return Collections.emptyList();
        }
        // Deletion lives on document.status: EDocUtil.deleteDocument sets it to 'D' and leaves the
        // ctl_document row (and its status) untouched so undeleteDocument can restore the prior
        // status. Checking only ctl_document.status would therefore still verify a deleted document.
        // The join also drops ctl rows that point at no document at all. ctl_document.status is
        // still compared null-safely: legacy rows may carry a NULL status and are live.
        Query query = entityManager.createQuery(
                "select distinct x.id.documentNo from CtlDocument x, Document d"
                        + " where d.documentNo = x.id.documentNo"
                        + " and x.id.module = :module and x.id.moduleId = :demographicNo"
                        + " and d.status <> 'D'"
                        + " and (x.status is null or x.status <> 'D')"
                        + " and x.id.documentNo in (:documentNos)");
        query.setParameter("module", DocumentDao.Module.DEMOGRAPHIC.getName());
        query.setParameter("demographicNo", demographicNo);
        query.setParameter("documentNos", documentNos);

        @SuppressWarnings("unchecked")
        List<Integer> owned = query.getResultList();
        return owned;
    }

}
