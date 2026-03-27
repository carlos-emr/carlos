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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.commons.io.IOUtils;
import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.Vacancy;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.WaitListManager.AdmissionDemographicPair;
import io.github.carlos_emr.carlos.utility.VelocityUtils;

/**
 * Unit tests for {@link WaitListManager}.
 *
 * <p>Tests the Velocity template merging for wait list email notifications
 * (admission and vacancy contexts) and properties file loading.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("manager")
@DisplayName("WaitListManager")
class WaitListManagerUnitTest {

    @Nested
    @DisplayName("waitListProperties")
    class WaitListProperties {

        @Test
        @DisplayName("should load properties file with from_address")
        void shouldLoadProperties_withFromAddress() {
            String fromAddress = WaitListManager.waitListProperties.getProperty("from_address");
            assertThat(fromAddress).isNotNull();
        }

        @Test
        @DisplayName("should have non-empty properties")
        void shouldHaveNonEmptyProperties() {
            assertThat(WaitListManager.waitListProperties).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("admission velocity context")
    class AdmissionVelocityContext {

        @Test
        @DisplayName("should merge admission template with demographic data")
        void shouldMergeTemplate_withDemographicData() throws IOException {
            Program program = new Program();
            program.setName("MyProgramName");

            String notes = "some of my notes";

            Calendar startCal = new GregorianCalendar(2012, 3, 4);
            Calendar endCal = new GregorianCalendar(2012, 3, 5);

            AdmissionDemographicPair a1 = new AdmissionDemographicPair();
            Demographic d1 = new Demographic();
            d1.setLastName("lastName1");
            d1.setFirstName("firstName1");
            d1.setSex("sex1");
            a1.setDemographic(d1);

            AdmissionDemographicPair a2 = new AdmissionDemographicPair();
            Demographic d2 = new Demographic();
            d2.setLastName("lastName2");
            d2.setFirstName("firstName2");
            d2.setSex("sex2");
            a2.setDemographic(d2);

            ArrayList<AdmissionDemographicPair> pairs = new ArrayList<>();
            pairs.add(a1);
            pairs.add(a2);

            VelocityContext ctx = WaitListManager.getAdmissionVelocityContext(
                    program, notes, startCal.getTime(), endCal.getTime(), pairs);

            InputStream templateIs = WaitListManagerUnitTest.class
                    .getResourceAsStream("/wait_list_velocity_template.txt");
            String template = IOUtils.toString(templateIs);

            String mergedTemplate = VelocityUtils.velocityEvaluate(ctx, template);

            InputStream expectedIs = WaitListManagerUnitTest.class
                    .getResourceAsStream("/wait_list_velocity_template_results.txt");
            String expectedResults = IOUtils.toString(expectedIs);

            assertThat(mergedTemplate).isEqualTo(expectedResults);
        }

        @Test
        @DisplayName("should include program in context")
        void shouldIncludeProgram_inContext() {
            Program program = new Program();
            program.setName("TestProgram");

            VelocityContext ctx = WaitListManager.getAdmissionVelocityContext(
                    program, null, null, null, new ArrayList<>());

            assertThat(ctx.get("program")).isSameAs(program);
        }

        @Test
        @DisplayName("should exclude notes when null")
        void shouldExcludeNotes_whenNull() {
            Program program = new Program();

            VelocityContext ctx = WaitListManager.getAdmissionVelocityContext(
                    program, null, null, null, new ArrayList<>());

            assertThat(ctx.containsKey("notes")).isFalse();
        }
    }

    @Nested
    @DisplayName("vacancy velocity context")
    class VacancyVelocityContext {

        @Test
        @DisplayName("should merge vacancy template with data")
        void shouldMergeTemplate_withVacancyData() throws IOException {
            Vacancy vacancy = new Vacancy();
            vacancy.setTemplateId(1234);
            vacancy.setStatus("test status");

            Calendar cal = new GregorianCalendar(2012, 3, 4);

            VelocityContext ctx = WaitListManager.getVacancyVelocityContext(
                    vacancy, "test notes", cal.getTime());

            InputStream templateIs = WaitListManagerUnitTest.class
                    .getResourceAsStream("/wait_list_immediate_vacancy_email_template.txt");
            String template = IOUtils.toString(templateIs);

            String mergedTemplate = VelocityUtils.velocityEvaluate(ctx, template);

            InputStream expectedIs = WaitListManagerUnitTest.class
                    .getResourceAsStream("/wait_list_immediate_vacancy_email_template_results.txt");
            String expectedResults = IOUtils.toString(expectedIs);

            assertThat(mergedTemplate).isEqualTo(expectedResults);
        }

        @Test
        @DisplayName("should include vacancy in context")
        void shouldIncludeVacancy_inContext() {
            Vacancy vacancy = new Vacancy();

            VelocityContext ctx = WaitListManager.getVacancyVelocityContext(
                    vacancy, null, null);

            assertThat(ctx.get("vacancy")).isSameAs(vacancy);
        }

        @Test
        @DisplayName("should exclude notes and date when null")
        void shouldExcludeOptionalFields_whenNull() {
            Vacancy vacancy = new Vacancy();

            VelocityContext ctx = WaitListManager.getVacancyVelocityContext(
                    vacancy, null, null);

            assertThat(ctx.containsKey("notes")).isFalse();
            assertThat(ctx.containsKey("date")).isFalse();
        }
    }
}
