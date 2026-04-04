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
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.form;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FrmRecordFactory}.
 *
 * <p>Verifies the allowlist-based instantiation introduced to fix CWE-470 /
 * SonarCloud {@code javasecurity:S6173}.  No Spring context is required because
 * the factory itself has no Spring dependencies; a subset of concrete
 * {@link FrmRecord} classes that also have no Spring field-initializers are
 * used as representative allowed values.</p>
 *
 * @since 2026-04-03
 * @see FrmRecordFactory
 */
@DisplayName("FrmRecordFactory Unit Tests")
@Tag("unit")
@Tag("security")
class FrmRecordFactoryUnitTest {

    private FrmRecordFactory factory;

    @BeforeEach
    void setUp() {
        factory = new FrmRecordFactory();
    }

    @Test
    @DisplayName("should return correct FrmRecord subtype for known allowlisted key")
    void shouldReturnCorrectInstance_forKnownAllowlistedKey() {
        FrmRecord result = factory.factory("Annual");

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(FrmAnnualRecord.class);
    }

    @Test
    @DisplayName("should return a new distinct instance on each call for the same key")
    void shouldReturnDistinctInstance_onEachCall() {
        FrmRecord first = factory.factory("Annual");
        FrmRecord second = factory.factory("Annual");

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first).isNotSameAs(second);
    }

    @Test
    @DisplayName("should return null when null is passed")
    void shouldReturnNull_whenNullPassed() {
        FrmRecord result = factory.factory(null);

        assertThat(result).isNull();
    }

    @ParameterizedTest(name = "factory(\"{0}\") should return null")
    @DisplayName("should return null for unknown or disallowed form name")
    @ValueSource(strings = {
            "",
            "unknown",
            "../../etc/passwd",
            "java.lang.Runtime",
            "AnnualRecord",            // full class suffix — not a valid key
            "io.github.carlos_emr.carlos.form.FrmAnnualRecord"  // fully qualified name
    })
    void shouldReturnNull_forUnknownOrDisallowedFormName(String which) {
        FrmRecord result = factory.factory(which);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should instantiate every entry in the allowlist registry")
    void shouldInstantiateAllRegisteredFormTypes() {
        // Verify all 61 allowlisted keys are correctly wired end-to-end.
        // The security guarantee of the CWE-470 fix depends on the registry being
        // complete and each entry pointing to an instantiable class.
        // Note: Rourke2009/2017/2020 are included here — their SpringUtils.getBean()
        // calls are inside method bodies, not field initializers or constructors,
        // so plain getDeclaredConstructor().newInstance() succeeds without a Spring context.
        String[] allKeys = {
                "2MinWalk", "AdfV2", "Annual", "AnnualV2",
                "BCAR2007", "BCAR2012", "BCAR2020", "BCAR",
                "BCBirthSumMo2008",
                "BCBrithSumMo",  // intentional typo — matches FrmBCBrithSumMoRecord and existing callers
                "BCClientChartChecklist", "BCHP", "BCINR",
                "BCNewBorn2008", "BCNewBorn",
                "CESD", "Caregiver", "Consultant", "CostQuestionnaire",
                "Counseling", "CounsellorAssessment", "DischargeSummary",
                "Falls", "GripStrength", "Growth0_36", "GrowthChart",
                "HomeFalls", "ImmunAllergy", "IntakeInfo", "InternetAccess",
                "Invoice", "LabReq07", "LabReq10", "LabReq",
                "LateLifeFDIDisability", "LateLifeFDIFunction",
                "MMSE", "MentalHealthForm14", "MentalHealthForm1", "MentalHealthForm42", "MentalHealth",
                "PalliativeCare", "PeriMenopausal", "Policy", "PositionHazard",
                "ReceptionAssessment", "RhImmuneGlobulin",
                "Rourke2006", "Rourke2009", "Rourke2017", "Rourke2020", "Rourke",
                "SF36Caregiver", "SF36", "SatisfactionScale",
                "SelfAdministered", "SelfAssessment", "SelfEfficacy", "SelfManagement",
                "TreatmentPref", "chf"
        };

        for (String key : allKeys) {
            FrmRecord result = factory.factory(key);
            assertThat(result)
                    .as("factory(\"%s\") should return a non-null FrmRecord", key)
                    .isNotNull()
                    .isInstanceOf(FrmRecord.class);
        }
    }
}
