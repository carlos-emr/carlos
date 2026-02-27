/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
 * Unit tests that verify the boundary behavior of the MAM (Mammogram) prevention rules
 * defined in {@code prevention.drl}, specifically the age thresholds updated in PR #475
 * to conform to ON/BC guidelines (ages 40–74, every two years).
 *
 * <p>The tests exercise the four MAM rules loaded from the production DRL:</p>
 * <ul>
 *   <li><b>MAM 3</b>: Warning fired when <em>no MAM records</em> exist for a female
 *       patient whose age falls in [40, 74]. This is the primary new-patient rule.</li>
 *   <li><b>MAM 1</b>: Reminder fired when the last MAM was 22–24 months ago (coming due)
 *       for a female patient in [40, 74].</li>
 *   <li><b>MAM 2</b>: Warning fired when the last MAM was {@literal >}24 months ago (overdue)
 *       for a female patient in [40, 74].</li>
 * </ul>
 *
 * <p>Age boundary test cases:</p>
 * <ul>
 *   <li>Age 39 – one year below minimum: no MAM rules should fire.</li>
 *   <li>Age 40 – minimum boundary (inclusive): MAM rules should fire.</li>
 *   <li>Age 74 – maximum boundary (inclusive): MAM rules should fire.</li>
 *   <li>Age 75 – one year above maximum: no MAM rules should fire.</li>
 * </ul>
 *
 * <p>No Spring context is required because DRL compilation and execution are
 * pure Drools KIE API operations with no external dependencies.</p>
 *
 * @see io.github.carlos_emr.carlos.drools.DroolsHelper
 * @see io.github.carlos_emr.carlos.prevention.Prevention
 * @since 2026-02-27
 */
@Tag("unit")
@Tag("drools")
@Tag("prevention")
@Tag("mam")
@DisplayName("MAM Prevention Rules - Age Boundary Tests")
class MamPreventionRulesDroolsUnitTest {

    /**
     * Compiled rule base loaded once for the entire test class.
     * Loading is expensive (~1s); sharing across tests via {@code @BeforeAll} keeps
     * the suite fast while ensuring all tests see the same production DRL.
     */
    private static KieBase kieBase;

    /**
     * Loads and compiles {@code prevention.drl} from the classpath once before
     * any test in this class runs.
     *
     * @throws DroolsCompilationException if the DRL contains syntax errors
     */
    @BeforeAll
    static void loadPreventionDrl() throws DroolsCompilationException {
        URL url = MamPreventionRulesDroolsUnitTest.class
                .getResource("/oscar/oscarPrevention/prevention.drl");
        assertThat(url).as("prevention.drl must be on the classpath").isNotNull();
        kieBase = DroolsHelper.loadFromUrl(url);
    }

    // -------------------------------------------------------------------------
    // Factory helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link Prevention} fact for a female patient with the specified
     * age (in whole years) and no existing MAM records.
     *
     * <p>The date-of-birth is set to midnight on (today − {@code ageInYears}).
     * Using midnight guarantees that {@link Prevention#getAgeInYears()} returns
     * exactly {@code ageInYears} regardless of the wall-clock time at which the
     * test runs, because the loop in {@code getNumYears} adds whole-year increments
     * and the comparison target ("today") is always after midnight.</p>
     *
     * @param ageInYears exact age in years for the test patient
     * @return a female {@link Prevention} with no MAM records
     */
    private static Prevention femaleWithNoMamRecords(int ageInYears) {
        // Use midnight to avoid sub-second timing issues in getAgeInYears()
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.YEAR, -ageInYears);
        Date dob = cal.getTime();
        return new Prevention("F", dob);
    }

    /**
     * Creates a {@link Prevention} fact for a female patient with the specified age
     * and a MAM record dated exactly {@code monthsSinceLast} months ago.
     *
     * <p>A {@link PreventionItem} for "MAM" is added so that
     * {@link Prevention#getHowManyMonthsSinceLast(String)} returns the expected value,
     * triggering MAM 1 (22–24 months, coming due) or MAM 2 ({@literal >}24 months, overdue).</p>
     *
     * @param ageInYears       exact age of the patient in years
     * @param monthsSinceLast  number of whole months since the last MAM was performed
     * @return a female {@link Prevention} with one MAM record
     */
    private static Prevention femaleWithLastMam(int ageInYears, int monthsSinceLast) {
        Prevention prev = femaleWithNoMamRecords(ageInYears);
        // Compute the date of the last mammogram
        Calendar mamDate = Calendar.getInstance();
        mamDate.add(Calendar.MONTH, -monthsSinceLast);
        PreventionItem mamItem = new PreventionItem("MAM", mamDate.getTime());
        prev.addPreventionItem(mamItem);
        return prev;
    }

    /**
     * Fires all rules for the given fact in a fresh {@link KieSession} and returns
     * the fact after rule execution. The session is always disposed.
     *
     * @param prev the Prevention fact to insert and evaluate
     * @return the same {@code prev} instance after rules have fired
     */
    private Prevention fireRules(Prevention prev) {
        KieSession session = kieBase.newKieSession();
        try {
            session.insert(prev);
            session.fireAllRules();
        } finally {
            session.dispose();
        }
        return prev;
    }

    // -------------------------------------------------------------------------
    // MAM 3: Warning when no MAM records exist in the 40–74 age range
    // -------------------------------------------------------------------------

    /**
     * Tests for MAM rule 3 ({@code "No Mammogram records can be found for this patient"}).
     *
     * <p>MAM 3 fires when all of the following are true:</p>
     * <ul>
     *   <li>age ≥ 40</li>
     *   <li>age ≤ 74</li>
     *   <li>patient is female</li>
     *   <li>no MAM records exist ({@code getHowManyMonthsSinceLast("MAM") == -1})</li>
     *   <li>patient is not ineligible</li>
     * </ul>
     */
    @Nested
    @DisplayName("MAM 3 - no mammogram records")
    class Mam3NoRecords {

        /**
         * Age 40 is the inclusive lower boundary: MAM 3 must fire.
         */
        @Test
        @DisplayName("should fire MAM 3 warning when age is 40 (lower boundary inclusive)")
        void shouldFireMam3Warning_whenAgeIs40() {
            Prevention prev = fireRules(femaleWithNoMamRecords(40));

            assertThat(prev.getWarnings())
                    .as("MAM 3 warning expected at age 40")
                    .anyMatch(w -> w.contains("No Mammogram records"));
        }

        /**
         * Age 57 is well inside the 40–74 range: MAM 3 must fire.
         */
        @Test
        @DisplayName("should fire MAM 3 warning when age is 57 (within range)")
        void shouldFireMam3Warning_whenAgeIs57() {
            Prevention prev = fireRules(femaleWithNoMamRecords(57));

            assertThat(prev.getWarnings())
                    .as("MAM 3 warning expected at age 57")
                    .anyMatch(w -> w.contains("No Mammogram records"));
        }

        /**
         * Age 74 is the inclusive upper boundary: MAM 3 must fire.
         */
        @Test
        @DisplayName("should fire MAM 3 warning when age is 74 (upper boundary inclusive)")
        void shouldFireMam3Warning_whenAgeIs74() {
            Prevention prev = fireRules(femaleWithNoMamRecords(74));

            assertThat(prev.getWarnings())
                    .as("MAM 3 warning expected at age 74")
                    .anyMatch(w -> w.contains("No Mammogram records"));
        }

        /**
         * Age 39 is one year below the lower boundary: MAM 3 must NOT fire.
         */
        @Test
        @DisplayName("should not fire MAM 3 when age is 39 (below lower boundary)")
        void shouldNotFireMam3_whenAgeIs39() {
            Prevention prev = fireRules(femaleWithNoMamRecords(39));

            assertThat(prev.getWarnings())
                    .as("MAM 3 warning must not fire at age 39")
                    .noneMatch(w -> w.contains("No Mammogram records"));
        }

        /**
         * Age 75 is one year above the upper boundary: MAM 3 must NOT fire.
         */
        @Test
        @DisplayName("should not fire MAM 3 when age is 75 (above upper boundary)")
        void shouldNotFireMam3_whenAgeIs75() {
            Prevention prev = fireRules(femaleWithNoMamRecords(75));

            assertThat(prev.getWarnings())
                    .as("MAM 3 warning must not fire at age 75")
                    .noneMatch(w -> w.contains("No Mammogram records"));
        }

        /**
         * Sex "M" (male), age 50 (within the 40–74 range): MAM 3 must NOT fire
         * because the rule requires {@code isFemale()}.
         */
        @Test
        @DisplayName("should not fire MAM 3 when patient is male even if age is in range")
        void shouldNotFireMam3_whenPatientIsMale() {
            // Build a male Prevention for age 50 – inside the range but wrong sex
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.YEAR, -50);
            Prevention prev = new Prevention("M", cal.getTime());

            fireRules(prev);

            assertThat(prev.getWarnings())
                    .as("MAM 3 warning must not fire for male patients")
                    .noneMatch(w -> w.contains("No Mammogram records"));
        }
    }

    // -------------------------------------------------------------------------
    // MAM 1: Reminder when last MAM was 22–24 months ago
    // -------------------------------------------------------------------------

    /**
     * Tests for MAM rule 1 ({@code "Mammogram is coming due for this patient"}).
     *
     * <p>MAM 1 fires when the last MAM was 22–24 months ago and the patient is
     * female in the 40–74 range with no next date set and not prevention-never.</p>
     */
    @Nested
    @DisplayName("MAM 1 - mammogram coming due (22–24 months)")
    class Mam1ComingDue {

        /**
         * Age 40, last MAM 23 months ago: MAM 1 reminder must fire.
         */
        @Test
        @DisplayName("should fire MAM 1 reminder when age is 40 and MAM was 23 months ago")
        void shouldFireMam1Reminder_whenAgeIs40AndMamWas23MonthsAgo() {
            Prevention prev = fireRules(femaleWithLastMam(40, 23));

            assertThat(prev.getReminder())
                    .as("MAM 1 reminder expected at age 40 with 23 months since last MAM")
                    .anyMatch(r -> r.contains("coming due"));
        }

        /**
         * Age 74, last MAM 22 months ago (lower edge of 22–24 window): MAM 1 must fire.
         */
        @Test
        @DisplayName("should fire MAM 1 reminder when age is 74 and MAM was 22 months ago")
        void shouldFireMam1Reminder_whenAgeIs74AndMamWas22MonthsAgo() {
            Prevention prev = fireRules(femaleWithLastMam(74, 22));

            assertThat(prev.getReminder())
                    .as("MAM 1 reminder expected at age 74 with 22 months since last MAM")
                    .anyMatch(r -> r.contains("coming due"));
        }

        /**
         * Age 39, last MAM 23 months ago: MAM 1 must NOT fire (below lower age boundary).
         */
        @Test
        @DisplayName("should not fire MAM 1 reminder when age is 39 (below lower boundary)")
        void shouldNotFireMam1Reminder_whenAgeIs39() {
            Prevention prev = fireRules(femaleWithLastMam(39, 23));

            assertThat(prev.getReminder())
                    .as("MAM 1 reminder must not fire at age 39")
                    .noneMatch(r -> r.contains("coming due"));
        }

        /**
         * Age 75, last MAM 23 months ago: MAM 1 must NOT fire (above upper age boundary).
         */
        @Test
        @DisplayName("should not fire MAM 1 reminder when age is 75 (above upper boundary)")
        void shouldNotFireMam1Reminder_whenAgeIs75() {
            Prevention prev = fireRules(femaleWithLastMam(75, 23));

            assertThat(prev.getReminder())
                    .as("MAM 1 reminder must not fire at age 75")
                    .noneMatch(r -> r.contains("coming due"));
        }
    }

    // -------------------------------------------------------------------------
    // MAM 2: Warning when last MAM was >24 months ago (overdue)
    // -------------------------------------------------------------------------

    /**
     * Tests for MAM rule 2 ({@code "Mammogram is overdue for this patient"}).
     *
     * <p>MAM 2 fires when the last MAM was {@literal >}24 months ago and the patient is
     * female in the 40–74 range with no next date set and not prevention-never.</p>
     */
    @Nested
    @DisplayName("MAM 2 - mammogram overdue (>24 months)")
    class Mam2Overdue {

        /**
         * Age 40, last MAM 30 months ago: MAM 2 warning must fire.
         */
        @Test
        @DisplayName("should fire MAM 2 warning when age is 40 and MAM was 30 months ago")
        void shouldFireMam2Warning_whenAgeIs40AndMamWas30MonthsAgo() {
            Prevention prev = fireRules(femaleWithLastMam(40, 30));

            assertThat(prev.getWarnings())
                    .as("MAM 2 overdue warning expected at age 40 with 30 months since last MAM")
                    .anyMatch(w -> w.contains("overdue"));
        }

        /**
         * Age 74, last MAM 36 months ago: MAM 2 warning must fire.
         */
        @Test
        @DisplayName("should fire MAM 2 warning when age is 74 and MAM was 36 months ago")
        void shouldFireMam2Warning_whenAgeIs74AndMamWas36MonthsAgo() {
            Prevention prev = fireRules(femaleWithLastMam(74, 36));

            assertThat(prev.getWarnings())
                    .as("MAM 2 overdue warning expected at age 74 with 36 months since last MAM")
                    .anyMatch(w -> w.contains("overdue"));
        }

        /**
         * Age 39, last MAM 30 months ago: MAM 2 must NOT fire (below lower age boundary).
         */
        @Test
        @DisplayName("should not fire MAM 2 warning when age is 39 (below lower boundary)")
        void shouldNotFireMam2Warning_whenAgeIs39() {
            Prevention prev = fireRules(femaleWithLastMam(39, 30));

            assertThat(prev.getWarnings())
                    .as("MAM 2 overdue warning must not fire at age 39")
                    .noneMatch(w -> w.contains("overdue"));
        }

        /**
         * Age 75, last MAM 30 months ago: MAM 2 must NOT fire (above upper age boundary).
         */
        @Test
        @DisplayName("should not fire MAM 2 warning when age is 75 (above upper boundary)")
        void shouldNotFireMam2Warning_whenAgeIs75() {
            Prevention prev = fireRules(femaleWithLastMam(75, 30));

            assertThat(prev.getWarnings())
                    .as("MAM 2 overdue warning must not fire at age 75")
                    .noneMatch(w -> w.contains("overdue"));
        }
    }
}
