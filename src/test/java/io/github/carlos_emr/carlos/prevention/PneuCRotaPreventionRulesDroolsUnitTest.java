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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The infant next-dose reminders in {@code prevention.drl} for pneumococcal conjugate (rules "Pneu-C 2", "Pneu-C 3",
 * "Pneu-C 4", "Pneu-C 6") and rotavirus (rule "Rota 2"): the next dose is timed from the most recent earlier dose under
 * any of the names the rule counts, so a dose recorded under a product name (Pneu-C-13, Pneu-C-15, Rota-1, Rota-5)
 * raises the reminder just as the plain name does (#5009).
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
    private static final String NEEDS_SECOND_ROTA = "Needs Second Rota vaccine";

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
        void shouldNotRemind_whenTheRota1DoseHasNoNextDate() {
            assertThat(reminders(infantWithOneRotaDose("Rota-1", false, false), NEEDS_SECOND_ROTA)).isZero();
        }

        @Test
        void shouldNotRemind_whenTheRota1DoseIsMarkedNeverRemind() {
            assertThat(reminders(infantWithOneRotaDose("Rota-1", true, true), NEEDS_SECOND_ROTA)).isZero();
        }
    }
}
