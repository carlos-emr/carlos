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

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.HL7HandlerMSHMapping;
import org.springframework.stereotype.Repository;

@Repository
public class HL7HandlerMSHMappingDaoImpl extends AbstractDaoImpl<HL7HandlerMSHMapping> implements HL7HandlerMSHMappingDao {

    public HL7HandlerMSHMappingDaoImpl() {
        super(HL7HandlerMSHMapping.class);
    }

    @Override
    public HL7HandlerMSHMapping findByFacility(String facility) {
        String sql = "select x from HL7HandlerMSHMapping x where x.facility=?1";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, facility);

        return (getSingleResultOrNull(query));
    }
}
