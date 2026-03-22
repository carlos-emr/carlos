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

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.CtlDiagCode;
import org.springframework.stereotype.Repository;

@Repository
/**
 * JPA implementation of {@link CtlDiagCodeDao} for control table data access.
 *
 * @since 2001
 */

public class CtlDiagCodeDaoImpl extends AbstractDaoImpl<CtlDiagCode> implements CtlDiagCodeDao {

    /** Constructs this DAO for the {@link CtlDiagCode} entity class. */

    public CtlDiagCodeDaoImpl() {
        super(CtlDiagCode.class);
    }

    @SuppressWarnings("unchecked")
    @NativeSql({"ctl_diagcode", "diagnosticcode"})
    public List<Object[]> getDiagnostics(String billRegion, String serviceType) {
        Query query = entityManager.createNativeQuery("SELECT d.diagnostic_code, d.description FROM diagnosticcode d, "
                + "ctl_diagcode c WHERE d.diagnostic_code=c.diagnostic_code and d.region = ?1 and c.servicetype = ?2");
        query.setParameter(1, billRegion);
        query.setParameter(2, serviceType);
        return query.getResultList();
    }

    public List<CtlDiagCode> findByServiceType(String serviceType) {
        Query q = entityManager.createQuery("select x from CtlDiagCode x where x.serviceType = ?1");
        q.setParameter(1, serviceType);

        @SuppressWarnings("unchecked")
        List<CtlDiagCode> results = q.getResultList();

        return results;
    }
}
