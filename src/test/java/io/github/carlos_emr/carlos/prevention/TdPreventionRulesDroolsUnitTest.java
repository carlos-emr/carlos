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
import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;

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
    private static final String ADULT_TDAP = "Needs adult Tdap (one dose)";
    private static final String SECOND_DTAP_IPV = "Needs Second DTaP-IPV /HIB";
    private static final String THIRD_DTAP_IPV = "Needs Third DTaP-IPV /HIB";

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

    /** A Tdap record dated today, saved the way the prevention page saves it: refused, or "ineligible". */
    private static PreventionItem declinedTdapToday(boolean ineligible) {
        io.github.carlos_emr.carlos.commn.model.Prevention record = new io.github.carlos_emr.carlos.commn.model.Prevention();
        record.setPreventionType("Tdap");
        record.setPreventionDate(yearsAgo(0));
        if (ineligible) {
            record.setIneligible(true);
        } else {
            record.setRefused(true);
        }
        return new PreventionItem(record, PreventionDateDTO.from(record.getPreventionDate(), null));
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
    void shouldRemindOfARepeatTd_whenTheOnlyTdIsTwelveYearsOldAfterTheAdultTdap() {
        // The adult Tdap was given at 30, so the 10-year Td rule decides (#5048: while it is due, it is the only one).
        assertThat(fireRules(adultWith("Tdap", 40, "Td", 12)).getWarnings()).contains(REPEAT_TD).doesNotContain(ADULT_TDAP);
    }

    @Test
    void shouldRemindOfARepeatTd_whenEveryTetanusVaccineIsTenOrMoreYearsOld() {
        assertThat(fireRules(adultWith("Td", 15, "Tdap", 11)).getWarnings()).contains(REPEAT_TD);
    }

    @Test
    void shouldNotRemind_whenTheTdIsFiveYearsOld() {
        // With the adult Tdap given at 40, only the 10-year Td interval holds the reminder back.
        assertThat(fireRules(adultWith("Tdap", 30, "Td", 5)).getWarnings()).doesNotContain(REPEAT_TD, ADULT_TDAP);
    }

    @Test
    void shouldNotRemind_whenAnOlderTdWasFollowedByARecentTdap() {
        assertThat(fireRules(adultWith("Td", 12, "Tdap", 2)).getWarnings()).doesNotContain(REPEAT_TD);
    }

    @Test
    void shouldAskForATdOnce_whenTheOnlyTetanusVaccineIsATdapElevenYearsOld() {
        assertThat(fireRules(adultWith("Tdap", 11)).getWarnings()).contains(NEEDS_TD).doesNotContain(REPEAT_TD, ADULT_TDAP);
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
    void shouldAskOnlyForTheAdultTdap_whenAnAdultsOnlyTetanusVaccineIsATdpIpvHibTwelveYearsOld() {
        // The first Td is due too, but the adult Tdap is the one dose to give (#5048); the Td rule's counting of a
        // TdP-IPV-Hib booster is covered at 16 (shouldAskForATd_whenATdpIpvHibBoosterGivenAtFiveIsElevenYearsOld).
        assertThat(fireRules(adultWith("TdP-IPV-Hib", 12)).getWarnings())
                .containsOnlyOnce(ADULT_TDAP).doesNotContain(NEEDS_TD, REPEAT_TD, NEEDS_TDAP);
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
    void shouldAskForTheTeenTdap_whenTheSchoolBoosterWasRecordedAsDtIpv() {
        // DT-IPV (and the other rarely used childhood products) is not counted as the 4-6-year booster.
        assertThat(fireRules(personWith(15, "DT-IPV", 10)).getWarnings()).containsOnlyOnce(NEEDS_TDAP);
    }

    @Test
    void shouldAskForTheTeenTdap_whenOnlyATdIpvIsOnFile() {
        // A Td-IPV has no pertussis component, so the teen dose is still due.
        assertThat(fireRules(personWith(15, "Td-IPV", 2)).getWarnings()).containsOnlyOnce(NEEDS_TDAP).doesNotContain(NEEDS_TD);
    }

    @Test
    void shouldNotAskForTheTeenTdapYet_whenAFifteenYearOldsBoosterIsSevenYearsOld() {
        assertThat(fireRules(personWith(15, "DTaP-IPV", 7)).getWarnings()).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldNotAskForTheTeenTdapButForTheAdultOne_whenTheTdapOnFileWasGivenAtFifteen() {
        assertThat(fireRules(personWith(30, "Tdap-IPV", 25, "Tdap", 15)).getWarnings())
                .doesNotContain(NEEDS_TDAP).containsOnlyOnce(ADULT_TDAP);
    }

    @Test
    void shouldAskForBothTheTdapAndATd_whenTheLastTetanusVaccineIsATdapIpvBoosterTenYearsOld() {
        assertThat(fireRules(personWith(15, "Tdap-IPV", 10)).getWarnings())
                .contains(NEEDS_TDAP, NEEDS_TD).doesNotContain(REPEAT_TD);
    }

    @Test
    void shouldAskForATd_whenASeventeenYearOldsOnlyTetanusVaccineIsATdIpvTwelveYearsOld() {
        // Under 18 the adult Tdap does not apply, so the first Td rule's counting of a Td-IPV shows on its own.
        assertThat(fireRules(personWith(17, "Td-IPV", 12)).getWarnings())
                .containsOnlyOnce(NEEDS_TD).doesNotContain(NEEDS_TDAP, REPEAT_TD, ADULT_TDAP);
    }

    @Test
    void shouldAskOnlyForTheAdultTdap_whenAnAdultsOnlyTetanusVaccineIsATdIpvTwelveYearsOld() {
        assertThat(fireRules(adultWith("Td-IPV", 12)).getWarnings())
                .containsOnlyOnce(ADULT_TDAP).doesNotContain(NEEDS_TD, NEEDS_TDAP, REPEAT_TD);
    }

    @Test
    void shouldNotAskForATdYet_whenAnOldTdapWasFollowedByARecentTdIpv() {
        assertThat(fireRules(adultWith("Tdap", 15, "Td-IPV", 3)).getWarnings()).doesNotContain(NEEDS_TD);
    }

    @Test
    void shouldAskForATd_whenATdapWasFollowedByATdapIpvElevenYearsAgo() {
        assertThat(fireRules(adultWith("Tdap", 20, "Tdap-IPV", 11)).getWarnings()).contains(NEEDS_TD);
    }

    // Adult Tdap (#5048). Canadian Immunization Guide, Pertussis vaccine: "Adults who have not received an adult dose
    // of pertussis-containing vaccine should receive one dose of Tdap vaccine, which can be administered regardless of
    // the interval since the last dose of tetanus and diphtheria toxoid-containing vaccine."

    @Test
    void shouldAskForTheAdultTdapOnce_whenAnAdultHasNoTetanusRecord() {
        assertThat(fireRules(personWith(30)).getWarnings())
                .containsOnlyOnce(ADULT_TDAP).doesNotContain(NEEDS_TDAP, NEEDS_TD, REPEAT_TD);
    }

    @Test
    void shouldAskForTheAdultTdap_atExactlyEighteen() {
        assertThat(fireRules(personWith(18)).getWarnings()).containsOnlyOnce(ADULT_TDAP);
    }

    @Test
    void shouldNotAskForTheAdultTdap_atSeventeen() {
        assertThat(fireRules(personWith(17, "DTaP-IPV", 12)).getWarnings()).doesNotContain(ADULT_TDAP).contains(NEEDS_TDAP);
    }

    @Test
    void shouldAskForTheAdultTdapAndNotTheTeenOne_whenAnAdultsSchoolBoosterIsOld() {
        assertThat(fireRules(personWith(25, "DTaP-IPV", 20)).getWarnings())
                .containsOnlyOnce(ADULT_TDAP).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldAskForTheAdultTdap_whenTheTdapWasGivenAtSeventeenYearsElevenMonths() {
        assertThat(fireRules(personWithDosesAtAgeInMonths(30, "Tdap", 215)).getWarnings()).containsOnlyOnce(ADULT_TDAP);
    }

    @Test
    void shouldNotAskForTheAdultTdap_whenTheTdapWasGivenAtEighteen() {
        assertThat(fireRules(personWithDosesAtAgeInMonths(30, "Tdap", 216)).getWarnings()).doesNotContain(ADULT_TDAP);
    }

    @Test
    void shouldNotAskForTheAdultTdap_whenATdapIpvWasGivenAsAnAdult() {
        assertThat(fireRules(personWith(40, "Tdap-IPV", 10)).getWarnings()).doesNotContain(ADULT_TDAP, NEEDS_TDAP);
    }

    @Test
    void shouldAskForTheAdultTdap_regardlessOfARecentTd() {
        assertThat(fireRules(adultWith("Td", 2)).getWarnings())
                .containsOnlyOnce(ADULT_TDAP).doesNotContain(NEEDS_TD, REPEAT_TD);
    }

    @Test
    void shouldAskOnlyForTheAdultTdap_whenARepeatTdIsAlsoDue() {
        // One Tdap dose is also the 10-year tetanus booster, so "Needs repeat Td" waits until it is on file.
        assertThat(fireRules(adultWith("Td", 12)).getWarnings())
                .containsOnlyOnce(ADULT_TDAP).doesNotContain(REPEAT_TD, NEEDS_TD);
    }

    @Test
    void shouldNotAskForTheAdultTdap_whenTheTdapIsMarkedNeverRemind() {
        Prevention adult = personWith(30);
        adult.addPreventionItem(new PreventionItem("Tdap", yearsAgo(20), "1", null));
        assertThat(fireRules(adult).getWarnings()).doesNotContain(ADULT_TDAP);
    }

    // A school booster given late (#5048): the teen Tdap follows it by 10 years, as the guide's catch-up schedule for
    // children 7 to 17 has it (Part 1, Table 3: "Tdap ... 10 years after last dose of Tdap-IPV"), so a booster given
    // at 7 is followed by the reminder at 17, not 16.

    @Test
    void shouldNotAskForTheTeenTdapAtSixteen_whenTheSchoolBoosterWasGivenAtSeven() {
        assertThat(fireRules(personWith(16, "DTaP-IPV", 9)).getWarnings()).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldAskForTheTeenTdapAtSeventeen_whenTheSchoolBoosterGivenAtSevenIsTenYearsOld() {
        assertThat(fireRules(personWith(17, "DTaP-IPV", 10)).getWarnings()).containsOnlyOnce(NEEDS_TDAP);
    }

    @Test
    void shouldAskForTheTeenTdapAtSeventeen_whenACatchUpTdapIpvGivenAtSevenIsTenYearsOld() {
        assertThat(fireRules(personWith(17, "Tdap-IPV", 10)).getWarnings()).containsOnlyOnce(NEEDS_TDAP);
    }

    @Test
    void shouldNotAskForTheTeenTdapAtSeventeen_whenTheSchoolBoosterGivenAtEightIsNineYearsOld() {
        assertThat(fireRules(personWith(17, "DTaP-IPV", 9)).getWarnings()).doesNotContain(NEEDS_TDAP);
    }

    @Test
    void shouldNotAskForTheTeenTdap_whenACatchUpTdapIpvWasGivenAtTen() {
        // Ten years after it is 20: the one Tdap then is also the adult dose.
        assertThat(fireRules(personWith(16, "Tdap-IPV", 6)).getWarnings()).doesNotContain(NEEDS_TDAP, ADULT_TDAP);
    }

    @Test
    void shouldNotAskForTheTeenTdapAtEighteen_whenTheLateBoosterIsUnderTenYearsOld() {
        // From 18 the adult Tdap takes over.
        assertThat(fireRules(personWith(18, "DTaP-IPV", 9)).getWarnings())
                .doesNotContain(NEEDS_TDAP).containsOnlyOnce(ADULT_TDAP);
    }

    @Test
    void shouldNotAskForTheAdultTdap_atSeventeenYearsAndThreeHundredSixtyFourDays() {
        Calendar birth = Calendar.getInstance();
        birth.setTime(yearsAgo(18));
        birth.add(Calendar.DAY_OF_MONTH, 1);
        assertThat(fireRules(new Prevention("M", birth.getTime())).getWarnings()).doesNotContain(ADULT_TDAP);
    }

    @Test
    void shouldRemindOfARepeatTd_whenTheAdultTdapIsMarkedNeverRemind() {
        // "Never remind" silences the adult Tdap; the Td rules then decide, and the Td is 12 years old.
        Prevention adult = adultWith("Td", 12);
        adult.addPreventionItem(new PreventionItem("Tdap", yearsAgo(60), "1", null));
        assertThat(fireRules(adult).getWarnings()).containsOnlyOnce(REPEAT_TD).doesNotContain(ADULT_TDAP);
    }

    // A refused or "ineligible" Tdap is not a dose to the Td rules (#5048): dismissing the adult Tdap must not
    // silence an overdue Td booster for 10 years.

    @Test
    void shouldRemindOfARepeatTd_whenTheAdultTdapWasRefusedToday() {
        Prevention adult = adultWith("Td", 12);
        adult.addPreventionItem(declinedTdapToday(false));
        assertThat(fireRules(adult).getWarnings()).containsOnlyOnce(REPEAT_TD).doesNotContain(ADULT_TDAP, NEEDS_TD);
    }

    @Test
    void shouldRemindOfARepeatTd_whenTheAdultTdapWasRecordedIneligibleToday() {
        Prevention adult = adultWith("Td", 12);
        adult.addPreventionItem(declinedTdapToday(true));
        assertThat(fireRules(adult).getWarnings()).containsOnlyOnce(REPEAT_TD).doesNotContain(ADULT_TDAP);
    }

    @Test
    void shouldAskForATd_whenTheAdultTdapWasRefusedAfterAnOldTdIpv() {
        Prevention adult = adultWith("Td-IPV", 12);
        adult.addPreventionItem(declinedTdapToday(false));
        assertThat(fireRules(adult).getWarnings()).containsOnlyOnce(NEEDS_TD).doesNotContain(ADULT_TDAP, REPEAT_TD);
    }

    @Test
    void shouldNotAskForATd_whenTheOnlyTetanusRecordIsARefusedTdap() {
        Prevention adult = adultWith();
        adult.addPreventionItem(declinedTdapToday(false));
        assertThat(fireRules(adult).getWarnings()).doesNotContain(NEEDS_TD, REPEAT_TD, ADULT_TDAP);
    }

    // DTaP-IPV is a childhood product (#5048): its next-date reminders ("DTaP-IPV 2.1", "3.1", "4.1") stop at 7, so
    // an adult's old record raises no DTaP-IPV reminder beside the adult Tdap.

    @Test
    void shouldAskOnlyForTheAdultTdap_whenAnAdultHasOneOldDtapIpv() {
        assertThat(fireRules(personWithDosesAtAgeInMonths(30, "DTaP-IPV", 2)).getWarnings())
                .doesNotContain(SECOND_DTAP_IPV).containsOnlyOnce(ADULT_TDAP);
    }

    @Test
    void shouldAskOnlyForTheAdultTdap_whenAnAdultHasTwoOldDtapIpv() {
        assertThat(fireRules(personWithDosesAtAgeInMonths(30, "DTaP-IPV", 2, 4)).getWarnings())
                .doesNotContain(THIRD_DTAP_IPV).containsOnlyOnce(ADULT_TDAP);
    }

    @Test
    void shouldStillAskForTheSecondDtapIpv_whenASixYearOldHasOneDose() {
        assertThat(fireRules(personWithDosesAtAgeInMonths(6, "DTaP-IPV", 2)).getWarnings()).contains(SECOND_DTAP_IPV);
    }

    @Test
    void shouldNotAskForTheSecondDtapIpv_whenASevenYearOldHasOneDose() {
        assertThat(fireRules(personWithDosesAtAgeInMonths(7, "DTaP-IPV", 2)).getWarnings()).doesNotContain(SECOND_DTAP_IPV);
    }
}
