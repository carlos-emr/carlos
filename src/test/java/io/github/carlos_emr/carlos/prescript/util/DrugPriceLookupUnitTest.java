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
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.carlos.commn.dao.ResourceStorageDao;
import io.github.carlos_emr.carlos.commn.model.ResourceStorage;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DrugPriceLookup}.
 *
 * <p>Tests verify lazy-loading behaviour, DIN lookup, null-safety, and the three-source
 * resolution order (config file → ResourceStorage → bundled classpath XML).
 * Static state is reset before and after each test to ensure isolation.
 *
 * @since 2026-03-22
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("DrugPriceLookup unit tests")
class DrugPriceLookupUnitTest extends CarlosUnitTestBase {

    private ResourceStorageDao mockResourceStorageDao;

    /**
     * Resets static cache and registers a mock ResourceStorageDao that returns null
     * (triggers classpath fallback) before each test.
     */
    @BeforeEach
    void setUp() {
        // Reset static state so each test starts with an empty, unloaded cache
        DrugPriceLookup.loaded = false;
        DrugPriceLookup.costLookup.clear();

        // Default: no DB resource found → classpath fallback
        mockResourceStorageDao = mock(ResourceStorageDao.class);
        when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(null);
        registerMock(ResourceStorageDao.class, mockResourceStorageDao);
    }

    /**
     * Cleans up static state after each test to prevent pollution across the suite.
     */
    @AfterEach
    void tearDown() {
        DrugPriceLookup.loaded = false;
        DrugPriceLookup.costLookup.clear();
    }

    @Nested
    @DisplayName("getPriceInfoForDin")
    class GetPriceInfoForDin {

        @Test
        @DisplayName("should return null when din is null")
        void shouldReturnNull_whenDinIsNull() {
            String result = DrugPriceLookup.getPriceInfoForDin(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should not trigger load when din is null")
        void shouldNotTriggerLoad_whenDinIsNull() {
            DrugPriceLookup.getPriceInfoForDin(null);
            // Formulary should not have been loaded for a null DIN
            assertThat(DrugPriceLookup.loaded).isFalse();
        }

        @Test
        @DisplayName("should return price when din exists in bundled classpath formulary")
        void shouldReturnPrice_whenDinExistsInClasspathFormulary() {
            // DIN 02536269 (Sandoz Bilastine) is present in oscar/oscarRx/data_extract_20250730.xml
            String result = DrugPriceLookup.getPriceInfoForDin("02536269");
            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("should return null when din is not in formulary")
        void shouldReturnNull_whenDinNotInFormulary() {
            String result = DrugPriceLookup.getPriceInfoForDin("00000000");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should load formulary lazily on first non-null din call")
        void shouldLoadFormularyLazily_onFirstNonNullCall() {
            assertThat(DrugPriceLookup.loaded).isFalse();
            DrugPriceLookup.getPriceInfoForDin("02536269");
            assertThat(DrugPriceLookup.loaded).isTrue();
        }

        @Test
        @DisplayName("should populate costLookup map after first lookup")
        void shouldPopulateCostLookupMap_afterFirstLookup() {
            assertThat(DrugPriceLookup.costLookup).isEmpty();
            DrugPriceLookup.getPriceInfoForDin("02536269");
            assertThat(DrugPriceLookup.costLookup).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("reLoadLookupInformation")
    class ReLoadLookupInformation {

        @Test
        @DisplayName("should clear and reload the cache")
        void shouldClearAndReloadCache() {
            // Trigger initial load
            DrugPriceLookup.getPriceInfoForDin("02536269");
            assertThat(DrugPriceLookup.loaded).isTrue();
            int initialSize = DrugPriceLookup.costLookup.size();
            assertThat(initialSize).isGreaterThan(0);

            // Reload
            DrugPriceLookup.reLoadLookupInformation();

            // Should be reloaded with same content
            assertThat(DrugPriceLookup.loaded).isTrue();
            assertThat(DrugPriceLookup.costLookup).hasSize(initialSize);
        }

        @Test
        @DisplayName("should still return price after reload")
        void shouldStillReturnPrice_afterReload() {
            DrugPriceLookup.getPriceInfoForDin("02536269"); // initial load
            DrugPriceLookup.reLoadLookupInformation();     // reload

            String result = DrugPriceLookup.getPriceInfoForDin("02536269");
            assertThat(result).isNotNull();
            assertThat(result).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("ResourceStorage source")
    class ResourceStorageSource {

        @Test
        @DisplayName("should fall through to classpath when resourceStorage has null file contents")
        void shouldFallThroughToClasspath_whenResourceStorageHasNullContents() {
            ResourceStorage mockStorage = mock(ResourceStorage.class);
            when(mockStorage.getFileContents()).thenReturn(null);
            when(mockStorage.getId()).thenReturn(1);
            when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(mockStorage);

            // Should fall through to classpath and still load a known DIN
            String result = DrugPriceLookup.getPriceInfoForDin("02536269");
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should use resourceStorage when file contents are present")
        void shouldUseResourceStorage_whenFileContentsPresent() {
            String minimalXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<extract><formulary>"
                    + "<drug id=\"99000001\"><individualPrice>5.99</individualPrice></drug>"
                    + "</formulary></extract>";

            ResourceStorage mockStorage = mock(ResourceStorage.class);
            when(mockStorage.getFileContents()).thenReturn(minimalXml.getBytes());
            when(mockStorage.getId()).thenReturn(1);
            when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(mockStorage);

            String result = DrugPriceLookup.getPriceInfoForDin("99000001");
            assertThat(result).isEqualTo("5.99");
        }

        @Test
        @DisplayName("should return null for unknown din when loaded from resourceStorage")
        void shouldReturnNull_forUnknownDin_whenLoadedFromResourceStorage() {
            String minimalXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<extract><formulary>"
                    + "<drug id=\"99000001\"><individualPrice>5.99</individualPrice></drug>"
                    + "</formulary></extract>";

            ResourceStorage mockStorage = mock(ResourceStorage.class);
            when(mockStorage.getFileContents()).thenReturn(minimalXml.getBytes());
            when(mockStorage.getId()).thenReturn(1);
            when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(mockStorage);

            String result = DrugPriceLookup.getPriceInfoForDin("00000000");
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should skip drug elements missing id attribute")
        void shouldSkipDrugElements_missingIdAttribute() {
            String xmlWithMissingId = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<extract><formulary>"
                    + "<drug><individualPrice>3.00</individualPrice></drug>"
                    + "<drug id=\"99000002\"><individualPrice>7.50</individualPrice></drug>"
                    + "</formulary></extract>";

            ResourceStorage mockStorage = mock(ResourceStorage.class);
            when(mockStorage.getFileContents()).thenReturn(xmlWithMissingId.getBytes());
            when(mockStorage.getId()).thenReturn(1);
            when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(mockStorage);

            // Drug with ID should still load
            String result = DrugPriceLookup.getPriceInfoForDin("99000002");
            assertThat(result).isEqualTo("7.50");
        }

        @Test
        @DisplayName("should skip drug elements missing individualPrice child")
        void shouldSkipDrugElements_missingIndividualPrice() {
            String xmlWithMissingPrice = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<extract><formulary>"
                    + "<drug id=\"99000003\"></drug>"
                    + "<drug id=\"99000004\"><individualPrice>2.25</individualPrice></drug>"
                    + "</formulary></extract>";

            ResourceStorage mockStorage = mock(ResourceStorage.class);
            when(mockStorage.getFileContents()).thenReturn(xmlWithMissingPrice.getBytes());
            when(mockStorage.getId()).thenReturn(1);
            when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(mockStorage);

            // Drug without price should not be in lookup
            assertThat(DrugPriceLookup.getPriceInfoForDin("99000003")).isNull();
            // Drug with price should be available
            assertThat(DrugPriceLookup.getPriceInfoForDin("99000004")).isEqualTo("2.25");
        }
    }
}
