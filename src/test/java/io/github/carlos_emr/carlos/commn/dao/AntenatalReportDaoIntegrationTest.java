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

import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Date;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

/** Fixed antenatal export ordering and cohort-selection compatibility checks. */
@Tag("integration")
@Tag("dao")
class AntenatalReportDaoIntegrationTest extends CarlosTestBase {
    @Autowired private DemographicExtDao extensions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    @Test
    void shouldKeepOneLatestRowAcrossCohort_forLegacyExport() {
        int firstPatient = patient();
        int secondPatient = patient();
        int outsidePatient = patient();
        DemographicExt older = extension(firstPatient, "report5549", "older", 1000);
        DemographicExt newer = extension(secondPatient, "report5549", "newer", 2000);
        extension(outsidePatient, "report5549", "outside cohort", 3000);
        extension(firstPatient, "another key", "outside key", 4000);
        entityManager.flush();
        assertThat(extensions.findReportValues("report5549", List.of(firstPatient, secondPatient), true))
                .extracting(DemographicExt::getId).containsExactly(newer.getId());
        assertThat(extensions.findReportValues("report5549", List.of(firstPatient, secondPatient), false))
                .extracting(DemographicExt::getId).containsExactly(older.getId(), newer.getId());
        assertThat(extensions.findReportValues("report5549", List.of(), true)).isEmpty();
        assertThat(extensions.findReportValues("report5549' or '1'='1", List.of(firstPatient, secondPatient), false)).isEmpty();
    }

    private int patient() {
        var patient = new io.github.carlos_emr.carlos.commn.model.Demographic();
        patient.setFirstName("Synthetic");
        patient.setLastName("Antenatal5549");
        patient.setSex("F");
        patient.setYearOfBirth("1990");
        patient.setMonthOfBirth("01");
        patient.setDateOfBirth("02");
        entityManager.persist(patient);
        entityManager.flush();
        return patient.getDemographicNo();
    }

    private DemographicExt extension(int demographic, String key, String value, long date) {
        DemographicExt row = new DemographicExt("5549", demographic, key, value);
        row.setDateCreated(new Date(date));
        entityManager.persist(row);
        return row;
    }
}
