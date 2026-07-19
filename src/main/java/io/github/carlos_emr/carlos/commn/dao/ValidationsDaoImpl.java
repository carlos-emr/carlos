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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.Validations;
import org.springframework.stereotype.Repository;

@Repository
public class ValidationsDaoImpl extends AbstractDaoImpl<Validations> implements ValidationsDao {

    public ValidationsDaoImpl() {
        super(Validations.class);
    }

    @SuppressWarnings("unchecked")
    public List<Validations> findAll() {
        Query query = createQuery("x", null);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Validations> findByAll(String regularExpParam, Double minValueParam, Double maxValueParam,
                                       Integer minLengthParam, Integer maxLengthParam, Boolean isNumericParam,
                                       Boolean isDateParam) {

        StringBuilder buf = new StringBuilder();
        Map<String, Object> params = new HashMap<String, Object>();
        for (Object[] i : new Object[][]{{"regularExp", regularExpParam}, {"minValue", minValueParam}, {"maxValue", maxValueParam}, {"minLength", minLengthParam}, {"maxLength", maxLengthParam}, {"isNumeric", isNumericParam}, {"isDate", isDateParam}}) {
            String name = (String) i[0];
            Object value = i[1];
            if (buf.length() > 0) {
                buf.append(" AND ");
            }
            buf.append("v.").append(name).append(" = :").append(name);
            params.put(name, value);
        }

        if (buf.length() > 0) {
            buf.insert(0, " WHERE ");
        }
        buf.insert(0, "FROM Validations v");

        Query query = entityManager.createQuery(buf.toString());
        for (Entry<String, Object> e : params.entrySet()) {
            query.setParameter(e.getKey(), e.getValue());
        }
        return query.getResultList();

    }

    @SuppressWarnings("unchecked")
    public List<Validations> findByName(String name) {
        Query query = createQuery("v", "v.name = ?1");
        query.setParameter(1, name);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> findValidationsBy(Integer demo, String type, Integer validationId) {
        String sql = "FROM Validations v, Measurement m " +
                "WHERE " +
                "m.demographicId = ?1 " +
                "AND m.type = ?2 " +
                "AND v.id = ?3 " +
                "GROUP BY m.id " +
                "ORDER BY m.dateObserved DESC, m.createDate DESC";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demo);
        query.setParameter(2, type);
        query.setParameter(3, validationId);
        return query.getResultList();

    }
}
