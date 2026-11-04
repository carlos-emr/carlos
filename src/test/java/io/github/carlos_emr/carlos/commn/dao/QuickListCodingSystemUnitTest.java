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
package io.github.carlos_emr.carlos.commn.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quick-list coding-system names (#4989): the ICHPPC coding system is also stored under its older name "ichppc".
 *
 * @since 2026-10-19
 */
@Tag("unit")
@DisplayName("Quick-list coding-system names")
class QuickListCodingSystemUnitTest {

    @Test
    void shouldIncludeTheOlderIchppcName_forTheIchppcCodingSystem() {
        assertThat(QuickListCodingSystem.storedNames("ichppccode")).containsExactly("ichppccode", "ichppc");
    }

    @Test
    void shouldKeepOnlyTheGivenName_forOtherCodingSystems() {
        assertThat(QuickListCodingSystem.storedNames("icd9")).containsExactly("icd9");
    }

    @Test
    void shouldReadIchppcAsIchppccode_whenCanonicalised() {
        assertThat(QuickListCodingSystem.canonical("ichppc")).isEqualTo("ichppccode");
        assertThat(QuickListCodingSystem.canonical("icd9")).isEqualTo("icd9");
        assertThat(QuickListCodingSystem.canonical(null)).isNull();
    }
}
