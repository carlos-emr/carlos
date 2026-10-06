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
import java.util.Date;
import java.util.Objects;
import jakarta.persistence.LockModeType;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.Immunizations;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ImmunizationsDaoImpl extends AbstractDaoImpl<Immunizations> implements ImmunizationsDao {

    public ImmunizationsDaoImpl() {
        super(Immunizations.class);
    }

    public List<Immunizations> findCurrentByDemographicNo(Integer demographicNo) {
        Query q = entityManager.createQuery("SELECT i FROM Immunizations i WHERE i.demographicNo=?1 AND i.archived=0");
        q.setParameter(1, demographicNo);

        @SuppressWarnings("unchecked")
        List<Immunizations> results = q.getResultList();

        return results;
    }

    @Override
    @Transactional
    public boolean replaceCurrent(Integer demographicNo, String providerNo, String xml, int expectedVersion) {
        Objects.requireNonNull(xml, "Schedule XML is required");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Invalid schedule version");
        }
        // Lock the stable patient row, including when no schedule exists yet. Every
        // schedule writer uses this transaction, so insert/archive is one operation.
        if (entityManager.find(Demographic.class, demographicNo, LockModeType.PESSIMISTIC_WRITE) == null) {
            return false;
        }
        List<Immunizations> current = entityManager.createQuery(
                "SELECT i FROM Immunizations i WHERE i.demographicNo=:patient AND i.archived=0", Immunizations.class)
                .setParameter("patient", demographicNo).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        int version = current.stream().mapToInt(Immunizations::getId).max().orElse(0);
        if (version != expectedVersion) {
            return false;
        }
        for (Immunizations previous : current) {
            previous.setArchived(1);
        }
        Immunizations next = new Immunizations();
        next.setDemographicNo(demographicNo);
        next.setProviderNo(providerNo);
        next.setImmunizations(xml);
        next.setSaveDate(new Date());
        next.setArchived(0);
        entityManager.persist(next);
        return true;
    }
}
