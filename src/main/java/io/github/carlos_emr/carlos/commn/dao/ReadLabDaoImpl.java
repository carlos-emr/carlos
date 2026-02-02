//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 * <p>
 * Modifications made by Magenta Health in 2024.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.ReadLab;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.Query;
import java.util.List;

@Repository
@Transactional
@SuppressWarnings("unchecked")
public class ReadLabDaoImpl extends AbstractDaoImpl<ReadLab> implements ReadLabDao {

    public ReadLabDaoImpl() {
        super(ReadLab.class);
    }

    @Override
    public void markAsRead(String providerNo, String labType, Integer labId) {
        ReadLab readLab = getByProviderNoAndLabTypeAndLabId(providerNo, labType, labId);
        if (readLab == null) {
            readLab = new ReadLab(providerNo, labType, labId);
            persist(readLab);
        }
    }

    @Override
    public boolean isRead(String providerNo, String labType, Integer labId) {
        return getByProviderNoAndLabTypeAndLabId(providerNo, labType, labId) != null;
    }

    @Override
    public ReadLab getByProviderNoAndLabTypeAndLabId(String providerNo, String labType, Integer labId) {
        String sql = "SELECT x FROM ReadLab x WHERE x.id.providerNo = ?1 " +
                "AND x.id.labType = ?2 AND x.id.labId = ?3";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, providerNo);
        query.setParameter(2, labType);
        query.setParameter(3, labId);

        @SuppressWarnings("unchecked")
        List<ReadLab> results = query.getResultList();
        return (results.size() > 0) ? results.get(0) : null;
    }
}
