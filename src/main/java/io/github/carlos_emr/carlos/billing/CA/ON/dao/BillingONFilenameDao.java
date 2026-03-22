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

import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFilename;
import io.github.carlos_emr.carlos.commn.dao.AbstractDaoImpl;
import org.springframework.stereotype.Repository;

/**
 * Data access object for {@link BillingONFilename} entities.
 * Provides persistence operations for Ontario billing filename records,
 * which track individual billing files within submission batches.
 *
 * @since 2026-03-17
 */
@Repository
public class BillingONFilenameDao extends AbstractDaoImpl<BillingONFilename> {

    /**
     * Constructs a new {@code BillingONFilenameDao} with the {@link BillingONFilename} entity class.
     */
    public BillingONFilenameDao() {
        super(BillingONFilename.class);
    }

    /**
     * Finds billing filenames by disk ID and status, ordered by ID descending.
     *
     * @param diskId Integer the disk submission batch ID
     * @param status String the file status to filter by
     * @return List of matching {@link BillingONFilename} records
     */
    public List<BillingONFilename> findByDiskIdAndStatus(Integer diskId, String status) {
        String q = "SELECT b FROM BillingONFilename b WHERE b.diskId = ?1  AND b.status = ?2 ORDER BY b.id DESC";
        Query query = entityManager.createQuery(q);
        query.setParameter(1, diskId);
        query.setParameter(2, status);

        @SuppressWarnings("unchecked")
        List<BillingONFilename> results = query.getResultList();

        return results;
    }

    /**
     * Finds billing filenames by disk ID and provider number, ordered by ID descending.
     *
     * @param diskId Integer the disk submission batch ID
     * @param provider String the provider number
     * @return List of matching {@link BillingONFilename} records
     */
    public List<BillingONFilename> findByDiskIdAndProvider(Integer diskId, String provider) {
        String q = "SELECT b FROM BillingONFilename b WHERE b.diskId = ?1  AND b.providerNo = ?2 ORDER BY b.id DESC";
        Query query = entityManager.createQuery(q);
        query.setParameter(1, diskId);
        query.setParameter(2, provider);

        @SuppressWarnings("unchecked")
        List<BillingONFilename> results = query.getResultList();

        return results;
    }

    /**
     * Finds all billing filenames for a given disk ID.
     *
     * @param diskId Integer the disk submission batch ID
     * @return List of all {@link BillingONFilename} records for the disk
     */
    public List<BillingONFilename> findByDiskId(Integer diskId) {
        String q = "SELECT b FROM BillingONFilename b WHERE b.diskId = ?1";
        Query query = entityManager.createQuery(q);
        query.setParameter(1, diskId);

        @SuppressWarnings("unchecked")
        List<BillingONFilename> results = query.getResultList();

        return results;
    }

    /**
     * Finds non-deleted billing filenames for a given disk ID, ordered by ID descending.
     * Excludes records with status 'D' (deleted).
     *
     * @param diskId Integer the disk submission batch ID
     * @return List of current (non-deleted) {@link BillingONFilename} records
     */
    public List<BillingONFilename> findCurrentByDiskId(Integer diskId) {
        String q = "SELECT b FROM BillingONFilename b WHERE b.diskId = ?1  AND b.status != ?2 ORDER BY b.id DESC";
        Query query = entityManager.createQuery(q);
        query.setParameter(1, diskId);
        query.setParameter(2, "D");

        @SuppressWarnings("unchecked")
        List<BillingONFilename> results = query.getResultList();

        return results;
    }
}
