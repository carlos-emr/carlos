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
 * The tetanus reminders in {@code prevention.drl}: an adult with a Td whose last tetanus-containing vaccine was given
 * ten or more years ago is told "Needs repeat Td" (rule "Td 2", #4998), one vaccinated more recently, with Td or any
 * other tetanus-containing vaccine, is not, and an adult whose only tetanus vaccine is an old Tdap is told "Needs Td"
 * (rule "Td 1") rather than both.
 *
 * @since 2026-10-18
 */
@Tag("unit")
@Tag("drools")
@Tag("prevention")
@DisplayName("Td prevention rules")
class TdPreventionRulesDroolsUnitTest {

    private static final String REPEAT_TD = "Needs repeat Td";
    private static final String NEEDS_TD = "Needs Td";

    private static KieBase kieBase;

    @BeforeAll
    static void loadPreventionDrl() throws DroolsCompilationException {
        URL url = TdPreventionRulesDroolsUnitTest.class.getResource("/oscar/prevention/prevention.drl");
        assertThat(url).as("prevention.drl must be on the classpath").isNotNull();
        kieBase = DroolsHelper.loadFromUrl(url);
    }

    private static Date yearsAgo(int years) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.YEAR, -years);
        return cal.getTime();
    }

    /** A 70-year-old with the given tetanus-containing vaccines, each given the given number of years ago. */
    private static Prevention adultWith(Object... typeAndYearsAgo) {
        Prevention prev = new Prevention("M", yearsAgo(70));
        for (int i = 0; i < typeAndYearsAgo.length; i += 2) {
            prev.addPreventionItem(new PreventionItem((String) typeAndYearsAgo[i], yearsAgo((Integer) typeAndYearsAgo[i + 1])));
        }
        return prev;
    }

    private static Prevention fireRules(Prevention prev) {
        KieSession session = kieBase.newKieSession();
        try {
            session.insert(prev);
            session.fireAllRules();
        } finally {
            session.dispose();
        }
        return prev;
    }

    @Test
    void shouldRemindOfARepeatTd_whenTheOnlyTdIsTwelveYearsOld() {
        assertThat(fireRules(adultWith("Td", 12)).getWarnings()).contains(REPEAT_TD);
    }

    @Test
    void shouldRemindOfARepeatTd_whenEveryTetanusVaccineIsTenOrMoreYearsOld() {
        assertThat(fireRules(adultWith("Td", 15, "Tdap", 11)).getWarnings()).contains(REPEAT_TD);
    }

    @Test
    void shouldNotRemind_whenTheTdIsFiveYearsOld() {
        assertThat(fireRules(adultWith("Td", 5)).getWarnings()).doesNotContain(REPEAT_TD);
    }

    @Test
    void shouldNotRemind_whenAnOlderTdWasFollowedByARecentTdap() {
        assertThat(fireRules(adultWith("Td", 12, "Tdap", 2)).getWarnings()).doesNotContain(REPEAT_TD);
    }

    @Test
    void shouldAskForATdOnce_whenTheOnlyTetanusVaccineIsATdapElevenYearsOld() {
        assertThat(fireRules(adultWith("Tdap", 11)).getWarnings()).contains(NEEDS_TD).doesNotContain(REPEAT_TD);
    }

    @Test
    void shouldRaiseNeitherTdReminder_whenNoTetanusVaccineWasEverGiven() {
        assertThat(fireRules(adultWith()).getWarnings()).doesNotContain(REPEAT_TD, NEEDS_TD);
    }
}
