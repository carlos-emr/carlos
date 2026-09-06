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
package io.github.carlos_emr.carlos.lab.ca.all.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the version-chain rules that decide which lab versions an acknowledgement files.
 *
 * <p>Getting this wrong is what left acknowledged labs sitting in the inbox: the inbox shows one
 * row per accession chain, so unless the older versions are filed the row re-appears pointing at
 * the previous version.
 *
 * @since 2026-09-06
 */
@DisplayName("Lab version chain")
@Tag("unit")
@Tag("lab")
class LabVersionChainUnitTest {

    @Test
    @DisplayName("should keep the chain order when parsing, because order is the version order")
    void shouldPreserveOrder_whenParsingChain() {
        assertThat(LabVersionChain.parse("169,170,171")).containsExactly(169, 170, 171);
    }

    @Test
    @DisplayName("should tolerate whitespace and junk entries in a posted chain")
    void shouldSkipJunkEntries_whenParsingChain() {
        assertThat(LabVersionChain.parse(" 169 , ,abc,170 ")).containsExactly(169, 170);
    }

    @Test
    @DisplayName("should return nothing when the chain is null, blank or has no lab numbers")
    void shouldReturnEmpty_forMissingChain() {
        assertThat(LabVersionChain.parse(null)).isEmpty();
        assertThat(LabVersionChain.parse("   ")).isEmpty();
        assertThat(LabVersionChain.parse("abc,,")).isEmpty();
    }

    @Test
    @DisplayName("should return the versions before the acknowledged one, oldest first")
    void shouldReturnPrecedingVersions_whenAcknowledgingNewest() {
        assertThat(LabVersionChain.olderThan(171, "169,170,171")).containsExactly(169, 170);
    }

    @Test
    @DisplayName("should leave newer versions alone when an older version is acknowledged")
    void shouldExcludeNewerVersions_whenAcknowledgingOlderVersion() {
        assertThat(LabVersionChain.olderThan(170, "169,170,171")).containsExactly(169);
    }

    @Test
    @DisplayName("should return nothing when the acknowledged lab is the only version")
    void shouldReturnEmpty_forSingleVersionChain() {
        assertThat(LabVersionChain.olderThan(170, "170")).isEmpty();
    }

    @Test
    @DisplayName("should file nothing rather than throw when the chain describes another lab")
    void shouldReturnEmpty_whenChainDoesNotContainTheAcknowledgedLab() {
        assertThatCode(() -> LabVersionChain.olderThan(999, "169,170,171")).doesNotThrowAnyException();
        assertThat(LabVersionChain.olderThan(999, "169,170,171")).isEmpty();
        assertThat(LabVersionChain.olderThan(999, null)).isEmpty();
    }

    @Test
    @DisplayName("should report whether a chain describes the acknowledged lab")
    void shouldReportMembership_forSuppliedChain() {
        assertThat(LabVersionChain.describes(170, "169,170")).isTrue();
        assertThat(LabVersionChain.describes(171, "169,170")).isFalse();
        assertThat(LabVersionChain.describes(171, null)).isFalse();
    }
}
