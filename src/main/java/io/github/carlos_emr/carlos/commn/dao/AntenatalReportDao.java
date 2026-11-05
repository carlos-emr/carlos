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
import io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Fixed antenatal report reads for legacy forms without entity mappings. */
@Repository
@Transactional(readOnly = true)
public class AntenatalReportDao {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    public record PlannerRow(String edb, String patientName, String weight, String height, java.util.Set<String> checked) { }

    public PlannerRow findPlanner(int id, int patient) {
        @SuppressWarnings("unchecked")
        List<jakarta.persistence.Tuple> rows = entityManager.createNativeQuery(
                "SELECT * FROM formAR WHERE ID=?1 AND demographic_no=?2", jakarta.persistence.Tuple.class)
                .setParameter(1, id).setParameter(2, patient).getResultList();
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        @SuppressWarnings("unchecked")
        List<String> flags = entityManager.createNativeQuery("SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA=SCHEMA() AND TABLE_NAME='formAR' AND DATA_TYPE='tinyint'", String.class).getResultList();
        java.util.Set<String> checked = new java.util.HashSet<>();
        for (String flag : flags) {
            if ("1".equals(io.github.carlos_emr.carlos.util.NativeQueryValues.asString(row.get(flag)))) checked.add(flag);
        }
        return new PlannerRow((String) row.get("c_finalEDB"), (String) row.get("c_pName"),
                (String) row.get("pg1_wt"), (String) row.get("pg1_ht"), java.util.Set.copyOf(checked));
    }

    /**
     * Distinct demographic identifiers, retaining JDBC's historical NULL-to-zero integer conversion.
     *
     * @return the complete cohort within the internal identifier budget
     * @throws ConfiguredReportQuery.LimitExceededException if the cohort exceeds that budget
     */
    public List<Integer> birthSummaryDemographics() throws ConfiguredReportQuery.LimitExceededException {
        List<?> values = entityManager.createNativeQuery("select distinct demographic_no from formBCBirthSumMo")
                .setMaxResults(ConfiguredReportQuery.MAX_IDENTIFIER_ROWS + 1)
                .setHint("jakarta.persistence.query.timeout", ConfiguredReportQuery.TIMEOUT_SECONDS * 1000)
                .getResultList();
        if (values.size() > ConfiguredReportQuery.MAX_IDENTIFIER_ROWS) {
            throw new ConfiguredReportQuery.LimitExceededException("Report identifier budget exceeded; narrow the filters");
        }
        return values.stream().map(value -> value == null ? 0 : ((Number) value).intValue()).toList();
    }
}
