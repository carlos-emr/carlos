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

import java.util.Collections;
import java.util.List;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.IntegratorFileLog;
import org.springframework.stereotype.Repository;

@Repository
public class IntegratorFileLogDaoImpl extends AbstractDaoImpl<IntegratorFileLog> implements IntegratorFileLogDao {

    public IntegratorFileLogDaoImpl() {
        super(IntegratorFileLog.class);
    }

    @Override
    public IntegratorFileLog getLastFileData() {
        String queryStr = "FROM IntegratorFileLog c ORDER BY c.id DESC";

        Query query = entityManager.createQuery(queryStr);
        query.setMaxResults(1);

        return this.getSingleResultOrNull(query);

    }

    @Override
    public List<IntegratorFileLog> getFileLogHistory() {
        String queryStr = "FROM IntegratorFileLog c ORDER BY c.id DESC";

        Query query = entityManager.createQuery(queryStr);

        List<IntegratorFileLog> results = query.getResultList();

        return (results);

    }

    @Override
    public IntegratorFileLog findByFilenameAndChecksum(String filename, String checksum) {
        String queryStr = "FROM IntegratorFileLog c WHERE c.filename = ?1 and c.checksum = ?2 ORDER BY c.id DESC";

        Query query = entityManager.createQuery(queryStr);
        query.setParameter(1, filename);
        query.setParameter(2, checksum);

        return this.getSingleResultOrNull(query);

    }

    @Override
    public List<IntegratorFileLog> findAllWithNoCompletedIntegratorStatus() {
        String queryStr = "FROM IntegratorFileLog c WHERE c.integratorStatus IS NULL OR c.integratorStatus != 'COMPLETED' ORDER BY c.id DESC";

        Query query = entityManager.createQuery(queryStr);

        List<IntegratorFileLog> results = query.getResultList();

        return (results);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<IntegratorFileLog> findAllWithNoCompletedOrErrorIntegratorStatus() {
        String queryStr = "FROM IntegratorFileLog c WHERE c.integratorStatus IS NULL OR c.integratorStatus NOT LIKE 'COMPLETED' AND c.integratorStatus NOT LIKE 'ERROR' ORDER BY c.id DESC";

        Query query = entityManager.createQuery(queryStr);

        List<IntegratorFileLog> results = query.getResultList();
        if (results == null) {
            results = Collections.emptyList();
        }

        return results;
    }

}
