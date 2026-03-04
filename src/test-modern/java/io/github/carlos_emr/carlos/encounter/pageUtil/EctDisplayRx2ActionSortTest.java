package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Prescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the medication stable-partition logic in {@link EctDisplayRx2Action}.
 *
 * <p>Verifies that active medications (current and not archived, or long-term)
 * are placed above non-active medications, and that relative order within
 * each group is preserved.</p>
 *
 * @since 2026-02-25
 */
@DisplayName("EctDisplayRx2Action - Medication Sort")
@Tag("unit")
@Tag("fast")
@Tag("encounter")
public class EctDisplayRx2ActionSortTest {

    private static Date daysFromNow(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, days);
        return cal.getTime();
    }

    private static Prescription activeDrug(int id) {
        Prescription p = new Prescription(id, "1", 1);
        p.setEndDate(daysFromNow(90));
        return p;
    }

    private static Prescription expiredDrug(int id) {
        Prescription p = new Prescription(id, "1", 1);
        p.setEndDate(daysFromNow(-30));
        return p;
    }

    private static Prescription longTermDrug(int id) {
        Prescription p = new Prescription(id, "1", 1);
        p.setLongTerm(true);
        return p;
    }

    private static Prescription archivedDrug(int id) {
        Prescription p = new Prescription(id, "1", 1);
        p.setEndDate(daysFromNow(90));
        p.setArchived("true");
        return p;
    }

    @Test
    @DisplayName("Active medications should appear before expired ones")
    void shouldSortActiveDrugsFirst_whenMixedWithExpired() {
        Prescription expired1 = expiredDrug(3);
        Prescription active1 = activeDrug(2);
        Prescription expired2 = expiredDrug(1);

        List<Prescription> drugs = new ArrayList<>(List.of(expired1, active1, expired2));
        EctDisplayRx2Action.stablePartitionActiveFirst(drugs);

        assertThat(drugs).containsExactly(active1, expired1, expired2);
    }

    @Test
    @DisplayName("Long-term medications should appear at the top")
    void shouldSortLongTermDrugsFirst_whenMixedWithExpired() {
        Prescription expired = expiredDrug(3);
        Prescription longTerm = longTermDrug(2);

        List<Prescription> drugs = new ArrayList<>(List.of(expired, longTerm));
        EctDisplayRx2Action.stablePartitionActiveFirst(drugs);

        assertThat(drugs).containsExactly(longTerm, expired);
    }

    @Test
    @DisplayName("Relative order within each group should be preserved")
    void shouldPreserveRelativeOrder_whenMultipleDrugsInSameGroup() {
        Prescription active1 = activeDrug(10);
        Prescription active2 = activeDrug(5);
        Prescription expired1 = expiredDrug(8);
        Prescription expired2 = expiredDrug(3);

        List<Prescription> drugs = new ArrayList<>(List.of(active1, expired1, active2, expired2));
        EctDisplayRx2Action.stablePartitionActiveFirst(drugs);

        assertThat(drugs).containsExactly(active1, active2, expired1, expired2);
    }

    @Test
    @DisplayName("All active drugs should remain in original order")
    void shouldNotReorder_whenAllDrugsAreActive() {
        Prescription a1 = activeDrug(3);
        Prescription a2 = activeDrug(2);
        Prescription a3 = activeDrug(1);

        List<Prescription> drugs = new ArrayList<>(List.of(a1, a2, a3));
        EctDisplayRx2Action.stablePartitionActiveFirst(drugs);

        assertThat(drugs).containsExactly(a1, a2, a3);
    }

    @Test
    @DisplayName("All expired drugs should remain in original order")
    void shouldNotReorder_whenAllDrugsAreExpired() {
        Prescription e1 = expiredDrug(3);
        Prescription e2 = expiredDrug(2);
        Prescription e3 = expiredDrug(1);

        List<Prescription> drugs = new ArrayList<>(List.of(e1, e2, e3));
        EctDisplayRx2Action.stablePartitionActiveFirst(drugs);

        assertThat(drugs).containsExactly(e1, e2, e3);
    }

    @Test
    @DisplayName("Empty list should not throw")
    void shouldHandleEmptyList_whenNoDrugsPresent() {
        List<Prescription> drugs = new ArrayList<>();
        EctDisplayRx2Action.stablePartitionActiveFirst(drugs);

        assertThat(drugs).isEmpty();
    }

    @Test
    @DisplayName("Archived drugs should sort below active drugs")
    void shouldSortArchivedBelow_whenMixedWithActive() {
        Prescription archived = archivedDrug(3);
        Prescription active = activeDrug(2);

        List<Prescription> drugs = new ArrayList<>(List.of(archived, active));
        EctDisplayRx2Action.stablePartitionActiveFirst(drugs);

        assertThat(drugs).containsExactly(active, archived);
    }

    @Test
    @DisplayName("Archived long-term drug should be classified as active per isActiveDrug semantics")
    void shouldClassifyAsActive_whenDrugIsArchivedAndLongTerm() {
        Prescription archivedLongTerm = longTermDrug(1);
        archivedLongTerm.setArchived("true");
        Prescription expired = expiredDrug(2);

        List<Prescription> drugs = new ArrayList<>(List.of(expired, archivedLongTerm));
        EctDisplayRx2Action.stablePartitionActiveFirst(drugs);

        // Archived long-term drugs are "active" per isActiveDrug() — downstream
        // filtering in getInfo() handles their removal from display.
        assertThat(drugs).containsExactly(archivedLongTerm, expired);
    }

    @Test
    @DisplayName("Mixed active, long-term, and expired should group correctly")
    void shouldGroupCorrectly_whenAllTypesPresent() {
        Prescription expired = expiredDrug(5);
        Prescription longTerm = longTermDrug(4);
        Prescription active = activeDrug(3);
        Prescription archived = archivedDrug(2);

        List<Prescription> drugs = new ArrayList<>(List.of(expired, longTerm, active, archived));
        EctDisplayRx2Action.stablePartitionActiveFirst(drugs);

        // longTerm and active are both "active" — should come first, in original relative order.
        // expired and archived are both "inactive" — should follow, also in original order.
        assertThat(drugs).containsExactly(longTerm, active, expired, archived);
    }
}
