package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Prescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the medication sort logic in {@link EctDisplayRx2Action}.
 *
 * <p>Verifies that active medications (current and not archived, or long-term)
 * are sorted above non-active medications, and that relative order within
 * each group is preserved by the stable sort.</p>
 *
 * @since 2026-02-25
 */
@DisplayName("EctDisplayRx2Action - Medication Sort")
@Tag("unit")
@Tag("fast")
@Tag("encounter")
public class EctDisplayRx2ActionSortTest {

    private static final Comparator<Prescription> ACTIVE_FIRST =
        EctDisplayRx2Action.ACTIVE_FIRST;

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
        drugs.sort(ACTIVE_FIRST);

        assertThat(drugs).containsExactly(active1, expired1, expired2);
    }

    @Test
    @DisplayName("Long-term medications should appear at the top")
    void shouldSortLongTermDrugsFirst_whenMixedWithExpired() {
        Prescription expired = expiredDrug(3);
        Prescription longTerm = longTermDrug(2);

        List<Prescription> drugs = new ArrayList<>(List.of(expired, longTerm));
        drugs.sort(ACTIVE_FIRST);

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
        drugs.sort(ACTIVE_FIRST);

        assertThat(drugs).containsExactly(active1, active2, expired1, expired2);
    }

    @Test
    @DisplayName("All active drugs should remain in original order")
    void shouldNotReorder_whenAllDrugsAreActive() {
        Prescription a1 = activeDrug(3);
        Prescription a2 = activeDrug(2);
        Prescription a3 = activeDrug(1);

        List<Prescription> drugs = new ArrayList<>(List.of(a1, a2, a3));
        drugs.sort(ACTIVE_FIRST);

        assertThat(drugs).containsExactly(a1, a2, a3);
    }

    @Test
    @DisplayName("All expired drugs should remain in original order")
    void shouldNotReorder_whenAllDrugsAreExpired() {
        Prescription e1 = expiredDrug(3);
        Prescription e2 = expiredDrug(2);
        Prescription e3 = expiredDrug(1);

        List<Prescription> drugs = new ArrayList<>(List.of(e1, e2, e3));
        drugs.sort(ACTIVE_FIRST);

        assertThat(drugs).containsExactly(e1, e2, e3);
    }

    @Test
    @DisplayName("Empty list should not throw")
    void shouldHandleEmptyList_whenNoDrugsPresent() {
        List<Prescription> drugs = new ArrayList<>();
        drugs.sort(ACTIVE_FIRST);

        assertThat(drugs).isEmpty();
    }

    @Test
    @DisplayName("Archived drugs should sort below active drugs")
    void shouldSortArchivedBelow_whenMixedWithActive() {
        Prescription archived = archivedDrug(3);
        Prescription active = activeDrug(2);

        List<Prescription> drugs = new ArrayList<>(List.of(archived, active));
        drugs.sort(ACTIVE_FIRST);

        assertThat(drugs).containsExactly(active, archived);
    }

    @Test
    @DisplayName("Mixed active, long-term, and expired should group correctly")
    void shouldGroupCorrectly_whenAllTypesPresent() {
        Prescription expired = expiredDrug(5);
        Prescription longTerm = longTermDrug(4);
        Prescription active = activeDrug(3);
        Prescription archived = archivedDrug(2);

        List<Prescription> drugs = new ArrayList<>(List.of(expired, longTerm, active, archived));
        drugs.sort(ACTIVE_FIRST);

        // longTerm and active are both "active" — should come first, in original relative order
        assertThat(drugs.indexOf(longTerm)).isLessThan(drugs.indexOf(expired));
        assertThat(drugs.indexOf(active)).isLessThan(drugs.indexOf(expired));
        assertThat(drugs.indexOf(longTerm)).isLessThan(drugs.indexOf(archived));
        assertThat(drugs.indexOf(active)).isLessThan(drugs.indexOf(archived));
        // Relative order within active group preserved: longTerm came before active in input
        assertThat(drugs.indexOf(longTerm)).isLessThan(drugs.indexOf(active));
    }
}
