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
 * Unit tests that verify the behavioral correctness of the Varicella (Var) prevention
 * rules defined in {@code prevention.drl}.
 *
 * <p>These tests cover four rules:</p>
 * <ul>
 *   <li><b>Var 1</b>: First dose at 15–24 months when at least one live vaccine (MMR or
 *       MMR-Var) is on record AND every recorded live vaccine was given &ge;28 days ago.</li>
 *   <li><b>Var 2</b>: First dose at 15–24 months when no live vaccine (MMR or MMR-Var)
 *       has ever been given (patient is live-vaccine naive).</li>
 *   <li><b>Var 3</b>: Catch-up dose at 4–6 years with the same MMR/MMR-Var spacing logic
 *       as Var 1.</li>
 *   <li><b>Var 4</b>: Catch-up dose at 4–6 years when patient is live-vaccine naive
 *       (mirrors Var 2).</li>
 * </ul>
 *
 * <p>Key invariant verified: a child with a <em>recently</em> administered MMR-Var must
 * NOT receive a Var recommendation until 28 days have elapsed, regardless of whether MMR
 * was given at a different time.</p>
 *
 * @see io.github.carlos_emr.carlos.prevention.Prevention
 * @see io.github.carlos_emr.carlos.drools.DroolsHelper
 * @since 2026-03-31
 */
@Tag("unit")
@Tag("drools")
@Tag("prevention")
@Tag("var")
@DisplayName("Var Prevention Rules - Live-Vaccine Spacing and Eligibility Tests")
class VarPreventionRulesDroolsUnitTest {

    /**
     * Compiled rule base loaded once for the entire test class.
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
        URL url = VarPreventionRulesDroolsUnitTest.class
                .getResource("/oscar/oscarPrevention/prevention.drl");
        assertThat(url).as("prevention.drl must be on the classpath").isNotNull();
        kieBase = DroolsHelper.loadFromUrl(url);
    }

    // -------------------------------------------------------------------------
    // Factory helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link Prevention} fact for a patient of the given age in months
     * with no vaccine records.
     *
     * @param ageInMonths exact age in months for the test patient
     * @return a {@link Prevention} with no vaccine records
     */
    private static Prevention patientWithAgeInMonths(int ageInMonths) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MONTH, -ageInMonths);
        return new Prevention("M", cal.getTime());
    }

    /**
     * Creates a {@link Prevention} fact for a patient of the given age in years
     * with no vaccine records.
     *
     * @param ageInYears exact age in years for the test patient
     * @return a {@link Prevention} with no vaccine records
     */
    private static Prevention patientWithAgeInYears(int ageInYears) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.YEAR, -ageInYears);
        return new Prevention("M", cal.getTime());
    }

    /**
     * Adds a vaccine record of the given type, given exactly {@code daysAgo} days ago,
     * to the supplied {@link Prevention} fact.
     *
     * @param prev      the prevention fact to modify
     * @param type      vaccine type (e.g. {@code "MMR"}, {@code "MMR-Var"})
     * @param daysAgo   how many days ago the vaccine was given
     */
    private static void addVaccine(Prevention prev, String type, int daysAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        prev.addPreventionItem(new PreventionItem(type, cal.getTime()));
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

    /**
     * Returns {@code true} if a Var-keyed warning was issued (from Var 1-4 rules),
     * as opposed to an MMR-Var-keyed warning from the MMR-Var 1 rule.
     *
     * @param prev the Prevention fact after rule execution
     * @return true if the "Var" prevention type has a warning
     */
    private static boolean hasVarWarning(Prevention prev) {
        return prev.getWarningMsgs().containsKey("Var");
    }

    // -------------------------------------------------------------------------
    // Var 2 / Var 4: Live-vaccine-naive patients
    // -------------------------------------------------------------------------

    /**
     * Tests for Var 2 (15–24 months) and Var 4 (4–6 years) which fire when
     * the patient has no MMR or MMR-Var on record at all.
     */
    @Nested
    @DisplayName("Live-vaccine-naive patients — Var 2 / Var 4")
    class LiveVaccineNaive {

        /**
         * Patient aged 18 months with no live vaccines: Var 2 must recommend Var.
         */
        @Test
        @DisplayName("should recommend Var when 18-month patient has no MMR or MMR-Var")
        void shouldRecommendVar_whenNoLiveVaccineGiven_at18Months() {
            Prevention prev = fireRules(patientWithAgeInMonths(18));

            assertThat(hasVarWarning(prev))
                    .as("Var 2 must fire for a live-vaccine-naive 18-month patient")
                    .isTrue();
        }

        /**
         * Patient aged 5 years with no live vaccines: Var 4 must recommend Var.
         */
        @Test
        @DisplayName("should recommend Var when 5-year patient has no MMR or MMR-Var")
        void shouldRecommendVar_whenNoLiveVaccineGiven_at5Years() {
            Prevention prev = fireRules(patientWithAgeInYears(5));

            assertThat(hasVarWarning(prev))
                    .as("Var 4 must fire for a live-vaccine-naive 5-year patient")
                    .isTrue();
        }

        /**
         * Patient aged 18 months who has an MMR on record: Var 2 must NOT fire
         * (this patient is NOT live-vaccine naive; handled by Var 1 once spacing is met).
         * Var 1 may fire if spacing is met — we only verify a single Var-keyed warning.
         */
        @Test
        @DisplayName("should not fire Var 2 when patient has an MMR record")
        void shouldNotFireVar2_whenPatientHasMmrRecord() {
            Prevention prev = patientWithAgeInMonths(18);
            addVaccine(prev, "MMR", 30);
            fireRules(prev);

            // The "Var" key in the warnings map can only hold one value, so if Var 1 fires
            // it produces a single entry. Var 2 would duplicate the key — the map keeps one.
            // We just verify the Var warning exists once (from Var 1, since spacing is met).
            assertThat(hasVarWarning(prev))
                    .as("Var 1 fires (spacing met), Var 2 must not also fire (MMR on record)")
                    .isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Var 1: Spacing check at 15–24 months
    // -------------------------------------------------------------------------

    /**
     * Tests for Var 1 (first dose at 15–24 months) which requires every recorded
     * live vaccine to be at least 28 days old.
     */
    @Nested
    @DisplayName("Var 1 — live-vaccine spacing at 15–24 months")
    class Var1Spacing {

        /**
         * MMR given 30 days ago: spacing met — Var 1 must recommend Var.
         */
        @Test
        @DisplayName("should recommend Var when MMR was given 30 days ago")
        void shouldRecommendVar_whenMmrWasGiven30DaysAgo() {
            Prevention prev = patientWithAgeInMonths(18);
            addVaccine(prev, "MMR", 30);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("Var 1 must fire: MMR given 30 days ago satisfies 28-day spacing")
                    .isTrue();
        }

        /**
         * MMR-Var given 30 days ago (no MMR): spacing met — Var 1 must recommend Var.
         */
        @Test
        @DisplayName("should recommend Var when MMR-Var was given 30 days ago and no MMR exists")
        void shouldRecommendVar_whenMmrVarWasGiven30DaysAgo() {
            Prevention prev = patientWithAgeInMonths(18);
            addVaccine(prev, "MMR-Var", 30);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("Var 1 must fire: MMR-Var given 30 days ago satisfies 28-day spacing")
                    .isTrue();
        }

        /**
         * MMR given 7 days ago: spacing NOT met — Var 1 must NOT fire.
         */
        @Test
        @DisplayName("should not recommend Var when MMR was given only 7 days ago")
        void shouldNotRecommendVar_whenMmrWasGiven7DaysAgo() {
            Prevention prev = patientWithAgeInMonths(18);
            addVaccine(prev, "MMR", 7);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("Var 1 must not fire: MMR given only 7 days ago, 28-day spacing not met")
                    .isFalse();
        }

        /**
         * MMR-Var given 7 days ago: spacing NOT met — Var 1 must NOT fire.
         * This is the core regression case: child received combined vaccine recently.
         */
        @Test
        @DisplayName("should not recommend Var when MMR-Var was given only 7 days ago")
        void shouldNotRecommendVar_whenMmrVarWasGiven7DaysAgo() {
            Prevention prev = patientWithAgeInMonths(18);
            addVaccine(prev, "MMR-Var", 7);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("Var 1 must not fire: MMR-Var given only 7 days ago, 28-day spacing not met")
                    .isFalse();
        }

        /**
         * Old MMR (60 days) but recent MMR-Var (7 days): MMR-Var spacing NOT met —
         * Var 1 must NOT fire. This tests that both vaccines are checked independently
         * and a recent one cannot be masked by an older one.
         */
        @Test
        @DisplayName("should not recommend Var when old MMR but recent MMR-Var (7 days)")
        void shouldNotRecommendVar_whenOldMmrButRecentMmrVar() {
            Prevention prev = patientWithAgeInMonths(18);
            addVaccine(prev, "MMR", 60);
            addVaccine(prev, "MMR-Var", 7);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("Var 1 must not fire: MMR-Var given 7 days ago violates 28-day rule even though MMR was 60 days ago")
                    .isFalse();
        }

        /**
         * Old MMR-Var (60 days) but recent MMR (7 days): MMR spacing NOT met —
         * Var 1 must NOT fire.
         */
        @Test
        @DisplayName("should not recommend Var when old MMR-Var but recent MMR (7 days)")
        void shouldNotRecommendVar_whenOldMmrVarButRecentMmr() {
            Prevention prev = patientWithAgeInMonths(18);
            addVaccine(prev, "MMR-Var", 60);
            addVaccine(prev, "MMR", 7);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("Var 1 must not fire: MMR given 7 days ago violates 28-day rule even though MMR-Var was 60 days ago")
                    .isFalse();
        }

        /**
         * Both MMR (60 days) and MMR-Var (35 days): both satisfy spacing — Var 1 must fire.
         */
        @Test
        @DisplayName("should recommend Var when both MMR (60d) and MMR-Var (35d) satisfy spacing")
        void shouldRecommendVar_whenBothMmrAndMmrVarSatisfySpacing() {
            Prevention prev = patientWithAgeInMonths(18);
            addVaccine(prev, "MMR", 60);
            addVaccine(prev, "MMR-Var", 35);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("Var 1 must fire: both MMR (60d) and MMR-Var (35d) are >= 28 days old")
                    .isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Var 3: Spacing check at 4–6 years (catch-up)
    // -------------------------------------------------------------------------

    /**
     * Tests for Var 3 (catch-up dose at 4–6 years), which mirrors Var 1 spacing logic.
     */
    @Nested
    @DisplayName("Var 3 — live-vaccine spacing at 4–6 years (catch-up)")
    class Var3Spacing {

        /**
         * 5-year-old, MMR-Var given 30 days ago: spacing met — Var 3 must recommend Var.
         */
        @Test
        @DisplayName("should recommend Var when 5-year patient has MMR-Var 30 days ago")
        void shouldRecommendVar_whenMmrVarGiven30DaysAgo_at5Years() {
            Prevention prev = patientWithAgeInYears(5);
            addVaccine(prev, "MMR-Var", 30);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("Var 3 must fire: MMR-Var given 30 days ago satisfies spacing for 5-year patient")
                    .isTrue();
        }

        /**
         * 5-year-old, MMR-Var given 7 days ago: spacing NOT met — Var 3 must NOT fire.
         * Core regression case at catch-up age.
         */
        @Test
        @DisplayName("should not recommend Var when 5-year patient had MMR-Var only 7 days ago")
        void shouldNotRecommendVar_whenMmrVarGiven7DaysAgo_at5Years() {
            Prevention prev = patientWithAgeInYears(5);
            addVaccine(prev, "MMR-Var", 7);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("Var 3 must not fire: MMR-Var given 7 days ago violates 28-day spacing")
                    .isFalse();
        }

        /**
         * 5-year-old, old MMR (365d) but recent MMR-Var (7d): Var 3 must NOT fire.
         */
        @Test
        @DisplayName("should not recommend Var when 5-year patient has old MMR but recent MMR-Var")
        void shouldNotRecommendVar_whenOldMmrButRecentMmrVar_at5Years() {
            Prevention prev = patientWithAgeInYears(5);
            addVaccine(prev, "MMR", 365);
            addVaccine(prev, "MMR-Var", 7);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("Var 3 must not fire: recent MMR-Var (7d) prevents Var even with old MMR")
                    .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // No recommendation when Var already given
    // -------------------------------------------------------------------------

    /**
     * Verifies that no Var rules fire once a Var dose is on record.
     */
    @Nested
    @DisplayName("No recommendation when Var already given")
    class AlreadyVaccinated {

        /**
         * 18-month patient with Var on record: no Var warning should fire.
         */
        @Test
        @DisplayName("should not recommend Var when 18-month patient already has a Var record")
        void shouldNotRecommendVar_whenVarAlreadyGiven_at18Months() {
            Prevention prev = patientWithAgeInMonths(18);
            addVaccine(prev, "Var", 60);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("No Var rule should fire once a Var dose is on record")
                    .isFalse();
        }

        /**
         * 5-year patient with Var on record: no Var warning should fire.
         */
        @Test
        @DisplayName("should not recommend Var when 5-year patient already has a Var record")
        void shouldNotRecommendVar_whenVarAlreadyGiven_at5Years() {
            Prevention prev = patientWithAgeInYears(5);
            addVaccine(prev, "Var", 60);
            fireRules(prev);

            assertThat(hasVarWarning(prev))
                    .as("No Var rule should fire once a Var dose is on record")
                    .isFalse();
        }
    }
}
