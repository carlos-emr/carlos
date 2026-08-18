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

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import org.springframework.stereotype.Repository;

@Repository
public class CVCImmunizationDaoImpl extends AbstractDaoImpl<CVCImmunization> implements CVCImmunizationDao {

    public CVCImmunizationDaoImpl() {
        super(CVCImmunization.class);
    }

    @Override
    public void removeAll() {
        Query query = entityManager.createQuery("DELETE FROM CVCImmunization");
        query.executeUpdate();
    }

    @Override
    public List<CVCImmunization> findAllGeneric() {
        Query query = entityManager.createQuery("SELECT x FROM CVCImmunization x WHERE x.generic = :generic");
        query.setParameter("generic", true);
        List<CVCImmunization> result = query.getResultList();
        return result;
    }

    @Override
    public List<CVCImmunization> findByParent(String conceptCodeId) {
        Query query = entityManager
                .createQuery("SELECT x FROM CVCImmunization x WHERE x.parentConceptId = :parentConceptId");
        query.setParameter("parentConceptId", conceptCodeId);
        List<CVCImmunization> result = query.getResultList();
        return result;
    }

    @Override
    public CVCImmunization findBySnomedConceptId(String conceptCodeId) {
        Query query = entityManager
                .createQuery("SELECT x FROM CVCImmunization x WHERE x.snomedConceptId = :snomedConceptId");
        query.setParameter("snomedConceptId", conceptCodeId);
        query.setMaxResults(1);
        List<CVCImmunization> result = query.getResultList();
        if (!result.isEmpty()) {
            return result.get(0);
        }
        return null;
    }

    @Override
    public List<CVCImmunization> query(String term, boolean includeGenerics, boolean includeBrands) {
        if (!includeBrands && !includeGenerics) {
            return new ArrayList<CVCImmunization>();
        }

        String segment = "";

        if (includeBrands && !includeGenerics) {
            segment = " AND generic=0 ";
        }
        if (!includeBrands && includeGenerics) {
            segment = " AND generic=1 ";
        }

        Query query = entityManager.createQuery(
                "SELECT x FROM CVCImmunization x WHERE x.displayName like :term OR x.picklistName like :term"
                        + segment);
        query.setParameter("term", "%" + term + "%");
        List<CVCImmunization> results = query.getResultList();
        return results;
    }
}
