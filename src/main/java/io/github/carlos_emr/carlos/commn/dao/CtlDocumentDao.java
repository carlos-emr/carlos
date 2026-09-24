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
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.CtlDocument;

public interface CtlDocumentDao extends AbstractDao<CtlDocument> {

    public CtlDocument getCtrlDocument(Integer docId);

    public List<CtlDocument> findByDocumentNoAndModule(Integer ctlDocNo, String module);

    /**
     * Returns the subset of {@code documentNos} that are linked to the given patient through a
     * non-deleted {@code ctl_document} row (module {@code demographic}, module_id = patient).
     *
     * <p>Used as an ownership check before a document id supplied by a browser is attached to,
     * or sent out with, that patient's referral. A document that is unknown, deleted, or linked
     * to another patient is simply absent from the result.</p>
     *
     * @param demographicNo the patient that must own the documents; {@code null} yields an empty list
     * @param documentNos candidate document numbers; {@code null} or empty yields an empty list
     *                    without querying (an empty JPQL {@code IN} list is not portable)
     * @return the owned document numbers; never {@code null}
     * @since 2026-09-24
     */
    public List<Integer> findDocumentNosForDemographic(Integer demographicNo, Collection<Integer> documentNos);

}
