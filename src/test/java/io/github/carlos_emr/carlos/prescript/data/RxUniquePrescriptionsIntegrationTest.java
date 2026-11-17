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
package io.github.carlos_emr.carlos.prescript.data;

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The unique medication list read back from persisted drug rows (#4420).
 *
 * <p>The unit test covers the selection rules on in-memory rows; this one proves they hold once
 * the rows have been through the database, whose converters and column types give back
 * timestamps, legacy zero values and empty strings rather than what was set.
 *
 * @since 2026-10-08
 */
@DisplayName("Unique prescriptions from persisted drug rows")
@Tag("integration")
@Tag("prescription")
@Tag("read")
@Transactional
class RxUniquePrescriptionsIntegrationTest extends CarlosTestBase {

    private static final int DEMO_NO = 4420;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private static Date daysFromToday(int days) {
        return Date.from(LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Drug persist(String name, String dosage, int startDaysAgo, int endInDays, String archivedReason) {
        Drug drug = new Drug();
        drug.setDemographicId(DEMO_NO);
        drug.setProviderNo("999001");
        drug.setBrandName(name);
        drug.setCustomName(name);
        drug.setGenericName("");
        drug.setRegionalIdentifier("");
        drug.setGcnSeqNo("0");
        drug.setDosage(dosage);
        drug.setUnit("mg");
        drug.setTakeMin(1);
        drug.setTakeMax(1);
        drug.setFreqCode("OD");
        drug.setDuration("30");
        drug.setDurUnit("D");
        drug.setQuantity("30");
        drug.setSpecial(name + " " + dosage + " mg once daily\nQty:30 Repeats:0");
        drug.setRxDate(daysFromToday(-startDaysAgo));
        drug.setWrittenDate(daysFromToday(-startDaysAgo));
        drug.setEndDate(daysFromToday(endInDays));
        drug.setCreateDate(new Date());
        drug.setPosition(0); // NOT NULL in the MariaDB schema; H2 has no default
        if (archivedReason != null) {
            drug.setArchived(true);
            drug.setArchivedReason(archivedReason);
            drug.setArchivedDate(new Date());
        }
        entityManager.persist(drug);
        return drug;
    }

    @Test
    @DisplayName("should list renewals once and concurrent regimens apart after a database round trip")
    void shouldListEachProductAndRegimenOnce_whenReadFromTheDatabase() {
        persist("Synthetic Renewed", "10", 120, -90, null);
        Drug renewal = persist("Synthetic Renewed", "10", 20, 10, null);
        persist("Synthetic Overlap", "10", 60, 30, null);
        Drug overlapNewest = persist("Synthetic Overlap", "10", 30, 60, null);
        Drug low = persist("Synthetic Regimen", "10", 60, 30, null);
        Drug high = persist("Synthetic Regimen", "20", 60, 30, null);
        persist("Synthetic ReRx", "10", 60, 30, Drug.REPRESCRIBED);
        Drug reRx = persist("Synthetic ReRx", "10", 1, 30, null);
        Drug stillTaken = persist("Synthetic Status", "10", 60, 30, null);
        persist("Synthetic Status", "10", 60, 30, Drug.OTHER);
        persist("Synthetic Deleted", "10", 1, 30, Drug.DELETED);
        entityManager.flush();
        entityManager.clear();

        RxPrescriptionData.Prescription[] unique = new RxPrescriptionData().getUniquePrescriptionsByPatient(DEMO_NO);

        assertThat(unique).extracting(RxPrescriptionData.Prescription::getDrugId).containsExactly(
                stillTaken.getId(), reRx.getId(), high.getId(), low.getId(), overlapNewest.getId(), renewal.getId());
        assertThat(unique).allSatisfy(prescription -> assertThat(prescription.isArchived()).isFalse());
    }
}
