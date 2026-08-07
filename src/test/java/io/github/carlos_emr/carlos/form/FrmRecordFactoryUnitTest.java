/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.form;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FrmRecordFactory} whitelist validation.
 *
 * <p>These tests verify that the factory rejects disallowed class names (including
 * null, empty, path-traversal, and arbitrary class names) while still accepting
 * the known-good form class name suffixes. This guards against CWE-470 (unsafe
 * reflective class instantiation from user input).</p>
 *
 * @since 2026-04-06
 */
@Tag("unit")
@Tag("form")
@DisplayName("FrmRecordFactory whitelist validation")
class FrmRecordFactoryUnitTest {

    private static final FrmRecordFactory factory = new FrmRecordFactory();

    // -------------------------------------------------------------------------
    // Whitelist content
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ALLOWED_FORM_CLASSES whitelist")
    class AllowedFormClassesWhitelist {

        @Test
        @DisplayName("should contain all expected form class suffixes")
        void shouldContainAllExpectedFormClassSuffixes() {
            assertThat(FrmRecordFactory.ALLOWED_FORM_CLASSES)
                    .contains(
                            "2MinWalk", "AdfV2", "Annual", "AnnualV2",
                            "BCAR", "BCAR2007", "BCAR2012", "BCAR2020",
                            "BCBirthSumMo2008", "BCBrithSumMo", "BCClientChartChecklist",
                            "BCHP", "BCINR", "BCNewBorn", "BCNewBorn2008",
                            "CESD", "Caregiver", "Consultant", "CostQuestionnaire",
                            "Counseling", "CounsellorAssessment", "DischargeSummary",
                            "Falls", "GripStrength", "Growth0_36", "GrowthChart",
                            "HomeFalls", "ImmunAllergy", "IntakeInfo", "InternetAccess",
                            "Invoice", "LabReq", "LabReq07", "LabReq10",
                            "LateLifeFDIDisability", "LateLifeFDIFunction",
                            "MMSE", "MentalHealth", "MentalHealthForm1",
                            "MentalHealthForm14", "MentalHealthForm42",
                            "PalliativeCare", "PeriMenopausal", "Policy",
                            "PositionHazard", "ReceptionAssessment", "RhImmuneGlobulin",
                            "Rourke", "Rourke2006", "Rourke2009", "Rourke2017", "Rourke2020",
                            "SF36", "SF36Caregiver", "SatisfactionScale",
                            "SelfAdministered", "SelfAssessment", "SelfEfficacy",
                            "SelfManagement", "TreatmentPref", "chf"
                    );
        }

        @Test
        @DisplayName("should not be modifiable at runtime")
        void shouldNotBeModifiable_atRuntime() {
            assertThatThrownBy(() -> FrmRecordFactory.ALLOWED_FORM_CLASSES.add("ShouldNotBeAllowed"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // -------------------------------------------------------------------------
    // Rejection of disallowed inputs
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("factory() rejection of disallowed inputs")
    class FactoryRejection {

        @Test
        @DisplayName("should return null when form class name is null")
        void shouldReturnNull_whenFormClassNameIsNull() {
            assertThat(factory.factory(null)).isNull();
        }

        @Test
        @DisplayName("should return null when form class name is empty")
        void shouldReturnNull_whenFormClassNameIsEmpty() {
            assertThat(factory.factory("")).isNull();
        }

        @Test
        @DisplayName("should return null when form class name is an arbitrary class outside the package")
        void shouldReturnNull_whenFormClassNameIsArbitraryClass() {
            assertThat(factory.factory("java.lang.Runtime")).isNull();
        }

        @Test
        @DisplayName("should return null when form class name contains path traversal characters")
        void shouldReturnNull_whenFormClassNameContainsPathTraversalCharacters() {
            assertThat(factory.factory("../../../etc/passwd")).isNull();
        }

        @Test
        @DisplayName("should return null when form class name is a plausible injection attempt")
        void shouldReturnNull_whenFormClassNameIsInjectionAttempt() {
            assertThat(factory.factory("Annual; DROP TABLE demographic; --")).isNull();
        }

        @Test
        @DisplayName("should return null when form class name is a fully qualified class name")
        void shouldReturnNull_whenFormClassNameIsFullyQualifiedClassName() {
            // Users must not be able to instantiate arbitrary classes by prefixing with the package
            assertThat(factory.factory("io.github.carlos_emr.carlos.form.FrmAnnualRecord")).isNull();
        }
    }
}
