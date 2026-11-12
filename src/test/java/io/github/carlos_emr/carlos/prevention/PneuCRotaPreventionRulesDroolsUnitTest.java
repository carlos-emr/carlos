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
package io.github.carlos_emr.carlos.prevention;

import java.net.URL;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import io.github.carlos_emr.carlos.drools.DroolsCompilationException;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The infant next-dose reminders in {@code prevention.drl} for pneumococcal conjugate (rules "Pneu-C 2", "Pneu-C 3",
 * "Pneu-C 4", "Pneu-C 6") and rotavirus (rule "Rota 2"): the next dose is timed from the most recent earlier dose under
 * any of the names the rule counts, so a dose recorded under a product name (Pneu-C-13, Pneu-C-15, Rota-1, Rota-5)
 * raises the reminder just as the plain name does (#5009).
 *
 * <p>#5048, following the Canadian Immunization Guide (Rotavirus vaccine): the second rotavirus dose is due 4 weeks
 * after the first (28 calendar days, whatever time of day the dose was saved) and before 8 months of age, whether or
 * not a next date was set; a next date that was set still decides when the reminder starts ("Rota 2.1"). A refused
 * or "ineligible" record is not a dose.</p>
 *
 * @since 2026-10-18
 */
@Tag("unit")
@Tag("drools")
@Tag("prevention")
@DisplayName("Pneumococcal conjugate and rotavirus next-dose prevention rules")
class PneuCRotaPreventionRulesDroolsUnitTest {

    private static final String NEEDS_SECOND_PNEU_C = "Needs Second Pneu-C";
    private static final String NEEDS_THIRD_PNEU_C = "Needs Third Pneu-C";
    private static final String NEEDS_FOURTH_PNEU_C = "Needs Fourth Pneu-C";
    private static final String NEEDS_FIRST_ROTA = "Needs First Rota vaccine";
    private static final String NEEDS_SECOND_ROTA = "Needs Second Rota vaccine";
    private static final String NEEDS_THIRD_ROTA = "Needs Third Rota vaccine";

    private static KieBase kieBase;

    @BeforeAll
    static void loadPreventionDrl() throws DroolsCompilationException {
        URL url = PneuCRotaPreventionRulesDroolsUnitTest.class.getResource("/oscar/prevention/prevention.drl");
        assertThat(url).as("prevention.drl must be on the classpath").isNotNull();
        kieBase = DroolsHelper.loadFromUrl(url);
    }

    private static Date monthsAgo(int months) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MONTH, -months);
        return cal.getTime();
    }

    /** An infant of the given age with the given doses, each given the given number of months ago, oldest first. */
    private static Prevention infant(int ageInMonths, Object... typeAndMonthsAgo) {
        Prevention prev = new Prevention("F", monthsAgo(ageInMonths));
        for (int i = 0; i < typeAndMonthsAgo.length; i += 2) {
            prev.addPreventionItem(new PreventionItem((String) typeAndMonthsAgo[i], monthsAgo((Integer) typeAndMonthsAgo[i + 1])));
        }
        return prev;
    }

    /** A 5-month-old with one rotavirus dose two months ago, recorded under the given name. */
    private static Prevention infantWithOneRotaDose(String type, boolean nextDateSet, boolean neverRemind) {
        Prevention prev = new Prevention("F", monthsAgo(5));
        Date nextDate = nextDateSet ? monthsAgo(0) : null;
        prev.addPreventionItem(new PreventionItem(type, monthsAgo(2), neverRemind ? "1" : "0", nextDate));
        return prev;
    }

    private static Date daysFromToday(int days) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    /**
     * An infant of the given age with one Rota-1 dose given the given number of days ago, and a next date that many
     * days from today (negative: in the past), or none.
     */
    private static Prevention infantWithRota1DoseDaysAgo(int ageInMonths, int doseDaysAgo, Integer nextDateInDays) {
        Prevention prev = new Prevention("F", monthsAgo(ageInMonths));
        Date nextDate = nextDateInDays == null ? null : daysFromToday(nextDateInDays);
        prev.addPreventionItem(new PreventionItem("Rota-1", daysFromToday(-doseDaysAgo), "0", nextDate));
        return prev;
    }

    /** A 5-month-old whose one Rota-1 record, dated 60 days ago, is saved the way the prevention page saves it. */
    private static Prevention infantWithSavedRota1Record(boolean refused, boolean ineligible) {
        io.github.carlos_emr.carlos.commn.model.Prevention record = new io.github.carlos_emr.carlos.commn.model.Prevention();
        record.setPreventionType("Rota-1");
        record.setPreventionDate(daysFromToday(-60));
        if (refused) {
            record.setRefused(true);
        }
        if (ineligible) {
            record.setIneligible(true);
        }
        Prevention prev = new Prevention("F", monthsAgo(5));
        prev.addPreventionItem(new PreventionItem(record, PreventionDateDTO.from(record.getPreventionDate(), null)));
        return prev;
    }

    /**
     * A rotavirus record saved the way the prevention page saves it: the given type, dated that many days ago, a
     * dose given unless refused, with a next date that many days from today (negative: in the past), or none.
     */
    private static PreventionItem rotaRecord(String type, int daysAgo, boolean refused, Integer nextDateInDays) {
        io.github.carlos_emr.carlos.commn.model.Prevention record = new io.github.carlos_emr.carlos.commn.model.Prevention();
        record.setPreventionType(type);
        record.setPreventionDate(daysFromToday(-daysAgo));
        if (refused) {
            record.setRefused(true);
        }
        if (nextDateInDays != null) {
            record.setNextDate(daysFromToday(nextDateInDays));
        }
        return new PreventionItem(record, PreventionDateDTO.from(record.getPreventionDate(), null));
    }

    /** An infant of the given age in months with the given rotavirus records, oldest first. */
    private static Prevention infantWithRotaRecords(int ageInMonths, PreventionItem... records) {
        Prevention prev = new Prevention("F", monthsAgo(ageInMonths));
        for (PreventionItem record : records) {
            prev.addPreventionItem(record);
        }
        return prev;
    }

    private static int reminders(Prevention prev, String message) {
        KieSession session = kieBase.newKieSession();
        try {
            session.insert(prev);
            session.fireAllRules();
        } finally {
            session.dispose();
        }
        return Collections.frequency(prev.getWarnings(), message);
    }

    @Nested
    @DisplayName("Pneu-C 2: second dose before 12 months")
    class SecondDoseBeforeTwelveMonths {

        @Test
        void shouldRemind_whenTheFirstDoseWasRecordedAsPneuC13() {
            assertThat(reminders(infant(5, "Pneu-C-13", 3), NEEDS_SECOND_PNEU_C)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenTheFirstDoseWasRecordedAsPlainPneuC() {
            assertThat(reminders(infant(5, "Pneu-C", 3), NEEDS_SECOND_PNEU_C)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenThePneuC13DoseWasOneMonthAgo() {
            assertThat(reminders(infant(5, "Pneu-C-13", 1), NEEDS_SECOND_PNEU_C)).isZero();
        }
    }

    @Nested
    @DisplayName("Pneu-C 3: third dose before 12 months")
    class ThirdDoseBeforeTwelveMonths {

        @Test
        void shouldRemind_whenBothDosesWereRecordedAsPneuC13() {
            assertThat(reminders(infant(8, "Pneu-C-13", 5, "Pneu-C-13", 3), NEEDS_THIRD_PNEU_C)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenBothDosesWereRecordedAsPlainPneuC() {
            assertThat(reminders(infant(8, "Pneu-C", 5, "Pneu-C", 3), NEEDS_THIRD_PNEU_C)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenAnOldPlainDoseWasFollowedByAPneuC13DoseOneMonthAgo() {
            assertThat(reminders(infant(8, "Pneu-C", 5, "Pneu-C-13", 1), NEEDS_THIRD_PNEU_C)).isZero();
        }
    }

    @Nested
    @DisplayName("Pneu-C 4: fourth dose between 15 and 24 months")
    class FourthDose {

        @Test
        void shouldRemind_whenAllThreeDosesWereRecordedAsPneuC15() {
            assertThat(reminders(infant(18, "Pneu-C-15", 16, "Pneu-C-15", 14, "Pneu-C-15", 8), NEEDS_FOURTH_PNEU_C)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenAllThreeDosesWereRecordedAsPlainPneuC() {
            assertThat(reminders(infant(18, "Pneu-C", 16, "Pneu-C", 14, "Pneu-C", 8), NEEDS_FOURTH_PNEU_C)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Pneu-C 6: second dose between 14 and 24 months")
    class SecondDoseAfterFourteenMonths {

        @Test
        void shouldRemind_whenTheFirstDoseWasRecordedAsPneuC15() {
            assertThat(reminders(infant(16, "Pneu-C-15", 4), NEEDS_SECOND_PNEU_C)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenTheFirstDoseWasRecordedAsPlainPneuC() {
            assertThat(reminders(infant(16, "Pneu-C", 4), NEEDS_SECOND_PNEU_C)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Rota 2: second rotavirus dose")
    class SecondRotaDose {

        @Test
        void shouldRemind_whenTheFirstDoseWasRecordedAsRota1WithANextDate() {
            assertThat(reminders(infantWithOneRotaDose("Rota-1", true, false), NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenTheFirstDoseWasRecordedAsRota5WithANextDate() {
            assertThat(reminders(infantWithOneRotaDose("Rota-5", true, false), NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenTheFirstDoseWasRecordedAsPlainRotaWithANextDate() {
            assertThat(reminders(infantWithOneRotaDose("Rota", true, false), NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenTheRota1DoseHasNoNextDate() {
            assertThat(reminders(infantWithOneRotaDose("Rota-1", false, false), NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenAThreeMonthOldsFirstDoseIsFiveWeeksOld() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(3, 35, null), NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenTheFirstDoseIsThreeWeeksOld() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(3, 21, null), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldRemind_whenTheFirstDoseIsExactlyTwentyEightDaysOld() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(3, 28, null), NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenTheFirstDoseIsTwentySevenDaysOld() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(3, 27, null), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldRemind_whenTheFirstDoseWasSavedLateInTheDayTwentyEightDaysAgo() {
            Calendar late = Calendar.getInstance();
            late.setTime(daysFromToday(-28));
            late.set(Calendar.HOUR_OF_DAY, 23);
            late.set(Calendar.MINUTE, 59);
            Prevention prev = new Prevention("F", monthsAgo(3));
            prev.addPreventionItem(new PreventionItem("Rota-1", late.getTime(), "0", null));
            assertThat(reminders(prev, NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenTheOnlyRotaRecordIsARefusal() {
            assertThat(reminders(infantWithSavedRota1Record(true, false), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_whenTheOnlyRotaRecordSaysIneligible() {
            assertThat(reminders(infantWithSavedRota1Record(false, true), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldRemind_whenTheOnlyRotaRecordIsASavedDoseGiven() {
            assertThat(reminders(infantWithSavedRota1Record(false, false), NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenASetNextDatePassesBeforeDayTwentyEight() {
            // Owner's decision: never before the 4-week minimum interval, whatever the next date says.
            assertThat(reminders(infantWithRota1DoseDaysAgo(3, 14, -2), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_whenANextDateAtDayTwentyHasPassedButTheDoseIsTwentySevenDaysOld() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(3, 27, -7), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldRemindAtDayTwentyEight_whenTheNextDateWasDayTwenty() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(3, 28, -8), NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenTheNextDateHasPassedButTheInfantIsEightMonthsOld() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(8, 150, -3), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_whenTheDoseWithoutANextDateIsMarkedNeverRemind() {
            assertThat(reminders(infantWithOneRotaDose("Rota-1", false, true), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldRemind_whenASevenMonthOldStillNeedsTheSecondDose() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(7, 120, null), NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenTheInfantIsEightMonthsOld() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(8, 150, null), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_beforeTheNextDateSetOnTheFirstDose() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(5, 60, 14), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldRemind_onceTheNextDateSetOnTheFirstDoseHasPassed() {
            assertThat(reminders(infantWithRota1DoseDaysAgo(5, 60, -3), NEEDS_SECOND_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenTheRota1DoseIsMarkedNeverRemind() {
            assertThat(reminders(infantWithOneRotaDose("Rota-1", true, true), NEEDS_SECOND_ROTA)).isZero();
        }
    }

    /**
     * #5048, Canadian Immunization Guide, Rotavirus vaccine: Rot-1 (Rotarix) is 2 doses and Rot-5 (RotaTeq) 3; "If any
     * dose in the series was Rot-5 vaccine, a total of 3 doses." The third dose follows the second's 4-week minimum
     * interval and the before-8-months limit, and a next date set on the second dose decides when the reminder starts,
     * never before day 28, as for the second dose.
     */
    @Nested
    @DisplayName("Rota 3: third rotavirus dose after a Rot-5 dose")
    class ThirdRotaDose {

        @Test
        void shouldRemind_whenBothDosesWereRota5AndTheSecondIsTwentyEightDaysOld() {
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota-5", 60, false, null), rotaRecord("Rota-5", 28, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isEqualTo(1);
            assertThat(prev.getWarnings()).doesNotContain(NEEDS_SECOND_ROTA);
        }

        @Test
        void shouldNotRemind_whenTheSecondRota5DoseIsTwentySevenDaysOld() {
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota-5", 60, false, null), rotaRecord("Rota-5", 27, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isZero();
        }

        @Test
        void shouldRemind_whenOnlyTheFirstDoseWasRota5() {
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota-5", 60, false, null), rotaRecord("Rota-1", 30, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenOnlyTheSecondDoseWasRota5() {
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota-1", 60, false, null), rotaRecord("Rota-5", 30, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenBothDosesWereRota1() {
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota-1", 60, false, null), rotaRecord("Rota-1", 30, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isZero();
        }

        @Test
        void shouldRemind_whenBothDosesWereRecordedUnderThePlainName() {
            // An unknown product counts as possibly Rot-5 (owner's decision): only an all-Rot-1 series ends at two.
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota", 60, false, null), rotaRecord("Rota", 30, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenARota1DoseWasFollowedByAPlainRotaDose() {
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota-1", 60, false, null), rotaRecord("Rota", 30, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenAPlainRotaDoseWasFollowedByARota1Dose() {
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota", 60, false, null), rotaRecord("Rota-1", 30, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldRemind_whenASevenMonthOldStillNeedsTheThirdDose() {
            Prevention prev = infantWithRotaRecords(7, rotaRecord("Rota-5", 120, false, null), rotaRecord("Rota-5", 60, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenTheInfantIsEightMonthsOld() {
            Prevention prev = infantWithRotaRecords(8, rotaRecord("Rota-5", 150, false, null), rotaRecord("Rota-5", 90, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_afterTheThirdDose() {
            Prevention prev = infantWithRotaRecords(5, rotaRecord("Rota-5", 90, false, null), rotaRecord("Rota-5", 60, false, null),
                    rotaRecord("Rota-5", 30, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_whenOneOfTheTwoRota5RecordsIsARefusal() {
            // One dose given: not yet the third.
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota-5", 60, true, null), rotaRecord("Rota-5", 30, false, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_whenTheThirdDoseWasRefused() {
            Prevention prev = infantWithRotaRecords(5, rotaRecord("Rota-5", 90, false, null), rotaRecord("Rota-5", 60, false, null),
                    rotaRecord("Rota-5", 30, true, null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_whenTheSecondDoseIsMarkedNeverRemind() {
            Prevention prev = new Prevention("F", monthsAgo(4));
            prev.addPreventionItem(new PreventionItem("Rota-5", daysFromToday(-60), "0", null));
            prev.addPreventionItem(new PreventionItem("Rota-5", daysFromToday(-30), "1", null));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_beforeTheNextDateSetOnTheSecondDose() {
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota-5", 60, false, null), rotaRecord("Rota-5", 30, false, 7));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isZero();
        }

        @Test
        void shouldRemind_onceTheNextDateSetOnTheSecondDoseHasPassed() {
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota-5", 70, false, null), rotaRecord("Rota-5", 35, false, -2));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_whenThePassedNextDateComesBeforeDayTwentyEight() {
            Prevention prev = infantWithRotaRecords(4, rotaRecord("Rota-5", 60, false, null), rotaRecord("Rota-5", 20, false, -2));
            assertThat(reminders(prev, NEEDS_THIRD_ROTA)).isZero();
        }
    }

    /**
     * #5048, Canadian Immunization Guide, Rotavirus vaccine: "Vaccination should not be initiated in infants aged 15
     * weeks or older." The first-dose reminder runs from 2 months of age and stops at 15 weeks (105 days).
     */
    @Nested
    @DisplayName("Rota 1: first rotavirus dose before 15 weeks")
    class FirstRotaDose {

        private Prevention infantBornDaysAgo(int days) {
            return new Prevention("F", daysFromToday(-days));
        }

        @Test
        void shouldRemind_atFifteenWeeksLessOneDay() {
            assertThat(reminders(infantBornDaysAgo(104), NEEDS_FIRST_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_atExactlyFifteenWeeks() {
            assertThat(reminders(infantBornDaysAgo(105), NEEDS_FIRST_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_atFifteenWeeksAndOneDay() {
            assertThat(reminders(infantBornDaysAgo(106), NEEDS_FIRST_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_atFourMonths() {
            assertThat(reminders(infant(4), NEEDS_FIRST_ROTA)).isZero();
        }

        @Test
        void shouldRemind_atTwoMonths() {
            assertThat(reminders(infant(2), NEEDS_FIRST_ROTA)).isEqualTo(1);
        }

        @Test
        void shouldNotRemind_beforeTwoMonths() {
            assertThat(reminders(infantBornDaysAgo(49), NEEDS_FIRST_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_whenADoseIsOnFile() {
            assertThat(reminders(infantWithRotaRecords(3, rotaRecord("Rota-1", 20, false, null)), NEEDS_FIRST_ROTA)).isZero();
        }
    }
}
