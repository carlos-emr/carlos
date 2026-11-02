/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Fixed antenatal report reads for legacy forms without entity mappings. */
@Repository
@Transactional(readOnly = true)
public class AntenatalReportDao {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /** Distinct demographic identifiers, retaining JDBC's historical NULL-to-zero integer conversion. */
    public List<Integer> birthSummaryDemographics() {
        List<?> values = entityManager.createNativeQuery("select distinct demographic_no from formBCBirthSumMo").getResultList();
        return values.stream().map(value -> value == null ? 0 : ((Number) value).intValue()).toList();
    }
}
