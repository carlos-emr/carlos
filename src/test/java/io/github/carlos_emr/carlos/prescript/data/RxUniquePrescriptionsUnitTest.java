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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The unique medication list: one entry per product and regimen (#4270, #4420).
 *
 * <p>Clinically different regimens of one product stay apart, while a renewal that differs only
 * in its dates, dispensing amounts, prescriber or notes replaces the earlier copy.
 *
 * @since 2026-10-05
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("Unique prescription content")
class RxUniquePrescriptionsUnitTest extends CarlosUnitTestBase {
    private static final LocalDate TODAY = LocalDate.now();

    private DrugDao dao;
    private RxPrescriptionData data;

    @BeforeEach
    void setUp() {
        dao = mock(DrugDao.class);
        registerMock(DrugDao.class, dao);
        data = new RxPrescriptionData();
    }

    private static Date daysFromToday(int days) {
        return Date.valueOf(TODAY.plusDays(days));
    }

    /** A current custom-name prescription with parsed (structured) instructions. */
    private Drug drug(int id, String name) {
        Drug drug = new Drug();
        drug.setId(id);
        drug.setDemographicId(1001);
        drug.setProviderNo("prescriber");
        drug.setBrandName(name);
        drug.setCustomName(name);
        drug.setGcnSeqNo("0");
        drug.setRxDate(daysFromToday(-60));
        drug.setWrittenDate(daysFromToday(-60));
        drug.setEndDate(daysFromToday(30));
        drug.setSpecial(name + "\n1 tab once daily\nQty:30 Repeats:0");
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

    private List<Integer> uniqueIds(Drug... drugs) {
        return Stream.of(unique(drugs)).map(RxPrescriptionData.Prescription::getDrugId).toList();
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
        assertThat(uniqueIds(oldest, middle, newest)).containsExactly(3, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "12345"})
    void shouldCollapseEquivalentCustomAndCodedPrescriptions_forEachProductCoding(String gcn) {
        Drug first = drug(1, "Synthetic X");
        Drug second = drug(2, "Synthetic X");
        first.setGcnSeqNo(gcn);
        second.setGcnSeqNo(gcn);
        assertThat(uniqueIds(first, second)).containsExactly(2);
    }

    private static Stream<Arguments> regimenChanges() {
        List<Arguments> changes = new ArrayList<>();
        addChange(changes, "dose", d -> d.setDosage("20"));
        addChange(changes, "dose unit", d -> d.setUnit("mcg"));
        addChange(changes, "dose range", d -> d.setTakeMax(2));
        addChange(changes, "amount per dose", d -> d.setTakeMin(2));
        addChange(changes, "frequency", d -> d.setFreqCode("BID"));
        addChange(changes, "route", d -> d.setRoute("topical"));
        addChange(changes, "method", d -> d.setMethod("apply"));
        addChange(changes, "form", d -> d.setDrugForm("liquid"));
        addChange(changes, "dispensed unit", d -> d.setUnitName("mL"));
        addChange(changes, "product identifier", d -> d.setRegionalIdentifier("different-product"));
        addChange(changes, "generic name", d -> d.setGenericName("different-ingredient"));
        addChange(changes, "as needed", d -> d.setPrn(true));
        return changes.stream();
    }

    private static Stream<Arguments> historyChanges() {
        List<Arguments> changes = new ArrayList<>();
        addChange(changes, "start date", d -> d.setRxDate(daysFromToday(-30)));
        addChange(changes, "written date", d -> d.setWrittenDate(daysFromToday(-30)));
        addChange(changes, "end date", d -> d.setEndDate(daysFromToday(60)));
        addChange(changes, "last refill date", d -> d.setLastRefillDate(daysFromToday(-5)));
        addChange(changes, "quantity", d -> d.setQuantity("60"));
        addChange(changes, "repeats", d -> d.setRepeat(2));
        addChange(changes, "duration", d -> d.setDuration("14"));
        addChange(changes, "dispensing interval", d -> d.setDispenseInterval("7"));
        addChange(changes, "prescriber", d -> d.setProviderNo("other-prescriber"));
        addChange(changes, "pharmacy", d -> d.setPharmacyId(7));
        addChange(changes, "clinical note", d -> d.setComment("Review before renewal"));
        addChange(changes, "past medication", d -> d.setPastMed(true));
        addChange(changes, "long term", d -> d.setLongTerm(true));
        addChange(changes, "additional instructions", d -> d.setSpecialInstruction("Take with food"));
        // Parsed instructions: the structured fields above carry the regimen, not the wording.
        addChange(changes, "reworded parsed instructions", d -> d.setSpecial("Synthetic X\n1 tab daily x 90 days\nQty:90 Repeats:1"));
        return changes.stream();
    }

    private static void addChange(List<Arguments> changes, String name, Consumer<Drug> mutation) {
        for (String gcn : List.of("0", "12345")) changes.add(Arguments.of(name, gcn, mutation));
    }

    @ParameterizedTest(name = "{0}, GCN {1}")
    @MethodSource("regimenChanges")
    void shouldKeepBothEntries_whenTheRegimenDiffers(String description, String gcn, Consumer<Drug> change) {
        Drug older = drug(1, "Synthetic X");
        Drug newer = drug(2, "Synthetic X");
        older.setGcnSeqNo(gcn);
        newer.setGcnSeqNo(gcn);
        change.accept(newer);
        assertThat(uniqueIds(older, newer)).as(description).containsExactly(2, 1);
    }

    @ParameterizedTest(name = "{0}, GCN {1}")
    @MethodSource("historyChanges")
    void shouldListTheRenewalOnce_whenOnlyHistoryDiffers(String description, String gcn, Consumer<Drug> change) {
        Drug older = drug(1, "Synthetic X");
        Drug newer = drug(2, "Synthetic X");
        older.setGcnSeqNo(gcn);
        newer.setGcnSeqNo(gcn);
        change.accept(newer);
        assertThat(uniqueIds(older, newer)).as(description).containsExactly(2);
    }

    @Test
    void shouldListTheRenewalOnce_whenTheEarlierCopyHasExpired() {
        Drug expired = drug(1, "Synthetic X");
        expired.setRxDate(daysFromToday(-120));
        expired.setEndDate(daysFromToday(-90));
        Drug renewal = drug(2, "Synthetic X");
        assertThat(uniqueIds(expired, renewal)).containsExactly(2);
    }

    @Test
    void shouldCompareFreeTextInstructions_withoutTheQuantityLine() {
        Drug first = freeText(1, "Synthetic X\nApply thinly to rash twice daily\nQty:1 tube Repeats:0");
        Drug renewal = freeText(2, "Synthetic X\nApply thinly to rash twice daily\nMitte:2 tube Repeats:3");
        Drug other = freeText(3, "Synthetic X\nApply thickly to scalp at night\nQty:1 tube Repeats:0");
        assertThat(uniqueIds(first, renewal, other)).containsExactly(3, 2);
    }

    private Drug freeText(int id, String special) {
        Drug drug = drug(id, "Synthetic X");
        drug.setCustomInstructions(true);
        drug.setTakeMin(0);
        drug.setTakeMax(0);
        drug.setFreqCode(null);
        drug.setSpecial(special);
        return drug;
    }

    @Test
    void shouldTreatBlankAndNullTextAlike_whenComparingEntries() {
        Drug older = drug(1, "Synthetic X");
        Drug newer = drug(2, "Synthetic X");
        older.setRoute(null);
        newer.setRoute("  ");
        older.setAtc("");
        assertThat(uniqueIds(older, newer)).containsExactly(2);
    }

    @Test
    void shouldListTheCurrentCopy_whenANewerEquivalentCopyIsArchived() {
        Drug current = drug(1, "Synthetic X");
        Drug archived = drug(2, "Synthetic X");
        archived.setArchived(true);
        archived.setArchivedReason(Drug.OTHER);
        assertThat(uniqueIds(current, archived)).containsExactly(1);
    }

    @Test
    void shouldListTheReprescribedCopy_afterReRxArchivesTheSource() {
        Drug source = drug(1, "Synthetic X");
        source.setArchived(true);
        source.setArchivedReason(Drug.REPRESCRIBED);
        Drug reRx = drug(2, "Synthetic X");
        assertThat(uniqueIds(source, reRx)).containsExactly(2);
    }

    @Test
    void shouldListTheDiscontinuedRenewal_ratherThanAnExpiredPredecessor() {
        Drug expired = drug(1, "Synthetic X");
        expired.setEndDate(daysFromToday(-30));
        Drug discontinued = drug(2, "Synthetic X");
        discontinued.setArchived(true);
        discontinued.setArchivedReason(Drug.NO_LONGER_NECESSARY);
        RxPrescriptionData.Prescription[] prescriptions = unique(expired, discontinued);
        assertThat(prescriptions).extracting(RxPrescriptionData.Prescription::getDrugId).containsExactly(2);
        assertThat(prescriptions[0].isArchived()).isTrue();
    }

    @Test
    void shouldListALongTermCopy_ratherThanANewerExpiredCopy() {
        Drug longTerm = drug(1, "Synthetic X");
        longTerm.setLongTerm(true);
        longTerm.setEndDate(daysFromToday(-30));
        Drug expired = drug(2, "Synthetic X");
        expired.setEndDate(daysFromToday(-10));
        assertThat(uniqueIds(longTerm, expired)).containsExactly(1);
    }

    @Test
    void shouldSkipDeletedRows_withoutHidingAnOlderActiveEntry() {
        Drug active = drug(1, "Synthetic X");
        Drug deleted = drug(2, "Synthetic X");
        deleted.setArchived(true);
        deleted.setArchivedReason(Drug.DELETED);
        assertThat(uniqueIds(active, deleted)).containsExactly(1);
    }

    @Test
    void shouldIgnoreDisplayVisibility_whenChoosingTheNewestEquivalentEntry() {
        Drug older = drug(1, "Synthetic X");
        Drug newer = drug(2, "Synthetic X");
        older.setHideFromDrugProfile(false);
        newer.setHideFromDrugProfile(true);
        newer.setHideFromCpp(true);
        RxPrescriptionData.Prescription[] prescriptions = unique(older, newer);
        assertThat(prescriptions).extracting(RxPrescriptionData.Prescription::getDrugId).containsExactly(2);
        assertThat(prescriptions[0].isHideCpp()).isTrue();
    }

    @Test
    void shouldRetainEntries_whenProductIdentityIsUnavailable() {
        Drug first = drug(1, null);
        Drug second = drug(2, null);
        first.setGcnSeqNo(null);
        second.setGcnSeqNo(null);
        assertThat(uniqueIds(first, second)).containsExactly(2, 1);
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
    void shouldNotReorderTheDaoResultList_whenSelectingEntries() {
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
