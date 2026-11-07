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

import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

/** Archive copies made by measurement deletion and HL7 replacement retain the original value. */
@Tag("integration")
@Tag("dao")
class MeasurementsDeletedDaoIntegrationTest extends CarlosTestBase {
    @Autowired private MeasurementDao measurementDao;
    @Autowired private MeasurementsDeletedDao measurementsDeletedDao;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    @Test
    void shouldPreserveLongMeasurementValue_whenAnArchiveCopyIsPersisted() {
        var source = new Measurement();
        source.setDemographicId(974233);
        source.setType("WT");
        source.setProviderNo("999998");
        source.setCreateDate(new Date());
        source.setDateObserved(new Date());
        String value = "x".repeat(300);
        source.setDataField(value);
        measurementDao.persist(source);
        entityManager.flush();
        // Both EctDeleteData2Action and Hl7textResultsData construct this archive
        // entity directly from the native measurement before deleting the source.
        var archived = new MeasurementsDeleted(source);
        measurementsDeletedDao.persist(archived);
        entityManager.flush();
        Integer archiveId = archived.getId();
        entityManager.clear();
        assertThat(measurementsDeletedDao.find(archiveId).getDataField()).isEqualTo(value);
    }
}
