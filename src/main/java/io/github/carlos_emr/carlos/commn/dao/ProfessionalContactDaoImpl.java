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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import org.springframework.stereotype.Repository;

@Repository
public class ProfessionalContactDaoImpl extends AbstractDaoImpl<ProfessionalContact> implements ProfessionalContactDao {

    /**
     * Allowlist mapping valid search-mode names to the corresponding safe HQL property name.
     * Values come from this map (not from user input), which breaks any CodeQL taint flow
     * from the HTTP request into the query string.
     */
    private static final Map<String, String> VALID_SEARCH_MODES;
    /**
     * Allowlist mapping valid ORDER BY column names to the corresponding safe HQL property name.
     */
    private static final Map<String, String> VALID_ORDER_BY_COLUMNS;

    static {
        VALID_SEARCH_MODES = new LinkedHashMap<>();
        for (String col : Arrays.asList(
                "search_name", "updateDate", "id", "lastName", "firstName", "address", "address2",
                "city", "province", "country", "postal", "residencePhone",
                "cellPhone", "workPhone", "workPhoneExtension", "email", "fax", "note", "deleted",
                "specialty", "cpso", "systemId")) {
            VALID_SEARCH_MODES.put(col, col);
        }

        VALID_ORDER_BY_COLUMNS = new LinkedHashMap<>();
        for (String col : Arrays.asList(
                "updateDate", "id", "lastName", "firstName", "address", "address2", "city",
                "province", "country", "postal", "residencePhone", "cellPhone",
                "workPhone", "workPhoneExtension", "email", "fax", "note", "deleted",
                "specialty", "cpso", "systemId")) {
            VALID_ORDER_BY_COLUMNS.put(col, col);
        }
    }

    public ProfessionalContactDaoImpl() {
        super(ProfessionalContact.class);
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    @Override
    public List<ProfessionalContact> findAll() {
        Query query = createQuery("x", null);
        return query.getResultList();
    }

    @Override
    public List<ProfessionalContact> search(String searchMode, String orderBy, String keyword) {
        StringBuilder where = new StringBuilder();
        List<String> paramList = new ArrayList<String>();

        // Validate searchMode to prevent SQL injection
        String validatedSearchMode = validateSearchMode(searchMode);
        
        if (validatedSearchMode.equals("search_name")) {
            String[] temp = keyword.split("\\,\\p{Space}*");
            if (temp.length > 1) {
                where.append("c.lastName like ?1 and c.firstName like ?2");
                paramList.add(temp[0] + "%");
                paramList.add(temp[1] + "%");
            } else {
                where.append("c.lastName like ?1");
                paramList.add(temp[0] + "%");
            }
        } else {
            where.append("c.").append(validatedSearchMode).append(" like ?1");
            paramList.add(keyword + "%");
        }
        
        // Validate and sanitize orderBy to prevent SQL injection
        String validatedOrderBy = validateOrderBy(orderBy);
        
        String sql = "SELECT c from ProfessionalContact c where " + where.toString() + " order by " + validatedOrderBy;

        Query query = entityManager.createQuery(sql);
        for (int x = 0; x < paramList.size(); x++) {
            query.setParameter(x + 1, paramList.get(x));
        }

        @SuppressWarnings("unchecked")
        List<ProfessionalContact> contacts = query.getResultList();
        return contacts;
    }
    
    /**
     * Validates the searchMode parameter to ensure it only contains valid column names.
     * Returns the safe, hardcoded column name from {@link #VALID_SEARCH_MODES} — never the raw
     * user input — so CodeQL taint flow from request parameters cannot reach the HQL query.
     *
     * @param searchMode the search mode to validate
     * @return the validated search mode from the allowlist
     * @throws IllegalArgumentException if searchMode is invalid
     */
    private String validateSearchMode(String searchMode) {
        if (searchMode == null || searchMode.trim().isEmpty()) {
            return "lastName"; // default to lastName
        }
        
        // Return the value from the static allowlist Map — not the user-supplied string — to
        // break any CodeQL taint flow from request parameters into the query string.
        String safeMode = VALID_SEARCH_MODES.get(searchMode);
        if (safeMode != null) {
            return safeMode;
        }
        
        throw new IllegalArgumentException("Invalid search mode: " + searchMode);
    }
    
    /**
     * Validates the orderBy parameter to ensure it only contains valid column names and sort orders.
     * Constructs the ORDER BY expression exclusively from {@link #VALID_ORDER_BY_COLUMNS} — never
     * from the raw user input — so CodeQL taint flow from request parameters cannot reach the HQL query.
     *
     * @param orderBy the order by clause to validate
     * @return the validated order by clause built from allowlisted constants
     * @throws IllegalArgumentException if orderBy contains invalid column names
     */
    private String validateOrderBy(String orderBy) {
        if (orderBy == null || orderBy.trim().isEmpty()) {
            return "c.lastName, c.firstName"; // default ordering
        }
        
        StringBuilder validatedOrderBy = new StringBuilder();
        String[] orderByParts = orderBy.split(",");
        
        for (int i = 0; i < orderByParts.length; i++) {
            String part = orderByParts[i].trim();
            if (part.isEmpty()) continue;
            
            // Remove any "c." prefix if present
            if (part.startsWith("c.")) {
                part = part.substring(2);
            }
            
            // Split column and sort order (ASC/DESC)
            String[] columnAndOrder = part.split("\\s+");
            String column = columnAndOrder[0];
            String sortOrder = "";
            
            if (columnAndOrder.length > 1) {
                String order = columnAndOrder[1].toUpperCase();
                if ("ASC".equals(order) || "DESC".equals(order)) {
                    sortOrder = " " + order;
                }
            }
            
            // Validate column name; use the allowlisted constant from the map, not user input
            String safeColumn = VALID_ORDER_BY_COLUMNS.get(column);
            if (safeColumn == null) {
                throw new IllegalArgumentException("Invalid order by column: " + column);
            }
            
            if (i > 0) {
                validatedOrderBy.append(", ");
            }
            // Append the safe constant from the static allowlist, not the raw user-supplied column
            validatedOrderBy.append("c.").append(safeColumn).append(sortOrder);
        }
        
        return validatedOrderBy.length() > 0 ? validatedOrderBy.toString() : "c.lastName, c.firstName";
    }
}
