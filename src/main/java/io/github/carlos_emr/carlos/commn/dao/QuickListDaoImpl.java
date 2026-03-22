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

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.QuickList;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link QuickListDao} for quick list data access.
 *
 * @since 2001
 */

public class QuickListDaoImpl extends AbstractDaoImpl<QuickList> implements QuickListDao {
    private static final Logger logger = MiscUtils.getLogger();

    /** Constructs this DAO for the {@link QuickList} entity class. */

    public QuickListDaoImpl() {
        super(QuickList.class);
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<QuickList> findAll() {
        Query query = createQuery("x", null);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<Object> findDistinct() {
        Query query = entityManager.createQuery("select distinct ql.quickListName from QuickList ql");
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<QuickList> findByNameResearchCodeAndCodingSystem(String quickListName, String researchCode, String codingSystem) {
        Query query = entityManager.createQuery("from QuickList q where q.quickListName = ?1 AND q.dxResearchCode = ?2 AND q.codingSystem = ?3");
        query.setParameter(1, quickListName);
        query.setParameter(2, researchCode);
        query.setParameter(3, codingSystem);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<QuickList> findByCodingSystem(String codingSystem) {
        Query query;
        if (codingSystem != null) {
            query = entityManager.createQuery("select ql from QuickList ql WHERE ql.codingSystem = ?1 AND ql.id IN (SELECT MIN(ql2.id) FROM QuickList ql2 WHERE ql2.codingSystem = ?1 GROUP BY ql2.quickListName)");
            query.setParameter(1, codingSystem);
        } else {
            query = entityManager.createQuery("select ql from QuickList ql WHERE ql.id IN (SELECT MIN(ql2.id) FROM QuickList ql2 GROUP BY ql2.quickListName)");
        }
        return query.getResultList();
    }

    @NativeSql
    @SuppressWarnings("unchecked")
    /** {@inheritDoc} */

    @Override
    public List<Object[]> findResearchCodeAndCodingSystemDescriptionByCodingSystem(String codingSystem, String quickListName) {
        try {
            // Validate codingSystem to prevent SQL injection - only allow known valid table names
            if (codingSystem == null || codingSystem.isEmpty()) {
                logger.error("no coding system provided");
                return new ArrayList<Object[]>();
            }
            
            // Validate codingSystem against allowed values to prevent SQL injection
            // Using the same enum validation as in getDescription() method
            try {
                // This will throw IllegalArgumentException if codingSystem is not valid
                AbstractCodeSystemDaoImpl.codingSystem.valueOf(codingSystem);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid coding system provided: " + codingSystem);
                return new ArrayList<>();
            }
            
            // Build SQL with validated table name and parameterized values for user input
            String sql = "Select q.dxResearchCode, c.description FROM quickList q, " + codingSystem 
                       + " c where q.codingSystem = ?1"
                       + " and q.quickListName = ?2 AND c." + codingSystem
                       + " = q.dxResearchCode order by c.description";
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, codingSystem);
            query.setParameter(2, quickListName);
            return query.getResultList();
        } catch (Exception e) {
            // TODO replace when test ignores are merged
            return new ArrayList<Object[]>();
        }

    }
}
