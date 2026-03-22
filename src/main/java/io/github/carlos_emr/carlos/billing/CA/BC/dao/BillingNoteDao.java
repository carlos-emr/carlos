/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingNotes;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link BillingNotes} entities.
 * Provides persistence operations for billing notes in the BC billing system,
 * supporting lookup by billing master number and optional note type filtering.
 *
 * @since 2026-03-17
 */
@Repository
public class BillingNoteDao extends AbstractDaoImpl<BillingNotes> {

    /**
     * Constructs a new {@code BillingNoteDao} with the {@link BillingNotes} entity class.
     */
    protected BillingNoteDao() {
        super(BillingNotes.class);
    }

    /**
     * Finds notes for the specified billing master and note type
     *
     * @param billingmaster_no Billing master number to find notes for
     * @param noteType         Optional type of the notes to be found. If this parameter is left as null note type is not used for lookup.
     * @return Returns all matching notes
     */
    @SuppressWarnings("unchecked")
    public List<BillingNotes> findNotes(Integer billingmaster_no, Integer noteType) {
        StringBuilder buf = new StringBuilder("FROM BillingNotes n WHERE n.billingmasterNo = :bmno");
        boolean isNoteTypeSpecified = noteType != null;
        if (isNoteTypeSpecified) buf.append(" AND n.noteType = :noteType");
        Query query = entityManager.createQuery(buf.toString());
        query.setParameter("bmno", billingmaster_no);
        if (isNoteTypeSpecified) query.setParameter("noteType", noteType);
        return query.getResultList();
    }

    /**
     * Gets single most recent note for the specified billing master number and note
     *
     * @param billingmaster_no Billing master number to look notes for
     * @param noteType         Type of the note to be found
     * @return Returns the note or null, if no note can be found
     */
    public BillingNotes findSingleNote(Integer billingmaster_no, Integer noteType) {
        Query query = entityManager.createQuery("FROM BillingNotes n WHERE n.billingmasterNo = :bmno AND n.noteType = :noteType ORDER BY n.createdate desc");
        query.setParameter("bmno", billingmaster_no);
        query.setParameter("noteType", noteType);
        query.setMaxResults(1);
        return getSingleResultOrNull(query);
    }

}
