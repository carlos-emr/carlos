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
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;

import io.github.carlos_emr.carlos.drools.DroolsCompilationException;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDateDTO;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Canadian Immunization Guide rules of #5048 and imported history of uncertain timing.
 *
 * <p>Several of the shipped {@code prevention.drl} functions ({@code dosesGiven}, {@code lastDoseGiven},
 * {@code monthsSinceLastDoseGiven}, {@code latestRotaNextDate}, {@code rotaDeclinedSinceLastDose}) read each
 * {@link PreventionItem}'s date directly rather than through {@link Prevention}'s guarded accessors, which refuse
 * to pick a latest event from a synthetic anchor. They rely on {@link PreventionDSImpl#getMessages(Prevention)}
 * refusing to fire any rule when an imported record's date is only partly known (see
 * {@code docs/cds-prevention-date-contract.md}). These tests fire the shipped rules through that entry point and
 * pin that contract for the new rotavirus and adult Tdap rules: exact history is evaluated, and history with an
 * uncertain imported date yields only the review warning, never a due or dose decision.</p>
 *
 * @since 2026-11-27
 */
@Tag("unit")
@Tag("drools")
@Tag("prevention")
@DisplayName("CIG prevention rules refuse imported history of uncertain timing")
class PreventionCigRulesDateReviewDroolsUnitTest {

    private static final String NEEDS_SECOND_ROTA = "Needs Second Rota vaccine";
    private static final String NEEDS_THIRD_ROTA = "Needs Third Rota vaccine";
    private static final String NEEDS_ADULT_TDAP = "Needs adult Tdap (one dose)";
    private static final String NEEDS_TD = "Needs Td";
    private static final String NEEDS_REPEAT_TD = "Needs repeat Td";
    private static final String REVIEW = "review history before applying due or dose rules";

    private static KieBase kieBase;
    private KieBase previousRuleBase;

    @BeforeAll
    static void loadPreventionDrl() throws DroolsCompilationException {
        URL url = PreventionCigRulesDateReviewDroolsUnitTest.class.getResource("/oscar/prevention/prevention.drl");
        assertThat(url).as("prevention.drl must be on the classpath").isNotNull();
        kieBase = DroolsHelper.loadFromUrl(url);
    }

    /** {@link PreventionDSImpl}'s rule base is static: install the shipped rules for each test only. */
    @BeforeEach
    void installShippedRules() {
        previousRuleBase = PreventionDSImpl.kieBase;
        PreventionDSImpl.kieBase = kieBase;
    }

    @AfterEach
    void restoreRuleBase() {
        PreventionDSImpl.kieBase = previousRuleBase;
    }

    /**
     * Fires the shipped rules through the production entry point, so its date-review guard applies.
     *
     * @param history Prevention the patient's prevention fact
     * @return Prevention the same fact with its warnings
     * @throws Exception Exception when rule evaluation fails
     */
    private static Prevention evaluate(Prevention history) throws Exception {
        return new PreventionDSImpl().getMessages(history);
    }

    /**
     * An imported (CDS) record of the type, given the given number of days ago, with the given date precision.
     *
     * @param type String the prevention type
     * @param daysAgo int days before today the dose is dated
     * @param format String the imported precision ({@code "YYYY"}, {@code "YYYY-MM"}), or {@code null} for an exact date
     * @return PreventionItem the rule fact for the record
     */
    private static PreventionItem imported(String type, int daysAgo, String format) {
        io.github.carlos_emr.carlos.commn.model.Prevention record = new io.github.carlos_emr.carlos.commn.model.Prevention();
        io.github.carlos_emr.carlos.commn.model.PreventionExt provenance = new io.github.carlos_emr.carlos.commn.model.PreventionExt();
        provenance.setKeyval("cdsDatePrecision");
        record.addPreventionExt(provenance);
        record.setPreventionType(type);
        record.setPreventionDate(java.sql.Date.valueOf(LocalDate.now().minusDays(daysAgo)));
        return new PreventionItem(record, PreventionDateDTO.from(record.getPreventionDate(), format));
    }

    private static Prevention patientBornDaysAgo(int days) {
        return new Prevention("F", java.sql.Date.valueOf(LocalDate.now().minusDays(days)));
    }

    private static Prevention patientBornYearsAgo(int years) {
        return new Prevention("F", java.sql.Date.valueOf(LocalDate.now().minusYears(years)));
    }

    private static long count(Prevention evaluated, String message) {
        return evaluated.getWarnings().stream().filter(message::equals).count();
    }

    @Test
    void shouldRemindThirdRotaDose_whenImportedDatesAreExact() throws Exception {
        Prevention infant = patientBornDaysAgo(150);
        infant.addPreventionItem(imported("Rota-1", 60, null));
        infant.addPreventionItem(imported("Rota-5", 30, null));

        Prevention evaluated = evaluate(infant);

        assertThat(evaluated.isDateReviewRequired()).isFalse();
        assertThat(count(evaluated, NEEDS_THIRD_ROTA)).isEqualTo(1);
        assertThat(evaluated.getWarnings()).noneMatch(warning -> warning.contains(REVIEW));
    }

    @Test
    void shouldOnlyAskForReview_whenAnImportedRotaDoseHasMonthPrecision() throws Exception {
        Prevention infant = patientBornDaysAgo(150);
        infant.addPreventionItem(imported("Rota-1", 60, null));
        infant.addPreventionItem(imported("Rota-5", 30, "YYYY-MM"));

        Prevention evaluated = evaluate(infant);

        assertThat(evaluated.isDateReviewRequired()).isTrue();
        assertThat(count(evaluated, NEEDS_SECOND_ROTA)).isZero();
        assertThat(count(evaluated, NEEDS_THIRD_ROTA)).isZero();
        assertThat(evaluated.getWarningMsgs()).containsKey("Rota-5");
        assertThat(evaluated.getWarnings()).allMatch(warning -> warning.contains(REVIEW));
    }

    @Test
    void shouldRemindAdultTdap_whenTheOnlyImportedTetanusDoseIsAnExactTd() throws Exception {
        Prevention adult = patientBornYearsAgo(30);
        adult.addPreventionItem(imported("Td", 2 * 365, null));

        Prevention evaluated = evaluate(adult);

        assertThat(count(evaluated, NEEDS_ADULT_TDAP)).isEqualTo(1);
        assertThat(count(evaluated, NEEDS_TD)).isZero();
        assertThat(count(evaluated, NEEDS_REPEAT_TD)).isZero();
    }

    @Test
    void shouldOnlyAskForReview_whenAnImportedTdapHasYearPrecision() throws Exception {
        Prevention adult = patientBornYearsAgo(30);
        adult.addPreventionItem(imported("Td", 12 * 365, null));
        adult.addPreventionItem(imported("Tdap", 2 * 365, "YYYY"));

        Prevention evaluated = evaluate(adult);

        assertThat(evaluated.isDateReviewRequired()).isTrue();
        assertThat(count(evaluated, NEEDS_ADULT_TDAP)).isZero();
        assertThat(count(evaluated, NEEDS_TD)).isZero();
        assertThat(count(evaluated, NEEDS_REPEAT_TD)).isZero();
        assertThat(evaluated.getWarningMsgs()).containsKey("Tdap");
        assertThat(evaluated.getWarnings()).allMatch(warning -> warning.contains(REVIEW));
    }

    @Test
    void shouldReturnMinusOneForCalendarDaysSinceLast_whenImportedDateRequiresReview() {
        Prevention infant = patientBornDaysAgo(150);
        infant.addPreventionItem(imported("Rota-1", 60, "YYYY-MM"));

        assertThat(infant.getCalendarDaysSinceLast("Rota-1")).isEqualTo(-1);
    }
}
