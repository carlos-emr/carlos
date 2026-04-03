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
@Tag("fast")
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
    @DisplayName("should return correct subtype for each entry in the allowlist registry")
    void shouldInstantiateAllRegisteredFormTypes() {
        // Spot-check a representative sample of allowlisted keys to ensure the
        // registry is correctly wired end-to-end.
        String[] sampleKeys = {
                "2MinWalk", "AdfV2", "Annual", "AnnualV2",
                "BCAR", "Falls", "GripStrength", "GrowthChart",
                "MMSE", "Rourke", "Rourke2006", "SF36", "chf"
        };

        for (String key : sampleKeys) {
            FrmRecord result = factory.factory(key);
            assertThat(result)
                    .as("factory(\"%s\") should return a non-null FrmRecord", key)
                    .isNotNull()
                    .isInstanceOf(FrmRecord.class);
        }
    }
}
