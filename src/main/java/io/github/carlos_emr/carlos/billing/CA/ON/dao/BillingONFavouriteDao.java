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
package io.github.carlos_emr.carlos.billing.CA.ON.dao;

import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFavourite;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link BillingONFavourite} entities.
 * Provides persistence operations for Ontario billing favourites,
 * which are saved billing templates for quick reuse by providers.
 *
 * @since 2026-03-17
 */
@Repository
public class BillingONFavouriteDao extends AbstractDaoImpl<BillingONFavourite> {

    /**
     * Constructs a new {@code BillingONFavouriteDao} with the {@link BillingONFavourite} entity class.
     */
    public BillingONFavouriteDao() {
        super(BillingONFavourite.class);
    }

    /**
     * Finds billing favourites by name.
     *
     * @param name String the favourite name to search for
     * @return List of matching {@link BillingONFavourite} records
     */
    public List<BillingONFavourite> findByName(String name) {
        Query q = entityManager.createQuery("SELECT b FROM BillingONFavourite b WHERE b.name = ?1");
        q.setParameter(1, name);

        @SuppressWarnings("unchecked")
        List<BillingONFavourite> results = q.getResultList();

        return results;
    }

    /**
     * Finds billing favourites by name and provider number.
     *
     * @param name String the favourite name
     * @param providerNo String the provider number
     * @return List of matching {@link BillingONFavourite} records
     */
    public List<BillingONFavourite> findByNameAndProviderNo(String name, String providerNo) {
        Query q = entityManager.createQuery("SELECT b FROM BillingONFavourite b WHERE b.name = ?1 AND b.providerNo = ?2");
        q.setParameter(1, name);
        q.setParameter(2, providerNo);

        @SuppressWarnings("unchecked")
        List<BillingONFavourite> results = q.getResultList();

        return results;
    }

    /**
     * Finds all non-deleted billing favourites.
     *
     * @return List of active {@link BillingONFavourite} records where deleted flag is 0
     */
    public List<BillingONFavourite> findCurrent() {
        Query q = entityManager.createQuery("SELECT b FROM BillingONFavourite b WHERE b.deleted=0");

        @SuppressWarnings("unchecked")
        List<BillingONFavourite> results = q.getResultList();

        return results;
    }
}
