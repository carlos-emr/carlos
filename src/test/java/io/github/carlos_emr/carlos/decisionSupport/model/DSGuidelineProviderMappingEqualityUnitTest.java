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
package io.github.carlos_emr.carlos.decisionSupport.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link DSGuidelineProviderMapping} compares by provider and guideline, not by the shared
 * {@code AbstractModel} identifier. Its override used to cast and dereference its argument
 * unconditionally, so {@code null}, a foreign type or a mapping without keys threw (#5303).
 *
 * @since 2026-11-26
 */
@Tag("unit")
class DSGuidelineProviderMappingEqualityUnitTest {

    @Test
    @DisplayName("should equal a mapping for the same provider and guideline")
    void shouldBeEqual_forSameProviderAndGuideline() {
        DSGuidelineProviderMapping first = new DSGuidelineProviderMapping("guideline-1", "999998");
        DSGuidelineProviderMapping second = new DSGuidelineProviderMapping("guideline-1", "999998");

        assertThat(first.equals(second)).isTrue();
        assertThat(second.equals(first)).isTrue();
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(List.of(first).indexOf(second)).isZero();
    }

    @Test
    @DisplayName("should not equal a mapping for another provider or guideline")
    void shouldNotBeEqual_forOtherProviderOrGuideline() {
        DSGuidelineProviderMapping mapping = new DSGuidelineProviderMapping("guideline-1", "999998");

        assertThat(mapping.equals(new DSGuidelineProviderMapping("guideline-1", "100"))).isFalse();
        assertThat(mapping.equals(new DSGuidelineProviderMapping("guideline-2", "999998"))).isFalse();
    }

    @Test
    @DisplayName("should answer false, not throw, for null and foreign objects")
    void shouldReturnFalse_forNullAndForeignObjects() {
        DSGuidelineProviderMapping mapping = new DSGuidelineProviderMapping("guideline-1", "999998");

        assertThat(mapping.equals(null)).isFalse();
        assertThat(mapping.equals("guideline-1")).isFalse();
    }

    @Test
    @DisplayName("should equal itself but not another mapping while keys are missing")
    void shouldNotThrow_whenKeysAreMissing() {
        DSGuidelineProviderMapping empty = new DSGuidelineProviderMapping();
        DSGuidelineProviderMapping noProvider = new DSGuidelineProviderMapping("guideline-1", null);
        DSGuidelineProviderMapping noGuideline = new DSGuidelineProviderMapping(null, "999998");

        assertThat(empty.equals(empty)).isTrue();
        assertThat(empty.equals(new DSGuidelineProviderMapping())).isFalse();
        assertThat(noProvider.equals(new DSGuidelineProviderMapping("guideline-1", null))).isFalse();
        assertThat(noGuideline.equals(new DSGuidelineProviderMapping(null, "999998"))).isFalse();
        assertThat(new DSGuidelineProviderMapping("guideline-1", "999998").equals(noProvider)).isFalse();
        assertThat(new DSGuidelineProviderMapping("guideline-1", "999998").equals(noGuideline)).isFalse();
    }
}
