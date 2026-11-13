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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import io.github.carlos_emr.carlos.drools.DroolsCompilationException;
import io.github.carlos_emr.carlos.drools.DroolsHelper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The yearly flu reminder in {@code prevention.drl}: a patient aged 65 or over whose most recent flu shot, recorded as
 * "Inf" or under the older name "Flu", was a year or more ago is told "Needs Flu Shot" (rule "Inf 2", #5008), and one
 * who never had a flu shot is told so once (rule "Inf 3"). A next date set on the last Inf takes precedence over the
 * yearly rule: the patient is told once that date has passed (rule "Inf Set Date").
 *
 * @since 2026-10-18
 */
@Tag("unit")
@Tag("drools")
@Tag("prevention")
@DisplayName("Influenza prevention rules")
class InfPreventionRulesDroolsUnitTest {

    private static final String NEEDS_FLU_SHOT = "Needs Flu Shot ";

    private static KieBase kieBase;

    @BeforeAll
    static void loadPreventionDrl() throws DroolsCompilationException {
        URL url = InfPreventionRulesDroolsUnitTest.class.getResource("/oscar/prevention/prevention.drl");
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

    /** A patient of the given age with the given flu shots, each given the given number of months ago. */
    private static Prevention patient(int ageInYears, Object... typeAndMonthsAgo) {
        Prevention prev = new Prevention("F", monthsAgo(ageInYears * 12));
        for (int i = 0; i < typeAndMonthsAgo.length; i += 2) {
            prev.addPreventionItem(new PreventionItem((String) typeAndMonthsAgo[i], monthsAgo((Integer) typeAndMonthsAgo[i + 1])));
        }
        return prev;
    }

    /** A 70-year-old whose only flu shot is an Inf 24 months old with a next date the given number of months ahead. */
    private static Prevention patientWithInfNextDate(int nextDateMonthsAhead) {
        Prevention prev = new Prevention("F", monthsAgo(70 * 12));
        prev.addPreventionItem(new PreventionItem("Inf", monthsAgo(24), "0", monthsAgo(-nextDateMonthsAhead)));
        return prev;
    }

    private static int fluReminders(Prevention prev) {
        KieSession session = kieBase.newKieSession();
        try {
            session.insert(prev);
            session.fireAllRules();
        } finally {
            session.dispose();
        }
        return Collections.frequency(prev.getWarnings(), NEEDS_FLU_SHOT);
    }

    @Test
    void shouldRemind_whenTheLastInfWasTwoYearsAgo() {
        assertThat(fluReminders(patient(70, "Inf", 24))).isEqualTo(1);
    }

    @Test
    void shouldRemind_whenTheOnlyFluShotIsALegacyFluRecordTwoYearsOld() {
        assertThat(fluReminders(patient(70, "Flu", 24))).isEqualTo(1);
    }

    @Test
    void shouldNotRemind_whenTheLastInfWasTwoMonthsAgo() {
        assertThat(fluReminders(patient(70, "Inf", 2))).isZero();
    }

    @Test
    void shouldNotRemind_whenAnOldInfWasFollowedByARecentLegacyFluRecord() {
        assertThat(fluReminders(patient(70, "Inf", 24, "Flu", 2))).isZero();
    }

    @Test
    void shouldRemindOnce_whenNoFluShotWasEverGiven() {
        assertThat(fluReminders(patient(70))).isEqualTo(1);
    }

    @Test
    void shouldRemindOnce_whenTheLastInfIsTwoYearsOldAndItsNextDateHasPassed() {
        assertThat(fluReminders(patientWithInfNextDate(-1))).isEqualTo(1);
    }

    @Test
    void shouldNotRemind_whenTheLastInfIsTwoYearsOldAndItsNextDateIsStillAhead() {
        assertThat(fluReminders(patientWithInfNextDate(1))).isZero();
    }

    @Test
    void shouldNotRemind_whenThePatientIsUnderSixtyFive() {
        assertThat(fluReminders(patient(60, "Inf", 24))).isZero();
    }
}
