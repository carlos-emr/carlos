//CHECKSTYLE:OFF
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
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import java.util.Set;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.HrmLog;
import org.springframework.stereotype.Repository;

@Repository
public class HrmLogDaoImpl extends AbstractDaoImpl<HrmLog> implements HrmLogDao {

    public HrmLogDaoImpl() {
        super(HrmLog.class);
    }

    @SuppressWarnings("unchecked")
    public List<HrmLog> query(int start, int length, String orderColumn, String orderDirection, String providerNo) {

        String sql = "FROM HrmLog d ";

        if (providerNo != null) {
            sql += " WHERE d.initiatingProviderNo = ?1";
        }
        
        // Whitelist validation for orderColumn to prevent SQL injection
        String validatedOrderColumn = validateOrderColumn(orderColumn);
        String validatedOrderDirection = validateOrderDirection(orderDirection);
        
        if (validatedOrderColumn != null && validatedOrderDirection != null) {
            sql += " ORDER BY d." + validatedOrderColumn + " " + validatedOrderDirection;
        }
        
        Query query = entityManager.createQuery(sql);

        if (providerNo != null) {
            query.setParameter(1, providerNo);
        }
        query.setFirstResult(start);
        query.setMaxResults(length);

        return query.getResultList();
    }

    private static final Set<String> VALID_ORDER_COLUMNS = Set.of(
        "id",
        "started",
        "initiatingProviderNo",
        "transactionType",
        "externalSystem",
        "error",
        "connected",
        "downloadedFiles",
        "numFilesDownloaded",
        "deleted"
    );

    private static final Set<String> VALID_ORDER_DIRECTIONS = Set.of("ASC", "DESC");
    
    /**
     * Validates the order column against a whitelist of allowed columns
     * @param orderColumn the column name to validate
     * @return the validated column name or null if invalid
     */
    private String validateOrderColumn(String orderColumn) {
        return VALID_ORDER_COLUMNS.contains(orderColumn) ? orderColumn : null;
    }
    
    /**
     * Validates the order direction against allowed values
     * @param orderDirection the direction to validate
     * @return the validated direction or null if invalid
     */
    private String validateOrderDirection(String orderDirection) {
        if (orderDirection == null) return null;
        String dir = orderDirection.trim().toUpperCase();
        return VALID_ORDER_DIRECTIONS.contains(dir) ? dir : null;
    }

}
