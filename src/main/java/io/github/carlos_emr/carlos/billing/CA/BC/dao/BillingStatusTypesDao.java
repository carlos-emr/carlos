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

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingStatusTypes;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link BillingStatusTypes} entities.
 * Provides persistence operations for billing status type lookup values
 * used in the BC billing system.
 *
 * @since 2026-03-17
 */
@Repository
public class BillingStatusTypesDao extends AbstractDaoImpl<BillingStatusTypes> {

    /**
     * Constructs a new {@code BillingStatusTypesDao} with the {@link BillingStatusTypes} entity class.
     */
    protected BillingStatusTypesDao() {
        super(BillingStatusTypes.class);
    }

    /**
     * Retrieves all billing status types.
     *
     * @return List of all {@link BillingStatusTypes} records
     */
    @SuppressWarnings("unchecked")
    public List<BillingStatusTypes> findAll() {
        Query query = entityManager.createQuery("SELECT x FROM " + modelClass.getSimpleName() + " x");
        List<BillingStatusTypes> results = query.getResultList();
        return results;
    }

    /**
     * Finds billing status types matching the given list of single-character codes.
     * Converts the string codes to characters for the IN clause query.
     *
     * @param codes List of String codes, where each string's first character is used as the lookup key
     * @return List of {@link BillingStatusTypes} matching the provided codes
     */
    @SuppressWarnings("unchecked")
    public List<BillingStatusTypes> findByCodes(List<String> codes) {
        Query query = entityManager.createQuery("FROM " + modelClass.getSimpleName() + " bst WHERE bst.id IN (:typeCodes)");
        List<Character> characterCodes = new ArrayList<>();
        for (String code : codes) {
            characterCodes.add(code.charAt(0));
        }
        query.setParameter("typeCodes", characterCodes);
        return query.getResultList();
    }

}
