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
package io.github.carlos_emr.carlos.providers.data;

import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

/**
 * Covers the "Last, First" parsing in {@link ProviderData#searchProvider(String, boolean)} used by
 * the provider autocomplete.
 *
 * @since 2026-09-24
 */
@DisplayName("ProviderData.searchProvider name parsing")
@Tag("unit")
@Tag("provider")
class ProviderDataSearchProviderUnitTest extends CarlosUnitTestBase {

    private ProviderDataDao providerDataDao;

    @BeforeEach
    void setUp() {
        // Mockito returns an empty list for unstubbed List-returning methods.
        providerDataDao = createAndRegisterMock(ProviderDataDao.class);
    }

    @Test
    @DisplayName("should search by last name only when the term ends with a comma")
    void shouldSearchByLastNameOnly_whenTermHasTrailingComma() {
        assertThat(ProviderData.searchProvider("Smith,", true)).isEmpty();

        verify(providerDataDao).findByName(eq(""), eq("Smith"), eq(true));
    }

    @Test
    @DisplayName("should split last and first names around the comma")
    void shouldSplitLastAndFirstName_whenTermHasComma() {
        ProviderData.searchProvider("Smith, Jo", false);

        verify(providerDataDao).findByName(eq("Jo"), eq("Smith"), eq(false));
    }

    @Test
    @DisplayName("should treat a term without a comma as a last name")
    void shouldSearchByLastNameOnly_whenTermHasNoComma() {
        ProviderData.searchProvider("  Smith  ", false);

        verify(providerDataDao).findByName(isNull(), eq("Smith"), eq(false));
    }
}
