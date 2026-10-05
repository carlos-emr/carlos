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

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unique-profile entries must retain distinct prescribing details and dated history.
 * @since 2026-10-05
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("Unique prescription content")
class RxUniquePrescriptionsUnitTest extends CarlosUnitTestBase {
    private DrugDao dao;
    private RxPrescriptionData data;

    @BeforeEach
    void setUp() {
        dao = mock(DrugDao.class);
        registerMock(DrugDao.class, dao);
        data = new RxPrescriptionData();
    }

    private Drug drug(int id, String name) {
        Drug drug = new Drug();
        drug.setId(id);
        drug.setDemographicId(1001);
        drug.setProviderNo("prescriber");
        drug.setBrandName(name);
        drug.setCustomName(name);
        drug.setGcnSeqNo("0");
        drug.setRxDate(Date.valueOf("2026-01-02"));
        drug.setWrittenDate(Date.valueOf("2026-01-02"));
        drug.setEndDate(Date.valueOf("2026-02-02"));
        drug.setSpecial(name + " 10 mg once daily");
        drug.setDosage("10");
        drug.setUnit("mg");
        drug.setTakeMin(1);
        drug.setTakeMax(1);
        drug.setFreqCode("OD");
        drug.setRoute("oral");
        drug.setQuantity("30");
        drug.setRepeat(0);
        drug.setPosition(id);
        return drug;
    }

    private RxPrescriptionData.Prescription[] unique(Drug... drugs) {
        when(dao.findByDemographicId(1001)).thenReturn(new ArrayList<>(List.of(drugs)));
        return data.getUniquePrescriptionsByPatient(1001);
    }

    @Test
    void shouldKeepNewestEquivalentEntry_whenDifferentDrugOccursBetweenDuplicates() {
        Drug oldest = drug(1, "Synthetic X");
        Drug middle = drug(2, "Synthetic Y");
        Drug newest = drug(3, "Synthetic X");
        // Row/linkage/render metadata do not change the prescribed treatment.
        oldest.setScriptNo(11);
        newest.setScriptNo(22);
        oldest.setCreateDate(Date.valueOf("2026-01-02"));
        newest.setCreateDate(Date.valueOf("2026-01-03"));
        newest.setLastUpdateDate(Date.valueOf("2026-01-04"));
        assertThat(unique(oldest, middle, newest)).extracting(RxPrescriptionData.Prescription::getDrugId)
                .containsExactly(3, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "12345"})
    void shouldCollapseEquivalentCustomAndCodedPrescriptions(String gcn) {
        Drug first = drug(1, "Synthetic X");
        Drug second = drug(2, "Synthetic X");
        first.setGcnSeqNo(gcn);
        second.setGcnSeqNo(gcn);
        assertThat(unique(first, second)).extracting(RxPrescriptionData.Prescription::getDrugId).containsExactly(2);
    }

    private static Stream<Arguments> clinicalChanges() {
        List<Arguments> changes = new ArrayList<>();
        addChange(changes, "dose", d -> d.setDosage("20"));
        addChange(changes, "dose range", d -> d.setTakeMax(2));
        addChange(changes, "frequency", d -> d.setFreqCode("BID"));
        addChange(changes, "route", d -> d.setRoute("topical"));
        addChange(changes, "form", d -> d.setDrugForm("liquid"));
        addChange(changes, "product identifier", d -> d.setRegionalIdentifier("different-product"));
        addChange(changes, "written instructions", d -> d.setSpecial("Synthetic X 20 mg once daily"));
        addChange(changes, "additional instructions", d -> d.setSpecialInstruction("Take with food"));
        addChange(changes, "quantity", d -> d.setQuantity("60"));
        addChange(changes, "repeats", d -> d.setRepeat(2));
        addChange(changes, "duration", d -> d.setDuration("14"));
        addChange(changes, "as needed", d -> d.setPrn(true));
        addChange(changes, "dispensing interval", d -> d.setDispenseInterval("7"));
        addChange(changes, "start date", d -> d.setRxDate(Date.valueOf("2026-01-03")));
        addChange(changes, "written date", d -> d.setWrittenDate(Date.valueOf("2026-01-03")));
        addChange(changes, "end date", d -> d.setEndDate(Date.valueOf("2026-03-02")));
        addChange(changes, "prescriber", d -> d.setProviderNo("other-prescriber"));
        addChange(changes, "archived status", d -> d.setArchived(true));
        addChange(changes, "past medication", d -> d.setPastMed(true));
        addChange(changes, "long term", d -> d.setLongTerm(true));
        addChange(changes, "clinical note", d -> d.setComment("Review before renewal"));
        return changes.stream();
    }

    private static void addChange(List<Arguments> changes, String name, Consumer<Drug> mutation) {
        for (String gcn : List.of("0", "12345")) changes.add(Arguments.of(name, gcn, mutation));
    }

    @ParameterizedTest(name = "{0}, GCN {1}")
    @MethodSource("clinicalChanges")
    void shouldKeepClinicallyDifferentEntries(String description, String gcn, Consumer<Drug> change) {
        Drug older = drug(1, "Synthetic X");
        Drug newer = drug(2, "Synthetic X");
        older.setGcnSeqNo(gcn);
        newer.setGcnSeqNo(gcn);
        change.accept(newer);
        assertThat(unique(older, newer)).as(description)
                .extracting(RxPrescriptionData.Prescription::getDrugId).containsExactly(2, 1);
    }

    @Test
    void shouldSkipDeletedRows_withoutHidingAnOlderActiveEntry() {
        Drug active = drug(1, "Synthetic X");
        Drug deleted = drug(2, "Synthetic X");
        deleted.setArchived(true);
        deleted.setArchivedReason(Drug.DELETED);
        assertThat(unique(active, deleted)).extracting(RxPrescriptionData.Prescription::getDrugId).containsExactly(1);
    }

    @Test
    void shouldRetainEntries_whenProductIdentityIsUnavailable() {
        Drug first = drug(1, null);
        Drug second = drug(2, null);
        first.setGcnSeqNo(null);
        second.setGcnSeqNo(null);
        assertThat(unique(first, second)).extracting(RxPrescriptionData.Prescription::getDrugId).containsExactly(2, 1);
    }

    @Test
    void shouldRetainAllEntries_inTheFullPrescriptionHistory() {
        Drug older = drug(1, "Synthetic X");
        Drug newer = drug(2, "Synthetic X");
        when(dao.findByDemographicIdOrderByPosition(1001, false)).thenReturn(List.of(older, newer));
        assertThat(data.getPrescriptionsByPatient(1001)).extracting(RxPrescriptionData.Prescription::getDrugId)
                .containsExactly(1, 2);
    }

    @Test
    void shouldNotReorderTheDaoResultList() {
        List<Drug> source = new ArrayList<>(List.of(drug(1, "X"), drug(2, "Y")));
        when(dao.findByDemographicId(1001)).thenReturn(source);
        assertThat(data.getUniquePrescriptionsByPatient(1001)).extracting(RxPrescriptionData.Prescription::getDrugId)
                .containsExactly(2, 1);
        assertThat(source).extracting(Drug::getId).containsExactly(1, 2);
    }

    @Test
    void shouldReturnEmpty_whenNoPrescriptionsExist() {
        assertThat(unique()).isEmpty();
    }
}
