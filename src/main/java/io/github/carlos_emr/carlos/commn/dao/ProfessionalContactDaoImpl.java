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
import java.util.List;
import java.util.Map;
import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import org.springframework.stereotype.Repository;

@Repository
public class ProfessionalContactDaoImpl extends AbstractDaoImpl<ProfessionalContact> implements ProfessionalContactDao {

    /**
     * Allowlist of valid search-mode values. Most entries map directly to HQL property
     * names on the ProfessionalContact entity; {@code search_name} is a logical mode that
     * triggers a composite lastName/firstName search and is not used as an HQL property.
     */
    private static final Map<String, String> VALID_SEARCH_MODES = Map.ofEntries(
            Map.entry("search_name", "search_name"),
            Map.entry("updateDate", "updateDate"),
            Map.entry("id", "id"),
            Map.entry("lastName", "lastName"),
            Map.entry("firstName", "firstName"),
            Map.entry("address", "address"),
            Map.entry("address2", "address2"),
            Map.entry("city", "city"),
            Map.entry("province", "province"),
            Map.entry("country", "country"),
            Map.entry("postal", "postal"),
            Map.entry("residencePhone", "residencePhone"),
            Map.entry("cellPhone", "cellPhone"),
            Map.entry("workPhone", "workPhone"),
            Map.entry("workPhoneExtension", "workPhoneExtension"),
            Map.entry("email", "email"),
            Map.entry("fax", "fax"),
            Map.entry("note", "note"),
            Map.entry("deleted", "deleted"),
            Map.entry("specialty", "specialty"),
            Map.entry("cpso", "cpso"),
            Map.entry("systemId", "systemId")
    );

    /** Allowlist mapping valid ORDER BY column names to safe HQL property names. */
    private static final Map<String, String> VALID_ORDER_BY_COLUMNS = Map.ofEntries(
            Map.entry("updateDate", "updateDate"),
            Map.entry("id", "id"),
            Map.entry("lastName", "lastName"),
            Map.entry("firstName", "firstName"),
            Map.entry("address", "address"),
            Map.entry("address2", "address2"),
            Map.entry("city", "city"),
            Map.entry("province", "province"),
            Map.entry("country", "country"),
            Map.entry("postal", "postal"),
            Map.entry("residencePhone", "residencePhone"),
            Map.entry("cellPhone", "cellPhone"),
            Map.entry("workPhone", "workPhone"),
            Map.entry("workPhoneExtension", "workPhoneExtension"),
            Map.entry("email", "email"),
            Map.entry("fax", "fax"),
            Map.entry("note", "note"),
            Map.entry("deleted", "deleted"),
            Map.entry("specialty", "specialty"),
            Map.entry("cpso", "cpso"),
            Map.entry("systemId", "systemId")
    );

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

        Query query = entityManager.createQuery(sql); // NOSONAR javasecurity:S3649 — searchMode and orderBy are allowlist-validated via VALID_SEARCH_MODES and VALID_ORDER_BY_COLUMNS maps
        for (int x = 0; x < paramList.size(); x++) {
            query.setParameter(x + 1, paramList.get(x));
        }

        @SuppressWarnings("unchecked")
        List<ProfessionalContact> contacts = query.getResultList();
        return contacts;
    }
    
    /**
     * Validates the searchMode parameter against {@link #VALID_SEARCH_MODES}.
     *
     * @param searchMode the search mode to validate
     * @return the validated search mode from the allowlist, defaulting to {@code "lastName"}
     *         if the input is null or empty
     * @throws IllegalArgumentException if searchMode is not in the allowlist
     */
    private String validateSearchMode(String searchMode) {
        if (searchMode == null || searchMode.trim().isEmpty()) {
            return "lastName";
        }

        String safeMode = VALID_SEARCH_MODES.get(searchMode);
        if (safeMode != null) {
            return safeMode;
        }

        throw new IllegalArgumentException("Invalid search mode: "
                + searchMode.substring(0, Math.min(searchMode.length(), 50)));
    }

    /**
     * Validates the orderBy parameter against {@link #VALID_ORDER_BY_COLUMNS}.
     *
     * @param orderBy the order by clause to validate
     * @return the validated order by clause built from allowlisted constants, defaulting to
     *         {@code "c.lastName, c.firstName"} if the input is null or empty
     * @throws IllegalArgumentException if orderBy contains invalid column names
     */
    private String validateOrderBy(String orderBy) {
        if (orderBy == null || orderBy.trim().isEmpty()) {
            return "c.lastName, c.firstName";
        }

        StringBuilder validatedOrderBy = new StringBuilder();
        String[] orderByParts = orderBy.split(",");

        for (String rawPart : orderByParts) {
            String part = rawPart == null ? "" : rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }

            if (part.regionMatches(true, 0, "c.", 0, 2)) {
                part = part.substring(2).trim();
            }

            String[] tokens = part.split("\\s+");
            if (tokens.length == 0 || tokens[0].isEmpty()) {
                continue;
            }
            if (tokens.length > 2) {
                String truncated = part.length() > 50 ? part.substring(0, 50) : part;
                throw new IllegalArgumentException("Invalid sort clause: " + truncated);
            }

            String column = tokens[0];
            String safeColumn = VALID_ORDER_BY_COLUMNS.get(column);
            if (safeColumn == null) {
                String truncated = column.length() > 50 ? column.substring(0, 50) : column;
                throw new IllegalArgumentException("Invalid sort column: " + truncated);
            }

            String direction = "";
            if (tokens.length == 2) {
                if ("ASC".equalsIgnoreCase(tokens[1])) {
                    direction = " ASC";
                } else if ("DESC".equalsIgnoreCase(tokens[1])) {
                    direction = " DESC";
                } else {
                    String truncated = tokens[1].length() > 50 ? tokens[1].substring(0, 50) : tokens[1];
                    throw new IllegalArgumentException("Invalid sort direction: " + truncated);
                }
            }

            if (validatedOrderBy.length() > 0) {
                validatedOrderBy.append(", ");
            }
            validatedOrderBy.append("c.").append(safeColumn).append(direction);
        }

        return validatedOrderBy.length() > 0 ? validatedOrderBy.toString() : "c.lastName, c.firstName";
    }
}
