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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.dao.Icd10Dao;
import io.github.carlos_emr.carlos.commn.dao.Icd9Dao;
import io.github.carlos_emr.carlos.commn.dao.SnomedCoreDao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the coding-system DAO allowlist in
 * {@link DemographicExportAction42Action}.
 *
 * <p>Verifies that only known {@link AbstractCodeSystemDao} implementations are
 * reachable and that the allowlist cannot be modified at runtime, guarding
 * against CWE-470 (unsafe reflective class instantiation).</p>
 *
 * @since 2026-04-14
 */
@Tag("unit")
@Tag("security")
@DisplayName("DemographicExportAction42Action coding-system allowlist")
class DemographicExportAction42ActionCodeSystemAllowlistTest {

    // -------------------------------------------------------------------------
    // Allowlist contents
    // -------------------------------------------------------------------------

    /** Verifies the allowlist contains the expected coding system to DAO class mappings. */
    @Nested
    @DisplayName("ALLOWED_CODE_SYSTEM_DAOS allowlist")
    class AllowlistContents {

        @Test
        @DisplayName("should contain exactly the expected coding systems")
        void shouldContainExpectedCodingSystems() {
            assertThat(DemographicExportAction42Action.ALLOWED_CODE_SYSTEM_DAOS)
                    .containsOnlyKeys("icd9", "icd10", "snomedcore", "msp");
        }

        @Test
        @DisplayName("should map icd9 to Icd9Dao")
        void shouldMapIcd9_toIcd9Dao() {
            assertThat(DemographicExportAction42Action.ALLOWED_CODE_SYSTEM_DAOS.get("icd9"))
                    .isEqualTo(Icd9Dao.class);
        }

        @Test
        @DisplayName("should map icd10 to Icd10Dao")
        void shouldMapIcd10_toIcd10Dao() {
            assertThat(DemographicExportAction42Action.ALLOWED_CODE_SYSTEM_DAOS.get("icd10"))
                    .isEqualTo(Icd10Dao.class);
        }

        @Test
        @DisplayName("should map snomedcore to SnomedCoreDao")
        void shouldMapSnomedcore_toSnomedCoreDao() {
            assertThat(DemographicExportAction42Action.ALLOWED_CODE_SYSTEM_DAOS.get("snomedcore"))
                    .isEqualTo(SnomedCoreDao.class);
        }

        @Test
        @DisplayName("should map msp to DiagnosticCodeDao")
        void shouldMapMsp_toDiagnosticCodeDao() {
            assertThat(DemographicExportAction42Action.ALLOWED_CODE_SYSTEM_DAOS.get("msp"))
                    .isEqualTo(DiagnosticCodeDao.class);
        }

        @Test
        @DisplayName("should not be modifiable at runtime")
        void shouldNotBeModifiable_atRuntime() {
            assertThatThrownBy(() ->
                    DemographicExportAction42Action.ALLOWED_CODE_SYSTEM_DAOS.put("evil", Icd9Dao.class))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // -------------------------------------------------------------------------
    // Rejection of unknown keys
    // -------------------------------------------------------------------------

    /** Verifies that unknown or malicious coding system keys are rejected by the allowlist. */
    @Nested
    @DisplayName("allowlist lookup rejection")
    class AllowlistLookupRejection {

        @Test
        @DisplayName("should return null for unknown coding system")
        void shouldReturnNull_forUnknownCodingSystem() {
            assertThat(DemographicExportAction42Action.ALLOWED_CODE_SYSTEM_DAOS.get("evil"))
                    .isNull();
        }

        @Test
        @DisplayName("should return null for empty string")
        void shouldReturnNull_forEmptyString() {
            assertThat(DemographicExportAction42Action.ALLOWED_CODE_SYSTEM_DAOS.get(""))
                    .isNull();
        }

        @Test
        @DisplayName("should return null for case-sensitive mismatch")
        void shouldReturnNull_forCaseSensitiveMismatch() {
            assertThat(DemographicExportAction42Action.ALLOWED_CODE_SYSTEM_DAOS.get("ICD9"))
                    .isNull();
        }
    }
}
