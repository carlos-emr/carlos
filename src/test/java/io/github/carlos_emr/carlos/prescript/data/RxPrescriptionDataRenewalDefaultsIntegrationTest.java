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
 * What a renewal (ReRx) and the Long Term toggle keep from a persisted drug, after a database
 * round trip (#5056).
 *
 * <p>The unit test covers the in-memory copy; this one reads the source from a real drugs row
 * (where a legacy row may hold NULL in {@code non_authoritative}) and saves the copy as a new
 * row the way the prescription save does, then reads that row back. A renewal is a new original
 * prescription, so it starts with the Non-Authoritative tick clear and no Comment (clinical
 * guidance on #5169). The Long Term toggle saves the same prescription again, so it keeps both.
 *
 * @since 2026-10-22
 */
@DisplayName("Renewal defaults and the Long Term toggle on persisted drugs")
@Tag("integration")
@Tag("prescription")
@Tag("create")
@Transactional
class RxPrescriptionDataRenewalDefaultsIntegrationTest extends CarlosTestBase {

    private static final int DEMO_NO = 5056;
    private static final String PROVIDER_NO = "999001";
    private static final String COMMENT = "FAKE take with food, pharmacy to blister pack";

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Drug persistSource(Boolean nonAuthoritative) {
        Date monthAgo = Date.from(LocalDate.now().minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Drug drug = new Drug();
        drug.setDemographicId(DEMO_NO);
        drug.setProviderNo(PROVIDER_NO);
        drug.setBrandName("FAKE Synthetic ReRx");
        drug.setCustomName("FAKE Synthetic ReRx");
        drug.setGenericName("");
        drug.setRegionalIdentifier("");
        drug.setGcnSeqNo("0");
        drug.setDosage("10");
        drug.setUnit("mg");
        drug.setTakeMin(1);
        drug.setTakeMax(1);
        drug.setFreqCode("OD");
        drug.setDuration("30");
        drug.setDurUnit("D");
        drug.setQuantity("30");
        drug.setSpecial("FAKE Synthetic ReRx 10 mg once daily\nQty:30 Repeats:0");
        drug.setRxDate(monthAgo);
        drug.setWrittenDate(monthAgo);
        drug.setEndDate(new Date());
        drug.setScriptNo(5056);
        drug.setPosition(0); // NOT NULL in the MariaDB schema; H2 has no default
        drug.setComment(COMMENT);
        drug.setNonAuthoritative(nonAuthoritative);
        entityManager.persist(drug);
        entityManager.flush();
        entityManager.clear();
        return drug;
    }

    private Drug reload(int drugId) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Drug.class, drugId);
    }

    @Test
    @DisplayName("should save the renewal authoritative and without a comment, leaving the source as it was")
    void shouldSaveAuthoritativeRenewalWithoutComment_whenRePrescribingPersistedDrug() {
        Drug source = persistSource(true);
        RxPrescriptionData data = new RxPrescriptionData();

        RxPrescriptionData.Prescription staged =
                data.newPrescription(PROVIDER_NO, DEMO_NO, data.getPrescription(source.getId()));
        assertThat(staged.Save("5057")).isTrue();

        Drug saved = reload(staged.getDrugId());
        assertThat(saved.getId()).isNotEqualTo(source.getId());
        assertThat(saved.getComment()).isNull();
        assertThat(saved.isNonAuthoritative()).isFalse();
        Drug unchanged = entityManager.find(Drug.class, source.getId());
        assertThat(unchanged.getComment()).isEqualTo(COMMENT);
        assertThat(unchanged.isNonAuthoritative()).isTrue();
    }

    @Test
    @DisplayName("should read a NULL legacy non_authoritative as unticked")
    void shouldLoadUnticked_whenSourceNonAuthoritativeIsNull() {
        Drug source = persistSource(false);
        entityManager.createNativeQuery("UPDATE drugs SET non_authoritative = NULL WHERE drugid = ?1")
                .setParameter(1, source.getId())
                .executeUpdate();
        entityManager.clear();
        RxPrescriptionData data = new RxPrescriptionData();

        RxPrescriptionData.Prescription loaded = data.getPrescriptionIfPresent(source.getId());

        assertThat(loaded.isNonAuthoritative()).isFalse();
        assertThat(loaded.getComment()).isEqualTo(COMMENT);
    }

    @Test
    @DisplayName("should keep the Non-Authoritative tick and comment when the Long Term toggle saves a new row")
    void shouldKeepNonAuthoritative_whenLongTermToggleSavesNewRow() {
        Drug source = persistSource(true);

        RxPrescriptionData.Prescription loaded = new RxPrescriptionData().getPrescriptionIfPresent(source.getId());
        loaded.setLongTerm(true);
        loaded.setShortTerm(false);
        assertThat(loaded.Save(loaded.getScript_no())).isTrue();

        Drug saved = reload(loaded.getDrugId());
        assertThat(saved.getId()).isNotEqualTo(source.getId());
        assertThat(saved.getLongTerm()).isTrue();
        assertThat(saved.isNonAuthoritative()).isTrue();
        assertThat(saved.getComment()).isEqualTo(COMMENT);
    }
}
