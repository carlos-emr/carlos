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
 * <p>#5048, following the Canadian Immunization Guide (Tetanus toxoid): the teen Tdap ("Tdap 1") is due from age 14,
 * ten years after the 4-6-year booster whichever product it was (DTaP-IPV, Tdap-IPV, or a DTaP-IPV-Hib or TdP-IPV-Hib
 * given at 4 years or later; infant doses do not count), and the first Td ("Td 1") ten years after the most recent
 * booster-age tetanus product (Tdap, Td-IPV, Tdap-IPV, or a TdP-IPV-Hib given at 4 years or later; its infant doses
 * do not count here either). When both apply, as for a teen whose school booster was a Tdap-IPV ten years ago, both
 * reminders show. By the owner's decision, a teen of 14 to 16 with no booster dose on file at all is also asked for
 * the Tdap ("Tdap 2"), since childhood records are often missing.</p>
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
    private static final String NEEDS_TDAP = "Needs Tdap";

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
        return personWith(70, typeAndYearsAgo);
    }

    /** A person of the given age with the given vaccines, each given the given number of years ago. */
    private static Prevention personWith(int ageInYears, Object... typeAndYearsAgo) {
        Prevention prev = new Prevention("M", yearsAgo(ageInYears));
        for (int i = 0; i < typeAndYearsAgo.length; i += 2) {
            prev.addPreventionItem(new PreventionItem((String) typeAndYearsAgo[i], yearsAgo((Integer) typeAndYearsAgo[i + 1])));
        }
        return prev;
    }

    /** A person of the given age with doses of one type given at the given ages in months. */
    private static Prevention personWithDosesAtAgeInMonths(int ageInYears, String type, int... monthsOfAge) {
        Date birth = yearsAgo(ageInYears);
        Prevention prev = new Prevention("M", birth);
        for (int months : monthsOfAge) {
            Calendar given = Calendar.getInstance();
            given.setTime(birth);
            given.add(Calendar.MONTH, months);
            prev.addPreventionItem(new PreventionItem(type, given.getTime()));
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

    @Test
    void shouldAskForTheTeenTdap_whenTheSchoolBoosterWasTdapIpvTenYearsAgo() {
        Prevention teen = personWith(15, "DTaP-IPV-Hib", 15, "DTaP-IPV-Hib", 14, "Tdap-IPV", 10);
        assertThat(fireRules(teen).getWarnings()).contains(NEEDS_TDAP);
    }

    @Test
    void shouldAskForTheTeenTdap_whenTheLastChildhoodDoseWasDtapIpvHibTenYearsAgo() {
        assertThat(fireRules(personWith(15, "DTaP-IPV-Hib", 14, "DTaP-IPV-Hib", 10)).getWarnings()).contains(NEEDS_TDAP);
    }

    @Test
    void shouldStillAskForTheTeenTdap_whenTheSchoolBoosterWasDtapIpvTenYearsAgo() {
        assertThat(fireRules(personWith(15, "DTaP-IPV", 14, "DTaP-IPV", 10)).getWarnings()).contains(NEEDS_TDAP);
    }

    @Test
    void shouldNotAskForTheTeenTdapYet_whenALaterTdapIpvBoosterIsUnderTenYearsOld() {
        assertThat(fireRules(personWith(16, "DTaP-IPV", 15, "Tdap-IPV", 9)).getWarnings()).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldNotAskForTheTeenTdap_beforeAgeFourteen() {
        assertThat(fireRules(personWith(13, "DTaP-IPV", 10)).getWarnings()).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldAskForTheTeenTdap_atExactlyFourteen() {
        assertThat(fireRules(personWith(14, "DTaP-IPV", 10)).getWarnings()).contains(NEEDS_TDAP);
    }

    @Test
    void shouldNotStartTheTeenTdapClock_fromADtapIpvHibGivenAtFortySevenMonths() {
        // At 17, past the 14-16 no-booster rule ("Tdap 2"), only the 10-year clock decides.
        assertThat(fireRules(personWithDosesAtAgeInMonths(17, "DTaP-IPV-Hib", 2, 4, 6, 47)).getWarnings())
                .doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldStartTheTeenTdapClock_fromADtapIpvHibGivenAtFortyEightMonths() {
        assertThat(fireRules(personWithDosesAtAgeInMonths(15, "DTaP-IPV-Hib", 2, 4, 6, 48)).getWarnings())
                .contains(NEEDS_TDAP);
    }

    @Test
    void shouldNotAskForTheTeenTdapYet_whenALaterBoosterDtapIpvHibIsUnderTenYearsOld() {
        Prevention teen = personWithDosesAtAgeInMonths(16, "DTaP-IPV", 6);
        Calendar booster = Calendar.getInstance();
        booster.setTime(yearsAgo(16));
        booster.add(Calendar.MONTH, 100);
        teen.addPreventionItem(new PreventionItem("DTaP-IPV-Hib", booster.getTime()));
        assertThat(fireRules(teen).getWarnings()).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldNotStartTheTeenTdapClock_fromInfantDtapIpvHibDoses() {
        // At 17, past the 14-16 no-booster rule ("Tdap 2"), only the 10-year clock decides.
        Prevention teen = personWith(17, "DTaP-IPV-Hib", 17, "DTaP-IPV-Hib", 16, "DTaP-IPV-Hib", 15);
        assertThat(fireRules(teen).getWarnings()).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldAskForTheTeenTdap_whenTheSchoolBoosterWasTheOlderTdpIpvHib() {
        assertThat(fireRules(personWith(15, "TdP-IPV-Hib", 14, "TdP-IPV-Hib", 10)).getWarnings()).contains(NEEDS_TDAP);
    }

    @Test
    void shouldAskForATd_whenTheOnlyTetanusVaccineIsATdpIpvHibTwelveYearsOld() {
        assertThat(fireRules(adultWith("TdP-IPV-Hib", 12)).getWarnings()).contains(NEEDS_TD).doesNotContain(REPEAT_TD);
    }

    @Test
    void shouldNotAskForATd_whenATwelveYearOldHadOnlyInfantTdpIpvHibDoses() {
        assertThat(fireRules(personWithDosesAtAgeInMonths(12, "TdP-IPV-Hib", 2, 4, 18)).getWarnings())
                .doesNotContain(NEEDS_TD, NEEDS_TDAP, REPEAT_TD);
    }

    @Test
    void shouldNotAskForATd_whenATwelveYearOldHadInfantTdpIpvHibAndADtapIpvBoosterAtFive() {
        Prevention child = personWithDosesAtAgeInMonths(12, "TdP-IPV-Hib", 2, 4, 18);
        Calendar booster = Calendar.getInstance();
        booster.setTime(yearsAgo(12));
        booster.add(Calendar.MONTH, 60);
        child.addPreventionItem(new PreventionItem("DTaP-IPV", booster.getTime()));
        assertThat(fireRules(child).getWarnings()).doesNotContain(NEEDS_TD, NEEDS_TDAP, REPEAT_TD);
    }

    @Test
    void shouldAskForATd_whenATdpIpvHibBoosterGivenAtFiveIsElevenYearsOld() {
        assertThat(fireRules(personWithDosesAtAgeInMonths(16, "TdP-IPV-Hib", 2, 4, 18, 60)).getWarnings())
                .contains(NEEDS_TD).doesNotContain(REPEAT_TD);
    }

    @Test
    void shouldAskForTheTeenTdapOnce_whenAFifteenYearOldHasNoTetanusRecord() {
        assertThat(fireRules(personWith(15)).getWarnings()).containsOnlyOnce(NEEDS_TDAP).doesNotContain(NEEDS_TD, REPEAT_TD);
    }

    @Test
    void shouldNotAskForTheTeenTdap_whenAThirteenYearOldHasNoTetanusRecord() {
        assertThat(fireRules(personWith(13)).getWarnings()).doesNotContain(NEEDS_TDAP, NEEDS_TD, REPEAT_TD);
    }

    @Test
    void shouldNotAskForTheTeenTdap_whenASeventeenYearOldHasNoTetanusRecord() {
        // The no-record reminder covers the guide's 14-16 window only.
        assertThat(fireRules(personWith(17)).getWarnings()).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldAskForTheTeenTdapButNoTd_whenAFourteenYearOldHasOnlyInfantTdpIpvHibDoses() {
        assertThat(fireRules(personWithDosesAtAgeInMonths(14, "TdP-IPV-Hib", 2, 4, 18)).getWarnings())
                .containsOnlyOnce(NEEDS_TDAP).doesNotContain(NEEDS_TD, REPEAT_TD);
    }

    @Test
    void shouldNotAskForTheTeenTdapYet_whenAFifteenYearOldsBoosterIsSevenYearsOld() {
        assertThat(fireRules(personWith(15, "DTaP-IPV", 7)).getWarnings()).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldNotAskForATdap_whenATdapIsOnFile() {
        assertThat(fireRules(personWith(30, "Tdap-IPV", 25, "Tdap", 15)).getWarnings()).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldAskForBothTheTdapAndATd_whenTheLastTetanusVaccineIsATdapIpvBoosterTenYearsOld() {
        assertThat(fireRules(personWith(15, "Tdap-IPV", 10)).getWarnings())
                .contains(NEEDS_TDAP, NEEDS_TD).doesNotContain(REPEAT_TD);
    }

    @Test
    void shouldAskForATd_whenTheOnlyTetanusVaccineIsATdIpvTwelveYearsOld() {
        assertThat(fireRules(adultWith("Td-IPV", 12)).getWarnings()).contains(NEEDS_TD).doesNotContain(NEEDS_TDAP, REPEAT_TD);
    }

    @Test
    void shouldNotAskForATdYet_whenAnOldTdapWasFollowedByARecentTdIpv() {
        assertThat(fireRules(adultWith("Tdap", 15, "Td-IPV", 3)).getWarnings()).doesNotContain(NEEDS_TD);
    }

    @Test
    void shouldAskForATd_whenATdapWasFollowedByATdapIpvElevenYearsAgo() {
        assertThat(fireRules(adultWith("Tdap", 20, "Tdap-IPV", 11)).getWarnings()).contains(NEEDS_TD);
    }
}
