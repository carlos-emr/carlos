/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
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

import io.github.carlos_emr.carlos.commn.NativeSql;
import io.github.carlos_emr.carlos.commn.model.DxAssociation;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.stereotype.Repository;
import io.github.carlos_emr.carlos.utility.LogSanitizer;

@Repository
public class DxDaoImpl extends AbstractDaoImpl<DxAssociation> implements DxDao {

    /**
     * Allowlist mapping the user-supplied coding system name to the corresponding
     * database table/column name.  Values come from this map — not from user input —
     * which breaks any CodeQL taint flow from the request into the native SQL query.
     */
    private static final Map<String, String> VALID_CODING_SYSTEMS = Map.of(
            "icd9",        "icd9",
            "icd10",       "icd10",
            "ichppccode",  "ichppccode",
            "SnomedCore",  "SnomedCore",
            "msp",         "msp"
    );

    public DxDaoImpl() {
        super(DxAssociation.class);
    }

    @Override
    public List<DxAssociation> findAllAssociations() {
        Query query = entityManager.createQuery("select x from DxAssociation x order by x.dxCodeType,x.dxCode");

        @SuppressWarnings("unchecked")
        List<DxAssociation> results = query.getResultList();

        return results;
    }

    @Override
    public int removeAssociations() {
        Query query = entityManager.createQuery("DELETE from DxAssociation");
        return query.executeUpdate();
    }

    @Override
    public DxAssociation findAssociation(String codeType, String code) {
        Query query = entityManager.createQuery("SELECT x from DxAssociation x where x.codeType = ?1 and x.code = ?2");
        query.setParameter(1, codeType);
        query.setParameter(2, code);

        @SuppressWarnings("unchecked")
        List<DxAssociation> results = query.getResultList();
        if (!results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }

    @NativeSql
    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> findCodingSystemDescription(String codingSystem, String code) {
        // Use the allowlist map to obtain a safe table/column name; if the value is not
        // in the map the input is invalid and we return an empty result.
        String safeSystem = VALID_CODING_SYSTEMS.get(codingSystem);
        if (safeSystem == null) {
            MiscUtils.getLogger().warn("Invalid coding system name: {}", LogSanitizer.sanitize(codingSystem));
            return new ArrayList<Object[]>();
        }

        try {
            // safeSystem comes from a hardcoded map — not from user input — so it is safe
            // to interpolate as a table/column identifier.  The code value is parameterized.
            String sql = "SELECT " + safeSystem + ", description FROM " + safeSystem + " WHERE " + safeSystem
                    + " = ?1";
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, code);
            return query.getResultList();
        } catch (Exception e) {
            MiscUtils.getLogger().error("error executing query for codingSystem: {}", LogSanitizer.sanitize(codingSystem), e);
            return new ArrayList<Object[]>();
        }
    }

    @NativeSql
    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> findCodingSystemDescription(String codingSystem, String[] keywords) {
        try {
            // Use the allowlist map to obtain a safe table/column name.
            String safeSystem = VALID_CODING_SYSTEMS.get(codingSystem);
            if (safeSystem == null) {
                MiscUtils.getLogger().warn("Invalid coding system name: {}", LogSanitizer.sanitize(codingSystem));
                return new ArrayList<Object[]>();
            }
            
            // Filter out empty keywords
            List<String> validKeywords = new ArrayList<>();
            for (String keyword : keywords) {
                if (keyword != null && !keyword.trim().isEmpty()) {
                    validKeywords.add(keyword.trim());
                }
            }
            
            if (validKeywords.isEmpty()) {
                // safeSystem comes from the allowlist map — safe to use as an identifier
                Query query = entityManager.createNativeQuery("select " + safeSystem + ", description from " + safeSystem);
                return query.getResultList();
            }
            
            // Build parameterized query; safeSystem is from the allowlist map
            StringBuilder buf = new StringBuilder("select " + safeSystem + ", description from " + safeSystem + " where ");
            List<String> conditions = new ArrayList<>();
            
            for (int i = 0; i < validKeywords.size(); i++) {
                int paramIndex = i * 2 + 1;
                conditions.add("(" + safeSystem + " like ?" + paramIndex + " or description like ?" + (paramIndex + 1) + ")");
            }
            
            buf.append(String.join(" or ", conditions));
            
            Query query = entityManager.createNativeQuery(buf.toString());
            
            // Set parameters
            int paramIndex = 1;
            for (String keyword : validKeywords) {
                String likePattern = "%" + keyword + "%";
                query.setParameter(paramIndex++, likePattern);
                query.setParameter(paramIndex++, likePattern);
            }
            
            return query.getResultList();
        } catch (Exception e) {
            MiscUtils.getLogger().error("error", e);
            return new ArrayList<Object[]>();
        }

    }

    @NativeSql
    @Override
    public String getCodeDescription(String codingSystem, String code) {
        String desc = "";
        
        // Use the allowlist map to obtain a safe table/column name.
        String safeSystem = VALID_CODING_SYSTEMS.get(codingSystem);
        if (safeSystem == null) {
            MiscUtils.getLogger().warn("Invalid coding system name: {}", LogSanitizer.sanitize(codingSystem));
            return desc;
        }
        
        // safeSystem comes from the hardcoded allowlist map — safe to use as an identifier
        String sql = "select description from " + safeSystem + " where " + safeSystem + "=?1";
        try {
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter(1, code);
            desc = (String) query.getSingleResult();
        } catch (Exception e) {
            MiscUtils.getLogger().error("error executing query for codingSystem: {}", LogSanitizer.sanitize(codingSystem), e);
        }
        return desc;
    }

}
