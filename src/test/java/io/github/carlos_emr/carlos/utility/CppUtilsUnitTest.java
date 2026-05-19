/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CppUtils} clinical practice guideline code management.
 *
 * @since 2026-03-31
 */
@DisplayName("CppUtils Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
@Tag("clinical")
@ResourceLock("CppUtils.cppCodes")
class CppUtilsUnitTest {

    private static final String[] DEFAULT_CPP_CODES = {
            "OMeds", "SocHistory", "MedHistory", "Concerns",
            "FamHistory", "Reminders", "RiskFactors",
            "OcularMedication", "TicklerNote", "ExternalNote"
    };

    @BeforeEach
    void setUp() {
        CppUtils.cppCodes = Arrays.copyOf(DEFAULT_CPP_CODES, DEFAULT_CPP_CODES.length);
    }

    @AfterEach
    void tearDown() {
        CppUtils.cppCodes = Arrays.copyOf(DEFAULT_CPP_CODES, DEFAULT_CPP_CODES.length);
    }

    @Nested
    @DisplayName("cppCodes default values")
    class DefaultValues {

        @Test
        @DisplayName("should contain OMeds code")
        void shouldContainOMeds_forDefaultCodes() {
            assertThat(Arrays.asList(CppUtils.cppCodes)).contains("OMeds");
        }

        @Test
        @DisplayName("should contain SocHistory code")
        void shouldContainSocHistory_forDefaultCodes() {
            assertThat(Arrays.asList(CppUtils.cppCodes)).contains("SocHistory");
        }

        @Test
        @DisplayName("should contain MedHistory code")
        void shouldContainMedHistory_forDefaultCodes() {
            assertThat(Arrays.asList(CppUtils.cppCodes)).contains("MedHistory");
        }

        @Test
        @DisplayName("should contain all 10 default CPP codes")
        void shouldContainAllDefaultCodes_forDefaultCodes() {
            assertThat(CppUtils.cppCodes).contains(DEFAULT_CPP_CODES);
        }
    }

    @Nested
    @DisplayName("addCppCode")
    class AddCppCode {

        @Test
        @DisplayName("should append new code to array")
        void shouldAppendNewCode_toArray() {
            int originalLength = CppUtils.cppCodes.length;
            CppUtils.addCppCode("TestCode");
            assertThat(CppUtils.cppCodes).hasSize(originalLength + 1);
            assertThat(CppUtils.cppCodes[CppUtils.cppCodes.length - 1]).isEqualTo("TestCode");
        }
    }
}
