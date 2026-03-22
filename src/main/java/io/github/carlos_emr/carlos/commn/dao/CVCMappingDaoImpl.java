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

import java.util.List;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.CVCMapping;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link CVCMappingDao} for Canadian Vaccine Catalogue (CVC) data access.
 *
 * @since 2001
 */

public class CVCMappingDaoImpl extends AbstractDaoImpl<CVCMapping> implements CVCMappingDao {

    /** Constructs this DAO for the {@link CVCMapping} entity class. */

    public CVCMappingDaoImpl() {
        super(CVCMapping.class);
    }

    public CVCMapping findByOscarName(String oscarName) {
        Query query = entityManager.createQuery("SELECT x FROM CVCMapping x WHERE x.oscarName = :oscarName");
        query.setParameter("oscarName", oscarName);

        return this.getSingleResultOrNull(query);
    }

    public CVCMapping findBySnomedId(String cvcSnomedId) {
        Query query = entityManager.createQuery("SELECT x FROM CVCMapping x WHERE x.cvcSnomedId = :cvcSnomedId");
        query.setParameter("cvcSnomedId", cvcSnomedId);

        return this.getSingleResultOrNull(query);
    }

    public List<CVCMapping> findMultipleByOscarName(String oscarName) {
        Query query = entityManager.createQuery("SELECT x FROM CVCMapping x WHERE x.oscarName = :oscarName");
        query.setParameter("oscarName", oscarName);

        List<CVCMapping> results = query.getResultList();
        return results;
    }
}
