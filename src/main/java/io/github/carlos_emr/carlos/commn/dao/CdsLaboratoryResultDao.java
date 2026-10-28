/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.CdsLaboratoryResult;
import org.springframework.stereotype.Repository;

/** Persistence and bounded restriction lookup for structured CDS laboratory results. */
@Repository
public class CdsLaboratoryResultDao extends AbstractDaoImpl<CdsLaboratoryResult> {
    public CdsLaboratoryResultDao() {
        super(CdsLaboratoryResult.class);
    }

    /** Tests for a restriction without loading result narratives or counting the patient's history. */
    public boolean hasBlockedResult(int demographicNo) {
        if (demographicNo < 1) throw new IllegalArgumentException("Invalid laboratory patient");
        return !entityManager.createQuery("select l.id from CdsLaboratoryResult l "
                        + "where l.demographicNo=:patient and l.sourceBlocked='Y'", String.class)
                .setParameter("patient", demographicNo)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }
}
